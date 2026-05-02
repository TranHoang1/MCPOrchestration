/**
 * File Parser for the Code Intelligence System.
 *
 * Extracts structured information (classes, functions, imports, package name)
 * from a single source file. Uses the TypeScript Compiler API for TS/JS files
 * and regex-based extraction for all other supported languages.
 */

import * as fs from "node:fs";
import * as path from "node:path";
import * as ts from "typescript";
import type {
  ParseResult,
  ClassInfo,
  FunctionInfo,
  ParameterInfo,
} from "./types.js";

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Parse a source file and extract structured information.
 *
 * @param filePath   - Path to the source file (relative or absolute).
 * @param language   - The detected language of the file.
 * @param moduleName - The module this file belongs to. Defaults to `"root"`.
 * @returns A {@link ParseResult} — never throws.
 */
export function parseFile(
  filePath: string,
  language: string,
  moduleName?: string,
): ParseResult {
  const mod = moduleName ?? "root";

  try {
    const content = fs.readFileSync(filePath, "utf-8");

    switch (language) {
      case "typescript":
      case "javascript":
        return parseTypeScript(filePath, content, language, mod);

      case "kotlin":
        return parseKotlin(filePath, content, mod);

      case "java":
        return parseJava(filePath, content, mod);

      case "python":
        return parsePython(filePath, content, mod);

      case "go":
        return parseGo(filePath, content, mod);

      case "rust":
        return parseRust(filePath, content, mod);

      case "csharp":
        return parseCSharp(filePath, content, mod);

      case "yaml":
        return parseYaml(filePath, content, mod);

      case "xml":
        return parseXml(filePath, content, mod);

      case "json":
        return parseJson(filePath, content, mod);

      case "properties":
      case "config":
        return parseProperties(filePath, content, mod);

      case "sql":
        return parseSql(filePath, content, mod);

      default:
        return parseFallback(filePath, content, language, mod);
    }
  } catch (err: unknown) {
    const message =
      err instanceof Error ? err.message : String(err);
    return {
      filePath,
      language,
      moduleName: mod,
      packageName: "",
      classes: [],
      functions: [],
      imports: [],
      indexingStatus: "parse_error",
      errorMessage: message,
    };
  }
}


// ---------------------------------------------------------------------------
// TypeScript / JavaScript — AST-based parsing via TS Compiler API
// ---------------------------------------------------------------------------

