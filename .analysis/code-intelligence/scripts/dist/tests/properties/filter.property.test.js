/**
 * Property-based tests for the File Scanner — File Path Filtering.
 *
 * Property 13: File Path Filtering
 *
 * **Validates: Requirements 5.4, 5.7, 10.1**
 */
import { describe, it, expect } from "vitest";
import * as fc from "fast-check";
import { filterFile } from "../../src/file-scanner.js";
// ---------------------------------------------------------------------------
// Arbitraries
// ---------------------------------------------------------------------------
/**
 * Arbitrary for a simple file extension starting with ".".
 * Generates strings like ".ts", ".java", ".py".
 */
const extensionArb = fc
    .stringMatching(/^[a-z]{1,6}$/)
    .map((s) => `.${s}`);
/**
 * Arbitrary for a directory name segment (no path separators, no dots).
 */
const dirNameArb = fc.stringMatching(/^[a-z][a-z0-9_-]{0,12}$/);
/**
 * Arbitrary for a simple file name (no path separators).
 */
const fileNameArb = fc.stringMatching(/^[a-z][a-z0-9_-]{0,12}$/);
/**
 * Arbitrary for a glob-style file pattern (e.g. "*.min.*", "*.lock").
 */
const filePatternArb = fc.stringMatching(/^\*\.[a-z]{1,8}(\.\*)?$/);
/**
 * Arbitrary for an IndexConfig with non-empty arrays.
 */
const indexConfigArb = fc.record({
    includedExtensions: fc.array(extensionArb, { minLength: 1, maxLength: 8 }),
    excludedDirectories: fc.array(dirNameArb, { minLength: 0, maxLength: 6 }),
    excludedFilePatterns: fc.array(filePatternArb, { minLength: 0, maxLength: 4 }),
});
/**
 * Build a file path from directory segments, a file name, and an extension.
 */
