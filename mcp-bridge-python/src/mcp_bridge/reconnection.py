"""Reconnection manager with exponential backoff and multi-URL failover."""

from __future__ import annotations

import asyncio
import sys

from .models import BridgeConfig, BridgeState
from .http_client import HttpStreamableClient
from .url_manager import UrlManager


class ReconnectionManager:
    """Manages auto-reconnection with multi-URL failover and backoff."""

    def __init__(self, config: BridgeConfig, client: HttpStreamableClient) -> None:
        self._config = config
        self._client = client
        self._attempt = 0
        self.state = BridgeState.DISCONNECTED
        self._url_manager = UrlManager(config.orchestrator_urls)

    async def connect_with_retry(self) -> bool:
        """Try all URLs sequentially for initial connection."""
        self.state = BridgeState.CONNECTING
        self._url_manager.clear_errors()

        for _ in range(self._url_manager.url_count):
            url = self._url_manager.active_url
            idx = self._url_manager.url_index
            self._log(f"Trying URL {idx + 1}/{self._url_manager.url_count}: {url}")

            try:
                if await self._client.initialize(url):
                    self.state = BridgeState.CONNECTED
                    self._attempt = 0
                    return True
            except Exception as e:
                self._url_manager.mark_failed(url, str(e))
                self._log(f"URL {idx + 1}/{self._url_manager.url_count} failed: {e}")

            if self._url_manager.has_next():
                self._url_manager.advance()

        self._report_errors()
        self.state = BridgeState.DISCONNECTED
        return False

    async def reconnect_loop(self) -> None:
        """Reconnect with retry on active URL, then rotate."""
        if not self._config.reconnect_enabled:
            return
        self.state = BridgeState.RECONNECTING

        # Phase 1: Retry active URL
        for i in range(self._config.max_retry_before_rotate):
            delay = self._calculate_backoff(i)
            self._log(
                f"Retry {i + 1}/{self._config.max_retry_before_rotate} "
                f"for {self._url_manager.active_url} in {delay}ms"
            )
            await asyncio.sleep(delay / 1000)
            self._client.reset_session()
            if await self._client.initialize(self._url_manager.active_url):
                self.state = BridgeState.CONNECTED
                self._attempt = 0
                self._log("Reconnected successfully")
                return

        # Phase 2: Rotate to other URLs
        if self._url_manager.url_count > 1:
            self._url_manager.clear_errors()
            self._url_manager.mark_failed(self._url_manager.active_url, "Exhausted retries")

            while self._url_manager.has_next():
                next_url = self._url_manager.advance()
                self._log(
                    f"Switching to URL {self._url_manager.url_index + 1}"
                    f"/{self._url_manager.url_count}: {next_url}"
                )
                self._client.reset_session()
                if await self._client.initialize(next_url):
                    self.state = BridgeState.CONNECTED
                    self._attempt = 0
                    return
                self._url_manager.mark_failed(next_url, "Connection failed")

            self._report_errors()
            self._url_manager.reset()

        # Phase 3: Infinite backoff loop
        await self._infinite_reconnect()

    async def _infinite_reconnect(self) -> None:
        """Fallback infinite reconnect on active URL."""
        while not self._client.is_connected:
            delay = self._calculate_backoff(self._attempt)
            self._log(f"Reconnecting in {delay}ms (attempt {self._attempt})")
            await asyncio.sleep(delay / 1000)
            self._client.reset_session()
            if await self._client.initialize(self._url_manager.active_url):
                self.state = BridgeState.CONNECTED
                self._attempt = 0
                self._log("Reconnected successfully")
                return
            self._attempt += 1

    def _report_errors(self) -> None:
        errors = self._url_manager.get_errors()
        lines = [f"  - {e.url}: {e.error}" for e in errors]
        self._log(f"All URLs failed:\n" + "\n".join(lines))

    def _calculate_backoff(self, attempt: int) -> int:
        delay = self._config.base_reconnect_delay_ms * (2 ** attempt)
        return min(delay, self._config.max_reconnect_delay_ms)

    @staticmethod
    def _log(msg: str) -> None:
        print(f"[mcp-bridge] {msg}", file=sys.stderr, flush=True)
