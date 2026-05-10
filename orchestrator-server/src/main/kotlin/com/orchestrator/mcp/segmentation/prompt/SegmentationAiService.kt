package com.orchestrator.mcp.segmentation.prompt

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V

/**
 * LangChain4j AiService interface for content classification.
 * Framework auto-implements this interface using annotated prompts.
 */
interface SegmentationAiService {

    @SystemMessage(
        """You are a Financial Domain Content Classifier. Analyze text from 
financial institution tickets and classify into exactly 3 categories.

Output MUST be valid JSON with this exact structure:
{
  "publicContent": "...",
  "technicalContent": "...",
  "businessRules": "...",
  "brSensitivityLevel": "LEVEL_1" | "LEVEL_2" | "LEVEL_3" | null
}

Classification rules:
- publicContent: Ticket metadata (ID, summary, dates, status, labels, assignee, priority)
- technicalContent: Code, logs, stack traces, configs, SQL, error messages, architecture
- businessRules: Interest rates, loan conditions, risk thresholds, fees, SLAs, processes

Sensitivity levels for businessRules:
- LEVEL_1 (Confidential): Specific numbers for rates, fees, commissions, pricing formulas
- LEVEL_2 (Internal): Conditions, thresholds, scoring criteria, approval rules
- LEVEL_3 (Restricted): General processes, SLAs, workflow definitions
- null: When no business rules are found

If multiple sensitivity levels apply, use the MOST RESTRICTIVE (lowest number)."""
    )
    @UserMessage("Classify the following masked text:\n\n{{maskedText}}")
    fun classify(@V("maskedText") maskedText: String): String
}
