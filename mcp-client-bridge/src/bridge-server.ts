/**
 * MCP Bridge Server — stdio MCP server that proxies requests
 * to a remote Orchestrator via HTTP Streamable transport.
 * Also manages local MCP servers and routes calls via SmartRouter.
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
import { HealthCheckManager } from './health-check-manager.js';
import { setWorkspaceRoot } from './local-stream-write.js';
import { LocalServerManager } from './local/local-server-manager.js';
import { ConfigWatcher } from './local/config-watcher.js';
import { RoutingTable } from './routing/routing-table.js';
import { SmartRouter } from './routing/smart-router.js';
import { UnifiedRegistry } from './registry/unified-registry.js';
import { getCoreTools, getLocalFileTools } from './tool-definitions.js';
import { BridgeHandlers } from './bridge-handlers.js';

/** Set of bridge-native tool names (not routed via SmartRouter). */
const BRIDGE_TOOLS = new Set([
  'find_tools', 'execute_dynamic_tool', 'toggle_tool',
  'reset_tools', 'manage_auto_approve', 'agent_log',
  'stream_write_file', 'embed_images',
]);

export class BridgeServer {
  private readonly httpClient: HttpStreamableClient;
  private readonly reconnectionManager: ReconnectionManager;
  private readonly healthCheckManager: HealthCheckManager;
  private readonly handlers: BridgeHandlers;
  private localManager: LocalServerManager | null = null;
  private routingTable: RoutingTable | null = null;
  private smartRouter: SmartRouter | null = null;
  private unifiedRegistry: UnifiedRegistry | null = null;
  private server: Server | null = null;

  constructor(private readonly config: BridgeConfig) {
    this.httpClient = new HttpStreamableClient(config);
    this.reconnectionManager = new ReconnectionManager(config, this.httpClient);
    this.healthCheckManager = new HealthCheckManager(
      { pingIntervalMs: config.pingIntervalMs, pingTimeoutMs: config.pingTimeoutMs },
      this.httpClient,
      this.reconnectionManager,
    );
    this.handlers = new BridgeHandlers(this.httpClient);
  }

  async start(): Promise<void> {
    if (this.config.enableLocalServers) await this.initLocalServers();
    await this.connectToOrchestrator();
    await this.startStdioServer();
  }

  async stop(): Promise<void> {
    console.error('[mcp-bridge] Shutting down...');
    this.healthCheckManager.stop();
    this.routingTable?.stopRefresh();
    await this.localManager?.stopAll();
    this.httpClient.close();
    await this.server?.close();
  }

  private async initLocalServers(): Promise<void> {
    const configPath = ConfigWatcher.resolveConfigPath(this.config.configPath);
    console.error(`[mcp-bridge] Local servers config: ${configPath}`);

    this.localManager = new LocalServerManager({
      configPath,
      healthIntervalMs: this.config.healthIntervalMs,
    });
    this.routingTable = new RoutingTable(this.config, this.config.routingRefreshMs);
    this.unifiedRegistry = new UnifiedRegistry('local-first');
    this.smartRouter = new SmartRouter(this.routingTable, this.localManager, this.httpClient);

    this.localManager.on('toolsChanged', () => this.refreshRegistry());
    this.localManager.on('serverReady', () => this.refreshRegistry());

    await this.localManager.startAll();
    this.refreshRegistry();
  }

  private async connectToOrchestrator(): Promise<void> {
    const connected = await this.reconnectionManager.connectWithRetry();
    if (!connected) {
      console.error('[mcp-bridge] Failed initial connection, will retry in background');
      this.reconnectionManager.reconnectLoop().catch(() => {});
    } else if (this.routingTable) {
      await this.routingTable.fetch();
      this.routingTable.startRefresh();
    }
    this.healthCheckManager.start();
  }

  private async startStdioServer(): Promise<void> {
    this.server = new Server(
      { name: 'mcp-bridge-node', version: '1.0.0' },
      { capabilities: { tools: { listChanged: true } } },
    );
    this.registerHandlers();
    this.server.oninitialized = async () => { await this.resolveWorkspaceRoot(); };

    const transport = new StdioServerTransport();
    await this.server.connect(transport);
    console.error('[mcp-bridge] Bridge MCP server ready (stdio transport)');
  }

  private async resolveWorkspaceRoot(): Promise<void> {
    if (!this.server) return;
    try {
      const result = await this.server.listRoots();
      if (result.roots && result.roots.length > 0) {
        const rootUri = result.roots[0].uri;
        const rootPath = rootUri.startsWith('file:///')
          ? decodeURIComponent(rootUri.slice(8)).replace(/\//g, '\\')
          : decodeURIComponent(rootUri.replace('file://', ''));
        setWorkspaceRoot(rootPath);
        console.error(`[mcp-bridge] Workspace root from client: ${rootPath}`);
      }
    } catch {
      console.error('[mcp-bridge] listRoots not supported by client, using cwd');
    }
  }

  private registerHandlers(): void {
    if (!this.server) return;
    this.server.setRequestHandler(ListToolsRequestSchema, async () => {
      const tools = [...getCoreTools()];
      if (this.config.enableLocalStreamWrite) tools.push(...getLocalFileTools());
      return { tools };
    });
    this.server.setRequestHandler(CallToolRequestSchema, async (request) => {
      const { name, arguments: args } = request.params;
      return this.handleToolCall(name, args ?? {});
    });
  }

  private async handleToolCall(name: string, args: Record<string, unknown>) {
    if (this.smartRouter && !BRIDGE_TOOLS.has(name)) {
      return this.handleSmartRoute(name, args);
    }
    switch (name) {
      case 'find_tools': return this.handlers.handleFindTools(args);
      case 'execute_dynamic_tool': return this.handlers.handleExecuteDynamicTool(args);
      case 'toggle_tool': return this.handlers.proxyToOrchestrator('toggle_tool', args);
      case 'reset_tools': return this.handlers.proxyToOrchestrator('reset_tools', args);
      case 'manage_auto_approve': return this.handlers.proxyToOrchestrator('manage_auto_approve', args);
      case 'agent_log': return this.handlers.proxyToOrchestrator('agent_log', args);
      case 'stream_write_file': return this.handlers.handleLocalStreamWrite(args);
      case 'embed_images': return this.handlers.handleLocalEmbedImages(args);
      default:
        if (this.smartRouter) return this.handleSmartRoute(name, args);
        return { content: [{ type: 'text' as const, text: `Unknown tool: ${name}` }], isError: true };
    }
  }

  private async handleSmartRoute(name: string, args: Record<string, unknown>) {
    try {
      const result = await this.smartRouter!.route(name, args);
      return { content: result.content.map((c) => ({ type: c.type as 'text', text: c.text })), isError: result.isError };
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      return { content: [{ type: 'text' as const, text: `Route failed: ${msg}` }], isError: true };
    }
  }

  private refreshRegistry(): void {
    if (!this.unifiedRegistry || !this.localManager) return;
    this.unifiedRegistry.setLocalTools(this.localManager.getAllTools());
    console.error(`[mcp-bridge] Registry: ${this.unifiedRegistry.localCount} local tools`);
  }
}
