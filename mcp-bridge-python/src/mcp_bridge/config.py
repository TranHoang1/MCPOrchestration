"""Configuration parsing — CLI args > env vars > defaults."""

import argparse
import os
from .models import BridgeConfig


def parse_config() -> BridgeConfig:
    parser = argparse.ArgumentParser(description="MCP Bridge Client (Python)")
    parser.add_argument("--url", default=None, help="Orchestrator URL")
    parser.add_argument("--timeout", type=int, default=None, help="Request timeout (ms)")
    parser.add_argument("--ping-interval", type=int, default=None, help="Ping interval (ms, 0=disabled)")
    parser.add_argument("--ping-timeout", type=int, default=None, help="Ping timeout (ms)")
    parser.add_argument("--token", default=None, help="JWT bridge token")
    parser.add_argument("--no-reconnect", action="store_true", help="Disable auto-reconnect")
    parser.add_argument("--no-local-tools", action="store_true", help="Disable local tools")
    args = parser.parse_args()

    url = args.url or os.environ.get("ORCHESTRATOR_URL", "http://localhost:8080")
    timeout = args.timeout or int(os.environ.get("BRIDGE_TIMEOUT", "30000"))
    ping_interval = args.ping_interval if args.ping_interval is not None else int(
        os.environ.get("BRIDGE_PING_INTERVAL", "30000")
    )
    ping_timeout = args.ping_timeout if args.ping_timeout is not None else int(
        os.environ.get("BRIDGE_PING_TIMEOUT", "5000")
    )
    token = args.token or os.environ.get("MCP_BRIDGE_TOKEN")

    return BridgeConfig(
        orchestrator_url=url,
        request_timeout_ms=timeout,
        ping_interval_ms=ping_interval,
        ping_timeout_ms=ping_timeout,
        reconnect_enabled=not args.no_reconnect,
        enable_local_tools=not args.no_local_tools,
        token=token,
    )
