/**
 * Property-based tests for the Configuration Loader.
 *
 * Property 16: Configuration Round-Trip
 * Property 17: Invalid Configuration Fallback
 */

import { describe, it, afterEach } from "vitest";
import * as fc from "fast-check";
import * as fs from "node:fs";
import * as path from "node:path";
import * as os from "node:os";
import { loadConfig, DEFAULT_CONFIG } from "../../src/config-loader.js";
import type { IndexConfig } from "../../src/types.js";

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
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "config-prop-"));
  tempDirs.push(dir);
  return dir;
}

/**
 * Arbitrary for a valid file extension string starting with ".".
 * Generates strings like ".ts", ".abc", ".x1".
 */
const extensionArb = fc
  .stringMatching(/^[a-z0-9]{1,6}$/)
  .map((s) => `.${s}`);

/**
 * Arbitrary for a non-empty directory name (no path separators).
 */
const dirNameArb = fc.stringMatching(/^[a-z0-9_-]{1,20}$/);

/**
 * Arbitrary for a non-empty file pattern string.
 */
const filePatternArb = fc.stringMatching(/^[a-z0-9.*_-]{1,20}$/);

/**
 * Arbitrary for a valid IndexConfig object.
 */
const indexConfigArb: fc.Arbitrary<IndexConfig> = fc.record({
  includedExtensions: fc.array(extensionArb, { minLength: 0, maxLength: 10 }),
  excludedDirectories: fc.array(dirNameArb, { minLength: 0, maxLength: 10 }),
  excludedFilePatterns: fc.array(filePatternArb, {
    minLength: 0,
    maxLength: 10,
  }),
});

describe("Feature: code-intelligence, Property 16: Configuration Round-Trip", () => {
  /**
   * **Validates: Requirements 10.1**
   *
   * For any valid IndexConfig, serializing to JSON, writing to a file,
   * and reading back via loadConfig() produces an identical config.
   */
  it("round-trips any valid IndexConfig through JSON file I/O", () => {
    fc.assert(
      fc.property(indexConfigArb, (config) => {
        const dir = makeTempDir();
        const filePath = path.join(dir, "index-config.json");

        // Serialize and write
        fs.writeFileSync(filePath, JSON.stringify(config), "utf-8");

        // Read back via loadConfig
        const loaded = loadConfig(filePath);

        // Verify deep equality
        expect(loaded).toEqual(config);
      }),
      { numRuns: 100 },
    );
  });
});

describe("Feature: code-intelligence, Property 17: Invalid Configuration Fallback", () => {
  /**
   * **Validates: Requirements 10.4**
   *
   * For any non-JSON string written to the config file, loadConfig()
   * returns DEFAULT_CONFIG without throwing an error.
   */
  it("returns DEFAULT_CONFIG for any invalid JSON content", () => {
    /**
     * Arbitrary that produces strings which are NOT valid JSON.
     * We filter out any string that JSON.parse can successfully parse.
     */
    const invalidJsonArb = fc
      .oneof(
        // Random strings (may include unicode)
        fc.string({ minLength: 1, maxLength: 200 }),
        // Partial / broken JSON fragments
        fc.constantFrom(
          "{",
          '{"includedExtensions":',
          "[1,2,",
          '{"key": undefined}',
          "null null",
          "<xml>not json</xml>",
        ),
        // Random byte sequences as string
        fc
          .array(fc.integer({ min: 1, max: 255 }), {
            minLength: 1,
            maxLength: 100,
          })
          .map((arr) => String.fromCharCode(...arr)),
      )
      .filter((s) => {
        try {
          JSON.parse(s);
          return false; // valid JSON — exclude it
        } catch {
          return true; // invalid JSON — keep it
        }
      });

    fc.assert(
      fc.property(
        invalidJsonArb,
        (invalidContent) => {
          const dir = makeTempDir();
          const filePath = path.join(dir, "index-config.json");

          // Write invalid content
          fs.writeFileSync(filePath, invalidContent, "utf-8");

          // loadConfig must not throw
          let loaded: IndexConfig | undefined;
          expect(() => {
            loaded = loadConfig(filePath);
          }).not.toThrow();

          // Must return DEFAULT_CONFIG
          expect(loaded).toEqual(DEFAULT_CONFIG);
        },
      ),
      { numRuns: 100 },
    );
  });
});
