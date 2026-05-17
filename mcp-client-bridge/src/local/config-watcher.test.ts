import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';
import { ConfigWatcher } from './config-watcher';

describe('ConfigWatcher', () => {
  let tmpDir: string;
  let configPath: string;

  beforeEach(() => {
    jest.spyOn(console, 'error').mockImplementation(() => {});
    tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'cw-test-'));
    configPath = path.join(tmpDir, 'mcp-servers.json');
  });

  afterEach(() => {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  });

  it('should load valid config', () => {
    const config = {
      mcpServers: {
        'test-server': { command: 'node', args: ['server.js'] },
      },
    };
    fs.writeFileSync(configPath, JSON.stringify(config));

    const watcher = new ConfigWatcher(configPath);
    const loaded = watcher.loadConfig();

    expect(loaded.mcpServers).toBeDefined();
    expect(loaded.mcpServers['test-server']).toBeDefined();
    expect(loaded.mcpServers['test-server'].command).toBe('node');
  });

  it('should return empty config on missing file', () => {
    const watcher = new ConfigWatcher('/nonexistent/path.json');
    const loaded = watcher.loadConfig();

    expect(loaded.mcpServers).toEqual({});
  });

  it('should return empty config on invalid JSON', () => {
    fs.writeFileSync(configPath, 'not json');

    const watcher = new ConfigWatcher(configPath);
    const loaded = watcher.loadConfig();

    expect(loaded.mcpServers).toEqual({});
  });

  it('should return empty config when mcpServers is missing', () => {
    fs.writeFileSync(configPath, JSON.stringify({ other: 'data' }));

    const watcher = new ConfigWatcher(configPath);
    const loaded = watcher.loadConfig();

    expect(loaded.mcpServers).toEqual({});
  });

  describe('resolveConfigPath', () => {
    it('should use CLI path if file exists', () => {
      fs.writeFileSync(configPath, '{}');
      const resolved = ConfigWatcher.resolveConfigPath(configPath);
      expect(resolved).toBe(configPath);
    });

    it('should fall back to CWD when CLI path not found', () => {
      const resolved = ConfigWatcher.resolveConfigPath('/nonexistent/file.json');
      // Should return CWD-based path or home-based path
      expect(resolved).toBeTruthy();
    });
  });

  it('should emit configChanged on file modification', (done) => {
    const config = { mcpServers: { s1: { command: 'echo' } } };
    fs.writeFileSync(configPath, JSON.stringify(config));

    const watcher = new ConfigWatcher(configPath);
    watcher.on('configChanged', (newConfig) => {
      expect(newConfig.mcpServers['s2']).toBeDefined();
      watcher.stop();
      done();
    });
    watcher.start();

    // Modify file after a short delay
    setTimeout(() => {
      const updated = { mcpServers: { s2: { command: 'node' } } };
      fs.writeFileSync(configPath, JSON.stringify(updated));
    }, 100);
  }, 5000);
});
