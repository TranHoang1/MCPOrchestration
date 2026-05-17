"""JSON-RPC communication over stdio pipes with asyncio."""

from __future__ import annotations

import asyncio
import json
import sys
from typing import Any


class StdioJsonRpc:
    """Handles JSON-RPC message framing, request/response matching, and timeouts."""

    def __init__(self) -> None:
        self._request_id = 0
        self._pending: dict[int, asyncio.Future[Any]] = {}
        self._reader_task: asyncio.Task[None] | None = None
        self._stdin: asyncio.StreamWriter | None = None
        self._stdout: asyncio.StreamReader | None = None

    def attach(self, stdin: asyncio.StreamWriter, stdout: asyncio.StreamReader) -> None:
        """Attach to a subprocess's stdio streams and start reading."""
        self._stdin = stdin
        self._stdout = stdout
        self._reader_task = asyncio.create_task(self._read_loop())

    def detach(self) -> None:
        """Detach and reject all pending requests."""
        if self._reader_task and not self._reader_task.done():
            self._reader_task.cancel()
        self._reader_task = None
        self._stdin = None
        self._stdout = None
        self.reject_all("Process detached")

    async def send_request(
        self, method: str, params: Any = None, timeout_ms: int = 30_000
    ) -> Any:
        """Send a JSON-RPC request and wait for response."""
        if not self._stdin:
            raise RuntimeError("stdin not attached")

        self._request_id += 1
        req_id = self._request_id
        msg: dict[str, Any] = {"jsonrpc": "2.0", "id": req_id, "method": method}
        if params is not None:
            msg["params"] = params

        future: asyncio.Future[Any] = asyncio.get_event_loop().create_future()
        self._pending[req_id] = future

        data = json.dumps(msg) + "\n"
        self._stdin.write(data.encode())
        await self._stdin.drain()

        try:
            return await asyncio.wait_for(future, timeout=timeout_ms / 1000)
        except asyncio.TimeoutError:
            self._pending.pop(req_id, None)
            raise TimeoutError(f"Request '{method}' timed out ({timeout_ms}ms)")

    def send_notification(self, method: str, params: Any = None) -> None:
        """Send a JSON-RPC notification (no response expected)."""
        if not self._stdin:
            return
        msg: dict[str, Any] = {"jsonrpc": "2.0", "method": method}
        if params is not None:
            msg["params"] = params
        data = json.dumps(msg) + "\n"
        self._stdin.write(data.encode())

    def reject_all(self, reason: str) -> None:
        """Reject all pending requests with an error."""
        for future in self._pending.values():
            if not future.done():
                future.set_exception(RuntimeError(reason))
        self._pending.clear()

    async def _read_loop(self) -> None:
        """Read lines from stdout and dispatch responses."""
        assert self._stdout is not None
        try:
            while True:
                line = await self._stdout.readline()
                if not line:
                    break
                self._handle_line(line.decode().strip())
        except asyncio.CancelledError:
            pass
        except Exception as e:
            self._log(f"Read loop error: {e}")

    def _handle_line(self, line: str) -> None:
        """Parse a JSON-RPC response line and resolve the pending future."""
        if not line:
            return
        try:
            msg = json.loads(line)
        except json.JSONDecodeError:
            return

        req_id = msg.get("id")
        if req_id is None:
            return

        future = self._pending.pop(req_id, None)
        if not future or future.done():
            return

        if "error" in msg:
            future.set_exception(RuntimeError(json.dumps(msg["error"])))
        else:
            future.set_result(msg.get("result"))

    @staticmethod
    def _log(msg: str) -> None:
        print(f"[stdio-rpc] {msg}", file=sys.stderr, flush=True)
