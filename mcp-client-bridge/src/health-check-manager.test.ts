import { HealthCheckManager, HealthCheckConfig } from './health-check-manager';
import { HttpStreamableClient } from './http-streamable-client';
import { ReconnectionManager, BridgeState } from './reconnection-manager';
import { BridgeConfig } from './bridge-config';

// Mock dependencies
jest.useFakeTimers();

describe('HealthCheckManager', () => {
  let config: HealthCheckConfig;
  let httpClient: jest.Mocked<HttpStreamableClient>;
  let reconnectionManager: jest.Mocked<ReconnectionManager>;
  let manager: HealthCheckManager;

  beforeEach(() => {
    jest.spyOn(console, 'error').mockImplementation(() => {});
    config = { pingIntervalMs: 30000, pingTimeoutMs: 5000 };

    httpClient = {
      sendRequest: jest.fn(),
      resetSession: jest.fn(),
      isConnected: true,
    } as unknown as jest.Mocked<HttpStreamableClient>;

    reconnectionManager = {
      state: BridgeState.CONNECTED,
      reconnectLoop: jest.fn().mockResolvedValue(undefined),
    } as unknown as jest.Mocked<ReconnectionManager>;

    manager = new HealthCheckManager(config, httpClient, reconnectionManager);
  });

  afterEach(() => {
    manager.stop();
    jest.clearAllTimers();
  });

  it('should start ping timer when started', () => {
    manager.start();
    expect(jest.getTimerCount()).toBe(1);
  });

  it('should not start when interval is 0 (disabled)', () => {
    const disabledConfig = { pingIntervalMs: 0, pingTimeoutMs: 5000 };
    const disabledManager = new HealthCheckManager(disabledConfig, httpClient, reconnectionManager);
    disabledManager.start();
    expect(jest.getTimerCount()).toBe(0);
  });

  it('should send ping after interval', async () => {
    httpClient.sendRequest.mockResolvedValue({ jsonrpc: '2.0', id: 1, result: {} });
    manager.start();

    jest.advanceTimersByTime(30000);
    await Promise.resolve(); // flush microtasks

    expect(httpClient.sendRequest).toHaveBeenCalledWith('ping');
  });

  it('should trigger reconnect on ping failure', async () => {
    httpClient.sendRequest.mockRejectedValue(new Error('timeout'));
    manager.start();

    jest.advanceTimersByTime(30000);
    // Flush multiple microtask cycles for async chain: schedulePing → executePing → sendPing → onPingFailure → triggerReconnect
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();

    expect(reconnectionManager.reconnectLoop).toHaveBeenCalled();
  });

  it('should stop timer on stop()', () => {
    manager.start();
    expect(jest.getTimerCount()).toBe(1);
    manager.stop();
    expect(jest.getTimerCount()).toBe(0);
  });
});
