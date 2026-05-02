/**
 * Incremental Indexer for the Code Intelligence System.
 *
 * Re-indexes only files that have changed since the last indexing run:
 * - Hook-triggered mode: receives explicit list of changed files
 * - Agent on-demand mode: scans all files, compares hashes against metadata
 *
 * For each changed file it re-parses, updates metadata, and regenerates
 * only the affected module's Analysis File and project-structure row.
 */

import * as fs from "node:fs";
import * as path from "node:path";
import type {
  IndexConfig,
  IndexMetadata,
  IndexResult,
  FileEntry,
  ModuleData,
  ProjectInfo,
  PackageInfo,
  DetectedPatterns,
  ParseResult,
  ClassInfo,
  FunctionInfo,
  ScannedFile,
  AnnotationRow,
  Module,
} from "./types.js";
import { loadConfig } from "./config-loader.js";
import { detectProjectType } from "./project-detector.js";
import { discoverModules } from "./module-discovery.js";
import { scanFiles, computeHash, filterFile, mapExtensionToLanguage } from "./file-scanner.js";
import { parseFile } from "./file-parser.js";
import { readMetadata, writeMetadata } from "./metadata-helpers.js";
import {
  generateProjectStructure,
  generateModuleAnalysis,
} from "./analysis-generator.js";
import { runFullIndex as runFullIndexFn } from "./full-indexer.js";

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const KB_PAYLOADS_FILENAME = "kb-payloads.json";

// ---------------------------------------------------------------------------
// Pattern detection helpers (mirrored from full-indexer.ts)
// ---------------------------------------------------------------------------

function detectDiStyle(
  classes: ClassInfo[],
  functions: FunctionInfo[],
  imports: string[],
): string {
  const allAnnotations = [
    ...classes.flatMap((c) => c.annotations),
    ...functions.flatMap((f) => f.annotations),
  ];
  const allText = [...allAnnotations, ...imports].join(" ");

  if (allText.includes("@Inject") || allText.includes("@Autowired")) {
    return "field injection";
  }
  for (const fn of functions) {
    if (fn.name === "constructor" && fn.parameters.length > 0) {
      return "constructor injection";
    }
  }
  for (const annotation of allAnnotations) {
    if (annotation.includes("Inject") || annotation.includes("Autowired")) {
      return "field injection";
    }
  }
  return "none";
}

function detectErrorHandling(classes: ClassInfo[], imports: string[]): string {
  const allText = [...imports, ...classes.map((c) => c.name)].join(" ");
  if (allText.includes("Result") || allText.includes("Either")) return "Result type";
  if (
    allText.includes("ExceptionHandler") ||
    allText.includes("@ExceptionHandler") ||
    allText.includes("ControllerAdvice")
  )
    return "exception handler";
  if (allText.includes("try") || allText.includes("catch") || allText.includes("Exception"))
    return "try-catch";
  return "unknown";
}

function detectNaming(classes: ClassInfo[]): string {
  const suffixes = ["Controller", "Service", "Repository"];
  const found: string[] = [];
  for (const suffix of suffixes) {
    if (classes.some((c) => c.name.endsWith(suffix))) found.push(`*${suffix}`);
  }
  return found.length > 0 ? found.join(", ") : "unknown";
}

function detectLogging(imports: string[]): string {
  const allImports = imports.join(" ");
  if (allImports.includes("slf4j") || allImports.includes("SLF4J")) return "SLF4J";
  if (allImports.includes("log4j") || allImports.includes("Log4j")) return "Log4j";
  if (allImports.includes("console")) return "console.log";
  return "unknown";
}

function detectTesting(imports: string[]): string {
  const allImports = imports.join(" ");
  if (allImports.includes("junit") || allImports.includes("org.junit") || allImports.includes("JUnit"))
    return "JUnit";
  if (allImports.includes("jest") || allImports.includes("@jest")) return "Jest";
  if (allImports.includes("pytest") || allImports.includes("unittest")) return "pytest";
  if (allImports.includes("vitest")) return "vitest";
  return "unknown";
}

