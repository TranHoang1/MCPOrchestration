"""Embedding fallback with priority chain for local inference."""

from __future__ import annotations

import sys
from typing import Any, Callable, Awaitable

from .model_downloader import is_model_cached, get_model_paths, download_model
from .onnx_inference import init_session, is_onnx_ready, infer

DIMENSIONS = 384
MAX_BATCH = 32

# Type for MCP embed function: async (texts) -> list[list[float]]
McpEmbedFn = Callable[[list[str]], Awaitable[list[list[float]]]]


class EmbeddingFallback:
    """Local embedding fallback — priority: MCP tool > ONNX cached > download > disable."""

    def __init__(self) -> None:
        self._initialized = False
        self._available = False
        self._mcp_embed_fn: McpEmbedFn | None = None

    def set_mcp_embed(self, fn: McpEmbedFn | None) -> None:
        """Register an MCP 'embed' tool function (highest priority)."""
        self._mcp_embed_fn = fn

    def is_available(self) -> bool:
        """Check if any embedding source is available."""
        if self._mcp_embed_fn:
            return True
        return self._available

    def dimensions(self) -> int:
        """Returns embedding dimensions (384 for all-MiniLM-L6-v2)."""
        return DIMENSIONS

    async def embed(self, texts: list[str]) -> list[list[float]]:
        """Embed texts using priority chain. Lazy-loads on first call."""
        if not texts:
            return []

        # Priority 1: MCP embed tool
        if self._mcp_embed_fn:
            try:
                return await self._mcp_embed_fn(texts)
            except Exception as e:
                self._log(f"MCP embed failed: {e}, falling back to ONNX")

        # Lazy init ONNX on first call
        if not self._initialized:
            self._init_onnx()

        if not self._available:
            self._log("No embedding source available")
            return [[0.0] * DIMENSIONS for _ in texts]

        # Process in batches
        return self._infer_batched(texts)

    def _init_onnx(self) -> None:
        """Initialize ONNX model (lazy, called once)."""
        self._initialized = True
        try:
            # Priority 2: Cached model
            if is_model_cached():
                paths = get_model_paths()
                if paths:
                    self._available = init_session(paths[0], paths[1])
                    if self._available:
                        self._log("ONNX model loaded from cache")
                        return

            # Priority 3: Download model
            self._log("Downloading model...")
            model_path, tokenizer_path = download_model()
            self._available = init_session(model_path, tokenizer_path)
            if self._available:
                self._log("ONNX model ready after download")
            else:
                self._log("ONNX Runtime not available — embedding disabled")
        except Exception as e:
            # Priority 4: Disable gracefully
            self._log(f"Init failed: {e} — embedding disabled")
            self._available = False

    def _infer_batched(self, texts: list[str]) -> list[list[float]]:
        """Run inference in batches of MAX_BATCH."""
        results: list[list[float]] = []
        for i in range(0, len(texts), MAX_BATCH):
            batch = texts[i : i + MAX_BATCH]
            embeddings = infer(batch)
            results.extend(embeddings)
        return results

    def _log(self, msg: str) -> None:
        print(f"[embedding] {msg}", file=sys.stderr, flush=True)
