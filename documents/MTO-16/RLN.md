# Release Notes (RLN)

## MCP Orchestration Server — MTO-16: Jira REST Client — Direct API Integration

---

## Release Information

| Field | Value |
|-------|-------|
| Release Version | 1.0.0 |
| Release Date | 2025-07-15 |
| Jira Ticket | MTO-16 |
| Environment | DEV / SIT / UAT / PROD |
| Author | DevOps Agent |
| Status | Draft |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-15 | DevOps Agent | Initiate document |

---

## 1. What's New

### 1.1 Feature Summary

A new Jira REST Client module has been added to the MCP Orchestration Server. This module provides a dedicated, high-performance HTTP communication layer for integrating directly with Jira Cloud/Server REST API v3. It serves as the foundation for the upcoming Jira Project Sync Service (Epic MTO-14), enabling automated background synchronization of Jira tickets into the Knowledge Base.

Key capabilities:
- Search Jira issues using JQL (Jira Query Language)
- Fetch individual issue details with field selection
- Retrieve and download issue attachments
- Built-in rate limiting to respect Jira API quotas
- Automatic retry with exponential backoff for transient failures
- Comprehensive error handling with typed exceptions

### 1.2 User-Facing Changes

| # | Change | Description | Impact |
|---|--------|-------------|--------|
| 1 | Jira API Integration | New module enables automated Jira data retrieval | Low (backend only, no UI changes) |
| 2 | Environment Configuration | 8 new environment variables for Jira connectivity | Medium (DevOps must configure) |
| 3 | Koin DI Registration | `jiraModule` added to application DI container | Low (transparent to end users) |

### 1.3 Screenshots (if applicable)

N/A — This is a backend module with no UI components.

---

## 2. Technical Changes

### 2.1 API Changes

| Type | Endpoint | Method | Description |
|------|----------|--------|-------------|
| New (Internal) | `JiraRestClient.searchIssues()` | POST /rest/api/3/search | Search Jira issues by JQL with pagination |
| New (Internal) | `JiraRestClient.getIssue()` | GET /rest/api/3/issue/{key} | Fetch single issue with field/expand options |
| New (Internal) | `JiraRestClient.getAttachments()` | GET /rest/api/3/issue/{key}?fields=attachment | Get attachment metadata for an issue |
| New (Internal) | `JiraRestClient.downloadAttachment()` | GET {content URL} | Download attachment binary content |

> Note: These are internal Kotlin suspend functions, not exposed as REST endpoints. They are consumed by the Sync Job Orchestrator (MTO-17).

### 2.2 Database Changes

| Type | Object | Description |
|------|--------|-------------|
| None | — | No database changes in this release |

### 2.3 Configuration Changes

| Property | Change Type | Description |
|----------|-----------|-------------|
| `JIRA_BASE_URL` | New | Jira instance base URL (required) |
| `JIRA_EMAIL` | New | Service account email for Basic Auth (required) |
| `JIRA_API_TOKEN` | New | Jira API token — secret (required) |
| `JIRA_RATE_LIMIT` | New | Max requests/second (default: 10) |
| `JIRA_TIMEOUT_MS` | New | Request timeout in ms (default: 30000) |
| `JIRA_MAX_RETRIES` | New | Max retry attempts (default: 3) |
| `JIRA_CONNECT_TIMEOUT_MS` | New | Connection timeout in ms (default: 10000) |
| `JIRA_SOCKET_TIMEOUT_MS` | New | Socket timeout in ms (default: 30000) |

### 2.4 Infrastructure Changes

| Component | Change | Description |
|-----------|--------|-------------|
| Fat JAR | Modified | `mcp-orchestrator-all.jar` now includes Jira client module |
| Firewall | New Rule | Outbound HTTPS to `*.atlassian.net:443` required |
| Systemd | Modified | `EnvironmentFile` directive added for Jira config |

---

## 3. Bug Fixes

No bug fixes included in this release. This is a new feature module.

---

## 4. Known Issues & Limitations

