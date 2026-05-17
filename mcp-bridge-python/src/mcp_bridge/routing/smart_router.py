"""Routes tool calls to local stdio or remote HTTP with O(1) lookup and fallback."""

from __future__ import annotations

import sys
import time
from dataclasses import dataclass, field
from typing import Any

from ..http_client import HttpStreamableClient
from ..local.local_server_manager import LocalServerManager
from .routing_table import RoutingTable, ToolRoute


@dataclass
class ToolMetrics:
    """Metrics for a single tool."""
    call_count: int = 0
    error_count: int = 0
    total_latency_ms: float = 0.0
    last_call_at: float | None = None


@dataclass
class RouteResult:
    """Result of a routed tool call."""
    content: list[dict[str, str]] = field(default_factory=list)
    is_error: bool = False


class SmartRouter:
    """Routes tool calls to local or remote with fallback and metrics."""

    def __init__(
        self,
        routing_table: RoutingTable,
        local_manager: LocalServerManager,
        http_client: HttpStreamableClient,
    ) -> None:
        self._routing_table = routing_table
        self._local_manager = local_manager
        self._http_client = http_client
        self._metrics: dict[str, ToolMetrics] = {}

    async def route(self, tool_name: str, args: dict[str, Any]) -> RouteResult:
        """Route a tool call to the appropriate destination."""
        start = time.time()
        try:
            result = await self._do_route(tool_name, args)
            self._record_metric(tool_name, time.time() - start, is_error=False)
            return result
        except Exception:
            self._record_metric(tool_name, time.time() - start, is_error=True)
            raise

    def get_metrics(self) -> dict[str, ToolMetrics]:
        """Get metrics for all tools."""
        return dict(self._metrics)

    async def _do_route(self, tool_name: str, args: dict[str, Any]) -> RouteResult:
        """Determine destination and execute call."""
        route = self._routing_table.resolve(tool_name)

        if route:
            return await self._route_by_definition(tool_name, args, route)

        # No explicit route — check local servers
        local_server = self._local_manager.find_server_for_tool(tool_name)
        if local_server:
            return await self._call_local(local_server, tool_name, args)

        # Default to routing table's default location
        if self._routing_table.default_location == "local":
            raise RuntimeError(f"Tool '{tool_name}' not found in any local server")

        return await self._call_remote(tool_name, args)

    async def _route_by_definition(
        self, tool_name: str, args: dict[str, Any], route: ToolRoute
    ) -> RouteResult:
        """Route based on explicit routing table definition."""
        if route.location == "local":
            return await self._try_local_with_fallback(tool_name, args, route)
        if route.location == "remote":
            return await self._try_remote_with_fallback(tool_name, args, route)
        raise RuntimeError(f"Invalid route location for '{tool_name}': {route.location}")

    async def _try_local_with_fallback(
        self, tool_name: str, args: dict[str, Any], route: ToolRoute
    ) -> RouteResult:
        """Try local, fall back to remote if configured."""
        try:
            server = route.server or self._local_manager.find_server_for_tool(tool_name)
            if not server:
                raise RuntimeError(f"No local server for tool '{tool_name}'")
            return await self._call_local(server, tool_name, args)
        except Exception:
            if route.fallback == "remote":
                _log(f"Local failed for '{tool_name}', falling back to remote")
                return await self._call_remote(tool_name, args)
            raise

    async def _try_remote_with_fallback(
        self, tool_name: str, args: dict[str, Any], route: ToolRoute
    ) -> RouteResult:
        """Try remote, fall back to local if configured."""
        try:
            return await self._call_remote(tool_name, args)
        except Exception:
            if route.fallback == "local":
                server = self._local_manager.find_server_for_tool(tool_name)
                if server:
                    _log(f"Remote failed for '{tool_name}', falling back to local")
                    return await self._call_local(server, tool_name, args)
            raise

    async def _call_local(
        self, server_name: str, tool_name: str, args: dict[str, Any]
    ) -> RouteResult:
        """Execute tool call on a local server."""
        result = await self._local_manager.call_tool(server_name, tool_name, args)
        if isinstance(result, dict):
            return RouteResult(
                content=result.get("content", [{"type": "text", "text": str(result)}]),
                is_error=result.get("isError", False),
            )
        return RouteResult(content=[{"type": "text", "text": str(result)}])

    async def _call_remote(self, tool_name: str, args: dict[str, Any]) -> RouteResult:
        """Execute tool call on the remote orchestrator."""
        response = await self._http_client.call_tool(tool_name, args)
        return RouteResult(
            content=response.get("content", [{"type": "text", "text": "{}"}]),
            is_error=response.get("isError", False),
        )

    def _record_metric(self, tool_name: str, elapsed: float, is_error: bool) -> None:
        """Record call metrics for a tool."""
        m = self._metrics.setdefault(tool_name, ToolMetrics())
        m.call_count += 1
        if is_error:
            m.error_count += 1
        m.total_latency_ms += elapsed * 1000
        m.last_call_at = time.time()


def _log(msg: str) -> None:
    print(f"[smart-router] {msg}", file=sys.stderr, flush=True)
