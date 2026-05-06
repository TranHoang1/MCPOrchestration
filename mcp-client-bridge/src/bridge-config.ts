/**
 * Configuration for the MCP Client Bridge.
 * Loaded from CLI args and environment variables.
 */
export interface BridgeConfig {
  orchestratorUrl: string;
  reconnectEnabled: boolean;
  maxReconnectDelayMs: number;
  baseReconnectDelayMs: number;
  requestTimeoutMs: number;
  enableLocalStreamWrite: boolean;
}

export const BridgeConfig = {
  /**
   * Load configuration from CLI args and environment.
   * Priority: CLI args > env vars > defaults.
   */
  load(args: string[]): BridgeConfig {
    return {
      orchestratorUrl: parseUrl(args),
      reconnectEnabled: !args.includes('--no-reconnect'),
      maxReconnectDelayMs: 15_000,
      baseReconnectDelayMs: 1_000,
      requestTimeoutMs: parseTimeout(args),
      enableLocalStreamWrite: !args.includes('--no-local-write'),
    };
  },
};

function parseUrl(args: string[]): string {
  const idx = args.indexOf('--url');
  if (idx >= 0 && idx + 1 < args.length) return args[idx + 1];
  return process.env.ORCHESTRATOR_URL ?? 'http://localhost:8080';
}

function parseTimeout(args: string[]): number {
  const idx = args.indexOf('--timeout');
  if (idx >= 0 && idx + 1 < args.length) {
    const val = parseInt(args[idx + 1], 10);
    if (!isNaN(val)) return val;
  }
  const envVal = process.env.BRIDGE_TIMEOUT;
  if (envVal) {
    const val = parseInt(envVal, 10);
    if (!isNaN(val)) return val;
  }
  return 30_000;
}