function parseTypeScript(
  filePath: string,
  content: string,
  language: string,
  moduleName: string,
): ParseResult {
  const sourceFile = ts.createSourceFile(
    path.basename(filePath),
    content,
    ts.ScriptTarget.Latest,
    true,
  );

  const classes: ClassInfo[] = [];
  const functions: FunctionInfo[] = [];
  const imports: string[] = [];

  function visit(node: ts.Node): void {
    // --- Import declarations ---
    if (ts.isImportDeclaration(node)) {
      const moduleSpecifier = node.moduleSpecifier;
      if (ts.isStringLiteral(moduleSpecifier)) {
        imports.push(moduleSpecifier.text);
      }
    }

    // --- Class declarations ---
    if (ts.isClassDeclaration(node) && node.name) {
      const exported = hasExportModifier(node);
      let superclass: string | undefined;
      const interfaces: string[] = [];

      if (node.heritageClauses) {
        for (const clause of node.heritageClauses) {
          if (clause.token === ts.SyntaxKind.ExtendsKeyword) {
            for (const type of clause.types) {
              superclass = type.expression.getText(sourceFile);
            }
          }
          if (clause.token === ts.SyntaxKind.ImplementsKeyword) {
            for (const type of clause.types) {
              interfaces.push(type.expression.getText(sourceFile));
            }
          }
        }
      }

      const decorators = getDecorators(node, sourceFile);

      classes.push({
        name: node.name.text,
        visibility: exported ? "exported" : "private",
        superclass,
        interfaces,
        annotations: decorators,
      });
    }

    // --- Function declarations ---
    if (ts.isFunctionDeclaration(node) && node.name) {
      const exported = hasExportModifier(node);
      const params = extractTsParameters(node.parameters, sourceFile);
      const returnType = node.type
        ? node.type.getText(sourceFile)
        : "void";
      const decorators = getDecorators(node, sourceFile);

      functions.push({
        name: node.name.text,
        visibility: exported ? "exported" : "private",
        parameters: params,
        returnType,
        annotations: decorators,
      });
    }

    // --- Variable statements with arrow functions (exported const fn = ...) ---
    if (ts.isVariableStatement(node)) {
      const exported = hasExportModifier(node);
      for (const decl of node.declarationList.declarations) {
        if (
          ts.isIdentifier(decl.name) &&
          decl.initializer &&
          (ts.isArrowFunction(decl.initializer) ||
            ts.isFunctionExpression(decl.initializer))
        ) {
          const fn = decl.initializer;
          const params = extractTsParameters(fn.parameters, sourceFile);
          const returnType = fn.type
            ? fn.type.getText(sourceFile)
            : "void";

          functions.push({
            name: decl.name.text,
            visibility: exported ? "exported" : "private",
            parameters: params,
            returnType,
            annotations: [],
          });
        }
      }
    }

    ts.forEachChild(node, visit);
  }

  visit(sourceFile);

  // Derive module-relative package name from file path
  const packageName = derivePackageName(filePath);

  return {
    filePath,
    language,
    moduleName,
    packageName,
    classes,
    functions,
    imports,
    indexingStatus: "success",
    errorMessage: undefined,
  };
}

/** Check whether a node has an `export` modifier. */
function hasExportModifier(node: ts.Node): boolean {
  const modifiers = ts.canHaveModifiers(node)
    ? ts.getModifiers(node)
    : undefined;
  if (!modifiers) return false;
  return modifiers.some((m) => m.kind === ts.SyntaxKind.ExportKeyword);
}

/** Extract parameter names and types from a TS parameter list. */
function extractTsParameters(
  params: ts.NodeArray<ts.ParameterDeclaration>,
  sourceFile: ts.SourceFile,
): ParameterInfo[] {
  return params.map((p) => ({
    name: p.name.getText(sourceFile),
    type: p.type ? p.type.getText(sourceFile) : "any",
  }));
}

/** Extract decorator names from a node. */
function getDecorators(
  node: ts.Node,
  sourceFile: ts.SourceFile,
): string[] {
  const decorators = ts.canHaveDecorators(node)
    ? ts.getDecorators(node)
    : undefined;
  if (!decorators) return [];
  return decorators.map((d) => d.expression.getText(sourceFile));
}


// ---------------------------------------------------------------------------
// Kotlin — regex-based parsing
// ---------------------------------------------------------------------------

function parseKotlin(
  filePath: string,
  content: string,
  moduleName: string,
): ParseResult {
  const lines = content.split("\n");

  let packageName = "";
  const imports: string[] = [];
  const classes: ClassInfo[] = [];
  const functions: FunctionInfo[] = [];

  const pkgRe = /^package\s+([\w.]+)/;
  const importRe = /^import\s+([\w.*]+)/;
  const classRe =
    /(public|private|internal|protected|open|abstract|data|sealed)?\s*(class|object|interface|enum class)\s+(\w+)(?:\s*:\s*([\w.]+))?(?:\s*,\s*([\w., ]+))?/;
  const funRe =
    /(public|private|internal|protected|override|open|suspend)?\s*fun\s+(\w+)\s*\(([^)]*)\)(?:\s*:\s*(\w+))?/;

  for (const line of lines) {
    const trimmed = line.trim();

    const pkgMatch = pkgRe.exec(trimmed);
    if (pkgMatch) {
      packageName = pkgMatch[1];
      continue;
    }

    const importMatch = importRe.exec(trimmed);
    if (importMatch) {
      imports.push(importMatch[1]);
      continue;
    }

    const classMatch = classRe.exec(trimmed);
    if (classMatch) {
      const visibility = classMatch[1] ?? "public";
      const name = classMatch[3];
      const superclass = classMatch[4] || undefined;
      const ifaces = classMatch[5]
        ? classMatch[5].split(",").map((s) => s.trim()).filter(Boolean)
        : [];

      classes.push({
        name,
        visibility,
        superclass,
        interfaces: ifaces,
        annotations: [],
      });
    }

    const funMatch = funRe.exec(trimmed);
    if (funMatch) {
      const visibility = funMatch[1] ?? "public";
      const name = funMatch[2];
      const rawParams = funMatch[3];
      const returnType = funMatch[4] ?? "Unit";
      const parameters = parseParamList(rawParams, ":");

      functions.push({
        name,
        visibility,
        parameters,
        returnType,
        annotations: [],
      });
    }
  }

  return {
    filePath,
    language: "kotlin",
    moduleName,
    packageName,
    classes,
    functions,
    imports,
    indexingStatus: "success",
    errorMessage: undefined,
  };
}

