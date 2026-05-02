/**
 * Knowledge Base Ingestion Helpers for the Code Intelligence System.
 *
 * Generates structured JSON payloads that agents can read and ingest
 * into the Knowledge Base via MCP tools (`mcp_knowledge_base_kb_ingest`).
 *
 * Three payload generators:
 * - `generateModuleKbPayload()` — module index document
 * - `generateAnnotationKbPayload()` — semantic annotation document
 * - `generateSchemaKbPayload()` — database schema document
 */

import type { AnnotationRow, KbIngestPayload, ModuleData } from "./types.js";

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Generate a KB ingestion payload for a module index document.
 *
 * @param module      - Full module data from the indexer.
 * @param projectName - Workspace / project name.
 * @returns A `KbIngestPayload` ready for KB ingestion.
 */
export function generateModuleKbPayload(
  module: ModuleData,
  projectName: string,
): KbIngestPayload {
  const content = buildModuleContent(module);

  return {
    title: `Code Index — ${module.name}`,
    content,
    tags: `code-index, ${module.name}, ${module.language}`,
    project: projectName,
  };
}

/**
 * Generate a KB ingestion payload for a semantic annotation.
 *
 * @param annotation  - The annotation row to ingest.
 * @param moduleName  - Name of the module the annotation belongs to.
 * @param projectName - Workspace / project name.
 * @returns A `KbIngestPayload` ready for KB ingestion.
 */
export function generateAnnotationKbPayload(
  annotation: AnnotationRow,
  moduleName: string,
  projectName: string,
): KbIngestPayload {
  const content = buildAnnotationContent(annotation, moduleName);

  return {
    title: `Annotation — ${annotation.target} — ${annotation.annotationType}`,
    content,
    tags: `semantic-annotation, ${moduleName}, ${annotation.annotationType}`,
    project: projectName,
  };
}

/**
 * Generate a KB ingestion payload for a database schema.
 *
 * @param schemaName    - Name of the database schema.
 * @param schemaContent - Pre-built content string describing the schema.
 * @param projectName   - Workspace / project name.
 * @returns A `KbIngestPayload` ready for KB ingestion.
 */
export function generateSchemaKbPayload(
  schemaName: string,
  schemaContent: string,
  projectName: string,
): KbIngestPayload {
  return {
    title: `Database Schema — ${schemaName}`,
    content: schemaContent,
    tags: `code-index, database, ${schemaName}`,
    project: projectName,
  };
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Build a human-readable content summary for a module KB document.
 */
function buildModuleContent(module: ModuleData): string {
  const lines: string[] = [];

  lines.push(`Module: ${module.name}`);
  lines.push(`Language: ${module.language}`);
  if (module.framework) {
    lines.push(`Framework: ${module.framework}`);
  }
  lines.push(`Purpose: ${module.purpose}`);
  lines.push(`Source Files: ${module.sourceFileCount}`);
  lines.push("");

  // Classes
  if (module.classes.length > 0) {
    lines.push("Classes:");
    for (const cls of module.classes) {
      const parts = [`  - ${cls.name} (${cls.visibility})`];
      if (cls.superclass) {
        parts.push(` extends ${cls.superclass}`);
      }
      if (cls.interfaces.length > 0) {
        parts.push(` implements ${cls.interfaces.join(", ")}`);
      }
      lines.push(parts.join(""));
    }
    lines.push("");
  }

  // Functions
  if (module.functions.length > 0) {
    lines.push("Functions:");
    for (const fn of module.functions) {
      const params = fn.parameters
        .map((p) => `${p.name}: ${p.type}`)
        .join(", ");
      lines.push(`  - ${fn.name}(${params}): ${fn.returnType} (${fn.visibility})`);
    }
    lines.push("");
  }

  // Packages
  if (module.packages.length > 0) {
    lines.push("Packages:");
    for (const pkg of module.packages) {
      lines.push(`  - ${pkg.name}: ${pkg.purpose}`);
    }
    lines.push("");
  }

  // Dependencies
  if (module.dependencies.length > 0) {
    lines.push(`Dependencies: ${module.dependencies.join(", ")}`);
    lines.push("");
  }

  // Patterns
  lines.push("Detected Patterns:");
  lines.push(`  DI Style: ${module.patterns.diStyle}`);
  lines.push(`  Error Handling: ${module.patterns.errorHandling}`);
  lines.push(`  Naming: ${module.patterns.naming}`);
  lines.push(`  Logging: ${module.patterns.logging}`);
  lines.push(`  Testing: ${module.patterns.testing}`);

  return lines.join("\n");
}

/**
 * Build a human-readable content string for an annotation KB document.
 */
function buildAnnotationContent(
  annotation: AnnotationRow,
  moduleName: string,
): string {
  const lines: string[] = [];

  lines.push(`Module: ${moduleName}`);
  lines.push(`Target: ${annotation.target}`);
  lines.push(`Author: ${annotation.authorAgent}`);
  lines.push(`Type: ${annotation.annotationType}`);
  lines.push(`Content: ${annotation.content}`);
  lines.push(`Timestamp: ${annotation.timestamp}`);

  return lines.join("\n");
}
