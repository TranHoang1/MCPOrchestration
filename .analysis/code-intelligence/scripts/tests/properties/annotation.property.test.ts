/**
 * Property-based tests for the Annotation Manager.
 *
 * Property 10: Annotation Format Correctness
 * Property 11: Annotation Preservation on Regeneration
 * Property 12: Deleted Target Annotation Marking
 *
 * **Validates: Requirements 4.3, 4.4, 4.5**
 */

import { describe, it, expect, afterEach } from "vitest";
import * as fc from "fast-check";
import * as fs from "node:fs";
import * as path from "node:path";
import * as os from "node:os";
import {
  addAnnotation,
  preserveAnnotations,
  readAnnotations,
} from "../../src/annotation-manager.js";
import { generateModuleAnalysis } from "../../src/analysis-generator.js";
import type { AnnotationRow, ModuleData } from "../../src/types.js";

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
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "annotation-prop-"));
  tempDirs.push(dir);
  return dir;
}

// ---------------------------------------------------------------------------
// Arbitraries
// ---------------------------------------------------------------------------

/** Safe identifier: starts with a letter, alphanumeric only. */
const identifierArb = fc.stringMatching(/^[a-z][a-z0-9]{1,12}$/);

/** Class-like name: starts with uppercase. */
const classNameArb = fc.stringMatching(/^[A-Z][a-zA-Z]{1,12}$/);

/** Valid annotation types. */
const annotationTypeArb = fc.constantFrom(
  "requirement-link",
  "design-decision",
  "implementation-note",
  "known-issue",
  "todo",
);

/** Author agent names. */
const authorAgentArb = fc.constantFrom(
  "SA_Agent",
  "DEV_Agent",
  "QA_Agent",
  "BA_Agent",
  "DevOps_Agent",
);

/**
 * Annotation content: alphanumeric words with spaces.
 * Avoids pipe characters which would break Markdown table parsing.
 */
const contentArb = fc
  .array(fc.stringMatching(/^[a-zA-Z0-9]{1,10}$/), { minLength: 1, maxLength: 8 })
  .map((words) => words.join(" "));

/** Module name for annotation tests. */
const moduleNameArb = identifierArb;

/**
 * Safe ISO timestamp arbitrary.
 * Uses integer-based generation to avoid invalid Date values from fc.date().
 */
const isoTimestampArb = fc
  .integer({
    min: new Date("2024-01-01T00:00:00Z").getTime(),
    max: new Date("2025-12-31T23:59:59Z").getTime(),
  })
  .map((ms) => new Date(ms).toISOString());

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Create a minimal module analysis file in the given output directory
 * so that `addAnnotation()` has a file to append to.
 */
function createMinimalModuleAnalysis(
  moduleName: string,
  outputDir: string,
  existingAnnotations: AnnotationRow[] = [],
): void {
  const minimalModule: ModuleData = {
    name: moduleName,
    path: `modules/${moduleName}`,
    language: "typescript",
    framework: null,
    dependencies: [],
    sourceFileCount: 1,
    packages: [{ name: "src", path: "src", purpose: "Source code" }],
    classes: [
      {
        name: "SampleClass",
        visibility: "public",
        superclass: undefined,
        interfaces: [],
        annotations: [],
      },
    ],
    functions: [
      {
        name: "sampleFunction",
        visibility: "public",
        parameters: [],
        returnType: "void",
        annotations: [],
      },
    ],
    patterns: {
      diStyle: "none",
      errorHandling: "try-catch",
      naming: "unknown",
      logging: "console.log",
      testing: "vitest",
    },
    purpose: "Test module",
  };

  generateModuleAnalysis(minimalModule, existingAnnotations, outputDir);
}

// ---------------------------------------------------------------------------
// Property 10: Annotation Format Correctness
// ---------------------------------------------------------------------------

