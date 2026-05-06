/**
 * Local stream_write_file tool that writes directly to the local disk.
 * Runs on the bridge side (client machine) for local file operations.
 */

import * as fs from 'node:fs';
import * as path from 'node:path';

export interface StreamWriteArgs {
  file_path: string;
  content: string;
  mode?: 'write' | 'append';
  encoding?: BufferEncoding;
}

export interface StreamWriteResult {
  file_path: string;
  bytes_written: number;
  mode: string;
}

export function handleStreamWrite(args: StreamWriteArgs): StreamWriteResult {
  const filePath = args.file_path;
  const content = args.content;
  const mode = args.mode ?? 'write';
  const encoding = args.encoding ?? 'utf-8';

  // Ensure parent directory exists
  const dir = path.dirname(filePath);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }

  // Write or append
  if (mode === 'append') {
    fs.appendFileSync(filePath, content, { encoding });
  } else {
    fs.writeFileSync(filePath, content, { encoding });
  }

  const stats = fs.statSync(filePath);
  return {
    file_path: filePath,
    bytes_written: stats.size,
    mode,
  };
}
