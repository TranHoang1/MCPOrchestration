---
inclusion: always
---

# Database Migration Rule — Flyway Mandatory

## ⛔ Quy tắc tuyệt đối: Mọi thay đổi database PHẢI qua Flyway migration

### KHÔNG BAO GIỜ được phép:

1. **Tạo table bằng `CREATE TABLE IF NOT EXISTS` trong application code** (Kotlin/Java)
2. **Dùng `DatabaseInitializer` pattern** — chạy DDL trực tiếp từ code khi startup
3. **Hardcode DDL trong companion object** hoặc string constants trong service classes
4. **Tạo index, alter column, add constraint** mà không có migration script

### PHẢI luôn:

1. **Tạo Flyway migration script** trong `src/main/resources/db/migration/`
2. **Đặt tên theo convention**: `V{version}__{description}.sql`
   - Ví dụ: `V1__create_users_table.sql`, `V2__add_email_index.sql`
3. **Tạo rollback script** tương ứng: `U{version}__{description}.sql`
4. **Migration phải idempotent** khi có thể (dùng `IF NOT EXISTS`, `IF EXISTS`)
5. **Test migration** trên empty DB VÀ existing DB trước khi merge

## Flyway Convention cho project này

### Thư mục migration scripts

```
{module}/src/main/resources/db/migration/
├── V1__initial_schema.sql          ← Baseline từ existing tables
├── V2__add_user_management.sql     ← Feature migration
├── V3__add_rls_policies.sql        ← Security migration
├── U2__add_user_management.sql     ← Rollback for V2
└── U3__add_rls_policies.sql        ← Rollback for V3
```

### Version numbering

| Module | Version Range | Ví dụ |
|--------|--------------|-------|
| orchestrator-server (core) | V1–V99 | `V1__tool_embeddings.sql` |
| orchestrator-server (sync) | V100–V199 | `V100__jira_sync_tables.sql` |
| orchestrator-server (security) | V200–V299 | `V200__rls_policies.sql` |
| orchestrator-server (user-mgmt) | V300–V399 | `V300__users_table.sql` |
| kb-server | V1–V99 (separate DB) | `V1__kb_entries.sql` |
| orchestrator-client | V1–V99 (separate DB) | `V1__server_config.sql` |

### Migration script template

```sql
-- V{N}__{description}.sql
-- Author: {agent/developer}
-- Ticket: {JIRA-KEY}
-- Description: {What this migration does}

-- DDL statements here
CREATE TABLE IF NOT EXISTS {table_name} (
    ...
);

CREATE INDEX IF NOT EXISTS {idx_name} ON {table_name} (...);
```

### Rollback script template

```sql
-- U{N}__{description}.sql
-- Rollback for V{N}__{description}.sql

DROP TABLE IF EXISTS {table_name};
DROP INDEX IF EXISTS {idx_name};
```

## Quy tắc cho từng Agent

### SA Agent (Solution Architect)
- Khi thiết kế TDD Section 4 (Database Design):
  - PHẢI specify migration scripts cụ thể (tên file, thứ tự)
  - PHẢI có rollback plan cho mỗi migration
  - PHẢI xác định version range phù hợp với module
  - PHẢI kiểm tra backward compatibility với version trước

### TA Agent (Technical Architect)
- Khi review FSD có data model changes:
  - PHẢI flag nếu FSD thiếu migration strategy
  - PHẢI đề xuất migration approach (additive vs breaking)
  - PHẢI verify FK dependencies giữa các migration

### DEV Agent (Developer)
- Khi implement database changes:
  - PHẢI tạo Flyway migration script, KHÔNG viết DDL trong Kotlin code
  - PHẢI chạy `./gradlew flywayMigrate` để verify
  - PHẢI chạy `./gradlew flywayInfo` để confirm version state
  - PHẢI tạo rollback script cho mỗi migration
  - Nếu TDD không có migration plan → DỪNG LẠI, báo SM yêu cầu SA bổ sung

### DevOps Agent
- Khi tạo deployment guide:
  - PHẢI include Flyway migration step trong deployment procedure
  - PHẢI có rollback procedure sử dụng `flyway undo`
  - PHẢI verify migration state trước và sau deploy

## Backward Compatibility Rules

### Additive changes (safe — no downtime):
- Thêm table mới
- Thêm column với DEFAULT value
- Thêm index
- Thêm constraint (CHECK, UNIQUE) nếu data đã comply

### Breaking changes (cần migration plan):
- Drop column → phải có deprecation period (2 releases)
- Rename column → tạo new column + copy data + drop old (3-step migration)
- Change data type → tạo new column + migrate data + drop old
- Drop table → verify no FK references + backup data

### Multi-step migration pattern (cho breaking changes):

```
Release N:   V10__add_new_column.sql     (add new, keep old)
Release N+1: V11__migrate_data.sql       (copy old → new)
Release N+2: V12__drop_old_column.sql    (remove old)
```

## Checklist trước khi merge code có database changes

- [ ] Migration script tồn tại trong `db/migration/`?
- [ ] Rollback script tồn tại?
- [ ] Migration chạy thành công trên empty DB?
- [ ] Migration chạy thành công trên existing DB (với data)?
- [ ] Không có DDL trong application code (Kotlin/Java)?
- [ ] Version number không conflict với existing migrations?
- [ ] Breaking changes có multi-step plan?
- [ ] TDD Section 4.3 đã document migration plan?
