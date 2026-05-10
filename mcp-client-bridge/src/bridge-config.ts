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
  pingIntervalMs: number;
  pingTimeoutMs: number;
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
      pingIntervalMs: parsePingInterval(args),
      pingTimeoutMs: parsePingTimeout(args),
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

function parsePingInterval(args: string[]): number {
  const idx = args.indexOf('--ping-interval');
  if (idx >= 0 && idx + 1 < args.length) {
    const val = parseInt(args[idx + 1], 10);
    if (!isNaN(val) && (val === 0 || val >= 5000)) return val;
  }
  const envVal = process.env.BRIDGE_PING_INTERVAL;
  if (envVal) {
    const val = parseInt(envVal, 10);
    if (!isNaN(val) && (val === 0 || val >= 5000)) return val;
  }
  return 30_000;
}

function parsePingTimeout(args: string[]): number {
  const idx = args.indexOf('--ping-timeout');
  if (idx >= 0 && idx + 1 < args.length) {
    const val = parseInt(args[idx + 1], 10);
    if (!isNaN(val) && val >= 1000) return val;
  }
  const envVal = process.env.BRIDGE_PING_TIMEOUT;
  if (envVal) {
    const val = parseInt(envVal, 10);
    if (!isNaN(val) && val >= 1000) return val;
  }
  return 5_000;
}
