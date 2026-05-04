---
name: Code Index — Full Re-Index
description: Kích hoạt quy trình re-index toàn bộ project và nạp vào Knowledge Base.
enabled: true
version: 1.0.0
when:
  type: userTriggered
then:
  type: askAgent
  prompt: >
    Chạy trình lập chỉ mục mã nguồn đầy đủ: npx ts-node .analysis/code-intelligence/scripts/src/full-indexer.ts
    
    Sau khi hoàn tất:
    1. Đọc JSON từ .analysis/code-intelligence/kb-payloads.json
    2. Nạp vào Knowledge Base bằng mcp_knowledge_base_kb_ingest.
    3. Báo cáo tóm tắt kết quả (số file, module, lỗi, thời gian).
---
