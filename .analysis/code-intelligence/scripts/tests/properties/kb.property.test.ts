/**
 * Property-based tests for Knowledge Base ingestion helpers.
 *
 * Property 5: KB Tagging Correctness
 * Property 6: KB Deduplication on Update
 *
 * **Validates: Requirements 2.2, 2.4, 4.6, 9.3**
 */

import { describe, it, expect } from "vitest";
import * as fc from "fast-check";
import {
  generateModuleKbPayload,
  generateAnnotationKbPayload,
  generateSchemaKbPayload,
} from "../../src/kb-helpers.js";
import type { AnnotationRow, ModuleData } from "../../src/types.js";

// ---------------------------------------------------------------------------
// Arbitraries
// ---------------------------------------------------------------------------

/** Safe identifier: lowercase letters + digits, starts with a letter. */
const identifierArb = fc.stringMatching(/^[a-z][a-z0-9]{1,12}$/);

/** Language names used in the system. */
const languageArb = fc.constantFrom(
  "kotlin",
  "java",
  "typescript",
  "javascript",
  "python",
  "go",
  "rust",
  "csharp",
  "yaml",
  "xml",
  "sql",
  "json",
  "properties",
);

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

/** Class-like name: starts with uppercase. */
const classNameArb = fc.stringMatching(/^[A-Z][a-zA-Z]{1,12}$/);

/** Simple content string (no pipe characters). */
const contentArb = fc
  .array(fc.stringMatching(/^[a-zA-Z0-9]{1,10}$/), { minLength: 1, maxLength: 8 })
  .map((words) => words.join(" "));

/** Safe ISO timestamp arbitrary. */
const isoTimestampArb = fc
  .integer({
    min: new Date("2024-01-01T00:00:00Z").getTime(),
    max: new Date("2025-12-31T23:59:59Z").getTime(),
  })
  .map((ms) => new Date(ms).toISOString());

/** Project name arbitrary. */
const projectNameArb = identifierArb;

/** Schema name arbitrary. */
const schemaNameArb = identifierArb;

/**
 * Generate a minimal but valid ModuleData for testing KB payload generation.
 */
const moduleDataArb = fc.record({
  name: identifierArb,
  language: languageArb,
  framework: fc.option(identifierArb, { nil: null }),
  purpose: contentArb,
  sourceFileCount: fc.integer({ min: 1, max: 500 }),
  path: identifierArb.map((n) => `modules/${n}`),
  dependencies: fc.array(identifierArb, { minLength: 0, maxLength: 5 }),
  packages: fc.array(
    fc.record({
      name: identifierArb,
      path: identifierArb.map((n) => `src/${n}`),
      purpose: contentArb,
    }),
    { minLength: 0, maxLength: 3 },
  ),
  classes: fc.array(
    fc.record({
      name: classNameArb,
      visibility: fc.constantFrom("public", "internal", "private"),
      superclass: fc.option(classNameArb, { nil: undefined }),
      interfaces: fc.array(classNameArb, { minLength: 0, maxLength: 2 }),
      annotations: fc.array(identifierArb, { minLength: 0, maxLength: 2 }),
    }),
    { minLength: 0, maxLength: 5 },
  ),
  functions: fc.array(
    fc.record({
      name: identifierArb,
      visibility: fc.constantFrom("public", "internal", "private"),
      parameters: fc.array(
        fc.record({
          name: identifierArb,
          type: identifierArb,
        }),
        { minLength: 0, maxLength: 3 },
      ),
      returnType: fc.constantFrom("void", "string", "number", "boolean"),
      annotations: fc.array(identifierArb, { minLength: 0, maxLength: 2 }),
    }),
    { minLength: 0, maxLength: 5 },
  ),
  patterns: fc.record({
    diStyle: fc.constantFrom("constructor injection", "field injection", "none"),
    errorHandling: fc.constantFrom("try-catch", "Result type", "exception handler"),
    naming: fc.constantFrom("*Controller/*Service/*Repository", "unknown"),
    logging: fc.constantFrom("SLF4J", "console.log", "println"),
    testing: fc.constantFrom("JUnit", "vitest", "pytest"),
  }),
}) as fc.Arbitrary<ModuleData>;

