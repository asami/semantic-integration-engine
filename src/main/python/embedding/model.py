import numpy as np
import hashlib

EMBED_DIM = 128

def compute_embedding(text: str) -> list[float]:
    """
    Lightweight OSS embedding:
    - SHA256 により単語ごとにハッシュ
    - 128 次元に落とし込み
    - L2 normalize
    """
    vec = np.zeros(EMBED_DIM, dtype=np.float32)

    for token in text.split():
        h = hashlib.sha256(token.encode("utf-8")).digest()
        for i in range(EMBED_DIM):
            vec[i] += h[i] / 255.0

    # L2 normalize
    norm = np.linalg.norm(vec)
    if norm > 0:
        vec /= norm

    return vec.tolist()
