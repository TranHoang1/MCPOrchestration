import { RoutingTable, RoutingTableData } from './routing-table';
import { BridgeConfig } from '../bridge-config';

// Mock global fetch
const mockFetch = jest.fn();
global.fetch = mockFetch as unknown as typeof fetch;

describe('RoutingTable', () => {
  let config: BridgeConfig;

  beforeEach(() => {
    jest.spyOn(console, 'error').mockImplementation(() => {});
    mockFetch.mockReset();
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
      token: 'test.jwt.token',
    };
  });

  it('should start with empty routing table', () => {
    const rt = new RoutingTable(config);
    expect(rt.getCached().tools).toEqual({});
    expect(rt.defaultLocation).toBe('remote');
  });

  it('should resolve tool from cached table', () => {
    const rt = new RoutingTable(config);
    rt.setFromMeta({
      routingTable: {
        version: '1.0.0',
        updatedAt: '2026-01-01',
        defaultLocation: 'remote',
        tools: {
          read_file: { location: 'local', server: 'fs-server' },
          jira_search: { location: 'remote', server: 'atlassian' },
        },
      },
    });

    expect(rt.resolve('read_file')).toEqual({ location: 'local', server: 'fs-server' });
    expect(rt.resolve('jira_search')).toEqual({ location: 'remote', server: 'atlassian' });
    expect(rt.resolve('unknown_tool')).toBeNull();
  });

  it('should set routing table from _meta', () => {
    const rt = new RoutingTable(config);
    const meta = {
      routingTable: {
        version: '2.0.0',
        updatedAt: '2026-07-01',
        defaultLocation: 'local' as const,
        tools: { embed: { location: 'local' as const, fallback: 'remote' as const } },
      },
    };
    rt.setFromMeta(meta);

    expect(rt.getCached().version).toBe('2.0.0');
    expect(rt.defaultLocation).toBe('local');
  });

  it('should fetch routing table with ETag caching', async () => {
    const tableData: RoutingTableData = {
      version: '1.0.0',
      updatedAt: '2026-07-01',
      defaultLocation: 'remote',
      tools: { tool_a: { location: 'local', server: 'srv' } },
    };

    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Map([['etag', '"abc123"']]) as unknown as Headers,
      json: () => Promise.resolve(tableData),
    });

    const rt = new RoutingTable(config);
    const result = await rt.fetch();

    expect(result).toBe(true);
    expect(rt.resolve('tool_a')).toEqual({ location: 'local', server: 'srv' });
    expect(mockFetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/routing-table',
      expect.objectContaining({
        method: 'GET',
        headers: expect.objectContaining({ Authorization: 'Bearer test.jwt.token' }),
      }),
    );
  });

  it('should handle 304 Not Modified', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 304 });

    const rt = new RoutingTable(config);
    const result = await rt.fetch();

    expect(result).toBe(true);
  });

  it('should keep cache on fetch error', async () => {
    const rt = new RoutingTable(config);
    rt.setFromMeta({
      routingTable: {
        version: '1.0.0',
        updatedAt: '',
        defaultLocation: 'local',
        tools: { cached_tool: { location: 'local' } },
      },
    });

    mockFetch.mockRejectedValueOnce(new Error('Network error'));
    const result = await rt.fetch();

    expect(result).toBe(false);
    expect(rt.resolve('cached_tool')).toEqual({ location: 'local' });
  });

  it('should keep cache on malformed response', async () => {
    const rt = new RoutingTable(config);
    rt.setFromMeta({
      routingTable: {
        version: '1.0.0',
        updatedAt: '',
        defaultLocation: 'remote',
        tools: { existing: { location: 'remote' } },
      },
    });

    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Map() as unknown as Headers,
      json: () => Promise.resolve({ invalid: 'data' }),
    });

    const result = await rt.fetch();
    expect(result).toBe(false);
    expect(rt.resolve('existing')).toEqual({ location: 'remote' });
  });
});
