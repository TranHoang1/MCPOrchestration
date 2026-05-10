package com.orchestrator.mcp.brmasking.prompt

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V

/**
 * LangChain4j AiService interface for identifying individual business rules
 * and categorizing them within financial document content.
 */
interface BrIdentificationAiService {

    @SystemMessage(
        """You are a Business Rules Identifier for financial documents.
Analyze the text and identify individual business rules.

Output MUST be a valid JSON array:
[
  {"text": "exact original BR text", "category": "RATE|APPROVAL|THRESHOLD|PROCESS|COMMISSION", "summary": "1-line non-sensitive summary"}
]

Categories:
- RATE: Interest rates, fees, commissions, pricing formulas, specific numbers
- APPROVAL: Conditions for approval/rejection, scoring criteria, eligibility rules
- THRESHOLD: Risk limits, NPL thresholds, exposure limits, cutoff values
- PROCESS: Workflow steps, SLA definitions, escalation rules, procedures
- COMMISSION: Agent/broker commissions, referral fees, bonus structures

Rules:
- Each distinct rule should be a separate entry
- "text" must be the EXACT substring from the input
- "summary" must NOT contain sensitive numbers or specific values
- If no business rules found, return empty array: []"""
    )
    @UserMessage("Identify business rules in:\n\n{{content}}")
    fun identify(@V("content") content: String): String
}
