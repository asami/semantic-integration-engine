import json
import requests
import chromadb
from chromadb.config import Settings
from openai import OpenAI

print("Loading JSON-LD...")
with open("/tmp/site.jsonld") as f:
    data = json.load(f)

# 1. JSON-LD から説明文を抽出（あなたの好きな構造に変更可）
docs = []
ids = []

for item in data.get("@graph", []):
    label = item.get("rdfs:label")
    desc  = item.get("description")
    if label or desc:
        text = f"{label}\n{desc}"
        docs.append(text)
        ids.append(item.get("@id", f"doc-{len(ids)}"))

print(f"Extracted {len(docs)} documents")

# 2. Embedding 生成
client = OpenAI()
embeddings = []

for d in docs:
    e = client.embeddings.create(
        model="text-embedding-3-small",
        input=d
    ).data[0].embedding
    embeddings.append(e)

# 3. ChromaDB にロード
chroma = chromadb.HttpClient(
    host="chromadb",
    port=8000,
    settings=Settings(allow_reset=True)
)

collection = chroma.get_or_create_collection("simplemodeling")

collection.add(
    ids=ids,
    documents=docs,
    embeddings=embeddings
)

print("ChromaDB initialization complete.")
