# Functional Specification Document (FSD)

## MCPOrchestration — MTO-45: Windows CMD Bridge Client

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-45 |
| Author | BA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-10 |
| Related BRD | BRD-v1-MTO-45.docx |

---

## 1. Functional Requirements

### 1.1 stdio MCP Server (Simplified)

**Tools Exposed:**

| # | Tool | Type | Implementation |
|---|------|------|---------------|
| 1 | find_tools | Proxy | curl.exe POST |
| 2 | execute_dynamic_tool | Proxy | curl.exe POST |
| 3 | toggle_tool | Proxy | curl.exe POST |
| 4 | reset_tools | Proxy | curl.exe POST |
| 5 | stream_write_file | Local | echo/type redirection |

**Note:** CMD bridge exposes fewer tools than other bridges due to CMD limitations. `embed_images` is not supported (requires base64 encoding which CMD cannot do efficiently).

### 1.2 HTTP Client (curl.exe)

```batch
:http_post
set "BODY=%~1"
curl.exe -s -m %TIMEOUT% -X POST ^
    -H "Content-Type: application/json" ^
    -H "Mcp-Session-Id: %SESSION_ID%" ^
    -d "%BODY%" ^
    "%ORCHESTRATOR_URL%" > %TEMP%\mcp_response.json 2>nul
if errorlevel 1 (set "HTTP_OK=0") else (set "HTTP_OK=1")
goto :eof
```

### 1.3 Main Loop

```batch
:main_loop
set /p "LINE=" < con
if "%LINE%"=="" goto :main_loop

REM Extract method using jq (if available) or string parsing
if exist "%JQ_PATH%" (
    for /f "delims=" %%m in ('echo %LINE% ^| "%JQ_PATH%" -r ".method"') do set "METHOD=%%m"
) else (
    REM Basic string parsing fallback
    call :extract_method "%LINE%"
)

if "%METHOD%"=="initialize" call :handle_initialize
if "%METHOD%"=="tools/list" call :handle_tools_list
if "%METHOD%"=="tools/call" call :handle_tool_call

goto :main_loop
```

### 1.4 Reconnection (Basic)

```batch
:reconnect_loop
set ATTEMPT=0
:reconnect_retry
if %ATTEMPT% GEQ 3 (
    echo [mcp-bridge] ERROR: Failed to reconnect after 3 attempts >&2
    goto :eof
)
set /a ATTEMPT+=1
echo [mcp-bridge] Reconnecting (attempt %ATTEMPT%/3)... >&2
timeout /t 5 /nobreak >nul
call :initialize_session
if "%SESSION_OK%"=="1" (
    echo [mcp-bridge] State: RECONNECTING → CONNECTED >&2
    goto :eof
)
goto :reconnect_retry
```

### 1.5 Configuration

| Parameter | Env Variable | Default |
|-----------|--------------|---------|
| URL | ORCHESTRATOR_URL | http://localhost:8080/mcp |
| Timeout | BRIDGE_TIMEOUT | 30 (seconds) |
| jq path | JQ_PATH | jq.exe (on PATH) |

### 1.6 Limitations vs Other Bridges

| Feature | CMD | Bash | PowerShell | Python | Node.js | Kotlin |
|---------|-----|------|-----------|--------|---------|--------|
| Health check (background) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| embed_images | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ |
| Exponential backoff | Basic (fixed 5s) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Max reconnect attempts | 3 | ∞ | ∞ | ∞ | ∞ | ∞ |
| JSON parsing | Limited | jq | Built-in | Built-in | Built-in | Built-in |

---

## 2. Appendix

### Diagram Index

| # | Diagram | Image | Source |
|---|---------|-------|--------|
| 1 | System Context | [system-context.png](diagrams/system-context.png) | [system-context.drawio](diagrams/system-context.drawio) |
