"""Merges local and remote tool definitions into a single unified list."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Literal

from ..local.server_process import ToolDefinition


ConflictResolution = Literal["local-first", "remote-first"]


@dataclass
class RegistryTool:
    """Tool definition with source metadata."""
    name: str
    description: str | None = None
    input_schema: dict | None = None
    source: Literal["local", "remote"] = "remote"
    server_name: str | None = None


class UnifiedRegistry:
    """Merges local and remote tools with conflict resolution."""

    def __init__(self, conflict_resolution: ConflictResolution = "local-first") -> None:
        self._conflict_resolution = conflict_resolution
        self._merged: list[RegistryTool] = []
        self._local_tools: list[RegistryTool] = []
        self._remote_tools: list[RegistryTool] = []

    def get_all(self) -> list[RegistryTool]:
        """Get all merged tools."""
        return list(self._merged)

    def get_tool_definitions(self) -> list[dict]:
        """Get tool definitions formatted for MCP tools/list response."""
        result = []
        for t in self._merged:
            entry: dict = {"name": t.name}
            if t.description:
                entry["description"] = t.description
            if t.input_schema:
                entry["inputSchema"] = t.input_schema
            result.append(entry)
        return result

    def set_local_tools(
        self,
        tools: list[ToolDefinition],
        server_map: dict[str, list[str]] | None = None,
    ) -> None:
        """Update local tools and re-merge."""
        self._local_tools = [
            RegistryTool(
                name=t.name,
                description=t.description,
                input_schema=t.input_schema,
                source="local",
                server_name=_find_server(t.name, server_map) if server_map else None,
            )
            for t in tools
        ]
        self._rebuild()

    def set_remote_tools(self, tools: list[ToolDefinition]) -> None:
        """Update remote tools and re-merge."""
        self._remote_tools = [
            RegistryTool(
                name=t.name,
                description=t.description,
                input_schema=t.input_schema,
                source="remote",
            )
            for t in tools
        ]
        self._rebuild()

    def has(self, tool_name: str) -> bool:
        """Check if a tool exists in the registry."""
        return any(t.name == tool_name for t in self._merged)

    def find(self, tool_name: str) -> RegistryTool | None:
        """Find a tool by name."""
        for t in self._merged:
            if t.name == tool_name:
                return t
        return None

    @property
    def local_count(self) -> int:
        return len(self._local_tools)

    @property
    def remote_count(self) -> int:
        return len(self._remote_tools)

    @property
    def total_count(self) -> int:
        return len(self._merged)

    def _rebuild(self) -> None:
        """Rebuild merged list with conflict resolution."""
        merged: dict[str, RegistryTool] = {}

        if self._conflict_resolution == "local-first":
            # Remote first (overwritten by local)
            for tool in self._remote_tools:
                merged[tool.name] = tool
            for tool in self._local_tools:
                merged[tool.name] = tool
        else:
            # Local first (overwritten by remote)
            for tool in self._local_tools:
                merged[tool.name] = tool
            for tool in self._remote_tools:
                merged[tool.name] = tool

        self._merged = list(merged.values())


def _find_server(tool_name: str, server_map: dict[str, list[str]] | None) -> str | None:
    """Find which server a tool belongs to."""
    if not server_map:
        return None
    for server, tools in server_map.items():
        if tool_name in tools:
            return server
    return None