function buildPath(dirs, name, ext) {
    const segments = [...dirs, `${name}${ext}`];
    return segments.join("/");
}
// ---------------------------------------------------------------------------
// Property 13: File Path Filtering
// ---------------------------------------------------------------------------
describe("Feature: code-intelligence, Property 13: File Path Filtering", () => {
    /**
     * **Validates: Requirements 5.4, 5.7, 10.1**
     *
     * Sub-property: Files with excluded extensions are always rejected.
     */
    it("rejects files whose extension is NOT in includedExtensions", () => {
        fc.assert(fc.property(fc.array(dirNameArb, { minLength: 0, maxLength: 4 }), fileNameArb, extensionArb, indexConfigArb, (dirs, name, ext, config) => {
            // Ensure the extension is NOT in the included list
            const excluded = config.includedExtensions.every((inc) => inc.toLowerCase() !== ext.toLowerCase());
            if (!excluded)
                return; // skip — extension happens to be included
            const filePath = buildPath(dirs, name, ext);
            expect(filterFile(filePath, config)).toBe(false);
        }), { numRuns: 100 });
    });
    /**
     * **Validates: Requirements 5.4, 5.7, 10.1**
     *
     * Sub-property: Files in excluded directories are always rejected.
     */
    it("rejects files whose path traverses an excluded directory", () => {
        fc.assert(fc.property(indexConfigArb.filter((c) => c.excludedDirectories.length > 0), fc.array(dirNameArb, { minLength: 0, maxLength: 3 }), fileNameArb, (config, prefixDirs, name) => {
            // Pick a random excluded directory to inject into the path
            const excludedDir = config.excludedDirectories[Math.floor(Math.random() * config.excludedDirectories.length)];
            // Use the first included extension so extension check passes
            const ext = config.includedExtensions[0];
            // Build path with the excluded directory in the middle
            const dirs = [...prefixDirs, excludedDir];
            const filePath = buildPath(dirs, name, ext);
            expect(filterFile(filePath, config)).toBe(false);
        }), { numRuns: 100 });
    });
    /**
     * **Validates: Requirements 5.4, 5.7, 10.1**
     *
     * Sub-property: Files matching excluded patterns are always rejected.
     */
    it("rejects files whose name matches an excluded file pattern", () => {
        fc.assert(fc.property(indexConfigArb.filter((c) => c.excludedFilePatterns.length > 0), fc.array(dirNameArb, { minLength: 0, maxLength: 3 }), fileNameArb, (config, dirs, baseName) => {
            // Pick a pattern and construct a file name that matches it.
            // Patterns are like "*.lock" or "*.min.*"
            const pattern = config.excludedFilePatterns[0];
            // Ensure no excluded directories in path
            const safeDirs = dirs.filter((d) => !config.excludedDirectories.includes(d));
            // Build a file name that matches the pattern.
            // Pattern "*.lock" → "something.lock"
            // Pattern "*.min.*" → "something.min.js"
            const patternSuffix = pattern.replace(/^\*/, "");
            // If pattern ends with ".*", replace trailing * with a concrete extension
            const resolvedSuffix = patternSuffix.endsWith(".*")
                ? patternSuffix.slice(0, -1) + "js"
                : patternSuffix;
            const matchingFileName = `${baseName}${resolvedSuffix}`;
            // Ensure the file extension is in includedExtensions so extension check passes
            const fileExt = "." + matchingFileName.split(".").pop();
            const configWithExt = {
                ...config,
                includedExtensions: [...config.includedExtensions, fileExt],
            };
            const filePath = [...safeDirs, matchingFileName].join("/");
            expect(filterFile(filePath, configWithExt)).toBe(false);
        }), { numRuns: 100 });
    });
    /**
     * **Validates: Requirements 5.4, 5.7, 10.1**
     *
     * Sub-property: Files meeting all three criteria are always accepted.
     * (a) extension in includedExtensions
     * (b) path does not traverse excludedDirectories
     * (c) name does not match excludedFilePatterns
     */
    it("accepts files that satisfy all three inclusion criteria", () => {
        fc.assert(fc.property(indexConfigArb, fc.array(dirNameArb, { minLength: 0, maxLength: 4 }), fileNameArb, (config, dirs, name) => {
            // Use the first included extension
            const ext = config.includedExtensions[0];
            // Filter out any directory segments that are in excludedDirectories
            const safeDirs = dirs.filter((d) => !config.excludedDirectories.includes(d));
            // Ensure the file name does not match any excluded pattern
            const fileName = `${name}${ext}`;
            for (const pattern of config.excludedFilePatterns) {
                const regexStr = pattern
                    .split("*").join("[^/]*")
                    .split(".").join("\\.");
                // Undo double-escaping from the split/join on "."
                const regex = new RegExp(`^${pattern.replace(/\./g, "\\.").replace(/\*/g, "[^/]*")}$`);
                if (regex.test(fileName))
                    return; // skip — name matches a pattern
            }
            const filePath = buildPath(safeDirs, name, ext);
            expect(filterFile(filePath, config)).toBe(true);
        }), { numRuns: 100 });
    });
    /**
     * **Validates: Requirements 5.4, 5.7, 10.1**
     *
     * Combined property: filterFile returns true if and only if ALL three
     * conditions hold simultaneously.
     */
    it("returns true iff extension included AND no excluded dir AND no excluded pattern match", () => {
        fc.assert(fc.property(indexConfigArb, fc.array(dirNameArb, { minLength: 0, maxLength: 4 }), fileNameArb, extensionArb, (config, dirs, name, ext) => {
            const filePath = buildPath(dirs, name, ext);
            const result = filterFile(filePath, config);
            // Condition (a): extension is in includedExtensions
            const extIncluded = config.includedExtensions.some((inc) => inc.toLowerCase() === ext.toLowerCase());
            // Condition (b): path does not traverse any excluded directory
            const segments = filePath.split("/");
            const noExcludedDir = !segments.some((seg) => config.excludedDirectories.includes(seg));
            // Condition (c): basename does not match any excluded pattern
            const basename = segments[segments.length - 1];
            const noPatternMatch = config.excludedFilePatterns.every((pattern) => {
                const regexStr = pattern
                    .replace(/\./g, "\\.")
                    .replace(/\*/g, "[^/]*");
                const regex = new RegExp(`^${regexStr}$`);
                return !regex.test(basename);
            });
            const expected = extIncluded && noExcludedDir && noPatternMatch;
            expect(result).toBe(expected);
        }), { numRuns: 100 });
    });
});
//# sourceMappingURL=filter.property.test.js.map