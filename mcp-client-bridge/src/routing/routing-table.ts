/**
 * Fetches, caches, and refreshes the tool routing table from the orchestrator.
 * Supports ETag caching and periodic refresh.
 */

import { BridgeConfig } from '../bridge-config.js';

export interface ToolRoute {
  location: 'local' | 'remote';
  server?: string;
  fallback?: 'local' | 'remote';
  priority?: number;
}

export interface RoutingTableData {
  version: string;
  updatedAt: string;
  defaultLocation: 'local' | 'remote';
  tools: Record<string, ToolRoute>;
}

const EMPTY_TABLE: RoutingTableData = {
  version: '0.0.0',
  updatedAt: '',
  defaultLocation: 'remote',
  tools: {},
};

export class RoutingTable {
  private cached: RoutingTableData = EMPTY_TABLE;
  private etag: string | null = null;
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly refreshIntervalMs: number;
  private readonly baseUrl: string;
  private readonly token: string | null;

  constructor(config: BridgeConfig, refreshIntervalMs = 60_000) {
    this.baseUrl = config.orchestratorUrl;
    this.token = config.token;
    this.refreshIntervalMs = refreshIntervalMs;
  }

  /** Get the cached routing table. */
  getCached(): RoutingTableData { return this.cached; }

  /** Resolve a tool name to its route. */
  resolve(toolName: string): ToolRoute | null {
    return this.cached.tools[toolName] ?? null;
  }

  /** Get the default location when tool is not in table. */
  get defaultLocation(): 'local' | 'remote' {
    return this.cached.defaultLocation;
  }

  /** Set routing table from initialize response _meta. */
  setFromMeta(meta: Record<string, unknown>): void {
    const rt = meta.routingTable as RoutingTableData | undefined;
    if (rt && rt.tools && typeof rt.tools === 'object') {
      this.cached = rt;
      console.error(`[routing-table] Loaded from _meta (${Object.keys(rt.tools).length} routes)`);
    }
  }

  /** Fetch routing table from orchestrator with ETag caching. */
  async fetch(): Promise<boolean> {
    try {
      const headers: Record<string, string> = { 'Accept': 'application/json' };
      if (this.token) headers['Authorization'] = `Bearer ${this.token}`;
      if (this.etag) headers['If-None-Match'] = this.etag;

      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 10_000);

      const res = await fetch(`${this.baseUrl}/api/routing-table`, {
        method: 'GET',
        headers,
        signal: controller.signal,
      });
      clearTimeout(timeout);

      if (res.status === 304) {
        return true; // Not modified, cache is valid
      }

      if (!res.ok) {
        console.error(`[routing-table] Fetch failed: HTTP ${res.status}`);
        return false;
      }

      const data = await res.json() as RoutingTableData;
      if (!data.tools || typeof data.tools !== 'object') {
        console.error('[routing-table] Malformed response: missing tools');
        return false;
      }

      this.cached = data;
      this.etag = res.headers.get('etag');
      console.error(`[routing-table] Updated (${Object.keys(data.tools).length} routes, v${data.version})`);
      return true;
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      console.error(`[routing-table] Fetch error: ${msg} (keeping cache)`);
      return false;
    }
  }

  /** Start periodic refresh. */
  startRefresh(): void {
    if (this.refreshIntervalMs <= 0) return;
    this.scheduleRefresh();
  }

  /** Stop periodic refresh. */
  stopRefresh(): void {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }
  }

  /** Trigger an immediate refresh (e.g., after local server restart). */
  async refresh(): Promise<void> {
    await this.fetch();
  }

  private scheduleRefresh(): void {
    this.refreshTimer = setTimeout(async () => {
      await this.fetch();
      this.scheduleRefresh();
    }, this.refreshIntervalMs);
  }
}
