# Hướng dẫn sử dụng (User Guide)

## MCP Orchestration Server — MTO-10: Nâng cấp MCP Orchestrator: Local Embedding, pgvector, Quản lý Tool & Auto-Approve

---

## Thông tin tài liệu

| Trường | Giá trị |
|-------|-------|
| Mã Jira | MTO-10 |
| Tiêu đề | Nâng cấp MCP Orchestrator: Local Embedding, pgvector, Quản lý Tool & Auto-Approve |
| Tác giả | DEV Agent |
| Người duyệt | BA Agent |
| Phiên bản | 1.5 |
| Ngày | 2026-05-04 |
| Trạng thái | Final |

---

## Lịch sử thay đổi

| Phiên bản | Ngày | Tác giả | Nội dung thay đổi |
|-----------|------|---------|-------------------|
| 1.0 | 04/05/2026 | DEV Agent | Tài liệu ban đầu cho MTO-10 |
| 1.1 | 04/05/2026 | SM Agent | Đơn giản hóa hướng dẫn theo phản hồi của người dùng |
| 1.2 | 04/05/2026 | DEV Agent | Sửa tên file JAR (dùng fat jar); Dọn dẹp lỗi định dạng lặp lại |
| 1.3 | 04/05/2026 | DEV Agent | **Hợp nhất toàn diện:** Khôi phục độ chi tiết kỹ thuật; **Đính chính kiến trúc:** Làm rõ chế độ Standalone (YAML/SSE) và Local Bridge (JSON/Stdio). |
| 1.4 | 04/05/2026 | BA Agent | Thêm "Tóm tắt yêu cầu cốt lõi" và "Giải thích trường cấu hình" theo yêu cầu của người dùng. |
| 1.5 | 04/05/2026 | DEV Agent | Thêm chi tiết về Điểm nhập thống nhất (Unified Entry Point) và ghi đè cấu hình embedding/vector_db qua JSON. |

---

## 1. Giới thiệu

### 1.1 Mục đích
**MCP Orchestration Server** là một proxy thông minh giúp tối ưu hóa và quản trị các công cụ AI (MCP tools). Hệ thống giải quyết vấn đề giới hạn số lượng tool của các IDE và tăng cường tính bảo mật.

### 1.2 Tính năng mới (MTO-10)
- **Local Embedding:** Hỗ trợ Ollama và LMStudio (không cần internet).
- **Tích hợp pgvector:** Lưu trữ dữ liệu vector trực tiếp trong PostgreSQL.
- **Auto-Approve System:** Tự động phê duyệt các tool an toàn.
- **Quản lý Tool động:** Bật/Tắt tool tại runtime mà không cần restart.

### 1.3 Tóm tắt yêu cầu cốt lõi (MTO-10)

Để bạn nắm nhanh, các mục tiêu chính của bản nâng cấp MTO-10 bao gồm:
- **Kiến trúc Ưu tiên Local:** Loại bỏ phụ thuộc vào OpenAI/Qdrant, chuyển sang dùng Ollama/LMStudio và PostgreSQL (pgvector) để chạy offline hoàn toàn.
- **Điều khiển động:** Cho phép Bật/Tắt các tool ngay khi đang chạy thông qua lệnh MCP mà không cần khởi động lại server.
- **Tự động hóa quy trình:** Đánh dấu các tool an toàn là "auto-approve" (tự động phê duyệt) đồng thời trong cả DB và file cấu hình.
- **Đa dạng môi trường:** Hỗ trợ cả chế độ Server hiệu năng cao (SSE) và tích hợp trực tiếp vào IDE (Stdio).
- **Bảo mật nền tảng:** Triển khai bộ lọc tool (Allowlist/Blocklist) ngay tại lớp indexing.

---

---

## 2. Hướng dẫn khởi đầu

1. **Chuẩn bị DB:** Đảm bảo Postgres đã bật extension vector: `CREATE EXTENSION IF NOT EXISTS vector;`
2. **Chạy ứng dụng:** `java -jar mcp-orchestrator-all.jar`

---

## 3. Tham chiếu cấu hình

Orchestrator hỗ trợ hai chế độ vận hành riêng biệt, mỗi chế độ có định dạng cấu hình khác nhau.

