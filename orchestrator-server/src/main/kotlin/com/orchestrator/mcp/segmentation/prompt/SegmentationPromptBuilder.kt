package com.orchestrator.mcp.segmentation.prompt

/**
 * Builds few-shot examples for the segmentation prompt.
 * Used to enhance LLM classification accuracy with domain-specific examples.
 */
class SegmentationPromptBuilder {

    /**
     * Builds a user message with optional few-shot examples prepended.
     */
    fun buildUserMessage(maskedText: String, includeFewShot: Boolean = true): String {
        val prefix = if (includeFewShot) buildFewShotExamples() else ""
        return "${prefix}Classify the following masked text:\n\n$maskedText"
    }

    private fun buildFewShotExamples(): String = buildString {
        appendLine("Examples:")
        appendLine()
        appendLine("Input: \"Ticket PROJ-123 assigned to [PII_NAME_01] on 2024-01-15\"")
        appendLine("""Output: {"publicContent":"Ticket PROJ-123 assigned to [PII_NAME_01] on 2024-01-15","technicalContent":"","businessRules":"","brSensitivityLevel":null}""")
        appendLine()
        appendLine("Input: \"NullPointerException at line 42 in PaymentService.kt\"")
        appendLine("""Output: {"publicContent":"","technicalContent":"NullPointerException at line 42 in PaymentService.kt","businessRules":"","brSensitivityLevel":null}""")
        appendLine()
        appendLine("Input: \"Lãi suất cho vay tiêu dùng: 12.5%/năm, phí trả nợ trước hạn: 3%\"")
        appendLine("""Output: {"publicContent":"","technicalContent":"","businessRules":"Lãi suất cho vay tiêu dùng: 12.5%/năm, phí trả nợ trước hạn: 3%","brSensitivityLevel":"LEVEL_1"}""")
        appendLine()
    }

    companion object {
        /** Maximum input length before truncation. */
        const val MAX_INPUT_LENGTH = 10_000
    }
}
