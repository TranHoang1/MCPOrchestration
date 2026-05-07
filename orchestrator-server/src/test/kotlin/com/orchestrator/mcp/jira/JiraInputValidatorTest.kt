package com.orchestrator.mcp.jira

import com.orchestrator.mcp.jira.exception.JiraValidationException
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain

/**
 * Unit tests for JiraInputValidator.
 * STC: TC-300 to TC-320 (Business Rule Validation)
 */
class JiraInputValidatorTest : FunSpec({

    val correlationId = "test-correlation"

    context("validateSearchParams") {
        test("TC-300: valid params pass validation") {
            shouldNotThrow<JiraValidationException> {
                JiraInputValidator.validateSearchParams("project = MTO", listOf("summary"), 0, 50, correlationId)
            }
        }

        test("TC-301: blank JQL throws validation exception") {
            val ex = shouldThrow<JiraValidationException> {
                JiraInputValidator.validateSearchParams("", emptyList(), 0, 50, correlationId)
            }
            ex.message shouldContain "JQL must not be blank"
        }

        test("TC-302: negative startAt throws validation exception") {
            val ex = shouldThrow<JiraValidationException> {
                JiraInputValidator.validateSearchParams("project = MTO", emptyList(), -1, 50, correlationId)
            }
            ex.message shouldContain "startAt"
        }

        test("TC-303: maxResults 0 throws validation exception") {
            shouldThrow<JiraValidationException> {
                JiraInputValidator.validateSearchParams("project = MTO", emptyList(), 0, 0, correlationId)
            }
        }

        test("TC-304: maxResults > 100 throws validation exception") {
            shouldThrow<JiraValidationException> {
                JiraInputValidator.validateSearchParams("project = MTO", emptyList(), 0, 101, correlationId)
            }
        }
    }

    context("validateIssueKey") {
        test("TC-305: valid issue key passes") {
            shouldNotThrow<JiraValidationException> {
                JiraInputValidator.validateIssueKey("MTO-16", correlationId)
            }
        }

        test("TC-306: lowercase key throws") {
            shouldThrow<JiraValidationException> {
                JiraInputValidator.validateIssueKey("mto-16", correlationId)
            }
        }

        test("TC-307: missing number throws") {
            shouldThrow<JiraValidationException> {
                JiraInputValidator.validateIssueKey("MTO-", correlationId)
            }
        }

        test("TC-308: empty key throws") {
            shouldThrow<JiraValidationException> {
                JiraInputValidator.validateIssueKey("", correlationId)
            }
        }
    }

    context("validateExpand") {
        test("TC-309: valid expand values pass") {
            shouldNotThrow<JiraValidationException> {
                JiraInputValidator.validateExpand(listOf("changelog", "renderedFields"), correlationId)
            }
        }

        test("TC-310: invalid expand value throws") {
            val ex = shouldThrow<JiraValidationException> {
                JiraInputValidator.validateExpand(listOf("changelog", "invalid"), correlationId)
            }
            ex.message shouldContain "invalid"
        }
    }

    context("validateDownloadUrl") {
        test("TC-311: matching domain passes") {
            shouldNotThrow<JiraValidationException> {
                JiraInputValidator.validateDownloadUrl(
                    "https://mycompany.atlassian.net/rest/api/3/attachment/content/123",
                    "https://mycompany.atlassian.net",
                    correlationId
                )
            }
        }

        test("TC-312: mismatched domain throws SSRF exception") {
            val ex = shouldThrow<JiraValidationException> {
                JiraInputValidator.validateDownloadUrl(
                    "https://evil.com/steal-data",
                    "https://mycompany.atlassian.net",
                    correlationId
                )
            }
            ex.message shouldContain "SSRF"
        }
    }
})
