/**
 * Manages auto-reconnection to the Orchestrator with exponential backoff.
 * Max delay capped at 15 seconds per spec.
 */

import { BridgeConfig } from './bridge-config.js';
import { HttpStreamableClient } from './http-streamable-client.js';

export enum BridgeState {
  DISCONNECTED = 'DISCONNECTED',
  CONNECTING = 'CONNECTING',
  CONNECTED = 'CONNECTED',
  RECONNECTING = 'RECONNECTING',
}

export class ReconnectionManager {
  private attempt = 0;
  state: BridgeState = BridgeState.DISCONNECTED;

  constructor(
    private readonly config: BridgeConfig,
    private readonly client: HttpStreamableClient,
  ) {}

  /**
   * Attempt initial connection with retry (up to 3 attempts).
   */
  async connectWithRetry(): Promise<boolean> {
    this.state = BridgeState.CONNECTING;

    for (let i = 0; i < 3; i++) {
      if (await this.client.initialize()) {
        this.state = BridgeState.CONNECTED;
        this.attempt = 0;
        return true;
      }
      const delayMs = this.calculateBackoff(i);
      console.error(`[mcp-bridge] Connection attempt ${i + 1} failed, retrying in ${delayMs}ms`);
      await this.sleep(delayMs);
    }

    this.state = BridgeState.DISCONNECTED;
    return false;
  }

  /**
   * Background reconnection loop with exponential backoff.
   */
  async reconnectLoop(): Promise<void> {
    if (!this.config.reconnectEnabled) return;

    while (!this.client.isConnected) {
      this.state = BridgeState.RECONNECTING;
      const delayMs = this.calculateBackoff(this.attempt);
      console.error(`[mcp-bridge] Reconnecting in ${delayMs}ms (attempt ${this.attempt})`);
      await this.sleep(delayMs);

      this.client.resetSession();
      if (await this.client.initialize()) {
        this.state = BridgeState.CONNECTED;
        this.attempt = 0;
        console.error('[mcp-bridge] Reconnected successfully');
        return;
      }
      this.attempt++;
    }
  }

  private calculateBackoff(attempt: number): number {
    const delay = this.config.baseReconnectDelayMs * Math.pow(2, attempt);
    return Math.min(delay, this.config.maxReconnectDelayMs);
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}
