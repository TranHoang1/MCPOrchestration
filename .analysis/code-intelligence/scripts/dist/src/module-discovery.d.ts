/**
 * Module Discovery for the Code Intelligence System.
 *
 * Discovers modules (subprojects) within a workspace based on the detected
 * build system. Each build system has its own discovery strategy:
 *
 * - Gradle: Parse `settings.gradle.kts` / `settings.gradle` for `include()` statements
 * - Maven: Parse `pom.xml` for `<module>` elements
 * - npm: Parse `package.json` for `workspaces` field (array or object form)
 * - Cargo: Parse `Cargo.toml` for `[workspace]` members
 * - Go: Single module per `go.mod`, workspace via `go.work`
 * - .NET: Parse `*.sln` for project references, fallback to `*.csproj` scan
 * - Python: Check for monorepo indicators, fallback to single module
 * - Generic: Single `root` module
 *
 * For each discovered module, source directories are detected based on
 * language-specific conventions.
 */
import type { DetectionResult, Module } from "./types.js";
/**
 * Discover all modules (subprojects) in the workspace based on the detected
 * build system.
 *
 * @param rootDir - Absolute or relative path to the project root.
 * @param detectionResult - The result from `detectProjectType()`.
 * @returns An array of discovered {@link Module} objects.
 */
export declare function discoverModules(rootDir: string, detectionResult: DetectionResult): Module[];
//# sourceMappingURL=module-discovery.d.ts.map