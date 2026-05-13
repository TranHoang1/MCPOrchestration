import { BridgeConfig } from './bridge-config';

describe('BridgeConfig', () => {
  const originalEnv = process.env;
  const originalExit = process.exit;

  beforeEach(() => {
    jest.spyOn(console, 'error').mockImplementation(() => {});
    process.env = { ...originalEnv };
    delete process.env.ORCHESTRATOR_URL;
    delete process.env.BRIDGE_TIMEOUT;
    delete process.env.MCP_BRIDGE_TOKEN;
  });

  afterAll(() => {
    process.env = originalEnv;
    process.exit = originalExit;
  });

  it('should use --url flag when provided', () => {
    const config = BridgeConfig.load(['--url', 'http://remote:9090']);
    expect(config.orchestratorUrl).toBe('http://remote:9090');
  });

  it('should default to localhost:8080', () => {
    const config = BridgeConfig.load([]);
    expect(config.orchestratorUrl).toBe('http://localhost:8080');
  });

  it('should use ORCHESTRATOR_URL env var as fallback', () => {
    process.env.ORCHESTRATOR_URL = 'http://env-host:7070';
    const config = BridgeConfig.load([]);
    expect(config.orchestratorUrl).toBe('http://env-host:7070');
  });

  it('should disable reconnect with --no-reconnect', () => {
    const config = BridgeConfig.load(['--no-reconnect']);
    expect(config.reconnectEnabled).toBe(false);
  });

  it('should enable reconnect by default', () => {
    const config = BridgeConfig.load([]);
    expect(config.reconnectEnabled).toBe(true);
  });

  it('should parse --timeout flag', () => {
    const config = BridgeConfig.load(['--timeout', '60000']);
    expect(config.requestTimeoutMs).toBe(60000);
  });

  it('should default timeout to 30000ms', () => {
    const config = BridgeConfig.load([]);
    expect(config.requestTimeoutMs).toBe(30000);
  });

  it('should have max reconnect delay of 15s', () => {
    const config = BridgeConfig.load([]);
    expect(config.maxReconnectDelayMs).toBe(15000);
  });

  it('should disable local stream write with --no-local-write', () => {
    const config = BridgeConfig.load(['--no-local-write']);
    expect(config.enableLocalStreamWrite).toBe(false);
  });

  describe('token parsing', () => {
    const validToken = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyMSJ9.dGVzdHNpZ25hdHVyZQ';

    it('should parse --token from CLI args', () => {
      const config = BridgeConfig.load(['--token', validToken]);
      expect(config.token).toBe(validToken);
    });

    it('should use MCP_BRIDGE_TOKEN env as fallback', () => {
      process.env.MCP_BRIDGE_TOKEN = validToken;
      const config = BridgeConfig.load([]);
      expect(config.token).toBe(validToken);
    });

    it('should prefer --token CLI over env var', () => {
      process.env.MCP_BRIDGE_TOKEN = 'envtoken.payload.sig';
      const config = BridgeConfig.load(['--token', validToken]);
      expect(config.token).toBe(validToken);
    });

    it('should default token to null when not provided', () => {
      const config = BridgeConfig.load([]);
      expect(config.token).toBeNull();
    });

    it('should exit with code 1 for invalid token format (not 3 parts)', () => {
      const mockExit = jest.fn() as unknown as typeof process.exit;
      process.exit = mockExit;
      try {
        BridgeConfig.load(['--token', 'invalid-no-dots']);
      } catch { /* exit mock */ }
      expect(mockExit).toHaveBeenCalledWith(1);
    });

    it('should exit with code 1 for token with invalid base64url chars', () => {
      const mockExit = jest.fn() as unknown as typeof process.exit;
      process.exit = mockExit;
      try {
        BridgeConfig.load(['--token', 'abc.def!.ghi']);
      } catch { /* exit mock */ }
      expect(mockExit).toHaveBeenCalledWith(1);
    });
  });
});