// ---------------------------------------------------------------------------
// Java — regex-based parsing
// ---------------------------------------------------------------------------

function parseJava(
  filePath: string,
  content: string,
  moduleName: string,
): ParseResult {
  const lines = content.split("\n");

  let packageName = "";
  const imports: string[] = [];
  const classes: ClassInfo[] = [];
  const functions: FunctionInfo[] = [];

  const pkgRe = /^package\s+([\w.]+);/;
  const importRe = /^import\s+([\w.*]+);/;
  const classRe =
    /(public|private|protected)?\s*(abstract\s+)?(class|interface|enum)\s+(\w+)(?:\s+extends\s+(\w+))?(?:\s+implements\s+([\w, ]+))?/;
  const methodRe =
    /(public|private|protected)?\s*(static\s+)?([\w<>\[\]]+)\s+(\w+)\s*\(([^)]*)\)/;

  for (const line of lines) {
    const trimmed = line.trim();

    const pkgMatch = pkgRe.exec(trimmed);
    if (pkgMatch) {
      packageName = pkgMatch[1];
      continue;
    }

    const importMatch = importRe.exec(trimmed);
    if (importMatch) {
      imports.push(importMatch[1]);
      continue;
    }

    const classMatch = classRe.exec(trimmed);
    if (classMatch) {
      const visibility = classMatch[1] ?? "package-private";
      const name = classMatch[4];
      const superclass = classMatch[5] || undefined;
      const ifaces = classMatch[6]
        ? classMatch[6].split(",").map((s) => s.trim()).filter(Boolean)
        : [];

      classes.push({
        name,
        visibility,
        superclass,
        interfaces: ifaces,
        annotations: [],
      });
    }

    const methodMatch = methodRe.exec(trimmed);
    if (methodMatch) {
      const visibility = methodMatch[1] ?? "package-private";
      const returnType = methodMatch[3];
      const name = methodMatch[4];
      const rawParams = methodMatch[5];

      // Skip lines that look like class declarations (return type = class/interface/enum)
      if (["class", "interface", "enum"].includes(returnType)) continue;

      const parameters = parseJavaParams(rawParams);

      functions.push({
        name,
        visibility,
        parameters,
        returnType,
        annotations: [],
      });
    }
  }

  return {
    filePath,
    language: "java",
    moduleName,
    packageName,
    classes,
    functions,
    imports,
    indexingStatus: "success",
    errorMessage: undefined,
  };
}


// ---------------------------------------------------------------------------
// Python — regex-based parsing
// ---------------------------------------------------------------------------

