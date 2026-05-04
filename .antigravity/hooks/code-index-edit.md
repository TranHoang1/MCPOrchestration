---
name: Code Index — File Edited
description: Cập nhật index khi file nguồn bị thay đổi.
enabled: true
version: 1.0.0
when:
  type: fileEdited
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
