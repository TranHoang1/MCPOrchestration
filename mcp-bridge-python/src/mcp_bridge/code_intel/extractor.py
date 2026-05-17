"""Signature extractor for Python bridge.
Uses only stdlib re module. Zero external dependencies.
"""

import re
from dataclasses import dataclass

@dataclass
class ExtractedSymbol:
    name: str
    kind: str
    signature: str
    line_start: int
    visibility: str | None = None


PATTERNS: dict[str, list[tuple[re.Pattern, str]]] = {
    'kotlin': [
        (re.compile(r'^\s*(?:public|private|internal|protected)?\s*(?:data|sealed|abstract|open|enum)?\s*class\s+(\w+)'), 'class'),
        (re.compile(r'^\s*(?:public|private|internal)?\s*interface\s+(\w+)'), 'interface'),
        (re.compile(r'^\s*(?:public|private|internal)?\s*(?:companion\s+)?object\s+(\w+)'), 'object'),
        (re.compile(r'^\s*(?:public|private|internal|protected)?\s*(?:suspend\s+)?(?:override\s+)?fun\s+(\w+)'), 'function'),
        (re.compile(r'^\s*(?:public|private|internal|protected)?\s*(?:override\s+)?(?:val|var)\s+(\w+)'), 'property'),
    ],
    'typescript': [
        (re.compile(r'^\s*export\s+(?:abstract\s+)?class\s+(\w+)'), 'class'),
        (re.compile(r'^\s*export\s+interface\s+(\w+)'), 'interface'),
        (re.compile(r'^\s*export\s+type\s+(\w+)'), 'type'),
        (re.compile(r'^\s*export\s+enum\s+(\w+)'), 'enum'),
        (re.compile(r'^\s*export\s+(?:async\s+)?function\s+(\w+)'), 'function'),
        (re.compile(r'^\s*export\s+const\s+(\w+)'), 'property'),
    ],
    'python': [
        (re.compile(r'^class\s+(\w+)'), 'class'),
        (re.compile(r'^(?:async\s+)?def\s+(\w+)'), 'function'),
    ],
    'go': [
        (re.compile(r'^func\s+(?:\(\w+\s+\*?\w+\)\s+)?(\w+)'), 'function'),
        (re.compile(r'^type\s+(\w+)\s+(struct|interface)'), 'type'),
    ],
    'rust': [
        (re.compile(r'^\s*(?:pub\s+)?(?:async\s+)?fn\s+(\w+)'), 'function'),
        (re.compile(r'^\s*(?:pub\s+)?struct\s+(\w+)'), 'struct'),
        (re.compile(r'^\s*(?:pub\s+)?enum\s+(\w+)'), 'enum'),
        (re.compile(r'^\s*(?:pub\s+)?trait\s+(\w+)'), 'interface'),
    ],
    'bash': [
        (re.compile(r'^(?:function\s+(\w+)|(\w+)\s*\(\))'), 'function'),
    ],
    'powershell': [
        (re.compile(r'^\s*function\s+([\w-]+)'), 'function'),
    ],
}
PATTERNS['javascript'] = PATTERNS['typescript']


def extract_symbols(content: str, language: str) -> list[ExtractedSymbol]:
    """Extract code symbols from file content using regex patterns."""
    lang_patterns = PATTERNS.get(language)
    if not lang_patterns:
        return []

    symbols: list[ExtractedSymbol] = []
    for i, line in enumerate(content.split('\n'), start=1):
        if len(symbols) >= 1000:
            break
        for pattern, kind in lang_patterns:
            match = pattern.match(line)
            if not match:
                continue
            name = next((g for g in match.groups() if g), '')
            if not name:
                continue
            symbols.append(ExtractedSymbol(
                name=name, kind=kind, signature=line.strip(),
                line_start=i, visibility=_extract_visibility(line),
            ))
            break
    return symbols


def _extract_visibility(line: str) -> str | None:
    trimmed = line.lstrip()
    if trimmed.startswith('private'): return 'private'
    if trimmed.startswith('protected'): return 'protected'
    if trimmed.startswith('internal'): return 'internal'
    if trimmed.startswith(('public', 'export', 'pub')): return 'public'
    return None
