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
import * as fs from "node:fs";
import * as path from "node:path";
// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
/**
 * Safely read a file's contents. Returns `null` on any error.
 */
function safeReadFile(filePath) {
    try {
        return fs.readFileSync(filePath, "utf-8");
    }
    catch {
        return null;
    }
}
/**
 * Check whether a directory exists.
 */
function dirExists(dirPath) {
    try {
        return fs.statSync(dirPath).isDirectory();
    }
    catch {
        return false;
    }
}
/**
 * Check whether a file exists.
 */
function fileExists(filePath) {
    try {
        return fs.statSync(filePath).isFile();
    }
    catch {
        return false;
    }
}
/**
 * Resolve simple glob patterns like `packages/*` by listing the parent
 * directory and returning matching subdirectories.
 * Only supports trailing `/*` or `/**` globs.
 */
function resolveGlob(rootDir, pattern) {
    // Strip trailing /** or /*
    const cleaned = pattern.replace(/\/\*\*?$/, "");
    const parentDir = path.join(rootDir, cleaned);
    if (!dirExists(parentDir))
        return [];
    try {
        const entries = fs.readdirSync(parentDir, { withFileTypes: true });
        return entries
            .filter((e) => e.isDirectory())
            .map((e) => path.join(cleaned, e.name));
    }
    catch {
        return [];
    }
}
/**
 * Filter a list of candidate source directories to only those that exist.
 */
function filterExistingDirs(rootDir, candidates) {
    return candidates.filter((d) => dirExists(path.join(rootDir, d)));
}
// ---------------------------------------------------------------------------
// Source directory detection per build system
// ---------------------------------------------------------------------------
/**
 * Detect source directories for a Gradle or Maven module.
 */
function detectGradleMavenSourceDirs(rootDir, modulePath) {
    const candidates = [
        path.join(modulePath, "src/main/kotlin"),
        path.join(modulePath, "src/main/java"),
        path.join(modulePath, "src/main/resources"),
        path.join(modulePath, "src/test/kotlin"),
        path.join(modulePath, "src/test/java"),
    ];
    const found = filterExistingDirs(rootDir, candidates);
    return found.length > 0 ? found : [modulePath];
}
/**
 * Detect source directories for an npm module.
 */
function detectNpmSourceDirs(rootDir, modulePath) {
    const candidates = [
        path.join(modulePath, "src"),
        path.join(modulePath, "lib"),
        path.join(modulePath, "app"),
    ];
    const found = filterExistingDirs(rootDir, candidates);
    return found.length > 0 ? found : [modulePath];
}
/**
 * Detect source directories for a Cargo module.
 */
function detectCargoSourceDirs(rootDir, modulePath) {
    const candidate = path.join(modulePath, "src");
    return dirExists(path.join(rootDir, candidate)) ? [candidate] : [modulePath];
}
/**
 * Detect source directories for a Go module.
 * Go files live in the module root.
 */
function detectGoSourceDirs(_rootDir, modulePath) {
    return [modulePath];
}
/**
 * Detect source directories for a Python module.
 */
function detectPythonSourceDirs(rootDir, modulePath, moduleName) {
    const candidates = [
        path.join(modulePath, "src"),
        path.join(modulePath, moduleName),
    ];
    const found = filterExistingDirs(rootDir, candidates);
    return found.length > 0 ? found : [modulePath];
}
/**
 * Detect source directories for a .NET module.
 */
function detectDotnetSourceDirs(rootDir, modulePath) {
    const candidates = [
        modulePath,
        path.join(modulePath, "Pages"),
        path.join(modulePath, "Controllers"),
    ];
    return filterExistingDirs(rootDir, candidates);
}
/**
 * Detect source directories for a generic / unknown project.
 */
function detectGenericSourceDirs(rootDir) {
    const candidates = ["./src", "./lib", "."];
    const found = filterExistingDirs(rootDir, candidates);
    return found.length > 0 ? found : ["."];
}
/**
 * Detect source directories for a module based on the project type.
 */
