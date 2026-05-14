import { BridgeConfig } from './bridge-config';

describe('BridgeConfig', () => {
  const originalEnv = process.env;

  beforeEach(() => {
    process.env = { ...originalEnv };
    delete process.env.ORCHESTRATOR_URL;
    delete process.env.ORCHESTRATOR_URLS;
  });

  afterAll(() => {
    process.env = originalEnv;
  });

  it('should parse single URL from --url arg', () => {
    const config = BridgeConfig.load(['--url', 'http://server:9090']);
    expect(config.orchestratorUrls).toEqual(['http://server:9090']);
    expect(config.orchestratorUrl).toBe('http://server:9090');
  });

  it('should parse comma-separated URLs from --url arg', () => {
    const config = BridgeConfig.load(['--url', 'http://a:8080,http://b:8080,http://c:8080']);
    expect(config.orchestratorUrls).toEqual(['http://a:8080', 'http://b:8080', 'http://c:8080']);
    expect(config.orchestratorUrl).toBe('http://a:8080');
  });

  it('should trim whitespace from URLs', () => {
    const config = BridgeConfig.load(['--url', ' http://a:8080 , http://b:8080 ']);
    expect(config.orchestratorUrls).toEqual(['http://a:8080', 'http://b:8080']);
  });

  it('should filter invalid URLs (non-http)', () => {
    const config = BridgeConfig.load(['--url', 'http://valid:8080,ftp://invalid,https://also-valid']);
    expect(config.orchestratorUrls).toEqual(['http://valid:8080', 'https://also-valid']);
  });

  it('should read ORCHESTRATOR_URLS env when no --url arg', () => {
    process.env.ORCHESTRATOR_URLS = 'http://env1:8080,http://env2:8080';
    const config = BridgeConfig.load([]);
    expect(config.orchestratorUrls).toEqual(['http://env1:8080', 'http://env2:8080']);
  });

  it('should fall back to ORCHESTRATOR_URL env (singular)', () => {
    process.env.ORCHESTRATOR_URL = 'http://single:8080';
    const config = BridgeConfig.load([]);
    expect(config.orchestratorUrls).toEqual(['http://single:8080']);
  });

  it('should default to localhost when no config', () => {
    const config = BridgeConfig.load([]);
    expect(config.orchestratorUrls).toEqual(['http://localhost:8080']);
  });

  it('should set new config fields with defaults', () => {
    const config = BridgeConfig.load([]);
    expect(config.connectionTimeoutMs).toBe(5_000);
    expect(config.maxRetryBeforeRotate).toBe(3);
  });

  it('should truncate URLs to max 10', () => {
    const urls = Array.from({ length: 15 }, (_, i) => `http://s${i}:8080`);
    const config = BridgeConfig.load(['--url', urls.join(',')]);
    expect(config.orchestratorUrls).toHaveLength(10);
  });
});
