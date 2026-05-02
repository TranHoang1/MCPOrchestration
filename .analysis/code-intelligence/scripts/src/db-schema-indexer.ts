/**
 * Database Schema Indexer for the Code Intelligence System.
 *
 * Generates:
 * - `database-schema.md` — human-readable Markdown analysis of database schemas
 * - KB ingestion payloads — structured JSON for Knowledge Base ingestion
 *
 * The agent queries the database via MCP tools (`mcp_database_mcp_list_schemas`,
 * `mcp_database_mcp_list_objects`, `mcp_database_mcp_get_object_details`),
 * passes the results as a `DatabaseSchema` object, and this module generates
 * the Markdown file and KB payloads.
 */

import * as fs from "node:fs";
import * as path from "node:path";
import type {
  DatabaseSchema,
  DataSourceInfo,
  SchemaInfo,
  TableInfo,
  ColumnInfo,
  KbIngestPayload,
} from "./types.js";

// ---------------------------------------------------------------------------
// Default output directory
// ---------------------------------------------------------------------------

const DEFAULT_OUTPUT_DIR = path.resolve(
  process.cwd(),
  ".analysis/code-intelligence",
);

// ---------------------------------------------------------------------------
// Public functions
// ---------------------------------------------------------------------------

/**
 * Generate the `database-schema.md` analysis file from schema data.
 *
 * @param schemaData - Database schema information provided by the agent.
 * @param outputDir  - Directory to write the file into.
 *                     Defaults to `.analysis/code-intelligence/`.
 */
export function generateDatabaseSchemaMarkdown(
  schemaData: DatabaseSchema,
  outputDir?: string,
): void {
  const dir = outputDir ?? DEFAULT_OUTPUT_DIR;
  const timestamp = new Date().toISOString();

  const lines: string[] = [
    `# Database Schema — ${schemaData.projectName}`,
    "",
    `**Last Updated:** ${timestamp}`,
    "",
  ];

  // Check for empty/stale data
  if (isSchemaDataEmpty(schemaData)) {
    lines.push(
      "> **Note:** Schema data is empty or may be stale. The database MCP may have been unavailable during indexing.",
    );
    lines.push("");
  }

  // Data Sources table
  lines.push("## Data Sources");
  lines.push("");
  lines.push("| Name | Type | Host | Database | Access |");
  lines.push("|------|------|------|----------|--------|");

  if (schemaData.dataSources.length > 0) {
    for (const ds of schemaData.dataSources) {
      lines.push(
        `| ${escapeMarkdown(ds.name)} | ${escapeMarkdown(ds.type)} | ${escapeMarkdown(ds.host)} | ${escapeMarkdown(ds.database)} | ${escapeMarkdown(ds.access)} |`,
      );
    }
  } else {
    // Fallback: use top-level dataSourceName/dataSourceType
    lines.push(
      `| ${escapeMarkdown(schemaData.dataSourceName)} | ${escapeMarkdown(schemaData.dataSourceType)} | — | — | — |`,
    );
  }

  lines.push("");

  // Per-data-source sections with schemas
  const dataSourceLabel =
    schemaData.dataSources.length > 0
      ? `${schemaData.dataSources[0].name} (${schemaData.dataSources[0].type})`
      : `${schemaData.dataSourceName} (${schemaData.dataSourceType})`;

  lines.push(`## ${escapeMarkdown(dataSourceLabel)}`);
  lines.push("");

  // Schemas table
  lines.push("### Schemas");
  lines.push("");
  lines.push("| Schema | Tables | Description |");
  lines.push("|--------|--------|-------------|");

  for (const schema of schemaData.schemas) {
    const tableCount = schema.tables.length;
    const description = schema.description || "—";
    lines.push(
      `| ${escapeMarkdown(schema.name)} | ${tableCount} | ${escapeMarkdown(description)} |`,
    );
  }

  lines.push("");

  // Tables table and Table Details per schema
  for (const schema of schemaData.schemas) {
    lines.push(`### Tables — ${escapeMarkdown(schema.name)} schema`);
    lines.push("");
    lines.push("| Table | Columns | Rows (approx) | Description |");
    lines.push("|-------|---------|---------------|-------------|");

    for (const table of schema.tables) {
      const colCount = table.columns.length;
      const rowCount = table.rowCount;
      const description = table.description || "—";
      lines.push(
        `| ${escapeMarkdown(table.name)} | ${colCount} | ${rowCount} | ${escapeMarkdown(description)} |`,
      );
    }

    lines.push("");

    // Table Details
    lines.push("### Table Details");
    lines.push("");

    for (const table of schema.tables) {
      lines.push(`#### ${escapeMarkdown(table.name)}`);
      lines.push("");
      lines.push("| Column | Type | Nullable | Default | Description |");
      lines.push("|--------|------|----------|---------|-------------|");

      for (const col of table.columns) {
        const nullable = col.nullable ? "YES" : "NO";
        const defaultVal = col.defaultValue ?? "—";
        const description = col.description || "—";
        lines.push(
          `| ${escapeMarkdown(col.name)} | ${escapeMarkdown(col.type)} | ${nullable} | ${escapeMarkdown(defaultVal)} | ${escapeMarkdown(description)} |`,
        );
      }

      lines.push("");
    }
  }

  const content = lines.join("\n");
  atomicWrite(path.join(dir, "database-schema.md"), content);
}