function detectSourceDirs(rootDir, modulePath, moduleName, projectType) {
    switch (projectType) {
        case "gradle-kotlin":
        case "gradle-java":
        case "maven-java":
            return detectGradleMavenSourceDirs(rootDir, modulePath);
        case "npm-typescript":
        case "npm-javascript":
            return detectNpmSourceDirs(rootDir, modulePath);
        case "cargo-rust":
            return detectCargoSourceDirs(rootDir, modulePath);
        case "go-module":
            return detectGoSourceDirs(rootDir, modulePath);
        case "python":
            return detectPythonSourceDirs(rootDir, modulePath, moduleName);
        case "dotnet":
            return detectDotnetSourceDirs(rootDir, modulePath);
        default:
            return detectGenericSourceDirs(rootDir);
    }
}
// ---------------------------------------------------------------------------
// Build-system-specific discovery
// ---------------------------------------------------------------------------
/**
 * Discover Gradle subprojects from `settings.gradle.kts` or `settings.gradle`.
 */
function discoverGradleModules(rootDir) {
    const settingsFiles = ["settings.gradle.kts", "settings.gradle"];
    let content = null;
    let settingsFile = "";
    for (const sf of settingsFiles) {
        content = safeReadFile(path.join(rootDir, sf));
        if (content !== null) {
            settingsFile = sf;
            break;
        }
    }
    if (content === null)
        return [];
    // Extract rootProject.name if present
    const rootNameMatch = content.match(/rootProject\.name\s*=\s*["']([^"']+)["']/);
    const _projectName = rootNameMatch?.[1] ?? null;
    // Extract include() statements — handles both single and multi-argument forms
    // Patterns:
    //   include("module-a")
    //   include("module-a", "module-b", "module-c")
    //   include 'module-a'
    //   include 'module-a', 'module-b'
    const modules = [];
    const seen = new Set();
    // Match include(...) or includeIfPresent(...) with parentheses — double quotes
    const includeParenRegex = /(?:include|includeIfPresent)\s*\(([^)]+)\)/g;
    let match;
    while ((match = includeParenRegex.exec(content)) !== null) {
        const args = match[1];
        // Extract all quoted strings from the arguments
        const doubleQuoted = [...args.matchAll(/"([^"]+)"/g)].map((m) => m[1]);
        const singleQuoted = [...args.matchAll(/'([^']+)'/g)].map((m) => m[1]);
        for (const name of [...doubleQuoted, ...singleQuoted]) {
            addGradleModule(rootDir, name, seen, modules);
        }
    }
    // Match include without parentheses — Groovy style: include 'module-a', 'module-b'
    const includeNoParen = /include\s+(?![\s(])(['"][^'"]+['"](?:\s*,\s*['"][^'"]+['"])*)/g;
    while ((match = includeNoParen.exec(content)) !== null) {
        const args = match[1];
        const doubleQuoted = [...args.matchAll(/"([^"]+)"/g)].map((m) => m[1]);
        const singleQuoted = [...args.matchAll(/'([^']+)'/g)].map((m) => m[1]);
        for (const name of [...doubleQuoted, ...singleQuoted]) {
            addGradleModule(rootDir, name, seen, modules);
        }
    }
    return modules;
}
/**
 * Add a Gradle module after normalizing the colon-prefixed path.
 */
