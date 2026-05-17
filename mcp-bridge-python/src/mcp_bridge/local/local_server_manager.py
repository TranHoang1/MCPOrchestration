"""Manages all local MCP server processes — spawn, health, restart, config reload."""

from __future__ import annotations

import asyncio
import sys
from typing import Any, Callable

from .config_watcher import ConfigWatcher, load_config
from .server_process import ServerConfig, ServerProcess, ServerState, ToolDefinition


class LocalServerManager:
    """Orchestrates multiple local MCP server processes."""

    def __init__(self, config_path: str, health_interval_ms: int = 30_000) -> None:
        self._servers: dict[str, ServerProcess] = {}
        self._health_interval = health_interval_ms / 1000
        self._health_task: asyncio.Task[None] | None = None
        self._config_path = config_path
        self._watcher = ConfigWatcher(config_path, on_change=self._handle_config_change)
        self._on_tools_changed: Callable[[], Any] | None = None

    def on_tools_changed(self, callback: Callable[[], Any]) -> None:
        """Register callback for when tool list changes."""
        self._on_tools_changed = callback

    @property
    def active_count(self) -> int:
        return sum(1 for s in self._servers.values() if s.state == ServerState.ACTIVE)

    async def start_all(self) -> None:
        """Start all configured servers and begin health monitoring."""
        configs = load_config(self._config_path)
        entries = [(n, c) for n, c in configs.items() if not c.disabled]
        self._log(f"Starting {len(entries)} local server(s)...")

        tasks = [self._start_server(name, cfg) for name, cfg in entries]
        await asyncio.gather(*tasks, return_exceptions=True)

        self._watcher.start()
        self._start_health_monitor()
        self._log(f"{self.active_count}/{len(entries)} servers active")

    async def stop_all(self) -> None:
        """Stop all servers and cleanup."""
        self._stop_health_monitor()
        self._watcher.stop()
        tasks = [s.stop() for s in self._servers.values()]
        await asyncio.gather(*tasks, return_exceptions=True)
        self._servers.clear()

    async def call_tool(self, server_name: str, tool_name: str, args: dict[str, Any]) -> Any:
        """Call a tool on a specific local server."""
        server = self._servers.get(server_name)
        if not server or server.state != ServerState.ACTIVE:
            state = server.state.value if server else "NOT_FOUND"
            raise RuntimeError(f"Server '{server_name}' not available (state: {state})")
        return await server.call_tool(tool_name, args)

    def get_all_tools(self) -> list[ToolDefinition]:
        """Get all tools from all active servers."""
        tools: list[ToolDefinition] = []
        for server in self._servers.values():
            if server.state == ServerState.ACTIVE:
                tools.extend(server.tools)
        return tools

    def find_server_for_tool(self, tool_name: str) -> str | None:
        """Find which server owns a tool by name."""
        for name, server in self._servers.items():
            if server.state == ServerState.ACTIVE:
                if any(t.name == tool_name for t in server.tools):
                    return name
        return None

    async def _start_server(self, name: str, config: ServerConfig) -> None:
        """Start a single server and register crash handler."""
        server = ServerProcess(name, config)
        server.on_crashed(lambda n: asyncio.create_task(self._handle_crash(n)))
        self._servers[name] = server
        ok = await server.start()
        if not ok:
            self._log(f"Server '{name}' failed to start")

    async def _handle_crash(self, name: str) -> None:
        """Attempt restart on crash."""
        server = self._servers.get(name)
        if not server:
            return
        self._log(f"Server '{name}' crashed, attempting restart...")
        ok = await server.restart()
        if not ok:
            self._log(f"Server '{name}' is DEAD after max retries")
        self._notify_tools_changed()

    async def _handle_config_change(self, new_configs: dict[str, ServerConfig]) -> None:
        """Handle hot-reload of mcp-servers.json."""
        current = set(self._servers.keys())
        incoming = set(new_configs.keys())

        # Remove servers no longer in config
        for name in current - incoming:
            self._log(f"Removing server: {name}")
            await self._servers[name].stop()
            del self._servers[name]

        # Add new servers
        for name in incoming - current:
            cfg = new_configs[name]
            if not cfg.disabled:
                self._log(f"Adding server: {name}")
                await self._start_server(name, cfg)

        # Disable servers marked disabled
        for name in incoming & current:
            if new_configs[name].disabled:
                await self._servers[name].stop()
                del self._servers[name]

        self._notify_tools_changed()

    def _start_health_monitor(self) -> None:
        """Start periodic health checks."""
        if self._health_interval <= 0:
            return
        self._health_task = asyncio.create_task(self._health_loop())

    def _stop_health_monitor(self) -> None:
        """Stop health check loop."""
        if self._health_task and not self._health_task.done():
            self._health_task.cancel()
        self._health_task = None

    async def _health_loop(self) -> None:
        """Periodically check server health."""
        try:
            while True:
                await asyncio.sleep(self._health_interval)
                await self._run_health_checks()
        except asyncio.CancelledError:
            pass

    async def _run_health_checks(self) -> None:
        """Run health check on all active servers."""
        for name, server in list(self._servers.items()):
            if server.state != ServerState.ACTIVE:
                continue
            if not await server.health_check():
                # Double-check before declaring crash
                if not await server.health_check():
                    self._log(f"'{name}' confirmed unhealthy → restart")
                    await self._handle_crash(name)

    def _notify_tools_changed(self) -> None:
        """Notify listeners that tool list has changed."""
        if self._on_tools_changed:
            self._on_tools_changed()

    def _log(self, msg: str) -> None:
        print(f"[local-manager] {msg}", file=sys.stderr, flush=True)
