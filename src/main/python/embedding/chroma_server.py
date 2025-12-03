from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
import uvicorn
import chromadb

app = FastAPI()

# Initialize Chroma client
client = chromadb.Client()

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


# ---------- Endpoints ----------

@app.get("/health")
def health():
    return {"status": "ok"}

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
        client.get_collection(name)
        return {"exists": True, "error": None}
    except Exception as e:
        return {"exists": False, "error": str(e)}

@app.post("/chroma/collections/{name}/create")
def collection_create(name: str):
    try:
        col = client.get_or_create_collection(name)
        return {"created": True, "error": None}
    except Exception as e:
        return {"created": False, "error": str(e)}

@app.delete("/chroma/collections/{name}")
def collection_delete(name: str):
    try:
        client.delete_collection(name)
        return {"deleted": True}
    except Exception as e:
        raise HTTPException(500, f"Delete failed: {e}")

@app.post("/chroma/collections/{name}/add")
def collection_add(name: str, req: AddDocumentsRequest):
    try:
        col = client.get_or_create_collection(name)
        col.add(
            ids=req.ids,
            documents=req.documents,
            metadatas=req.metadatas,
            embeddings=req.embeddings,
        )
        return {"status": "ok", "count": len(req.ids), "error": None}
    except Exception as e:
        return {"status": "error", "count": 0, "error": str(e)}

@app.delete("/chroma/collections/{name}/delete")
def collection_delete_docs(name: str, req: DeleteDocumentsRequest):
    try:
        col = client.get_or_create_collection(name)
        col.delete(ids=req.ids)
        return {"deleted": len(req.ids), "error": None}
    except Exception as e:
        return {"deleted": 0, "error": str(e)}

@app.post("/chroma/collections/{name}/query")
def collection_query(name: str, req: QueryRequest):
    try:
        col = client.get_or_create_collection(name)
        results = col.query(
            query_texts=req.query_texts,
            query_embeddings=req.query_embeddings,
            n_results=req.n_results,
        )
        return {"results": results}
    except Exception as e:
        return {"results": None, "error": str(e)}


# ---------- Main ----------
if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8081)
