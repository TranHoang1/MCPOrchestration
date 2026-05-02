/**
 * Full Indexer for the Code Intelligence System.
 *
 * Orchestrates a complete re-index of the workspace:
 * 1. Load configuration
 * 2. Auto-detect project type
 * 3. Discover modules
 * 4. Scan and parse all source files
 * 5. Write index metadata
 * 6. Generate analysis files (project-structure.md + per-module .md)
 * 7. Output KB ingestion payloads
 */
import * as fs from "node:fs";
import * as path from "node:path";
import { loadConfig } from "./config-loader.js";
import { detectProjectType } from "./project-detector.js";
import { discoverModules } from "./module-discovery.js";
import { scanFiles } from "./file-scanner.js";
import { parseFile } from "./file-parser.js";
import { writeMetadata } from "./metadata-helpers.js";
import { generateProjectStructure, generateModuleAnalysis, } from "./analysis-generator.js";
// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------
const METADATA_FILENAME = "index-metadata.json";
const KB_PAYLOADS_FILENAME = "kb-payloads.json";
// ---------------------------------------------------------------------------
// Pattern detection helpers
// ---------------------------------------------------------------------------
/**
 * Detect the dependency injection style from parsed classes and imports.
 */
function detectDiStyle(classes, functions, imports) {
    const allAnnotations = [
        ...classes.flatMap((c) => c.annotations),
        ...functions.flatMap((f) => f.annotations),
    ];
    const allText = [...allAnnotations, ...imports].join(" ");
    if (allText.includes("@Inject") || allText.includes("@Autowired")) {
        return "field injection";
    }
    // Check for constructor injection patterns — classes with constructor params
    // that look like injected dependencies
    for (const fn of functions) {
        if (fn.name === "constructor" && fn.parameters.length > 0) {
            return "constructor injection";
        }
    }
    // Check for Inject/Autowired in annotations
    for (const annotation of allAnnotations) {
        if (annotation.includes("Inject") ||
            annotation.includes("Autowired")) {
            return "field injection";
        }
    }
    return "none";
}
/**
 * Detect the error handling style from imports and class names.
 */
function detectErrorHandling(classes, imports) {
    const allText = [...imports, ...classes.map((c) => c.name)].join(" ");
    if (allText.includes("Result") || allText.includes("Either")) {
        return "Result type";
    }
    if (allText.includes("ExceptionHandler") ||
        allText.includes("@ExceptionHandler") ||
        allText.includes("ControllerAdvice")) {
        return "exception handler";
    }
    if (allText.includes("try") ||
        allText.includes("catch") ||
        allText.includes("Exception")) {
        return "try-catch";
    }
    return "unknown";
}
/**
 * Detect naming conventions from class names.
 */
function detectNaming(classes) {
    const suffixes = ["Controller", "Service", "Repository"];
    const found = [];
    for (const suffix of suffixes) {
        if (classes.some((c) => c.name.endsWith(suffix))) {
            found.push(`*${suffix}`);
        }
    }
    return found.length > 0 ? found.join(", ") : "unknown";
}
/**
 * Detect the logging framework from imports.
 */
function detectLogging(imports) {
    const allImports = imports.join(" ");
    if (allImports.includes("slf4j") || allImports.includes("SLF4J")) {
        return "SLF4J";
    }
    if (allImports.includes("log4j") || allImports.includes("Log4j")) {
        return "Log4j";
    }
    if (allImports.includes("console")) {
        return "console.log";
    }
    return "unknown";
}
/**
 * Detect the testing framework from imports.
 */
function detectTesting(imports) {
    const allImports = imports.join(" ");
    if (allImports.includes("junit") ||
        allImports.includes("org.junit") ||
        allImports.includes("JUnit")) {
        return "JUnit";
    }
    if (allImports.includes("jest") || allImports.includes("@jest")) {
        return "Jest";
    }
    if (allImports.includes("pytest") || allImports.includes("unittest")) {
        return "pytest";
    }
    if (allImports.includes("vitest")) {
        return "vitest";
    }
    return "unknown";
}
/**
 * Detect coding patterns from aggregated parse results for a module.
 */
