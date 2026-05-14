"""URL manager — sequential rotation with error tracking."""

from __future__ import annotations

import time
from dataclasses import dataclass, field


@dataclass
class UrlError:
    """Record of a failed URL connection attempt."""

    url: str
    error: str
    timestamp: float = field(default_factory=time.time)


class UrlManager:
    """Manages ordered URL list with rotation and error tracking."""

    def __init__(self, urls: list[str]) -> None:
        if not urls:
            raise ValueError("No valid URLs configured")
        self._urls = list(urls)
        self._url_index = 0
        self._errors: list[UrlError] = []

    @property
    def active_url(self) -> str:
        return self._urls[self._url_index]

    @property
    def url_index(self) -> int:
        return self._url_index

    @property
    def url_count(self) -> int:
        return len(self._urls)

    def advance(self) -> str:
        """Move to next URL in rotation, return new active URL."""
        self._url_index = (self._url_index + 1) % len(self._urls)
        return self.active_url

    def mark_failed(self, url: str, error: str) -> None:
        """Record a connection failure for a URL."""
        self._errors.append(UrlError(url=url, error=error))

    def get_errors(self) -> list[UrlError]:
        """Get all collected errors."""
        return list(self._errors)

    def clear_errors(self) -> None:
        """Clear error collection, resetting the rotation cycle."""
        self._errors.clear()

    def has_next(self) -> bool:
        """True if there are untried URLs in current cycle."""
        return len(self._errors) < len(self._urls)

    def reset(self) -> None:
        """Reset to first URL and clear errors."""
        self._url_index = 0
        self._errors.clear()
