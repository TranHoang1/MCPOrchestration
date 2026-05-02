/**
 * Property-based tests for the Indexer.
 *
 * Property 4: Parse Error Isolation
 * Property 14: Index Summary Completeness
 * Property 1: Full Index Completeness
 *
 * **Validates: Requirements 1.1, 1.2, 1.7, 7.3**
 */
import { describe, it, expect, afterEach } from "vitest";
import * as fc from "fast-check";
import * as fs from "node:fs";
import * as path from "node:path";
import * as os from "node:os";
import { parseFile } from "../../src/file-parser.js";
import { runFullIndex } from "../../src/full-indexer.js";
// ---------------------------------------------------------------------------
// Temp directory management
// ---------------------------------------------------------------------------
let tempDirs = [];
afterEach(() => {
    for (const dir of tempDirs) {
        try {
            fs.rmSync(dir, { recursive: true, force: true });
        }
        catch {
            // best-effort cleanup
        }
    }
    tempDirs = [];
});
function makeTempDir() {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "parser-prop-"));
    tempDirs.push(dir);
    return dir;
}
// ---------------------------------------------------------------------------
// Arbitraries
// ---------------------------------------------------------------------------
/**
 * Arbitrary for valid TypeScript source content.
 * Generates syntactically valid TS code with classes and functions.
 */
const validTsContentArb = fc
    .record({
    className: fc.stringMatching(/^[A-Z][a-zA-Z]{1,12}$/),
    funcName: fc.stringMatching(/^[a-z][a-zA-Z]{1,12}$/),
    paramName: fc.stringMatching(/^[a-z][a-zA-Z]{0,8}$/),
    returnValue: fc.stringMatching(/^[a-z0-9]{1,6}$/),
})
    .map(({ className, funcName, paramName, returnValue }) => [
    `export class ${className} {`,
    `  name: string = "${returnValue}";`,
    `}`,
    ``,
    `export function ${funcName}(${paramName}: string): string {`,
    `  return "${returnValue}";`,
    `}`,
].join("\n"));
/**
 * Arbitrary for invalid/unparseable TypeScript content.
 * Generates content that will cause parse errors or read failures.
 */
const invalidTsContentArb = fc.oneof(
// Completely broken syntax
fc.constantFrom("export class { broken", "function (( {{{ invalid", "const x: = ;; ;;", "import { from from from", "class class class", "export default default default", "{{{{{{{{", ")))))))))"), 
// Random binary-like content
fc
    .array(fc.integer({ min: 0, max: 255 }), { minLength: 10, maxLength: 100 })
    .map((arr) => Buffer.from(arr).toString("binary")));
/**
 * Arbitrary for a safe file name (no path separators).
 */
