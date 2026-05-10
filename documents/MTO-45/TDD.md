# Technical Design Document (TDD)

## MCPOrchestration — MTO-45: Windows CMD Bridge Client

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-45 |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2026-05-10 |
| Related BRD | BRD-v1-MTO-45.docx |
| Related FSD | FSD-v1-MTO-45.docx |

---

## 1. Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Windows Batch (CMD) | cmd.exe |
| HTTP | curl.exe | Bundled with Windows 10+ |
| JSON | jq.exe (optional) | 1.6+ |
| Deployment | Single .cmd file | mcp-bridge.cmd |

---

## 2. Architecture

### 2.1 File Structure

```
mcp-bridge-cmd/
├── mcp-bridge.cmd          # Complete bridge script
├── jq.exe                  # Optional: bundled jq for JSON parsing
├── tests/
│   └── test-bridge.cmd     # Basic integration tests
└── README.md
```

### 2.2 Script Structure

```batch
@echo off
setlocal enabledelayedexpansion

REM === Configuration ===
if "%ORCHESTRATOR_URL%"=="" set "ORCHESTRATOR_URL=http://localhost:8080/mcp"
if "%BRIDGE_TIMEOUT%"=="" set "BRIDGE_TIMEOUT=30"
set "SESSION_ID="
set "STATE=DISCONNECTED"

REM === Initialize ===
call :initialize_session
if "%SESSION_OK%"=="1" (
    set "STATE=CONNECTED"
    echo [mcp-bridge] State: DISCONNECTED → CONNECTED >&2
)

REM === Main Loop ===
:main_loop
set "LINE="
set /p "LINE="
if "!LINE!"=="" goto :main_loop
call :process_request "!LINE!"
goto :main_loop

REM === Functions below ===
```

### 2.3 Key Implementation Details

**JSON Response Construction (without jq):**

```batch
:json_response
REM Build JSON-RPC response manually
set "ID=%~1"
set "RESULT=%~2"
echo {"jsonrpc":"2.0","id":%ID%,"result":%RESULT%}
goto :eof
```

**stream_write_file Implementation:**

```batch
:handle_stream_write_file
set "FILE_PATH=%~1"
set "CONTENT=%~2"
set "MODE=%~3"
if "%MODE%"=="" set "MODE=write"

if "%MODE%"=="write" (
    echo %CONTENT%> "%FILE_PATH%"
) else if "%MODE%"=="append" (
    echo %CONTENT%>> "%FILE_PATH%"
)

for %%F in ("%FILE_PATH%") do set "FILE_SIZE=%%~zF"
call :json_response %ID% "{\"file_path\":\"%FILE_PATH%\",\"total_size\":%FILE_SIZE%,\"mode\":\"%MODE%\"}"
goto :eof
```

---

## 3. Implementation Checklist

| # | File | Description |
|---|------|-------------|
| 1 | `mcp-bridge-cmd/mcp-bridge.cmd` | Complete bridge script |
| 2 | `mcp-bridge-cmd/tests/test-bridge.cmd` | Basic tests |
| 3 | `mcp-bridge-cmd/README.md` | Usage documentation |

---

## 4. Known Limitations

| Limitation | Impact | Workaround |
|-----------|--------|------------|
| No background processes | Cannot do periodic health check | Check connection on each tool call |
| Limited JSON parsing | Cannot handle nested JSON without jq | Bundle jq.exe or use basic string parsing |
| No exponential backoff | Fixed retry delay | Use fixed 5s delay, max 3 attempts |
| Variable expansion issues | Special characters in JSON break parsing | Use temp files for large payloads |
| No async I/O | Main loop blocks on each request | Acceptable for CMD use case |

---

## 5. Appendix

### Diagram Index

| # | Diagram | Image | Source |
|---|---------|-------|--------|
| 1 | Architecture | [architecture.png](diagrams/architecture.png) | [architecture.drawio](diagrams/architecture.drawio) |
