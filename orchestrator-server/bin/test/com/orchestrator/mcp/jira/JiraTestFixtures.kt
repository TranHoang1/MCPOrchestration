package com.orchestrator.mcp.jira

/**
 * JSON fixtures for Jira REST Client tests.
 */

internal const val SEARCH_RESPONSE_JSON = """
{
  "startAt": 0,
  "maxResults": 50,
  "total": 120,
  "issues": [
    {
      "id": "10001",
      "key": "MTO-1",
      "self": "https://test.atlassian.net/rest/api/3/issue/10001",
      "fields": {
        "summary": "Test issue",
        "status": {"name": "To Do"}
      }
    }
  ]
}
"""

internal const val ISSUE_RESPONSE_JSON = """
{
  "id": "10016",
  "key": "MTO-16",
  "self": "https://test.atlassian.net/rest/api/3/issue/10016",
  "fields": {
    "summary": "Jira REST Client",
    "status": {"name": "In Progress"},
    "assignee": {"displayName": "Dev", "accountId": "abc123"}
  },
  "changelog": {"histories": []}
}
"""

internal const val ATTACHMENT_RESPONSE_JSON = """
{
  "id": "10016",
  "key": "MTO-16",
  "self": "https://test.atlassian.net/rest/api/3/issue/10016",
  "fields": {
    "attachment": [
      {
        "id": "att-001",
        "filename": "design.pdf",
        "mimeType": "application/pdf",
        "size": 2048,
        "content": "https://test.atlassian.net/rest/api/3/attachment/content/att-001",
        "author": {
          "displayName": "Duc Nguyen",
          "emailAddress": "duc@example.com",
          "accountId": "user-123"
        },
        "created": "2025-07-14T10:00:00.000+0000"
      }
    ]
  }
}
"""