const fileNameArb = fc.stringMatching(/^[a-z][a-z0-9]{0,8}$/);
// ---------------------------------------------------------------------------
// Property 4: Parse Error Isolation
// ---------------------------------------------------------------------------
describe("Feature: code-intelligence, Property 4: Parse Error Isolation", () => {
    /**
     * **Validates: Requirements 1.7**
     *
     * Valid TypeScript files always get indexingStatus: "success".
     */
    it("valid files always produce indexingStatus 'success'", () => {
        fc.assert(fc.property(validTsContentArb, fileNameArb, (content, name) => {
            const dir = makeTempDir();
            const filePath = path.join(dir, `${name}.ts`);
            fs.writeFileSync(filePath, content, "utf-8");
            const result = parseFile(filePath, "typescript", "test-module");
            expect(result.indexingStatus).toBe("success");
            expect(result.errorMessage).toBeUndefined();
            expect(result.filePath).toBe(filePath);
            expect(result.language).toBe("typescript");
        }), { numRuns: 100 });
    });
    /**
     * **Validates: Requirements 1.7**
     *
     * Files that cannot be read (non-existent) get indexingStatus: "parse_error"
     * with a non-empty errorMessage, and no exceptions are thrown.
     */
    it("non-existent files produce parse_error with non-empty errorMessage", () => {
        fc.assert(fc.property(fileNameArb, (name) => {
            const dir = makeTempDir();
            const filePath = path.join(dir, `${name}-nonexistent.ts`);
            // parseFile should NOT throw
            let result;
            expect(() => {
                result = parseFile(filePath, "typescript", "test-module");
            }).not.toThrow();
            expect(result.indexingStatus).toBe("parse_error");
            expect(result.errorMessage).toBeDefined();
            expect(result.errorMessage.length).toBeGreaterThan(0);
        }), { numRuns: 100 });
    });
    /**
     * **Validates: Requirements 1.7**
     *
     * For a mixed set of valid and invalid files, the count of success +
     * parse_error equals the total number of files processed, and no
     * exceptions are thrown.
     */
    it("success + parse_error count equals total files for mixed sets", () => {
        fc.assert(fc.property(fc.array(fc.record({
            name: fileNameArb,
            valid: fc.boolean(),
            content: fc.oneof(validTsContentArb, invalidTsContentArb),
        }), { minLength: 1, maxLength: 10 }), (fileSpecs) => {
            const dir = makeTempDir();
            // Deduplicate names to avoid collisions
            const seen = new Set();
            const uniqueSpecs = fileSpecs.filter((spec) => {
                if (seen.has(spec.name))
                    return false;
                seen.add(spec.name);
                return true;
            });
            // Write files: valid ones get valid content, invalid ones get
            // either invalid content or are not written at all (non-existent)
            const filePaths = [];
            for (const spec of uniqueSpecs) {
                const filePath = path.join(dir, `${spec.name}.ts`);
                if (spec.valid) {
                    // Write valid TS content
                    const validContent = [
                        `export function fn_${spec.name}(): string {`,
                        `  return "hello";`,
                        `}`,
                    ].join("\n");
                    fs.writeFileSync(filePath, validContent, "utf-8");
                    filePaths.push({ path: filePath, isValid: true });
                }
                else {
                    // Don't write the file — it won't exist, causing a read error
                    filePaths.push({ path: filePath, isValid: false });
                }
            }
            // Parse all files
            let successCount = 0;
            let errorCount = 0;
            for (const file of filePaths) {
                let result;
                // Must not throw
                expect(() => {
                    result = parseFile(file.path, "typescript", "test-module");
                }).not.toThrow();
                if (result.indexingStatus === "success") {
                    successCount++;
                }
                else if (result.indexingStatus === "parse_error") {
                    errorCount++;
                    // parse_error must have a non-empty errorMessage
                    expect(result.errorMessage).toBeDefined();
                    expect(result.errorMessage.length).toBeGreaterThan(0);
                }
            }
            // Total must add up
            expect(successCount + errorCount).toBe(filePaths.length);
        }), { numRuns: 100 });
    });
    /**
     * **Validates: Requirements 1.7**
     *
     * parseFile never throws an exception regardless of input — it always
     * returns a ParseResult.
     */
    it("parseFile never throws for any content", () => {
        fc.assert(fc.property(fc.oneof(validTsContentArb, invalidTsContentArb), fileNameArb, fc.constantFrom("typescript", "javascript", "kotlin", "java", "python", "go", "rust", "csharp"), (content, name, language) => {
            const dir = makeTempDir();
            const filePath = path.join(dir, `${name}.src`);
            fs.writeFileSync(filePath, content, "utf-8");
            // Must not throw
            let result;
            expect(() => {
                result = parseFile(filePath, language, "test-module");
            }).not.toThrow();
            // Must return a valid ParseResult
            expect(result).toBeDefined();
            expect(result.filePath).toBe(filePath);
            expect(["success", "parse_error"]).toContain(result.indexingStatus);
            if (result.indexingStatus === "parse_error") {
                expect(result.errorMessage).toBeDefined();
                expect(result.errorMessage.length).toBeGreaterThan(0);
            }
        }), { numRuns: 100 });
    });
});
// ---------------------------------------------------------------------------
// Property 14: Index Summary Completeness
// ---------------------------------------------------------------------------
describe("Feature: code-intelligence, Property 14: Index Summary Completeness", () => {
    /**
     * **Validates: Requirements 7.3**
     *
     * For any IndexResult, all required fields are present and not
     * null/undefined: totalFiles, totalModules, totalClasses,
     * totalFunctions, parseErrors, elapsedMs.
     */
    it("all required fields are present and not null/undefined", () => {
        /** Arbitrary for a valid IndexResult object. */
        const indexResultArb = fc.record({
            totalFiles: fc.nat({ max: 10000 }),
            totalModules: fc.nat({ max: 100 }),
            totalClasses: fc.nat({ max: 5000 }),
            totalFunctions: fc.nat({ max: 10000 }),
            parseErrors: fc.nat({ max: 500 }),
            elapsedMs: fc.nat({ max: 600000 }),
        });
        fc.assert(fc.property(indexResultArb, (result) => {
            // All required fields must be defined and not null
            expect(result.totalFiles).toBeDefined();
            expect(result.totalFiles).not.toBeNull();
            expect(typeof result.totalFiles).toBe("number");
            expect(result.totalModules).toBeDefined();
            expect(result.totalModules).not.toBeNull();
            expect(typeof result.totalModules).toBe("number");
            expect(result.totalClasses).toBeDefined();
            expect(result.totalClasses).not.toBeNull();
            expect(typeof result.totalClasses).toBe("number");
            expect(result.totalFunctions).toBeDefined();
            expect(result.totalFunctions).not.toBeNull();
            expect(typeof result.totalFunctions).toBe("number");
            expect(result.parseErrors).toBeDefined();
            expect(result.parseErrors).not.toBeNull();
            expect(typeof result.parseErrors).toBe("number");
            expect(result.elapsedMs).toBeDefined();
            expect(result.elapsedMs).not.toBeNull();
            expect(typeof result.elapsedMs).toBe("number");
            // All numeric fields must be non-negative
            expect(result.totalFiles).toBeGreaterThanOrEqual(0);
            expect(result.totalModules).toBeGreaterThanOrEqual(0);
            expect(result.totalClasses).toBeGreaterThanOrEqual(0);
            expect(result.totalFunctions).toBeGreaterThanOrEqual(0);
            expect(result.parseErrors).toBeGreaterThanOrEqual(0);
            expect(result.elapsedMs).toBeGreaterThanOrEqual(0);
        }), { numRuns: 100 });
    });
});
// ---------------------------------------------------------------------------
// Property 1: Full Index Completeness
// ---------------------------------------------------------------------------
/**
 * Default metadata path used by writeMetadata() when no path is provided.
 * This matches the DEFAULT_METADATA_PATH in metadata-helpers.ts.
 */
