import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';
import { handleStreamWrite } from './local-stream-write';

describe('handleStreamWrite', () => {
  let tempDir: string;

  beforeEach(() => {
    tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-test-'));
  });

  afterEach(() => {
    fs.rmSync(tempDir, { recursive: true, force: true });
  });

  it('should write content to a new file', () => {
    const filePath = path.join(tempDir, 'test.txt');
    const result = handleStreamWrite({ file_path: filePath, content: 'hello world' });

    expect(result.file_path).toBe(filePath);
    expect(result.mode).toBe('write');
    expect(fs.readFileSync(filePath, 'utf-8')).toBe('hello world');
  });

  it('should append content to existing file', () => {
    const filePath = path.join(tempDir, 'append.txt');
    fs.writeFileSync(filePath, 'line1\n');

    handleStreamWrite({ file_path: filePath, content: 'line2\n', mode: 'append' });

    expect(fs.readFileSync(filePath, 'utf-8')).toBe('line1\nline2\n');
  });

  it('should create parent directories', () => {
    const filePath = path.join(tempDir, 'a', 'b', 'c', 'deep.txt');
    const result = handleStreamWrite({ file_path: filePath, content: 'deep' });

    expect(result.bytes_written).toBeGreaterThan(0);
    expect(fs.readFileSync(filePath, 'utf-8')).toBe('deep');
  });

  it('should overwrite existing file in write mode', () => {
    const filePath = path.join(tempDir, 'overwrite.txt');
    fs.writeFileSync(filePath, 'old content');

    handleStreamWrite({ file_path: filePath, content: 'new content' });

    expect(fs.readFileSync(filePath, 'utf-8')).toBe('new content');
  });

  it('should return bytes_written', () => {
    const filePath = path.join(tempDir, 'size.txt');
    const result = handleStreamWrite({ file_path: filePath, content: 'hello' });

    expect(result.bytes_written).toBe(5);
  });
});
