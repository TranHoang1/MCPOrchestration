/**
 * Configuration for the MCP Client Bridge.
 * Loaded from CLI args and environment variables.
 */
export interface BridgeConfig {
  orchestratorUrls: string[];
  orchestratorUrl: string;
  reconnectEnabled: boolean;
  maxReconnectDelayMs: number;
  baseReconnectDelayMs: number;
  requestTimeoutMs: number;
  connectionTimeoutMs: number;
  maxRetryBeforeRotate: number;
  enableLocalStreamWrite: boolean;
  enableLocalServers: boolean;
  configPath: string | undefined;
  healthIntervalMs: number;
  routingRefreshMs: number;
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
    const urls = parseUrls(args);
    return {
      orchestratorUrls: urls,
      orchestratorUrl: urls[0],
      reconnectEnabled: !args.includes('--no-reconnect'),
      maxReconnectDelayMs: 15_000,
      baseReconnectDelayMs: 1_000,
      requestTimeoutMs: parseTimeout(args),
      connectionTimeoutMs: 5_000,
      maxRetryBeforeRotate: 3,
      enableLocalStreamWrite: !args.includes('--no-local-write'),
      enableLocalServers: !args.includes('--no-local-servers'),
      configPath: parseConfigPath(args),
      healthIntervalMs: parseHealthInterval(args),
      routingRefreshMs: parseRoutingRefresh(args),
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

function parseUrls(args: string[]): string[] {
  const idx = args.indexOf('--url');
  let raw: string;
  if (idx >= 0 && idx + 1 < args.length) {
    raw = args[idx + 1];
  } else {
    raw = process.env.ORCHESTRATOR_URLS
      ?? process.env.ORCHESTRATOR_URL
      ?? 'http://localhost:8080';
  }

  const urls = raw
    .split(',')
    .map(u => u.trim())
    .filter(u => u.length > 0)
    .filter(u => u.startsWith('http://') || u.startsWith('https://'));

  if (urls.length === 0) {
    console.error('[mcp-bridge] No valid URLs configured');
    process.exit(1);
  }
  if (urls.length > 10) {
    console.error('[mcp-bridge] URL list truncated to 10 entries');
    return urls.slice(0, 10);
  }
  return urls;
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

function parseConfigPath(args: string[]): string | undefined {
  const idx = args.indexOf('--config-path');
  if (idx >= 0 && idx + 1 < args.length) return args[idx + 1];
  return process.env.MCP_CONFIG_PATH ?? undefined;
}

function parseHealthInterval(args: string[]): number {
  const idx = args.indexOf('--health-interval');
  if (idx >= 0 && idx + 1 < args.length) {
    const val = parseInt(args[idx + 1], 10);
    if (!isNaN(val) && val >= 0) return val;
  }
  const envVal = process.env.MCP_HEALTH_INTERVAL;
  if (envVal) {
    const val = parseInt(envVal, 10);
    if (!isNaN(val) && val >= 0) return val;
  }
  return 30_000;
}

function parseRoutingRefresh(args: string[]): number {
  const idx = args.indexOf('--routing-refresh');
  if (idx >= 0 && idx + 1 < args.length) {
    const val = parseInt(args[idx + 1], 10);
    if (!isNaN(val) && val >= 0) return val;
  }
  const envVal = process.env.MCP_ROUTING_REFRESH;
  if (envVal) {
    const val = parseInt(envVal, 10);
    if (!isNaN(val) && val >= 0) return val;
  }
  return 60_000;
}
