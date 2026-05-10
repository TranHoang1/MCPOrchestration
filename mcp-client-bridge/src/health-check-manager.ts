/**
 * Health Check Manager — Periodic ping to verify Orchestrator connectivity.
 * Triggers reconnection when ping fails.
 */

import { BridgeConfig } from './bridge-config.js';
import { HttpStreamableClient } from './http-streamable-client.js';
import { ReconnectionManager, BridgeState } from './reconnection-manager.js';

export interface HealthCheckConfig {
  pingIntervalMs: number;       // default 30000 (0 = disabled)
  pingTimeoutMs: number;        // default 5000
}

export class HealthCheckManager {
  private timer: ReturnType<typeof setTimeout> | null = null;
  private pingId = 0;
  private consecutiveFailures = 0;

  constructor(
    private readonly config: HealthCheckConfig,
    private readonly httpClient: HttpStreamableClient,
    private readonly reconnectionManager: ReconnectionManager,
  ) {}

  start(): void {
    if (this.config.pingIntervalMs <= 0) {
      console.error('[mcp-bridge] Health check disabled (interval=0)');
      return;
    }
    this.consecutiveFailures = 0;
    this.schedulePing();
    console.error(`[mcp-bridge] Health check started (interval=${this.config.pingIntervalMs}ms)`);
  }

  stop(): void {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }

  private schedulePing(): void {
    this.timer = setTimeout(async () => {
      await this.executePing();
    }, this.config.pingIntervalMs);
  }

  private async executePing(): Promise<void> {
    const ok = await this.sendPing();
    if (ok) {
      this.onPingSuccess();
    } else {
      this.onPingFailure();
    }
  }

  private async sendPing(): Promise<boolean> {
    try {
      const response = await this.httpClient.sendRequest('ping');
      // Any JSON-RPC response (even error) means server is alive
      return response !== null && response !== undefined;
    } catch {
      return false;
    }
  }

  private onPingSuccess(): void {
    if (this.consecutiveFailures > 0) {
      console.error('[mcp-bridge] Ping OK — connection restored');
    }
    this.consecutiveFailures = 0;
    this.pingId++;
    if (this.pingId % 10 === 1) {
      console.error(`[mcp-bridge] Health check OK (ping #${this.pingId})`);
    }
    this.schedulePing();
  }

  private onPingFailure(): void {
    this.consecutiveFailures++;
    console.error(
      `[mcp-bridge] Ping failed (${this.consecutiveFailures} consecutive failure${this.consecutiveFailures > 1 ? 's' : ''})`,
    );

    if (this.consecutiveFailures >= 1 && this.reconnectionManager.state !== BridgeState.RECONNECTING) {
      this.triggerReconnect();
    } else {
      // Already reconnecting or below threshold — just schedule next ping
      this.schedulePing();
    }
  }

  private triggerReconnect(): void {
    this.stop();
    console.error('[mcp-bridge] State: CONNECTED → DISCONNECTED (ping timeout)');
    this.reconnectionManager.state = BridgeState.DISCONNECTED;
    this.httpClient.resetSession();
    this.reconnectionManager.reconnectLoop().then(() => {
      if (this.reconnectionManager.state === BridgeState.CONNECTED) {
        console.error('[mcp-bridge] Reconnected — resuming health check');
        this.consecutiveFailures = 0;
      }
      // Always restart health check regardless of reconnect result
      this.schedulePing();
    }).catch(() => {
      // Reconnect loop failed — still restart health check to keep reporting
      console.error('[mcp-bridge] Reconnect failed — health check will continue reporting');
      this.schedulePing();
    });
  }
}
