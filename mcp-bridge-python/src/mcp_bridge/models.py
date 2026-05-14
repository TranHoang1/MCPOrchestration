"""Data models and enums for the MCP Bridge."""

from dataclasses import dataclass, field
from enum import Enum


class BridgeState(Enum):
    DISCONNECTED = "DISCONNECTED"
    CONNECTING = "CONNECTING"
    CONNECTED = "CONNECTED"
    RECONNECTING = "RECONNECTING"


@dataclass
class BridgeConfig:
    orchestrator_urls: list[str] = field(default_factory=lambda: ["http://localhost:8080"])
    orchestrator_url: str = "http://localhost:8080"
    request_timeout_ms: int = 30_000
    connection_timeout_ms: int = 5_000
    max_retry_before_rotate: int = 3
    ping_interval_ms: int = 30_000
    ping_timeout_ms: int = 5_000
    base_reconnect_delay_ms: int = 1_000
    max_reconnect_delay_ms: int = 15_000
    enable_local_tools: bool = True
    reconnect_enabled: bool = True
    token: str | None = None
