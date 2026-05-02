/**
 * Shared type definitions for the Code Intelligence System.
 *
 * All interfaces used across indexing, parsing, analysis generation,
 * annotation management, and KB ingestion scripts.
 */
/** Configuration for which files to index and which to exclude. */
export interface IndexConfig {
    includedExtensions: string[];
    excludedDirectories: string[];
    excludedFilePatterns: string[];
}
/** Per-file entry stored in index-metadata.json. */
export interface FileEntry {
    contentHash: string;
    lastIndexedTimestamp: string;
    language: string;
    moduleName: string;
    indexingStatus: "success" | "parse_error" | "read_error";
}
/** Top-level structure of index-metadata.json. */
export interface IndexMetadata {
    version: string;
    lastFullIndexTimestamp: string | null;
    projectName: string | null;
    projectType: string | null;
    totalFiles: number;
    files: Record<string, FileEntry>;
}
/** Result of auto-detecting the project type from build files. */
export interface DetectionResult {
    projectType: string;
    primaryLanguage: string;
    framework: string | null;
    buildFile: string;
}
/** A discovered module (subproject) within the workspace. */
export interface Module {
    name: string;
    path: string;
    sourceDirectories: string[];
    buildFile: string | null;
    language: string | null;
}
/** A single parameter in a function signature. */
export interface ParameterInfo {
    name: string;
    type: string;
}
/** A class extracted from a source file. */
export interface ClassInfo {
    name: string;
    visibility: string;
    superclass: string | undefined;
    interfaces: string[];
    annotations: string[];
}
/** A function extracted from a source file. */
export interface FunctionInfo {
    name: string;
    visibility: string;
    parameters: ParameterInfo[];
    returnType: string;
    annotations: string[];
}
/** Result of parsing a single source file. */
export interface ParseResult {
    filePath: string;
    language: string;
    moduleName: string;
    packageName: string;
    classes: ClassInfo[];
    functions: FunctionInfo[];
    imports: string[];
    indexingStatus: "success" | "parse_error";
    errorMessage: string | undefined;
}
/** A single annotation row in a Module Analysis File. */
export interface AnnotationRow {
    target: string;
    authorAgent: string;
    annotationType: string;
    content: string;
    timestamp: string;
}
/** A file discovered by the scanner with its content hash. */
export interface ScannedFile {
    filePath: string;
    contentHash: string;
    language: string;
}
/** Summary returned after a full or incremental indexing run. */
export interface IndexResult {
    totalFiles: number;
    totalModules: number;
    totalClasses: number;
    totalFunctions: number;
    parseErrors: number;
    elapsedMs: number;
}
/** High-level project information for the project-structure file. */
export interface ProjectInfo {
    projectName: string;
    projectType: string;
    primaryLanguage: string;
    framework: string | null;
}
/** Detected coding patterns within a module. */
export interface DetectedPatterns {
    diStyle: string;
    errorHandling: string;
    naming: string;
    logging: string;
    testing: string;
}
/** Information about a single package within a module. */
export interface PackageInfo {
    name: string;
    path: string;
    purpose: string;
}
/** Full data for a single module, used by the analysis generator. */
export interface ModuleData {
    name: string;
    path: string;
    language: string;
    framework: string | null;
    dependencies: string[];
    sourceFileCount: number;
    packages: PackageInfo[];
    classes: ClassInfo[];
    functions: FunctionInfo[];
    patterns: DetectedPatterns;
    purpose: string;
}
/** Payload structure for ingesting a document into the Knowledge Base. */
export interface KbIngestPayload {
    title: string;
    content: string;
    tags: string;
    project: string;
}
/** Column definition within a database table. */
export interface ColumnInfo {
    name: string;
    type: string;
    nullable: boolean;
    defaultValue: string | null;
    description: string;
}
/** Table definition within a database schema. */
export interface TableInfo {
    name: string;
    columns: ColumnInfo[];
    rowCount: number;
    description: string;
}
/** Schema definition within a database. */
export interface SchemaInfo {
    name: string;
    tables: TableInfo[];
}
/** Top-level database schema structure for the DB schema indexer. */
export interface DatabaseSchema {
    schemas: SchemaInfo[];
    dataSourceName: string;
    dataSourceType: string;
}
//# sourceMappingURL=types.d.ts.map