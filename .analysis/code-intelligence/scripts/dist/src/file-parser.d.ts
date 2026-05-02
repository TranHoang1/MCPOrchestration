/**
 * File Parser for the Code Intelligence System.
 *
 * Extracts structured information (classes, functions, imports, package name)
 * from a single source file. Uses the TypeScript Compiler API for TS/JS files
 * and regex-based extraction for all other supported languages.
 */
import type { ParseResult } from "./types.js";
/**
 * Parse a source file and extract structured information.
 *
 * @param filePath   - Path to the source file (relative or absolute).
 * @param language   - The detected language of the file.
 * @param moduleName - The module this file belongs to. Defaults to `"root"`.
 * @returns A {@link ParseResult} — never throws.
 */
export declare function parseFile(filePath: string, language: string, moduleName?: string): ParseResult;
//# sourceMappingURL=file-parser.d.ts.map