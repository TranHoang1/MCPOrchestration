/**
 * Local stream_write_file tool that writes directly to the local disk.
 * Runs on the bridge side (client machine) for local file operations.
 */

import * as fs from 'node:fs';
import * as path from 'node:path';

/**
 * Workspace root resolved from MCP roots/list or fallback.
 * Set after server connects and queries client for roots.
 */
let WORKSPACE_ROOT = process.cwd();

export function setWorkspaceRoot(root: string): void {
  WORKSPACE_ROOT = root;
}

export function getWorkspaceRoot(): string {
  return WORKSPACE_ROOT;
}

export interface StreamWriteArgs {
  file_path: string;
  content?: string;
  mode?: 'write' | 'append' | 'create';
  encoding?: BufferEncoding;
}

export interface StreamWriteResult {
  file_path: string;
  bytes_written: number;
  total_size: number;
  file_size_before: number;
  mode: string;
}

export function handleStreamWrite(args: StreamWriteArgs): StreamWriteResult {
  const rawPath = args.file_path;
  const mode = args.mode ?? 'write';
  const content = mode === 'create' ? (args.content ?? '') : args.content;
  const encoding = args.encoding ?? 'utf-8';

  if (mode !== 'create' && content == null) {
    throw new Error("'content' is required for mode 'write' or 'append'");
  }

  // Resolve relative paths against workspace root
  const filePath = path.isAbsolute(rawPath) ? rawPath : path.resolve(getWorkspaceRoot(), rawPath);

  // Ensure parent directory exists
  const dir = path.dirname(filePath);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }

  // Get file size before write
  const fileSizeBefore = fs.existsSync(filePath) ? fs.statSync(filePath).size : 0;

  // Create, write, or append
  if (mode === 'create') {
    if (fs.existsSync(filePath)) {
      throw new Error(`File already exists: ${filePath}. Use mode='write' to overwrite or mode='append' to add content.`);
    }
    fs.writeFileSync(filePath, content ?? '', { encoding });
  } else if (mode === 'append') {
    fs.appendFileSync(filePath, content ?? '', { encoding });
  } else {
    fs.writeFileSync(filePath, content ?? '', { encoding });
  }

  const stats = fs.statSync(filePath);
  return {
    file_path: filePath,
    bytes_written: stats.size - fileSizeBefore,
    total_size: stats.size,
    file_size_before: fileSizeBefore,
    mode,
  };
}
