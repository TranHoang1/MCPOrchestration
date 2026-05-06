import { BridgeConfig } from './bridge-config';

describe('BridgeConfig', () => {
  const originalEnv = process.env;

  beforeEach(() => {
    process.env = { ...originalEnv };
    delete process.env.ORCHESTRATOR_URL;
    delete process.env.BRIDGE_TIMEOUT;
  });

  afterAll(() => {
    process.env = originalEnv;
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
});
