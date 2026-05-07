/**
 * HTTP Streamable client — POST /mcp with JSON-RPC.
 * Simple stateless request-response. No SSE, no sessions.
 */

import { BridgeConfig } from './bridge-config.js';

interface JsonRpcResponse {
  jsonrpc: '2.0';
  id: number | null;
  result?: unknown;
  error?: { code: number; message: string };
}

interface ToolCallResult {
  content?: Array<{ type: string; text: string }>;
  isError?: boolean;
}

export class HttpStreamableClient {
  private requestIdCounter = 0;
  private _connected = false;

  constructor(private readonly config: BridgeConfig) {}

  get isConnected(): boolean {
    return this._connected;
  }

  async initialize(): Promise<boolean> {
    try {
      const response = await this.post('initialize', {
        protocolVersion: '2025-03-26',
        capabilities: {},
        clientInfo: {
          name: 'mcp-bridge-node',
          version: '1.0.0',
        },
      });

      if (response.result || response.error) {
        // Even "already initialized" means server is up
        this._connected = true;
        console.error(
          '[mcp-bridge] Connected to orchestrator ' +
            'via HTTP Streamable',
        );
        return true;
      }
      return false;
    } catch (err) {
      console.error('[mcp-bridge] Initialize failed:', err);
      this._connected = false;
      return false;
    }
  }

  async callTool(
    name: string,
    args: Record<string, unknown>,
  ): Promise<ToolCallResult> {
    const response = await this.post('tools/call', {
      name,
      arguments: args,
    });

    if (response.error) {
      return {
        content: [{
          type: 'text',
          text: JSON.stringify(response.error),
        }],
        isError: true,
      };
    }

    return (response.result as ToolCallResult) ?? {
      content: [{ type: 'text', text: '{}' }],
    };
  }

  async sendRequest(
    method: string,
    params?: Record<string, unknown>,
  ): Promise<JsonRpcResponse> {
    return this.post(method, params);
  }

  resetSession(): void {
    this._connected = false;
  }

  close(): void {
    this._connected = false;
  }

  private async post(
    method: string,
    params?: Record<string, unknown>,
  ): Promise<JsonRpcResponse> {
    const id = ++this.requestIdCounter;
    const body = JSON.stringify({
      jsonrpc: '2.0',
      id,
      method,
      ...(params && { params }),
    });

    const controller = new AbortController();
    const timeout = setTimeout(
      () => controller.abort(),
      this.config.requestTimeoutMs,
    );

    try {
      const res = await fetch(
        `${this.config.orchestratorUrl}/mcp`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body,
          signal: controller.signal,
        },
      );

      if (!res.ok) {
        throw new Error(
          `HTTP ${res.status}: ${res.statusText}`,
        );
      }

      const text = await res.text();
      if (!text) {
        throw new Error('Empty response');
      }
      return JSON.parse(text) as JsonRpcResponse;
    } finally {
      clearTimeout(timeout);
    }
  }
}
