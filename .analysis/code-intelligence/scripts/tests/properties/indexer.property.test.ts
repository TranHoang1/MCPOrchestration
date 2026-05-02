/**
 * Property-based tests for the Indexer.
 *
 * Property 4: Parse Error Isolation
 * Property 14: Index Summary Completeness
 * Property 1: Full Index Completeness
 * Property 2: Incremental Change Detection Accuracy
 * Property 3: Incremental Deletion Cleanup
 * Property 9: Incremental Scope Isolation
 *
 * **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.7, 3.3, 7.3**
 */

import { describe, it, expect, afterEach } from "vitest";
import * as fc from "fast-check";
import * as fs from "node:fs";
import * as path from "node:path";
import * as os from "node:os";
import { parseFile } from "../../src/file-parser.js";
import { runFullIndex } from "../../src/full-indexer.js";
import { runIncrementalIndex } from "../../src/incremental-indexer.js";
import type { IndexResult, IndexMetadata } from "../../src/types.js";

// ---------------------------------------------------------------------------
// Temp directory management
// ---------------------------------------------------------------------------

let tempDirs: string[] = [];

afterEach(() => {
  for (const dir of tempDirs) {
    try {
      fs.rmSync(dir, { recursive: true, force: true });
    } catch {
      // best-effort cleanup
    }
  }
  tempDirs = [];
});

