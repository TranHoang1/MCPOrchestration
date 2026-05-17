"""Code Intelligence module for Python bridge — Layer 1 (FTS5) + optional Layer 2 (Ollama)."""

from .database import DatabaseManager
from .scanner import FileScanner
from .extractor import extract_symbols
from .tools import CodeIntelTools

__all__ = ['DatabaseManager', 'FileScanner', 'extract_symbols', 'CodeIntelTools']
