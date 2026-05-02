/**
 * Index Metadata read/write helpers for the Code Intelligence System.
 *
 * Provides `readMetadata()` and `writeMetadata()` with an atomic write
 * strategy (write to `.tmp`, then rename) to prevent corruption from
 * interrupted writes.
 */

import * as fs from "node:fs";
import * as path from "node:path";
import type { IndexMetadata } from "./types.js";

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Default path for the index-metadata.json file. */
export const DEFAULT_METADATA_PATH = path.resolve(
  process.cwd(),
  ".analysis/code-intelligence/index-metadata.json",
);

// ---------------------------------------------------------------------------
// Public functions
// ---------------------------------------------------------------------------

/**
 * Read and validate the index metadata file.
 *
 * @param metadataPath - Optional override for the metadata file location.
 *   Defaults to `.analysis/code-intelligence/index-metadata.json` resolved
 *   relative to `process.cwd()`.
 * @returns The parsed {@link IndexMetadata}, or `null` when the file is
 *   missing, corrupted, or structurally invalid (triggers full re-index).
 */
export function readMetadata(metadataPath?: string): IndexMetadata | null {
  const resolvedPath = metadataPath ?? DEFAULT_METADATA_PATH;

  let raw: string;
  try {
    raw = fs.readFileSync(resolvedPath, "utf-8");
  } catch (err: unknown) {
    if ((err as NodeJS.ErrnoException).code === "ENOENT") {
      // File doesn't exist — return null silently (triggers full re-index).
      return null;
    }
    // Unexpected read error — treat as corrupted.
    process.stderr.write(
      `[Code-Index] ERROR: Corrupted metadata — ${resolvedPath} — ${(err as Error).message}\n`,
    );
    safeDelete(resolvedPath);
    return null;
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch (err: unknown) {
    process.stderr.write(
      `[Code-Index] ERROR: Corrupted metadata — ${resolvedPath} — ${(err as Error).message}\n`,
    );
    safeDelete(resolvedPath);
    return null;
  }

  // Validate structure: version must exist and files must be an object.
  if (!isValidMetadata(parsed)) {
    process.stderr.write(
      `[Code-Index] ERROR: Corrupted metadata — ${resolvedPath} — missing or invalid "version" or "files" field\n`,
    );
    safeDelete(resolvedPath);
    return null;
  }

  return parsed as IndexMetadata;
}

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
export function writeMetadata(
  metadata: IndexMetadata,
  metadataPath?: string,
): void {
  const resolvedPath = metadataPath ?? DEFAULT_METADATA_PATH;

  // Ensure totalFiles matches the actual number of file entries.
  metadata.totalFiles = Object.keys(metadata.files).length;

  const json = JSON.stringify(metadata, null, 2);
  const tmpPath = `${resolvedPath}.tmp`;

  const dir = path.dirname(resolvedPath);
  fs.mkdirSync(dir, { recursive: true });

  fs.writeFileSync(tmpPath, json, "utf-8");

  try {
    fs.renameSync(tmpPath, resolvedPath);
  } catch (err: unknown) {
    process.stderr.write(
      `[Code-Index] ERROR: Metadata write failed — ${resolvedPath} — ${(err as Error).message}\n`,
    );
    // Do NOT delete the temp file — it may be recoverable.
  }
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Validate that a parsed value has the expected IndexMetadata shape.
 */
function isValidMetadata(value: unknown): boolean {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const obj = value as Record<string, unknown>;

  if (typeof obj.version !== "string") {
    return false;
  }

  if (typeof obj.files !== "object" || obj.files === null || Array.isArray(obj.files)) {
    return false;
  }

  return true;
}

/**
 * Attempt to delete a file, ignoring errors if it no longer exists.
 */
function safeDelete(filePath: string): void {
  try {
    fs.unlinkSync(filePath);
  } catch {
    // Ignore — file may already be gone.
  }
}
