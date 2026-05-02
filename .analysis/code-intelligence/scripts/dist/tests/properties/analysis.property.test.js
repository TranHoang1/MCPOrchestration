/**
 * Property-based tests for the Analysis Generator.
 *
 * Property 7: Project Structure Completeness
 * Property 8: Module Analysis Completeness
 */
import { describe, it, expect, afterEach } from "vitest";
import * as fc from "fast-check";
import * as fs from "node:fs";
import * as path from "node:path";
import * as os from "node:os";
import { generateProjectStructure, generateModuleAnalysis, } from "../../src/analysis-generator.js";
// ---------------------------------------------------------------------------
// Temp directory management
// ---------------------------------------------------------------------------
let tempDirs = [];
afterEach(() => {
    for (const dir of tempDirs) {
        try {
            fs.rmSync(dir, { recursive: true, force: true });
        }
        catch {
            // best-effort cleanup
        }
    }
    tempDirs = [];
});
function makeTempDir() {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "analysis-prop-"));
    tempDirs.push(dir);
    return dir;
}
// ---------------------------------------------------------------------------
// Arbitraries
// ---------------------------------------------------------------------------
/** Safe identifier: starts with a letter, alphanumeric. */
const identifierArb = fc.stringMatching(/^[a-z][a-z0-9]{1,12}$/);
/** Language arbitrary. */
const languageArb = fc.constantFrom("kotlin", "java", "typescript", "javascript", "python", "go", "rust", "csharp");
/** Framework arbitrary (nullable). */
const frameworkArb = fc.oneof(fc.constant(null), fc.constantFrom("Spring Boot", "React", "Express.js", "NestJS", "Django"));
/** Visibility arbitrary. */
const visibilityArb = fc.constantFrom("public", "private", "internal", "protected");
/** ParameterInfo arbitrary. */
const parameterArb = fc.record({
    name: identifierArb,
    type: fc.constantFrom("string", "number", "boolean", "void", "any"),
});
/** ClassInfo arbitrary. */
const classInfoArb = fc.record({
    name: fc.stringMatching(/^[A-Z][a-zA-Z]{1,12}$/),
    visibility: visibilityArb,
    superclass: fc.oneof(fc.constant(undefined), fc.stringMatching(/^[A-Z][a-zA-Z]{1,10}$/)),
    interfaces: fc.array(fc.stringMatching(/^[A-Z][a-zA-Z]{1,10}$/), { minLength: 0, maxLength: 3 }),
    annotations: fc.array(fc.stringMatching(/^[a-zA-Z]{1,10}$/), { minLength: 0, maxLength: 3 }),
});
/** FunctionInfo arbitrary. */
const functionInfoArb = fc.record({
    name: identifierArb,
    visibility: visibilityArb,
    parameters: fc.array(parameterArb, { minLength: 0, maxLength: 3 }),
    returnType: fc.constantFrom("string", "number", "boolean", "void"),
    annotations: fc.array(fc.stringMatching(/^[a-zA-Z]{1,10}$/), { minLength: 0, maxLength: 2 }),
});
/** DetectedPatterns arbitrary. */
const patternsArb = fc.record({
    diStyle: fc.constantFrom("constructor injection", "field injection", "none"),
    errorHandling: fc.constantFrom("try-catch", "Result type", "exception handler", "unknown"),
    naming: fc.constantFrom("*Controller, *Service", "*Repository", "unknown"),
    logging: fc.constantFrom("SLF4J", "console.log", "Log4j", "unknown"),
    testing: fc.constantFrom("JUnit", "Jest", "vitest", "pytest", "unknown"),
});
/** PackageInfo arbitrary. */
const packageInfoArb = fc.record({
    name: identifierArb,
    path: identifierArb.map((n) => `src/${n}`),
    purpose: fc.constantFrom("Business logic", "Data access", "HTTP request handling", "Utility functions"),
});
/** ModuleData arbitrary. */
const moduleDataArb = fc.record({
    name: identifierArb,
    path: identifierArb.map((n) => `modules/${n}`),
    language: languageArb,
    framework: frameworkArb,
    dependencies: fc.array(identifierArb, { minLength: 0, maxLength: 5 }),
    sourceFileCount: fc.integer({ min: 0, max: 500 }),
    packages: fc.array(packageInfoArb, { minLength: 0, maxLength: 5 }),
    classes: fc.array(classInfoArb, { minLength: 0, maxLength: 5 }),
    functions: fc.array(functionInfoArb, { minLength: 0, maxLength: 5 }),
    patterns: patternsArb,
    purpose: fc.constantFrom("API layer", "Business logic", "Data access", "Application module"),
});
/** ProjectInfo arbitrary. */
const projectInfoArb = fc.record({
    projectName: identifierArb,
    projectType: fc.constantFrom("gradle-kotlin", "npm-typescript", "maven-java", "generic"),
    primaryLanguage: languageArb,
    framework: frameworkArb,
});
// ---------------------------------------------------------------------------
// Property 7: Project Structure Completeness
// ---------------------------------------------------------------------------
describe("Feature: code-intelligence, Property 7: Project Structure Completeness", () => {
    /**
     * **Validates: Requirements 3.1**
     *
     * For any set of modules, generateProjectStructure() produces a
     * project-structure.md with exactly one row per module containing
     * all required columns. No module missing, no extra rows.
     */
    it("generates exactly one row per module with all required columns", () => {
        fc.assert(fc.property(fc.array(moduleDataArb, { minLength: 1, maxLength: 15 }), projectInfoArb, (modules, projectInfo) => {
            // Deduplicate module names
            const seen = new Set();
            const uniqueModules = modules.filter((m) => {
                if (seen.has(m.name))
                    return false;
                seen.add(m.name);
                return true;
            });
            const dir = makeTempDir();
            generateProjectStructure(uniqueModules, projectInfo, dir);
            const filePath = path.join(dir, "project-structure.md");
            expect(fs.existsSync(filePath)).toBe(true);
            const content = fs.readFileSync(filePath, "utf-8");
            const lines = content.split("\n");
            // Find the Modules table: header row starts with "| Module"
            const headerIdx = lines.findIndex((l) => l.startsWith("| Module"));
            expect(headerIdx).toBeGreaterThanOrEqual(0);
            // The separator row is right after the header
            const separatorIdx = headerIdx + 1;
            expect(lines[separatorIdx]).toMatch(/^\|[-| ]+\|$/);
            // Collect data rows after the separator until we hit an empty line or non-table line
            const dataRows = [];
            for (let i = separatorIdx + 1; i < lines.length; i++) {
                const line = lines[i].trim();
                if (!line.startsWith("|"))
                    break;
                dataRows.push(line);
            }
            // Exactly one row per module
            expect(dataRows.length).toBe(uniqueModules.length);
            // Each row contains the module name and required columns
            for (const mod of uniqueModules) {
                const matchingRow = dataRows.find((row) => row.includes(`| ${mod.name} |`));
                expect(matchingRow).toBeDefined();
                // Verify the row has the required columns:
                // Module | Purpose | Language | Framework | Key Dependencies | Source Files
                const cells = matchingRow
                    .split("|")
                    .map((c) => c.trim())
                    .filter((c) => c.length > 0);
                expect(cells.length).toBe(6);
                // Module name
                expect(cells[0]).toBe(mod.name);
                // Purpose (non-empty)
                expect(cells[1].length).toBeGreaterThan(0);
                // Language
                expect(cells[2]).toBe(mod.language);
                // Framework (could be "—" for null)
                expect(cells[3].length).toBeGreaterThan(0);
                // Dependencies (could be "—")
                expect(cells[4].length).toBeGreaterThan(0);
                // Source file count
                expect(cells[5]).toBe(String(mod.sourceFileCount));
            }
        }), { numRuns: 100 });
    });
});
// ---------------------------------------------------------------------------
// Property 8: Module Analysis Completeness
// ---------------------------------------------------------------------------
describe("Feature: code-intelligence, Property 8: Module Analysis Completeness", () => {
    /**
     * **Validates: Requirements 3.2**
     *
     * For any module, generateModuleAnalysis() produces a file with all
     * required sections: Package Structure, Key Classes, Public API Surface,
     * Dependencies, Detected Patterns, Annotations.
     */
    it("generates all required sections for any module", () => {
        fc.assert(fc.property(moduleDataArb, (module) => {
            const dir = makeTempDir();
            generateModuleAnalysis(module, [], dir);
            const filePath = path.join(dir, "modules", `${module.name}.md`);
            expect(fs.existsSync(filePath)).toBe(true);
            const content = fs.readFileSync(filePath, "utf-8");
            // All required sections must be present
            const requiredSections = [
                "## Package Structure",
                "## Key Classes",
                "## Public API Surface",
                "## Dependencies",
                "## Detected Patterns",
                "## Annotations",
            ];
            for (const section of requiredSections) {
                expect(content).toContain(section);
            }
        }), { numRuns: 100 });
    });
});
//# sourceMappingURL=analysis.property.test.js.map