function detectPatterns(classes, functions, imports) {
    return {
        diStyle: detectDiStyle(classes, functions, imports),
        errorHandling: detectErrorHandling(classes, imports),
        naming: detectNaming(classes),
        logging: detectLogging(imports),
        testing: detectTesting(imports),
    };
}
/**
 * Infer the purpose of a module from its class names, package names, or module name.
 */
function inferModulePurpose(moduleName, classes, packageNames) {
    const allNames = [
        moduleName,
        ...classes.map((c) => c.name),
        ...packageNames,
    ]
        .join(" ")
        .toLowerCase();
    if (allNames.includes("api") || allNames.includes("controller")) {
        return "API layer";
    }
    if (allNames.includes("service") || allNames.includes("business")) {
        return "Business logic";
    }
    if (allNames.includes("repository") ||
        allNames.includes("dao") ||
        allNames.includes("data")) {
        return "Data access";
    }
    if (allNames.includes("config") || allNames.includes("configuration")) {
        return "Configuration";
    }
    if (allNames.includes("common") || allNames.includes("shared")) {
        return "Shared utilities";
    }
    if (allNames.includes("test") || allNames.includes("spec")) {
        return "Testing";
    }
    if (allNames.includes("web") || allNames.includes("ui")) {
        return "Web/UI layer";
    }
    if (allNames.includes("model") || allNames.includes("domain")) {
        return "Domain model";
    }
    return "Application module";
}
// ---------------------------------------------------------------------------
// Main orchestration
// ---------------------------------------------------------------------------
/**
 * Run a full index of the workspace.
 *
 * Scans all source files across all modules, rebuilds the entire Code Index
 * from scratch, generates all Analysis Files, and outputs structured data
 * for KB ingestion.
 *
 * @param rootDir    - Absolute or relative path to the project root.
 *                     Defaults to `process.cwd()`.
 * @param configPath - Optional override for the config file location.
 * @returns An {@link IndexResult} summarising the indexing run.
 */
