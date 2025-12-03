from fastapi import FastAPI
from pydantic import BaseModel
from model import compute_embedding

app = FastAPI(
    title="SIE OSS Embedding Engine",
    description="Lightweight OSS embedding service for SIE RAG pipeline",
    version="0.1"
)

class EmbeddingRequest(BaseModel):
    texts: list[str]

class EmbeddingResponse(BaseModel):
    embeddings: list[list[float]]

@app.post("/v1/embeddings", response_model=EmbeddingResponse)
async def embeddings(req: EmbeddingRequest):
    embs = [compute_embedding(t) for t in req.texts]
    return EmbeddingResponse(embeddings=embs)

@app.get("/health")
async def health():
    return {"status": "ok"}
