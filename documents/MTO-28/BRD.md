# Business Requirements Document (BRD)

## MCPOrchestration — MTO-28: KB Refinery — LangChain4j Content Segmentation

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-28 |
| Title | KB Refinery — LangChain4j Content Segmentation |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Parent Epic | MTO-24 (Knowledge Base Refinery) |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | BA Agent – Business Analyst | Create document |
| Peer Reviewer | SA Agent – Solution Architect | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | BA Agent | Initiate document — auto-generated from Jira ticket MTO-28 |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |

---

## 1. Introduction

### 1.1 Scope

This document defines the business requirements for integrating LangChain4j into the KB Refinery pipeline to perform intelligent content segmentation. The system will classify masked text (output from PII Masking Engine — MTO-27) into three distinct content layers: Public Metadata, Technical Content, and Business Rules. Each layer receives appropriate sensitivity classification to enable downstream processing with proper access controls.

### 1.2 Out of Scope

- PII detection and masking (handled by MTO-27)
- KbEntry model definition (handled by MTO-26)
- Knowledge Base storage and retrieval
- Vector embedding generation
- UI/Frontend for content segmentation
- LLM model training or fine-tuning

### 1.3 Preliminary Requirements

| Prerequisite | Ticket | Status |
|-------------|--------|--------|
| KbEntry data model defined | MTO-26 | Required |
| PII Masking Engine providing masked text input | MTO-27 | Required |
| LangChain4j library available in project dependencies | — | Required |
| At least one LLM provider configured (OpenAI/Ollama/Azure) | — | Required |

---

## 2. Business Requirements

### 2.1 High Level Process Map

The Content Segmentation Service sits in the KB Refinery pipeline between PII Masking (MTO-27) and Knowledge Base storage. It receives masked text and classifies content into three layers, each with appropriate sensitivity levels. This enables the system to:

1. Store public metadata openly for search and discovery
2. Protect technical content with standard access controls
3. Apply strict access controls to business rules based on sensitivity level

### 2.2 List of User Stories / Use Cases

| # | Story / Use Case | Priority | Source Ticket |
|---|-----------------|----------|---------------|
| 1 | As a KB Refinery pipeline, I want to segment masked text into Public Metadata, Technical Content, and Business Rules so that each content type receives appropriate access controls | MUST HAVE | MTO-28 |
| 2 | As a system administrator, I want to configure which LLM provider (OpenAI/Ollama/Azure) is used for segmentation so that I can balance cost, privacy, and performance | MUST HAVE | MTO-28 |
| 3 | As a security officer, I want Business Rules content to be classified by sensitivity level (Confidential/Internal/Restricted) so that access controls match the content's risk profile | MUST HAVE | MTO-28 |
| 4 | As a system administrator, I want to enforce local-only LLM processing for Business Rules when configured so that sensitive financial rules never leave the organization's infrastructure | MUST HAVE | MTO-28 |
| 5 | As a developer, I want the segmentation service to use LangChain4j AiService pattern so that prompt management and LLM interaction follow established patterns | SHOULD HAVE | MTO-28 |

---

### 2.3 Details of User Stories

---

#### Business Flow

**Step 1:** KB Refinery pipeline receives a Jira ticket's content (already PII-masked by MTO-27)

**Step 2:** Masked text is passed to ContentSegmentationService

**Step 3:** Service builds a classification prompt using SegmentationPromptBuilder with few-shot examples

**Step 4:** Prompt is sent to configured LLM provider via LangChain4j AiService

**Step 5:** LLM returns structured JSON with three content sections + BR sensitivity level

**Step 6:** Service parses response into SegmentationResult data class

**Step 7:** Result is returned to pipeline for downstream processing (storage with access controls)

> **Note:** If Business Rules content is detected AND configuration specifies local-only mode, the service MUST use local LLM (Ollama) regardless of the default provider setting.

---

#### STORY 1: Content Segmentation

> As a KB Refinery pipeline, I want to segment masked text into Public Metadata, Technical Content, and Business Rules so that each content type receives appropriate access controls

**Requirement Details:**

1. The service receives masked text (String) as input — text has already been processed by PiiMaskingEngine (MTO-27)
2. The service classifies content into exactly three categories:
   - **Public Metadata**: Basic ticket information (ID, Summary, creation date, status, labels, assignee)
   - **Technical Content**: System logs, Java/SQL code snippets, configuration files, stack traces, error messages, technical architecture descriptions
   - **Business Rules**: Interest rate formulas, loan approval conditions, risk thresholds, branching process rules, commission rates, SLA definitions