export function runFullIndex(rootDir, configPath) {
    const startTime = Date.now();
    const resolvedRoot = path.resolve(rootDir ?? process.cwd());
    // --- Step 1: Load configuration ---
    const defaultConfigPath = path.join(resolvedRoot, ".analysis/code-intelligence/index-config.json");
    const config = loadConfig(configPath ?? defaultConfigPath);
    // --- Step 2: Delete existing metadata ---
    const metadataPath = path.join(resolvedRoot, ".analysis/code-intelligence", METADATA_FILENAME);
    try {
        fs.unlinkSync(metadataPath);
    }
    catch {
        // File may not exist — that's fine.
    }
    // --- Step 3: Auto-detect project type ---
    const detectionResult = detectProjectType(resolvedRoot);
    // --- Step 4: Discover modules ---
    const modules = discoverModules(resolvedRoot, detectionResult);
    // --- Step 5: Scan and parse all files per module ---
    const allFileEntries = {};
    const modulesData = [];
    let totalClasses = 0;
    let totalFunctions = 0;
    let parseErrors = 0;
    let totalFiles = 0;
    // Count total files across all modules for progress logging
    const moduleScans = [];
    let grandTotalFiles = 0;
    for (const mod of modules) {
        try {
            const scannedFiles = scanFiles(config, mod.sourceDirectories, resolvedRoot);
            moduleScans.push({ module: mod, files: scannedFiles });
            grandTotalFiles += scannedFiles.length;
        }
        catch (err) {
            process.stderr.write(`[Code-Index] ERROR: Scan failed — ${mod.name} — ${err.message}\n`);
            moduleScans.push({ module: mod, files: [] });
        }
    }
    let filesProcessed = 0;
    for (const { module: mod, files: scannedFiles } of moduleScans) {
        const moduleClasses = [];
        const moduleFunctions = [];
        const moduleImports = [];
        const packageNameSet = new Set();
        const dependencySet = new Set();
        for (const file of scannedFiles) {
            filesProcessed++;
            const absolutePath = path.resolve(resolvedRoot, file.filePath);
            // Log progress
            process.stdout.write(`[Code-Index] INFO: Indexing — ${filesProcessed}/${grandTotalFiles} files (${mod.name})\n`);
            // Parse the file
            let parseResult;
            try {
                parseResult = parseFile(absolutePath, file.language, mod.name);
            }
            catch (err) {
                process.stderr.write(`[Code-Index] ERROR: Parse failed — ${file.filePath} — ${err.message}\n`);
                parseResult = {
                    filePath: file.filePath,
                    language: file.language,
                    moduleName: mod.name,
                    packageName: "",
                    classes: [],
                    functions: [],
                    imports: [],
                    indexingStatus: "parse_error",
                    errorMessage: err.message,
                };
            }
            // Track parse errors
            if (parseResult.indexingStatus === "parse_error") {
                parseErrors++;
            }
            // Aggregate classes and functions
            moduleClasses.push(...parseResult.classes);
            moduleFunctions.push(...parseResult.functions);
            moduleImports.push(...parseResult.imports);
            totalClasses += parseResult.classes.length;
            totalFunctions += parseResult.functions.length;
            // Track package names
            if (parseResult.packageName) {
                packageNameSet.add(parseResult.packageName);
            }
            // Track cross-module dependencies from imports
            for (const imp of parseResult.imports) {
                for (const otherMod of modules) {
                    if (otherMod.name !== mod.name &&
                        imp.includes(otherMod.name)) {
                        dependencySet.add(otherMod.name);
                    }
                }
            }
            // Build FileEntry for metadata
            const timestamp = new Date().toISOString();
            allFileEntries[file.filePath] = {
                contentHash: file.contentHash,
                lastIndexedTimestamp: timestamp,
                language: file.language,
                moduleName: mod.name,
                indexingStatus: parseResult.indexingStatus === "parse_error"
                    ? "parse_error"
                    : "success",
            };
            totalFiles++;
        }
        // Build PackageInfo objects from unique package names
        const packages = Array.from(packageNameSet).map((pkgName) => ({
            name: pkgName,
            path: pkgName.replace(/\./g, "/"),
            purpose: inferPackagePurpose(pkgName),
        }));
        // Detect patterns for this module
        const patterns = detectPatterns(moduleClasses, moduleFunctions, moduleImports);
        // Infer module purpose
        const purpose = inferModulePurpose(mod.name, moduleClasses, Array.from(packageNameSet));
        // Build ModuleData
        const moduleData = {
            name: mod.name,
            path: mod.path,
            language: mod.language ?? detectionResult.primaryLanguage,
            framework: detectionResult.framework,
            dependencies: Array.from(dependencySet),
            sourceFileCount: scannedFiles.length,
            packages,
            classes: moduleClasses,
            functions: moduleFunctions,
            patterns,
            purpose,
        };
        modulesData.push(moduleData);
    }
    // --- Step 6: Build and write IndexMetadata ---
    const metadata = {
        version: "1.0",
        lastFullIndexTimestamp: new Date().toISOString(),
        projectName: path.basename(resolvedRoot),
        projectType: detectionResult.projectType,
        totalFiles,
        files: allFileEntries,
    };
    const outputDir = path.join(resolvedRoot, ".analysis/code-intelligence");
    try {
        writeMetadata(metadata, metadataPath);
    }
    catch (err) {
        process.stderr.write(`[Code-Index] ERROR: Metadata write failed — ${err.message}\n`);
    }
    // --- Step 7: Build ProjectInfo and generate analysis files ---
    const projectInfo = {
        projectName: path.basename(resolvedRoot),
        projectType: detectionResult.projectType,
        primaryLanguage: detectionResult.primaryLanguage,
        framework: detectionResult.framework,
    };
    try {
        generateProjectStructure(modulesData, projectInfo, outputDir);
    }
    catch (err) {
        process.stderr.write(`[Code-Index] ERROR: Project structure generation failed — ${err.message}\n`);
    }
    // Generate per-module analysis files
    for (const moduleData of modulesData) {
        try {
            generateModuleAnalysis(moduleData, [], outputDir);
        }
        catch (err) {
            process.stderr.write(`[Code-Index] ERROR: Module analysis generation failed — ${moduleData.name} — ${err.message}\n`);
        }
    }
    // --- Step 8: Output KB ingestion payloads ---
    const kbPayloads = modulesData.map((mod) => ({
        title: `Code Index — ${mod.name}`,
        content: buildModuleSummary(mod),
        tags: `code-index, ${mod.name}, ${mod.language}`,
        project: projectInfo.projectName,
    }));
    const kbPayloadsPath = path.join(outputDir, KB_PAYLOADS_FILENAME);
    try {
        fs.mkdirSync(path.dirname(kbPayloadsPath), { recursive: true });
        fs.writeFileSync(kbPayloadsPath, JSON.stringify(kbPayloads, null, 2), "utf-8");
    }
    catch (err) {
        process.stderr.write(`[Code-Index] ERROR: KB payloads write failed — ${err.message}\n`);
    }
    // --- Step 9: Calculate elapsed time and log summary ---
    const elapsedMs = Date.now() - startTime;
    process.stdout.write(`[Code-Index] INFO: Full index complete — ${totalFiles} files, ${modules.length} modules, ${totalClasses} classes, ${totalFunctions} functions, ${parseErrors} errors, ${elapsedMs}ms\n`);
    return {
        totalFiles,
        totalModules: modules.length,
        totalClasses,
        totalFunctions,
        parseErrors,
        elapsedMs,
    };
}
// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------
/**
 * Infer a package's purpose from its name.
 */