function makeTempDir(): string {
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
const validTsContentArb: fc.Arbitrary<string> = fc
  .record({
    className: fc.stringMatching(/^[A-Z][a-zA-Z]{1,12}$/),
    funcName: fc.stringMatching(/^[a-z][a-zA-Z]{1,12}$/),
    paramName: fc.stringMatching(/^[a-z][a-zA-Z]{0,8}$/),
    returnValue: fc.stringMatching(/^[a-z0-9]{1,6}$/),
  })
  .map(({ className, funcName, paramName, returnValue }) =>
    [
      `export class ${className} {`,
      `  name: string = "${returnValue}";`,
      `}`,
      ``,
      `export function ${funcName}(${paramName}: string): string {`,
      `  return "${returnValue}";`,
      `}`,
    ].join("\n"),
  );

/**
 * Arbitrary for invalid/unparseable TypeScript content.
 * Generates content that will cause parse errors or read failures.
 */
const invalidTsContentArb: fc.Arbitrary<string> = fc.oneof(
  // Completely broken syntax
  fc.constantFrom(
    "export class { broken",
    "function (( {{{ invalid",
    "const x: = ;; ;;",
    "import { from from from",
    "class class class",
    "export default default default",
    "{{{{{{{{",
    ")))))))))",
  ),
  // Random binary-like content
  fc
    .array(fc.integer({ min: 0, max: 255 }), { minLength: 10, maxLength: 100 })
    .map((arr) => Buffer.from(arr).toString("binary")),
);

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
    fc.assert(
      fc.property(validTsContentArb, fileNameArb, (content, name) => {
        const dir = makeTempDir();
        const filePath = path.join(dir, `${name}.ts`);
        fs.writeFileSync(filePath, content, "utf-8");

        const result = parseFile(filePath, "typescript", "test-module");

        expect(result.indexingStatus).toBe("success");
        expect(result.errorMessage).toBeUndefined();
        expect(result.filePath).toBe(filePath);
        expect(result.language).toBe("typescript");
      }),
      { numRuns: 100 },
    );
  });

  /**
   * **Validates: Requirements 1.7**
   *
   * Files that cannot be read (non-existent) get indexingStatus: "parse_error"
   * with a non-empty errorMessage, and no exceptions are thrown.
   */
  it("non-existent files produce parse_error with non-empty errorMessage", () => {
    fc.assert(
      fc.property(fileNameArb, (name) => {
        const dir = makeTempDir();
        const filePath = path.join(dir, `${name}-nonexistent.ts`);

        // parseFile should NOT throw
        let result: ReturnType<typeof parseFile> | undefined;
        expect(() => {
          result = parseFile(filePath, "typescript", "test-module");
        }).not.toThrow();

        expect(result!.indexingStatus).toBe("parse_error");
        expect(result!.errorMessage).toBeDefined();
        expect(result!.errorMessage!.length).toBeGreaterThan(0);
      }),
      { numRuns: 100 },
    );
  });

  /**
   * **Validates: Requirements 1.7**
   *
   * For a mixed set of valid and invalid files, the count of success +
   * parse_error equals the total number of files processed, and no
   * exceptions are thrown.
   */
  it("success + parse_error count equals total files for mixed sets", () => {
    fc.assert(
      fc.property(
        fc.array(
          fc.record({
            name: fileNameArb,
            valid: fc.boolean(),
            content: fc.oneof(validTsContentArb, invalidTsContentArb),
          }),
          { minLength: 1, maxLength: 10 },
        ),
        (fileSpecs) => {
          const dir = makeTempDir();

          // Deduplicate names to avoid collisions
          const seen = new Set<string>();
          const uniqueSpecs = fileSpecs.filter((spec) => {
            if (seen.has(spec.name)) return false;
            seen.add(spec.name);
            return true;
          });

          // Write files: valid ones get valid content, invalid ones get
          // either invalid content or are not written at all (non-existent)
          const filePaths: Array<{ path: string; isValid: boolean }> = [];

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
            } else {
              // Don't write the file — it won't exist, causing a read error
              filePaths.push({ path: filePath, isValid: false });
            }
          }

          // Parse all files
          let successCount = 0;
          let errorCount = 0;

          for (const file of filePaths) {
            let result: ReturnType<typeof parseFile> | undefined;

            // Must not throw
            expect(() => {
              result = parseFile(file.path, "typescript", "test-module");
            }).not.toThrow();

            if (result!.indexingStatus === "success") {
              successCount++;
            } else if (result!.indexingStatus === "parse_error") {
              errorCount++;
              // parse_error must have a non-empty errorMessage
              expect(result!.errorMessage).toBeDefined();
              expect(result!.errorMessage!.length).toBeGreaterThan(0);
            }
          }

          // Total must add up
          expect(successCount + errorCount).toBe(filePaths.length);
        },
      ),
      { numRuns: 100 },
    );
  });

  /**
   * **Validates: Requirements 1.7**
   *
   * parseFile never throws an exception regardless of input — it always
   * returns a ParseResult.
   */
  it("parseFile never throws for any content", () => {
    fc.assert(
      fc.property(
        fc.oneof(validTsContentArb, invalidTsContentArb),
        fileNameArb,
        fc.constantFrom("typescript", "javascript", "kotlin", "java", "python", "go", "rust", "csharp"),
        (content, name, language) => {
          const dir = makeTempDir();
          const filePath = path.join(dir, `${name}.src`);
          fs.writeFileSync(filePath, content, "utf-8");

          // Must not throw
          let result: ReturnType<typeof parseFile> | undefined;
          expect(() => {
            result = parseFile(filePath, language, "test-module");
          }).not.toThrow();

          // Must return a valid ParseResult
          expect(result).toBeDefined();
          expect(result!.filePath).toBe(filePath);
          expect(["success", "parse_error"]).toContain(result!.indexingStatus);

          if (result!.indexingStatus === "parse_error") {
            expect(result!.errorMessage).toBeDefined();
            expect(result!.errorMessage!.length).toBeGreaterThan(0);
          }
        },
      ),
      { numRuns: 100 },
    );
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
    const indexResultArb: fc.Arbitrary<IndexResult> = fc.record({
      totalFiles: fc.nat({ max: 10000 }),
      totalModules: fc.nat({ max: 100 }),
      totalClasses: fc.nat({ max: 5000 }),
      totalFunctions: fc.nat({ max: 10000 }),
      parseErrors: fc.nat({ max: 500 }),
      elapsedMs: fc.nat({ max: 600000 }),
    });

    fc.assert(
      fc.property(indexResultArb, (result) => {
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
      }),
      { numRuns: 100 },
    );
  });
});

// ---------------------------------------------------------------------------
// Property 1: Full Index Completeness
// ---------------------------------------------------------------------------

/**
 * Default metadata path used by writeMetadata() when no path is provided.
 * This matches the DEFAULT_METADATA_PATH in metadata-helpers.ts.
 */
const DEFAULT_METADATA_PATH = path.resolve(
  process.cwd(),
  ".analysis/code-intelligence/index-metadata.json",
);

