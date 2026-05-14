---
inclusion: manual
---

# Manual Web Test — Quy trình bắt buộc

## Khi nào áp dụng

Khi user yêu cầu "test manual", "QA test", "test web", "test UI", hoặc bất kỳ yêu cầu nào liên quan đến kiểm tra giao diện web trên browser.

## Quy trình bắt buộc

### Bước 1: DevOps — Build & Deploy

1. Build jar: `gradlew :orchestrator-server:shadowJar :kb-server:shadowJar`
2. Copy jar vào TempRelease
3. **Kill process cũ** và **restart server mới**:
   - Dùng `control_pwsh_process` để start server process
   - Verify server đã ready (health check hoặc wait for port)
4. Báo QA server đã sẵn sàng

### Bước 2: QA — Test tất cả màn hình

Dùng browser DevTools MCP tools (`navigate_page`, `take_snapshot`, `take_screenshot`, `click`, `fill`) để:

1. **Mở từng page** và verify:
   - Page load không lỗi (không 404, không blank)
   - Nav-bar hiện đúng (có links, có logout)
   - Content render đúng (không "Loading..." stuck, không JSON error)

2. **Test flows chính:**
   - Login → Profile → Logout → redirect đúng
   - Navigate qua tất cả nav links
   - Create/Edit/Delete operations (nếu có)
   - Error states (invalid input, unauthorized)

3. **Checklist pages:**
   - [ ] `/login` — login form, SSO button
   - [ ] `/profile` — user info, bridge token, credentials
   - [ ] `/static/admin-users.html` — user list, create user
   - [ ] `/admin/schemas` — credential schemas
   - [ ] `/sync/dashboard` — sync status
   - [ ] `/sync/graph-viewer` — 3D graph
   - [ ] `/static/setup.html` — first-time setup (khi DB trống)

### Bước 3: Dev — Fix lỗi

Nếu QA phát hiện lỗi:
1. Dev fix ngay (không cần hỏi user)
2. DevOps rebuild + restart
3. QA test lại

### Bước 4: Lặp lại cho đến khi PASS

Loop: Fix → Build → Deploy → Test → cho đến khi **tất cả pages PASS**.

### Bước 5: Báo cáo cho user

Chỉ báo user khi **tất cả tests đã PASS**. Format:

```
## ✅ Manual Web Test — PASSED

| Page | Status | Notes |
|------|--------|-------|
| /login | ✅ | ... |
| /profile | ✅ | ... |
| ... | ... | ... |

Server running at: localhost:{port}
```

## ⛔ KHÔNG BAO GIỜ

- Báo user "restart server để test" — DevOps phải tự restart
- Báo user từng lỗi một — phải tự fix hết rồi mới báo
- Skip test page nào — phải test TẤT CẢ
- Để lỗi "Loading..." hoặc JSON parse error — phải debug console

## Tools cần dùng

- `mcp_lowcode_devtools_local_navigate_page` — mở page
- `mcp_lowcode_devtools_local_take_snapshot` — đọc DOM
- `mcp_lowcode_devtools_local_take_screenshot` — chụp màn hình
- `mcp_lowcode_devtools_local_list_console_messages` — check JS errors
- `mcp_lowcode_devtools_local_click` — click elements
- `mcp_lowcode_devtools_local_fill` — fill forms
- `control_pwsh_process` — start/stop server process
- `execute_pwsh` — build, copy jar
