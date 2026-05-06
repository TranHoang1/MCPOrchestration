/**
 * HTTP Streamable client connecting to the MCP Orchestrator /mcp endpoint.
 * Manages session lifecycle and request/response handling.
 */

import { BridgeConfig } from './bridge-config.js';

interface JsonRpcRequest {
  jsonrpc: '2.0';
  id: number;
  method: string;
  params?: Record<string, unknown>;
}

interface JsonRpcResponse {
  jsonrpc: '2.0';
  id: number | null;
  result?: unknown;
  error?: { code: number; message: string };
}

export class HttpStreamableClient {
  private sessionId: string | null = null;
  private requestIdCounter = 0;
  private _connected = false;

  constructor(private readonly config: BridgeConfig) {}

  get isConnected(): boolean {
    return this._connected;
  }

  async initialize(): Promise<boolean> {
    const request = this.buildRequest('initialize', {
      protocolVersion: '2025-03-26',
      capabilities: {},
      clientInfo: { name: 'mcp-bridge-node', version: '1.0.0' },
    });

    try {
      const response = await this.sendRaw(request, false);
      this.sessionId = response.headers.get('mcp-session-id');
      this._connected = this.sessionId !== null;
      console.error(`[mcp-bridge] Session initialized: ${this.sessionId}`);
      return this._connected;
    } catch (err) {
      console.error(`[mcp-bridge] Initialize failed:`, err);
      this._connected = false;
      return false;
    }
  }

  async sendRequest(method: string, params?: Record<string, unknown>): Promise<JsonRpcResponse> {
    const request = this.buildRequest(method, params);
    const response = await this.sendRaw(request, true);
    const body = await response.text();
    return JSON.parse(body) as JsonRpcResponse;
  }

  resetSession(): void {
    this.sessionId = null;
    this._connected = false;
  }

  close(): void {
    this._connected = false;
    this.sessionId = null;
  }

  private async sendRaw(body: string, includeSession: boolean): Promise<Response> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };
    if (includeSession && this.sessionId) {
      headers['Mcp-Session-Id'] = this.sessionId;
    }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.config.requestTimeoutMs);

    try {
      const response = await fetch(`${this.config.orchestratorUrl}/mcp`, {
        method: 'POST',
        headers,
        body,
        signal: controller.signal,
      });
      return response;
    } finally {
      clearTimeout(timeout);
    }
  }

  private buildRequest(method: string, params?: Record<string, unknown>): string {
    const request: JsonRpcRequest = {
      jsonrpc: '2.0',
      id: ++this.requestIdCounter,
      method,
      ...(params && { params }),
    };
    return JSON.stringify(request);
  }
}