function addGradleModule(rootDir, rawName, seen, modules) {
    // Handle colon-prefixed paths: `:module-name` → `module-name`
    // Handle nested: `:parent:child` → `parent/child`
    const normalized = rawName
        .replace(/^:/, "")
        .replace(/:/g, "/");
    if (seen.has(normalized))
        return;
    seen.add(normalized);
    const modulePath = normalized;
    const moduleDir = path.join(rootDir, modulePath);
    if (!dirExists(moduleDir))
        return;
    // Determine build file for this module
    let buildFile = null;
    if (fileExists(path.join(moduleDir, "build.gradle.kts"))) {
        buildFile = path.join(modulePath, "build.gradle.kts");
    }
    else if (fileExists(path.join(moduleDir, "build.gradle"))) {
        buildFile = path.join(modulePath, "build.gradle");
    }
    modules.push({
        name: normalized.replace(/\//g, "-"),
        path: modulePath,
        sourceDirectories: detectGradleMavenSourceDirs(rootDir, modulePath),
        buildFile,
        language: null,
    });
}
/**
 * Discover Maven child modules from root `pom.xml`.
 */
function discoverMavenModules(rootDir) {
    const content = safeReadFile(path.join(rootDir, "pom.xml"));
    if (content === null)
        return [];
    const modules = [];
    const moduleRegex = /<module>\s*([^<]+?)\s*<\/module>/g;
    let match;
    while ((match = moduleRegex.exec(content)) !== null) {
        const moduleName = match[1].trim();
        const modulePath = moduleName;
        const moduleDir = path.join(rootDir, modulePath);
        if (!dirExists(moduleDir))
            continue;
        let buildFile = null;
        if (fileExists(path.join(moduleDir, "pom.xml"))) {
            buildFile = path.join(modulePath, "pom.xml");
        }
        modules.push({
            name: moduleName,
            path: modulePath,
            sourceDirectories: detectGradleMavenSourceDirs(rootDir, modulePath),
            buildFile,
            language: null,
        });
    }
    return modules;
}
/**
 * Discover npm workspace packages from `package.json`.
 */
function discoverNpmModules(rootDir) {
    const content = safeReadFile(path.join(rootDir, "package.json"));
    if (content === null)
        return [];
    let pkg;
    try {
        pkg = JSON.parse(content);
    }
    catch {
        return [];
    }
    // Workspaces can be an array or an object with a `packages` key
    let workspacePatterns = [];
    if (Array.isArray(pkg.workspaces)) {
        workspacePatterns = pkg.workspaces;
    }
    else if (pkg.workspaces !== null &&
        typeof pkg.workspaces === "object" &&
        !Array.isArray(pkg.workspaces)) {
        const ws = pkg.workspaces;
        if (Array.isArray(ws.packages)) {
            workspacePatterns = ws.packages;
        }
    }
    if (workspacePatterns.length === 0)
        return [];
    const modules = [];
    const seen = new Set();
    for (const pattern of workspacePatterns) {
        // If pattern contains a glob wildcard, resolve it
        if (pattern.includes("*")) {
            const resolved = resolveGlob(rootDir, pattern);
            for (const modulePath of resolved) {
                addNpmModule(rootDir, modulePath, seen, modules);
            }
        }
        else {
            // Direct path
            addNpmModule(rootDir, pattern, seen, modules);
        }
    }
    return modules;
}
/**
 * Add an npm workspace module if it contains a `package.json`.
 */
function addNpmModule(rootDir, modulePath, seen, modules) {
    if (seen.has(modulePath))
        return;
    seen.add(modulePath);
    const moduleDir = path.join(rootDir, modulePath);
    if (!dirExists(moduleDir))
        return;
    // Verify the directory contains a package.json
    if (!fileExists(path.join(moduleDir, "package.json")))
        return;
    const moduleName = path.basename(modulePath);
    modules.push({
        name: moduleName,
        path: modulePath,
        sourceDirectories: detectNpmSourceDirs(rootDir, modulePath),
        buildFile: path.join(modulePath, "package.json"),
        language: null,
    });
}
/**
 * Discover Cargo workspace members from `Cargo.toml`.
 */
function discoverCargoModules(rootDir) {
    const content = safeReadFile(path.join(rootDir, "Cargo.toml"));
    if (content === null)
        return [];
    // Check for [workspace] section
    if (!content.includes("[workspace]"))
        return [];
    // Extract members array — handles:
    //   members = ["crate-a", "crate-b"]
    //   members = [\n  "crate-a",\n  "crate-b"\n]
    const membersMatch = content.match(/members\s*=\s*\[([^\]]*)\]/s);
    if (!membersMatch)
        return [];
    const membersStr = membersMatch[1];
    const memberPatterns = [...membersStr.matchAll(/"([^"]+)"/g)].map((m) => m[1]);
    const modules = [];
    const seen = new Set();
    for (const pattern of memberPatterns) {
        if (pattern.includes("*")) {
            const resolved = resolveGlob(rootDir, pattern);
            for (const modulePath of resolved) {
                addCargoModule(rootDir, modulePath, seen, modules);
            }
        }
        else {
            addCargoModule(rootDir, pattern, seen, modules);
        }
    }
    return modules;
}
/**
 * Add a Cargo workspace member module.
 */
function addCargoModule(rootDir, modulePath, seen, modules) {
    if (seen.has(modulePath))
        return;
    seen.add(modulePath);
    const moduleDir = path.join(rootDir, modulePath);
    if (!dirExists(moduleDir))
        return;
    const moduleName = path.basename(modulePath);
    let buildFile = null;
    if (fileExists(path.join(moduleDir, "Cargo.toml"))) {
        buildFile = path.join(modulePath, "Cargo.toml");
    }
    modules.push({
        name: moduleName,
        path: modulePath,
        sourceDirectories: detectCargoSourceDirs(rootDir, modulePath),
        buildFile,
        language: "rust",
    });
}
/**
 * Discover Go modules from `go.mod` and optionally `go.work`.
 */
