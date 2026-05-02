/**
 * Property-based tests for the Database Schema Indexer.
 *
 * Property 15: Database Analysis Completeness
 *
 * **Validates: Requirements 9.2**
 */

import { describe, it, expect, afterEach } from "vitest";
import * as fc from "fast-check";
import * as fs from "node:fs";
import * as path from "node:path";
import * as os from "node:os";
import { generateDatabaseSchemaMarkdown } from "../../src/db-schema-indexer.js";
import type {
  DatabaseSchema,
  SchemaInfo,
  TableInfo,
  ColumnInfo,
} from "../../src/types.js";

// ---------------------------------------------------------------------------
// Temp directory management
// ---------------------------------------------------------------------------

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

function makeTempDir(): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "db-schema-prop-"));
  tempDirs.push(dir);
  return dir;
}

// ---------------------------------------------------------------------------
// Arbitraries
// ---------------------------------------------------------------------------

/** Safe identifier for schema/table/column names: lowercase alpha start, alphanumeric. */
const identifierArb = fc.stringMatching(/^[a-z][a-z0-9]{1,12}$/);

/** Column type arbitrary — common SQL types. */
const columnTypeArb = fc.constantFrom(
  "integer",
  "bigint",
  "varchar(255)",
  "text",
  "boolean",
  "timestamp",
  "date",
  "numeric(10,2)",
  "uuid",
  "jsonb",
);

/** Column description: simple alphanumeric words, no pipe characters. */
const descriptionArb = fc
  .array(fc.stringMatching(/^[a-zA-Z0-9]{1,8}$/), {
    minLength: 1,
    maxLength: 4,
  })
  .map((words) => words.join(" "));

/** Default value arbitrary — nullable string. */
const defaultValueArb = fc.oneof(
  fc.constant(null),
  fc.constantFrom("0", "true", "false", "now()", "gen_random_uuid()"),
);

/** Single column arbitrary. */
const columnArb: fc.Arbitrary<ColumnInfo> = fc.record({
  name: identifierArb,
  type: columnTypeArb,
  nullable: fc.boolean(),
  defaultValue: defaultValueArb,
  description: descriptionArb,
});

/** Generate 1-8 columns with unique names. */
const columnsArb: fc.Arbitrary<ColumnInfo[]> = fc
  .array(columnArb, { minLength: 1, maxLength: 8 })
  .map((cols) => {
    const seen = new Set<string>();
    return cols.filter((c) => {
      if (seen.has(c.name)) return false;
      seen.add(c.name);
      return true;
    });
  })
  .filter((cols) => cols.length >= 1);

/** Single table arbitrary. */
const tableArb: fc.Arbitrary<TableInfo> = fc
  .record({
    name: identifierArb,
    columns: columnsArb,
    rowCount: fc.integer({ min: 0, max: 1_000_000 }),
    description: descriptionArb,
  });

/** Generate 1-5 tables with unique names. */
const tablesArb: fc.Arbitrary<TableInfo[]> = fc
  .array(tableArb, { minLength: 1, maxLength: 5 })
  .map((tables) => {
    const seen = new Set<string>();
    return tables.filter((t) => {
      if (seen.has(t.name)) return false;
      seen.add(t.name);
      return true;
    });
  })
  .filter((tables) => tables.length >= 1);

/** Single schema arbitrary. */
const schemaArb: fc.Arbitrary<SchemaInfo> = fc.record({
  name: identifierArb,
  tables: tablesArb,
  description: descriptionArb,
});

/** Generate 1-5 schemas with unique names. */
const schemasArb: fc.Arbitrary<SchemaInfo[]> = fc
  .array(schemaArb, { minLength: 1, maxLength: 5 })
  .map((schemas) => {
    const seen = new Set<string>();
    return schemas.filter((s) => {
      if (seen.has(s.name)) return false;
      seen.add(s.name);
      return true;
    });
  })
  .filter((schemas) => schemas.length >= 1);

/** Full DatabaseSchema arbitrary. */
const databaseSchemaArb: fc.Arbitrary<DatabaseSchema> = fc.record({
  projectName: identifierArb,
  dataSources: fc.constant([]),
  schemas: schemasArb,
  dataSourceName: identifierArb,
  dataSourceType: fc.constantFrom("PostgreSQL", "MySQL", "Oracle", "SQLite"),
});

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Escape pipe characters for matching in Markdown table cells.
 * The generator mirrors the escapeMarkdown function in db-schema-indexer.
 */
function escapeForMatch(text: string): string {
  return text.replace(/\|/g, "\\|");
}

/**
 * Extract Markdown table data rows from a section of lines.
 * Skips the header row and separator row, returns data rows.
 */
