/**
 * Annotation Manager for the Code Intelligence System.
 *
 * Provides the canonical implementation for managing semantic annotations
 * in Module Analysis Files. Both the incremental indexer and other consumers
 * should use this module for annotation reading, writing, and preservation.
 *
 * Two main functions:
 * - `addAnnotation()` — Append a new annotation row to a module's analysis file
 * - `preserveAnnotations()` — Read and return existing annotations, marking
 *   deleted targets as `[DELETED]`
 */

import * as fs from "node:fs";
import * as path from "node:path";
import type { AnnotationRow, KbIngestPayload } from "./types.js";

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const VALID_ANNOTATION_TYPES = [
  "requirement-link",
  "design-decision",
  "implementation-note",
  "known-issue",
  "todo",
] as const;

type AnnotationType = (typeof VALID_ANNOTATION_TYPES)[number];

const DEFAULT_OUTPUT_DIR = path.resolve(
  process.cwd(),
  ".analysis/code-intelligence",
);

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Add a semantic annotation to a module's Analysis File.
 *
 * Reads the target module's analysis file, appends a new row to the
 * `## Annotations` Markdown table, and outputs structured JSON for
 * KB ingestion.
 *
 * @param moduleName     - Name of the module (maps to `modules/{moduleName}.md`).
 * @param target         - The class or function name being annotated.
 * @param authorAgent    - The agent writing the annotation (e.g., "SA_Agent").
 * @param annotationType - One of the valid annotation types.
 * @param content        - The annotation content text.
 * @param outputDir      - Optional override for the analysis output directory.
 *                         Defaults to `.analysis/code-intelligence/`.
 */
export function addAnnotation(
  moduleName: string,
  target: string,
  authorAgent: string,
  annotationType: string,
  content: string,
  outputDir?: string,
): void {
  // Validate annotation type
  if (!VALID_ANNOTATION_TYPES.includes(annotationType as AnnotationType)) {
    throw new Error(
      `[Code-Index] ERROR: Invalid annotation type "${annotationType}". ` +
        `Valid types: ${VALID_ANNOTATION_TYPES.join(", ")}`,
    );
  }

  const dir = outputDir ?? DEFAULT_OUTPUT_DIR;
  const analysisFilePath = path.join(dir, "modules", `${moduleName}.md`);

  // Read the existing analysis file
  let fileContent: string;
  try {
    fileContent = fs.readFileSync(analysisFilePath, "utf-8");
  } catch {
    throw new Error(
      `[Code-Index] ERROR: Module analysis file not found — ${analysisFilePath}`,
    );
  }

  // Build the new annotation row
  const timestamp = new Date().toISOString();
  const annotation: AnnotationRow = {
    target,
    authorAgent,
    annotationType,
    content,
    timestamp,
  };

  const newRow = formatAnnotationRow(annotation);

  // Find the ## Annotations section and append the row
  const updatedContent = appendAnnotationRow(fileContent, newRow);

  // Write the updated file atomically
  const tmpPath = `${analysisFilePath}.tmp`;
  fs.mkdirSync(path.dirname(analysisFilePath), { recursive: true });
  fs.writeFileSync(tmpPath, updatedContent, "utf-8");
  fs.renameSync(tmpPath, analysisFilePath);

  // Output structured JSON for KB ingestion
  const kbPayload: KbIngestPayload = {
    title: `Annotation — ${target} — ${annotationType}`,
    content: `Module: ${moduleName}\nTarget: ${target}\nAuthor: ${authorAgent}\nType: ${annotationType}\nContent: ${content}\nTimestamp: ${timestamp}`,
    tags: `semantic-annotation, ${moduleName}, ${annotationType}`,
    project: path.basename(path.resolve(dir, "../..")),
  };

  process.stdout.write(JSON.stringify(kbPayload) + "\n");
}

/**
 * Read and preserve existing annotations from a module's analysis file.
 *
 * Parses the `## Annotations` section, returns all annotation rows,
 * and marks targets that no longer exist in `currentTargets` as
 * `[DELETED] {original-target-name}`.
 *
 * @param existingAnalysisFile - Absolute or relative path to the module's
 *                               analysis file.
 * @param currentTargets       - Array of class/function names that currently
 *                               exist in the module.
 * @returns Array of annotation rows with deleted targets marked.
 */
export function preserveAnnotations(
  existingAnalysisFile: string,
  currentTargets: string[],
): AnnotationRow[] {
  const annotations = readAnnotations(existingAnalysisFile);
  return markDeletedTargets(annotations, currentTargets);
}

/**
 * Read annotations from a module analysis file without any modification.
 *
 * @param analysisFilePath - Path to the module's analysis file.
 * @returns Array of parsed annotation rows.
 */
