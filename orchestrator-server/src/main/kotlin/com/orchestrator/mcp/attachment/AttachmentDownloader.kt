package com.orchestrator.mcp.attachment

import com.orchestrator.mcp.jira.JiraRestClient
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.LoggerFactory

/**
 * Downloads attachment binary content from Jira with concurrency limiting.
 */
interface AttachmentDownloader {
    suspend fun download(url: String): ByteArray
}

class AttachmentDownloaderImpl(
    private val jiraRestClient: JiraRestClient,
    private val semaphore: Semaphore
) : AttachmentDownloader {

    private val logger = LoggerFactory.getLogger(AttachmentDownloaderImpl::class.java)

    override suspend fun download(url: String): ByteArray {
        semaphore.acquire()
        try {
            logger.debug("Downloading attachment: {}", url.take(80))
            val result = jiraRestClient.downloadAttachment(url)
            return result.content
        } finally {
            semaphore.release()
        }
    }
}
