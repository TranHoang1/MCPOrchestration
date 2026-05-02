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
import * as fs from "node:fs";
import * as path from "node:path";
// ---------------------------------------------------------------------------
// Default output directory
// ---------------------------------------------------------------------------
const DEFAULT_OUTPUT_DIR = path.resolve(process.cwd(), ".analysis/code-intelligence");
// ---------------------------------------------------------------------------
// Public functions
// ---------------------------------------------------------------------------
/**
 * Generate the project-structure.md file.
 *
 * @param modules     - All discovered modules with their analysis data.
 * @param projectInfo - High-level project metadata.
 * @param outputDir   - Directory to write the file into.
 *                      Defaults to `.analysis/code-intelligence/`.
 */
export function generateProjectStructure(modules, projectInfo, outputDir) {
    const dir = outputDir ?? DEFAULT_OUTPUT_DIR;
    const timestamp = new Date().toISOString();
    const lines = [
        `# Project Structure — ${projectInfo.projectName}`,
        "",
        `**Last Updated:** ${timestamp}`,
        `**Project Type:** ${projectInfo.projectType}`,
        "",
        "## Modules",
        "",
        "| Module | Purpose | Language | Framework | Key Dependencies | Source Files |",
        "|--------|---------|----------|-----------|-----------------|-------------|",
    ];
    for (const mod of modules) {
        const framework = mod.framework ?? "—";
        const deps = mod.dependencies.slice(0, 5).join(", ") || "—";
        const purpose = mod.purpose || "—";
        lines.push(`| ${mod.name} | ${purpose} | ${mod.language} | ${framework} | ${deps} | ${mod.sourceFileCount} |`);
    }
    lines.push("");
    lines.push("## Inter-Module Dependencies");
    lines.push("");
    lines.push("| Module | Depends On |");
    lines.push("|--------|-----------|");
    for (const mod of modules) {
        const dependsOn = mod.dependencies.join(", ") || "—";
        lines.push(`| ${mod.name} | ${dependsOn} |`);
    }
    lines.push("");
    const content = lines.join("\n");
    atomicWrite(path.join(dir, "project-structure.md"), content);
}
/**
 * Generate a per-module analysis file at `modules/{module.name}.md`.
 *
 * @param module      - Full module data including classes, functions, patterns.
 * @param annotations - Existing annotation rows to include in the file.
 * @param outputDir   - Directory to write the file into.
 *                      Defaults to `.analysis/code-intelligence/`.
 */
export function generateModuleAnalysis(module, annotations, outputDir) {
    const dir = outputDir ?? DEFAULT_OUTPUT_DIR;
    const modulesDir = path.join(dir, "modules");
    const timestamp = new Date().toISOString();
    // Ensure modules/ directory exists
    fs.mkdirSync(modulesDir, { recursive: true });
    const framework = module.framework ?? "—";
    const lines = [
        `# Module Analysis — ${module.name}`,
        "",
        `**Last Updated:** ${timestamp}`,
        `**Language:** ${module.language} | **Framework:** ${framework}`,
        "",
        "## Package Structure",
        "",
        "```",
        buildPackageTree(module),
        "```",
        "",
        "## Key Classes",
        "",
        "| Class | Package | Responsibility | Visibility |",
        "|-------|---------|---------------|------------|",
    ];
    for (const cls of module.classes) {
        const pkg = inferPackageFromClass(cls, module);
        const responsibility = inferResponsibility(cls);
        lines.push(`| ${cls.name} | ${pkg} | ${responsibility} | ${cls.visibility} |`);
    }
    lines.push("");
    lines.push("## Public API Surface");
    lines.push("");
    const publicFunctions = module.functions.filter((fn) => fn.visibility === "public");
    if (publicFunctions.length === 0) {
        lines.push("_No public functions detected._");
    }
    else {
        for (const fn of publicFunctions) {
            lines.push(`- \`${formatFunctionSignature(fn)}\``);
        }
    }
    lines.push("");
    lines.push("## Dependencies");
    lines.push("");
    lines.push("| Imports From | Classes Used |");
    lines.push("|-------------|-------------|");
    if (module.dependencies.length === 0) {
        lines.push("| — | — |");
    }
    else {
        for (const dep of module.dependencies) {
            const classesUsed = findClassesFromDependency(dep, module);
            lines.push(`| ${dep} | ${classesUsed || "—"} |`);
        }
    }
    lines.push("");
    lines.push("## Detected Patterns");
    lines.push("");
    lines.push(`- **DI Style**: ${module.patterns.diStyle}`);
    lines.push(`- **Error Handling**: ${module.patterns.errorHandling}`);
    lines.push(`- **Naming**: ${module.patterns.naming}`);
    lines.push(`- **Logging**: ${module.patterns.logging}`);
    lines.push(`- **Testing**: ${module.patterns.testing}`);
    lines.push("");
    lines.push("## Annotations");
    lines.push("");
    lines.push("| Target | Author Agent | Type | Content | Timestamp |");
    lines.push("|--------|-------------|------|---------|-----------|");
    for (const annotation of annotations) {
        lines.push(formatAnnotationRow(annotation));
    }
    lines.push("");
    const content = lines.join("\n");
    atomicWrite(path.join(modulesDir, `${module.name}.md`), content);
}
/**
 * Format a single annotation row as a Markdown table row.
 *
 * Exported for reuse by the annotation-manager.
 */
export function formatAnnotationRow(annotation) {
    return `| ${annotation.target} | ${annotation.authorAgent} | ${annotation.annotationType} | ${annotation.content} | ${annotation.timestamp} |`;
}
// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------
/**
 * Write content to a file atomically: write to a `.tmp` sibling first,
 * then rename over the target path.
 */
