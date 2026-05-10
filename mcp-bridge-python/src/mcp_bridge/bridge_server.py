"""stdio MCP server — reads JSON-RPC from stdin, writes to stdout."""

from __future__ import annotations

import asyncio
import json
import sys
from typing import Any

from .models import BridgeConfig
from .http_client import HttpStreamableClient
from .health_check import HealthCheckManager
from .local_tools import handle_stream_write, handle_embed_images


class BridgeServer:
    """stdio MCP server that proxies requests to Orchestrator."""

    def __init__(
        self,
        config: BridgeConfig,
        http_client: HttpStreamableClient,
        health_check: HealthCheckManager,
    ) -> None:
        self._config = config
        self._http_client = http_client
        self._health_check = health_check
        self._workspace_root: str | None = None

    async def run(self) -> None:
        """Main loop: read stdin line by line, process, write stdout."""
        reader = asyncio.StreamReader()
        protocol = asyncio.StreamReaderProtocol(reader)
        loop = asyncio.get_event_loop()
        await loop.connect_read_pipe(lambda: protocol, sys.stdin.buffer)

        self._log("Bridge MCP server ready (stdio transport)")

        while True:
            line = await reader.readline()
            if not line:
                break
            text = line.decode("utf-8").strip()
            if not text:
                continue
            try:
                request = json.loads(text)
                response = await self._handle_request(request)
                if response:
                    self._write_response(response)
            except json.JSONDecodeError:
                self._log(f"Invalid JSON: {text[:100]}")
            except Exception as e:
                self._log(f"Error handling request: {e}")

    async def _handle_request(self, request: dict[str, Any]) -> dict[str, Any] | None:
        method = request.get("method", "")
        req_id = request.get("id")
        params = request.get("params", {})

        if method == "initialize":
            return self._handle_initialize(req_id)
        elif method == "initialized":
            return None  # notification, no response
        elif method == "tools/list":
            return self._handle_tools_list(req_id)
        elif method == "tools/call":
            return await self._handle_tool_call(req_id, params)
        else:
            return self._error_response(req_id, -32601, f"Method not found: {method}")

    def _handle_initialize(self, req_id: Any) -> dict[str, Any]:
        return {
            "jsonrpc": "2.0",
            "id": req_id,
            "result": {
                "protocolVersion": "2025-03-26",
                "capabilities": {"tools": {"listChanged": True}},
                "serverInfo": {"name": "mcp-bridge-python", "version": "1.0.0"},
            },
        }

    def _handle_tools_list(self, req_id: Any) -> dict[str, Any]:
        return {"jsonrpc": "2.0", "id": req_id, "result": {"tools": self._get_tool_definitions()}}

    async def _handle_tool_call(self, req_id: Any, params: dict[str, Any]) -> dict[str, Any]:
        name = params.get("name", "")
        args = params.get("arguments", {})

        try:
            result = await self._dispatch_tool(name, args)
            return {"jsonrpc": "2.0", "id": req_id, "result": result}
        except Exception as e:
            return {"jsonrpc": "2.0", "id": req_id, "result": {
                "content": [{"type": "text", "text": f"{name} failed: {e}"}], "isError": True
            }}

    async def _dispatch_tool(self, name: str, args: dict[str, Any]) -> dict[str, Any]:
        if name == "stream_write_file" and self._config.enable_local_tools:
            result = handle_stream_write(args, self._workspace_root)
            return {"content": [{"type": "text", "text": json.dumps(result)}]}
        elif name == "embed_images" and self._config.enable_local_tools:
            result = handle_embed_images(args, self._workspace_root)
            return {"content": [{"type": "text", "text": json.dumps(result)}]}
        else:
            # Proxy to orchestrator
            return await self._http_client.call_tool(name, args)

    def _get_tool_definitions(self) -> list[dict[str, Any]]:
        tools: list[dict[str, Any]] = [
            {
                "name": "find_tools",
                "description": "Search for available tools by describing what you want to accomplish.",
                "inputSchema": {
                    "type": "object",
                    "properties": {"query": {"type": "string", "description": "Natural language description"}},
                    "required": ["query"],
                },
            },
            {
                "name": "execute_dynamic_tool",
                "description": "Execute a tool on an upstream MCP server",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "tool_name": {"type": "string", "description": "Exact tool name"},
                        "arguments": {"type": "object", "description": "Arguments for the tool"},
                    },
                    "required": ["tool_name"],
                },
            },
            {
                "name": "toggle_tool",
                "description": "Enable or disable a specific tool or an entire server.",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "tool_name": {"type": "string"},
                        "server_name": {"type": "string"},
                        "enabled": {"type": "boolean"},
                    },
                    "required": ["enabled"],
                },
            },
            {
                "name": "reset_tools",
                "description": "Reset all tool/server toggle states to default.",
                "inputSchema": {
                    "type": "object",
                    "properties": {"server_name": {"type": "string"}},
                    "required": [],
                },
            },
            {
                "name": "manage_auto_approve",
                "description": "Add or remove tools from the auto-approve list.",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "tool_name": {"type": "string"},
                        "server_name": {"type": "string"},
                        "auto_approve": {"type": "boolean"},
                    },
                    "required": ["auto_approve"],
                },
            },
            {
                "name": "agent_log",
                "description": "Write an execution log entry for agent activity tracking.",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "ticket_key": {"type": "string"},
                        "agent_name": {"type": "string"},
                        "step": {"type": "string"},
                        "status": {"type": "string"},
                        "message": {"type": "string"},
                        "artifacts": {"type": "string"},
                    },
                    "required": ["ticket_key", "agent_name", "step", "status", "message"],
                },
            },
        ]
        if self._config.enable_local_tools:
            tools.extend([
                {
                    "name": "stream_write_file",
                    "description": "Write content directly to a file on disk.",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "file_path": {"type": "string", "description": "Path to the output file"},
                            "content": {"type": "string", "description": "Text content to write"},
                            "mode": {"type": "string", "description": "write, append, or create"},
                            "encoding": {"type": "string", "description": "Character encoding (default: utf-8)"},
                        },
                        "required": ["file_path"],
                    },
                },
                {
                    "name": "embed_images",
                    "description": "Replace local image references with inline base64 data URIs.",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "file_path": {"type": "string", "description": "Path to the source markdown file"},
                            "output_path": {"type": "string", "description": "Optional output path"},
                        },
                        "required": ["file_path"],
                    },
                },
            ])
        return tools

    def _error_response(self, req_id: Any, code: int, message: str) -> dict[str, Any]:
        return {"jsonrpc": "2.0", "id": req_id, "error": {"code": code, "message": message}}

    def _write_response(self, response: dict[str, Any]) -> None:
        line = json.dumps(response, ensure_ascii=False)
        sys.stdout.write(line + "\n")
        sys.stdout.flush()

    @staticmethod
    def _log(msg: str) -> None:
        print(f"[mcp-bridge] {msg}", file=sys.stderr, flush=True)
