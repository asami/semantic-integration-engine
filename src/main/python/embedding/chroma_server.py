from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
import uvicorn
import chromadb
from sentence_transformers import SentenceTransformer
import os

app = FastAPI()

# Initialize Chroma client
from chromadb.config import Settings
from chromadb import PersistentClient

PERSIST_DIR = "/chroma-data"

client = PersistentClient(path=PERSIST_DIR)

# Initialize OSS embedding model
MODEL_NAME = os.getenv("EMBEDDING_MODEL", "sentence-transformers/all-MiniLM-L6-v2")
model = SentenceTransformer(MODEL_NAME)

# Default non-empty metadata for Chroma collection (Chroma 0.4.x requires non-empty dict)
DEFAULT_COLLECTION_METADATA = {
    "initialized": True
}

# ---------- Models ----------
class AddDocumentsRequest(BaseModel):
    ids: List[str]
    documents: List[str]
    metadatas: Optional[List[Dict[str, Any]]] = None
    embeddings: Optional[List[List[float]]] = None

class QueryRequest(BaseModel):
    query_texts: Optional[List[str]] = None
    query_embeddings: Optional[List[List[float]]] = None
    n_results: int = 5

class DeleteDocumentsRequest(BaseModel):
    ids: List[str]

class EmbedRequest(BaseModel):
    texts: List[str]

class EmbedResponse(BaseModel):
    vectors: List[List[float]]


# ---------- Endpoints ----------

@app.get("/health")
def health():
    # Model readiness: model is considered ready if it is loaded (not None)
    model_ready = model is not None

    # Chroma readiness: try listing collections
    try:
        client.list_collections()
        chroma_ready = True
        chroma_error = None
    except Exception as e:
        chroma_ready = False
        chroma_error = str(e)

    # Top-level degraded status
    degraded = not (model_ready and chroma_ready)

    return {
        "status": "degraded" if degraded else "ok",
        "model_ready": model_ready,
        "chroma_ready": chroma_ready,
        "chroma_error": chroma_error,
    }

@app.get("/chroma/collections")
def list_collections():
    try:
        cols = client.list_collections()
        return {"collections": [c.name for c in cols]}
    except Exception as e:
        raise HTTPException(500, f"List failed: {e}")


@app.get("/chroma/collections/{name}/exists")
def collection_exists(name: str):
    try:
        client.get_collection(name=name)
        return {"exists": True, "error": None}
    except Exception as e:
        return {"exists": False, "error": str(e)}

# Collection count endpoint
@app.get("/chroma/collections/{name}/count")
def collection_count(name: str):
    try:
        col = client.get_collection(name=name)
        return {"count": col.count()}
    except Exception as e:
        raise HTTPException(500, f"Count failed: {e}")

@app.post("/chroma/collections/{name}/create")
def collection_create(name: str):
    try:
        col = client.get_or_create_collection(name=name, metadata=DEFAULT_COLLECTION_METADATA)
        return {"created": True, "error": None}
    except Exception as e:
        return {"created": False, "error": str(e)}

@app.delete("/chroma/collections/{name}")
def collection_delete(name: str):
    try:
        client.delete_collection(name=name)
        return {"deleted": True}
    except Exception as e:
        raise HTTPException(500, f"Delete failed: {e}")

@app.post("/chroma/collections/{name}/add")
def collection_add(name: str, req: AddDocumentsRequest):
    try:
        col = client.get_or_create_collection(name=name, metadata=DEFAULT_COLLECTION_METADATA)
        if req.metadatas is None:
            # Ensure metadata is never an empty dict (Chroma 0.4.x limitation)
            metadatas = [{"id": req.ids[i]} for i in range(len(req.ids))]
        else:
            metadatas = req.metadatas
        col.add(
            ids=req.ids,
            documents=req.documents,
            metadatas=metadatas,
            embeddings=req.embeddings,
        )
        return {"status": "ok", "count": len(req.ids), "error": None}
    except Exception as e:
        return {"status": "error", "count": 0, "error": str(e)}

@app.delete("/chroma/collections/{name}/delete")
def collection_delete_docs(name: str, req: DeleteDocumentsRequest):
    try:
        col = client.get_or_create_collection(name=name, metadata=DEFAULT_COLLECTION_METADATA)
        col.delete(ids=req.ids)
        return {"deleted": len(req.ids), "error": None}
    except Exception as e:
        return {"deleted": 0, "error": str(e)}

@app.post("/chroma/collections/{name}/query")
def collection_query(name: str, req: QueryRequest):
    try:
        col = client.get_or_create_collection(name=name, metadata=DEFAULT_COLLECTION_METADATA)
        results = col.query(
            query_texts=req.query_texts,
            query_embeddings=req.query_embeddings,
            n_results=req.n_results,
        )
        return {"results": results}
    except Exception as e:
        return {"results": None, "error": str(e)}

@app.post("/embed", response_model=EmbedResponse)
def embed(req: EmbedRequest):
    if not req.texts:
        return EmbedResponse(vectors=[])
    embeddings = model.encode(req.texts, convert_to_numpy=True).tolist()
    return EmbedResponse(vectors=embeddings)


# ---------- Main ----------
if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8081)
