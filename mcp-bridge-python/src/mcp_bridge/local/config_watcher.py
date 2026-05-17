"""Watches mcp-servers.json for changes with polling-based debounce."""

from __future__ import annotations

import asyncio
import json
import os
import sys
from pathlib import Path
from typing import Any, Callable

from .server_process import ServerConfig


McpServersConfig = dict[str, ServerConfig]


def load_config(config_path: str) -> dict[str, ServerConfig]:
    """Load and parse mcp-servers.json. Returns empty dict on error."""
    try:
        raw = Path(config_path).read_text(encoding="utf-8")
        parsed = json.loads(raw)
        servers = parsed.get("mcpServers", {})
        if not isinstance(servers, dict):
            _log("Invalid config: mcpServers is not an object")
            return {}
        return {
            name: ServerConfig(
                command=cfg["command"],
                args=cfg.get("args", []),
                env=cfg.get("env", {}),
                timeout=cfg.get("timeout", 30_000),
                max_retries=cfg.get("maxRetries", 3),
                disabled=cfg.get("disabled", False),
            )
            for name, cfg in servers.items()
            if isinstance(cfg, dict) and "command" in cfg
        }
    except (OSError, json.JSONDecodeError, KeyError) as e:
        _log(f"Failed to load {config_path}: {e}")
        return {}


def resolve_config_path(cli_path: str | None = None) -> str:
    """Resolve config path: CLI arg → CWD → home directory."""
    if cli_path:
        abs_path = os.path.abspath(cli_path)
        if os.path.exists(abs_path):
            return abs_path

    cwd_path = os.path.join(os.getcwd(), "mcp-servers.json")
    if os.path.exists(cwd_path):
        return cwd_path

    home = os.environ.get("HOME") or os.environ.get("USERPROFILE") or "."
    home_path = os.path.join(home, ".mcp-bridge", "mcp-servers.json")
    if os.path.exists(home_path):
        return home_path

    return cwd_path


class ConfigWatcher:
    """Polls mcp-servers.json for changes and invokes callback on modification."""

    def __init__(
        self,
        config_path: str,
        on_change: Callable[[dict[str, ServerConfig]], Any] | None = None,
        poll_interval: float = 2.0,
    ) -> None:
        self._config_path = config_path
        self._on_change = on_change
        self._poll_interval = poll_interval
        self._last_mtime: float = 0.0
        self._task: asyncio.Task[None] | None = None

    @property
    def path(self) -> str:
        return self._config_path

    def start(self) -> None:
        """Start polling for config changes."""
        if not os.path.exists(self._config_path):
            _log(f"Config not found: {self._config_path}")
            return
        self._last_mtime = self._get_mtime()
        self._task = asyncio.create_task(self._poll_loop())
        _log(f"Watching: {self._config_path}")

    def stop(self) -> None:
        """Stop polling."""
        if self._task and not self._task.done():
            self._task.cancel()
        self._task = None

    async def _poll_loop(self) -> None:
        """Poll file mtime and trigger callback on change."""
        try:
            while True:
                await asyncio.sleep(self._poll_interval)
                mtime = self._get_mtime()
                if mtime > self._last_mtime:
                    self._last_mtime = mtime
                    _log("Config changed, reloading...")
                    config = load_config(self._config_path)
                    if self._on_change:
                        result = self._on_change(config)
                        if asyncio.iscoroutine(result):
                            await result
        except asyncio.CancelledError:
            pass

    def _get_mtime(self) -> float:
        """Get file modification time, return 0 if file doesn't exist."""
        try:
            return os.path.getmtime(self._config_path)
        except OSError:
            return 0.0


def _log(msg: str) -> None:
    print(f"[config-watcher] {msg}", file=sys.stderr, flush=True)
