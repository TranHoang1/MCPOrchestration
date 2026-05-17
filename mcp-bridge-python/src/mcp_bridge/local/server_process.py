"""Single local MCP server process with state machine lifecycle."""

from __future__ import annotations

import asyncio
import os
import signal
import sys
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Callable

from .stdio_json_rpc import StdioJsonRpc


class ServerState(Enum):
    """Lifecycle states for a local MCP server process."""
    STARTING = "STARTING"
    READY = "READY"
    ACTIVE = "ACTIVE"
    CRASHED = "CRASHED"
    RESTARTING = "RESTARTING"
    STOPPING = "STOPPING"
    DEAD = "DEAD"
    FAILED = "FAILED"


@dataclass
class ServerConfig:
    """Configuration for a single local MCP server."""
    command: str
    args: list[str] = field(default_factory=list)
    env: dict[str, str] = field(default_factory=dict)
    timeout: int = 30_000
    max_retries: int = 3
    disabled: bool = False


@dataclass
class ToolDefinition:
    """MCP tool definition from a local server."""
    name: str
    description: str | None = None
    input_schema: dict[str, Any] | None = None


class ServerProcess:
    """Manages a single local MCP server process with state machine."""

    def __init__(self, name: str, config: ServerConfig) -> None:
        self.name = name
        self._config = config
        self._process: asyncio.subprocess.Process | None = None
        self._rpc = StdioJsonRpc()
        self._state = ServerState.STARTING
        self._retry_count = 0
        self._tools: list[ToolDefinition] = []
        self._on_crashed: Callable[[str], None] | None = None

    @property
    def state(self) -> ServerState:
        return self._state

    @property
    def tools(self) -> list[ToolDefinition]:
        return list(self._tools)

    def on_crashed(self, callback: Callable[[str], None]) -> None:
        """Register a callback for crash events."""
        self._on_crashed = callback

    async def start(self) -> bool:
        """Spawn process, initialize MCP, and fetch tools."""
        self._state = ServerState.STARTING
        try:
            await self._spawn_process()
            if not await self._initialize():
                self._state = ServerState.FAILED
                return False
            self._state = ServerState.READY
            if await self._fetch_tools():
                self._state = ServerState.ACTIVE
            return True
        except Exception as e:
            self._log(f"Start failed: {e}")
            self._state = ServerState.FAILED
            return False

    async def stop(self) -> None:
        """Gracefully stop the server process."""
        self._state = ServerState.STOPPING
        await self._kill_process()
        self._tools = []

    async def restart(self) -> bool:
        """Restart with exponential backoff. Returns False if max retries exceeded."""
        if self._retry_count >= self._config.max_retries:
            self._state = ServerState.DEAD
            self._log(f"Max retries ({self._config.max_retries}) exceeded → DEAD")
            return False
        self._state = ServerState.RESTARTING
        delay = min(1.0 * (2 ** self._retry_count), 30.0)
        self._retry_count += 1
        self._log(f"Restart #{self._retry_count} in {delay:.0f}s")
        await asyncio.sleep(delay)
        return await self.start()

    async def call_tool(self, tool_name: str, args: dict[str, Any]) -> Any:
        """Call a tool on this server via JSON-RPC."""
        return await self._rpc.send_request("tools/call", {"name": tool_name, "arguments": args})

    async def health_check(self) -> bool:
        """Ping the server with tools/list to verify it's alive."""
        try:
            await self._rpc.send_request("tools/list", {}, timeout_ms=5000)
            return True
        except Exception:
            return False

    async def _spawn_process(self) -> None:
        """Spawn the subprocess with stdio pipes."""
        env = {**os.environ, **self._config.env}
        self._process = await asyncio.create_subprocess_exec(
            self._config.command, *self._config.args,
            stdin=asyncio.subprocess.PIPE,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
            env=env,
        )
        assert self._process.stdin and self._process.stdout
        self._rpc.attach(self._process.stdin, self._process.stdout)
        asyncio.create_task(self._monitor_stderr())
        asyncio.create_task(self._monitor_exit())

    async def _initialize(self) -> bool:
        """Send MCP initialize handshake."""
        try:
            result = await self._rpc.send_request("initialize", {
                "protocolVersion": "2025-03-26",
                "capabilities": {},
                "clientInfo": {"name": "mcp-bridge-python", "version": "1.0.0"},
            }, timeout_ms=self._config.timeout)
            self._rpc.send_notification("notifications/initialized", {})
            return result is not None
        except Exception as e:
            self._log(f"Initialize error: {e}")
            return False

    async def _fetch_tools(self) -> bool:
        """Fetch tool definitions from the server."""
        try:
            result = await self._rpc.send_request("tools/list", {})
            raw_tools = result.get("tools", []) if isinstance(result, dict) else []
            self._tools = [
                ToolDefinition(
                    name=t["name"],
                    description=t.get("description"),
                    input_schema=t.get("inputSchema"),
                )
                for t in raw_tools
            ]
            self._log(f"Discovered {len(self._tools)} tools")
            return True
        except Exception:
            return False

    async def _monitor_stderr(self) -> None:
        """Read stderr for logging."""
        assert self._process and self._process.stderr
        try:
            async for line in self._process.stderr:
                text = line.decode().strip()
                if text:
                    self._log(f"[stderr] {text}")
        except Exception:
            pass

    async def _monitor_exit(self) -> None:
        """Monitor process exit and trigger crash handling."""
        assert self._process
        await self._process.wait()
        if self._state == ServerState.STOPPING:
            return
        self._log(f"Process exited with code {self._process.returncode}")
        self._handle_crash()

    def _handle_crash(self) -> None:
        """Handle unexpected process termination."""
        self._rpc.reject_all("Process terminated")
        if self._state in (ServerState.STOPPING, ServerState.DEAD):
            return
        self._state = ServerState.CRASHED
        if self._on_crashed:
            self._on_crashed(self.name)

    async def _kill_process(self) -> None:
        """Gracefully terminate, then force kill after timeout."""
        if not self._process:
            return
        proc = self._process
        self._process = None
        self._rpc.detach()

        try:
            proc.terminate()
            await asyncio.wait_for(proc.wait(), timeout=5.0)
        except asyncio.TimeoutError:
            self._log("Force killing (SIGKILL)")
            proc.kill()
            await proc.wait()

    def _log(self, msg: str) -> None:
        print(f"[local:{self.name}] {msg}", file=sys.stderr, flush=True)
