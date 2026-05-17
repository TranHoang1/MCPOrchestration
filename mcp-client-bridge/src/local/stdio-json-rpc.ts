/**
 * JSON-RPC communication over stdio pipes.
 * Handles message framing, request/response matching, and timeouts.
 */

import { ChildProcess } from 'node:child_process';

interface PendingRequest {
  resolve: (v: unknown) => void;
  reject: (e: Error) => void;
  timer: ReturnType<typeof setTimeout>;
}

export class StdioJsonRpc {
  private requestId = 0;
  private pendingRequests = new Map<number, PendingRequest>();
  private buffer = '';
  private process: ChildProcess | null = null;

  /** Attach to a child process's stdio streams. */
  attach(proc: ChildProcess): void {
    this.process = proc;
    proc.stdout?.on('data', (chunk: Buffer) => this.onData(chunk));
  }

  /** Detach and reject all pending requests. */
  detach(): void {
    this.rejectAll('Process detached');
    this.process = null;
    this.buffer = '';
  }

  /** Send a JSON-RPC request and wait for response. */
  sendRequest(method: string, params?: unknown, timeoutMs = 30_000): Promise<unknown> {
    return new Promise((resolve, reject) => {
      if (!this.process?.stdin?.writable) {
        reject(new Error('stdin not writable'));
        return;
      }
      const id = ++this.requestId;
      const msg = JSON.stringify({
        jsonrpc: '2.0',
        id,
        method,
        ...(params !== undefined && { params }),
      });

      const timer = setTimeout(() => {
        this.pendingRequests.delete(id);
        reject(new Error(`Request '${method}' timed out (${timeoutMs}ms)`));
      }, timeoutMs);

      this.pendingRequests.set(id, { resolve, reject, timer });
      this.process.stdin.write(msg + '\n');
    });
  }

  /** Send a JSON-RPC notification (no response expected). */
  sendNotification(method: string, params?: unknown): void {
    if (!this.process?.stdin?.writable) return;
    const msg = JSON.stringify({
      jsonrpc: '2.0',
      method,
      ...(params !== undefined && { params }),
    });
    this.process.stdin.write(msg + '\n');
  }

  /** Reject all pending requests with an error message. */
  rejectAll(reason: string): void {
    for (const [id, pending] of this.pendingRequests) {
      clearTimeout(pending.timer);
      pending.reject(new Error(reason));
      this.pendingRequests.delete(id);
    }
  }

  private onData(chunk: Buffer): void {
    this.buffer += chunk.toString();
    const lines = this.buffer.split('\n');
    this.buffer = lines.pop() ?? '';

    for (const line of lines) {
      if (!line.trim()) continue;
      try {
        const msg = JSON.parse(line);
        this.handleMessage(msg);
      } catch {
        // Non-JSON output, ignore
      }
    }
  }

  private handleMessage(msg: { id?: number; result?: unknown; error?: unknown }): void {
    if (msg.id == null) return; // Notification, ignore
    const pending = this.pendingRequests.get(msg.id);
    if (!pending) return;

    this.pendingRequests.delete(msg.id);
    clearTimeout(pending.timer);

    if (msg.error) {
      pending.reject(new Error(JSON.stringify(msg.error)));
    } else {
      pending.resolve(msg.result);
    }
  }
}