| # | Issue | Impact | Workaround | Target Fix |
|---|-------|--------|------------|------------|
| 1 | Only Basic Auth supported | Cannot use OAuth 2.0 for Jira authentication | Use API token (sufficient for current needs) | Future iteration |
| 2 | No write operations | Cannot create/update/delete Jira issues | Read-only by design for sync use case | Out of scope |
| 3 | Single-instance rate limiting | Rate limiter is per-JVM, not distributed | Run single sync instance per environment | MTO-17 (if needed) |
| 4 | Attachment size not capped | Large attachments may consume significant memory | Monitor heap usage; implement size limit in MTO-17 | MTO-17 |

---

## 5. Dependencies

### 5.1 Pre-requisite Releases

| Release | Version | Status | Required Before |
|---------|---------|--------|-----------------|
| MCP Orchestrator base | 1.0.0 | Deployed | This release |
| JVM 21 runtime | 21.x | Installed | This release |

### 5.2 External System Changes

| System | Change Required | Status | Contact |
|--------|----------------|--------|---------|
| Jira Cloud | API token provisioned for service account | Pending | Jira Admin |
| Network/Firewall | Outbound HTTPS to *.atlassian.net | Pending | Network Team |

---

## 6. Migration Notes

### 6.1 Data Migration

| Migration | Description | Automated | Estimated Time |
|-----------|-------------|-----------|----------------|
| None | No data migration required | — | — |

### 6.2 Breaking Changes

No breaking changes in this release. Fully backward compatible.

The Jira REST Client is a new additive module. Existing functionality is unaffected. If `JIRA_BASE_URL`, `JIRA_EMAIL`, or `JIRA_API_TOKEN` environment variables are not set, the Jira module will throw a `JiraValidationException` at startup — but this only affects the Jira client initialization, not the core orchestrator functionality.

### 6.3 Backward Compatibility

Fully backward compatible. The new module is additive and does not modify any existing APIs, configurations, or behaviors. The application will continue to function normally for all existing use cases even without Jira configuration (though the Jira client will not initialize).

---

## 7. Testing Summary

| Test Level | Total | Passed | Failed | Blocked | Pass Rate |
|-----------|-------|--------|--------|---------|-----------|
| Unit Tests | 22 | 22 | 0 | 0 | 100% |
| Integration Tests | 8 | 8 | 0 | 0 | 100% |
| **Total** | **30** | **30** | **0** | **0** | **100%** |

### Defect Summary

| Severity | Found | Fixed | Open | Deferred |
|----------|-------|-------|------|----------|
| Critical | 0 | 0 | 0 | 0 |
| Major | 0 | 0 | 0 | 0 |
| Minor | 0 | 0 | 0 | 0 |

---

## 8. Deployment Instructions

See: [Deployment Guide](DPG.md)

### Quick Reference

| Step | Action | Estimated Time |
|------|--------|---------------|
| 1 | Configure environment variables | 5 min |
| 2 | Build fat JAR | 2 min |
| 3 | Stop service + deploy artifact | 1 min |
| 4 | Start service | 30 sec |
| 5 | Post-deployment verification | 2 min |
| **Total** | | **~10 minutes** |

---

## 9. Rollback Plan

See: [Deployment Guide — Section 8](DPG.md#8-rollback-plan)

**Rollback Decision Criteria:**
- Application fails to start after deployment
- Health check fails after 60 seconds
- Existing functionality broken (regression)
- Performance degradation > 50%

**Estimated Rollback Time:** ~2 minutes

---

## 10. Contacts

| Role | Name | Contact | Responsibility |
|------|------|---------|---------------|
| Release Manager | SM Agent | sm@company.com | Release coordination |
| Dev Lead | Development Team | dev-lead@company.com | Technical issues |
| QA Lead | QA Team | qa-lead@company.com | Testing sign-off |
| DevOps | DevOps Team | devops@company.com | Deployment execution |
| Business Owner | Product Owner | po@company.com | Business sign-off |

---

## 11. Approval

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Dev Lead | | | ☐ Approved |
| QA Lead | | | ☐ Approved |
| Business Owner | | | ☐ Approved |
| Release Manager | | | ☐ Approved |
