package com.orchestrator.mcp.bridge

import org.slf4j.LoggerFactory

/**
 * Manages ordered URL list with rotation and error tracking.
 * Thread-safe via synchronized access to mutable state.
 */
class UrlManager(urls: List<String>) {
    private val logger = LoggerFactory.getLogger(UrlManager::class.java)
    private val _urls: List<String> = urls.toList()
    private var _urlIndex: Int = 0
    private val _errors: MutableList<UrlError> = mutableListOf()

    init {
        require(_urls.isNotEmpty()) { "No valid URLs configured" }
    }

    val activeUrl: String get() = _urls[_urlIndex]
    val urlIndex: Int get() = _urlIndex
    val urlCount: Int get() = _urls.size

    @Synchronized
    fun advance(): String {
        _urlIndex = (_urlIndex + 1) % _urls.size
        return activeUrl
    }

    @Synchronized
    fun markFailed(url: String, error: String) {
        _errors.add(UrlError(url, error, System.currentTimeMillis()))
    }

    fun getErrors(): List<UrlError> = _errors.toList()

    @Synchronized
    fun clearErrors() {
        _errors.clear()
    }

    fun hasNext(): Boolean = _errors.size < _urls.size

    @Synchronized
    fun reset() {
        _urlIndex = 0
        _errors.clear()
    }
}

data class UrlError(
    val url: String,
    val error: String,
    val timestamp: Long
)
