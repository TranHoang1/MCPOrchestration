"""MCP tool handlers for Python bridge code intelligence.
Provides: code_search, code_symbols, code_context, code_modules, code_index_status.
"""

import json
from .database import DatabaseManager


class CodeIntelTools:
    """Handles code intelligence MCP tool calls."""

    def __init__(self, db: DatabaseManager):
        self._db = db
        self.status = 'idle'
        self.progress = 0

    def handle_code_search(self, args: dict) -> dict:
        query = args.get('query', '')
        if not query:
            return self._error('Missing query parameter')
        language = args.get('language')
        limit = min(max(args.get('limit', 20), 1), 100)

        sql = """SELECT s.name, s.kind, s.signature, s.line_start, f.path, m.name as module_name
            FROM symbols_fts fts
            JOIN symbols s ON fts.rowid = s.id
            JOIN files f ON s.file_id = f.id
            LEFT JOIN modules m ON f.module_id = m.id
            WHERE symbols_fts MATCH ?"""
        params = [query]
        if language:
            sql += " AND f.language = ?"
            params.append(language)
        sql += f" ORDER BY rank LIMIT {limit}"

        rows = self._db.fetchall(sql, tuple(params))
        results = [
            {'file': r[4], 'symbol': r[0], 'kind': r[1], 'signature': r[2],
             'line': r[3], 'module': r[5], 'relevance': 1.0}
            for r in rows
        ]
        return self._ok({'results': results, 'total_matches': len(results)})

    def handle_code_symbols(self, args: dict) -> dict:
        file_path = args.get('file_path', '')
        if not file_path:
            return self._error('Missing file_path parameter')

        rows = self._db.fetchall(
            """SELECT s.name, s.kind, s.signature, s.line_start, s.line_end, s.visibility
               FROM symbols s JOIN files f ON s.file_id = f.id
               WHERE f.path = ? ORDER BY s.line_start""",
            (file_path,)
        )
        if not rows:
            return self._error('FILE_NOT_FOUND: File not in index')

        symbols = [
            {'name': r[0], 'kind': r[1], 'signature': r[2],
             'line_start': r[3], 'line_end': r[4], 'visibility': r[5]}
            for r in rows
        ]
        return self._ok({'file': file_path, 'symbols': symbols, 'symbol_count': len(symbols)})

    def handle_code_context(self, args: dict) -> dict:
        query = args.get('query', '')
        if not query:
            return self._error('Missing query parameter')
        top_k = min(max(args.get('top_k', 5), 1), 50)

        rows = self._db.fetchall(
            """SELECT s.name, s.signature, f.path FROM symbols_fts fts
               JOIN symbols s ON fts.rowid = s.id JOIN files f ON s.file_id = f.id
               WHERE symbols_fts MATCH ? ORDER BY rank LIMIT ?""",
            (query, top_k)
        )
        results = [
            {'file': r[2], 'summary': r[1], 'symbols': [r[0]],
             'relevance': 1.0, 'search_method': 'fts5'}
            for r in rows
        ]
        return self._ok({'results': results, 'search_method': 'fts5'})

    def handle_code_modules(self, args: dict) -> dict:
        rows = self._db.fetchall(
            """SELECT m.name, m.path, m.summary, COUNT(DISTINCT f.id), COUNT(s.id)
               FROM modules m LEFT JOIN files f ON f.module_id = m.id
               LEFT JOIN symbols s ON s.file_id = f.id GROUP BY m.id ORDER BY m.name"""
        )
        modules = [
            {'name': r[0], 'path': r[1], 'summary': r[2],
             'file_count': r[3], 'symbol_count': r[4]}
            for r in rows
        ]
        return self._ok({'modules': modules, 'total_modules': len(modules)})

    def handle_code_index_status(self, args: dict) -> dict:
        files = self._db.fetchone('SELECT COUNT(*) FROM files')[0]
        symbols = self._db.fetchone('SELECT COUNT(*) FROM symbols')[0]
        modules = self._db.fetchone('SELECT COUNT(*) FROM modules')[0]
        last = self._db.fetchone('SELECT MAX(last_indexed) FROM files')[0]

        return self._ok({
            'status': self.status, 'files_indexed': files,
            'symbols_indexed': symbols, 'modules_detected': modules,
            'last_indexed': last, 'indexing_progress': self.progress,
            'layers': {'fts5': True, 'embeddings': False, 'summaries': False},
            'db_size_mb': 0.0,
        })

    @staticmethod
    def _ok(data: dict) -> dict:
        return {'content': [{'type': 'text', 'text': json.dumps(data)}]}

    @staticmethod
    def _error(msg: str) -> dict:
        return {'content': [{'type': 'text', 'text': msg}], 'isError': True}
