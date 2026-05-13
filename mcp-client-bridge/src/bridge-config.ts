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
  token: string | null;
}

export const BridgeConfig = {
  /**
   * Load configuration from CLI args and environment.
   * Priority: CLI args > env vars > defaults.
   */
  load(args: string[]): BridgeConfig {
    const token = parseToken(args);
    return {
      orchestratorUrl: parseUrl(args),
      reconnectEnabled: !args.includes('--no-reconnect'),
      maxReconnectDelayMs: 15_000,
      baseReconnectDelayMs: 1_000,
      requestTimeoutMs: parseTimeout(args),
      enableLocalStreamWrite: !args.includes('--no-local-write'),
      pingIntervalMs: parsePingInterval(args),
      pingTimeoutMs: parsePingTimeout(args),
      token,
    };
  },
};

/** Parse --token CLI arg or MCP_BRIDGE_TOKEN env. Validates JWT format. */
function parseToken(args: string[]): string | null {
  const idx = args.indexOf('--token');
  const cliToken = (idx >= 0 && idx + 1 < args.length) ? args[idx + 1] : null;
  const token = cliToken ?? process.env.MCP_BRIDGE_TOKEN ?? null;
  if (token) validateTokenFormat(token);
  return token;
}

/** Validate token is 3-part base64url JWT (header.payload.signature). */
function validateTokenFormat(token: string): void {
  const parts = token.split('.');
  if (parts.length !== 3) {
    console.error('[mcp-bridge] Invalid token format. Expected JWT (header.payload.signature)');
    process.exit(1);
  }
  const base64urlRegex = /^[A-Za-z0-9_-]+$/;
  for (const part of parts) {
    if (!base64urlRegex.test(part)) {
      console.error('[mcp-bridge] Invalid token format. JWT parts must be base64url encoded');
      process.exit(1);
    }
  }
}

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
