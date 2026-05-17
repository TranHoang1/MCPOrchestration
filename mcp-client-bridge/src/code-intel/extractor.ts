/**
 * Signature extractor for Node.js bridge.
 * Extracts code symbols using regex patterns per language.
 */

export interface ExtractedSymbol {
  name: string;
  kind: string;
  signature: string;
  lineStart: number;
  visibility: string | null;
}

const PATTERNS: Record<string, Array<{ regex: RegExp; kind: string }>> = {
  kotlin: [
    { regex: /^\s*(?:public|private|internal|protected)?\s*(?:data|sealed|abstract|open|enum)?\s*class\s+(\w+)/, kind: 'class' },
    { regex: /^\s*(?:public|private|internal)?\s*interface\s+(\w+)/, kind: 'interface' },
    { regex: /^\s*(?:public|private|internal)?\s*(?:companion\s+)?object\s+(\w+)/, kind: 'object' },
    { regex: /^\s*(?:public|private|internal|protected)?\s*(?:suspend\s+)?(?:override\s+)?fun\s+(\w+)/, kind: 'function' },
    { regex: /^\s*(?:public|private|internal|protected)?\s*(?:override\s+)?(?:val|var)\s+(\w+)/, kind: 'property' },
  ],
  typescript: [
    { regex: /^\s*export\s+(?:abstract\s+)?class\s+(\w+)/, kind: 'class' },
    { regex: /^\s*export\s+interface\s+(\w+)/, kind: 'interface' },
    { regex: /^\s*export\s+type\s+(\w+)/, kind: 'type' },
    { regex: /^\s*export\s+enum\s+(\w+)/, kind: 'enum' },
    { regex: /^\s*export\s+(?:async\s+)?function\s+(\w+)/, kind: 'function' },
    { regex: /^\s*export\s+const\s+(\w+)/, kind: 'property' },
  ],
  python: [
    { regex: /^class\s+(\w+)/, kind: 'class' },
    { regex: /^(?:async\s+)?def\s+(\w+)/, kind: 'function' },
  ],
  go: [
    { regex: /^func\s+(?:\(\w+\s+\*?\w+\)\s+)?(\w+)/, kind: 'function' },
    { regex: /^type\s+(\w+)\s+(struct|interface)/, kind: 'type' },
  ],
  rust: [
    { regex: /^\s*(?:pub\s+)?(?:async\s+)?fn\s+(\w+)/, kind: 'function' },
    { regex: /^\s*(?:pub\s+)?struct\s+(\w+)/, kind: 'struct' },
    { regex: /^\s*(?:pub\s+)?enum\s+(\w+)/, kind: 'enum' },
    { regex: /^\s*(?:pub\s+)?trait\s+(\w+)/, kind: 'interface' },
  ],
  bash: [
    { regex: /^(?:function\s+(\w+)|(\w+)\s*\(\))/, kind: 'function' },
  ],
  powershell: [
    { regex: /^\s*function\s+([\w-]+)/, kind: 'function' },
  ],
};

// JavaScript uses same patterns as TypeScript
PATTERNS['javascript'] = PATTERNS['typescript'];

export function extractSymbols(content: string, language: string): ExtractedSymbol[] {
  const langPatterns = PATTERNS[language];
  if (!langPatterns) return [];

  const lines = content.split('\n');
  const symbols: ExtractedSymbol[] = [];

  for (let i = 0; i < lines.length && symbols.length < 1000; i++) {
    const line = lines[i];
    for (const { regex, kind } of langPatterns) {
      const match = regex.exec(line);
      if (!match) continue;
      const name = match[1] || match[2] || '';
      if (!name) continue;
      symbols.push({
        name,
        kind,
        signature: line.trim(),
        lineStart: i + 1,
        visibility: extractVisibility(line),
      });
      break;
    }
  }
  return symbols;
}

function extractVisibility(line: string): string | null {
  const trimmed = line.trimStart();
  if (trimmed.startsWith('private')) return 'private';
  if (trimmed.startsWith('protected')) return 'protected';
  if (trimmed.startsWith('internal')) return 'internal';
  if (trimmed.startsWith('public') || trimmed.startsWith('export')) return 'public';
  if (trimmed.startsWith('pub')) return 'public';
  return null;
}
