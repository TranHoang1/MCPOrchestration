"""Local tools — stream_write_file and embed_images (executed locally, not proxied)."""

from __future__ import annotations

import base64
import os
import re
from pathlib import Path
from typing import Any


def handle_stream_write(args: dict[str, Any], workspace_root: str | None = None) -> dict[str, Any]:
    """Write content to a file on disk."""
    file_path = args.get("file_path", "")
    content = args.get("content", "")
    mode = args.get("mode", "write")
    encoding = args.get("encoding", "utf-8")

    if not file_path:
        raise ValueError("file_path is required")

    resolved = _resolve_path(file_path, workspace_root)

    if mode == "create" and resolved.exists():
        raise ValueError(f"File already exists: {resolved}")

    if not content and resolved.exists():
        return {"status": "no-op", "path": str(resolved), "message": "File exists, no content provided"}

    resolved.parent.mkdir(parents=True, exist_ok=True)

    if mode == "append":
        with open(resolved, "a", encoding=encoding) as f:
            if resolved.stat().st_size > 0:
                f.write("\n")
            f.write(content)
    else:
        with open(resolved, "w", encoding=encoding) as f:
            f.write(content)

    return {"status": "ok", "path": str(resolved), "bytes_written": len(content.encode(encoding))}


def handle_embed_images(args: dict[str, Any], workspace_root: str | None = None) -> dict[str, Any]:
    """Replace local image references with base64 data URIs."""
    file_path = args.get("file_path", "")
    output_path = args.get("output_path")

    if not file_path:
        raise ValueError("file_path is required")

    resolved = _resolve_path(file_path, workspace_root)
    if not resolved.exists():
        raise ValueError(f"File not found: {resolved}")

    content = resolved.read_text(encoding="utf-8")
    img_pattern = re.compile(r"!\[([^\]]*)\]\(([^)]+)\)")
    images_embedded = 0

    def replace_image(match: re.Match[str]) -> str:
        nonlocal images_embedded
        alt_text = match.group(1)
        img_src = match.group(2)
        if img_src.startswith(("http://", "https://", "data:")):
            return match.group(0)
        img_path = (resolved.parent / img_src).resolve()
        if not img_path.exists():
            return match.group(0)
        data = img_path.read_bytes()
        ext = img_path.suffix.lower().lstrip(".")
        mime = {"png": "image/png", "jpg": "image/jpeg", "jpeg": "image/jpeg", "gif": "image/gif", "svg": "image/svg+xml"}.get(ext, "application/octet-stream")
        b64 = base64.b64encode(data).decode("ascii")
        images_embedded += 1
        return f"![{alt_text}](data:{mime};base64,{b64})"

    result = img_pattern.sub(replace_image, content)
    out = _resolve_path(output_path, workspace_root) if output_path else resolved
    out.write_text(result, encoding="utf-8")

    return {"status": "ok", "images_embedded": images_embedded, "output_path": str(out)}


def _resolve_path(path: str, workspace_root: str | None) -> Path:
    p = Path(path)
    if p.is_absolute():
        return p
    root = Path(workspace_root) if workspace_root else Path.cwd()
    return (root / p).resolve()