function discoverGoModules(rootDir) {
    const modules = [];
    // Check for go.work (workspace support)
    const goWorkContent = safeReadFile(path.join(rootDir, "go.work"));
    if (goWorkContent !== null) {
        // Extract `use` directives from go.work
        // Patterns:
        //   use (
        //     ./module-a
        //     ./module-b
        //   )
        //   use ./module-a
        const useBlockMatch = goWorkContent.match(/use\s*\(([^)]*)\)/s);
        if (useBlockMatch) {
            const useBlock = useBlockMatch[1];
            const usePaths = [...useBlock.matchAll(/\.\/([^\s]+)/g)].map((m) => m[1]);
            for (const usePath of usePaths) {
                if (dirExists(path.join(rootDir, usePath))) {
                    modules.push({
                        name: path.basename(usePath),
                        path: usePath,
                        sourceDirectories: detectGoSourceDirs(rootDir, usePath),
                        buildFile: fileExists(path.join(rootDir, usePath, "go.mod"))
                            ? path.join(usePath, "go.mod")
                            : null,
                        language: "go",
                    });
                }
            }
        }
        // Also handle single-line `use ./path`
        const singleUseMatches = [
            ...goWorkContent.matchAll(/^use\s+\.\/([^\s]+)/gm),
        ];
        for (const m of singleUseMatches) {
            const usePath = m[1];
            if (dirExists(path.join(rootDir, usePath)) &&
                !modules.some((mod) => mod.path === usePath)) {
                modules.push({
                    name: path.basename(usePath),
                    path: usePath,
                    sourceDirectories: detectGoSourceDirs(rootDir, usePath),
                    buildFile: fileExists(path.join(rootDir, usePath, "go.mod"))
                        ? path.join(usePath, "go.mod")
                        : null,
                    language: "go",
                });
            }
        }
        if (modules.length > 0)
            return modules;
    }
    // Single module from go.mod
    const goModContent = safeReadFile(path.join(rootDir, "go.mod"));
    if (goModContent === null)
        return [];
    // Extract module name from `module` declaration
    const moduleMatch = goModContent.match(/^module\s+(\S+)/m);
    const moduleName = moduleMatch
        ? path.basename(moduleMatch[1])
        : "root";
    modules.push({
        name: moduleName,
        path: ".",
        sourceDirectories: detectGoSourceDirs(rootDir, "."),
        buildFile: "go.mod",
        language: "go",
    });
    return modules;
}
/**
 * Discover .NET projects from `*.sln` or by scanning for `*.csproj` files.
 */
function discoverDotnetModules(rootDir, buildFile) {
    const modules = [];
    const seen = new Set();
    // Try parsing the .sln file for Project(...) lines
    if (buildFile.endsWith(".sln")) {
        const content = safeReadFile(path.join(rootDir, buildFile));
        if (content !== null) {
            // Project("{FAE04EC0-...}") = "ProjectName", "Path\To\Project.csproj", "{GUID}"
            const projectRegex = /Project\s*\([^)]*\)\s*=\s*"([^"]+)"\s*,\s*"([^"]+)"/g;
            let match;
            while ((match = projectRegex.exec(content)) !== null) {
                const projectName = match[1];
                const projectPath = match[2].replace(/\\/g, "/");
                // Skip solution folders and non-.csproj entries
                if (!projectPath.endsWith(".csproj"))
                    continue;
                const projectDir = path.dirname(projectPath);
                if (seen.has(projectDir))
                    continue;
                seen.add(projectDir);
                if (!dirExists(path.join(rootDir, projectDir)))
                    continue;
                modules.push({
                    name: projectName,
                    path: projectDir,
                    sourceDirectories: detectDotnetSourceDirs(rootDir, projectDir),
                    buildFile: projectPath,
                    language: "csharp",
                });
            }
        }
    }
    // Fallback: scan for *.csproj files in immediate subdirectories
    if (modules.length === 0) {
        try {
            const entries = fs.readdirSync(rootDir, { withFileTypes: true });
            for (const entry of entries) {
                if (!entry.isDirectory())
                    continue;
                const subDir = entry.name;
                const subDirPath = path.join(rootDir, subDir);
                try {
                    const subEntries = fs.readdirSync(subDirPath);
                    const csproj = subEntries.find((e) => e.endsWith(".csproj"));
                    if (csproj && !seen.has(subDir)) {
                        seen.add(subDir);
                        modules.push({
                            name: subDir,
                            path: subDir,
                            sourceDirectories: detectDotnetSourceDirs(rootDir, subDir),
                            buildFile: path.join(subDir, csproj),
                            language: "csharp",
                        });
                    }
                }
                catch {
                    // Skip unreadable subdirectories
                }
            }
        }
        catch {
            // Root directory unreadable
        }
    }
    return modules;
}
/**
 * Discover Python modules — check for monorepo indicators, fallback to single module.
 */
