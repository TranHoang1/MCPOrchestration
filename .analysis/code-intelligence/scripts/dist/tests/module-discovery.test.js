/**
 * Unit tests for the Module Discovery system.
 *
 * Tests the `discoverModules()` function using temporary directories
 * with mock settings/build files and module directories to verify
 * correct module discovery across different build systems.
 */
import { describe, it, afterEach } from "vitest";
import * as fs from "node:fs";
import * as path from "node:path";
import * as os from "node:os";
import { discoverModules } from "../src/module-discovery.js";
/** Temp directories created during tests, cleaned up in afterEach. */
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
/** Create a temp directory and track it for cleanup. */
function makeTempDir() {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "mod-disc-"));
    tempDirs.push(dir);
    return dir;
}
/** Helper to create a DetectionResult for Gradle-Kotlin projects. */
function gradleKotlinDetection(buildFile = "build.gradle.kts") {
    return {
        projectType: "gradle-kotlin",
        primaryLanguage: "kotlin",
        framework: null,
        buildFile,
    };
}
/** Helper to create a DetectionResult for Maven projects. */
function mavenDetection() {
    return {
        projectType: "maven-java",
        primaryLanguage: "java",
        framework: null,
        buildFile: "pom.xml",
    };
}
/** Helper to create a DetectionResult for npm-typescript projects. */
function npmTsDetection() {
    return {
        projectType: "npm-typescript",
        primaryLanguage: "typescript",
        framework: null,
        buildFile: "package.json",
    };
}
describe("discoverModules", () => {
    it("discovers Gradle modules from settings.gradle.kts", () => {
        const dir = makeTempDir();
        fs.writeFileSync(path.join(dir, "settings.gradle.kts"), `rootProject.name = "my-project"\ninclude("core", "gateway")`);
        fs.mkdirSync(path.join(dir, "core"), { recursive: true });
        fs.mkdirSync(path.join(dir, "gateway"), { recursive: true });
        const modules = discoverModules(dir, gradleKotlinDetection());
        expect(modules).toHaveLength(2);
        const names = modules.map((m) => m.name);
        expect(names).toContain("core");
        expect(names).toContain("gateway");
    });
    it("handles colon-prefixed Gradle module names", () => {
        const dir = makeTempDir();
        fs.writeFileSync(path.join(dir, "settings.gradle.kts"), `include(":core")`);
        fs.mkdirSync(path.join(dir, "core"), { recursive: true });
        const modules = discoverModules(dir, gradleKotlinDetection());
        expect(modules).toHaveLength(1);
        expect(modules[0].name).toBe("core");
    });
    it("discovers Maven modules from pom.xml", () => {
        const dir = makeTempDir();
        fs.writeFileSync(path.join(dir, "pom.xml"), `<project>
  <modules>
    <module>api</module>
  </modules>
</project>`);
        fs.mkdirSync(path.join(dir, "api"), { recursive: true });
        const modules = discoverModules(dir, mavenDetection());
        expect(modules).toHaveLength(1);
        expect(modules[0].name).toBe("api");
    });
    it("falls back to root module when no modules detected", () => {
        const dir = makeTempDir();
        fs.writeFileSync(path.join(dir, "build.gradle.kts"), "plugins { }");
        const modules = discoverModules(dir, gradleKotlinDetection());
        expect(modules).toHaveLength(1);
        expect(modules[0].name).toBe("root");
        expect(modules[0].path).toBe(".");
    });
    it("detects source directories for Gradle modules", () => {
        const dir = makeTempDir();
        fs.writeFileSync(path.join(dir, "settings.gradle.kts"), `include("core")`);
        fs.mkdirSync(path.join(dir, "core", "src", "main", "kotlin"), {
            recursive: true,
        });
        const modules = discoverModules(dir, gradleKotlinDetection());
        expect(modules).toHaveLength(1);
        // Normalize separators for cross-platform compatibility (Windows uses backslash)
        const normalized = modules[0].sourceDirectories.map((d) => d.replace(/\\/g, "/"));
        expect(normalized).toContain("core/src/main/kotlin");
    });
    it("discovers npm workspaces from package.json", () => {
        const dir = makeTempDir();
        fs.writeFileSync(path.join(dir, "package.json"), JSON.stringify({
            name: "monorepo",
            workspaces: ["packages/*"],
        }));
        // Create workspace packages with their own package.json files
        const uiDir = path.join(dir, "packages", "ui");
        const apiDir = path.join(dir, "packages", "api");
        fs.mkdirSync(uiDir, { recursive: true });
        fs.mkdirSync(apiDir, { recursive: true });
        fs.writeFileSync(path.join(uiDir, "package.json"), JSON.stringify({ name: "@monorepo/ui" }));
        fs.writeFileSync(path.join(apiDir, "package.json"), JSON.stringify({ name: "@monorepo/api" }));
        const modules = discoverModules(dir, npmTsDetection());
        expect(modules).toHaveLength(2);
        const names = modules.map((m) => m.name);
        expect(names).toContain("ui");
        expect(names).toContain("api");
    });
});
//# sourceMappingURL=module-discovery.test.js.map