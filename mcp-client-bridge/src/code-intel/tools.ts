/**
 * MCP tool handlers for code intelligence in Node.js bridge.
 * Provides: code_search, code_symbols, code_context, code_modules, code_index_status.
 */

import { CodeIntelDb } from './database.js';

export interface ToolResult {
  content: Array<{ type: 'text'; text: string }>;
  isError?: boolean;
}

export function handleCodeSearch(db: CodeIntelDb, args: Record<string, unknown>): ToolResult {
  const query = args.query as string;
  if (!query) return error('Missing query parameter');

  const language = args.language as string | undefined;
  const limit = Math.min(Math.max((args.limit as number) || 20, 1), 100);

  let sql = `SELECT s.name, s.kind, s.signature, s.line_start, f.path, m.name as module_name
    FROM symbols_fts fts
    JOIN symbols s ON fts.rowid = s.id
    JOIN files f ON s.file_id = f.id
    LEFT JOIN modules m ON f.module_id = m.id
    WHERE symbols_fts MATCH ?`;
  if (language) sql += ` AND f.language = '${language}'`;
  sql += ` ORDER BY rank LIMIT ${limit}`;

  const rows = db.all(sql, query);
  const results = rows.map((r: any) => ({
    file: r.path, symbol: r.name, kind: r.kind,
    signature: r.signature, line: r.line_start,
    module: r.module_name, relevance: 1.0,
  }));
  return ok({ results, total_matches: results.length });
}

export function handleCodeSymbols(db: CodeIntelDb, args: Record<string, unknown>): ToolResult {
  const filePath = args.file_path as string;
  if (!filePath) return error('Missing file_path parameter');

  const rows = db.all(
    `SELECT s.name, s.kind, s.signature, s.line_start, s.line_end, s.visibility
     FROM symbols s JOIN files f ON s.file_id = f.id WHERE f.path = ? ORDER BY s.line_start`,
    filePath
  );
  if (rows.length === 0) return error('FILE_NOT_FOUND: File not in index');
  return ok({ file: filePath, symbols: rows, symbol_count: rows.length });
}

export function handleCodeContext(db: CodeIntelDb, args: Record<string, unknown>): ToolResult {
  const query = args.query as string;
  if (!query) return error('Missing query parameter');
  const topK = Math.min(Math.max((args.top_k as number) || 5, 1), 50);

  // FTS5 fallback (Layer 2 not implemented in Node.js bridge yet)
  const rows = db.all(
    `SELECT s.name, s.signature, f.path FROM symbols_fts fts
     JOIN symbols s ON fts.rowid = s.id JOIN files f ON s.file_id = f.id
     WHERE symbols_fts MATCH ? ORDER BY rank LIMIT ?`,
    query, topK
  );
  const results = rows.map((r: any) => ({
    file: r.path, summary: r.signature, symbols: [r.name],
    relevance: 1.0, search_method: 'fts5',
  }));
  return ok({ results, search_method: 'fts5' });
}

export function handleCodeModules(db: CodeIntelDb): ToolResult {
  const rows = db.all(
    `SELECT m.name, m.path, m.summary, COUNT(DISTINCT f.id) as file_count, COUNT(s.id) as symbol_count
     FROM modules m LEFT JOIN files f ON f.module_id = m.id LEFT JOIN symbols s ON s.file_id = f.id
     GROUP BY m.id ORDER BY m.name`
  );
  return ok({ modules: rows, total_modules: rows.length });
}

export function handleCodeIndexStatus(db: CodeIntelDb, status: string, progress: number): ToolResult {
  const files = db.get('SELECT COUNT(*) as c FROM files')?.c ?? 0;
  const symbols = db.get('SELECT COUNT(*) as c FROM symbols')?.c ?? 0;
  const modules = db.get('SELECT COUNT(*) as c FROM modules')?.c ?? 0;
  const lastIndexed = db.get('SELECT MAX(last_indexed) as t FROM files')?.t ?? null;

  return ok({
    status, files_indexed: files, symbols_indexed: symbols,
    modules_detected: modules, last_indexed: lastIndexed,
    indexing_progress: progress,
    layers: { fts5: true, embeddings: false, summaries: false },
    db_size_mb: 0,
  });
}

function ok(data: unknown): ToolResult {
  return { content: [{ type: 'text', text: JSON.stringify(data) }] };
}

function error(msg: string): ToolResult {
  return { content: [{ type: 'text', text: msg }], isError: true };
}
