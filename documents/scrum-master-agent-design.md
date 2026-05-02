# Scrum Master Agent — Design Document

## 1. Mục đích

Scrum Master Agent (`sm-agent`) là **entry point duy nhất** để user tương tác với pipeline multi-agent. Thay vì user phải biết gọi agent nào cho việc gì, user chỉ cần nói với SM và SM sẽ tự điều phối.

## 2. Nguyên tắc thiết kế

1. **Một entry point** — User chỉ cần gọi `/sm-agent` cho mọi việc
2. **Context-aware** — SM biết ticket đang ở phase nào, documents nào đã có (via STATUS.json + KB)
3. **Quality gates** — SM không chuyển phase nếu chưa pass quality check
4. **Feedback loops** — SM tự chạy BA↔SA loop (max 5 vòng)
5. **Transparent** — SM báo user đang làm gì, ở phase nào, kết quả ra sao
6. **User vẫn là người quyết định** — SM đề xuất, user approve trước khi chuyển phase lớn
7. **Knowledge Base first** — Tất cả agents đọc/ghi qua KB để tối ưu context window
8. **Code Intelligence aware** — Agents đọc codebase analysis trước khi tạo documents

## 3. SDLC Phases & Agent Mapping

```
Phase 1:   Requirements    → ba-agent              → BRD.md
Phase 2:   Specification   → ba-agent + ta-agent   → FSD.md (BA draft + TA enrich)
Phase 2.5: UI Design       → ui-agent (nếu có UI)  → Wireframes, Stitch screens
Phase 3:   Design          → sa-agent              → TDD.md (đọc code + DB)
Phase 3.5: Feedback Loop   → ba↔sa                 → FSD fix ↔ discrepancy report (max 5 vòng)
Phase 4:   Test Planning   → qa-agent              → STP.md + STC.md
Phase 5:   Implementation  → dev-agent             → Source code + code index update
Phase 6:   Testing         → qa-agent              → Test execution
Phase 6.5: UAT             → PO/User               → Acceptance sign-off
Phase 7:   Deployment      → devops-agent           → DPG.md + RLN.md + Deploy
```

## 4. Agent Data Access Matrix

### Write (Output → KB)

| Agent | Document Output | KB Ingest | Code Index |
|-------|----------------|-----------|------------|
| **BA** | BRD.md, FSD.md (draft) | ✅ BRD + FSD | ❌ |
| **TA** | FSD.md (enriched) | ✅ FSD (updated) | ❌ |
| **UI** | Wireframes, Stitch screens | ✅ UI design summary | ❌ |
| **SA** | TDD.md | ✅ TDD | ❌ |
| **QA** | STP.md, STC.md | ✅ STP + STC | ❌ |
| **DEV** | Source code | ✅ Implementation summary | ✅ Code intelligence index |
| **DevOps** | DPG.md, RLN.md | ✅ DPG + RLN | ❌ |

### Read (Input Sources)

| Agent | Read from KB | Read Code Intelligence | Read Source Code | Read DB |
|-------|-------------|----------------------|-----------------|---------|
| **BA** | ✅ BRD (khi tạo FSD) | ✅ project-structure + modules | ❌ | ❌ |
| **TA** | ✅ BRD | ✅ project-structure + modules | ❌ | ❌ |
| **UI** | ✅ BRD + FSD | ✅ frontend module | ✅ (existing pages, CSS, components) | ❌ |
| **SA** | ✅ BRD + FSD | ✅ all relevant modules | ✅ (deep-dive source files) | ✅ (actual schema) |
| **QA** | ✅ BRD + FSD + TDD | ❌ | ❌ | ❌ |
| **DEV** | ✅ TDD + FSD + BRD | ✅ project-structure + modules | ✅ (existing patterns) | ❌ |
| **DevOps** | ✅ TDD + FSD + BRD | ❌ | ✅ (scan configs) | ❌ |

## 5. Status Tracking

SM track trạng thái mỗi ticket qua file `documents/{TICKET}/STATUS.json`:

```json
{
  "ticket": "MTO-5",
  "currentPhase": "design",
  "phases": {
    "requirements": { "status": "done", "file": "BRD.md", "version": 1, "completedAt": "..." },
    "specification": { "status": "done", "file": "FSD.md", "version": 1, "completedAt": "..." },
    "ui_design": { "status": "skipped", "reason": "No UI components" },
    "design": { "status": "not_started", "file": "TDD.md", "version": null },
    "feedback_loop": { "status": "not_started", "iterations": 0, "maxIterations": 5 },
    "test_planning": { "status": "not_started" },
    "implementation": { "status": "not_started" },
    "testing": { "status": "not_started" },
    "deployment": { "status": "not_started" }
  },
  "lastUpdated": "2026-05-01T17:30:00Z"
}
```