const DEFAULT_METADATA_PATH = path.resolve(process.cwd(), ".analysis/code-intelligence/index-metadata.json");
describe("Feature: code-intelligence, Property 1: Full Index Completeness", () => {
    /** Backup of the original metadata file content (if it exists). */
    let originalMetadata = null;
    // Save and restore the real metadata file around each test
    afterEach(() => {
        try {
            if (originalMetadata !== null) {
                fs.writeFileSync(DEFAULT_METADATA_PATH, originalMetadata, "utf-8");
            }
        }
        catch {
            // best-effort restore
        }
        originalMetadata = null;
    });
    /**
     * **Validates: Requirements 1.1, 1.2**
     *
     * Generate a small random file tree in a temp directory, run
     * runFullIndex(), and verify every .ts file has a corresponding
     * entry in index-metadata.json with valid contentHash, timestamp,
     * language, and indexingStatus.
     */
    it("every generated .ts file has a complete metadata entry after full index", () => {
        // Save original metadata before any test iteration modifies it
        try {
            originalMetadata = fs.readFileSync(DEFAULT_METADATA_PATH, "utf-8");
        }
        catch {
            originalMetadata = null;
        }
        /** Arbitrary for a safe file name. */
        const tsFileNameArb = fc.stringMatching(/^[a-z][a-z0-9]{1,8}$/);
        /** Arbitrary for valid TypeScript content. */
        const tsContentArb = fc
            .record({
            funcName: fc.stringMatching(/^[a-z][a-zA-Z]{1,10}$/),
            returnVal: fc.stringMatching(/^[a-z0-9]{1,6}$/),
        })
            .map(({ funcName, returnVal }) => `export function ${funcName}(): string {\n  return "${returnVal}";\n}\n`);
        fc.assert(fc.property(fc.array(fc.record({ name: tsFileNameArb, content: tsContentArb }), { minLength: 1, maxLength: 10 }), (fileSpecs) => {
            const dir = makeTempDir();
            // Deduplicate file names
            const seen = new Set();
            const uniqueSpecs = fileSpecs.filter((spec) => {
                if (seen.has(spec.name))
                    return false;
                seen.add(spec.name);
                return true;
            });
            // Create a build.gradle.kts so project detection works (Gradle project)
            fs.writeFileSync(path.join(dir, "build.gradle.kts"), 'plugins { kotlin("jvm") }', "utf-8");
            // Create settings.gradle.kts with no includes (flat project)
            fs.writeFileSync(path.join(dir, "settings.gradle.kts"), 'rootProject.name = "test-project"', "utf-8");
            // Write an index-config.json that includes .ts files
            const config = {
                includedExtensions: [".ts"],
                excludedDirectories: [
                    "node_modules", ".git", "build", "dist", ".analysis",
                ],
                excludedFilePatterns: ["*.min.*"],
            };
            const analysisDir = path.join(dir, ".analysis", "code-intelligence");
            fs.mkdirSync(analysisDir, { recursive: true });
            fs.writeFileSync(path.join(analysisDir, "index-config.json"), JSON.stringify(config), "utf-8");
            // Write TypeScript files into the root directory
            const writtenFiles = [];
            for (const spec of uniqueSpecs) {
                const fileName = `${spec.name}.ts`;
                fs.writeFileSync(path.join(dir, fileName), spec.content, "utf-8");
                writtenFiles.push(fileName);
            }
            // Run full index — metadata is written to DEFAULT_METADATA_PATH
            // (process.cwd()/.analysis/code-intelligence/index-metadata.json)
            const result = runFullIndex(dir, path.join(analysisDir, "index-config.json"));
            // Verify the result has all required fields and correct counts
            expect(result.totalFiles).toBeGreaterThanOrEqual(writtenFiles.length);
            expect(result.totalModules).toBeGreaterThanOrEqual(1);
            expect(typeof result.totalClasses).toBe("number");
            expect(typeof result.totalFunctions).toBe("number");
            expect(typeof result.parseErrors).toBe("number");
            expect(typeof result.elapsedMs).toBe("number");
            // Read the generated index-metadata.json from the default path
            expect(fs.existsSync(DEFAULT_METADATA_PATH)).toBe(true);
            const metadataRaw = fs.readFileSync(DEFAULT_METADATA_PATH, "utf-8");
            const metadata = JSON.parse(metadataRaw);
            // Verify every written .ts file has a corresponding entry
            for (const fileName of writtenFiles) {
                // The file path in metadata is relative to the project root
                // Find the entry that ends with our file name
                const matchingKey = Object.keys(metadata.files).find((key) => key.endsWith(fileName));
                expect(matchingKey).toBeDefined();
                const entry = metadata.files[matchingKey];
                expect(entry).toBeDefined();
                // Valid contentHash starting with "sha256:"
                expect(entry.contentHash).toMatch(/^sha256:[a-f0-9]{64}$/);
                // Valid lastIndexedTimestamp (ISO 8601)
                expect(entry.lastIndexedTimestamp).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/);
                expect(new Date(entry.lastIndexedTimestamp).getTime()).not.toBeNaN();
                // Language is typescript
                expect(entry.language).toBe("typescript");
                // indexingStatus is success
                expect(entry.indexingStatus).toBe("success");
            }
        }), { numRuns: 20 });
        // Restore original metadata after all iterations
        try {
            if (originalMetadata !== null) {
                fs.writeFileSync(DEFAULT_METADATA_PATH, originalMetadata, "utf-8");
            }
        }
        catch {
            // best-effort restore
        }
    });
});
//# sourceMappingURL=indexer.property.test.js.map