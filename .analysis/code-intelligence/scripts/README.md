# Code Intelligence Indexer — Multi-Language

Bộ indexer phân tích source code và tạo ra markdown analysis files cho AI agents sử dụng.

## Chọn script phù hợp với môi trường

| Thư mục | Ngôn ngữ | Yêu cầu | Cách chạy |
|---------|----------|----------|-----------|
| `python/` | Python 3.7+ | Chỉ cần Python (standard library) | `python main.py <project_root>` |
| `java/` | Java 17+ | Chỉ cần JDK (standard library) | `run.bat <project_root>` hoặc `./run.sh <project_root>` |
| `powershell/` | PowerShell 5.1+ | Built-in trên Windows | `.\full-indexer.ps1 -RootDir <project_root>` |
| `bash/` | Bash 4+ | Built-in trên Linux/Mac | `bash full-indexer.sh <project_root>` |
| `nodejs/` | TypeScript | Node.js 18+ + npm install | `cd nodejs && npm install && npx tsx src/full-indexer.ts <project_root>` |

## Output (giống nhau bất kể dùng script nào)

```
.analysis/code-intelligence/
├── project-structure.md      ← Tổng quan project (modules, languages, frameworks)
├── modules/
│   ├── root.md               ← Analysis cho module root
│   ├── core.md               ← Analysis cho module core
│   └── ...
├── index-metadata.json       ← Metadata (timestamps, file hashes)
├── kb-payloads.json          ← Payloads sẵn sàng ingest vào Knowledge Base
└── index-config.json         ← Config (extensions, excludes)
```

## Khi nào dùng cái nào?

- **Windows (không có Python/Node)** → dùng `powershell/` (built-in, zero install)
- **Linux/Mac (không có Python/Node)** → dùng `bash/` (built-in, zero install)
- **Dự án Python/Django/FastAPI** → dùng `python/` (đã có Python sẵn)
- **Dự án Java/Kotlin/Spring** → dùng `java/` (đã có JDK sẵn)
- **Dự án Node.js/TypeScript** → dùng `nodejs/` (đã có Node sẵn, parser chính xác nhất)
- **Dự án Go/Rust** → dùng `python/` hoặc `bash/`
- **CI/CD pipeline** → dùng `python/` hoặc `bash/` (thường có sẵn)
- **Không có gì** → dùng Kiro AI agent fallback (xem steering file `code-intelligence.md`)

## Thêm ngôn ngữ mới (tương lai)

Tạo thư mục mới (ví dụ: `bash/`, `powershell/`, `java/`) với cùng logic:
1. Đọc `index-config.json`
2. Detect project type từ build files
3. Discover modules
4. Scan + parse source files
5. Ghi output vào cùng location với cùng format
