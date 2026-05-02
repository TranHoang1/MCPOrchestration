/**
 * File Scanner for the Code Intelligence System.
 *
 * Discovers files matching configured extensions, filters by exclusion
 * rules, computes SHA-256 content hashes, and maps extensions to languages.
 */
import * as fs from "node:fs";
import * as path from "node:path";
import * as crypto from "node:crypto";
// ---------------------------------------------------------------------------
// Extension → language mapping
// ---------------------------------------------------------------------------
/**
 * Compound extensions checked first (order matters — `.gradle.kts` before `.kts`).
 */
const COMPOUND_EXTENSION_MAP = [
    { suffix: ".gradle.kts", language: "gradle" },
];
/**
 * Simple (single-dot) extension → language mapping.
 */
const EXTENSION_MAP = {
    ".kt": "kotlin",
    ".java": "java",
    ".ts": "typescript",
    ".tsx": "typescript",
    ".js": "javascript",
    ".jsx": "javascript",
    ".py": "python",
    ".go": "go",
    ".rs": "rust",
    ".cs": "csharp",
    ".gradle": "gradle",
    ".yml": "yaml",
    ".yaml": "yaml",
    ".xml": "xml",
    ".sql": "sql",
    ".json": "json",
    ".properties": "properties",
    ".toml": "config",
    ".cfg": "config",
    ".ini": "config",
};
// ---------------------------------------------------------------------------
// Public functions
// ---------------------------------------------------------------------------
/**
 * Determine whether a file should be indexed based on the configuration.
 *
 * A file is included when **all three** conditions hold:
 * 1. Its extension is in `config.includedExtensions`.
 * 2. Its path does not traverse any directory in `config.excludedDirectories`.
 * 3. Its basename does not match any pattern in `config.excludedFilePatterns`.
 */
export function filterFile(filePath, config) {
    const basename = path.basename(filePath);
    // --- Check (a): extension must be in includedExtensions ---
    const hasIncludedExtension = config.includedExtensions.some((ext) => {
        // Handle compound extensions like ".gradle.kts"
        if (ext.indexOf(".") !== ext.lastIndexOf(".")) {
            return basename.endsWith(ext);
        }
        // Simple extension — compare against path.extname
        return path.extname(filePath).toLowerCase() === ext.toLowerCase();
    });
    if (!hasIncludedExtension) {
        return false;
    }
    // --- Check (b): path must not traverse any excluded directory ---
    const segments = filePath.split(/[/\\]/);
    for (const segment of segments) {
        if (config.excludedDirectories.includes(segment)) {
            return false;
        }
    }
    // --- Check (c): basename must not match any excluded file pattern ---
    for (const pattern of config.excludedFilePatterns) {
        const regex = globToRegex(pattern);
        if (regex.test(basename)) {
            return false;
        }
    }
    return true;
}
/**
 * Compute the SHA-256 content hash of a file.
 *
 * @returns A string in the format `"sha256:<hex-digest>"`.
 */
export function computeHash(filePath) {
    const content = fs.readFileSync(filePath);
    const hash = crypto.createHash("sha256").update(content).digest("hex");
    return `sha256:${hash}`;
}
/**
 * Map a file path's extension to a language string.
 *
 * Compound extensions (e.g. `.gradle.kts`) are checked first so they
 * take precedence over their simple suffix (`.kts`).
 */
export function mapExtensionToLanguage(filePath) {
    const basename = path.basename(filePath).toLowerCase();
    // Check compound extensions first
    for (const { suffix, language } of COMPOUND_EXTENSION_MAP) {
        if (basename.endsWith(suffix)) {
            return language;
        }
    }
    // Fall back to simple extension
    const ext = path.extname(filePath).toLowerCase();
    return EXTENSION_MAP[ext] ?? "unknown";
}
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
export function scanFiles(config, sourceDirectories, rootDir) {
    const resolvedRoot = path.resolve(rootDir ?? process.cwd());
    const results = [];
    for (const dir of sourceDirectories) {
        const resolvedDir = path.resolve(resolvedRoot, dir);
        walkDirectory(resolvedDir, resolvedRoot, config, results);
    }
    return results;
}
// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------
/**
 * Recursively walk a directory, collecting matching files into `results`.
 */
function walkDirectory(dir, rootDir, config, results) {
    let entries;
    try {
        entries = fs.readdirSync(dir, { withFileTypes: true });
    }
    catch {
        // Permission denied or other read error — skip silently.
        return;
    }
    for (const entry of entries) {
        const fullPath = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            // Skip excluded directories early for performance
            if (config.excludedDirectories.includes(entry.name)) {
                continue;
            }
            walkDirectory(fullPath, rootDir, config, results);
        }
        else if (entry.isFile()) {
            const relativePath = path.relative(rootDir, fullPath);
            if (!filterFile(relativePath, config)) {
                continue;
            }
            try {
                const contentHash = computeHash(fullPath);
                const language = mapExtensionToLanguage(relativePath);
                results.push({ filePath: relativePath, contentHash, language });
            }
            catch {
                // Read error (permission denied, broken symlink, etc.) — skip file.
            }
        }
    }
}
/**
 * Convert a simple glob pattern to a regular expression.
 *
 * Supported wildcards:
 * - `*` → `[^/]*` (match any characters except path separator)
 * - `.` → `\\.`   (literal dot)
 *
 * The resulting regex is anchored to match the full string.
 */
function globToRegex(pattern) {
    let regexStr = "";
    for (const ch of pattern) {
        switch (ch) {
            case "*":
                regexStr += "[^/]*";
                break;
            case ".":
                regexStr += "\\.";
                break;
            default:
                regexStr += ch;
                break;
        }
    }
    return new RegExp(`^${regexStr}$`);
}
//# sourceMappingURL=file-scanner.js.map