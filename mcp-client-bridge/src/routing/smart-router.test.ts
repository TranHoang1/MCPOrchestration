import { SmartRouter } from './smart-router';
import { RoutingTable } from './routing-table';
import { LocalServerManager } from '../local/local-server-manager';
import { HttpStreamableClient } from '../http-streamable-client';
import { BridgeConfig } from '../bridge-config';

describe('SmartRouter', () => {
  let routingTable: RoutingTable;
  let localManager: LocalServerManager;
  let httpClient: HttpStreamableClient;
  let router: SmartRouter;
  let config: BridgeConfig;

  beforeEach(() => {
    jest.spyOn(console, 'error').mockImplementation(() => {});
    config = {
      orchestratorUrls: ['http://localhost:8080'],
      orchestratorUrl: 'http://localhost:8080',
      reconnectEnabled: true,
      maxReconnectDelayMs: 15000,
      baseReconnectDelayMs: 1000,
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

    routingTable = new RoutingTable(config);
    localManager = {
      callTool: jest.fn(),
      findServerForTool: jest.fn(),
      getAllTools: jest.fn().mockReturnValue([]),
    } as unknown as LocalServerManager;
    httpClient = {
      callTool: jest.fn(),
    } as unknown as HttpStreamableClient;

    router = new SmartRouter(routingTable, localManager, httpClient);
  });

  it('should route local tool via routing table', async () => {
    routingTable.setFromMeta({
      routingTable: {
        version: '1.0.0',
        updatedAt: '',
        defaultLocation: 'remote',
        tools: { read_file: { location: 'local', server: 'fs-server' } },
      },
    });

    (localManager.callTool as jest.Mock).mockResolvedValue({
      content: [{ type: 'text', text: 'file content' }],
    });

    const result = await router.route('read_file', { path: '/test' });

    expect(localManager.callTool).toHaveBeenCalledWith('fs-server', 'read_file', { path: '/test' });
    expect(result.content[0].text).toBe('file content');
  });

  it('should route remote tool via routing table', async () => {
    routingTable.setFromMeta({
      routingTable: {
        version: '1.0.0',
        updatedAt: '',
        defaultLocation: 'remote',
        tools: { jira_search: { location: 'remote', server: 'atlassian' } },
      },
    });

    (httpClient.callTool as jest.Mock).mockResolvedValue({
      content: [{ type: 'text', text: '{"results":[]}' }],
    });

    const result = await router.route('jira_search', { query: 'test' });

    expect(httpClient.callTool).toHaveBeenCalledWith('jira_search', { query: 'test' });
    expect(result.content[0].text).toBe('{"results":[]}');
  });

  it('should fallback to remote when local fails', async () => {
    routingTable.setFromMeta({
      routingTable: {
        version: '1.0.0',
        updatedAt: '',
        defaultLocation: 'remote',
        tools: { embed: { location: 'local', server: 'embed-srv', fallback: 'remote' } },
      },
    });

    (localManager.callTool as jest.Mock).mockRejectedValue(new Error('Server down'));
    (httpClient.callTool as jest.Mock).mockResolvedValue({
      content: [{ type: 'text', text: 'remote result' }],
    });

    const result = await router.route('embed', { text: 'hello' });

    expect(result.content[0].text).toBe('remote result');
  });

  it('should use defaultLocation for unknown tools', async () => {
    routingTable.setFromMeta({
      routingTable: {
        version: '1.0.0',
        updatedAt: '',
        defaultLocation: 'remote',
        tools: {},
      },
    });

    (localManager.findServerForTool as jest.Mock).mockReturnValue(null);
    (httpClient.callTool as jest.Mock).mockResolvedValue({
      content: [{ type: 'text', text: 'ok' }],
    });

    const result = await router.route('unknown_tool', {});
    expect(httpClient.callTool).toHaveBeenCalledWith('unknown_tool', {});
    expect(result.content[0].text).toBe('ok');
  });

  it('should find tool in local servers when not in routing table', async () => {
    (localManager.findServerForTool as jest.Mock).mockReturnValue('my-server');
    (localManager.callTool as jest.Mock).mockResolvedValue({
      content: [{ type: 'text', text: 'local result' }],
    });

    const result = await router.route('local_only_tool', { arg: 1 });

    expect(localManager.callTool).toHaveBeenCalledWith('my-server', 'local_only_tool', { arg: 1 });
    expect(result.content[0].text).toBe('local result');
  });

  it('should collect metrics per tool', async () => {
    (localManager.findServerForTool as jest.Mock).mockReturnValue('srv');
    (localManager.callTool as jest.Mock).mockResolvedValue({
      content: [{ type: 'text', text: 'ok' }],
    });

    await router.route('tool_a', {});
    await router.route('tool_a', {});

    const metrics = router.getToolMetrics('tool_a');
    expect(metrics).toBeDefined();
    expect(metrics!.callCount).toBe(2);
    expect(metrics!.errorCount).toBe(0);
    expect(metrics!.lastCallAt).toBeGreaterThan(0);
  });

  it('should record error metrics on failure', async () => {
    (localManager.findServerForTool as jest.Mock).mockReturnValue(null);
    (httpClient.callTool as jest.Mock).mockRejectedValue(new Error('fail'));

    // Default location is remote (empty routing table)
    try {
      await router.route('failing_tool', {});
    } catch {
      // expected
    }

    const metrics = router.getToolMetrics('failing_tool');
    expect(metrics).toBeDefined();
    expect(metrics!.errorCount).toBe(1);
  });
});