export function readAnnotations(analysisFilePath: string): AnnotationRow[] {
  let content: string;
  try {
    content = fs.readFileSync(analysisFilePath, "utf-8");
  } catch {
    return [];
  }

  return parseAnnotationsFromContent(content);
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Parse annotation rows from the `## Annotations` section of a Markdown
 * analysis file content string.
 */
function parseAnnotationsFromContent(content: string): AnnotationRow[] {
  const annotations: AnnotationRow[] = [];

  // Find the ## Annotations section
  const annotationSectionIdx = content.indexOf("## Annotations");
  if (annotationSectionIdx === -1) return [];

  // Extract content from the annotations section to the next section or EOF
  const sectionContent = content.slice(annotationSectionIdx);
  const nextSectionMatch = sectionContent.slice("## Annotations".length).search(/^## /m);
  const sectionEnd = nextSectionMatch !== -1
    ? "## Annotations".length + nextSectionMatch
    : sectionContent.length;
  const relevantContent = sectionContent.slice(0, sectionEnd);

  const lines = relevantContent.split("\n");

  // Skip the section heading, then find the table header and separator
  let pastHeader = false;
  for (const line of lines) {
    const trimmed = line.trim();

    // Skip the section heading
    if (trimmed.startsWith("## ")) continue;
    // Skip empty lines
    if (!trimmed) continue;

    // Skip the table header row and separator
    if (
      trimmed.startsWith("| Target") ||
      trimmed.startsWith("|--") ||
      trimmed.startsWith("| ---") ||
      /^\|[-\s|]+\|$/.test(trimmed)
    ) {
      pastHeader = true;
      continue;
    }

    // Parse data rows after the header
    if (pastHeader && trimmed.startsWith("|")) {
      const cells = trimmed
        .split("|")
        .map((c) => c.trim())
        .filter(Boolean);

      if (cells.length >= 5) {
        annotations.push({
          target: cells[0],
          authorAgent: cells[1],
          annotationType: cells[2],
          content: cells[3],
          timestamp: cells[4],
        });
      }
    }
  }

  return annotations;
}

/**
 * Mark annotations whose targets no longer exist in the current codebase.
 *
 * Targets already marked as `[DELETED]` are left unchanged.
 */
function markDeletedTargets(
  annotations: AnnotationRow[],
  currentTargets: string[],
): AnnotationRow[] {
  const targetSet = new Set(currentTargets);

  return annotations.map((annotation) => {
    // Already marked as deleted — leave as-is
    if (annotation.target.startsWith("[DELETED]")) {
      return annotation;
    }

    // If the target no longer exists, mark it
    if (!targetSet.has(annotation.target)) {
      return {
        ...annotation,
        target: `[DELETED] ${annotation.target}`,
      };
    }

    return annotation;
  });
}

/**
 * Format a single annotation row as a Markdown table row.
 */
function formatAnnotationRow(annotation: AnnotationRow): string {
  return `| ${annotation.target} | ${annotation.authorAgent} | ${annotation.annotationType} | ${annotation.content} | ${annotation.timestamp} |`;
}

/**
 * Append a new annotation row to the `## Annotations` section of a
 * Markdown analysis file.
 *
 * Finds the annotation table and appends the row after the last existing
 * row (or after the table header/separator if no rows exist).
 */
function appendAnnotationRow(fileContent: string, newRow: string): string {
  const lines = fileContent.split("\n");
  const annotationSectionIdx = lines.findIndex((l) =>
    l.trim().startsWith("## Annotations"),
  );

  if (annotationSectionIdx === -1) {
    // No annotations section found — append one at the end
    return (
      fileContent.trimEnd() +
      "\n\n## Annotations\n\n" +
      "| Target | Author Agent | Type | Content | Timestamp |\n" +
      "|--------|-------------|------|---------|----------|\n" +
      newRow +
      "\n"
    );
  }

  // Find the insertion point: after the last table row in the annotations section
  let insertIdx = annotationSectionIdx + 1;
  let lastTableRowIdx = -1;

  for (let i = annotationSectionIdx + 1; i < lines.length; i++) {
    const trimmed = lines[i].trim();

    // Stop if we hit the next section
    if (trimmed.startsWith("## ") && !trimmed.startsWith("## Annotations")) {
      break;
    }

    // Track the last line that is part of the table (header, separator, or data row)
    if (trimmed.startsWith("|")) {
      lastTableRowIdx = i;
    }
  }

  if (lastTableRowIdx !== -1) {
    insertIdx = lastTableRowIdx + 1;
  }

  // Insert the new row
  lines.splice(insertIdx, 0, newRow);
  return lines.join("\n");
}