### 3.1 Chế độ A: Instance độc lập (Standalone - HTTP/SSE)
**Trường hợp sử dụng:** Chạy Orchestrator như một dịch vụ web (Web Service) duy trì liên tục.
- **Định dạng:** YAML (`application.yml`).
- **Giao thức:** HTTP Streamable / Server-Sent Events (SSE).

**Ví dụ `application.yml`:**
```yaml
orchestrator:
  server:
    port: 8080
    protocol: sse
  embedding:
    provider: ollama
    model: nomic-embed-text
  vector_db:
    provider: pgvector
    connection_string: "postgresql://..."

#### 3.1.1 Tại sao cần các trường này?

| Trường | Lý do cần thiết |
|--------|-----------------|
| `protocol: sse` | Cờ này chỉ định ứng dụng khởi động như một **web server duy trì liên tục** (Chế độ Standalone). Nếu không đặt hoặc đặt là `stdio`, ứng dụng sẽ đợi lệnh từ đầu vào tiêu chuẩn, phù hợp cho IDE nhưng không phù hợp cho truy cập web đa người dùng. |
| `port: 8080` | Xác định cổng mạng mà SSE stream sẽ phục vụ. Đây là điều kiện bắt buộc để AI client có thể kết nối qua HTTP. |
```

---

### 3.2 Chế độ B: Cầu nối cục bộ (Local Bridge - Stdio)
**Trường hợp sử dụng:** Tích hợp trực tiếp vào các IDE (Cursor, Claude, VSCode).
- **Định dạng:** JSON (nằm trong phần cài đặt của Client app).
- **Giao thức:** JSON-RPC 2.0 qua Standard I/O (stdio).

**Ví dụ cấu hình IDE:**
**Ví dụ cấu hình IDE (Cấu hình JSON nâng cao):**
Trong **chế độ Stdio**, giờ đây bạn có thể định nghĩa toàn bộ môi trường Orchestrator (bao gồm cả cài đặt Embedding và Vector DB) trực tiếp trong file cấu hình của client (ví dụ: `mcpServers` của Cursor hoặc file `config.json` riêng biệt).

```json
{
  "mcpServers": {
    "mcp-orchestrator": {
      "command": "java",
      "args": [
        "-jar", 
        "C:\\path\\to\\mcp-orchestrator-all.jar",
        "--config",
        "C:\\path\\to\\mcp-config.json"
      ],
      "env": {
        "EMBEDDING_PROVIDER": "ollama",
        "EMBEDDING_MODEL": "nomic-embed-text",
        "VECTOR_DB_PROVIDER": "pgvector",
        "VECTOR_DB_CONNECTION_STRING": "postgresql://postgres:password@localhost:5432/mcp_db"
      }
    }
  }
}
```

**Các biến môi trường quan trọng:**

| Biến | Mô tả |
|------|-------|
| `EMBEDDING_PROVIDER` | Provider cho embedding (`ollama`, `openai`, `lmstudio`). |
| `EMBEDDING_MODEL` | Tên model cụ thể (ví dụ: `nomic-embed-text`). |
| `EMBEDDING_BASE_URL` | URL gốc của provider (ví dụ: `http://localhost:11434` cho Ollama). |
| `VECTOR_DB_CONNECTION_STRING` | Chuỗi kết nối đầy đủ tới database. |
| `SERVER_PROTOCOL` | Giao thức chạy server (`stdio` mặc định hoặc `sse`). |

#### 3.2.1 Cấu hình đặc biệt cho Ollama
Nếu bạn sử dụng **Ollama** cho embedding tại địa phương:
1.  **Tải model:** Chạy lệnh `ollama pull nomic-embed-text`.
2.  **Kiểm tra Dimensions:** Model `nomic-embed-text` thường sử dụng **768** dimensions. Đảm bảo giá trị này khớp với `EMBEDDING_DIMENSIONS` (mặc định là 768).
3.  **Base URL:** Nếu Ollama chạy ở port mặc định, bạn không cần thiết lập `EMBEDDING_BASE_URL`. Nếu chạy ở port khác, hãy đặt thành `http://your-ip:port`.

