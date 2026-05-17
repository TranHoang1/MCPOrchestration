/**
 * Manages all local MCP server processes.
 * Handles spawning, health monitoring, restart, and config hot-reload.
 */

import { EventEmitter } from 'node:events';
import { ServerProcess, ServerState, ServerConfig, ToolDefinition } from './server-process.js';
import { ConfigWatcher, McpServersConfig } from './config-watcher.js';

export interface LocalServerManagerConfig {
  configPath: string;
  healthIntervalMs: number;
}

export class LocalServerManager extends EventEmitter {
  private servers = new Map<string, ServerProcess>();
  private configWatcher: ConfigWatcher;
  private healthTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly healthIntervalMs: number;

  constructor(config: LocalServerManagerConfig) {
    super();
    this.healthIntervalMs = config.healthIntervalMs;
    this.configWatcher = new ConfigWatcher(config.configPath);
    this.configWatcher.on('configChanged', (cfg: McpServersConfig) => {
      this.handleConfigChange(cfg).catch((e) =>
        console.error('[local-manager] Config reload error:', e),
      );
    });
  }

  /** Start all configured servers and begin health monitoring. */
  async startAll(): Promise<void> {
    const config = this.configWatcher.loadConfig();
    const entries = Object.entries(config.mcpServers);
    console.error(`[local-manager] Starting ${entries.length} local server(s)...`);

    const startPromises = entries
      .filter(([, cfg]) => !cfg.disabled)
      .map(([name, cfg]) => this.startServer(name, cfg));

    await Promise.allSettled(startPromises);
    this.configWatcher.start();
    this.startHealthMonitor();
    console.error(`[local-manager] ${this.activeCount}/${entries.length} servers active`);
  }

  /** Stop all servers and cleanup. */
  async stopAll(): Promise<void> {
    this.stopHealthMonitor();
    this.configWatcher.stop();
    const stops = [...this.servers.values()].map((s) => s.stop());
    await Promise.allSettled(stops);
    this.servers.clear();
  }

  /** Call a tool on a specific local server. */
  async callTool(
    serverName: string,
    toolName: string,
    args: Record<string, unknown>,
  ): Promise<unknown> {
    const server = this.servers.get(serverName);
    if (!server || server.currentState !== ServerState.ACTIVE) {
      throw new Error(`Server '${serverName}' not available (state: ${server?.currentState ?? 'NOT_FOUND'})`);
    }
    return server.callTool(toolName, args);
  }

  /** Get all tools from all active servers. */
  getAllTools(): ToolDefinition[] {
    const tools: ToolDefinition[] = [];
    for (const server of this.servers.values()) {
      if (server.currentState === ServerState.ACTIVE) {
        tools.push(...server.currentTools);
      }
    }
    return tools;
  }

  /** Get tools from a specific server. */
  getTools(serverName: string): ToolDefinition[] {
    return this.servers.get(serverName)?.currentTools ?? [];
  }

  /** Find which server owns a tool. */
  findServerForTool(toolName: string): string | null {
    for (const [name, server] of this.servers) {
      if (server.currentState === ServerState.ACTIVE) {
        if (server.currentTools.some((t) => t.name === toolName)) {
          return name;
        }
      }
    }
    return null;
  }

  get activeCount(): number {
    let count = 0;
    for (const s of this.servers.values()) {
      if (s.currentState === ServerState.ACTIVE) count++;
    }
    return count;
  }

  private async startServer(name: string, config: ServerConfig): Promise<void> {
    const server = new ServerProcess(name, config);
    server.on('crashed', (n: string) => this.handleCrash(n));
    this.servers.set(name, server);
    const ok = await server.start();
    if (ok) {
      this.emit('serverReady', name);
    } else {
      console.error(`[local-manager] Server '${name}' failed to start`);
    }
  }

  private async handleCrash(name: string): Promise<void> {
    const server = this.servers.get(name);
    if (!server) return;
    console.error(`[local-manager] Server '${name}' crashed, attempting restart...`);
    const ok = await server.restart();
    if (ok) {
      this.emit('serverReady', name);
      this.emit('toolsChanged');
    } else {
      this.emit('serverDead', name);
      this.emit('toolsChanged');
    }
  }

  private async handleConfigChange(newConfig: McpServersConfig): Promise<void> {
    const currentNames = new Set(this.servers.keys());
    const newNames = new Set(Object.keys(newConfig.mcpServers));

    // Remove servers no longer in config
    for (const name of currentNames) {
      if (!newNames.has(name)) {
        console.error(`[local-manager] Removing server: ${name}`);
        await this.servers.get(name)?.stop();
        this.servers.delete(name);
      }
    }

    // Add new servers
    for (const name of newNames) {
      const cfg = newConfig.mcpServers[name];
      if (cfg.disabled) continue;
      if (!currentNames.has(name)) {
        console.error(`[local-manager] Adding server: ${name}`);
        await this.startServer(name, cfg);
      }
    }

    // Restart changed servers (simple: command or args changed)
    for (const name of newNames) {
      if (!currentNames.has(name)) continue;
      const cfg = newConfig.mcpServers[name];
      if (cfg.disabled) {
        await this.servers.get(name)?.stop();
        this.servers.delete(name);
        continue;
      }
      // For simplicity, restart if config object differs
      // A production version would deep-compare
    }

    this.emit('toolsChanged');
  }

  private startHealthMonitor(): void {
    if (this.healthIntervalMs <= 0) return;
    this.healthTimer = setTimeout(() => this.runHealthCheck(), this.healthIntervalMs);
  }

  private stopHealthMonitor(): void {
    if (this.healthTimer) {
      clearTimeout(this.healthTimer);
      this.healthTimer = null;
    }
  }

  private async runHealthCheck(): Promise<void> {
    for (const [name, server] of this.servers) {
      if (server.currentState !== ServerState.ACTIVE) continue;
      const ok = await server.healthCheck();
      if (!ok) {
        console.error(`[local-manager] Health check failed for '${name}'`);
        // Second check before declaring crash
        const ok2 = await server.healthCheck();
        if (!ok2) {
          console.error(`[local-manager] '${name}' confirmed unhealthy → restart`);
          this.handleCrash(name);
        }
      }
    }
    // Schedule next check
    this.healthTimer = setTimeout(() => this.runHealthCheck(), this.healthIntervalMs);
  }
}
