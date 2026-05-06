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
    }

    return tools;
  }

  private async handleToolCall(name: string, args: Record<string, unknown>) {
    switch (name) {
      case 'find_tools':
        return this.handleFindTools(args);
      case 'execute_dynamic_tool':
        return this.handleExecuteDynamicTool(args);
      case 'stream_write_file':
        return this.handleLocalStreamWrite(args);
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
      const response = await this.httpClient.sendRequest('find_tools', { query });
      const result = JSON.stringify(response.result ?? response);
      return { content: [{ type: 'text' as const, text: result }] };
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
      const response = await this.httpClient.sendRequest('execute_dynamic_tool', {
        tool_name: toolName,
        arguments: args.arguments ?? {},
      });
      const result = JSON.stringify(response.result ?? response);
      return { content: [{ type: 'text' as const, text: result }] };
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
}
