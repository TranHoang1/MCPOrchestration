/**
 * Routes tool calls to the correct destination (local stdio or remote HTTP).
 * O(1) lookup by tool name with fallback support and metrics collection.
 */

import { RoutingTable, ToolRoute } from './routing-table.js';
import { LocalServerManager } from '../local/local-server-manager.js';
import { HttpStreamableClient } from '../http-streamable-client.js';

export interface ToolMetrics {
  callCount: number;
  errorCount: number;
  totalLatencyMs: number;
  lastCallAt: number | null;
}

export interface RouteResult {
  content: Array<{ type: string; text: string }>;
  isError?: boolean;
}

export class SmartRouter {
  private metrics = new Map<string, ToolMetrics>();

  constructor(
    private readonly routingTable: RoutingTable,
    private readonly localManager: LocalServerManager,
    private readonly httpClient: HttpStreamableClient,
  ) {}

  /** Route a tool call to the appropriate destination. */
  async route(toolName: string, args: Record<string, unknown>): Promise<RouteResult> {
    const start = Date.now();
    try {
      const result = await this.doRoute(toolName, args);
      this.recordMetric(toolName, Date.now() - start, false);
      return result;
    } catch (err) {
      this.recordMetric(toolName, Date.now() - start, true);
      throw err;
    }
  }

  /** Get metrics for all tools. */
  getMetrics(): Map<string, ToolMetrics> {
    return new Map(this.metrics);
  }

  /** Get metrics for a specific tool. */
  getToolMetrics(toolName: string): ToolMetrics | undefined {
    return this.metrics.get(toolName);
  }

  private async doRoute(toolName: string, args: Record<string, unknown>): Promise<RouteResult> {
    const route = this.routingTable.resolve(toolName);

    // If route exists in routing table, use it
    if (route) {
      return this.routeByDefinition(toolName, args, route);
    }

    // No explicit route — check if tool exists locally
    const localServer = this.localManager.findServerForTool(toolName);
    if (localServer) {
      return this.callLocal(localServer, toolName, args);
    }

    // Default: use routing table's defaultLocation
    if (this.routingTable.defaultLocation === 'local') {
      throw new Error(`Tool '${toolName}' not found in any local server`);
    }

    // Default to remote
    return this.callRemote(toolName, args);
  }

  private async routeByDefinition(
    toolName: string,
    args: Record<string, unknown>,
    route: ToolRoute,
  ): Promise<RouteResult> {
    if (route.location === 'local') {
      try {
        const server = route.server ?? this.localManager.findServerForTool(toolName);
        if (!server) throw new Error(`No local server for tool '${toolName}'`);
        return await this.callLocal(server, toolName, args);
      } catch (err) {
        // Fallback to remote if configured
        if (route.fallback === 'remote') {
          console.error(`[smart-router] Local call failed for '${toolName}', falling back to remote`);
          return this.callRemote(toolName, args);
        }
        throw err;
      }
    }

    if (route.location === 'remote') {
      try {
        return await this.callRemote(toolName, args);
      } catch (err) {
        // Fallback to local if configured
        if (route.fallback === 'local') {
          const server = this.localManager.findServerForTool(toolName);
          if (server) {
            console.error(`[smart-router] Remote call failed for '${toolName}', falling back to local`);
            return this.callLocal(server, toolName, args);
          }
        }
        throw err;
      }
    }

    throw new Error(`Invalid route location for '${toolName}': ${route.location}`);
  }

  private async callLocal(
    serverName: string,
    toolName: string,
    args: Record<string, unknown>,
  ): Promise<RouteResult> {
    const result = await this.localManager.callTool(serverName, toolName, args) as {
      content?: Array<{ type: string; text: string }>;
      isError?: boolean;
    };
    return {
      content: result?.content ?? [{ type: 'text', text: JSON.stringify(result) }],
      isError: result?.isError,
    };
  }

  private async callRemote(
    toolName: string,
    args: Record<string, unknown>,
  ): Promise<RouteResult> {
    const response = await this.httpClient.callTool(toolName, args);
    return {
      content: response.content ?? [{ type: 'text', text: '{}' }],
      isError: response.isError,
    };
  }

  private recordMetric(toolName: string, latencyMs: number, isError: boolean): void {
    const existing = this.metrics.get(toolName) ?? {
      callCount: 0,
      errorCount: 0,
      totalLatencyMs: 0,
      lastCallAt: null,
    };
    existing.callCount++;
    if (isError) existing.errorCount++;
    existing.totalLatencyMs += latencyMs;
    existing.lastCallAt = Date.now();
    this.metrics.set(toolName, existing);
  }
}