function atomicWrite(filePath, content) {
    const dir = path.dirname(filePath);
    fs.mkdirSync(dir, { recursive: true });
    const tmpPath = `${filePath}.tmp`;
    fs.writeFileSync(tmpPath, content, "utf-8");
    fs.renameSync(tmpPath, filePath);
}
/**
 * Build a text-based package tree from the module's packages list.
 */
function buildPackageTree(module) {
    if (module.packages.length === 0) {
        return `${module.name}/`;
    }
    const lines = [];
    const lastIdx = module.packages.length - 1;
    for (let i = 0; i < module.packages.length; i++) {
        const pkg = module.packages[i];
        const prefix = i === lastIdx ? "└── " : "├── ";
        const purpose = pkg.purpose ? `     # ${pkg.purpose}` : "";
        lines.push(`${prefix}${pkg.name}/${purpose}`);
    }
    return `${module.name}/\n${lines.join("\n")}`;
}
/**
 * Infer which package a class belongs to based on module packages.
 *
 * Falls back to the module name if no matching package is found.
 */
function inferPackageFromClass(cls, module) {
    // Check annotations for package hints
    for (const annotation of cls.annotations) {
        if (annotation.startsWith("package:")) {
            return annotation.slice("package:".length).trim();
        }
    }
    // If there's only one package, use it
    if (module.packages.length === 1) {
        return module.packages[0].name;
    }
    // Try to match class name patterns to package purposes
    for (const pkg of module.packages) {
        const pkgLower = pkg.name.toLowerCase();
        const clsLower = cls.name.toLowerCase();
        if ((pkgLower.includes("controller") && clsLower.includes("controller")) ||
            (pkgLower.includes("service") && clsLower.includes("service")) ||
            (pkgLower.includes("repository") && clsLower.includes("repository")) ||
            (pkgLower.includes("model") && clsLower.includes("model")) ||
            (pkgLower.includes("dto") && clsLower.includes("dto")) ||
            (pkgLower.includes("config") && clsLower.includes("config"))) {
            return pkg.name;
        }
    }
    return module.packages.length > 0 ? module.packages[0].name : module.name;
}
/**
 * Infer a class's responsibility from its name and annotations.
 */
function inferResponsibility(cls) {
    const name = cls.name;
    if (name.endsWith("Controller") || name.endsWith("Handler")) {
        return "HTTP request handling";
    }
    if (name.endsWith("Service") || name.endsWith("ServiceImpl")) {
        return "Business logic";
    }
    if (name.endsWith("Repository") || name.endsWith("Dao")) {
        return "Data access";
    }
    if (name.endsWith("Config") || name.endsWith("Configuration")) {
        return "Configuration";
    }
    if (name.endsWith("Dto") || name.endsWith("DTO") || name.endsWith("Request") || name.endsWith("Response")) {
        return "Data transfer object";
    }
    if (name.endsWith("Entity") || name.endsWith("Model")) {
        return "Domain model";
    }
    if (name.endsWith("Exception") || name.endsWith("Error")) {
        return "Error handling";
    }
    if (name.endsWith("Mapper") || name.endsWith("Converter")) {
        return "Data mapping";
    }
    if (name.endsWith("Factory")) {
        return "Object creation";
    }
    if (name.endsWith("Validator")) {
        return "Input validation";
    }
    if (name.endsWith("Listener") || name.endsWith("Observer")) {
        return "Event handling";
    }
    if (name.endsWith("Filter") || name.endsWith("Interceptor")) {
        return "Request filtering";
    }
    if (name.endsWith("Adapter")) {
        return "Integration adapter";
    }
    if (name.endsWith("Client")) {
        return "External service client";
    }
    if (name.endsWith("Test") || name.endsWith("Spec")) {
        return "Test class";
    }
    if (name.endsWith("Utils") || name.endsWith("Util") || name.endsWith("Helper")) {
        return "Utility functions";
    }
    // Check annotations for hints
    for (const annotation of cls.annotations) {
        const lower = annotation.toLowerCase();
        if (lower.includes("restcontroller") || lower.includes("controller")) {
            return "HTTP request handling";
        }
        if (lower.includes("service")) {
            return "Business logic";
        }
        if (lower.includes("repository")) {
            return "Data access";
        }
        if (lower.includes("component")) {
            return "Application component";
        }
        if (lower.includes("entity")) {
            return "Domain model";
        }
        if (lower.includes("configuration")) {
            return "Configuration";
        }
    }
    return "Application component";
}
/**
 * Format a function signature as a readable string.
 */
function formatFunctionSignature(fn) {
    const params = fn.parameters
        .map((p) => `${p.name}: ${p.type}`)
        .join(", ");
    return `${fn.name}(${params}): ${fn.returnType}`;
}
/**
 * Find classes imported from a specific dependency module.
 *
 * This is a best-effort heuristic based on class names and the
 * dependency module name.
 */
function findClassesFromDependency(dep, module) {
    // Look through imports in functions/classes for references to the dep
    const classNames = [];
    for (const cls of module.classes) {
        for (const iface of cls.interfaces) {
            if (iface.toLowerCase().includes(dep.toLowerCase())) {
                classNames.push(iface);
            }
        }
        if (cls.superclass?.toLowerCase().includes(dep.toLowerCase())) {
            classNames.push(cls.superclass);
        }
    }
    return classNames.length > 0 ? classNames.join(", ") : "—";
}
//# sourceMappingURL=analysis-generator.js.map