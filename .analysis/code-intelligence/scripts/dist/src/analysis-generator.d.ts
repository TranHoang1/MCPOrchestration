/**
 * Analysis Generator for the Code Intelligence System.
 *
 * Generates human-readable Markdown analysis files:
 * - `project-structure.md` — high-level overview of all modules
 * - `modules/{module-name}.md` — detailed per-module analysis
 *
 * Both files are written atomically (temp file + rename) and include
 * a "Last Updated" UTC timestamp.
 */
import type { ModuleData, ProjectInfo, AnnotationRow } from "./types.js";
/**
 * Generate the project-structure.md file.
 *
 * @param modules     - All discovered modules with their analysis data.
 * @param projectInfo - High-level project metadata.
 * @param outputDir   - Directory to write the file into.
 *                      Defaults to `.analysis/code-intelligence/`.
 */
export declare function generateProjectStructure(modules: ModuleData[], projectInfo: ProjectInfo, outputDir?: string): void;
/**
 * Generate a per-module analysis file at `modules/{module.name}.md`.
 *
 * @param module      - Full module data including classes, functions, patterns.
 * @param annotations - Existing annotation rows to include in the file.
 * @param outputDir   - Directory to write the file into.
 *                      Defaults to `.analysis/code-intelligence/`.
 */
export declare function generateModuleAnalysis(module: ModuleData, annotations: AnnotationRow[], outputDir?: string): void;
/**
 * Format a single annotation row as a Markdown table row.
 *
 * Exported for reuse by the annotation-manager.
 */
export declare function formatAnnotationRow(annotation: AnnotationRow): string;
//# sourceMappingURL=analysis-generator.d.ts.map