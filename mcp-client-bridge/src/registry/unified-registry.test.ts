import { UnifiedRegistry } from './unified-registry';
import { ToolDefinition } from '../local/server-process';

describe('UnifiedRegistry', () => {
  beforeEach(() => {
    jest.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('should start empty', () => {
    const registry = new UnifiedRegistry();
    expect(registry.getAll()).toEqual([]);
    expect(registry.totalCount).toBe(0);
  });

  it('should merge local and remote tools without conflicts', () => {
    const registry = new UnifiedRegistry();
    const local: ToolDefinition[] = [
      { name: 'read_file', description: 'Read a file' },
    ];
    const remote: ToolDefinition[] = [
      { name: 'jira_search', description: 'Search Jira' },
    ];

    registry.setLocalTools(local);
    registry.setRemoteTools(remote);

    expect(registry.totalCount).toBe(2);
    expect(registry.has('read_file')).toBe(true);
    expect(registry.has('jira_search')).toBe(true);
  });

  it('should resolve conflicts with local-first (default)', () => {
    const registry = new UnifiedRegistry('local-first');
    const local: ToolDefinition[] = [
      { name: 'embed', description: 'Local embed' },
    ];
    const remote: ToolDefinition[] = [
      { name: 'embed', description: 'Remote embed' },
    ];

    registry.setLocalTools(local);
    registry.setRemoteTools(remote);

    expect(registry.totalCount).toBe(1);
    const tool = registry.find('embed');
    expect(tool?.source).toBe('local');
    expect(tool?.description).toBe('Local embed');
  });

  it('should resolve conflicts with remote-first', () => {
    const registry = new UnifiedRegistry('remote-first');
    const local: ToolDefinition[] = [
      { name: 'embed', description: 'Local embed' },
    ];
    const remote: ToolDefinition[] = [
      { name: 'embed', description: 'Remote embed' },
    ];

    registry.setLocalTools(local);
    registry.setRemoteTools(remote);

    expect(registry.totalCount).toBe(1);
    const tool = registry.find('embed');
    expect(tool?.source).toBe('remote');
    expect(tool?.description).toBe('Remote embed');
  });

  it('should update counts correctly', () => {
    const registry = new UnifiedRegistry();
    registry.setLocalTools([
      { name: 'tool_a' },
      { name: 'tool_b' },
    ]);
    registry.setRemoteTools([
      { name: 'tool_c' },
    ]);

    expect(registry.localCount).toBe(2);
    expect(registry.remoteCount).toBe(1);
    expect(registry.totalCount).toBe(3);
  });

  it('should return tool definitions without source metadata', () => {
    const registry = new UnifiedRegistry();
    registry.setLocalTools([
      { name: 'tool_a', description: 'A tool', inputSchema: { type: 'object' } },
    ]);

    const defs = registry.getToolDefinitions();
    expect(defs).toHaveLength(1);
    expect(defs[0]).toEqual({
      name: 'tool_a',
      description: 'A tool',
      inputSchema: { type: 'object' },
    });
    // Should not have 'source' field
    expect((defs[0] as unknown as Record<string, unknown>).source).toBeUndefined();
  });

  it('should refresh with new tools', () => {
    const registry = new UnifiedRegistry();
    registry.setLocalTools([{ name: 'old_tool' }]);
    expect(registry.has('old_tool')).toBe(true);

    registry.refresh([{ name: 'new_tool' }], []);
    expect(registry.has('old_tool')).toBe(false);
    expect(registry.has('new_tool')).toBe(true);
  });
});
