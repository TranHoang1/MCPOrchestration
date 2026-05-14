/**
 * Manages auto-reconnection to the Orchestrator with exponential backoff.
 * Supports multi-URL failover via UrlManager.
 */

import { BridgeConfig } from './bridge-config.js';
import { HttpStreamableClient } from './http-streamable-client.js';
import { UrlManager } from './url-manager.js';

export enum BridgeState {
  DISCONNECTED = 'DISCONNECTED',
  CONNECTING = 'CONNECTING',
  CONNECTED = 'CONNECTED',
  RECONNECTING = 'RECONNECTING',
}

export class ReconnectionManager {
  private attempt = 0;
  state: BridgeState = BridgeState.DISCONNECTED;
  private readonly urlManager: UrlManager;

  constructor(
    private readonly config: BridgeConfig,
    private readonly client: HttpStreamableClient,
  ) {
    this.urlManager = new UrlManager(config.orchestratorUrls);
  }

  /** Attempt initial connection trying all URLs sequentially. */
  async connectWithRetry(): Promise<boolean> {
    this.state = BridgeState.CONNECTING;
    this.urlManager.clearErrors();

    for (let i = 0; i < this.urlManager.urlCount; i++) {
      const url = this.urlManager.activeUrl;
      const idx = this.urlManager.urlIndex;
      console.error(
        `[mcp-bridge] Trying URL ${idx + 1}/${this.urlManager.urlCount}: ${url}`,
      );

      try {
        if (await this.client.initialize(url)) {
          this.state = BridgeState.CONNECTED;
          this.attempt = 0;
          return true;
        }
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        this.urlManager.markFailed(url, msg);
        console.error(
          `[mcp-bridge] URL ${idx + 1}/${this.urlManager.urlCount} failed: ${msg}`,
        );
      }

      if (this.urlManager.hasNext()) {
        this.urlManager.advance();
      }
    }

    this.reportErrors();
    this.state = BridgeState.DISCONNECTED;
    return false;
  }

  /** Background reconnection with retry + URL rotation. */
  async reconnectLoop(): Promise<void> {
    if (!this.config.reconnectEnabled) return;
    this.state = BridgeState.RECONNECTING;

    // Phase 1: Retry active URL
    for (let i = 0; i < this.config.maxRetryBeforeRotate; i++) {
      const delay = this.calculateBackoff(i);
      console.error(
        `[mcp-bridge] Retry ${i + 1}/${this.config.maxRetryBeforeRotate} ` +
        `for ${this.urlManager.activeUrl} in ${delay}ms`,
      );
      await this.sleep(delay);

      this.client.resetSession();
      if (await this.client.initialize(this.urlManager.activeUrl)) {
        this.state = BridgeState.CONNECTED;
        this.attempt = 0;
        console.error('[mcp-bridge] Reconnected successfully');
        return;
      }
    }

    // Phase 2: Rotate to other URLs
    if (this.urlManager.urlCount > 1) {
      this.urlManager.clearErrors();
      this.urlManager.markFailed(this.urlManager.activeUrl, 'Exhausted retries');

      while (this.urlManager.hasNext()) {
        const nextUrl = this.urlManager.advance();
        console.error(
          `[mcp-bridge] Switching to URL ` +
          `${this.urlManager.urlIndex + 1}/${this.urlManager.urlCount}: ${nextUrl}`,
        );

        this.client.resetSession();
        if (await this.client.initialize(nextUrl)) {
          this.state = BridgeState.CONNECTED;
          this.attempt = 0;
          return;
        }
        this.urlManager.markFailed(nextUrl, 'Connection failed');
      }

      this.reportErrors();
      this.urlManager.reset();
    }

    // Phase 3: Infinite backoff loop on first URL
    await this.infiniteReconnect();
  }

  private async infiniteReconnect(): Promise<void> {
    while (!this.client.isConnected) {
      const delay = this.calculateBackoff(this.attempt);
      console.error(
        `[mcp-bridge] Reconnecting in ${delay}ms (attempt ${this.attempt})`,
      );
      await this.sleep(delay);

      this.client.resetSession();
      if (await this.client.initialize(this.urlManager.activeUrl)) {
        this.state = BridgeState.CONNECTED;
        this.attempt = 0;
        console.error('[mcp-bridge] Reconnected successfully');
        return;
      }
      this.attempt++;
    }
  }

  private reportErrors(): void {
    const errors = this.urlManager.getErrors();
    const lines = errors.map(e => `  - ${e.url}: ${e.error}`);
    console.error(`[mcp-bridge] All URLs failed:\n${lines.join('\n')}`);
  }

  private calculateBackoff(attempt: number): number {
    const delay = this.config.baseReconnectDelayMs * Math.pow(2, attempt);
    return Math.min(delay, this.config.maxReconnectDelayMs);
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}
