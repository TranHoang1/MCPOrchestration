/**
 * Configuration Loader for the Code Intelligence System.
 *
 * Reads `.analysis/code-intelligence/index-config.json` and falls back
 * to sensible defaults when the file is missing or contains invalid JSON.
 */

import * as fs from "node:fs";
import * as path from "node:path";
import type { IndexConfig } from "./types.js";

/** Default configuration used when the config file is missing or invalid. */
export const DEFAULT_CONFIG: IndexConfig = {
  includedExtensions: [
    ".kt",
    ".java",
    ".ts",
    ".tsx",
    ".js",
    ".jsx",
    ".py",
    ".go",
    ".rs",
    ".cs",
    ".gradle.kts",
    ".gradle",
    ".yml",
    ".yaml",
    ".properties",
    ".xml",
    ".json",
    ".sql",
    ".toml",
    ".cfg",
    ".ini",
  ],
  excludedDirectories: [
    "build",
    "dist",
    "out",
    "target",
    ".gradle",
    ".git",
    "node_modules",
    ".idea",
    ".kiro",
    ".vscode",
    "__pycache__",
    ".mypy_cache",
    "vendor",
    "bin",
    "obj",
  ],
  excludedFilePatterns: [
    "*.generated.*",
    "*.min.*",
    "*.map",
    "*.lock",
    "*.sum",
  ],
};

/**
 * Load the index configuration from disk.
 *
 * @param configPath - Optional override for the config file location.
 *   Defaults to `.analysis/code-intelligence/index-config.json` resolved
 *   relative to `process.cwd()`.
 * @returns The parsed {@link IndexConfig}, or {@link DEFAULT_CONFIG} when
 *   the file is missing or contains invalid JSON.
 */
export function loadConfig(configPath?: string): IndexConfig {
  const resolvedPath =
    configPath ??
    path.resolve(
      process.cwd(),
      ".analysis/code-intelligence/index-config.json",
    );

  let raw: string;
  try {
    raw = fs.readFileSync(resolvedPath, "utf-8");
  } catch (err: unknown) {
    if ((err as NodeJS.ErrnoException).code === "ENOENT") {
      // File doesn't exist — silently return defaults.
      return DEFAULT_CONFIG;
    }
    // Unexpected read error — treat like invalid config.
    process.stderr.write(
      `[Code-Index] ERROR: Invalid config JSON — ${resolvedPath} — ${(err as Error).message}\n`,
    );
    return DEFAULT_CONFIG;
  }

  try {
    return JSON.parse(raw) as IndexConfig;
  } catch (err: unknown) {
    process.stderr.write(
      `[Code-Index] ERROR: Invalid config JSON — ${resolvedPath} — ${(err as Error).message}\n`,
    );
    return DEFAULT_CONFIG;
  }
}