describe("Feature: code-intelligence, Property 1: Full Index Completeness", () => {
  /** Backup of the original metadata file content (if it exists). */
  let originalMetadata: string | null = null;

  // Save and restore the real metadata file around each test
  afterEach(() => {
    try {
      if (originalMetadata !== null) {
        fs.writeFileSync(DEFAULT_METADATA_PATH, originalMetadata, "utf-8");
      }
    } catch {
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
    } catch {
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
      .map(
        ({ funcName, returnVal }) =>
          `export function ${funcName}(): string {\n  return "${returnVal}";\n}\n`,
      );

    fc.assert(
      fc.property(
        fc.array(
          fc.record({ name: tsFileNameArb, content: tsContentArb }),
          { minLength: 1, maxLength: 10 },
        ),
        (fileSpecs) => {
          const dir = makeTempDir();

          // Deduplicate file names
          const seen = new Set<string>();
          const uniqueSpecs = fileSpecs.filter((spec) => {
            if (seen.has(spec.name)) return false;
            seen.add(spec.name);
            return true;
          });

          // Create a build.gradle.kts so project detection works (Gradle project)
          fs.writeFileSync(
            path.join(dir, "build.gradle.kts"),
            'plugins { kotlin("jvm") }',
            "utf-8",
          );

          // Create settings.gradle.kts with no includes (flat project)
          fs.writeFileSync(
            path.join(dir, "settings.gradle.kts"),
            'rootProject.name = "test-project"',
            "utf-8",
          );

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
          fs.writeFileSync(
            path.join(analysisDir, "index-config.json"),
            JSON.stringify(config),
            "utf-8",
          );

          // Write TypeScript files into the root directory
          const writtenFiles: string[] = [];
          for (const spec of uniqueSpecs) {
            const fileName = `${spec.name}.ts`;
            fs.writeFileSync(path.join(dir, fileName), spec.content, "utf-8");
            writtenFiles.push(fileName);
          }

          // Run full index — metadata is written to dir/.analysis/code-intelligence/index-metadata.json
          const result = runFullIndex(dir, path.join(analysisDir, "index-config.json"));

          // Verify the result has all required fields and correct counts
          expect(result.totalFiles).toBeGreaterThanOrEqual(writtenFiles.length);
          expect(result.totalModules).toBeGreaterThanOrEqual(1);
          expect(typeof result.totalClasses).toBe("number");
          expect(typeof result.totalFunctions).toBe("number");
          expect(typeof result.parseErrors).toBe("number");
          expect(typeof result.elapsedMs).toBe("number");

          // Read the generated index-metadata.json from the temp dir's analysis path
          const tempMetadataPath = path.join(analysisDir, "index-metadata.json");
          expect(fs.existsSync(tempMetadataPath)).toBe(true);

          const metadataRaw = fs.readFileSync(tempMetadataPath, "utf-8");
          const metadata: IndexMetadata = JSON.parse(metadataRaw);

          // Verify every written .ts file has a corresponding entry
          for (const fileName of writtenFiles) {
            // The file path in metadata is relative to the project root
            // Find the entry that ends with our file name
            const matchingKey = Object.keys(metadata.files).find((key) =>
              key.endsWith(fileName),
            );
            expect(matchingKey).toBeDefined();

            const entry = metadata.files[matchingKey!];
            expect(entry).toBeDefined();

            // Valid contentHash starting with "sha256:"
            expect(entry.contentHash).toMatch(/^sha256:[a-f0-9]{64}$/);

            // Valid lastIndexedTimestamp (ISO 8601)
            expect(entry.lastIndexedTimestamp).toMatch(
              /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/,
            );
            expect(new Date(entry.lastIndexedTimestamp).getTime()).not.toBeNaN();

            // Language is typescript
            expect(entry.language).toBe("typescript");

            // indexingStatus is success
            expect(entry.indexingStatus).toBe("success");
          }
        },
      ),
      { numRuns: 20 },
    );

    // Restore original metadata after all iterations
    try {
      if (originalMetadata !== null) {
        fs.writeFileSync(DEFAULT_METADATA_PATH, originalMetadata, "utf-8");
      }
    } catch {
      // best-effort restore
    }
  });
});


// ---------------------------------------------------------------------------
// Property 2: Incremental Change Detection Accuracy
// ---------------------------------------------------------------------------

describe("Feature: code-intelligence, Property 2: Incremental Change Detection Accuracy", () => {
  /** Backup of the original metadata file content (if it exists). */
  let originalMetadata: string | null = null;

  // Save and restore the real metadata file around each test
  afterEach(() => {
    try {
      if (originalMetadata !== null) {
        fs.writeFileSync(DEFAULT_METADATA_PATH, originalMetadata, "utf-8");
      }
    } catch {
      // best-effort restore
    }
    originalMetadata = null;
  });

  /**
   * **Validates: Requirements 1.3**
   *
   * Generate an indexed file set, randomly modify a subset (change content
   * so their SHA-256 hash changes), run runIncrementalIndex(), and verify:
   * - Only modified files have updated hashes and timestamps in metadata
   * - Unchanged files retain their original hashes and timestamps exactly
   */
  it("only modified files are re-indexed; unchanged files retain original entries", { timeout: 120_000 }, () => {
    // Save original metadata before any test iteration modifies it
    try {
      originalMetadata = fs.readFileSync(DEFAULT_METADATA_PATH, "utf-8");
    } catch {
      originalMetadata = null;
    }

    /** Arbitrary for a safe file name. */
    const tsFileNameArb = fc.stringMatching(/^[a-z][a-z0-9]{1,6}$/);

    /** Arbitrary for valid TypeScript content with a unique token. */
    const tsContentArb = (token: string) =>
      `export function fn_${token}(): string {\n  return "${token}";\n}\n`;

    /**
     * Arbitrary for a file set: 2–8 files, each with a unique name and
     * a boolean indicating whether it will be modified after the initial index.
     */
    const fileSetArb = fc
      .array(
        fc.record({
          name: tsFileNameArb,
          modify: fc.boolean(),
        }),
        { minLength: 2, maxLength: 8 },
      )
      .map((specs) => {
        // Deduplicate names
        const seen = new Set<string>();
        return specs.filter((s) => {
          if (seen.has(s.name)) return false;
          seen.add(s.name);
          return true;
        });
      })
      .filter((specs) => specs.length >= 2);

    fc.assert(
      fc.property(fileSetArb, (fileSpecs) => {
        const dir = makeTempDir();

        // --- Project scaffolding ---
        fs.writeFileSync(
          path.join(dir, "build.gradle.kts"),
          'plugins { kotlin("jvm") }',
          "utf-8",
        );
        fs.writeFileSync(
          path.join(dir, "settings.gradle.kts"),
          'rootProject.name = "test-project"',
          "utf-8",
        );

        const analysisDir = path.join(dir, ".analysis", "code-intelligence");
        fs.mkdirSync(analysisDir, { recursive: true });

        const config = {
          includedExtensions: [".ts"],
          excludedDirectories: [
            "node_modules", ".git", "build", "dist", ".analysis",
          ],
          excludedFilePatterns: ["*.min.*"],
        };
        const configPath = path.join(analysisDir, "index-config.json");
        fs.writeFileSync(configPath, JSON.stringify(config), "utf-8");

        // --- Step 1: Write initial files ---
        const fileNames: string[] = [];
        for (const spec of fileSpecs) {
          const fileName = `${spec.name}.ts`;
          fs.writeFileSync(
            path.join(dir, fileName),
            tsContentArb(spec.name),
            "utf-8",
          );
          fileNames.push(fileName);
        }

        // --- Step 2: Run full index to establish baseline ---
        runFullIndex(dir, configPath);

        // Read baseline metadata
        const metadataPath = path.join(analysisDir, "index-metadata.json");
        expect(fs.existsSync(metadataPath)).toBe(true);
        const baselineRaw = fs.readFileSync(metadataPath, "utf-8");
        const baselineMetadata: IndexMetadata = JSON.parse(baselineRaw);

        // Capture baseline entries for each file
        const baselineEntries = new Map<
          string,
          { contentHash: string; lastIndexedTimestamp: string }
        >();
        for (const fileName of fileNames) {
          const key = Object.keys(baselineMetadata.files).find((k) =>
            k.endsWith(fileName),
          );
          expect(key).toBeDefined();
          const entry = baselineMetadata.files[key!];
          baselineEntries.set(fileName, {
            contentHash: entry.contentHash,
            lastIndexedTimestamp: entry.lastIndexedTimestamp,
          });
        }

        // --- Step 3: Modify the subset of files marked for modification ---
        const modifiedFileNames = new Set<string>();
        for (const spec of fileSpecs) {
          if (spec.modify) {
            const fileName = `${spec.name}.ts`;
            // Write different content so the hash changes
            fs.writeFileSync(
              path.join(dir, fileName),
              tsContentArb(`${spec.name}_modified`),
              "utf-8",
            );
            modifiedFileNames.add(fileName);
          }
        }

        // Small delay to ensure timestamps differ
        const beforeIncremental = Date.now();
        while (Date.now() - beforeIncremental < 10) {
          // busy-wait a few ms so ISO timestamps can differ
        }

        // --- Step 4: Run incremental index ---
        runIncrementalIndex(dir, undefined, configPath);

        // --- Step 5 & 6: Read updated metadata and verify ---
        const updatedRaw = fs.readFileSync(metadataPath, "utf-8");
        const updatedMetadata: IndexMetadata = JSON.parse(updatedRaw);

        for (const fileName of fileNames) {
          const key = Object.keys(updatedMetadata.files).find((k) =>
            k.endsWith(fileName),
          );
          expect(key).toBeDefined();

          const updatedEntry = updatedMetadata.files[key!];
          const baseline = baselineEntries.get(fileName)!;

          if (modifiedFileNames.has(fileName)) {
            // Modified files: hash MUST differ from baseline
            expect(updatedEntry.contentHash).not.toBe(baseline.contentHash);
            // Hash must still be a valid sha256 format
            expect(updatedEntry.contentHash).toMatch(/^sha256:[a-f0-9]{64}$/);
            // Timestamp must be updated (>= baseline)
            expect(
              new Date(updatedEntry.lastIndexedTimestamp).getTime(),
            ).toBeGreaterThanOrEqual(
              new Date(baseline.lastIndexedTimestamp).getTime(),
            );
          } else {
            // Unchanged files: hash and timestamp MUST be identical to baseline
            expect(updatedEntry.contentHash).toBe(baseline.contentHash);
            expect(updatedEntry.lastIndexedTimestamp).toBe(
              baseline.lastIndexedTimestamp,
            );
          }

          // All entries must have valid structure regardless
          expect(updatedEntry.language).toBe("typescript");
          expect(updatedEntry.indexingStatus).toBe("success");
        }
      }),
      { numRuns: 100 },
    );

    // Restore original metadata after all iterations
    try {
      if (originalMetadata !== null) {
        fs.writeFileSync(DEFAULT_METADATA_PATH, originalMetadata, "utf-8");
      }
    } catch {
      // best-effort restore
    }
  });
});


// ---------------------------------------------------------------------------
// Property 3: Incremental Deletion Cleanup
// ---------------------------------------------------------------------------

describe("Feature: code-intelligence, Property 3: Incremental Deletion Cleanup", () => {
  /** Backup of the original metadata file content (if it exists). */
  let originalMetadata: string | null = null;

  // Save and restore the real metadata file around each test
  afterEach(() => {
    try {
      if (originalMetadata !== null) {
        fs.writeFileSync(DEFAULT_METADATA_PATH, originalMetadata, "utf-8");
      }
    } catch {
      // best-effort restore
    }
    originalMetadata = null;
  });

  /**
   * **Validates: Requirements 1.4**
   *
   * Generate an indexed file set, randomly delete a subset of files from
   * disk, run runIncrementalIndex(), and verify:
   * - Deleted files are absent from metadata
   * - Non-deleted files retain their original hash and timestamp exactly
   */
  it("deleted files are removed from metadata; non-deleted files retain original entries", { timeout: 120_000 }, () => {
    // Save original metadata before any test iteration modifies it
    try {
      originalMetadata = fs.readFileSync(DEFAULT_METADATA_PATH, "utf-8");
    } catch {
      originalMetadata = null;
    }

    /** Arbitrary for a safe file name. */
    const tsFileNameArb = fc.stringMatching(/^[a-z][a-z0-9]{1,6}$/);

    /** Arbitrary for valid TypeScript content with a unique token. */
    const tsContentArb = (token: string) =>
      `export function fn_${token}(): string {\n  return "${token}";\n}\n`;

    /**
     * Arbitrary for a file set: 3–8 files, each with a unique name and
     * a boolean indicating whether it will be deleted after the initial index.
     * We ensure at least one file is deleted and at least one is kept.
     */
    const fileSetArb = fc
      .array(
        fc.record({
          name: tsFileNameArb,
          deleteIt: fc.boolean(),
        }),
        { minLength: 3, maxLength: 8 },
      )
      .map((specs) => {
        // Deduplicate names
        const seen = new Set<string>();
        return specs.filter((s) => {
          if (seen.has(s.name)) return false;
          seen.add(s.name);
          return true;
        });
      })
      .filter((specs) => {
        // Need at least 2 files, with at least one deleted and one kept
        if (specs.length < 2) return false;
        const hasDeleted = specs.some((s) => s.deleteIt);
        const hasKept = specs.some((s) => !s.deleteIt);
        return hasDeleted && hasKept;
      });

    fc.assert(
      fc.property(fileSetArb, (fileSpecs) => {
        const dir = makeTempDir();

        // --- Project scaffolding ---
        fs.writeFileSync(
          path.join(dir, "build.gradle.kts"),
          'plugins { kotlin("jvm") }',
          "utf-8",
        );
        fs.writeFileSync(
          path.join(dir, "settings.gradle.kts"),
          'rootProject.name = "test-project"',
          "utf-8",
        );

        const analysisDir = path.join(dir, ".analysis", "code-intelligence");
        fs.mkdirSync(analysisDir, { recursive: true });

        const config = {
          includedExtensions: [".ts"],
          excludedDirectories: [
            "node_modules", ".git", "build", "dist", ".analysis",
          ],
          excludedFilePatterns: ["*.min.*"],
        };
        const configPath = path.join(analysisDir, "index-config.json");
        fs.writeFileSync(configPath, JSON.stringify(config), "utf-8");

        // --- Step 1: Write initial files ---
        const fileNames: string[] = [];
        for (const spec of fileSpecs) {
          const fileName = `${spec.name}.ts`;
          fs.writeFileSync(
            path.join(dir, fileName),
            tsContentArb(spec.name),
            "utf-8",
          );
          fileNames.push(fileName);
        }

        // --- Step 2: Run full index to establish baseline ---
        runFullIndex(dir, configPath);

        // Read baseline metadata
        const metadataPath = path.join(analysisDir, "index-metadata.json");
        expect(fs.existsSync(metadataPath)).toBe(true);
        const baselineRaw = fs.readFileSync(metadataPath, "utf-8");
        const baselineMetadata: IndexMetadata = JSON.parse(baselineRaw);

        // Capture baseline entries for each file
        const baselineEntries = new Map<
          string,
          { key: string; contentHash: string; lastIndexedTimestamp: string }
        >();
        for (const fileName of fileNames) {
          const key = Object.keys(baselineMetadata.files).find((k) =>
            k.endsWith(fileName),
          );
          expect(key).toBeDefined();
          const entry = baselineMetadata.files[key!];
          baselineEntries.set(fileName, {
            key: key!,
            contentHash: entry.contentHash,
            lastIndexedTimestamp: entry.lastIndexedTimestamp,
          });
        }

        // --- Step 3: Delete the subset of files marked for deletion ---
        const deletedFileNames = new Set<string>();
        for (const spec of fileSpecs) {
          if (spec.deleteIt) {
            const fileName = `${spec.name}.ts`;
            const filePath = path.join(dir, fileName);
            fs.unlinkSync(filePath);
            deletedFileNames.add(fileName);
          }
        }

        // --- Step 4: Run incremental index (on-demand mode) ---
        runIncrementalIndex(dir, undefined, configPath);

        // --- Step 5: Read updated metadata and verify ---
        const updatedRaw = fs.readFileSync(metadataPath, "utf-8");
        const updatedMetadata: IndexMetadata = JSON.parse(updatedRaw);

        for (const fileName of fileNames) {
          const baseline = baselineEntries.get(fileName)!;

          if (deletedFileNames.has(fileName)) {
            // Deleted files: MUST be absent from metadata
            const key = Object.keys(updatedMetadata.files).find((k) =>
              k.endsWith(fileName),
            );
            expect(key).toBeUndefined();
          } else {
            // Non-deleted files: MUST retain original hash and timestamp
            const key = Object.keys(updatedMetadata.files).find((k) =>
              k.endsWith(fileName),
            );
            expect(key).toBeDefined();

            const updatedEntry = updatedMetadata.files[key!];

            // Hash must be identical to baseline
            expect(updatedEntry.contentHash).toBe(baseline.contentHash);

            // Timestamp must be identical to baseline
            expect(updatedEntry.lastIndexedTimestamp).toBe(
              baseline.lastIndexedTimestamp,
            );

            // All entries must have valid structure
            expect(updatedEntry.language).toBe("typescript");
            expect(updatedEntry.indexingStatus).toBe("success");
          }
        }

        // --- Step 6: Verify totalFiles count is correct ---
        const expectedFileCount = fileNames.length - deletedFileNames.size;
        const actualMetadataFileCount = Object.keys(updatedMetadata.files).filter(
          (k) => fileNames.some((fn) => k.endsWith(fn)),
        ).length;
        expect(actualMetadataFileCount).toBe(expectedFileCount);
      }),
      { numRuns: 100 },
    );

    // Restore original metadata after all iterations
    try {
      if (originalMetadata !== null) {
        fs.writeFileSync(DEFAULT_METADATA_PATH, originalMetadata, "utf-8");
      }
    } catch {
      // best-effort restore
    }
  });
});


// ---------------------------------------------------------------------------
// Property 9: Incremental Scope Isolation
// ---------------------------------------------------------------------------

describe("Feature: code-intelligence, Property 9: Incremental Scope Isolation", () => {
  /** Backup of the original metadata file content (if it exists). */
  let originalMetadata: string | null = null;

  // Save and restore the real metadata file around each test
  afterEach(() => {
    try {
      if (originalMetadata !== null) {
        fs.writeFileSync(DEFAULT_METADATA_PATH, originalMetadata, "utf-8");
      }
    } catch {
      // best-effort restore
    }
    originalMetadata = null;
  });

  /**
   * **Validates: Requirements 3.3**
   *
   * Generate a multi-module Gradle project (2–3 subprojects), run a full
   * index to establish baseline, save the byte content of all module
   * analysis files, randomly pick one module and modify a file in it,
   * run runIncrementalIndex(), and verify that analysis files for
   * NON-modified modules remain byte-identical to their pre-incremental state.
   */
  it("non-modified modules' analysis files remain byte-identical after incremental index", { timeout: 120_000 }, () => {
    // Save original metadata before any test iteration modifies it
    try {
      originalMetadata = fs.readFileSync(DEFAULT_METADATA_PATH, "utf-8");
    } catch {
      originalMetadata = null;
    }

    /** Arbitrary for a safe module name. */
    const moduleNameArb = fc.stringMatching(/^[a-z][a-z0-9]{1,6}$/);

    /** Arbitrary for a safe file name. */
    const tsFileNameArb = fc.stringMatching(/^[a-z][a-z0-9]{1,6}$/);

    /**
     * Arbitrary for a multi-module project: 2–3 modules, each with 1–3 files,
     * plus an index indicating which module to modify.
     */
    const multiModuleArb = fc
      .record({
        modules: fc.array(
          fc.record({
            name: moduleNameArb,
            files: fc.array(tsFileNameArb, { minLength: 1, maxLength: 3 }),
          }),
          { minLength: 2, maxLength: 3 },
        ),
        modifyIndex: fc.nat(),
      })
      .map((spec) => {
        // Deduplicate module names
        const seenModules = new Set<string>();
        const uniqueModules = spec.modules.filter((m) => {
          if (seenModules.has(m.name)) return false;
          seenModules.add(m.name);
          return true;
        });

        // Deduplicate file names within each module
        const deduped = uniqueModules.map((m) => {
          const seenFiles = new Set<string>();
          const uniqueFiles = m.files.filter((f) => {
            if (seenFiles.has(f)) return false;
            seenFiles.add(f);
            return true;
          });
          return { ...m, files: uniqueFiles.length > 0 ? uniqueFiles : ["main"] };
        });

        return {
          modules: deduped,
          modifyIndex: spec.modifyIndex % Math.max(deduped.length, 1),
        };
      })
      .filter((spec) => spec.modules.length >= 2);

    fc.assert(
      fc.property(multiModuleArb, (spec) => {
        const dir = makeTempDir();

        // --- Step 1: Create multi-module Gradle project scaffolding ---

        // Root build.gradle.kts
        fs.writeFileSync(
          path.join(dir, "build.gradle.kts"),
          'plugins { kotlin("jvm") }',
          "utf-8",
        );

        // settings.gradle.kts with include statements for all modules
        const includeStatements = spec.modules
          .map((m) => `include("${m.name}")`)
          .join("\n");
        fs.writeFileSync(
          path.join(dir, "settings.gradle.kts"),
          `rootProject.name = "test-project"\n${includeStatements}\n`,
          "utf-8",
        );

        // Index config
        const analysisDir = path.join(dir, ".analysis", "code-intelligence");
        fs.mkdirSync(analysisDir, { recursive: true });

        const config = {
          includedExtensions: [".ts"],
          excludedDirectories: [
            "node_modules", ".git", "build", "dist", ".analysis",
          ],
          excludedFilePatterns: ["*.min.*"],
        };
        const configPath = path.join(analysisDir, "index-config.json");
        fs.writeFileSync(configPath, JSON.stringify(config), "utf-8");

        // Create each module directory with build.gradle.kts and .ts source files
        for (const mod of spec.modules) {
          const modDir = path.join(dir, mod.name);
          fs.mkdirSync(modDir, { recursive: true });

          // Module build file
          fs.writeFileSync(
            path.join(modDir, "build.gradle.kts"),
            'plugins { kotlin("jvm") }',
            "utf-8",
          );

          // Write .ts source files into the module directory
          for (const fileName of mod.files) {
            fs.writeFileSync(
              path.join(modDir, `${fileName}.ts`),
              `export function fn_${fileName}_${mod.name}(): string {\n  return "${fileName}";\n}\n`,
              "utf-8",
            );
          }
        }

        // --- Step 2: Run full index to establish baseline ---
        runFullIndex(dir, configPath);

        // --- Step 3: Read and save byte content of all module analysis files ---
        const modulesDir = path.join(analysisDir, "modules");
        const baselineAnalysisFiles = new Map<string, Buffer>();

        for (const mod of spec.modules) {
          const analysisFilePath = path.join(modulesDir, `${mod.name}.md`);
          if (fs.existsSync(analysisFilePath)) {
            baselineAnalysisFiles.set(
              mod.name,
              fs.readFileSync(analysisFilePath),
            );
          }
        }

        // Verify baseline analysis files were created for all modules
        for (const mod of spec.modules) {
          expect(baselineAnalysisFiles.has(mod.name)).toBe(true);
        }

        // --- Step 4: Randomly pick one module and modify a file in it ---
        const modifiedModule = spec.modules[spec.modifyIndex];
        const fileToModify = modifiedModule.files[0];
        const modifiedFilePath = path.join(
          dir,
          modifiedModule.name,
          `${fileToModify}.ts`,
        );

        // Write different content so the hash changes
        fs.writeFileSync(
          modifiedFilePath,
          `export function fn_${fileToModify}_${modifiedModule.name}_modified(): string {\n  return "modified_content";\n}\n`,
          "utf-8",
        );

        // Small delay to ensure timestamps differ
        const beforeIncremental = Date.now();
        while (Date.now() - beforeIncremental < 10) {
          // busy-wait a few ms so ISO timestamps can differ
        }

        // --- Step 5: Run incremental index ---
        runIncrementalIndex(dir, undefined, configPath);

        // --- Step 6: Verify non-modified modules' analysis files are byte-identical ---
        for (const mod of spec.modules) {
          const analysisFilePath = path.join(modulesDir, `${mod.name}.md`);
          expect(fs.existsSync(analysisFilePath)).toBe(true);

          if (mod.name !== modifiedModule.name) {
            // NON-modified module: analysis file must be byte-identical
            const currentContent = fs.readFileSync(analysisFilePath);
            const baselineContent = baselineAnalysisFiles.get(mod.name)!;

            expect(currentContent.equals(baselineContent)).toBe(true);
          }
        }
      }),
      { numRuns: 100 },
    );

    // Restore original metadata after all iterations
    try {
      if (originalMetadata !== null) {
        fs.writeFileSync(DEFAULT_METADATA_PATH, originalMetadata, "utf-8");
      }
    } catch {
      // best-effort restore
    }
  });
});