function discoverPythonModules(rootDir) {
    const modules = [];
    const monorepoIndicators = ["packages", "libs"];
    for (const indicator of monorepoIndicators) {
        const indicatorDir = path.join(rootDir, indicator);
        if (!dirExists(indicatorDir))
            continue;
        try {
            const entries = fs.readdirSync(indicatorDir, { withFileTypes: true });
            for (const entry of entries) {
                if (!entry.isDirectory())
                    continue;
                const subDir = path.join(indicator, entry.name);
                const subDirFull = path.join(rootDir, subDir);
                // Check for pyproject.toml or setup.py in the subdirectory
                if (fileExists(path.join(subDirFull, "pyproject.toml")) ||
                    fileExists(path.join(subDirFull, "setup.py"))) {
                    modules.push({
                        name: entry.name,
                        path: subDir,
                        sourceDirectories: detectPythonSourceDirs(rootDir, subDir, entry.name),
                        buildFile: fileExists(path.join(subDirFull, "pyproject.toml"))
                            ? path.join(subDir, "pyproject.toml")
                            : path.join(subDir, "setup.py"),
                        language: "python",
                    });
                }
            }
        }
        catch {
            // Skip unreadable directories
        }
    }
    // If monorepo modules found, return them
    if (modules.length > 0)
        return modules;
    // Fallback: single module
    return [];
}
// ---------------------------------------------------------------------------
// Flat project fallback
// ---------------------------------------------------------------------------
/**
 * Create a single `root` module as a fallback when no modules are detected.
 */
function createRootModule(rootDir, projectType, buildFile) {
    const sourceDirs = detectSourceDirs(rootDir, ".", "root", projectType);
    return {
        name: "root",
        path: ".",
        sourceDirectories: sourceDirs,
        buildFile: buildFile !== "none" ? buildFile : null,
        language: null,
    };
}
// ---------------------------------------------------------------------------
// Main discovery function
// ---------------------------------------------------------------------------
/**
 * Discover all modules (subprojects) in the workspace based on the detected
 * build system.
 *
 * @param rootDir - Absolute or relative path to the project root.
 * @param detectionResult - The result from `detectProjectType()`.
 * @returns An array of discovered {@link Module} objects.
 */
export function discoverModules(rootDir, detectionResult) {
    const resolvedRoot = path.resolve(rootDir);
    const { projectType, buildFile } = detectionResult;
    let modules;
    try {
        switch (projectType) {
            case "gradle-kotlin":
            case "gradle-java":
                modules = discoverGradleModules(resolvedRoot);
                break;
            case "maven-java":
                modules = discoverMavenModules(resolvedRoot);
                break;
            case "npm-typescript":
            case "npm-javascript":
                modules = discoverNpmModules(resolvedRoot);
                break;
            case "cargo-rust":
                modules = discoverCargoModules(resolvedRoot);
                break;
            case "go-module":
                modules = discoverGoModules(resolvedRoot);
                break;
            case "dotnet":
                modules = discoverDotnetModules(resolvedRoot, buildFile);
                break;
            case "python":
                modules = discoverPythonModules(resolvedRoot);
                break;
            default:
                modules = [];
                break;
        }
    }
    catch {
        // If settings file can't be read or any error occurs, return empty
        // which triggers the flat project fallback below.
        modules = [];
    }
    // Flat project fallback: if no modules detected, create single root module
    if (modules.length === 0) {
        modules = [createRootModule(resolvedRoot, projectType, buildFile)];
    }
    process.stdout.write(`[Code-Index] INFO: Module discovery — found ${modules.length} module(s) for ${projectType}\n`);
    return modules;
}
//# sourceMappingURL=module-discovery.js.map