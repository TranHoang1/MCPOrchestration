import { ReconnectionManager, BridgeState } from './reconnection-manager';
import { HttpStreamableClient } from './http-streamable-client';
import { BridgeConfig } from './bridge-config';

// Mock HttpStreamableClient
jest.mock('./http-streamable-client');

describe('ReconnectionManager', () => {
  let config: BridgeConfig;
  let mockClient: jest.Mocked<HttpStreamableClient>;

  beforeEach(() => {
    config = {
      orchestratorUrl: 'http://localhost:8080',
      reconnectEnabled: true,
      maxReconnectDelayMs: 100,
      baseReconnectDelayMs: 10,
      requestTimeoutMs: 30000,
      enableLocalStreamWrite: true,
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

  it('should transition to DISCONNECTED after 3 failed attempts', async () => {
    mockClient.initialize = jest.fn().mockResolvedValue(false);
    Object.defineProperty(mockClient, 'isConnected', { get: () => false });

    const manager = new ReconnectionManager(config, mockClient);
    const result = await manager.connectWithRetry();

    expect(result).toBe(false);
    expect(manager.state).toBe(BridgeState.DISCONNECTED);
    expect(mockClient.initialize).toHaveBeenCalledTimes(3);
  });

  it('should not reconnect when disabled', async () => {
    config.reconnectEnabled = false;
    mockClient.initialize = jest.fn().mockResolvedValue(false);
    Object.defineProperty(mockClient, 'isConnected', { get: () => false });

    const manager = new ReconnectionManager(config, mockClient);
    await manager.reconnectLoop();

    // Should return immediately without calling initialize
    expect(mockClient.initialize).not.toHaveBeenCalled();
  });
});
