/**
 * Unit tests for the Project Detector.
 *
 * Tests the `detectProjectType()` function using temporary directories
 * with mock build files to verify correct project type, framework,
 * and primary language detection.
 */

import { describe, it, afterEach } from "vitest";
import * as fs from "node:fs";
import * as path from "node:path";
import * as os from "node:os";
import { detectProjectType } from "../src/project-detector.js";

/** Temp directories created during tests, cleaned up in afterEach. */
let tempDirs: string[] = [];

afterEach(() => {
  for (const dir of tempDirs) {
    try {
      fs.rmSync(dir, { recursive: true, force: true });
    } catch {
      // best-effort cleanup
    }
  }
  tempDirs = [];
});

/** Create a temp directory and track it for cleanup. */
function makeTempDir(): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "proj-detect-"));
  tempDirs.push(dir);
  return dir;
}

describe("detectProjectType", () => {
  it("detects gradle-kotlin when build.gradle.kts exists", () => {
    const dir = makeTempDir();
    fs.writeFileSync(path.join(dir, "build.gradle.kts"), "plugins { }");

    const result = detectProjectType(dir);

    expect(result.projectType).toBe("gradle-kotlin");
    expect(result.buildFile).toBe("build.gradle.kts");
  });

  it("detects gradle-java when build.gradle exists", () => {
    const dir = makeTempDir();
    fs.writeFileSync(path.join(dir, "build.gradle"), "plugins { }");

    const result = detectProjectType(dir);

    expect(result.projectType).toBe("gradle-java");
    expect(result.buildFile).toBe("build.gradle");
  });

  it("detects maven-java when pom.xml exists", () => {
    const dir = makeTempDir();
    fs.writeFileSync(
      path.join(dir, "pom.xml"),
      "<project><modelVersion>4.0.0</modelVersion></project>",
    );

    const result = detectProjectType(dir);

    expect(result.projectType).toBe("maven-java");
    expect(result.buildFile).toBe("pom.xml");
  });

  it("detects npm-typescript when package.json and tsconfig.json exist", () => {
    const dir = makeTempDir();
    fs.writeFileSync(
      path.join(dir, "package.json"),
      JSON.stringify({ name: "test-pkg", dependencies: {} }),
    );
    fs.writeFileSync(
      path.join(dir, "tsconfig.json"),
      JSON.stringify({ compilerOptions: {} }),
    );

    const result = detectProjectType(dir);

    expect(result.projectType).toBe("npm-typescript");
  });

  it("detects npm-javascript when only package.json exists", () => {
    const dir = makeTempDir();
    fs.writeFileSync(
      path.join(dir, "package.json"),
      JSON.stringify({ name: "test-pkg", dependencies: {} }),
    );

    const result = detectProjectType(dir);

    expect(result.projectType).toBe("npm-javascript");
  });

  it("detects generic when no build file exists", () => {
    const dir = makeTempDir();

    const result = detectProjectType(dir);

    expect(result.projectType).toBe("generic");
    expect(result.buildFile).toBe("none");
  });

  it("detects Spring Boot framework from build.gradle.kts", () => {
    const dir = makeTempDir();
    fs.writeFileSync(
      path.join(dir, "build.gradle.kts"),
      `plugins { id("org.springframework.boot") }
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
}`,
    );

    const result = detectProjectType(dir);

    expect(result.projectType).toBe("gradle-kotlin");
    expect(result.framework).toBe("Spring Boot");
  });

  it("detects React framework from package.json", () => {
    const dir = makeTempDir();
    fs.writeFileSync(
      path.join(dir, "package.json"),
      JSON.stringify({
        name: "react-app",
        dependencies: { "react": "^18.0.0", "react-dom": "^18.0.0" },
      }),
    );
    fs.writeFileSync(
      path.join(dir, "tsconfig.json"),
      JSON.stringify({ compilerOptions: {} }),
    );

    const result = detectProjectType(dir);

    expect(result.projectType).toBe("npm-typescript");
    expect(result.framework).toBe("React");
  });

  it("returns kotlin as primary language for gradle-kotlin project", () => {
    const dir = makeTempDir();
    fs.writeFileSync(path.join(dir, "build.gradle.kts"), "plugins { }");

    const result = detectProjectType(dir);

    expect(result.primaryLanguage).toBe("kotlin");
  });
});