describe("Feature: code-intelligence, Property 10: Annotation Format Correctness", () => {
  /**
   * **Validates: Requirements 4.3**
   *
   * Generate random annotations (all types, various content lengths),
   * run addAnnotation(), verify the Markdown table row has exactly five
   * columns: Target, Author Agent, Annotation Type, Content, Timestamp.
   * The row is parseable back into its constituent fields.
   */
  it("annotation row has exactly five columns and is parseable back into fields", () => {
    // Suppress stdout from addAnnotation (KB payload JSON)
    const originalWrite = process.stdout.write;

    fc.assert(
      fc.property(
        moduleNameArb,
        classNameArb,
        authorAgentArb,
        annotationTypeArb,
        contentArb,
        (moduleName, target, authorAgent, annotationType, content) => {
          const dir = makeTempDir();

          // Create a module analysis file to annotate
          createMinimalModuleAnalysis(moduleName, dir);

          // Suppress stdout during addAnnotation
          process.stdout.write = (() => true) as typeof process.stdout.write;

          try {
            addAnnotation(
              moduleName,
              target,
              authorAgent,
              annotationType,
              content,
              dir,
            );
          } finally {
            process.stdout.write = originalWrite;
          }

          // Read back the annotations from the file
          const analysisFilePath = path.join(
            dir,
            "modules",
            `${moduleName}.md`,
          );
          const annotations = readAnnotations(analysisFilePath);

          // Find the annotation we just added (last one)
          expect(annotations.length).toBeGreaterThanOrEqual(1);
          const lastAnnotation = annotations[annotations.length - 1];

          // Verify all five fields are present and match input
          expect(lastAnnotation.target).toBe(target);
          expect(lastAnnotation.authorAgent).toBe(authorAgent);
          expect(lastAnnotation.annotationType).toBe(annotationType);
          expect(lastAnnotation.content).toBe(content);

          // Timestamp must be a valid ISO 8601 string
          expect(lastAnnotation.timestamp).toMatch(
            /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/,
          );
          expect(new Date(lastAnnotation.timestamp).getTime()).not.toBeNaN();

          // Verify the raw Markdown row has exactly 5 columns
          const fileContent = fs.readFileSync(analysisFilePath, "utf-8");
          const lines = fileContent.split("\n");

          // Find annotation data rows (after the header and separator)
          const annotationSectionIdx = lines.findIndex((l) =>
            l.trim().startsWith("## Annotations"),
          );
          expect(annotationSectionIdx).toBeGreaterThanOrEqual(0);

          const dataRows: string[] = [];
          let pastSeparator = false;
          for (
            let i = annotationSectionIdx + 1;
            i < lines.length;
            i++
          ) {
            const trimmed = lines[i].trim();
            if (
              trimmed.startsWith("## ") &&
              !trimmed.startsWith("## Annotations")
            )
              break;
            if (/^\|[-\s|]+\|$/.test(trimmed)) {
              pastSeparator = true;
              continue;
            }
            if (trimmed.startsWith("| Target")) continue;
            if (pastSeparator && trimmed.startsWith("|")) {
              dataRows.push(trimmed);
            }
          }

          // The last data row should be our annotation
          expect(dataRows.length).toBeGreaterThanOrEqual(1);
          const lastRow = dataRows[dataRows.length - 1];

          // Parse the row: split by | and filter empty strings
          const cells = lastRow
            .split("|")
            .map((c) => c.trim())
            .filter(Boolean);

          // Exactly 5 columns
          expect(cells.length).toBe(5);
        },
      ),
      { numRuns: 100 },
    );
  });
});

// ---------------------------------------------------------------------------
// Property 11: Annotation Preservation on Regeneration
// ---------------------------------------------------------------------------