function detectPatterns(
  classes: ClassInfo[],
  functions: FunctionInfo[],
  imports: string[],
): DetectedPatterns {
  return {
    diStyle: detectDiStyle(classes, functions, imports),
    errorHandling: detectErrorHandling(classes, imports),
    naming: detectNaming(classes),
    logging: detectLogging(imports),
    testing: detectTesting(imports),
  };
}

function inferModulePurpose(
  moduleName: string,
  classes: ClassInfo[],
  packageNames: string[],
): string {
  const allNames = [moduleName, ...classes.map((c) => c.name), ...packageNames]
    .join(" ")
    .toLowerCase();
  if (allNames.includes("api") || allNames.includes("controller")) return "API layer";
  if (allNames.includes("service") || allNames.includes("business")) return "Business logic";
  if (allNames.includes("repository") || allNames.includes("dao") || allNames.includes("data"))
    return "Data access";
  if (allNames.includes("config") || allNames.includes("configuration")) return "Configuration";
  if (allNames.includes("common") || allNames.includes("shared")) return "Shared utilities";
  if (allNames.includes("test") || allNames.includes("spec")) return "Testing";
  if (allNames.includes("web") || allNames.includes("ui")) return "Web/UI layer";
  if (allNames.includes("model") || allNames.includes("domain")) return "Domain model";
  return "Application module";
}

function inferPackagePurpose(packageName: string): string {
  const lower = packageName.toLowerCase();
  if (lower.includes("controller") || lower.includes("api")) return "HTTP request handling";
  if (lower.includes("service")) return "Business logic";
  if (lower.includes("repository") || lower.includes("dao")) return "Data access";
  if (lower.includes("model") || lower.includes("domain") || lower.includes("entity"))
    return "Domain model";
  if (lower.includes("dto")) return "Data transfer objects";
  if (lower.includes("config") || lower.includes("configuration")) return "Configuration";
  if (lower.includes("util") || lower.includes("helper") || lower.includes("common"))
    return "Utility functions";
  if (lower.includes("security") || lower.includes("auth")) return "Security/Authentication";
  if (lower.includes("exception") || lower.includes("error")) return "Error handling";
  if (lower.includes("mapper") || lower.includes("converter")) return "Data mapping";
  if (lower.includes("test")) return "Testing";
  return "Application logic";
}


// ---------------------------------------------------------------------------
// Annotation preservation helpers
// ---------------------------------------------------------------------------

/**
 * Read existing annotations from a module analysis file.
 *
 * Parses the `## Annotations` section and returns all annotation rows.
 * If the annotation-manager module is available, delegates to it;
 * otherwise uses a simple regex-based parser.
 */
