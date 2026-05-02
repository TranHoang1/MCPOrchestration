# Yêu cầu thông tin MCP Server để đăng ký custom agent

Vui lòng cung cấp các thông tin sau để tôi tiếp tục thử đăng ký agent:

- Base URL của MCP server (ví dụ: http://mcp.example.com:8080)
- Endpoint đăng ký agent (ví dụ: /agents, /api/agents, /v1/agents). Nếu là root, ghi "/".
- Thông tin xác thực nếu có:
  - Bearer token: Xác nhận header Authorization: Bearer <TOKEN>
  - Hoặc Basic auth: username và password
  - Hoặc chỉ rõ không cần auth
- Nếu server yêu cầu header/cert đặc biệt, mô tả ngắn gọn.
- Ghi chú nếu server chạy trên container/local và cần forward port.

Ví dụ lệnh PowerShell để gọi script:
```powershell
powershell -ExecutionPolicy Bypass -File scripts\register_agent.ps1 -Endpoint "http://your-mcp-server:8080/agents" -File "config/agents/sample_custom_agent.json"
```

Ví dụ curl:
```bash
curl -X POST "http://your-mcp-server:8080/agents" -H "Content-Type: application/json" -H "Authorization: Bearer YOUR_TOKEN" --data-binary @config/agents/sample_custom_agent.json
```

Sau khi bạn cung cấp thông tin, tôi sẽ thử đăng ký lại và báo kết quả.