3. The service returns a SegmentationResult containing all three content sections plus a BR sensitivity level

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| maskedText | String | Yes | PII-masked text from MTO-27 | "Ticket [PERSON_1] reported: interest rate formula is base + 2.5%..." |
| publicContent | String | Yes | Extracted public metadata | "ID: MTO-100, Status: Open, Labels: [finance, loan]" |
| technicalContent | String | Yes | Extracted technical content | "Stack trace: NullPointerException at LoanService.java:45..." |
| businessRules | String | Yes | Extracted business rules | "Interest rate = base_rate + risk_premium (2.5% for category A)" |
| brSensitivityLevel | Enum | Yes | Sensitivity classification for BR content | Level 1 (Confidential) |

**Acceptance Criteria:**

1. Given masked text containing mixed content, when segmentation is performed, then the result contains non-empty publicContent, technicalContent, and businessRules sections
2. Given masked text with no business rules, when segmentation is performed, then businessRules is empty and brSensitivityLevel is null
3. Given masked text with interest rate formulas, when segmentation is performed, then brSensitivityLevel is Level 1 (Confidential)
4. Segmentation completes within 10 seconds for text up to 10,000 characters
5. The service handles LLM timeout gracefully with appropriate error response

---

#### STORY 2: LLM Provider Configuration

> As a system administrator, I want to configure which LLM provider is used for segmentation so that I can balance cost, privacy, and performance

**Requirement Details:**

1. Support three LLM providers: OpenAI, Ollama (local), Azure OpenAI
2. Configuration via YAML (application.yml) with the following properties:
   - Provider selection (openai / ollama / azure)
   - Model name (e.g., gpt-4o-mini, llama3, gpt-4o)
   - Temperature (0.0 - 1.0, default 0.1 for classification tasks)
   - Max tokens (default 2000)
   - API key (for cloud providers)
   - Base URL (for Ollama or custom endpoints)
3. Provider can be changed without code modification (config-only change)

**Acceptance Criteria:**

1. Given OpenAI is configured, when segmentation runs, then requests go to OpenAI API
2. Given Ollama is configured with local URL, when segmentation runs, then requests go to local Ollama instance
3. Given invalid provider configuration, when service starts, then a clear error message is logged and service fails fast
4. Configuration supports environment variable substitution for secrets (API keys)

---

#### STORY 3: BR Sensitivity Level Classification

> As a security officer, I want Business Rules content to be classified by sensitivity level so that access controls match the content's risk profile

**Requirement Details:**

1. Three sensitivity levels for Business Rules:
   - **Level 1 (Confidential)**: Interest rate formulas, fee percentages, commission rates, pricing algorithms
   - **Level 2 (Internal)**: Loan approval conditions, risk thresholds, branching process rules, scoring criteria
   - **Level 3 (Restricted)**: General processes, internal SLAs, workflow definitions, standard operating procedures
2. Classification is performed by the LLM as part of the segmentation prompt
3. Few-shot examples in the prompt guide the LLM to classify correctly

**Acceptance Criteria:**

1. Given text containing "lãi suất = base + 2.5%", when classified, then brSensitivityLevel = Level 1
2. Given text containing "điều kiện duyệt vay: thu nhập >= 10 triệu", when classified, then brSensitivityLevel = Level 2
3. Given text containing "SLA xử lý hồ sơ: 3 ngày làm việc", when classified, then brSensitivityLevel = Level 3
4. Given text with no business rules, when classified, then brSensitivityLevel = null

---

#### STORY 4: Local-Only LLM Enforcement for Business Rules

> As a system administrator, I want to enforce local-only LLM processing for Business Rules when configured so that sensitive financial rules never leave the organization's infrastructure

**Requirement Details:**

1. Configuration flag: `segmentation.br-local-only: true/false`
2. When `br-local-only = true`:
   - Initial segmentation can use any configured provider
   - If Business Rules content is detected in the result, the service verifies the provider used
   - If a cloud provider was used AND br-local-only is true, the service re-processes ONLY the BR section using local Ollama
3. When `br-local-only = false`: No additional processing, use configured provider for everything

**Acceptance Criteria:**

1. Given br-local-only=true and cloud provider configured, when BR content is detected, then BR section is re-processed via local Ollama
2. Given br-local-only=true and Ollama configured as default, when segmentation runs, then no re-processing needed (already local)
3. Given br-local-only=false, when segmentation runs, then all content processed by default provider regardless of content type

---

#### STORY 5: LangChain4j AiService Pattern

> As a developer, I want the segmentation service to use LangChain4j AiService pattern so that prompt management and LLM interaction follow established patterns

**Requirement Details:**

1. Define a LangChain4j AiService interface with `@SystemMessage` and `@UserMessage` annotations
2. System prompt defines the role as "Financial domain content classifier"
3. User prompt provides the masked text with classification instructions
4. Output format: Structured JSON with three sections + sensitivity level
5. Include few-shot examples in the system prompt for accurate classification
6. Use LangChain4j's built-in JSON output parsing

**Acceptance Criteria:**