/** Annotation row arbitrary. */
const annotationRowArb: fc.Arbitrary<AnnotationRow> = fc.record({
  target: classNameArb,
  authorAgent: authorAgentArb,
  annotationType: annotationTypeArb,
  content: contentArb,
  timestamp: isoTimestampArb,
});

// ---------------------------------------------------------------------------
// Property 5: KB Tagging Correctness
// ---------------------------------------------------------------------------

describe("Feature: code-intelligence, Property 5: Knowledge Base Tagging Correctness", () => {
  /**
   * **Validates: Requirements 2.2, 4.6, 9.3**
   *
   * Generate random module names, languages, annotation types, schema names,
   * run KB payload generators, verify tag arrays match the required format.
   * The `project` field is always set to the workspace name.
   */
  it("module KB payload has correct tags: code-index, {module-name}, {language}", () => {
    fc.assert(
      fc.property(
        moduleDataArb,
        projectNameArb,
        (module, projectName) => {
          const payload = generateModuleKbPayload(module, projectName);

          // Title format
          expect(payload.title).toBe(`Code Index — ${module.name}`);

          // Tags must be exactly: "code-index, {module-name}, {language}"
          expect(payload.tags).toBe(
            `code-index, ${module.name}, ${module.language}`,
          );

          // Parse tags and verify each component
          const tagParts = payload.tags.split(", ").map((t) => t.trim());
          expect(tagParts).toHaveLength(3);
          expect(tagParts[0]).toBe("code-index");
          expect(tagParts[1]).toBe(module.name);
          expect(tagParts[2]).toBe(module.language);

          // Project field is always set to the workspace name
          expect(payload.project).toBe(projectName);

          // Content is non-empty
          expect(payload.content.length).toBeGreaterThan(0);
        },
      ),
      { numRuns: 100 },
    );
  });

  it("annotation KB payload has correct tags: semantic-annotation, {module-name}, {annotation-type}", () => {
    fc.assert(
      fc.property(
        annotationRowArb,
        identifierArb,
        projectNameArb,
        (annotation, moduleName, projectName) => {
          const payload = generateAnnotationKbPayload(
            annotation,
            moduleName,
            projectName,
          );

          // Title format
          expect(payload.title).toBe(
            `Annotation — ${annotation.target} — ${annotation.annotationType}`,
          );

          // Tags must be exactly: "semantic-annotation, {module-name}, {annotation-type}"
          expect(payload.tags).toBe(
            `semantic-annotation, ${moduleName}, ${annotation.annotationType}`,
          );

          // Parse tags and verify each component
          const tagParts = payload.tags.split(", ").map((t) => t.trim());
          expect(tagParts).toHaveLength(3);
          expect(tagParts[0]).toBe("semantic-annotation");
          expect(tagParts[1]).toBe(moduleName);
          expect(tagParts[2]).toBe(annotation.annotationType);

          // Project field is always set to the workspace name
          expect(payload.project).toBe(projectName);

          // Content is non-empty
          expect(payload.content.length).toBeGreaterThan(0);
        },
      ),
      { numRuns: 100 },
    );
  });

  it("schema KB payload has correct tags: code-index, database, {schema-name}", () => {
    fc.assert(
      fc.property(
        schemaNameArb,
        contentArb,
        projectNameArb,
        (schemaName, schemaContent, projectName) => {
          const payload = generateSchemaKbPayload(
            schemaName,
            schemaContent,
            projectName,
          );

          // Title format
          expect(payload.title).toBe(`Database Schema — ${schemaName}`);

          // Tags must be exactly: "code-index, database, {schema-name}"
          expect(payload.tags).toBe(
            `code-index, database, ${schemaName}`,
          );

          // Parse tags and verify each component
          const tagParts = payload.tags.split(", ").map((t) => t.trim());
          expect(tagParts).toHaveLength(3);
          expect(tagParts[0]).toBe("code-index");
          expect(tagParts[1]).toBe("database");
          expect(tagParts[2]).toBe(schemaName);

          // Project field is always set to the workspace name
          expect(payload.project).toBe(projectName);

          // Content matches what was passed in
          expect(payload.content).toBe(schemaContent);
        },
      ),
      { numRuns: 100 },
    );
  });
});

