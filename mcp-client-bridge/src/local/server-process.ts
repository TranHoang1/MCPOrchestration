/**
 * Single local MCP server process with state machine lifecycle.
 * Manages spawn, initialize, health check, and restart for one server.
 */

import { ChildProcess, spawn } from 'node:child_process';
import { EventEmitter } from 'node:events';
import { StdioJsonRpc } from './stdio-json-rpc.js';

export enum ServerState {
  STARTING = 'STARTING',
  READY = 'READY',
  ACTIVE = 'ACTIVE',
  CRASHED = 'CRASHED',
  RESTARTING = 'RESTARTING',
  STOPPING = 'STOPPING',
  DEAD = 'DEAD',
  FAILED = 'FAILED',
}

export interface ServerConfig {
  command: string;
  args?: string[];
  env?: Record<string, string>;
  timeout?: number;
  maxRetries?: number;
  disabled?: boolean;
}

export interface ToolDefinition {
  name: string;
  description?: string;
  inputSchema?: Record<string, unknown>;
}

export class ServerProcess extends EventEmitter {
  private process: ChildProcess | null = null;
  private rpc = new StdioJsonRpc();
  private state: ServerState = ServerState.STARTING;
  private retryCount = 0;
  private tools: ToolDefinition[] = [];

  constructor(
    readonly name: string,
    private readonly config: ServerConfig,
  ) {
    super();
  }

  get currentState(): ServerState { return this.state; }
  get currentTools(): ToolDefinition[] { return [...this.tools]; }
  get maxRetries(): number { return this.config.maxRetries ?? 3; }

  async start(): Promise<boolean> {
    this.state = ServerState.STARTING;
    this.emit('stateChange', this.name, this.state);

    try {
      this.spawnProcess();
      const initOk = await this.initialize();
      if (!initOk) {
        this.state = ServerState.FAILED;
        this.emit('stateChange', this.name, this.state);
        return false;
      }
      this.state = ServerState.READY;
      const toolsOk = await this.fetchTools();
      if (toolsOk) this.state = ServerState.ACTIVE;
      this.emit('stateChange', this.name, this.state);
      return true;
    } catch (err) {
      console.error(`[local:${this.name}] Start failed:`, err);
      this.state = ServerState.FAILED;
      this.emit('stateChange', this.name, this.state);
      return false;
    }
  }

  async stop(): Promise<void> {
    this.state = ServerState.STOPPING;
    this.emit('stateChange', this.name, this.state);
    await this.killProcess();
    this.tools = [];
  }

  async restart(): Promise<boolean> {
    if (this.retryCount >= this.maxRetries) {
      this.state = ServerState.DEAD;
      this.emit('stateChange', this.name, this.state);
      console.error(`[local:${this.name}] Max retries (${this.maxRetries}) exceeded → DEAD`);
      return false;
    }
    this.state = ServerState.RESTARTING;
    this.emit('stateChange', this.name, this.state);

    const delay = Math.min(1000 * Math.pow(2, this.retryCount), 30_000);
    this.retryCount++;
    console.error(`[local:${this.name}] Restart #${this.retryCount} in ${delay}ms`);
    await this.sleep(delay);
    return this.start();
  }

  async callTool(toolName: string, args: Record<string, unknown>): Promise<unknown> {
    return this.rpc.sendRequest('tools/call', { name: toolName, arguments: args });
  }

  async healthCheck(): Promise<boolean> {
    try {
      await this.rpc.sendRequest('tools/list', {}, 5000);
      return true;
    } catch {
      return false;
    }
  }

  private spawnProcess(): void {
    const env = { ...process.env, ...this.config.env };
    this.process = spawn(this.config.command, this.config.args ?? [], {
      stdio: ['pipe', 'pipe', 'pipe'],
      env,
      shell: process.platform === 'win32',
    });

    this.rpc.attach(this.process);
    this.process.stderr?.on('data', (chunk: Buffer) => {
      console.error(`[local:${this.name}:stderr] ${chunk.toString().trim()}`);
    });
    this.process.on('exit', (code) => this.onExit(code));
    this.process.on('error', (err) => {
      console.error(`[local:${this.name}] Process error:`, err.message);
      this.handleCrash();
    });
  }

  private async initialize(): Promise<boolean> {
    try {
      const result = await this.rpc.sendRequest('initialize', {
        protocolVersion: '2025-03-26',
        capabilities: {},
        clientInfo: { name: 'mcp-bridge-local', version: '1.0.0' },
      }, this.config.timeout ?? 30_000);
      this.rpc.sendNotification('notifications/initialized', {});
      return !!result;
    } catch (err) {
      console.error(`[local:${this.name}] Initialize timeout/error:`, err);
      return false;
    }
  }

  private async fetchTools(): Promise<boolean> {
    try {
      const result = await this.rpc.sendRequest('tools/list', {}) as { tools?: ToolDefinition[] };
      this.tools = result?.tools ?? [];
      console.error(`[local:${this.name}] Discovered ${this.tools.length} tools`);
      return true;
    } catch {
      return false;
    }
  }

  private onExit(code: number | null): void {
    if (this.state === ServerState.STOPPING) return;
    console.error(`[local:${this.name}] Process exited with code ${code}`);
    this.handleCrash();
  }

  private handleCrash(): void {
    this.rpc.rejectAll('Process terminated');
    if (this.state === ServerState.STOPPING || this.state === ServerState.DEAD) return;
    this.state = ServerState.CRASHED;
    this.emit('stateChange', this.name, this.state);
    this.emit('crashed', this.name);
  }

  private async killProcess(): Promise<void> {
    if (!this.process) return;
    const proc = this.process;
    this.process = null;
    this.rpc.detach();

    proc.kill('SIGTERM');
    const exited = await Promise.race([
      new Promise<boolean>((r) => proc.on('exit', () => r(true))),
      this.sleep(5000).then(() => false),
    ]);

    if (!exited) {
      console.error(`[local:${this.name}] Force killing (SIGKILL)`);
      proc.kill('SIGKILL');
    }
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((r) => setTimeout(r, ms));
  }
}
