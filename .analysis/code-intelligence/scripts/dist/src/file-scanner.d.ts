/**
 * File Scanner for the Code Intelligence System.
 *
 * Discovers files matching configured extensions, filters by exclusion
 * rules, computes SHA-256 content hashes, and maps extensions to languages.
 */
import type { IndexConfig, ScannedFile } from "./types.js";
/**
 * Determine whether a file should be indexed based on the configuration.
 *
 * A file is included when **all three** conditions hold:
 * 1. Its extension is in `config.includedExtensions`.
 * 2. Its path does not traverse any directory in `config.excludedDirectories`.
 * 3. Its basename does not match any pattern in `config.excludedFilePatterns`.
 */
export declare function filterFile(filePath: string, config: IndexConfig): boolean;
/**
 * Compute the SHA-256 content hash of a file.
 *
 * @returns A string in the format `"sha256:<hex-digest>"`.
 */
export declare function computeHash(filePath: string): string;
/**
 * Map a file path's extension to a language string.
 *
 * Compound extensions (e.g. `.gradle.kts`) are checked first so they
 * take precedence over their simple suffix (`.kts`).
 */
export declare function mapExtensionToLanguage(filePath: string): string;
/**
 * Recursively scan source directories and return all files that pass
 * the configured filters, along with their content hashes and detected
 * languages.
 *
 * @param config           - Index configuration (extensions, exclusions).
 * @param sourceDirectories - Directories to scan (absolute or relative paths).
 * @param rootDir          - Project root used to compute relative file paths.
 *                           Defaults to `process.cwd()`.
 * @returns An array of {@link ScannedFile} objects.
 */
export declare function scanFiles(config: IndexConfig, sourceDirectories: string[], rootDir?: string): ScannedFile[];
//# sourceMappingURL=file-scanner.d.ts.map