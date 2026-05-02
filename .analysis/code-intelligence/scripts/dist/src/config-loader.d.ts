/**
 * Configuration Loader for the Code Intelligence System.
 *
 * Reads `.analysis/code-intelligence/index-config.json` and falls back
 * to sensible defaults when the file is missing or contains invalid JSON.
 */
import type { IndexConfig } from "./types.js";
/** Default configuration used when the config file is missing or invalid. */
export declare const DEFAULT_CONFIG: IndexConfig;
/**
 * Load the index configuration from disk.
 *
 * @param configPath - Optional override for the config file location.
 *   Defaults to `.analysis/code-intelligence/index-config.json` resolved
 *   relative to `process.cwd()`.
 * @returns The parsed {@link IndexConfig}, or {@link DEFAULT_CONFIG} when
 *   the file is missing or contains invalid JSON.
 */
export declare function loadConfig(configPath?: string): IndexConfig;
//# sourceMappingURL=config-loader.d.ts.map