function extractTableRows(
  lines: string[],
  startIdx: number,
): { rows: string[][]; endIdx: number } {
  const rows: string[][] = [];
  let i = startIdx;

  // Skip until we find a table header (starts with |)
  while (i < lines.length && !lines[i].trim().startsWith("|")) {
    i++;
  }
  if (i >= lines.length) return { rows, endIdx: i };

  // Skip header row
  i++;
  // Skip separator row (|---|---|...)
  if (i < lines.length && /^\|[-\s|]+\|$/.test(lines[i].trim())) {
    i++;
  }

  // Read data rows
  while (i < lines.length && lines[i].trim().startsWith("|")) {
    const cells = lines[i]
      .split("|")
      .map((c) => c.trim())
      .filter(Boolean);
    rows.push(cells);
    i++;
  }

  return { rows, endIdx: i };
}

// ---------------------------------------------------------------------------
// Property 15: Database Analysis Completeness
// ---------------------------------------------------------------------------

describe("Feature: code-intelligence, Property 15: Database Analysis Completeness", () => {
  /**
   * **Validates: Requirements 9.2**
   *
   * Generate random DatabaseSchema objects with 1-5 schemas, each with
   * 1-5 tables, each with 1-8 columns. Run generateDatabaseSchemaMarkdown(),
   * read the generated database-schema.md, and verify:
   * - Each schema has a row in the Schemas table
   * - Each table has a row in its schema's Tables table with correct
   *   column count and row count
   * - Each table has a detailed column definition section with all
   *   columns listed
   */
  it("generated database-schema.md contains complete schema, table, and column information", () => {
    fc.assert(
      fc.property(databaseSchemaArb, (schemaData) => {
        const dir = makeTempDir();

        // Generate the database schema markdown
        generateDatabaseSchemaMarkdown(schemaData, dir);

        // Read the generated file
        const filePath = path.join(dir, "database-schema.md");
        expect(fs.existsSync(filePath)).toBe(true);

        const content = fs.readFileSync(filePath, "utf-8");
        const lines = content.split("\n");

        // -----------------------------------------------------------------
        // 1. Verify each schema has a row in the Schemas table
        // -----------------------------------------------------------------
        const schemasHeaderIdx = lines.findIndex(
          (l) => l.trim() === "### Schemas",
        );
        expect(schemasHeaderIdx).toBeGreaterThanOrEqual(0);

        const schemasTable = extractTableRows(lines, schemasHeaderIdx + 1);

        // One row per schema
        expect(schemasTable.rows.length).toBe(schemaData.schemas.length);

        for (const schema of schemaData.schemas) {
          const matchingRow = schemasTable.rows.find(
            (row) => row[0] === escapeForMatch(schema.name),
          );
          expect(matchingRow).toBeDefined();
          // Table count column
          expect(matchingRow![1]).toBe(String(schema.tables.length));
        }

        // -----------------------------------------------------------------
        // 2. Verify each table has a row in its schema's Tables table
        // -----------------------------------------------------------------
        for (const schema of schemaData.schemas) {
          const tablesHeaderPattern = `### Tables — ${escapeForMatch(schema.name)} schema`;
          const tablesHeaderIdx = lines.findIndex(
            (l) => l.trim() === tablesHeaderPattern,
          );
          expect(tablesHeaderIdx).toBeGreaterThanOrEqual(0);

          const tablesTable = extractTableRows(lines, tablesHeaderIdx + 1);

          // One row per table in this schema
          expect(tablesTable.rows.length).toBe(schema.tables.length);

          for (const table of schema.tables) {
            const matchingRow = tablesTable.rows.find(
              (row) => row[0] === escapeForMatch(table.name),
            );
            expect(matchingRow).toBeDefined();
            // Column count
            expect(matchingRow![1]).toBe(String(table.columns.length));
            // Row count (approx)
            expect(matchingRow![2]).toBe(String(table.rowCount));
          }
        }

        // -----------------------------------------------------------------
        // 3. Verify each table has a detailed column definition section
        // -----------------------------------------------------------------
        for (const schema of schemaData.schemas) {
          for (const table of schema.tables) {
            // Find the table detail heading: #### {tableName}
            const detailHeaderIdx = lines.findIndex(
              (l) => l.trim() === `#### ${escapeForMatch(table.name)}`,
            );
            expect(detailHeaderIdx).toBeGreaterThanOrEqual(0);

            const columnTable = extractTableRows(
              lines,
              detailHeaderIdx + 1,
            );

            // One row per column
            expect(columnTable.rows.length).toBe(table.columns.length);

            for (const col of table.columns) {
              const matchingRow = columnTable.rows.find(
                (row) => row[0] === escapeForMatch(col.name),
              );
              expect(matchingRow).toBeDefined();
              // Type
              expect(matchingRow![1]).toBe(escapeForMatch(col.type));
              // Nullable
              expect(matchingRow![2]).toBe(col.nullable ? "YES" : "NO");
              // Default value
              const expectedDefault =
                col.defaultValue !== null
                  ? escapeForMatch(col.defaultValue)
                  : "—";
              expect(matchingRow![3]).toBe(expectedDefault);
            }
          }
        }
      }),
      { numRuns: 100 },
    );
  });
});
