/**
 * Project Detector for the Code Intelligence System.
 *
 * Auto-detects the project type, primary language, and framework
 * by scanning build files, counting source file extensions, and
 * searching dependency declarations for known framework patterns.
 */
import type { DetectionResult } from "./types.js";
/**
 * Detect the project type, primary language, and framework for a workspace.
 *
 * Scans the root directory for build files in priority order, counts source
 * file extensions to determine the primary language, and reads the build file
 * to detect known framework patterns.
 *
 * @param rootDir - Absolute or relative path to the project root.
 * @returns A {@link DetectionResult} describing the project.
 */
export declare function detectProjectType(rootDir: string): DetectionResult;
//# sourceMappingURL=project-detector.d.ts.map