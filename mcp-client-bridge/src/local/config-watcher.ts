/**
 * Watches mcp-servers.json for changes with debounce.
 * Emits 'configChanged' when file is modified.
 */

import * as fs from 'node:fs';
import * as path from 'node:path';
import { EventEmitter } from 'node:events';
import { ServerConfig } from './server-process.js';

export interface McpServersConfig {
  mcpServers: Record<string, ServerConfig>;
}

export class ConfigWatcher extends EventEmitter {
  private watcher: fs.FSWatcher | null = null;
  private debounceTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly debounceMs = 1000;

  constructor(private configPath: string) {
    super();
  }

  get path(): string { return this.configPath; }

  /** Load and parse config from disk. Returns empty config on error. */
  loadConfig(): McpServersConfig {
    try {
      const raw = fs.readFileSync(this.configPath, 'utf-8');
      const parsed = JSON.parse(raw) as McpServersConfig;
      if (!parsed.mcpServers || typeof parsed.mcpServers !== 'object') {
        console.error(`[config-watcher] Invalid config: missing mcpServers`);
        return { mcpServers: {} };
      }
      return parsed;
    } catch (err) {
      console.error(`[config-watcher] Failed to load ${this.configPath}:`, err);
      return { mcpServers: {} };
    }
  }

  /** Start watching the config file for changes. */
  start(): void {
    if (!fs.existsSync(this.configPath)) {
      console.error(`[config-watcher] Config not found: ${this.configPath}`);
      return;
    }
    try {
      this.watcher = fs.watch(this.configPath, () => this.onFileChange());
      console.error(`[config-watcher] Watching: ${this.configPath}`);
    } catch (err) {
      console.error(`[config-watcher] Watch failed:`, err);
    }
  }

  stop(): void {
    if (this.debounceTimer) clearTimeout(this.debounceTimer);
    this.watcher?.close();
    this.watcher = null;
  }

  private onFileChange(): void {
    if (this.debounceTimer) clearTimeout(this.debounceTimer);
    this.debounceTimer = setTimeout(() => {
      console.error('[config-watcher] Config changed, reloading...');
      this.emit('configChanged', this.loadConfig());
    }, this.debounceMs);
  }

  /** Resolve config path: CLI arg → CWD → home directory. */
  static resolveConfigPath(cliPath?: string): string {
    if (cliPath) {
      const abs = path.isAbsolute(cliPath) ? cliPath : path.resolve(cliPath);
      if (fs.existsSync(abs)) return abs;
    }
    const cwdPath = path.resolve(process.cwd(), 'mcp-servers.json');
    if (fs.existsSync(cwdPath)) return cwdPath;

    const homePath = path.resolve(
      process.env.HOME ?? process.env.USERPROFILE ?? '.',
      '.mcp-bridge',
      'mcp-servers.json',
    );
    if (fs.existsSync(homePath)) return homePath;

    // Return CWD path even if not found (will be created or error later)
    return cwdPath;
  }
}
