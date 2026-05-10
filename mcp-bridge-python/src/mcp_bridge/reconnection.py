"""Reconnection manager with exponential backoff."""

from __future__ import annotations

import asyncio
import sys

from .models import BridgeConfig, BridgeState
from .http_client import HttpStreamableClient


class ReconnectionManager:
    """Manages auto-reconnection with exponential backoff (max 15s)."""

    def __init__(self, config: BridgeConfig, client: HttpStreamableClient) -> None:
        self._config = config
        self._client = client
        self._attempt = 0
        self.state = BridgeState.DISCONNECTED

    async def connect_with_retry(self) -> bool:
        """Attempt initial connection (up to 3 attempts)."""
        self.state = BridgeState.CONNECTING
        for i in range(3):
            if await self._client.initialize():
                self.state = BridgeState.CONNECTED
                self._attempt = 0
                return True
            delay = self._calculate_backoff(i)
            self._log(f"Connection attempt {i + 1} failed, retrying in {delay}ms")
            await asyncio.sleep(delay / 1000)
        self.state = BridgeState.DISCONNECTED
        return False

    async def reconnect_loop(self) -> None:
        """Background reconnection loop with exponential backoff."""
        if not self._config.reconnect_enabled:
            return
        while not self._client.is_connected:
            self.state = BridgeState.RECONNECTING
            delay = self._calculate_backoff(self._attempt)
            self._log(f"Reconnecting in {delay}ms (attempt {self._attempt})")
            await asyncio.sleep(delay / 1000)
            self._client.reset_session()
            if await self._client.initialize():
                self.state = BridgeState.CONNECTED
                self._attempt = 0
                self._log("Reconnected successfully")
                return
            self._attempt += 1

    def _calculate_backoff(self, attempt: int) -> int:
        delay = self._config.base_reconnect_delay_ms * (2 ** attempt)
        return min(delay, self._config.max_reconnect_delay_ms)

    @staticmethod
    def _log(msg: str) -> None:
        print(f"[mcp-bridge] {msg}", file=sys.stderr, flush=True)