function parsePython(
  filePath: string,
  content: string,
  moduleName: string,
): ParseResult {
  const lines = content.split("\n");

  const imports: string[] = [];
  const classes: ClassInfo[] = [];
  const functions: FunctionInfo[] = [];

  const importRe = /^import\s+(\S+)/;
  const fromImportRe = /^from\s+(\S+)\s+import/;
  const classRe = /^class\s+(\w+)(?:\(([^)]*)\))?:/;
  const defRe = /^def\s+(\w+)\s*\(([^)]*)\)(?:\s*->\s*(\S+))?:/;

  for (const line of lines) {
    const trimmed = line.trim();

    const importMatch = importRe.exec(trimmed);
    if (importMatch) {
      imports.push(importMatch[1]);
      continue;
    }

    const fromMatch = fromImportRe.exec(trimmed);
    if (fromMatch) {
      imports.push(fromMatch[1]);
      continue;
    }

    const classMatch = classRe.exec(trimmed);
    if (classMatch) {
      const name = classMatch[1];
      const bases = classMatch[2]
        ? classMatch[2].split(",").map((s) => s.trim()).filter(Boolean)
        : [];
      const superclass = bases.length > 0 ? bases[0] : undefined;
      const interfaces = bases.length > 1 ? bases.slice(1) : [];

      classes.push({
        name,
        visibility: "public",
        superclass,
        interfaces,
        annotations: [],
      });
    }

    const defMatch = defRe.exec(trimmed);
    if (defMatch) {
      const name = defMatch[1];
      const rawParams = defMatch[2];
      const returnType = defMatch[3] ?? "None";
      const parameters = parsePythonParams(rawParams);

      // Functions starting with _ are considered private
      const visibility = name.startsWith("_") ? "private" : "public";

      functions.push({
        name,
        visibility,
        parameters,
        returnType,
        annotations: [],
      });
    }
  }

  return {
    filePath,
    language: "python",
    moduleName,
    packageName: derivePackageName(filePath),
    classes,
    functions,
    imports,
    indexingStatus: "success",
    errorMessage: undefined,
  };
}

// ---------------------------------------------------------------------------
// Go — regex-based parsing
// ---------------------------------------------------------------------------

function parseGo(
  filePath: string,
  content: string,
  moduleName: string,
): ParseResult {
  let packageName = "";
  const imports: string[] = [];
  const classes: ClassInfo[] = [];
  const functions: FunctionInfo[] = [];

  // Package
  const pkgMatch = /^package\s+(\w+)/m.exec(content);
  if (pkgMatch) {
    packageName = pkgMatch[1];
  }

  // Imports — both single and block form
  const importBlockRe = /import\s*\(([\s\S]*?)\)/g;
  let blockMatch: RegExpExecArray | null;
  while ((blockMatch = importBlockRe.exec(content)) !== null) {
    const block = blockMatch[1];
    const pathRe = /"([\w./]+)"/g;
    let pathMatch: RegExpExecArray | null;
    while ((pathMatch = pathRe.exec(block)) !== null) {
      imports.push(pathMatch[1]);
    }
  }

  // Single-line imports
  const singleImportRe = /^import\s+"([\w./]+)"/gm;
  let singleMatch: RegExpExecArray | null;
  while ((singleMatch = singleImportRe.exec(content)) !== null) {
    imports.push(singleMatch[1]);
  }

  // Types (structs)
  const typeRe = /type\s+(\w+)\s+struct/g;
  let typeMatch: RegExpExecArray | null;
  while ((typeMatch = typeRe.exec(content)) !== null) {
    const name = typeMatch[1];
    // In Go, exported names start with uppercase
    const visibility = name[0] === name[0].toUpperCase() ? "exported" : "unexported";
    classes.push({
      name,
      visibility,
      superclass: undefined,
      interfaces: [],
      annotations: [],
    });
  }

  // Functions
  const funcRe = /func\s+(\w+)\s*\(([^)]*)\)(?:\s*(\S+))?/g;
  let funcMatch: RegExpExecArray | null;
  while ((funcMatch = funcRe.exec(content)) !== null) {
    const name = funcMatch[1];
    const rawParams = funcMatch[2];
    const returnType = funcMatch[3] ?? "";
    const visibility = name[0] === name[0].toUpperCase() ? "exported" : "unexported";
    const parameters = parseGoParams(rawParams);

    functions.push({
      name,
      visibility,
      parameters,
      returnType,
      annotations: [],
    });
  }

  return {
    filePath,
    language: "go",
    moduleName,
    packageName,
    classes,
    functions,
    imports,
    indexingStatus: "success",
    errorMessage: undefined,
  };
}