#### 3.2.2 Điểm nhập thống nhất (Unified Entry Point)
Orchestrator sử dụng một điểm nhập duy nhất: `com.orchestrator.mcp.Main`.
- **Tự động phát hiện**: Hệ thống tự động nhận diện chế độ chạy (**SSE** hoặc **Stdio**) dựa trên giá trị `orchestrator.server.protocol` trong cấu hình.
- **An toàn đầu ra**: Trong chế độ Stdio, tất cả log ứng dụng sẽ được tự động chuyển hướng sang `System.err` để đảm bảo không gây nhiễu luồng giao tiếp JSON-RPC trên `System.out`.
> [!TIP]
> Bạn chỉ cần thêm phần `"env": { ... }` nếu trong file cấu hình của Orchestrator (`application.yml` hoặc `mcp-servers.json`) có sử dụng các biến dạng `${VAR_NAME}` cần truyền giá trị từ môi trường.

---

### 3.3 Cấu hình Upstream Server (JSON)
Dù chạy ở chế độ nào, Orchestrator quản lý kết nối tới các server thượng nguồn (Jira, Slack, v.v.) thông qua một file cấu hình JSON riêng biệt (thường là `mcp-servers.json`).

- **Định dạng:** JSON.
- **Giao thức Upstream hỗ trợ:** Cả **stdio** và **http/sse**.

**Ví dụ cấu hình Upstream:**
```json
{
  "mcpServers": {
    "sample-server": {
      "command": "npx",
      "args": ["..."],
      "env": { "SECRET": "..." }
    }
  }
}
```

---

## 4. Hướng dẫn chi tiết: Các tính năng mới MTO-10

### 4.1 Quản lý Tool động (`toggle_tool`)
Bật hoặc tắt tool ngay khi đang chạy.

**Ví dụ: Tắt tool xóa ticket**
```json
{
  "name": "execute_dynamic_tool",
  "arguments": {
    "tool_name": "toggle_tool",
    "arguments": {
      "tool_name": "jira_delete_issue",
      "enabled": false
    }
  }
}
```

---

### 4.2 Cấu hình Tự động phê duyệt (`manage_auto_approve`)
Đánh dấu tool an toàn để AI chạy không cần hỏi.

**Ví dụ: Tự động phê duyệt lệnh lấy thông tin ticket**
```json
{
  "name": "execute_dynamic_tool",
  "arguments": {
    "tool_name": "manage_auto_approve",
    "arguments": {
      "tool_name": "jira_get_issue",
      "auto_approve": true
    }
  }
}
```

---

### 4.3 Hướng dẫn Local Embedding (Ollama / LMStudio)

#### 4.3.1 Model khuyến nghị
- **Ollama:** `nomic-embed-text` (768 dimensions).

#### 4.3.2 Kiểm tra kết nối
Nếu Orchestrator không kết nối được AI local, hãy chạy lệnh `ollama serve`.

#### 4.3.3 Tự động chuẩn hóa kích thước Vector (Dimension Normalization)
MTO-10 tự động xử lý các sai lệch về kích thước vector giữa các model. Nếu model local của bạn (ví dụ: `all-minilm`) tạo ra vector 384 chiều nhưng database yêu cầu 768 chiều, Orchestrator sẽ **tự động bù (pad)** thêm các giá trị 0. Ngược lại, nếu vector quá lớn, nó sẽ bị **cắt bớt (truncate)**. Điều này đảm bảo hệ thống luôn ổn định dù bạn thay đổi model local.

---

### 4.4 Khôi phục mặc định (`reset_tools`)
Xóa các tùy chỉnh tạm thời và nạp lại từ file cấu hình.

---

### 4.5 Bộ lọc công cụ (Allowlist / Blocklist)
Giới hạn tool được phép sử dụng trong file `mcp-servers.json`.

---

### 4.6 Cơ chế đồng bộ Config-DB
File `mcp-servers.json` là **Nguồn sự thật duy nhất**. Hệ thống tự đồng bộ vào DB khi khởi động.

---

### 4.7 Bảo trì PostgreSQL pgvector
Lệnh xóa chỉ mục để nạp lại: `TRUNCATE TABLE mcp_tool_embeddings;`

---

## 5. Sử dụng
- `find_tools`: Tìm kiếm tool.
- `execute_dynamic_tool`: Thực thi tool.

---

## 6. Giải quyết sự cố
(Xem bảng tra cứu lỗi trong tài liệu kỹ thuật).
