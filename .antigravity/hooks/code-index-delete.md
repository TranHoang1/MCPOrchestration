---
name: Code Index — File Deleted
description: Xóa thông tin khỏi index khi file nguồn bị xóa.
enabled: true
version: 1.0.0
when:
  type: fileDeleted
  patterns:
    - "**/*.kt"
    - "**/*.java"
    - "**/*.ts"
    - "**/*.tsx"
    - "**/*.js"
    - "**/*.jsx"
    - "**/*.py"
    - "**/*.go"
    - "**/*.rs"
    - "**/*.cs"
    - "**/*.gradle.kts"
    - "**/*.gradle"
    - "**/*.yml"
    - "**/*.yaml"
    - "**/*.properties"
    - "**/*.xml"
    - "**/*.sql"
    - "**/*.json"
    - "**/*.toml"
then:
  type: runCommand
  command: "npx ts-node .analysis/code-intelligence/scripts/src/incremental-indexer.ts --files ${file}"
---