// ---------------------------------------------------------------------------
// Rust — regex-based parsing
// ---------------------------------------------------------------------------

function parseRust(
  filePath: string,
  content: string,
  moduleName: string,
): ParseResult {
  const imports: string[] = [];
  const classes: ClassInfo[] = [];
  const functions: FunctionInfo[] = [];

  // Use statements
  const useRe = /^use\s+([\w:*]+);/gm;
  let useMatch: RegExpExecArray | null;
  while ((useMatch = useRe.exec(content)) !== null) {
    imports.push(useMatch[1]);
  }

  // Structs
  const structRe = /(pub\s+)?struct\s+(\w+)/g;
  let structMatch: RegExpExecArray | null;
  while ((structMatch = structRe.exec(content)) !== null) {
    classes.push({
      name: structMatch[2],
      visibility: structMatch[1] ? "pub" : "private",
      superclass: undefined,
      interfaces: [],
      annotations: [],
    });
  }

  // Enums
  const enumRe = /(pub\s+)?enum\s+(\w+)/g;
  let enumMatch: RegExpExecArray | null;
  while ((enumMatch = enumRe.exec(content)) !== null) {
    classes.push({
      name: enumMatch[2],
      visibility: enumMatch[1] ? "pub" : "private",
      superclass: undefined,
      interfaces: [],
      annotations: [],
    });
  }

  // Functions
  const fnRe = /(pub\s+)?fn\s+(\w+)\s*\(([^)]*)\)(?:\s*->\s*(\S+))?/g;
  let fnMatch: RegExpExecArray | null;
  while ((fnMatch = fnRe.exec(content)) !== null) {
    const name = fnMatch[2];
    const rawParams = fnMatch[3];
    const returnType = fnMatch[4] ?? "()";
    const parameters = parseRustParams(rawParams);

    functions.push({
      name,
      visibility: fnMatch[1] ? "pub" : "private",
      parameters,
      returnType,
      annotations: [],
    });
  }

  return {
    filePath,
    language: "rust",
    moduleName,
    packageName: derivePackageName(filePath),
    classes,
    functions,
    imports,
    indexingStatus: "success",
    errorMessage: undefined,
  };
}

// ---------------------------------------------------------------------------
// C# — regex-based parsing
// ---------------------------------------------------------------------------

function parseCSharp(
  filePath: string,
  content: string,
  moduleName: string,
): ParseResult {
  let packageName = "";
  const imports: string[] = [];
  const classes: ClassInfo[] = [];
  const functions: FunctionInfo[] = [];

  // Using statements
  const usingRe = /^using\s+([\w.]+);/gm;
  let usingMatch: RegExpExecArray | null;
  while ((usingMatch = usingRe.exec(content)) !== null) {
    imports.push(usingMatch[1]);
  }

  // Namespace
  const nsMatch = /namespace\s+([\w.]+)/m.exec(content);
  if (nsMatch) {
    packageName = nsMatch[1];
  }

  // Classes / interfaces
  const classRe =
    /(public|private|protected|internal)?\s*(abstract\s+)?(class|interface)\s+(\w+)(?:\s*:\s*([\w, ]+))?/g;
  let classMatch: RegExpExecArray | null;
  while ((classMatch = classRe.exec(content)) !== null) {
    const visibility = classMatch[1] ?? "internal";
    const name = classMatch[4];
    const bases = classMatch[5]
      ? classMatch[5].split(",").map((s) => s.trim()).filter(Boolean)
      : [];
    const superclass = bases.length > 0 ? bases[0] : undefined;
    const interfaces = bases.length > 1 ? bases.slice(1) : [];

    classes.push({
      name,
      visibility,
      superclass,
      interfaces,
      annotations: [],
    });
  }

  // Methods
  const methodRe =
    /(public|private|protected|internal)?\s*(static\s+)?(async\s+)?([\w<>\[\]]+)\s+(\w+)\s*\(([^)]*)\)/g;
  let methodMatch: RegExpExecArray | null;
  while ((methodMatch = methodRe.exec(content)) !== null) {
    const visibility = methodMatch[1] ?? "private";
    const returnType = methodMatch[4];
    const name = methodMatch[5];
    const rawParams = methodMatch[6];

    // Skip if return type looks like a class keyword
    if (["class", "interface", "enum", "namespace"].includes(returnType)) continue;

    const parameters = parseJavaParams(rawParams); // C# params are similar to Java

    functions.push({
      name,
      visibility,
      parameters,
      returnType,
      annotations: [],
    });
  }

  return {
    filePath,
    language: "csharp",
    moduleName,
    packageName,
    classes,
    functions,
    imports,
    indexingStatus: "success",
    errorMessage: undefined,
  };
}


