/**
 * Project Detector for the Code Intelligence System.
 *
 * Auto-detects the project type, primary language, and framework
 * by scanning build files, counting source file extensions, and
 * searching dependency declarations for known framework patterns.
 */

import * as fs from "node:fs";
import * as path from "node:path";
import type { DetectionResult } from "./types.js";

// ---------------------------------------------------------------------------
// Build file → project type mapping (priority order)
// ---------------------------------------------------------------------------

/** Build files checked in priority order — first match wins. */
const BUILD_FILE_PRIORITY: Array<{ file: string; type: string }> = [
  { file: "build.gradle.kts", type: "gradle-kotlin" },
  { file: "build.gradle", type: "gradle-java" },
  { file: "pom.xml", type: "maven-java" },
  { file: "package.json", type: "npm" }, // refined to npm-typescript / npm-javascript later
  { file: "Cargo.toml", type: "cargo-rust" },
  { file: "go.mod", type: "go-module" },
  { file: "pyproject.toml", type: "python" },
  { file: "setup.py", type: "python" },
];

// ---------------------------------------------------------------------------
// Source extensions used for language counting
// ---------------------------------------------------------------------------

const SOURCE_EXTENSIONS: Record<string, string> = {
  ".kt": "kotlin",
  ".java": "java",
  ".ts": "typescript",
  ".tsx": "typescript",
  ".js": "javascript",
  ".jsx": "javascript",
  ".py": "python",
  ".go": "go",
  ".rs": "rust",
  ".cs": "csharp",
};

// ---------------------------------------------------------------------------
// Framework detection patterns per build system
// ---------------------------------------------------------------------------

interface FrameworkPattern {
  pattern: string;
  framework: string;
}

const GRADLE_FRAMEWORKS: FrameworkPattern[] = [
  { pattern: "spring-boot-starter", framework: "Spring Boot" },
  { pattern: "org.springframework.boot", framework: "Spring Boot" },
  { pattern: "io.ktor", framework: "Ktor" },
  { pattern: "ktor-", framework: "Ktor" },
  { pattern: "io.micronaut", framework: "Micronaut" },
  { pattern: "android", framework: "Android" },
];

const MAVEN_FRAMEWORKS: FrameworkPattern[] = [
  { pattern: "spring-boot-starter", framework: "Spring Boot" },
  { pattern: "io.micronaut", framework: "Micronaut" },
  { pattern: "io.quarkus", framework: "Quarkus" },
];

const NPM_FRAMEWORKS: FrameworkPattern[] = [
  { pattern: '"react"', framework: "React" },
  { pattern: '"react-dom"', framework: "React" },
  { pattern: '"next"', framework: "Next.js" },
  { pattern: '"@angular/core"', framework: "Angular" },
  { pattern: '"vue"', framework: "Vue.js" },
  { pattern: '"express"', framework: "Express.js" },
  { pattern: '"@nestjs/core"', framework: "NestJS" },
];

const CARGO_FRAMEWORKS: FrameworkPattern[] = [
  { pattern: "actix-web", framework: "Actix Web" },
  { pattern: "axum", framework: "Axum" },
  { pattern: "rocket", framework: "Rocket" },
];

const GO_FRAMEWORKS: FrameworkPattern[] = [
  { pattern: "github.com/gin-gonic/gin", framework: "Gin" },
  { pattern: "github.com/gofiber/fiber", framework: "Fiber" },
];

const PYTHON_FRAMEWORKS: FrameworkPattern[] = [
  { pattern: "django", framework: "Django" },
  { pattern: "flask", framework: "Flask" },
  { pattern: "fastapi", framework: "FastAPI" },
];

// ---------------------------------------------------------------------------
// Project type → default language fallback
// ---------------------------------------------------------------------------