## 6. User Interaction Patterns

### Pattern 1: Full pipeline
```
User: "MTO-5"
SM: Kiểm tra status → chưa có gì → bắt đầu từ Phase 1
SM: "📋 MTO-5 — Bắt đầu tạo tài liệu. Phase 1: Requirements (BA Agent)..."
SM: Gọi BA → BRD done → ingest KB
SM: "✅ BRD done. Chuyển sang Phase 2: Specification?"
User: "OK"
SM: Gọi BA (draft) → FSD draft done
SM: Gọi TA (enrich) → FSD enriched → ingest KB
SM: Kiểm tra FSD có UI specs? → Không → skip Phase 2.5
SM: "✅ FSD done (BA draft + TA enrichment). Chuyển sang Phase 3: Design?"
User: "OK"
SM: Gọi SA → TDD done + discrepancy report
SM: "⚠️ SA phát hiện 3 discrepancies. Chạy feedback loop..."
SM: Tự chạy BA fix → SA review → lặp cho đến hết
SM: "✅ FSD v2 + TDD v2 consistent. Chuyển sang Phase 4?"
...
```

### Pattern 2: Resume từ giữa
```
User: "MTO-5"
SM: Kiểm tra status → BRD done, FSD done, TDD chưa có
SM: "📋 MTO-5 — BRD ✅, FSD ✅, TDD chưa có. Tiếp tục Phase 3: Design?"
User: "OK"
SM: Gọi SA → ...
```

### Pattern 3: Chỉ một phase
```
User: "Tạo TDD cho MTO-5"
SM: Kiểm tra prerequisites → FSD phải có trước
SM: FSD exists → gọi SA trực tiếp
```

### Pattern 4: Redo một phase
```
User: "Tạo lại FSD cho MTO-5"
SM: Gọi BA tạo lại FSD → TA enrich → sau đó tự trigger SA review (vì TDD đã có, cần update)
```

### Pattern 5: Ticket có UI
```
User: "COLLEX-64"
SM: Phase 2 done → FSD có Section 3.x.5 (UI Specifications)
SM: "FSD có UI specs. Chạy Phase 2.5: UI Design?"
User: "OK"
SM: Gọi UI agent → wireframes + Stitch screens → embed vào FSD
SM: "✅ UI Design done. Chuyển sang Phase 3?"
```

## 7. Quality Gates

| Gate | Condition | Action nếu fail |
|------|-----------|-----------------|
| → Phase 2 | BRD phải tồn tại (file hoặc KB) | SM gọi BA tạo BRD trước |
| → Phase 2.5 | FSD phải có UI Specifications section | Skip Phase 2.5 nếu không có UI |
| → Phase 3 | FSD phải tồn tại (file hoặc KB) | SM gọi BA+TA tạo FSD trước |
| TDD → done | Không còn Critical/High discrepancy | SM chạy feedback loop |
| → Phase 5 | TDD phải tồn tại, Jira status = IN PROGRESS | SM đợi hoặc transition |
| → Phase 6 | Code compile, unit tests pass | SM báo DEV fix |
| → Phase 7 | All test cases pass, UAT accepted | SM báo QA/DEV fix |

## 8. Feedback Loops

### Loop 1: BA ↔ SA (Document consistency)
- **Trigger**: SA tạo `DISCREPANCY.md`
- **Process**: BA fix FSD → SA review → lặp
- **Exit**: Không còn discrepancy HOẶC max 5 vòng
- **SM role**: Tự động chạy loop, báo user progress

### Loop 2: DEV ↔ QA (Code quality)
- **Trigger**: QA phát hiện bugs trong testing
- **Process**: DEV fix → QA retest → lặp
- **Exit**: All tests pass
- **SM role**: Tự động chạy loop, báo user progress

## 9. Agents được SM điều phối