// ---------------------------------------------------------------------------
// Configuration files — YAML, XML, JSON, Properties
// ---------------------------------------------------------------------------

function parseYaml(
  filePath: string,
  content: string,
  moduleName: string,
): ParseResult {
  const classes: ClassInfo[] = [];
  const keyRe = /^(\w[\w-]*):/gm;
  let match: RegExpExecArray | null;
  while ((match = keyRe.exec(content)) !== null) {
    classes.push({
      name: match[1],
      visibility: "public",
      superclass: undefined,
      interfaces: [],
      annotations: [],
    });
  }

  return {
    filePath,
    language: "yaml",
    moduleName,
    packageName: "",
    classes,
    functions: [],
    imports: [],
    indexingStatus: "success",
    errorMessage: undefined,
  };
}

function parseXml(
  filePath: string,
  content: string,
  moduleName: string,
): ParseResult {
  const classes: ClassInfo[] = [];

  // Extract root element
  const rootMatch = /<(\w[\w-]*)[>\s/]/m.exec(content);
  if (rootMatch) {
    classes.push({
      name: rootMatch[1],
      visibility: "public",
      superclass: undefined,
      interfaces: [],
      annotations: [],
    });
  }

  // Extract key child elements (direct children of root, unique names)
  const childRe = /<(\w[\w-]*)[>\s/]/g;
  const seen = new Set<string>();
  if (rootMatch) seen.add(rootMatch[1]);

  let childMatch: RegExpExecArray | null;
  while ((childMatch = childRe.exec(content)) !== null) {
    const name = childMatch[1];
    if (!seen.has(name)) {
      seen.add(name);
      classes.push({
        name,
        visibility: "public",
        superclass: undefined,
        interfaces: [],
        annotations: [],
      });
    }
  }

  return {
    filePath,
    language: "xml",
    moduleName,
    packageName: "",
    classes,
    functions: [],
    imports: [],
    indexingStatus: "success",
    errorMessage: undefined,
  };
}

function parseJson(
  filePath: string,
  content: string,
  moduleName: string,
): ParseResult {
  const classes: ClassInfo[] = [];

  try {
    const parsed = JSON.parse(content);
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      for (const key of Object.keys(parsed)) {
        classes.push({
          name: key,
          visibility: "public",
          superclass: undefined,
          interfaces: [],
          annotations: [],
        });
      }
    }
  } catch {
    // Invalid JSON — return empty classes, still success for config files
  }

  return {
    filePath,
    language: "json",
    moduleName,
    packageName: "",
    classes,
    functions: [],
    imports: [],
    indexingStatus: "success",
    errorMessage: undefined,
  };
}