describe("Feature: code-intelligence, Property 11: Annotation Preservation on Regeneration", () => {
  /**
   * **Validates: Requirements 4.4**
   *
   * Generate random annotations, regenerate the analysis file due to a
   * code change via preserveAnnotations(), verify all annotations are
   * preserved exactly: same target, same author, same type, same content,
   * same timestamp. No annotation lost or modified.
   */
  it("all annotations are preserved exactly after regeneration", () => {
    /** Arbitrary for a single annotation row with a fixed target from a known set. */
    const annotationRowArb = fc
      .record({
        target: classNameArb,
        authorAgent: authorAgentArb,
        annotationType: annotationTypeArb,
        content: contentArb,
        timestamp: isoTimestampArb,
      });

    fc.assert(
      fc.property(
        moduleNameArb,
        fc.array(annotationRowArb, { minLength: 1, maxLength: 10 }),
        (moduleName, annotations) => {
          const dir = makeTempDir();

          // Deduplicate annotations by target to avoid ambiguity
          const seen = new Set<string>();
          const uniqueAnnotations = annotations.filter((a) => {
            if (seen.has(a.target)) return false;
            seen.add(a.target);
            return true;
          });

          // Create a module analysis file with the annotations
          createMinimalModuleAnalysis(moduleName, dir, uniqueAnnotations);

          const analysisFilePath = path.join(
            dir,
            "modules",
            `${moduleName}.md`,
          );

          // All targets exist in currentTargets so none get marked deleted
          const currentTargets = uniqueAnnotations.map((a) => a.target);

          // Run preserveAnnotations — simulates regeneration
          const preserved = preserveAnnotations(
            analysisFilePath,
            currentTargets,
          );

          // Verify count matches
          expect(preserved.length).toBe(uniqueAnnotations.length);

          // Verify each annotation is preserved exactly
          for (let i = 0; i < uniqueAnnotations.length; i++) {
            const original = uniqueAnnotations[i];
            const result = preserved[i];

            expect(result.target).toBe(original.target);
            expect(result.authorAgent).toBe(original.authorAgent);
            expect(result.annotationType).toBe(original.annotationType);
            expect(result.content).toBe(original.content);
            expect(result.timestamp).toBe(original.timestamp);
          }
        },
      ),
      { numRuns: 100 },
    );
  });

  /**
   * **Validates: Requirements 4.4**
   *
   * After preserveAnnotations() returns, regenerating the module analysis
   * file with the preserved annotations and reading them back yields
   * identical annotations.
   */
  it("annotations survive a full regeneration round-trip", () => {
    const annotationRowArb = fc.record({
      target: classNameArb,
      authorAgent: authorAgentArb,
      annotationType: annotationTypeArb,
      content: contentArb,
      timestamp: isoTimestampArb,
    });

    fc.assert(
      fc.property(
        moduleNameArb,
        fc.array(annotationRowArb, { minLength: 1, maxLength: 8 }),
        (moduleName, annotations) => {
          const dir = makeTempDir();

          // Deduplicate
          const seen = new Set<string>();
          const uniqueAnnotations = annotations.filter((a) => {
            if (seen.has(a.target)) return false;
            seen.add(a.target);
            return true;
          });

          // Step 1: Create initial analysis file with annotations
          createMinimalModuleAnalysis(moduleName, dir, uniqueAnnotations);

          const analysisFilePath = path.join(
            dir,
            "modules",
            `${moduleName}.md`,
          );

          // Step 2: Read and preserve annotations (simulating code change)
          const currentTargets = uniqueAnnotations.map((a) => a.target);
          const preserved = preserveAnnotations(
            analysisFilePath,
            currentTargets,
          );

          // Step 3: Regenerate the analysis file with preserved annotations
          createMinimalModuleAnalysis(moduleName, dir, preserved);

          // Step 4: Read back and verify
          const readBack = readAnnotations(analysisFilePath);

          expect(readBack.length).toBe(uniqueAnnotations.length);

          for (let i = 0; i < uniqueAnnotations.length; i++) {
            expect(readBack[i].target).toBe(uniqueAnnotations[i].target);
            expect(readBack[i].authorAgent).toBe(
              uniqueAnnotations[i].authorAgent,
            );
            expect(readBack[i].annotationType).toBe(
              uniqueAnnotations[i].annotationType,
            );
            expect(readBack[i].content).toBe(uniqueAnnotations[i].content);
            expect(readBack[i].timestamp).toBe(
              uniqueAnnotations[i].timestamp,
            );
          }
        },
      ),
      { numRuns: 100 },
    );
  });
});

// ---------------------------------------------------------------------------
// Property 12: Deleted Target Annotation Marking
// ---------------------------------------------------------------------------

