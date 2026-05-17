import { ReconnectionManager, BridgeState } from './reconnection-manager';
import { HttpStreamableClient } from './http-streamable-client';
import { BridgeConfig } from './bridge-config';

// Mock HttpStreamableClient
jest.mock('./http-streamable-client');

describe('ReconnectionManager', () => {
  let config: BridgeConfig;
  let mockClient: jest.Mocked<HttpStreamableClient>;

  beforeEach(() => {
    jest.spyOn(console, 'error').mockImplementation(() => {});
    config = {
      orchestratorUrls: ['http://localhost:8080'],
      orchestratorUrl: 'http://localhost:8080',
      reconnectEnabled: true,
      maxReconnectDelayMs: 100,
      baseReconnectDelayMs: 10,
      requestTimeoutMs: 30000,
      connectionTimeoutMs: 5000,
      maxRetryBeforeRotate: 3,
      enableLocalStreamWrite: true,
      enableLocalServers: true,
      configPath: undefined,
      healthIntervalMs: 30000,
      routingRefreshMs: 60000,
      pingIntervalMs: 30000,
      pingTimeoutMs: 5000,
      token: null,
    };
    mockClient = new HttpStreamableClient(config) as jest.Mocked<HttpStreamableClient>;
  });

  it('should start in DISCONNECTED state', () => {
    const manager = new ReconnectionManager(config, mockClient);
    expect(manager.state).toBe(BridgeState.DISCONNECTED);
  });

  it('should transition to CONNECTED on successful init', async () => {
    mockClient.initialize = jest.fn().mockResolvedValue(true);
    Object.defineProperty(mockClient, 'isConnected', { get: () => true });

    const manager = new ReconnectionManager(config, mockClient);
    const result = await manager.connectWithRetry();

    expect(result).toBe(true);
    expect(manager.state).toBe(BridgeState.CONNECTED);
  });

  it('should transition to DISCONNECTED after all URLs fail', async () => {
    mockClient.initialize = jest.fn().mockResolvedValue(false);
    Object.defineProperty(mockClient, 'isConnected', { get: () => false });

    const manager = new ReconnectionManager(config, mockClient);
    const result = await manager.connectWithRetry();

    expect(result).toBe(false);
    expect(manager.state).toBe(BridgeState.DISCONNECTED);
  });

  it('should try multiple URLs on failover', async () => {
    config.orchestratorUrls = ['http://primary:8080', 'http://backup:8080'];
    config.orchestratorUrl = 'http://primary:8080';

    mockClient.initialize = jest.fn()
      .mockResolvedValueOnce(false)
      .mockResolvedValueOnce(true);
    Object.defineProperty(mockClient, 'isConnected', { get: () => true });

    const manager = new ReconnectionManager(config, mockClient);
    const result = await manager.connectWithRetry();

    expect(result).toBe(true);
    expect(mockClient.initialize).toHaveBeenCalledTimes(2);
    expect(mockClient.initialize).toHaveBeenCalledWith('http://primary:8080');
    expect(mockClient.initialize).toHaveBeenCalledWith('http://backup:8080');
  });

  it('should not reconnect when disabled', async () => {
    config.reconnectEnabled = false;
    mockClient.initialize = jest.fn().mockResolvedValue(false);
    Object.defineProperty(mockClient, 'isConnected', { get: () => false });

    const manager = new ReconnectionManager(config, mockClient);
    await manager.reconnectLoop();

    expect(mockClient.initialize).not.toHaveBeenCalled();
  });
});