| Agent | File | Khi nào SM gọi | Input | Output |
|-------|------|----------------|-------|--------|
| ba-agent | `.kiro/agents/ba-agent.md` | Phase 1, 2 (draft), feedback loop | Jira ticket, KB, code intelligence | BRD.md, FSD.md (draft) |
| ta-agent | `.kiro/agents/ta-agent.md` | Phase 2 (enrich) | FSD draft, BRD (KB), code intelligence | FSD.md (enriched) |
| ui-agent | `.kiro/agents/ui-agent.md` | Phase 2.5 (nếu có UI) | FSD, BRD (KB), frontend source code | Wireframes, Stitch screens |
| sa-agent | `.kiro/agents/sa-agent.md` | Phase 3, feedback loop | BRD+FSD (KB), code intelligence, source code, DB | TDD.md, DISCREPANCY.md |
| qa-agent | `.kiro/agents/qa-agent.md` | Phase 4, 6 | BRD+FSD+TDD (KB) | STP.md, STC.md, test results |
| dev-agent | `.kiro/agents/dev-agent.md` | Phase 5 | TDD+FSD+BRD (KB), code intelligence | Source code, code index |
| devops-agent | `.kiro/agents/devops-agent.md` | Phase 7 | TDD+FSD+BRD (KB), source configs | DPG.md, RLN.md |

## 10. Knowledge Base Integration

### Nguyên tắc

- **Mỗi agent tạo document xong → ingest vào KB** (với tags: doc type, ticket key, project key)
- **Mỗi agent đọc document → search KB trước, fallback file** (giảm context window)
- **DEV tạo code xong → update code intelligence index** (cho SA, QA, DevOps dùng)

### KB Tags Convention

| Document | Tags |
|----------|------|
| BRD | `brd, {TICKET}, {PROJECT}, requirements, sdlc` |
| FSD | `fsd, {TICKET}, {PROJECT}, specification, sdlc` |
| TDD | `tdd, {TICKET}, {PROJECT}, design, architecture, sdlc` |
| STP | `stp, {TICKET}, {PROJECT}, test-plan, qa, sdlc` |
| STC | `stc, {TICKET}, {PROJECT}, test-cases, qa, sdlc` |
| DPG | `dpg, {TICKET}, {PROJECT}, deployment, devops, sdlc` |
| RLN | `rln, {TICKET}, {PROJECT}, release-notes, devops, sdlc` |
| UI Design | `ui-design, {TICKET}, {PROJECT}, mockup, wireframe, sdlc` |
| Implementation | `implementation, {TICKET}, {PROJECT}, code, sdlc` |
| Draw.io Diagram | `drawio, diagram, {diagram-type}, {TICKET}, {PROJECT}` |

### KB Content Rules

- **ALL `.md` files**: Ingest **FULL content** — DO NOT SUMMARIZE
- **ALL `.drawio` files**: Ingest **FULL XML content** — AI agents parse XML to understand diagram structure
- **Reason**: Downstream agents read from KB to avoid loading large files into context. If KB has summaries instead of full content, agents must fallback to file reads → defeats the purpose

### SM Verification

- **Before invoking agent**: Verify required KB documents exist
- **After agent completes**: Verify output document AND drawio files have been ingested into KB
- **If KB ingest missing**: Ask agent to ingest before marking phase as done

## 11. Jira Integration

### Transition Timing

| Khi nào | Jira Transition |
|---------|----------------|
| Phase 1 bắt đầu | TO DO → DOCS REVIEW |
| Tài liệu approved, DEV bắt đầu | DOCS REVIEW → IN PROGRESS |
| DEV submit PR | IN PROGRESS → IN REVIEW |
| Code review approved | IN REVIEW → QA TEST |
| QA tests pass | QA TEST → UAT |
| PO accepts UAT | UAT → READY FOR PRODUCT |
| Deploy + sanity pass | READY FOR PRODUCT → DONE |

### Document Attachment

- Attach DOCX versions to Jira ticket sau Phase 3 (BRD, FSD, TDD)
- Attach STP/STC sau Phase 4
- Attach DPG/RLN sau Phase 7
- File naming: `{DOC}-v{version}-{TICKET}.docx`

## 12. Điều SM KHÔNG làm

- SM **không tự viết documents** — chỉ gọi agents
- SM **không tự viết code** — chỉ gọi dev-agent
- SM **không quyết định thay user** — luôn hỏi trước khi chuyển phase lớn
- SM **không skip quality gates** — nếu prerequisite thiếu, phải tạo trước
- SM **không skip KB ingest** — verify mỗi agent đã ingest output vào KB

## 13. Implementation Notes

- SM agent file: `.kiro/agents/sm-agent.md`
- Status file: `documents/{TICKET}/STATUS.json`
- Workflow docs: `documents/workflows/{PROJECT}-workflows.md`
- SM dùng `invokeSubAgent` để gọi các agents khác
- SM dùng `mcp_knowledge_base_kb_search` để verify KB state
- SM dùng `mcp_jira_*` tools để transition Jira tickets
- SM dùng `fsWrite` để update STATUS.json
