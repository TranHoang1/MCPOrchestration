---
inclusion: always
---

# Code Standards — All Languages

## File Size Rules

- **Maximum 200 lines per file** (excluding blank lines and comments)
- **Maximum 20 lines per function/method** (excluding signature and closing brace)
- If a file exceeds 200 lines → split into multiple files by responsibility
- If a function exceeds 20 lines → extract helper functions

## File Organization

Each script/module folder MUST follow single-responsibility principle:

```
script-folder/
├── main.py / main.sh / main.ps1     ← Entry point only (orchestration)
├── config.py                         ← Configuration loading
├── detector.py                       ← Project type detection
├── discovery.py                      ← Module discovery
├── scanner.py                        ← File scanning + filtering
├── parser.py                         ← Source file parsing (may split per language)
├── patterns.py                       ← Pattern detection logic
├── generator.py                      ← Output file generation
└── utils.py                          ← Shared utilities (hash, path helpers)
```

## Naming Conventions

| Language | Files | Functions | Variables | Constants |
|----------|-------|-----------|-----------|-----------|
| Python | `snake_case.py` | `snake_case()` | `snake_case` | `UPPER_SNAKE` |
| PowerShell | `PascalCase.ps1` | `Verb-Noun` | `$camelCase` | `$UPPER_SNAKE` |
| Bash | `kebab-case.sh` | `snake_case()` | `snake_case` | `UPPER_SNAKE` |
| TypeScript | `kebab-case.ts` | `camelCase()` | `camelCase` | `UPPER_SNAKE` |
| Kotlin/Java | `PascalCase.kt` | `camelCase()` | `camelCase` | `UPPER_SNAKE` |

## Function Design Rules

1. **Single responsibility** — one function does one thing
2. **Max 3 parameters** — use config objects/dicts for more
3. **No side effects in pure functions** — separate I/O from logic
4. **Early return** — avoid deep nesting, return early on error/edge cases
5. **Descriptive names** — function name should describe what it returns or does

## Import/Dependency Rules

- **Zero external dependencies** for indexer scripts (stdlib only)
- Group imports: stdlib → project modules → (external if any)
- No circular imports between modules

## Error Handling

- Every I/O operation MUST have error handling (try/catch or equivalent)
- Functions return meaningful error info, never silently fail
- Log errors with context (file path, operation attempted)

## Documentation

- Every file starts with a module docstring explaining its purpose
- Every public function has a one-line docstring
- No inline comments explaining "what" — only "why" when non-obvious
