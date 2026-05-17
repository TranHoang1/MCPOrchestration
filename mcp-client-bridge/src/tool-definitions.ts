/**
 * Static tool definitions for the bridge's built-in tools.
 * Separated from bridge-server.ts for maintainability.
 */

export interface ToolDef {
  name: string;
  description: string;
  inputSchema: { type: 'object'; properties: Record<string, unknown>; required: string[] };
}

/** Core bridge tools (always available). */
export function getCoreTools(): ToolDef[] {
  return [
    {
      name: 'find_tools',
      description: 'Search for available tools by describing what you want to accomplish. ' +
        'Returns tool definitions with input schemas. ' +
        'Also discovers hidden tools not listed in tools/list (e.g. jira_project_sync, jira_sync_status, export_drawio).',
      inputSchema: {
        type: 'object',
        properties: { query: { type: 'string', description: 'Natural language description' } },
        required: ['query'],
      },
    },
    {
      name: 'execute_dynamic_tool',
      description: 'Execute a tool on an upstream MCP server',
      inputSchema: {
        type: 'object',
        properties: {
          tool_name: { type: 'string', description: 'Exact tool name' },
          arguments: { type: 'object', description: 'Arguments for the tool' },
        },
        required: ['tool_name'],
      },
    },
    {
      name: 'toggle_tool',
      description: 'Enable or disable a specific tool or an entire server for the current session.',
      inputSchema: {
        type: 'object',
        properties: {
          tool_name: { type: 'string', description: 'Name of the tool to toggle' },
          server_name: { type: 'string', description: 'Name of the server to toggle (disables all its tools)' },
          enabled: { type: 'boolean', description: 'Whether to enable or disable' },
        },
        required: ['enabled'],
      },
    },
    {
      name: 'reset_tools',
      description: 'Reset all tool/server toggle states to their default enabled state for the session.',
      inputSchema: {
        type: 'object',
        properties: {
          server_name: { type: 'string', description: 'Optional. If provided, only resets tools for this server.' },
        },
        required: [],
      },
    },
    {
      name: 'manage_auto_approve',
      description: 'Add or remove tools from the auto-approve list (persists across restarts).',
      inputSchema: {
        type: 'object',
        properties: {
          tool_name: { type: 'string', description: 'Name of the tool to update' },
          server_name: { type: 'string', description: 'Name of the server (if updating all tools of a server)' },
          auto_approve: { type: 'boolean', description: 'Whether to add or remove from auto-approve list' },
        },
        required: ['auto_approve'],
      },
    },
    {
      name: 'agent_log',
      description: 'Write an execution log entry for agent activity tracking.',
      inputSchema: {
        type: 'object',
        properties: {
          ticket_key: { type: 'string', description: 'Jira ticket key (e.g. MTO-12)' },
          agent_name: { type: 'string', description: 'Agent: SM, BA, TA, SA, QA, DEV, DEVOPS' },
          step: { type: 'string', description: 'Step ID (e.g. Step-1, Self-Check)' },
          status: { type: 'string', description: 'START|DONE|ARTIFACT|SKIP|ERROR|WARN|VERIFY' },
          message: { type: 'string', description: 'What happened' },
          artifacts: { type: 'string', description: 'Optional JSON of artifact paths' },
        },
        required: ['ticket_key', 'agent_name', 'step', 'status', 'message'],
      },
    },
  ];
}

/** Local file operation tools (when enableLocalStreamWrite is true). */
export function getLocalFileTools(): ToolDef[] {
  return [
    {
      name: 'stream_write_file',
      description: 'Write content directly to a file on disk without buffering. ' +
        'Supports absolute and relative paths (relative resolved from workspace root). ' +
        "Modes: 'write' (overwrite/create), 'append' (add to end), 'create' (fail if file exists). " +
        'If file does not exist, it will be created automatically. ' +
        'If file already exists and no content is provided, no changes are made (no-op).',
      inputSchema: {
        type: 'object',
        properties: {
          file_path: { type: 'string', description: 'Path to the output file. Supports absolute or relative path (resolved from workspace root)' },
          content: { type: 'string', description: 'Text content to write. Optional — if omitted or empty, creates an empty file (or no-op if file already exists).' },
          mode: { type: 'string', description: "write, append, or create. 'create' fails if file already exists. Default: 'write'" },
          encoding: { type: 'string', description: 'Character encoding (default: utf-8)' },
        },
        required: ['file_path'],
      },
    },
    {
      name: 'embed_images',
      description: 'Read a markdown file and replace all local image references with inline base64 data URIs, ' +
        'then write the result to output_path (or overwrite the original file if output_path is omitted). ' +
        'Returns metadata only — no markdown content in response. Pure file I/O, no AI tokens consumed. ' +
        'Use before export_docx to include images in the document.',
      inputSchema: {
        type: 'object',
        properties: {
          file_path: { type: 'string', description: 'Path to the source markdown file. Supports absolute or relative path (resolved from workspace root)' },
          output_path: { type: 'string', description: 'Optional. Path to save the embedded result. If omitted, the original file is overwritten in-place.' },
        },
        required: ['file_path'],
      },
    },
  ];
}