function inferPackagePurpose(packageName) {
    const lower = packageName.toLowerCase();
    if (lower.includes("controller") || lower.includes("api")) {
        return "HTTP request handling";
    }
    if (lower.includes("service")) {
        return "Business logic";
    }
    if (lower.includes("repository") || lower.includes("dao")) {
        return "Data access";
    }
    if (lower.includes("model") || lower.includes("domain") || lower.includes("entity")) {
        return "Domain model";
    }
    if (lower.includes("dto")) {
        return "Data transfer objects";
    }
    if (lower.includes("config") || lower.includes("configuration")) {
        return "Configuration";
    }
    if (lower.includes("util") || lower.includes("helper") || lower.includes("common")) {
        return "Utility functions";
    }
    if (lower.includes("security") || lower.includes("auth")) {
        return "Security/Authentication";
    }
    if (lower.includes("exception") || lower.includes("error")) {
        return "Error handling";
    }
    if (lower.includes("mapper") || lower.includes("converter")) {
        return "Data mapping";
    }
    if (lower.includes("test")) {
        return "Testing";
    }
    return "Application logic";
}
/**
 * Build a text summary of a module for KB ingestion.
 */
function buildModuleSummary(mod) {
    const lines = [];
    lines.push(`Module: ${mod.name}`);
    lines.push(`Language: ${mod.language}`);
    lines.push(`Framework: ${mod.framework ?? "none"}`);
    lines.push(`Purpose: ${mod.purpose}`);
    lines.push(`Source files: ${mod.sourceFileCount}`);
    lines.push(`Classes: ${mod.classes.length}`);
    lines.push(`Functions: ${mod.functions.length}`);
    lines.push("");
    if (mod.packages.length > 0) {
        lines.push("Packages:");
        for (const pkg of mod.packages) {
            lines.push(`  - ${pkg.name}: ${pkg.purpose}`);
        }
        lines.push("");
    }
    if (mod.classes.length > 0) {
        lines.push("Key classes:");
        for (const cls of mod.classes.slice(0, 20)) {
            lines.push(`  - ${cls.name} (${cls.visibility})`);
        }
        if (mod.classes.length > 20) {
            lines.push(`  ... and ${mod.classes.length - 20} more`);
        }
        lines.push("");
    }
    if (mod.dependencies.length > 0) {
        lines.push(`Dependencies: ${mod.dependencies.join(", ")}`);
        lines.push("");
    }
    lines.push("Detected patterns:");
    lines.push(`  DI Style: ${mod.patterns.diStyle}`);
    lines.push(`  Error Handling: ${mod.patterns.errorHandling}`);
    lines.push(`  Naming: ${mod.patterns.naming}`);
    lines.push(`  Logging: ${mod.patterns.logging}`);
    lines.push(`  Testing: ${mod.patterns.testing}`);
    return lines.join("\n");
}
// ---------------------------------------------------------------------------
// CLI entry point
// ---------------------------------------------------------------------------
const isMainModule = process.argv[1]?.replace(/\\/g, "/").endsWith("full-indexer.ts") ||
    process.argv[1]?.replace(/\\/g, "/").endsWith("full-indexer.js");
if (isMainModule) {
    const rootDir = process.argv[2] || process.cwd();
    const result = runFullIndex(rootDir);
    console.log(JSON.stringify(result, null, 2));
}
//# sourceMappingURL=full-indexer.js.map