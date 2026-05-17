"""ONNX Runtime wrapper for all-MiniLM-L6-v2 inference."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

try:
    import numpy as np
    _numpy_available = True
except ImportError:
    _numpy_available = False

DIMENSIONS = 384
MAX_SEQ_LEN = 256
MAX_BATCH = 32

# Lazy-loaded references
_session: Any = None
_tokenizer: "WordPieceTokenizer | None" = None
_ort_available: bool | None = None


class WordPieceTokenizer:
    """Simple WordPiece tokenizer using tokenizer.json vocab."""

    def __init__(self, config: dict[str, Any]) -> None:
        self.vocab: dict[str, int] = config["model"]["vocab"]
        self.cls_id = self.vocab.get("[CLS]", 101)
        self.sep_id = self.vocab.get("[SEP]", 102)
        self.pad_id = self.vocab.get("[PAD]", 0)
        self.unk_id = self.vocab.get("[UNK]", 100)

    def tokenize(self, text: str, max_len: int) -> list[int]:
        """Tokenize text into input_ids with CLS/SEP, truncate/pad."""
        tokens = self._wordpiece_tokenize(text.lower())
        truncated = tokens[: max_len - 2]
        ids = [self.cls_id] + truncated + [self.sep_id]
        ids += [self.pad_id] * (max_len - len(ids))
        return ids

    def _wordpiece_tokenize(self, text: str) -> list[int]:
        """Tokenize text using WordPiece algorithm."""
        words = text.split()
        ids: list[int] = []
        for word in words:
            ids.extend(self._tokenize_word(word))
            if len(ids) >= MAX_SEQ_LEN - 2:
                break
        return ids

    def _tokenize_word(self, word: str) -> list[int]:
        """Tokenize a single word into subword IDs."""
        ids: list[int] = []
        start = 0
        while start < len(word):
            found = False
            for end in range(len(word), start, -1):
                sub = word[start:end] if start == 0 else f"##{word[start:end]}"
                if sub in self.vocab:
                    ids.append(self.vocab[sub])
                    start = end
                    found = True
                    break
            if not found:
                ids.append(self.unk_id)
                start += 1
        return ids


def _check_ort() -> bool:
    """Check if onnxruntime and numpy are available."""
    global _ort_available
    if _ort_available is not None:
        return _ort_available
    if not _numpy_available:
        _ort_available = False
        _log("numpy not installed — ONNX inference unavailable")
        return False
    try:
        import onnxruntime  # noqa: F401
        _ort_available = True
    except ImportError:
        _ort_available = False
        _log("onnxruntime not installed — using stub")
    return _ort_available


def init_session(model_path: Path, tokenizer_path: Path) -> bool:
    """Initialize ONNX session and tokenizer. Returns True on success."""
    global _session, _tokenizer
    if _session is not None:
        return True
    if not _check_ort():
        return False
    try:
        import onnxruntime as ort
        _session = ort.InferenceSession(str(model_path))
        with open(tokenizer_path, "r", encoding="utf-8") as f:
            config = json.load(f)
        _tokenizer = WordPieceTokenizer(config)
        return True
    except Exception as e:
        _log(f"Failed to init ONNX session: {e}")
        return False


def is_onnx_ready() -> bool:
    """Check if ONNX inference is ready."""
    return _session is not None and _tokenizer is not None


def infer(texts: list[str]) -> list[list[float]]:
    """Run inference on texts. Returns list of 384-dim embeddings."""
    if not is_onnx_ready():
        return [[0.0] * DIMENSIONS for _ in texts]

    batch = texts[:MAX_BATCH]
    batch_size = len(batch)

    # Tokenize
    all_ids = [_tokenizer.tokenize(t, MAX_SEQ_LEN) for t in batch]  # type: ignore
    input_ids = np.array(all_ids, dtype=np.int64)  # type: ignore[name-defined]
    attention_mask = (input_ids != 0).astype(np.int64)  # type: ignore[name-defined]
    token_type_ids = np.zeros_like(input_ids, dtype=np.int64)  # type: ignore[name-defined]

    # Run inference
    feeds = {
        "input_ids": input_ids,
        "attention_mask": attention_mask,
        "token_type_ids": token_type_ids,
    }
    outputs = _session.run(None, feeds)
    hidden_state = outputs[0]  # shape: [batch, seq_len, 384]

    return _mean_pool_normalize(hidden_state, attention_mask, batch_size)


def _mean_pool_normalize(
    hidden: Any, mask: Any, batch_size: int
) -> list[list[float]]:
    """Mean pooling + L2 normalization."""
    results: list[list[float]] = []
    for i in range(batch_size):
        # Expand mask for broadcasting
        token_mask = mask[i][:, None]  # [seq_len, 1]
        masked = hidden[i] * token_mask  # [seq_len, 384]
        token_count = token_mask.sum()
        if token_count > 0:
            embedding = masked.sum(axis=0) / token_count
        else:
            embedding = np.zeros(DIMENSIONS)  # type: ignore[name-defined]
        # L2 normalize
        norm = np.linalg.norm(embedding)  # type: ignore[name-defined]
        if norm > 0:
            embedding = embedding / norm
        results.append(embedding.tolist())
    return results


def _log(msg: str) -> None:
    print(f"[embedding] {msg}", file=sys.stderr, flush=True)
