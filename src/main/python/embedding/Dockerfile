FROM python:3.11-slim

WORKDIR /app

# 必須：chromadb / numpy / pyarrow のビルド用
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    gcc \
    g++ \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt /app/requirements.txt

RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir -r /app/requirements.txt

COPY . /app

EXPOSE 8081

CMD ["uvicorn", "chroma_server:app", "--host", "0.0.0.0", "--port", "8081"]