const PROJECT_TYPE_LANGUAGE: Record<string, string> = {
  "gradle-kotlin": "kotlin",
  "gradle-java": "java",
  "maven-java": "java",
  "npm-typescript": "typescript",
  "npm-javascript": "javascript",
  "cargo-rust": "rust",
  "go-module": "go",
  python: "python",
  dotnet: "csharp",
  generic: "unknown",
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Recursively count source file extensions up to a maximum depth.
 * Returns a map of language → count.
 */
function countSourceFiles(
  dir: string,
  maxDepth: number,
  currentDepth = 0,
): Record<string, number> {
  const counts: Record<string, number> = {};

  if (currentDepth > maxDepth) return counts;

  let entries: fs.Dirent[];
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return counts;
  }

  for (const entry of entries) {
    if (entry.isDirectory()) {
      // Skip common non-source directories for performance
      if (
        entry.name === "node_modules" ||
        entry.name === ".git" ||
        entry.name === "build" ||
        entry.name === "dist" ||
        entry.name === "out" ||
        entry.name === "target" ||
        entry.name === ".gradle" ||
        entry.name === "vendor"
      ) {
        continue;
      }
      const childCounts = countSourceFiles(
        path.join(dir, entry.name),
        maxDepth,
        currentDepth + 1,
      );
      for (const [lang, count] of Object.entries(childCounts)) {
        counts[lang] = (counts[lang] ?? 0) + count;
      }
    } else if (entry.isFile()) {
      const ext = getSourceExtension(entry.name);
      if (ext) {
        const lang = SOURCE_EXTENSIONS[ext]!;
        counts[lang] = (counts[lang] ?? 0) + 1;
      }
    }
  }

  return counts;
}

/**
 * Return the matching source extension for a filename, or `null` if none.
 * Checks longer extensions first (e.g. `.tsx` before `.ts` is not needed
 * since `path.extname` handles it, but we list them explicitly).
 */
function getSourceExtension(filename: string): string | null {
  const ext = path.extname(filename).toLowerCase();
  return ext in SOURCE_EXTENSIONS ? ext : null;
}

/**
 * Determine the primary language from extension counts.
 * Returns the language with the highest count, or `null` if no source files.
 */
function getPrimaryLanguage(counts: Record<string, number>): string | null {
  let best: string | null = null;
  let bestCount = 0;
  for (const [lang, count] of Object.entries(counts)) {
    if (count > bestCount) {
      best = lang;
      bestCount = count;
    }
  }
  return best;
}

/**
 * Safely read a file's contents. Returns `null` on any error.
 */
function safeReadFile(filePath: string): string | null {
  try {
    return fs.readFileSync(filePath, "utf-8");
  } catch {
    return null;
  }
}

/**
 * Search file content for framework patterns. Returns the first match or `null`.
 */
function detectFramework(
  content: string,
  patterns: FrameworkPattern[],
): string | null {
  for (const { pattern, framework } of patterns) {
    if (content.includes(pattern)) {
      return framework;
    }
  }
  return null;
}

/**
 * Check whether the npm project uses TypeScript.
 * - If `tsconfig.json` exists in root → true
 * - Else if any `.ts`/`.tsx` files found (quick scan, depth 3) → true
 */
function isNpmTypeScript(rootDir: string): boolean {
  if (fs.existsSync(path.join(rootDir, "tsconfig.json"))) {
    return true;
  }

  // Quick scan for .ts/.tsx files
  return hasTypeScriptFiles(rootDir, 3, 0);
}

/**
 * Recursively check for `.ts` or `.tsx` files up to a max depth.
 */
function hasTypeScriptFiles(
  dir: string,
  maxDepth: number,
  currentDepth: number,
): boolean {
  if (currentDepth > maxDepth) return false;

  let entries: fs.Dirent[];
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return false;
  }

  for (const entry of entries) {
    if (entry.isFile()) {
      const ext = path.extname(entry.name).toLowerCase();
      if (ext === ".ts" || ext === ".tsx") {
        return true;
      }
    } else if (entry.isDirectory()) {
      if (
        entry.name === "node_modules" ||
        entry.name === ".git" ||
        entry.name === "dist" ||
        entry.name === "build"
      ) {
        continue;
      }
      if (hasTypeScriptFiles(path.join(dir, entry.name), maxDepth, currentDepth + 1)) {
        return true;
      }
    }
  }

  return false;
}

