"""HTTP Streamable client — POST /mcp with JSON-RPC."""

from __future__ import annotations

import sys
from typing import Any

import httpx

from .models import BridgeConfig


class HttpStreamableClient:
    """Async HTTP client for Orchestrator communication."""

    def __init__(self, config: BridgeConfig) -> None:
        self._config = config
        self._client: httpx.AsyncClient | None = None
        self._session_id: str | None = None
        self._connected = False
        self._request_id = 0
        self._active_url = config.orchestrator_url

    @property
    def is_connected(self) -> bool:
        return self._connected

    async def initialize(self, url: str | None = None) -> bool:
        """Send initialize request, store session ID."""
        if url:
            self._active_url = url
        self._ensure_client()
        request = self._build_request("initialize", {
            "protocolVersion": "2025-03-26",
            "capabilities": {},
            "clientInfo": {"name": "mcp-bridge-python", "version": "1.0.0"},
        })
        try:
            response = await self._post(request, include_session=False)
            self._session_id = response.headers.get("mcp-session-id")
            self._connected = self._session_id is not None
            if self._connected:
                self._log("Connected to orchestrator via HTTP Streamable")
            return self._connected
        except Exception as e:
            self._log(f"Initialize failed: {e}")
            self._connected = False
            return False

    async def call_tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        """Send tools/call request to Orchestrator."""
        request = self._build_request("tools/call", {"name": name, "arguments": arguments})
        response = await self._post(request, include_session=True)
        data = response.json()
        if "error" in data:
            return {"content": [{"type": "text", "text": str(data["error"])}], "isError": True}
        return data.get("result", {"content": [{"type": "text", "text": "{}"}]})

    async def send_request(self, method: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
        """Send arbitrary JSON-RPC request."""
        request = self._build_request(method, params)
        response = await self._post(request, include_session=True)
        return response.json()

    def reset_session(self) -> None:
        self._session_id = None
        self._connected = False
        self._request_id = 0

    async def close(self) -> None:
        self._connected = False
        self._session_id = None
        if self._client:
            await self._client.aclose()
            self._client = None

    def _ensure_client(self) -> None:
        if self._client is None:
            timeout = self._config.request_timeout_ms / 1000
            self._client = httpx.AsyncClient(timeout=timeout)

    def _build_request(self, method: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
        self._request_id += 1
        req: dict[str, Any] = {"jsonrpc": "2.0", "id": self._request_id, "method": method}
        if params:
            req["params"] = params
        return req

    async def _post(self, body: dict[str, Any], *, include_session: bool) -> httpx.Response:
        self._ensure_client()
        assert self._client is not None
        headers: dict[str, str] = {"Content-Type": "application/json"}
        if self._config.token:
            headers["Authorization"] = f"Bearer {self._config.token}"
        if include_session and self._session_id:
            headers["Mcp-Session-Id"] = self._session_id
        url = f"{self._active_url}/mcp"
        response = self._client.post(url, json=body, headers=headers)
        resp = await response
        resp.raise_for_status()
        return resp

    @staticmethod
    def _log(msg: str) -> None:
        print(f"[mcp-bridge] {msg}", file=sys.stderr, flush=True)