function parseProperties(
  filePath: string,
  content: string,
  moduleName: string,
): ParseResult {
  const classes: ClassInfo[] = [];
  const propRe = /^([\w.]+)\s*=/gm;
  let match: RegExpExecArray | null;
  while ((match = propRe.exec(content)) !== null) {
    classes.push({
      name: match[1],
      visibility: "public",
      superclass: undefined,
      interfaces: [],
      annotations: [],
    });
  }

  return {
    filePath,
    language: "properties",
    moduleName,
    packageName: "",
    classes,
    functions: [],
    imports: [],
    indexingStatus: "success",
    errorMessage: undefined,
  };
}

// ---------------------------------------------------------------------------
// SQL — regex-based parsing
// ---------------------------------------------------------------------------

function parseSql(
  filePath: string,
  content: string,
  moduleName: string,
): ParseResult {
  const classes: ClassInfo[] = [];

  // Tables
  const tableRe = /CREATE\s+TABLE\s+(\w+)/gi;
  let tableMatch: RegExpExecArray | null;
  while ((tableMatch = tableRe.exec(content)) !== null) {
    classes.push({
      name: tableMatch[1],
      visibility: "public",
      superclass: undefined,
      interfaces: [],
      annotations: ["TABLE"],
    });
  }

  // Views
  const viewRe = /CREATE\s+VIEW\s+(\w+)/gi;
  let viewMatch: RegExpExecArray | null;
  while ((viewMatch = viewRe.exec(content)) !== null) {
    classes.push({
      name: viewMatch[1],
      visibility: "public",
      superclass: undefined,
      interfaces: [],
      annotations: ["VIEW"],
    });
  }

  // Procedures
  const procRe = /CREATE\s+PROCEDURE\s+(\w+)/gi;
  let procMatch: RegExpExecArray | null;
  while ((procMatch = procRe.exec(content)) !== null) {
    classes.push({
      name: procMatch[1],
      visibility: "public",
      superclass: undefined,
      interfaces: [],
      annotations: ["PROCEDURE"],
    });
  }

  return {
    filePath,
    language: "sql",
    moduleName,
    packageName: "",
    classes,
    functions: [],
    imports: [],
    indexingStatus: "success",
    errorMessage: undefined,
  };
}

// ---------------------------------------------------------------------------
// Fallback — unsupported languages
// ---------------------------------------------------------------------------

