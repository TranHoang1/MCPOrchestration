/**
 * Index Metadata read/write helpers for the Code Intelligence System.
 *
 * Provides `readMetadata()` and `writeMetadata()` with an atomic write
 * strategy (write to `.tmp`, then rename) to prevent corruption from
 * interrupted writes.
 */
import type { IndexMetadata } from "./types.js";
/** Default path for the index-metadata.json file. */
export declare const DEFAULT_METADATA_PATH: string;
/**
 * Read and validate the index metadata file.
 *
 * @param metadataPath - Optional override for the metadata file location.
 *   Defaults to `.analysis/code-intelligence/index-metadata.json` resolved
 *   relative to `process.cwd()`.
 * @returns The parsed {@link IndexMetadata}, or `null` when the file is
 *   missing, corrupted, or structurally invalid (triggers full re-index).
 */
export declare function readMetadata(metadataPath?: string): IndexMetadata | null;
/**
 * Write index metadata to disk using an atomic write strategy.
 *
 * The file is first written to a `.tmp` sibling, then renamed over the
 * target path. This prevents readers from seeing a partially-written file.
 *
 * @param metadata     - The metadata object to persist.
 * @param metadataPath - Optional override for the metadata file location.
 *   Defaults to `.analysis/code-intelligence/index-metadata.json` resolved
 *   relative to `process.cwd()`.
 */
export declare function writeMetadata(metadata: IndexMetadata, metadataPath?: string): void;
//# sourceMappingURL=metadata-helpers.d.ts.map