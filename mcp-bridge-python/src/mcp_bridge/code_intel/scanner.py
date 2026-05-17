"""File scanner for Python bridge code intelligence.
Uses only stdlib: os, hashlib, re. Zero external dependencies.
"""

import os
import hashlib
from pathlib import Path
from dataclasses import dataclass

EXTENSION_MAP = {
    '.kt': 'kotlin', '.kts': 'kotlin', '.java': 'java',
    '.ts': 'typescript', '.tsx': 'typescript',
    '.js': 'javascript', '.jsx': 'javascript', '.mjs': 'javascript',
    '.py': 'python', '.go': 'go', '.rs': 'rust',
    '.sh': 'bash', '.ps1': 'powershell', '.psm1': 'powershell',
}

DEFAULT_EXCLUDES = {
    'node_modules', 'build', 'dist', '.git', '.gradle',
    '__pycache__', '.venv', 'venv', '.idea',
}


@dataclass
class ScannedFile:
    relative_path: str
    absolute_path: str
    language: str
    size_bytes: int


def detect_language(filename: str) -> str | None:
    """Detect language from file extension."""
    ext = Path(filename).suffix.lower()
    return EXTENSION_MAP.get(ext)


def hash_file(filepath: str) -> str:
    """Compute SHA-256 hash of file content."""
    h = hashlib.sha256()
    with open(filepath, 'rb') as f:
        for chunk in iter(lambda: f.read(8192), b''):
            h.update(chunk)
    return h.hexdigest()


class FileScanner:
    """Scans workspace for indexable source files."""

    def __init__(self, workspace_root: str, max_depth: int = 20, max_size_kb: int = 500):
        self._root = workspace_root
        self._max_depth = max_depth
        self._max_size = max_size_kb * 1024

    def scan(self) -> list[ScannedFile]:
        """Recursively scan workspace, return eligible files."""
        results: list[ScannedFile] = []
        self._walk(self._root, 0, results)
        return results

    def _walk(self, directory: str, depth: int, results: list[ScannedFile]) -> None:
        if depth > self._max_depth:
            return
        try:
            entries = os.scandir(directory)
        except PermissionError:
            return

        for entry in entries:
            if entry.is_dir(follow_symlinks=False):
                if entry.name in DEFAULT_EXCLUDES or entry.name.startswith('.'):
                    continue
                self._walk(entry.path, depth + 1, results)
            elif entry.is_file(follow_symlinks=False):
                lang = detect_language(entry.name)
                if not lang:
                    continue
                try:
                    size = entry.stat().st_size
                except OSError:
                    continue
                if size > self._max_size:
                    continue
                rel = os.path.relpath(entry.path, self._root).replace('\\', '/')
                results.append(ScannedFile(rel, entry.path, lang, size))