describe("Feature: code-intelligence, Property 12: Deleted Target Annotation Marking", () => {
  /**
   * **Validates: Requirements 4.5**
   *
   * Generate annotations referencing targets, delete random targets from
   * currentTargets, run preserveAnnotations(), verify [DELETED] marking
   * applied without removing annotations. Annotations for existing targets
   * remain unchanged.
   */
  it("deleted targets get [DELETED] marking; existing targets remain unchanged", () => {
    const annotationRowArb = fc.record({
      target: classNameArb,
      authorAgent: authorAgentArb,
      annotationType: annotationTypeArb,
      content: contentArb,
      timestamp: isoTimestampArb,
    });

    /**
     * Generate a set of annotations and a boolean per annotation indicating
     * whether its target should be "deleted" (removed from currentTargets).
     */
    const testCaseArb = fc
      .record({
        moduleName: moduleNameArb,
        entries: fc.array(
          fc.record({
            annotation: annotationRowArb,
            deleteTarget: fc.boolean(),
          }),
          { minLength: 2, maxLength: 10 },
        ),
      })
      .map((tc) => {
        // Deduplicate by target name
        const seen = new Set<string>();
        const uniqueEntries = tc.entries.filter((e) => {
          if (seen.has(e.annotation.target)) return false;
          seen.add(e.annotation.target);
          return true;
        });
        return { ...tc, entries: uniqueEntries };
      })
      .filter((tc) => {
        // Ensure at least one deleted and one kept
        const hasDeleted = tc.entries.some((e) => e.deleteTarget);
        const hasKept = tc.entries.some((e) => !e.deleteTarget);
        return tc.entries.length >= 2 && hasDeleted && hasKept;
      });

    fc.assert(
      fc.property(testCaseArb, ({ moduleName, entries }) => {
        const dir = makeTempDir();

        const annotations = entries.map((e) => e.annotation);

        // Create analysis file with all annotations
        createMinimalModuleAnalysis(moduleName, dir, annotations);

        const analysisFilePath = path.join(
          dir,
          "modules",
          `${moduleName}.md`,
        );

        // Build currentTargets: only include targets NOT marked for deletion
        const currentTargets = entries
          .filter((e) => !e.deleteTarget)
          .map((e) => e.annotation.target);

        // Run preserveAnnotations
        const preserved = preserveAnnotations(
          analysisFilePath,
          currentTargets,
        );

        // Verify: same count — no annotations removed
        expect(preserved.length).toBe(annotations.length);

        // Verify each annotation
        for (let i = 0; i < entries.length; i++) {
          const entry = entries[i];
          const result = preserved[i];
          const original = entry.annotation;

          if (entry.deleteTarget) {
            // Target should be marked as [DELETED]
            expect(result.target).toBe(`[DELETED] ${original.target}`);
          } else {
            // Target should remain unchanged
            expect(result.target).toBe(original.target);
          }

          // All other fields must remain unchanged regardless
          expect(result.authorAgent).toBe(original.authorAgent);
          expect(result.annotationType).toBe(original.annotationType);
          expect(result.content).toBe(original.content);
          expect(result.timestamp).toBe(original.timestamp);
        }
      }),
      { numRuns: 100 },
    );
  });

  /**
   * **Validates: Requirements 4.5**
   *
   * Annotations already marked as [DELETED] should not be double-marked
   * when preserveAnnotations() runs again.
   */
  it("already-deleted annotations are not double-marked", () => {
    const annotationRowArb = fc.record({
      target: classNameArb.map((name) => `[DELETED] ${name}`),
      authorAgent: authorAgentArb,
      annotationType: annotationTypeArb,
      content: contentArb,
      timestamp: isoTimestampArb,
    });

    fc.assert(
      fc.property(
        moduleNameArb,
        fc.array(annotationRowArb, { minLength: 1, maxLength: 5 }),
        (moduleName, annotations) => {
          // Deduplicate
          const seen = new Set<string>();
          const uniqueAnnotations = annotations.filter((a) => {
            if (seen.has(a.target)) return false;
            seen.add(a.target);
            return true;
          });

          const dir = makeTempDir();

          // Create analysis file with already-deleted annotations
          createMinimalModuleAnalysis(moduleName, dir, uniqueAnnotations);

          const analysisFilePath = path.join(
            dir,
            "modules",
            `${moduleName}.md`,
          );

          // Run preserveAnnotations with empty currentTargets
          const preserved = preserveAnnotations(analysisFilePath, []);

          // Verify: same count
          expect(preserved.length).toBe(uniqueAnnotations.length);

          // Verify: targets still have single [DELETED] prefix, not doubled
          for (let i = 0; i < uniqueAnnotations.length; i++) {
            expect(preserved[i].target).toBe(uniqueAnnotations[i].target);
            expect(preserved[i].target).toMatch(/^\[DELETED\] [A-Z]/);
            expect(preserved[i].target).not.toMatch(
              /^\[DELETED\] \[DELETED\]/,
            );
          }
        },
      ),
      { numRuns: 100 },
    );
  });
});