// ---------------------------------------------------------------------------
// Property 6: KB Deduplication on Update
// ---------------------------------------------------------------------------

describe("Feature: code-intelligence, Property 6: Knowledge Base Deduplication on Update", () => {
  /**
   * **Validates: Requirements 2.4**
   *
   * Generate random module, produce KB payload twice with different content,
   * verify the payload uses the same title/key (module name) so that agents
   * can search-then-update rather than creating duplicates.
   */
  it("two payloads for the same module produce the same title for deduplication", () => {
    fc.assert(
      fc.property(
        moduleDataArb,
        projectNameArb,
        fc.integer({ min: 1, max: 100 }),
        (module, projectName, extraFiles) => {
          // First payload — original module data
          const payload1 = generateModuleKbPayload(module, projectName);

          // Second payload — same module name but different content
          const updatedModule: ModuleData = {
            ...module,
            sourceFileCount: module.sourceFileCount + extraFiles,
            purpose: `${module.purpose} (updated)`,
          };
          const payload2 = generateModuleKbPayload(updatedModule, projectName);

          // Title must be identical — this is the deduplication key
          expect(payload1.title).toBe(payload2.title);
          expect(payload1.title).toBe(`Code Index — ${module.name}`);

          // Tags must be identical (same module, same language)
          expect(payload1.tags).toBe(payload2.tags);

          // Project must be identical
          expect(payload1.project).toBe(payload2.project);

          // Content should differ (module was updated)
          expect(payload1.content).not.toBe(payload2.content);
        },
      ),
      { numRuns: 100 },
    );
  });

  it("two payloads for the same annotation target produce the same title for deduplication", () => {
    fc.assert(
      fc.property(
        annotationRowArb,
        identifierArb,
        projectNameArb,
        (annotation, moduleName, projectName) => {
          // First payload
          const payload1 = generateAnnotationKbPayload(
            annotation,
            moduleName,
            projectName,
          );

          // Second payload — same target and type but different content
          const updatedAnnotation: AnnotationRow = {
            ...annotation,
            content: `${annotation.content} (updated)`,
            timestamp: new Date().toISOString(),
          };
          const payload2 = generateAnnotationKbPayload(
            updatedAnnotation,
            moduleName,
            projectName,
          );

          // Title must be identical — deduplication key is target + type
          expect(payload1.title).toBe(payload2.title);
          expect(payload1.title).toBe(
            `Annotation — ${annotation.target} — ${annotation.annotationType}`,
          );

          // Tags must be identical
          expect(payload1.tags).toBe(payload2.tags);

          // Project must be identical
          expect(payload1.project).toBe(payload2.project);
        },
      ),
      { numRuns: 100 },
    );
  });

  it("two payloads for the same schema produce the same title for deduplication", () => {
    fc.assert(
      fc.property(
        schemaNameArb,
        contentArb,
        contentArb,
        projectNameArb,
        (schemaName, content1, content2, projectName) => {
          const payload1 = generateSchemaKbPayload(
            schemaName,
            content1,
            projectName,
          );
          const payload2 = generateSchemaKbPayload(
            schemaName,
            content2,
            projectName,
          );

          // Title must be identical — deduplication key is schema name
          expect(payload1.title).toBe(payload2.title);
          expect(payload1.title).toBe(`Database Schema — ${schemaName}`);

          // Tags must be identical
          expect(payload1.tags).toBe(payload2.tags);

          // Project must be identical
          expect(payload1.project).toBe(payload2.project);
        },
      ),
      { numRuns: 100 },
    );
  });
});
