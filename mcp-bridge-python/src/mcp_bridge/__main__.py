"""Entry point — python -m mcp_bridge."""

from __future__ import annotations

import asyncio
import signal
import sys

from .config import parse_config
from .http_client import HttpStreamableClient
from .reconnection import ReconnectionManager
from .health_check import HealthCheckManager
from .bridge_server import BridgeServer


def _log(msg: str) -> None:
    print(f"[mcp-bridge] {msg}", file=sys.stderr, flush=True)


async def _run() -> None:
    config = parse_config()
    _log(f"Connecting to orchestrator at: {config.orchestrator_url}")

    http_client = HttpStreamableClient(config)
    reconnection = ReconnectionManager(config, http_client)
    health_check = HealthCheckManager(config, http_client, reconnection)
    bridge = BridgeServer(config, http_client, health_check)

    # Connect to Orchestrator
    connected = await reconnection.connect_with_retry()
    if connected:
        health_check.start()
    else:
        _log("Failed initial connection, will retry in background")
        asyncio.ensure_future(reconnection.reconnect_loop())

    # Run stdio server (blocks until stdin closes)
    await bridge.run()

    # Cleanup
    health_check.stop()
    await http_client.close()


def main() -> None:
    """Main entry point."""
    _log("MCP Bridge Client (Python) v1.0.0 starting...")

    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)

    def shutdown(sig: signal.Signals) -> None:
        _log(f"Received {sig.name}, shutting down...")
        for task in asyncio.all_tasks(loop):
            task.cancel()

    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, shutdown, sig)
        except NotImplementedError:
            # Windows doesn't support add_signal_handler
            pass

    try:
        loop.run_until_complete(_run())
    except (KeyboardInterrupt, asyncio.CancelledError):
        pass
    finally:
        loop.close()


if __name__ == "__main__":
    main()
