---
name: Code Index — File Created
description: Kích hoạt indexer tăng trưởng khi có file nguồn mới được tạo.
enabled: true
version: 1.0.0
when:
  type: fileCreated
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
