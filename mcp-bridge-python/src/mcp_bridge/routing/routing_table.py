"""Fetches, caches, and refreshes the tool routing table from the orchestrator."""

from __future__ import annotations

import asyncio
import sys
from dataclasses import dataclass, field
from typing import Any, Literal

import httpx


@dataclass
class ToolRoute:
    """Routing definition for a single tool."""
    location: Literal["local", "remote"]
    server: str | None = None
    fallback: Literal["local", "remote"] | None = None
    priority: int = 0


@dataclass
class RoutingTableData:
    """Full routing table from orchestrator."""
    version: str = "0.0.0"
    updated_at: str = ""
    default_location: Literal["local", "remote"] = "remote"
    tools: dict[str, ToolRoute] = field(default_factory=dict)


class RoutingTable:
    """Manages routing table fetch, ETag caching, and periodic refresh."""

    def __init__(
        self,
        base_url: str,
        token: str | None = None,
        refresh_interval_ms: int = 60_000,
    ) -> None:
        self._base_url = base_url
        self._token = token
        self._refresh_interval = refresh_interval_ms / 1000
        self._cached = RoutingTableData()
        self._etag: str | None = None
        self._refresh_task: asyncio.Task[None] | None = None

    @property
    def cached(self) -> RoutingTableData:
        return self._cached

    @property
    def default_location(self) -> Literal["local", "remote"]:
        return self._cached.default_location

    def resolve(self, tool_name: str) -> ToolRoute | None:
        """Resolve a tool name to its route definition."""
        return self._cached.tools.get(tool_name)

    def set_from_meta(self, meta: dict[str, Any]) -> None:
        """Set routing table from initialize response _meta field."""
        rt = meta.get("routingTable")
        if not isinstance(rt, dict) or "tools" not in rt:
            return
        self._cached = self._parse_table(rt)
        _log(f"Loaded from _meta ({len(self._cached.tools)} routes)")

    async def fetch(self) -> bool:
        """Fetch routing table from orchestrator with ETag caching."""
        try:
            headers: dict[str, str] = {"Accept": "application/json"}
            if self._token:
                headers["Authorization"] = f"Bearer {self._token}"
            if self._etag:
                headers["If-None-Match"] = self._etag

            async with httpx.AsyncClient(timeout=10.0) as client:
                resp = await client.get(f"{self._base_url}/api/routing-table", headers=headers)

            if resp.status_code == 304:
                return True

            if resp.status_code != 200:
                _log(f"Fetch failed: HTTP {resp.status_code}")
                return False

            data = resp.json()
            if not isinstance(data.get("tools"), dict):
                _log("Malformed response: missing tools")
                return False

            self._cached = self._parse_table(data)
            self._etag = resp.headers.get("etag")
            _log(f"Updated ({len(self._cached.tools)} routes, v{self._cached.version})")
            return True
        except Exception as e:
            _log(f"Fetch error: {e} (keeping cache)")
            return False

    def start_refresh(self) -> None:
        """Start periodic refresh loop."""
        if self._refresh_interval <= 0:
            return
        self._refresh_task = asyncio.create_task(self._refresh_loop())

    def stop_refresh(self) -> None:
        """Stop periodic refresh."""
        if self._refresh_task and not self._refresh_task.done():
            self._refresh_task.cancel()
        self._refresh_task = None

    async def refresh(self) -> None:
        """Trigger an immediate refresh."""
        await self.fetch()

    async def _refresh_loop(self) -> None:
        """Periodically fetch routing table."""
        try:
            while True:
                await asyncio.sleep(self._refresh_interval)
                await self.fetch()
        except asyncio.CancelledError:
            pass

    @staticmethod
    def _parse_table(data: dict[str, Any]) -> RoutingTableData:
        """Parse raw JSON into RoutingTableData."""
        tools: dict[str, ToolRoute] = {}
        for name, route_data in data.get("tools", {}).items():
            if isinstance(route_data, dict):
                tools[name] = ToolRoute(
                    location=route_data.get("location", "remote"),
                    server=route_data.get("server"),
                    fallback=route_data.get("fallback"),
                    priority=route_data.get("priority", 0),
                )
        return RoutingTableData(
            version=data.get("version", "0.0.0"),
            updated_at=data.get("updatedAt", ""),
            default_location=data.get("defaultLocation", "remote"),
            tools=tools,
        )


def _log(msg: str) -> None:
    print(f"[routing-table] {msg}", file=sys.stderr, flush=True)
