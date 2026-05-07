/**
 * MCP Bridge Server — stdio MCP server that proxies requests
 * to a remote Orchestrator via HTTP Streamable transport.
 */

import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from '@modelcontextprotocol/sdk/types.js';
import { BridgeConfig } from './bridge-config.js';
import { HttpStreamableClient } from './http-streamable-client.js';
import { ReconnectionManager } from './reconnection-manager.js';
import { handleStreamWrite, StreamWriteArgs } from './local-stream-write.js';
import { handleEmbedImages, EmbedImagesArgs } from './local-embed-images.js';

export class BridgeServer {
  private readonly httpClient: HttpStreamableClient;
  private readonly reconnectionManager: ReconnectionManager;
  private server: Server | null = null;

  constructor(private readonly config: BridgeConfig) {
    this.httpClient = new HttpStreamableClient(config);
    this.reconnectionManager = new ReconnectionManager(config, this.httpClient);
  }

  async start(): Promise<void> {
    await this.connectToOrchestrator();
    await this.startStdioServer();
  }

  async stop(): Promise<void> {
    console.error('[mcp-bridge] Shutting down...');
    this.httpClient.close();
    await this.server?.close();
  }

  private async connectToOrchestrator(): Promise<void> {
    const connected = await this.reconnectionManager.connectWithRetry();
    if (!connected) {
      console.error('[mcp-bridge] Failed initial connection, will retry in background');
      this.reconnectionManager.reconnectLoop().catch(() => {});
    }
  }

  private async startStdioServer(): Promise<void> {
    this.server = new Server(
      { name: 'mcp-bridge-node', version: '1.0.0' },
      { capabilities: { tools: { listChanged: true } } },
    );

    this.registerHandlers();

    const transport = new StdioServerTransport();
    await this.server.connect(transport);
    console.error('[mcp-bridge] Bridge MCP server ready (stdio transport)');
  }

  private registerHandlers(): void {
    if (!this.server) return;

    this.server.setRequestHandler(ListToolsRequestSchema, async () => {
      return { tools: this.getToolDefinitions() };
    });

    this.server.setRequestHandler(CallToolRequestSchema, async (request) => {
      const { name, arguments: args } = request.params;
      return this.handleToolCall(name, args ?? {});
    });
  }

  private getToolDefinitions() {
    interface ToolDef {
      name: string;
      description: string;
      inputSchema: { type: 'object'; properties: Record<string, unknown>; required: string[] };
    }

    const tools: ToolDef[] = [
      {
        name: 'find_tools',
        description: 'Search for available tools by describing what you want to accomplish',
        inputSchema: {
          type: 'object',
          properties: {
            query: { type: 'string', description: 'Natural language description' },
          },
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
    ];

    tools.push(
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
    );

    if (this.config.enableLocalStreamWrite) {
      tools.push({
        name: 'stream_write_file',
        description: 'Write content directly to a file on disk without buffering',
        inputSchema: {
          type: 'object',
          properties: {
            file_path: { type: 'string', description: 'Absolute path to the output file' },
            content: { type: 'string', description: 'Text content to write' },
            mode: { type: 'string', description: 'write or append' },
            encoding: { type: 'string', description: 'Character encoding (default: utf-8)' },
          },
          required: ['file_path', 'content'],
        },
      });

      tools.push({
        name: 'embed_images',
        description: 'Read a markdown file and embed all local image references as base64 data URIs. Use before export_docx to include images in the document.',
        inputSchema: {
          type: 'object',
          properties: {
            file_path: { type: 'string', description: 'Absolute path to the markdown file' },
          },
          required: ['file_path'],
        },
      });
    }

    return tools;
  }

  private async handleToolCall(name: string, args: Record<string, unknown>) {
    switch (name) {
      case 'find_tools':
        return this.handleFindTools(args);
      case 'execute_dynamic_tool':
        return this.handleExecuteDynamicTool(args);
      case 'toggle_tool':
        return this.handleProxyToOrchestrator('toggle_tool', args);
      case 'reset_tools':
        return this.handleProxyToOrchestrator('reset_tools', args);
      case 'manage_auto_approve':
        return this.handleProxyToOrchestrator('manage_auto_approve', args);
      case 'agent_log':
        return this.handleProxyToOrchestrator('agent_log', args);
      case 'stream_write_file':
        return this.handleLocalStreamWrite(args);
      case 'embed_images':
        return this.handleLocalEmbedImages(args);
      default:
        return { content: [{ type: 'text' as const, text: `Unknown tool: ${name}` }], isError: true };
    }
  }

  private async handleFindTools(args: Record<string, unknown>) {
    const query = args.query as string;
    if (!query) {
      return { content: [{ type: 'text' as const, text: "Missing 'query' parameter" }], isError: true };
    }
    try {
      const response = await this.httpClient.callTool('find_tools', { query });
      const text = response.content?.[0]?.text ?? '{}';
      return { content: [{ type: 'text' as const, text }] };
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      return { content: [{ type: 'text' as const, text: `find_tools failed: ${msg}` }], isError: true };
    }
  }

  private async handleExecuteDynamicTool(args: Record<string, unknown>) {
    const toolName = args.tool_name as string;
    if (!toolName) {
      return { content: [{ type: 'text' as const, text: "Missing 'tool_name' parameter" }], isError: true };
    }
    try {
      const response = await this.httpClient.callTool('execute_dynamic_tool', {
        tool_name: toolName,
        arguments: args.arguments ?? {},
      });
      const text = response.content?.[0]?.text ?? '{}';
      return { content: [{ type: 'text' as const, text }] };
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      return { content: [{ type: 'text' as const, text: `execute_dynamic_tool failed: ${msg}` }], isError: true };
    }
  }

  private handleLocalStreamWrite(args: Record<string, unknown>) {
    try {
      const result = handleStreamWrite(args as unknown as StreamWriteArgs);
      return { content: [{ type: 'text' as const, text: JSON.stringify(result) }] };
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      return { content: [{ type: 'text' as const, text: `stream_write_file failed: ${msg}` }], isError: true };
    }
  }

  private handleLocalEmbedImages(args: Record<string, unknown>) {
    try {
      const result = handleEmbedImages(args as unknown as EmbedImagesArgs);
      return { content: [{ type: 'text' as const, text: JSON.stringify({
        markdown: result.markdown,
        images_embedded: result.images_embedded,
        images_failed: result.images_failed,
        total_size_bytes: result.total_size_bytes,
      }) }] };
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      return { content: [{ type: 'text' as const, text: `embed_images failed: ${msg}` }], isError: true };
    }
  }

  private async handleProxyToOrchestrator(toolName: string, args: Record<string, unknown>) {
    try {
      const response = await this.httpClient.callTool(toolName, args);
      const text = response.content?.[0]?.text ?? '{}';
      return { content: [{ type: 'text' as const, text }] };
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      return { content: [{ type: 'text' as const, text: `${toolName} failed: ${msg}` }], isError: true };
    }
  }
}
