import { UrlManager } from './url-manager';

describe('UrlManager', () => {
  it('should throw on empty URL list', () => {
    expect(() => new UrlManager([])).toThrow('No valid URLs configured');
  });

  it('should return first URL as active by default', () => {
    const mgr = new UrlManager(['http://a:8080', 'http://b:8080']);
    expect(mgr.activeUrl).toBe('http://a:8080');
    expect(mgr.urlIndex).toBe(0);
    expect(mgr.urlCount).toBe(2);
  });

  it('should advance to next URL with wrap-around', () => {
    const mgr = new UrlManager(['http://a:8080', 'http://b:8080']);
    expect(mgr.advance()).toBe('http://b:8080');
    expect(mgr.urlIndex).toBe(1);
    expect(mgr.advance()).toBe('http://a:8080');
    expect(mgr.urlIndex).toBe(0);
  });

  it('should track errors and hasNext correctly', () => {
    const mgr = new UrlManager(['http://a:8080', 'http://b:8080']);
    expect(mgr.hasNext()).toBe(true);
    mgr.markFailed('http://a:8080', 'timeout');
    expect(mgr.hasNext()).toBe(true);
    mgr.markFailed('http://b:8080', 'refused');
    expect(mgr.hasNext()).toBe(false);
  });

  it('should return errors list', () => {
    const mgr = new UrlManager(['http://a:8080']);
    mgr.markFailed('http://a:8080', 'timeout');
    const errors = mgr.getErrors();
    expect(errors).toHaveLength(1);
    expect(errors[0].url).toBe('http://a:8080');
    expect(errors[0].error).toBe('timeout');
    expect(errors[0].timestamp).toBeGreaterThan(0);
  });

  it('should clear errors', () => {
    const mgr = new UrlManager(['http://a:8080']);
    mgr.markFailed('http://a:8080', 'err');
    mgr.clearErrors();
    expect(mgr.getErrors()).toHaveLength(0);
    expect(mgr.hasNext()).toBe(true);
  });

  it('should reset to first URL and clear errors', () => {
    const mgr = new UrlManager(['http://a:8080', 'http://b:8080']);
    mgr.advance();
    mgr.markFailed('http://b:8080', 'err');
    mgr.reset();
    expect(mgr.urlIndex).toBe(0);
    expect(mgr.activeUrl).toBe('http://a:8080');
    expect(mgr.getErrors()).toHaveLength(0);
  });

  it('should work with single URL (backward compat)', () => {
    const mgr = new UrlManager(['http://localhost:8080']);
    expect(mgr.urlCount).toBe(1);
    expect(mgr.activeUrl).toBe('http://localhost:8080');
    expect(mgr.advance()).toBe('http://localhost:8080');
  });
});
