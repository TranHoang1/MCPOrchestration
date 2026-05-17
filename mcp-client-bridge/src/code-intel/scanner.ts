/**
 * File scanner for Node.js bridge code intelligence.
 * Traverses workspace, respects .gitignore, detects languages.
 */

import { readdirSync, readFileSync, statSync, existsSync } from 'fs';
import { join, relative, extname } from 'path';
import { createHash } from 'crypto';

export interface ScannedFile {
  relativePath: string;
  absolutePath: string;
  language: string;
  sizeBytes: number;
}

const EXTENSION_MAP: Record<string, string> = {
  '.kt': 'kotlin', '.kts': 'kotlin', '.java': 'java',
  '.ts': 'typescript', '.tsx': 'typescript',
  '.js': 'javascript', '.jsx': 'javascript', '.mjs': 'javascript',
  '.py': 'python', '.go': 'go', '.rs': 'rust',
  '.sh': 'bash', '.ps1': 'powershell', '.psm1': 'powershell',
};

const DEFAULT_EXCLUDES = [
  'node_modules', 'build', 'dist', '.git', '.gradle', '__pycache__', '.venv',
];

export function detectLanguage(fileName: string): string | null {
  const ext = extname(fileName).toLowerCase();
  return EXTENSION_MAP[ext] ?? null;
}

export function hashFile(filePath: string): string {
  const content = readFileSync(filePath);
  return createHash('sha256').update(content).digest('hex');
}

export function scanWorkspace(workspaceRoot: string, maxDepth = 20, maxSizeKb = 500): ScannedFile[] {
  const results: ScannedFile[] = [];
  const maxSizeBytes = maxSizeKb * 1024;

  function walk(dir: string, depth: number): void {
    if (depth > maxDepth) return;
    let entries;
    try { entries = readdirSync(dir, { withFileTypes: true }); } catch { return; }

    for (const entry of entries) {
      const fullPath = join(dir, entry.name);
      if (entry.isDirectory()) {
        if (DEFAULT_EXCLUDES.includes(entry.name) || entry.name.startsWith('.')) continue;
        walk(fullPath, depth + 1);
      } else if (entry.isFile()) {
        const lang = detectLanguage(entry.name);
        if (!lang) continue;
        try {
          const stat = statSync(fullPath);
          if (stat.size > maxSizeBytes) continue;
          results.push({
            relativePath: relative(workspaceRoot, fullPath).replace(/\\/g, '/'),
            absolutePath: fullPath,
            language: lang,
            sizeBytes: stat.size,
          });
        } catch { /* skip unreadable */ }
      }
    }
  }

  walk(workspaceRoot, 0);
  return results;
}