function readAnnotationsFromFile(analysisFilePath: string): AnnotationRow[] {
  let content: string;
  try {
    content = fs.readFileSync(analysisFilePath, "utf-8");
  } catch {
    return [];
  }

  const annotations: AnnotationRow[] = [];

  // Find the ## Annotations section
  const annotationSectionIdx = content.indexOf("## Annotations");
  if (annotationSectionIdx === -1) return [];

  const sectionContent = content.slice(annotationSectionIdx);
  const lines = sectionContent.split("\n");

  // Skip header line, separator line, and table header row
  // Format: | Target | Author Agent | Type | Content | Timestamp |
  let headerFound = false;
  for (const line of lines) {
    const trimmed = line.trim();

    // Skip the section heading
    if (trimmed.startsWith("## ")) continue;
    // Skip empty lines
    if (!trimmed) continue;
    // Skip the table header and separator
    if (trimmed.startsWith("|--") || trimmed.startsWith("| Target") || trimmed.startsWith("|--------")) {
      headerFound = true;
      continue;
    }

    // Parse table rows after the header
    if (headerFound && trimmed.startsWith("|")) {
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
 * Mark annotations whose targets no longer exist as [DELETED].
 */
function markDeletedAnnotations(
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

// ---------------------------------------------------------------------------
// Change categorisation
// ---------------------------------------------------------------------------

interface ChangeSet {
  modified: string[];   // Files whose hash differs from metadata
  added: string[];      // Files not present in metadata
  deleted: string[];    // Files in metadata but not on disk
}

/**
 * Determine which files have been modified, added, or deleted by comparing
 * the current scan results against stored metadata.
 */
function detectChanges(
  currentFiles: Map<string, ScannedFile>,
  metadata: IndexMetadata,
): ChangeSet {
  const modified: string[] = [];
  const added: string[] = [];
  const deleted: string[] = [];

  // Check current files against metadata
  for (const [filePath, scanned] of currentFiles) {
    const existing = metadata.files[filePath];
    if (!existing) {
      added.push(filePath);
    } else if (existing.contentHash !== scanned.contentHash) {
      modified.push(filePath);
    }
    // else: unchanged — skip
  }

  // Check metadata entries that are no longer on disk
  for (const filePath of Object.keys(metadata.files)) {
    if (!currentFiles.has(filePath)) {
      deleted.push(filePath);
    }
  }

  return { modified, added, deleted };
}

/**
 * Determine changes for hook-triggered mode where we receive explicit
 * file paths. We check each file's existence and hash against metadata.
 */
function detectChangesForFiles(
  changedFiles: string[],
  metadata: IndexMetadata,
  rootDir: string,
  config: IndexConfig,
): ChangeSet {
  const modified: string[] = [];
  const added: string[] = [];
  const deleted: string[] = [];

  for (const filePath of changedFiles) {
    // Normalise to relative path
    const relativePath = path.isAbsolute(filePath)
      ? path.relative(rootDir, filePath)
      : filePath;

    const absolutePath = path.resolve(rootDir, relativePath);

    // Check if file exists on disk
    let fileExists: boolean;
    try {
      fs.accessSync(absolutePath, fs.constants.R_OK);
      fileExists = true;
    } catch {
      fileExists = false;
    }

    if (!fileExists) {
      // File was deleted
      if (metadata.files[relativePath]) {
        deleted.push(relativePath);
      }
      continue;
    }

    // Check if file should be indexed (extension/exclusion filters)
    if (!filterFile(relativePath, config)) {
      continue;
    }

    const existing = metadata.files[relativePath];
    const currentHash = computeHash(absolutePath);

    if (!existing) {
      added.push(relativePath);
    } else if (existing.contentHash !== currentHash) {
      modified.push(relativePath);
    }
    // else: unchanged — skip
  }

  return { modified, added, deleted };
}

// ---------------------------------------------------------------------------
// Module data rebuilding
// ---------------------------------------------------------------------------

/**
 * Rebuild full ModuleData for a specific module by re-reading all its
 * file entries from metadata and re-parsing them.
 *
 * This is needed to regenerate the module's analysis file accurately.
 */
function rebuildModuleData(
  moduleName: string,
  metadata: IndexMetadata,
  rootDir: string,
  modules: Module[],
  detectionResult: { primaryLanguage: string; framework: string | null },
): ModuleData {
  const moduleClasses: ClassInfo[] = [];
  const moduleFunctions: FunctionInfo[] = [];
  const moduleImports: string[] = [];
  const packageNameSet = new Set<string>();
  const dependencySet = new Set<string>();
  let sourceFileCount = 0;

  // Collect all files belonging to this module from metadata
  for (const [filePath, entry] of Object.entries(metadata.files)) {
    if (entry.moduleName !== moduleName) continue;

    sourceFileCount++;
    const absolutePath = path.resolve(rootDir, filePath);

    // Re-parse the file to get full class/function data
    let parseResult: ParseResult;
    try {
      parseResult = parseFile(absolutePath, entry.language, moduleName);
    } catch {
      // If parse fails, skip this file's contribution to module data
      continue;
    }

    moduleClasses.push(...parseResult.classes);
    moduleFunctions.push(...parseResult.functions);
    moduleImports.push(...parseResult.imports);

    if (parseResult.packageName) {
      packageNameSet.add(parseResult.packageName);
    }

    // Track cross-module dependencies
    for (const imp of parseResult.imports) {
      for (const otherMod of modules) {
        if (otherMod.name !== moduleName && imp.includes(otherMod.name)) {
          dependencySet.add(otherMod.name);
        }
      }
    }
  }

  const packages: PackageInfo[] = Array.from(packageNameSet).map((pkgName) => ({
    name: pkgName,
    path: pkgName.replace(/\./g, "/"),
    purpose: inferPackagePurpose(pkgName),
  }));

  const patterns = detectPatterns(moduleClasses, moduleFunctions, moduleImports);
  const purpose = inferModulePurpose(moduleName, moduleClasses, Array.from(packageNameSet));

  // Find the module's path from the modules list
  const moduleInfo = modules.find((m) => m.name === moduleName);

  return {
    name: moduleName,
    path: moduleInfo?.path ?? ".",
    language: moduleInfo?.language ?? detectionResult.primaryLanguage,
    framework: detectionResult.framework,
    dependencies: Array.from(dependencySet),
    sourceFileCount,
    packages,
    classes: moduleClasses,
    functions: moduleFunctions,
    patterns,
    purpose,
  };
}

/**
 * Build a text summary of a module for KB ingestion.
 */
function buildModuleSummary(mod: ModuleData): string {
  const lines: string[] = [];
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
// Main orchestration
// ---------------------------------------------------------------------------

/**
 * Run an incremental index of the workspace.
 *
 * Re-indexes only files that have changed since the last indexing run.
 * If no metadata exists, falls back to a full index via the full-indexer.
 *
 * @param rootDir      - Absolute or relative path to the project root.
 *                       Defaults to `process.cwd()`.
 * @param changedFiles - Optional list of changed file paths (hook-triggered mode).
 *                       If undefined, scans all files and compares hashes (on-demand mode).
 * @param configPath   - Optional override for the config file location.
 * @returns An {@link IndexResult} summarising the incremental indexing run.
 */
export function runIncrementalIndex(
  rootDir?: string,
  changedFiles?: string[],
  configPath?: string,
): IndexResult {
  const startTime = Date.now();
  const resolvedRoot = path.resolve(rootDir ?? process.cwd());

  // --- Step 1: Load configuration ---
  const defaultConfigPath = path.join(resolvedRoot, ".analysis/code-intelligence/index-config.json");
  const config: IndexConfig = loadConfig(configPath ?? defaultConfigPath);

  // --- Step 2: Read existing metadata ---
  const metadataPath = path.join(
    resolvedRoot,
    ".analysis/code-intelligence/index-metadata.json",
  );
  const metadata = readMetadata(metadataPath);

  // If metadata is null (missing/corrupted), fall back to full index
  if (metadata === null) {
    process.stdout.write(
      "[Code-Index] INFO: No valid metadata found — falling back to full index\n",
    );
    // Import and run full index dynamically to avoid circular dependency issues
    // We re-implement the core logic here instead
    return runFullReindex(resolvedRoot, config, configPath);
  }

  // --- Step 3: Detect project type and modules ---
  const detectionResult = detectProjectType(resolvedRoot);
  const modules = discoverModules(resolvedRoot, detectionResult);

  // --- Step 3b: Detect new and removed modules ---
  // Compare discovered modules against modules known in metadata.
  const metadataModuleNames = new Set<string>();
  for (const entry of Object.values(metadata.files)) {
    metadataModuleNames.add(entry.moduleName);
  }
  const discoveredModuleNames = new Set(modules.map((m) => m.name));

  // New modules: discovered but not in metadata
  const newModuleNames = new Set<string>();
  for (const mod of modules) {
    if (!metadataModuleNames.has(mod.name)) {
      newModuleNames.add(mod.name);
    }
  }

  // Removed modules: in metadata but not discovered
  const removedModuleNames = new Set<string>();
  for (const name of metadataModuleNames) {
    if (!discoveredModuleNames.has(name)) {
      removedModuleNames.add(name);
    }
  }

  if (newModuleNames.size > 0) {
    process.stdout.write(
      `[Code-Index] INFO: New module(s) detected — ${[...newModuleNames].join(", ")}\n`,
    );
  }
  if (removedModuleNames.size > 0) {
    process.stdout.write(
      `[Code-Index] INFO: Removed module(s) detected — ${[...removedModuleNames].join(", ")}\n`,
    );
  }

  // --- Step 4: Determine what changed ---
  let changes: ChangeSet;

  if (changedFiles !== undefined) {
    // Hook-triggered mode: process only the specified files
    changes = detectChangesForFiles(changedFiles, metadata, resolvedRoot, config);
  } else {
    // Agent on-demand mode: scan all files and compare hashes
    const allScannedFiles = new Map<string, ScannedFile>();

    for (const mod of modules) {
      try {
        const scanned = scanFiles(config, mod.sourceDirectories, resolvedRoot);
        for (const file of scanned) {
          allScannedFiles.set(file.filePath, file);
        }
      } catch (err: unknown) {
        process.stderr.write(
          `[Code-Index] ERROR: Scan failed — ${mod.name} — ${(err as Error).message}\n`,
        );
      }
    }

    changes = detectChanges(allScannedFiles, metadata);
  }

  const totalChanges = changes.modified.length + changes.added.length + changes.deleted.length;
  const hasModuleLifecycleChanges = newModuleNames.size > 0 || removedModuleNames.size > 0;

  if (totalChanges === 0 && !hasModuleLifecycleChanges) {
    const elapsedMs = Date.now() - startTime;
    process.stdout.write(
      `[Code-Index] INFO: Incremental index — no changes detected (${elapsedMs}ms)\n`,
    );
    return {
      totalFiles: metadata.totalFiles,
      totalModules: modules.length,
      totalClasses: 0,
      totalFunctions: 0,
      parseErrors: 0,
      elapsedMs,
    };
  }

  process.stdout.write(
    `[Code-Index] INFO: Incremental index — ${changes.modified.length} modified, ${changes.added.length} added, ${changes.deleted.length} deleted\n`,
  );

  // --- Step 5: Process changes and update metadata ---
  let totalClasses = 0;
  let totalFunctions = 0;
  let parseErrors = 0;
  const affectedModules = new Set<string>();

  // Process modified files
  for (const filePath of changes.modified) {
    const absolutePath = path.resolve(resolvedRoot, filePath);
    const existingEntry = metadata.files[filePath];
    const language = existingEntry?.language ?? mapExtensionToLanguage(filePath);
    const moduleName = existingEntry?.moduleName ?? resolveModuleName(filePath, modules);

    let parseResult: ParseResult;
    try {
      parseResult = parseFile(absolutePath, language, moduleName);
    } catch (err: unknown) {
      process.stderr.write(
        `[Code-Index] ERROR: Parse failed — ${filePath} — ${(err as Error).message}\n`,
      );
      parseResult = {
        filePath,
        language,
        moduleName,
        packageName: "",
        classes: [],
        functions: [],
        imports: [],
        indexingStatus: "parse_error",
        errorMessage: (err as Error).message,
      };
    }

    if (parseResult.indexingStatus === "parse_error") {
      parseErrors++;
    }

    totalClasses += parseResult.classes.length;
    totalFunctions += parseResult.functions.length;

    // Update metadata entry
    const newHash = computeHash(absolutePath);
    metadata.files[filePath] = {
      contentHash: newHash,
      lastIndexedTimestamp: new Date().toISOString(),
      language,
      moduleName,
      indexingStatus: parseResult.indexingStatus === "parse_error" ? "parse_error" : "success",
    };

    affectedModules.add(moduleName);

    process.stdout.write(
      `[Code-Index] INFO: Re-indexed (modified) — ${filePath}\n`,
    );
  }

  // Process added files
  for (const filePath of changes.added) {
    const absolutePath = path.resolve(resolvedRoot, filePath);
    const language = mapExtensionToLanguage(filePath);
    const moduleName = resolveModuleName(filePath, modules);

    let parseResult: ParseResult;
    try {
      parseResult = parseFile(absolutePath, language, moduleName);
    } catch (err: unknown) {
      process.stderr.write(
        `[Code-Index] ERROR: Parse failed — ${filePath} — ${(err as Error).message}\n`,
      );
      parseResult = {
        filePath,
        language,
        moduleName,
        packageName: "",
        classes: [],
        functions: [],
        imports: [],
        indexingStatus: "parse_error",
        errorMessage: (err as Error).message,
      };
    }

    if (parseResult.indexingStatus === "parse_error") {
      parseErrors++;
    }

    totalClasses += parseResult.classes.length;
    totalFunctions += parseResult.functions.length;

    // Add new metadata entry
    const contentHash = computeHash(absolutePath);
    metadata.files[filePath] = {
      contentHash,
      lastIndexedTimestamp: new Date().toISOString(),
      language,
      moduleName,
      indexingStatus: parseResult.indexingStatus === "parse_error" ? "parse_error" : "success",
    };

    affectedModules.add(moduleName);

    process.stdout.write(
      `[Code-Index] INFO: Indexed (new) — ${filePath}\n`,
    );
  }

  // Process deleted files
  for (const filePath of changes.deleted) {
    const existingEntry = metadata.files[filePath];
    if (existingEntry) {
      affectedModules.add(existingEntry.moduleName);
    }

    // Remove from metadata
    delete metadata.files[filePath];

    process.stdout.write(
      `[Code-Index] INFO: Removed (deleted) — ${filePath}\n`,
    );
  }

  // --- Step 5b: Handle new modules — scan and index all their files ---
  for (const mod of modules) {
    if (!newModuleNames.has(mod.name)) continue;

    try {
      const scannedFiles = scanFiles(config, mod.sourceDirectories, resolvedRoot);

      for (const file of scannedFiles) {
        // Skip if already processed as an added file
        if (metadata.files[file.filePath]) continue;

        const absolutePath = path.resolve(resolvedRoot, file.filePath);

        let parseResult: ParseResult;
        try {
          parseResult = parseFile(absolutePath, file.language, mod.name);
        } catch (err: unknown) {
          process.stderr.write(
            `[Code-Index] ERROR: Parse failed — ${file.filePath} — ${(err as Error).message}\n`,
          );
          parseResult = {
            filePath: file.filePath,
            language: file.language,
            moduleName: mod.name,
            packageName: "",
            classes: [],
            functions: [],
            imports: [],
            indexingStatus: "parse_error",
            errorMessage: (err as Error).message,
          };
        }

        if (parseResult.indexingStatus === "parse_error") {
          parseErrors++;
        }

        totalClasses += parseResult.classes.length;
        totalFunctions += parseResult.functions.length;

        metadata.files[file.filePath] = {
          contentHash: file.contentHash,
          lastIndexedTimestamp: new Date().toISOString(),
          language: file.language,
          moduleName: mod.name,
          indexingStatus: parseResult.indexingStatus === "parse_error" ? "parse_error" : "success",
        };

        process.stdout.write(
          `[Code-Index] INFO: Indexed (new module) — ${file.filePath}\n`,
        );
      }

      affectedModules.add(mod.name);
    } catch (err: unknown) {
      process.stderr.write(
        `[Code-Index] ERROR: New module scan failed — ${mod.name} — ${(err as Error).message}\n`,
      );
    }
  }

  // --- Step 5c: Handle removed modules — clean up metadata and analysis files ---
  const outputDir = path.join(resolvedRoot, ".analysis/code-intelligence");

  for (const removedName of removedModuleNames) {
    // Remove all file entries belonging to the removed module from metadata
    for (const [filePath, entry] of Object.entries(metadata.files)) {
      if (entry.moduleName === removedName) {
        delete metadata.files[filePath];
        process.stdout.write(
          `[Code-Index] INFO: Removed (module cleanup) — ${filePath}\n`,
        );
      }
    }

    // Delete the module's analysis file
    const analysisFilePath = path.join(outputDir, "modules", `${removedName}.md`);
    try {
      if (fs.existsSync(analysisFilePath)) {
        fs.unlinkSync(analysisFilePath);
        process.stdout.write(
          `[Code-Index] INFO: Deleted module analysis — ${removedName}\n`,
        );
      }
    } catch (err: unknown) {
      process.stderr.write(
        `[Code-Index] ERROR: Module analysis deletion failed — ${removedName} — ${(err as Error).message}\n`,
      );
    }
  }

  // --- Step 6: Regenerate analysis files for affected modules only ---
  // (outputDir already declared in Step 5c)
  const allModulesData: ModuleData[] = [];

  for (const moduleName of affectedModules) {
    try {
      const moduleData = rebuildModuleData(
        moduleName,
        metadata,
        resolvedRoot,
        modules,
        detectionResult,
      );
      allModulesData.push(moduleData);

      // Read existing annotations from the module's analysis file
      const analysisFilePath = path.join(outputDir, "modules", `${moduleName}.md`);
      let annotations = readAnnotationsFromFile(analysisFilePath);

      // For deleted files, mark annotations whose targets no longer exist
      if (changes.deleted.length > 0) {
        const currentTargets = [
          ...moduleData.classes.map((c) => c.name),
          ...moduleData.functions.map((f) => f.name),
        ];
        annotations = markDeletedAnnotations(annotations, currentTargets);
      }

      // Regenerate the module's analysis file
      generateModuleAnalysis(moduleData, annotations, outputDir);

      process.stdout.write(
        `[Code-Index] INFO: Regenerated analysis — ${moduleName}\n`,
      );
    } catch (err: unknown) {
      process.stderr.write(
        `[Code-Index] ERROR: Module analysis regeneration failed — ${moduleName} — ${(err as Error).message}\n`,
      );
    }
  }

  // --- Step 7: Update project-structure.md ---
  // Rebuild all module data for the project structure (we need all modules, not just affected)
  try {
    const allModules = modules.map((mod) => {
      // If this module was affected, use the freshly rebuilt data
      const rebuilt = allModulesData.find((m) => m.name === mod.name);
      if (rebuilt) return rebuilt;

      // Otherwise build a lightweight ModuleData from metadata
      return buildModuleDataFromMetadata(mod, metadata, detectionResult);
    });

    const projectInfo: ProjectInfo = {
      projectName: metadata.projectName ?? path.basename(resolvedRoot),
      projectType: metadata.projectType ?? detectionResult.projectType,
      primaryLanguage: detectionResult.primaryLanguage,
      framework: detectionResult.framework,
    };

    generateProjectStructure(allModules, projectInfo, outputDir);
  } catch (err: unknown) {
    process.stderr.write(
      `[Code-Index] ERROR: Project structure update failed — ${(err as Error).message}\n`,
    );
  }

  // --- Step 8: Write updated metadata atomically ---
  try {
    writeMetadata(metadata, metadataPath);
  } catch (err: unknown) {
    process.stderr.write(
      `[Code-Index] ERROR: Metadata write failed — ${(err as Error).message}\n`,
    );
  }

  // --- Step 9: Output KB ingestion payloads for affected modules ---
  const kbPayloads: Array<{
    title: string;
    content: string;
    tags: string;
    project: string;
    action?: string;
  }> = allModulesData.map((mod) => ({
    title: `Code Index — ${mod.name}`,
    content: buildModuleSummary(mod),
    tags: `code-index, ${mod.name}, ${mod.language}`,
    project: metadata.projectName ?? path.basename(resolvedRoot),
  }));

  // Add cleanup payloads for removed modules
  for (const removedName of removedModuleNames) {
    kbPayloads.push({
      title: `Code Index — ${removedName}`,
      content: `Module "${removedName}" has been removed from the project.`,
      tags: `code-index, ${removedName}`,
      project: metadata.projectName ?? path.basename(resolvedRoot),
      action: "remove",
    });
  }

  if (kbPayloads.length > 0) {
    const kbPayloadsPath = path.join(outputDir, KB_PAYLOADS_FILENAME);
    try {
      fs.writeFileSync(kbPayloadsPath, JSON.stringify(kbPayloads, null, 2), "utf-8");
    } catch (err: unknown) {
      process.stderr.write(
        `[Code-Index] ERROR: KB payloads write failed — ${(err as Error).message}\n`,
      );
    }
  }

  // --- Step 10: Log summary and return ---
  const elapsedMs = Date.now() - startTime;
  const totalFiles = Object.keys(metadata.files).length;

  process.stdout.write(
    `[Code-Index] INFO: Incremental index complete — ${totalChanges} changes processed, ${totalFiles} total files, ${affectedModules.size} modules affected, ${parseErrors} errors, ${elapsedMs}ms\n`,
  );

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
 * Resolve which module a file belongs to based on its path and the
 * discovered module list.
 */
function resolveModuleName(filePath: string, modules: Module[]): string {
  const normalizedPath = filePath.replace(/\\/g, "/");

  // Try to match the file path against module paths
  for (const mod of modules) {
    const modPath = mod.path.replace(/\\/g, "/");
    if (modPath === ".") continue; // Skip root module for now
    if (normalizedPath.startsWith(modPath + "/") || normalizedPath.startsWith(modPath + "\\")) {
      return mod.name;
    }
  }

  // Fallback: check if there's a root module
  const rootModule = modules.find((m) => m.path === ".");
  return rootModule?.name ?? "root";
}

/**
 * Build a lightweight ModuleData from metadata entries (without re-parsing).
 * Used for unaffected modules when regenerating project-structure.md.
 */
function buildModuleDataFromMetadata(
  mod: Module,
  metadata: IndexMetadata,
  detectionResult: { primaryLanguage: string; framework: string | null },
): ModuleData {
  let sourceFileCount = 0;
  const dependencySet = new Set<string>();

  for (const [_filePath, entry] of Object.entries(metadata.files)) {
    if (entry.moduleName === mod.name) {
      sourceFileCount++;
    }
  }

  return {
    name: mod.name,
    path: mod.path,
    language: mod.language ?? detectionResult.primaryLanguage,
    framework: detectionResult.framework,
    dependencies: Array.from(dependencySet),
    sourceFileCount,
    packages: [],
    classes: [],
    functions: [],
    patterns: {
      diStyle: "unknown",
      errorHandling: "unknown",
      naming: "unknown",
      logging: "unknown",
      testing: "unknown",
    },
    purpose: "Application module",
  };
}

/**
 * Perform a full re-index when no valid metadata exists.
 *
 * Imports the full-indexer at the top level to avoid circular dependency
 * issues at module load time. This function is only called when metadata
 * is missing/corrupted.
 */
function runFullReindex(
  resolvedRoot: string,
  _config: IndexConfig,
  configPath?: string,
): IndexResult {
  return runFullIndexFn(resolvedRoot, configPath ?? undefined);
}

// ---------------------------------------------------------------------------
// CLI entry point
// ---------------------------------------------------------------------------

if (process.argv[1] && process.argv[1].replace(/\\/g, "/").includes("incremental-indexer")) {
  // Parse --files argument for hook-triggered mode
  const filesArgIdx = process.argv.indexOf("--files");
  let changedFiles: string[] | undefined;

  if (filesArgIdx !== -1 && process.argv[filesArgIdx + 1]) {
    changedFiles = process.argv[filesArgIdx + 1].split(",").map((f) => f.trim()).filter(Boolean);
  }

  const rootDir = process.argv[2] && !process.argv[2].startsWith("--")
    ? process.argv[2]
    : process.cwd();

  const result = runIncrementalIndex(rootDir, changedFiles);
  console.log(JSON.stringify(result, null, 2));
}
