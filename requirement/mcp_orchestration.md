Chào bạn, đây là bản đặc tả yêu cầu (Requirement Specification) chi tiết để bạn gửi cho một AI Agent (như Kiro, Claude, hoặc một Kotlin Developer Agent). Bản đặc tả này được thiết kế theo tư duy hệ thống, tập trung vào tính module hóa và tối ưu hóa hiệu suất bằng Kotlin.

---

# Software Requirement Specification: MCP Orchestration Server

## 1. Mục tiêu hệ thống
Xây dựng một **MCP Orchestration Server** bằng **Kotlin** đóng vai trò là lớp Proxy thông minh giữa AI IDE (Kiro) và các MCP Servers khác. Mục đích chính là giảm tải Context Window bằng cách thực hiện truy vấn công cụ (Tool Discovery) qua Knowledge Base thay vì nạp tất cả công cụ vào prompt.

## 2. Stack kỹ thuật yêu cầu
*   **Ngôn ngữ:** Kotlin 2.x (ưu tiên sử dụng Coroutines cho non-blocking I/O).
*   **Framework:** Ktor hoặc Spring Boot 3.x.
*   **Giao thức:** MCP (Model Context Protocol).
*   **Lưu trữ:** Vector Database (ví dụ: Milvus, Qdrant hoặc Local FAISS/Chroma via JNI) để lưu trữ Tool Definitions.
*   **Embeddings:** Tích hợp OpenAI hoặc HuggingFace local để vector hóa mô tả công cụ.

## 3. Kiến trúc Tool Discovery & Execution
Hệ thống chỉ phơi ra **2 công cụ duy nhất** cho Kiro IDE:

### A. Công cụ `find_tools`
*   **Input:** `query: String` (Mô tả hành động AI muốn thực hiện).
*   **Logic:** 
    1. Thực hiện Semantic Search trên Vector DB để tìm các công cụ có mô tả khớp nhất.
    2. Rút trích `name`, `description`, và `input_schema` của Top-K công cụ (ví dụ: tối đa 5).
*   **Output:** Một danh sách JSON chứa định nghĩa chi tiết của các công cụ tìm được.

### B. Công cụ `execute_dynamic_tool`
*   **Input:** 
    *   `tool_name: String`
    *   `arguments: Map<String, Any>`
*   **Logic:** 
    1. Xác định MCP Server đích đang quản lý `tool_name`.
    2. Chuyển tiếp (Proxy) yêu cầu thực thi đến server đó qua giao thức JSON-RPC của MCP.
    3. Nhận kết quả và trả về cho AI Agent.



---

## 4. Các tính năng cốt lõi (Core Features)

### 4.1. Tool Registration & Indexing
*   Cung cấp một script hoặc endpoint để quét (scan) các MCP Servers hiện có.
*   Tự động trích xuất metadata và cập nhật vào Vector Database.

### 4.2. Quản lý trạng thái (State Management)
*   Theo dõi danh sách các MCP Server đang hoạt động (Upstream Servers).
*   Xử lý lỗi nếu một Upstream Server bị mất kết nối.

### 4.3. Cấu hình linh hoạt
*   Cho phép cấu hình danh sách MCP Servers đích qua file `application.yml` hoặc `config.json`.

---

## 5. Hướng dẫn cho AI Agent thực hiện (Implementation Prompts)

**Bạn hãy gửi yêu cầu này cho Agent:**

> "Hãy viết một ứng dụng Kotlin sử dụng Ktor để làm MCP Orchestration Server. 
> 1. Sử dụng thư viện `kotlinx.serialization` để xử lý JSON.
> 2. Triển khai lớp `ToolRegistry` để quản lý và tìm kiếm tool bằng Search Semantic (có thể giả lập bằng keyword search trước nếu chưa có Vector DB).
> 3. Tạo 2 endpoint tuân thủ giao thức MCP: `find_tools` và `execute_dynamic_tool`.
> 4. Đảm bảo mã nguồn tuân thủ SOLID và sử dụng Coroutines để xử lý song song các yêu cầu từ các Upstream MCP Servers.
> 5. Viết Unit Test cho logic điều phối (Dispatcher)."

---

## 6. Luồng hoạt động trong Kiro IDE (Sequence)

1. **User:** "Hãy kiểm tra log hệ thống và tạo ticket Jira nếu có lỗi."
2. **Kiro (Context):** Chỉ có tool `find_tools`.
3. **Kiro -> Orchestrator:** `find_tools("check logs and create Jira ticket")`
4. **Orchestrator:** Trả về Schema của tool `read_logs` và `create_jira_issue`.
5. **Kiro (Context):** Bây giờ AI hiểu cách dùng 2 tool này.
6. **Kiro -> Orchestrator:** `execute_dynamic_tool("read_logs", {path: "/var/log/app.log"})`
7. **Orchestrator:** Gọi MCP Server Log thực tế và trả về dữ liệu.

