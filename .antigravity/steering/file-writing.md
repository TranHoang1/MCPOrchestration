---
name: file-writing
description: Quy tắc viết file lớn cho tất cả agents. Áp dụng khi tạo documents (BRD, FSD, TDD, STP, STC, UG) hoặc bất kỳ file nào > 200 dòng.
inclusion: auto
---

# File Writing Standards for All Agents

## ⛔ CRITICAL: Quy tắc viết documents lớn (> 200 dòng)

### Vấn đề

Khi agent viết document lớn (FSD ~1300 dòng, TDD ~800 dòng), việc truyền toàn bộ content trong 1 lần gọi tool có thể bị **truncated** (cắt giữa chừng) do giới hạn output size. Điều này dẫn đến:
- Tool nhận được object thiếu field `content` → error
- Agent retry với cùng payload → lặp lại lỗi
- Mất thời gian và token vô ích

### Quy tắc bắt buộc

#### 1. KHÔNG BAO GIỜ viết toàn bộ document lớn trong 1 lần gọi tool

**⚠️ LUÔN DÙNG `stream_write_file` (MCP tool) để ghi documents.**

`stream_write_file` được thiết kế đặc biệt để thay thế `fsWrite/fsAppend`. KHÔNG dùng `fsWrite/fsAppend` cho documents.

**❌ SAI — Gọi 1 lần với toàn bộ content:**
```
stream_write_file(file_path="...", content="<entire 1300-line document>")
```

**✅ ĐÚNG — Chia thành chunks nhỏ bằng stream_write_file:**
```
# Chunk 1: Header + Section 1-2 (mode: write — tạo file mới)
stream_write_file(file_path="C:/projects/kotlin/MCPOrchestration/documents/{TICKET}/{DOC}.md", content="<sections 1-2>", mode="write")

# Chunk 2: Section 3.1-3.3 (mode: append)
stream_write_file(file_path="C:/projects/kotlin/MCPOrchestration/documents/{TICKET}/{DOC}.md", content="<sections 3.1-3.3>", mode="append")

# Chunk 3: Section 3.4-3.7 (mode: append)
stream_write_file(file_path="C:/projects/kotlin/MCPOrchestration/documents/{TICKET}/{DOC}.md", content="<sections 3.4-3.7>", mode="append")

# ... tiếp tục cho đến hết document
```

**⛔ KHÔNG dùng `fsWrite/fsAppend`** — Các tool này có giới hạn và không phải mục đích thiết kế cho việc ghi documents lớn. `stream_write_file` được tạo ra để thay thế chúng.

#### 2. Giới hạn kích thước mỗi chunk

| Tool | Max content per call | Ghi chú |
|------|---------------------|---------|
| `stream_write_file` | ≤ 4000 characters | Dùng `mode: "write"` cho chunk đầu, `mode: "append"` cho chunks sau |
| `fsWrite` | ≤ 50 dòng | Chỉ dùng cho chunk đầu tiên (tạo file) |
| `fsAppend` | ≤ 4000 characters | Dùng cho tất cả chunks sau chunk đầu |

#### 3. Pattern chuẩn cho document lớn

```
# Bước 1: Tạo file với header + section đầu tiên
stream_write_file(
  file_path = "C:/projects/kotlin/MCPOrchestration/documents/{TICKET}/{DOC}.md",
  content = "<Document header + Section 1 Introduction>",
  mode = "write"
)

# Bước 2-N: Append từng section
stream_write_file(
  file_path = "C:/projects/kotlin/MCPOrchestration/documents/{TICKET}/{DOC}.md",
  content = "<Section 2 System Overview>",
  mode = "append"
)

stream_write_file(
  file_path = "...",
  content = "<Section 3.1 Feature A>",
  mode = "append"
)

# ... tiếp tục
```

#### 4. Fallback strategy

Nếu `stream_write_file` fail (bất kỳ lý do gì):
1. **ĐỌC ERROR MESSAGE** — phân tích nguyên nhân cụ thể
2. **KHÔNG retry với cùng payload** — nếu lỗi là thiếu content, nghĩa là content quá lớn bị truncated
3. **Chuyển sang fsWrite + fsAppend** ngay lập tức:
   ```
   fsWrite(path="documents/{TICKET}/{DOC}.md", text="<chunk 1 — max 50 lines>")
   fsAppend(path="documents/{TICKET}/{DOC}.md", text="<chunk 2>")
   fsAppend(path="documents/{TICKET}/{DOC}.md", text="<chunk 3>")
   ```
4. **KHÔNG retry quá 1 lần** với cùng tool nếu cùng error — chuyển fallback ngay

#### 5. Kết hợp với Logging Protocol

Mỗi chunk viết PHẢI có log entry TRƯỚC và SAU:

```
agent_log(step: "Write-1", status: "START", message: "Section 1 Introduction")
stream_write_file(file_path="...", content="<section 1>", mode="write")
agent_log(step: "Write-1", status: "DONE", message: "Section 1 written — 2500 chars")

agent_log(step: "Write-2", status: "START", message: "Section 2 System Overview")
stream_write_file(file_path="...", content="<section 2>", mode="append")
agent_log(step: "Write-2", status: "DONE", message: "Section 2 appended — 3200 chars")
```

#### 6. DOCX Export (MANDATORY sau khi viết xong document)

**Dùng `pandoc` CLI** (KHÔNG dùng MCP tool `export_docx` — tool đó yêu cầu truyền toàn bộ content dưới dạng string, bị truncated với documents lớn).

**Command pattern:**
```bash
pandoc "documents/{TICKET}/{DOC}.md" -o "documents/{TICKET}/{DOC}-v{VERSION}-{TICKET}.docx" --from markdown --to docx --resource-path="documents/{TICKET}"
```

**Ví dụ:**
```bash
pandoc "documents/MTO-15/BRD.md" -o "documents/MTO-15/BRD-v1-MTO-15.docx" --from markdown --to docx --resource-path="documents/MTO-15"
```

**Quy tắc:**
- `--resource-path` PHẢI trỏ đến folder chứa document (để pandoc tìm được images trong `diagrams/`)
- Tên file DOCX PHẢI chứa version: `{DOC}-v{MAJOR}-{TICKET}.docx`
- Export DOCX là bước CUỐI CÙNG sau khi document hoàn chỉnh và self-check pass
- Nếu pandoc không available → log WARNING và skip (DOCX không block pipeline)

### Áp dụng cho tất cả agents

| Agent | Documents | Estimated size |
|-------|-----------|----------------|
| BA | BRD | 500-700 dòng → 3-4 chunks |
| TA | FSD | 1000-1500 dòng → 6-8 chunks |
| SA | TDD | 800-1200 dòng → 5-7 chunks |
| QA | STP, STC | 300-600 dòng → 2-4 chunks |
| DEV | UG | 200-400 dòng → 2-3 chunks |
| DevOps | DPG, RLN | 200-400 dòng → 2-3 chunks |

### ⛔ Error Recovery Rules

1. **Nếu tool trả về error "data/content must be of type string"** → Content bị truncated. GIẢM kích thước chunk và retry.
2. **Nếu tool trả về error 2 lần liên tiếp** → Chuyển sang fallback tool (fsWrite/fsAppend) NGAY LẬP TỨC. Không retry lần 3.
3. **Nếu file bị viết dở** (crash giữa chừng) → Đọc file hiện tại, xác định section cuối cùng hoàn chỉnh, append từ section tiếp theo.
4. **KHÔNG BAO GIỜ** gọi cùng tool với cùng parameters sau khi nhận error — phải thay đổi approach.
