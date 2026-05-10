"""Health check manager — periodic ping & auto-reconnect (MTO-46)."""

from __future__ import annotations

import asyncio
import sys

from .models import BridgeConfig, BridgeState
from .http_client import HttpStreamableClient
from .reconnection import ReconnectionManager


class HealthCheckManager:
    """Periodic ping + state management."""

    def __init__(
        self,
        config: BridgeConfig,
        http_client: HttpStreamableClient,
        reconnection: ReconnectionManager,
    ) -> None:
        self._config = config
        self._http_client = http_client
        self._reconnection = reconnection
        self._consecutive_failures = 0
        self._task: asyncio.Task[None] | None = None

    def start(self) -> None:
        """Start health check asyncio task."""
        if self._config.ping_interval_ms <= 0:
            self._log("Health check disabled (interval=0)")
            return
        self._consecutive_failures = 0
        self._task = asyncio.ensure_future(self._ping_loop())
        self._log(f"Health check started (interval={self._config.ping_interval_ms}ms)")

    def stop(self) -> None:
        """Cancel health check task."""
        if self._task and not self._task.done():
            self._task.cancel()
            self._task = None

    async def _ping_loop(self) -> None:
        """Main ping loop: sleep → ping → check."""
        try:
            while True:
                await asyncio.sleep(self._config.ping_interval_ms / 1000)
                ok = await self._send_ping()
                if ok:
                    self._on_ping_success()
                else:
                    await self._on_ping_failure()
        except asyncio.CancelledError:
            pass

    async def _send_ping(self) -> bool:
        """Send ping request. Any JSON-RPC response = alive."""
        try:
            await self._http_client.send_request("ping")
            return True
        except Exception:
            return False

    def _on_ping_success(self) -> None:
        if self._consecutive_failures > 0:
            self._log("Ping OK — connection restored")
        self._consecutive_failures = 0

    async def _on_ping_failure(self) -> None:
        self._consecutive_failures += 1
        self._log(f"Ping failed ({self._consecutive_failures} consecutive)")
        if self._consecutive_failures >= 1:
            await self._trigger_reconnect()

    async def _trigger_reconnect(self) -> None:
        self.stop()
        self._log("State: CONNECTED → DISCONNECTED (ping timeout)")
        self._reconnection.state = BridgeState.DISCONNECTED
        self._http_client.reset_session()
        await self._reconnection.reconnect_loop()
        if self._reconnection.state == BridgeState.CONNECTED:
            self.start()

    @staticmethod
    def _log(msg: str) -> None:
        print(f"[mcp-bridge] {msg}", file=sys.stderr, flush=True)