1. The AiService interface is properly annotated with LangChain4j annotations
2. System prompt clearly defines the classifier role and output format
3. Few-shot examples cover all three content types and all three sensitivity levels
4. JSON output is automatically parsed into SegmentationResult by LangChain4j

---

## 3. Dependencies

| Dependency | Type | Related Ticket | Description |
|------------|------|----------------|-------------|
| KbEntry Model | System | MTO-26 | Data model for KB entries — SegmentationResult maps to KbEntry fields |
| PII Masking Engine | System | MTO-27 | Provides masked text input to segmentation service |
| LangChain4j Library | External | — | AI/LLM integration framework for Java/Kotlin |
| OpenAI API | External | — | Cloud LLM provider (optional) |
| Ollama | Infrastructure | — | Local LLM runtime (required for br-local-only mode) |
| Azure OpenAI | External | — | Alternative cloud LLM provider (optional) |

---

## 4. Stakeholders

| Role | Name / Team | Responsibility | Source |
|------|-------------|----------------|--------|
| Product Owner | Duc Nguyen | Define requirements, approve deliverables | Reporter |
| Solution Architect | SA Agent | Technical design and review | — |
| Developer | DEV Agent | Implementation | — |
| QA | QA Agent | Testing and verification | — |

---

## 5. Risks and Assumptions

### 5.1 Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| LLM classification accuracy below acceptable threshold | High | Medium | Use few-shot examples, temperature=0.1, validate output format |
| LLM latency exceeds 10s timeout | Medium | Low | Configure timeout, implement retry with backoff, cache common patterns |
| Ollama not available when br-local-only=true | High | Low | Health check on startup, clear error message, fallback strategy |
| LLM provider API changes break integration | Medium | Low | Use LangChain4j abstraction layer, pin library version |
| Cost overrun from excessive LLM API calls | Medium | Medium | Implement token counting, batch processing, caching |

### 5.2 Assumptions

- PII masking (MTO-27) is complete and provides clean masked text
- KbEntry model (MTO-26) is defined and available
- At least one LLM provider will be configured in the deployment environment
- LangChain4j supports Kotlin coroutines or can be wrapped in suspend functions
- The financial domain vocabulary (Vietnamese) is understood by the configured LLM

---

## 6. Non-Functional Requirements

| Category | Requirement | Details |
|----------|-------------|---------|
| Performance | Segmentation completes within 10 seconds | For text up to 10,000 characters |
| Performance | Token usage optimized | Prompt + response < 4000 tokens per call |
| Reliability | Graceful degradation on LLM failure | Return error result, don't crash pipeline |
| Security | BR content never sent to cloud when br-local-only=true | Enforced at service level |
| Security | API keys stored securely | Environment variables, not in config files |
| Configurability | Provider switchable without code change | YAML configuration only |
| Testability | Service testable without real LLM | Interface-based design, mockable |
| Maintainability | File ≤ 200 lines, Function ≤ 20 lines | SOLID principles, clean code |

---

## 7. Related Tickets

| Ticket Key | Summary | Status | Type | Relationship |
|------------|---------|--------|------|--------------|
| MTO-28 | KB Refinery — LangChain4j Content Segmentation | Docs Review | Story | Main ticket |
| MTO-24 | Knowledge Base Refinery | — | Epic | Parent Epic |
| MTO-26 | KB Refinery — KbEntry Model | — | Story | Dependency (data model) |
| MTO-27 | KB Refinery — PII Masking Engine | — | Story | Dependency (input provider) |

---

## 8. Appendix

### Glossary

| Term | Definition |
|------|------------|
| Content Segmentation | Process of classifying text into distinct content categories |
| Public Metadata | Non-sensitive ticket information (ID, status, labels) |
| Technical Content | System-related content (code, logs, configs, stack traces) |
| Business Rules | Domain-specific rules (formulas, conditions, thresholds) |
| BR Sensitivity Level | Classification of business rules by confidentiality (Level 1-3) |
| LangChain4j | Java/Kotlin framework for LLM integration |
| AiService | LangChain4j pattern for declarative LLM interaction via annotated interfaces |
| PII Masking | Process of replacing personally identifiable information with placeholders |
| Ollama | Local LLM runtime for running models on-premise |
| Few-shot Examples | Sample input/output pairs included in prompts to guide LLM behavior |

### Reference Documents

| Document | Link / Location |
|----------|-----------------|
| LangChain4j Documentation | https://docs.langchain4j.dev/ |
| MTO-26 BRD | documents/MTO-26/BRD.md |
| MTO-27 BRD | documents/MTO-27/BRD.md |
| Project Structure | .analysis/code-intelligence/project-structure.md |

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Business Flow | [business-flow.png](diagrams/business-flow.png) | [business-flow.drawio](diagrams/business-flow.drawio) |
| 2 | Use Case Diagram | [use-case.png](diagrams/use-case.png) | [use-case.drawio](diagrams/use-case.drawio) |
