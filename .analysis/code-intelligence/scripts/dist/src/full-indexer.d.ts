/**
 * Full Indexer for the Code Intelligence System.
 *
 * Orchestrates a complete re-index of the workspace:
 * 1. Load configuration
 * 2. Auto-detect project type
 * 3. Discover modules
 * 4. Scan and parse all source files
 * 5. Write index metadata
 * 6. Generate analysis files (project-structure.md + per-module .md)
 * 7. Output KB ingestion payloads
 */
import type { IndexResult } from "./types.js";
/**
 * Run a full index of the workspace.
 *
 * Scans all source files across all modules, rebuilds the entire Code Index
 * from scratch, generates all Analysis Files, and outputs structured data
 * for KB ingestion.
 *
 * @param rootDir    - Absolute or relative path to the project root.
 *                     Defaults to `process.cwd()`.
 * @param configPath - Optional override for the config file location.
 * @returns An {@link IndexResult} summarising the indexing run.
 */
export declare function runFullIndex(rootDir?: string, configPath?: string): IndexResult;
//# sourceMappingURL=full-indexer.d.ts.map