/**
 * Check for .sln files in the root directory.
 */
function findSlnFile(rootDir: string): string | null {
  try {
    const entries = fs.readdirSync(rootDir);
    return entries.find((e) => e.endsWith(".sln")) ?? null;
  } catch {
    return null;
  }
}

/**
 * Check for .csproj files in the root directory.
 */
function findCsprojFile(rootDir: string): string | null {
  try {
    const entries = fs.readdirSync(rootDir);
    return entries.find((e) => e.endsWith(".csproj")) ?? null;
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// Main detection function
// ---------------------------------------------------------------------------

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
export function detectProjectType(rootDir: string): DetectionResult {
  const resolvedRoot = path.resolve(rootDir);

  // --- Step 1: Build file scanning ---
  let projectType = "generic";
  let buildFile = "none";

  // Check priority-ordered build files
  for (const entry of BUILD_FILE_PRIORITY) {
    if (fs.existsSync(path.join(resolvedRoot, entry.file))) {
      projectType = entry.type;
      buildFile = entry.file;
      break;
    }
  }

  // Check for .sln / .csproj if no match yet
  if (projectType === "generic") {
    const slnFile = findSlnFile(resolvedRoot);
    if (slnFile) {
      projectType = "dotnet";
      buildFile = slnFile;
    } else {
      const csprojFile = findCsprojFile(resolvedRoot);
      if (csprojFile) {
        projectType = "dotnet";
        buildFile = csprojFile;
      }
    }
  }

  // Refine npm → npm-typescript or npm-javascript
  if (projectType === "npm") {
    projectType = isNpmTypeScript(resolvedRoot)
      ? "npm-typescript"
      : "npm-javascript";
  }

  // --- Step 2: Language detection ---
  const langCounts = countSourceFiles(resolvedRoot, 3);
  const detectedLanguage = getPrimaryLanguage(langCounts);

  // If the build system implies a specific language and that language has any
  // source files, prefer it over the raw file-count winner.  This prevents
  // e.g. a `portal/` directory full of .ts files from overriding the primary
  // language of a Gradle-Kotlin project.
  const impliedLanguage = PROJECT_TYPE_LANGUAGE[projectType];
  let primaryLanguage: string;
  if (impliedLanguage && impliedLanguage !== "unknown" && langCounts[impliedLanguage]) {
    primaryLanguage = impliedLanguage;
  } else {
    primaryLanguage = detectedLanguage ?? impliedLanguage ?? "unknown";
  }

  // --- Step 3: Framework detection ---
  let framework: string | null = null;
  const buildFilePath = path.join(resolvedRoot, buildFile);
  const buildContent = buildFile !== "none" ? safeReadFile(buildFilePath) : null;

  if (buildContent) {
    switch (projectType) {
      case "gradle-kotlin":
      case "gradle-java":
        framework = detectFramework(buildContent, GRADLE_FRAMEWORKS);
        break;
      case "maven-java":
        framework = detectFramework(buildContent, MAVEN_FRAMEWORKS);
        break;
      case "npm-typescript":
      case "npm-javascript":
        framework = detectFramework(buildContent, NPM_FRAMEWORKS);
        break;
      case "cargo-rust":
        framework = detectFramework(buildContent, CARGO_FRAMEWORKS);
        break;
      case "go-module":
        framework = detectFramework(buildContent, GO_FRAMEWORKS);
        break;
      case "python":
        framework = detectFramework(buildContent, PYTHON_FRAMEWORKS);
        break;
    }
  }

  // --- Step 4: Log and return ---
  const result: DetectionResult = {
    projectType,
    primaryLanguage,
    framework,
    buildFile,
  };

  process.stdout.write(
    `[Code-Index] INFO: Project detected — type=${projectType}, language=${primaryLanguage}, framework=${framework}, buildFile=${buildFile}\n`,
  );

  return result;
}
