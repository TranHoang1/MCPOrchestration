# Technical Design Document (TDD)

## MTO-122: File Scanner + Signature Extractor

---

## 1. Architecture Overview

### 1.1 Component Placement

```
orchestrator-bridge/src/main/kotlin/com/orchestrator/mcp/bridge/codeintel/
├── scanner/
│   ├── FileScanner.kt              # Recursive workspace traversal
│   ├── LanguageDetector.kt         # Extension → language mapping
│   ├── GitignoreParser.kt          # .gitignore pattern matching
│   └── ContentHasher.kt            # SHA-256 file hashing
├── extractor/
│   ├── SignatureExtractor.kt       # Interface + dispatcher
│   ├── KotlinExtractor.kt          # Kotlin regex patterns
│   ├── TypeScriptExtractor.kt      # TypeScript/JS regex patterns
│   ├── PythonExtractor.kt          # Python regex patterns
│   ├── GoExtractor.kt              # Go regex patterns
│   ├── RustExtractor.kt            # Rust regex patterns
│   └── ShellExtractor.kt           # Bash + PowerShell patterns
```

### 1.2 Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Traversal | `java.nio.file.Files.walkFileTree` | Handles symlinks, depth control |
| Gitignore | Custom glob parser | No external deps, simple patterns |
| Hashing | `java.security.MessageDigest` SHA-256 | Stdlib, fast, collision-resistant |
| Extraction | Regex per language | Simple, fast, no AST parsing needed |
| Concurrency | Sequential scan, batch DB writes | SQLite single-writer; scan is I/O bound |

---

## 2. Detailed Design

### 2.1 FileScanner

Traverses workspace, filters files, returns list of scannable paths.

### 2.2 LanguageDetector

Maps file extensions to language identifiers.

### 2.3 SignatureExtractor

Dispatches to language-specific extractors based on detected language.

### 2.4 ContentHasher

Computes SHA-256 of file content for change detection.

---

## 3. Implementation Checklist

| # | File | Lines (est.) |
|---|------|-------------|
| 1 | `scanner/LanguageDetector.kt` | ~40 |
| 2 | `scanner/ContentHasher.kt` | ~30 |
| 3 | `scanner/GitignoreParser.kt` | ~60 |
| 4 | `scanner/FileScanner.kt` | ~80 |
| 5 | `extractor/SignatureExtractor.kt` | ~40 |
| 6 | `extractor/KotlinExtractor.kt` | ~70 |
| 7 | `extractor/TypeScriptExtractor.kt` | ~70 |
| 8 | `extractor/PythonExtractor.kt` | ~50 |
| 9 | `extractor/GoExtractor.kt` | ~50 |
| 10 | `extractor/RustExtractor.kt` | ~60 |
| 11 | `extractor/ShellExtractor.kt` | ~50 |
