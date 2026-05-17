"""Downloads and caches the all-MiniLM-L6-v2 ONNX model from HuggingFace."""

from __future__ import annotations

import os
import sys
from pathlib import Path
from urllib.request import urlretrieve

MODEL_NAME = "all-MiniLM-L6-v2"
BASE_URL = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main"
MODEL_FILE = "onnx/model.onnx"
TOKENIZER_FILE = "tokenizer.json"


def get_cache_dir() -> Path:
    """Get the cache directory for the embedding model."""
    return Path.home() / ".mcp-bridge" / "models" / MODEL_NAME


def is_model_cached() -> bool:
    """Check if model files are already cached locally."""
    cache = get_cache_dir()
    return (cache / "model.onnx").exists() and (cache / "tokenizer.json").exists()


def get_model_paths() -> tuple[Path, Path] | None:
    """Get paths to cached model files. Returns None if not cached."""
    if not is_model_cached():
        return None
    cache = get_cache_dir()
    return cache / "model.onnx", cache / "tokenizer.json"


def download_model() -> tuple[Path, Path]:
    """Download model from HuggingFace and cache locally."""
    cache = get_cache_dir()
    cache.mkdir(parents=True, exist_ok=True)

    model_path = cache / "model.onnx"
    tokenizer_path = cache / "tokenizer.json"

    if not model_path.exists():
        _log("Downloading model (~80MB)...")
        _download_file(f"{BASE_URL}/{MODEL_FILE}", model_path)
        _log("Model downloaded.")

    if not tokenizer_path.exists():
        _log("Downloading tokenizer...")
        _download_file(f"{BASE_URL}/{TOKENIZER_FILE}", tokenizer_path)
        _log("Tokenizer downloaded.")

    return model_path, tokenizer_path


def _download_file(url: str, dest: Path) -> None:
    """Download a file from URL to local path."""
    try:
        urlretrieve(url, str(dest))
    except Exception as e:
        # Clean up partial download
        if dest.exists():
            dest.unlink()
        raise RuntimeError(f"Download failed: {e}") from e


def _log(msg: str) -> None:
    print(f"[embedding] {msg}", file=sys.stderr, flush=True)