/**
 * Generate structured JSON payloads for Knowledge Base ingestion.
 *
 * Returns one payload per schema, tagged with `code-index`, `database`,
 * and the schema name.
 *
 * @param schemaData - Database schema information provided by the agent.
 * @returns Array of KB ingestion payloads, one per schema.
 */
export function generateKbIngestJson(
  schemaData: DatabaseSchema,
): KbIngestPayload[] {
  const payloads: KbIngestPayload[] = [];

  for (const schema of schemaData.schemas) {
    const content = buildSchemaContent(schema, schemaData);
    payloads.push({
      title: `Database Schema — ${schema.name}`,
      content,
      tags: `code-index, database, ${schema.name}`,
      project: schemaData.projectName,
    });
  }

  return payloads;
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Check if schema data is empty or stale (no schemas or no tables).
 */
function isSchemaDataEmpty(schemaData: DatabaseSchema): boolean {
  if (schemaData.schemas.length === 0) {
    return true;
  }

  // Check if all schemas have zero tables
  const totalTables = schemaData.schemas.reduce(
    (sum, s) => sum + s.tables.length,
    0,
  );
  return totalTables === 0;
}

/**
 * Build a summary content string for a single schema for KB ingestion.
 */
function buildSchemaContent(
  schema: SchemaInfo,
  schemaData: DatabaseSchema,
): string {
  const lines: string[] = [];

  lines.push(`Schema: ${schema.name}`);
  lines.push(`Data Source: ${schemaData.dataSourceName} (${schemaData.dataSourceType})`);
  lines.push(`Tables: ${schema.tables.length}`);
  if (schema.description) {
    lines.push(`Description: ${schema.description}`);
  }
  lines.push("");

  for (const table of schema.tables) {
    lines.push(`Table: ${table.name}`);
    if (table.description) {
      lines.push(`  Description: ${table.description}`);
    }
    lines.push(`  Columns: ${table.columns.length}`);
    lines.push(`  Rows (approx): ${table.rowCount}`);

    for (const col of table.columns) {
      const nullable = col.nullable ? "nullable" : "not null";
      const defaultStr = col.defaultValue ? `, default: ${col.defaultValue}` : "";
      const descStr = col.description ? ` — ${col.description}` : "";
      lines.push(`    - ${col.name}: ${col.type} (${nullable}${defaultStr})${descStr}`);
    }

    lines.push("");
  }

  return lines.join("\n");
}

/**
 * Escape pipe characters in Markdown table cell content.
 */
function escapeMarkdown(text: string): string {
  return text.replace(/\|/g, "\\|");
}

/**
 * Write content to a file atomically: write to a `.tmp` sibling first,
 * then rename over the target path.
 */
function atomicWrite(filePath: string, content: string): void {
  const dir = path.dirname(filePath);
  fs.mkdirSync(dir, { recursive: true });

  const tmpPath = `${filePath}.tmp`;
  fs.writeFileSync(tmpPath, content, "utf-8");
  fs.renameSync(tmpPath, filePath);
}
