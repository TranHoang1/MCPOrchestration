"""Unit tests for UrlManager."""

import pytest
from mcp_bridge.url_manager import UrlManager


def test_empty_urls_raises():
    with pytest.raises(ValueError, match="No valid URLs configured"):
        UrlManager([])


def test_single_url():
    mgr = UrlManager(["http://localhost:8080"])
    assert mgr.active_url == "http://localhost:8080"
    assert mgr.url_count == 1
    assert mgr.url_index == 0


def test_advance_wraps_around():
    mgr = UrlManager(["http://a:8080", "http://b:8080"])
    assert mgr.advance() == "http://b:8080"
    assert mgr.url_index == 1
    assert mgr.advance() == "http://a:8080"
    assert mgr.url_index == 0


def test_mark_failed_and_has_next():
    mgr = UrlManager(["http://a:8080", "http://b:8080"])
    assert mgr.has_next() is True
    mgr.mark_failed("http://a:8080", "timeout")
    assert mgr.has_next() is True
    mgr.mark_failed("http://b:8080", "refused")
    assert mgr.has_next() is False


def test_get_errors():
    mgr = UrlManager(["http://a:8080"])
    mgr.mark_failed("http://a:8080", "timeout")
    errors = mgr.get_errors()
    assert len(errors) == 1
    assert errors[0].url == "http://a:8080"
    assert errors[0].error == "timeout"


def test_clear_errors():
    mgr = UrlManager(["http://a:8080"])
    mgr.mark_failed("http://a:8080", "err")
    mgr.clear_errors()
    assert mgr.get_errors() == []
    assert mgr.has_next() is True


def test_reset():
    mgr = UrlManager(["http://a:8080", "http://b:8080"])
    mgr.advance()
    mgr.mark_failed("http://b:8080", "err")
    mgr.reset()
    assert mgr.url_index == 0
    assert mgr.active_url == "http://a:8080"
    assert mgr.get_errors() == []


def test_backward_compat_single_url():
    mgr = UrlManager(["http://localhost:8080"])
    assert mgr.url_count == 1
    assert mgr.advance() == "http://localhost:8080"