function parseFallback(
  filePath: string,
  content: string,
  language: string,
  moduleName: string,
): ParseResult {
  const imports: string[] = [];

  // Try to extract import/require patterns
  const importRe = /(?:^import\s+|require\s*\(\s*['"])(\S+)/gm;
  let match: RegExpExecArray | null;
  while ((match = importRe.exec(content)) !== null) {
    imports.push(match[1].replace(/['");]/g, ""));
  }

  return {
    filePath,
    language,
    moduleName,
    packageName: "",
    classes: [],
    functions: [],
    imports,
    indexingStatus: "success",
    errorMessage: undefined,
  };
}


// ---------------------------------------------------------------------------
// Shared helpers — parameter parsing
// ---------------------------------------------------------------------------

/**
 * Derive a package-like name from a file path.
 *
 * For Kotlin/Java files, extracts the portion after `src/main/kotlin/` or
 * `src/main/java/` (the standard Gradle/Maven source root) so that absolute
 * Windows paths like `C:\projects\core\src\main\kotlin\com\fec\core\service\MyService.kt`
 * produce `com.fec.core.service` instead of `C:.projects.core...`.
 *
 * For other languages, falls back to the directory portion with path
 * separators replaced by dots, stripping any absolute-path prefix.
 */
function derivePackageName(filePath: string): string {
  // Normalize to forward slashes so the regex works on Windows paths too
  const normalized = filePath.replace(/\\/g, "/");

  // For Kotlin/Java: extract path after src/main/kotlin/ or src/main/java/
  const srcMainMatch = normalized.match(/src\/main\/(?:kotlin|java)\/(.+)/);
  if (srcMainMatch) {
    const packagePath = path.posix.dirname(srcMainMatch[1]);
    return packagePath === "." ? "" : packagePath.replace(/\//g, ".");
  }

  // For test sources: extract path after src/test/kotlin/ or src/test/java/
  const srcTestMatch = normalized.match(/src\/test\/(?:kotlin|java)\/(.+)/);
  if (srcTestMatch) {
    const packagePath = path.posix.dirname(srcTestMatch[1]);
    return packagePath === "." ? "" : packagePath.replace(/\//g, ".");
  }

  // Fallback: use the directory portion, stripping drive letters and leading dots
  const parsed = path.parse(normalized);
  const dir = parsed.dir;
  if (!dir || dir === ".") return "";

  const parts = dir.split("/").filter((p) => p && !p.includes(":"));
  return parts.join(".");
}

/**
 * Parse a comma-separated parameter list where type follows name
 * with a given separator (e.g. `:` for Kotlin).
 *
 * Example: `name: String, age: Int` → [{name:"name", type:"String"}, ...]
 */
function parseParamList(
  raw: string,
  typeSeparator: string,
): ParameterInfo[] {
  if (!raw.trim()) return [];

  return raw
    .split(",")
    .map((p) => p.trim())
    .filter(Boolean)
    .map((p) => {
      const parts = p.split(typeSeparator).map((s) => s.trim());
      return {
        name: parts[0] ?? p,
        type: parts[1] ?? "Any",
      };
    });
}

/**
 * Parse Java/C#-style parameters where type precedes name.
 *
 * Example: `String name, int age` → [{name:"name", type:"String"}, ...]
 */
function parseJavaParams(raw: string): ParameterInfo[] {
  if (!raw.trim()) return [];

  return raw
    .split(",")
    .map((p) => p.trim())
    .filter(Boolean)
    .map((p) => {
      // Remove annotations like @NotNull
      const cleaned = p.replace(/@\w+\s*/g, "").trim();
      const parts = cleaned.split(/\s+/);
      if (parts.length >= 2) {
        return {
          name: parts[parts.length - 1],
          type: parts.slice(0, parts.length - 1).join(" "),
        };
      }
      return { name: cleaned, type: "Object" };
    });
}

/**
 * Parse Python-style parameters.
 *
 * Example: `self, name: str, age: int = 0` → [{name:"name", type:"str"}, ...]
 */
function parsePythonParams(raw: string): ParameterInfo[] {
  if (!raw.trim()) return [];

  return raw
    .split(",")
    .map((p) => p.trim())
    .filter(Boolean)
    .filter((p) => p !== "self" && p !== "cls")
    .map((p) => {
      // Remove default values
      const withoutDefault = p.split("=")[0].trim();
      const parts = withoutDefault.split(":").map((s) => s.trim());
      return {
        name: parts[0],
        type: parts[1] ?? "Any",
      };
    });
}

/**
 * Parse Go-style parameters.
 *
 * Example: `name string, age int` → [{name:"name", type:"string"}, ...]
 */
function parseGoParams(raw: string): ParameterInfo[] {
  if (!raw.trim()) return [];

  return raw
    .split(",")
    .map((p) => p.trim())
    .filter(Boolean)
    .map((p) => {
      const parts = p.split(/\s+/);
      if (parts.length >= 2) {
        return {
          name: parts[0],
          type: parts.slice(1).join(" "),
        };
      }
      return { name: parts[0], type: "" };
    });
}

/**
 * Parse Rust-style parameters.
 *
 * Example: `name: &str, age: i32` → [{name:"name", type:"&str"}, ...]
 */
function parseRustParams(raw: string): ParameterInfo[] {
  if (!raw.trim()) return [];

  return raw
    .split(",")
    .map((p) => p.trim())
    .filter(Boolean)
    .filter((p) => !p.startsWith("&self") && !p.startsWith("&mut self") && p !== "self")
    .map((p) => {
      const parts = p.split(":").map((s) => s.trim());
      return {
        name: parts[0],
        type: parts[1] ?? "",
      };
    });
}
