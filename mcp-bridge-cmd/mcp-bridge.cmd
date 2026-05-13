@echo off
setlocal enabledelayedexpansion

:: ============================================================
:: MCP Bridge Client — Windows CMD
:: Connects to MCP Orchestrator via HTTP Streamable transport.
:: Exposes tools locally via stdio MCP server.
:: ============================================================

set "VERSION=1.0.0"
set "STATE=DISCONNECTED"
set "SESSION_ID="
set "PING_ID=0"
set "CONSECUTIVE_FAILURES=0"

:: === Configuration ===
if "%ORCHESTRATOR_URL%"=="" set "ORCHESTRATOR_URL=http://localhost:8080"
if "%BRIDGE_TIMEOUT%"=="" set "BRIDGE_TIMEOUT=30"
if "%BRIDGE_PING_INTERVAL%"=="" set "BRIDGE_PING_INTERVAL=30"
if "%BRIDGE_PING_TIMEOUT%"=="" set "BRIDGE_PING_TIMEOUT=5"
if "%MCP_BRIDGE_TOKEN%"=="" set "MCP_BRIDGE_TOKEN="

:: Parse arguments
:parse_args
if "%~1"=="" goto :args_done
if "%~1"=="--url" (set "ORCHESTRATOR_URL=%~2" & shift & shift & goto :parse_args)
if "%~1"=="--token" (set "MCP_BRIDGE_TOKEN=%~2" & shift & shift & goto :parse_args)
if "%~1"=="--timeout" (set "BRIDGE_TIMEOUT=%~2" & shift & shift & goto :parse_args)
if "%~1"=="--ping-interval" (set "BRIDGE_PING_INTERVAL=%~2" & shift & shift & goto :parse_args)
if "%~1"=="--help" (goto :usage)
shift
goto :parse_args
:args_done

echo [mcp-bridge] MCP Bridge Client (CMD) v%VERSION% starting... >&2
echo [mcp-bridge] Connecting to orchestrator at: %ORCHESTRATOR_URL% >&2

:: Build auth header if token provided
set "AUTH_HEADER="
if not "%MCP_BRIDGE_TOKEN%"=="" (
    set "AUTH_HEADER=-H "Authorization: Bearer %MCP_BRIDGE_TOKEN%""
    echo [mcp-bridge] Using JWT authentication >&2
) else (
    echo [mcp-bridge] Warning: No token provided >&2
)

:: === Initial Connection ===
call :initialize_session
if !errorlevel! neq 0 (
    echo [mcp-bridge] Connection attempt 1 failed, retrying... >&2
    timeout /t 1 /nobreak >nul
    call :initialize_session
    if !errorlevel! neq 0 (
        echo [mcp-bridge] Connection attempt 2 failed, retrying... >&2
        timeout /t 2 /nobreak >nul
        call :initialize_session
        if !errorlevel! neq 0 (
            echo [mcp-bridge] Failed initial connection >&2
        )
    )
)

echo [mcp-bridge] Bridge MCP server ready (stdio transport) >&2

:: === Main Loop ===
:main_loop
set "line="
set /p "line="
if "!line!"=="" goto :main_loop
if "!line!"=="" goto :eof

:: Check for jq.exe — required for full functionality
where jq >nul 2>&1
if !errorlevel! equ 0 (
    call :main_loop_jq
) else (
    echo [mcp-bridge] WARNING: jq.exe not found on PATH >&2
    echo [mcp-bridge] Running in BASIC mode — limited functionality (proxy-only, no argument parsing) >&2
    echo [mcp-bridge] >&2
    echo [mcp-bridge] Install jq for full functionality: >&2
    echo [mcp-bridge]   winget install jqlang.jq >&2
    echo [mcp-bridge]   -- or -- >&2
    echo [mcp-bridge]   scoop install jq >&2
    echo [mcp-bridge]   -- or -- >&2
    echo [mcp-bridge]   Download from https://jqlang.github.io/jq/download/ >&2
    echo [mcp-bridge] >&2
    call :main_loop_basic
)
goto :eof

:: === Main Loop with jq ===
:main_loop_jq
:read_loop_jq
set "line="
set /p "line="
if "!line!"=="" goto :read_loop_jq

for /f "delims=" %%m in ('echo !line! ^| jq -r ".method // empty"') do set "method=%%m"
for /f "delims=" %%i in ('echo !line! ^| jq -r ".id // empty"') do set "req_id=%%i"

if "!method!"=="initialize" (
    call :handle_initialize !req_id!
) else if "!method!"=="initialized" (
    rem notification, no response
) else if "!method!"=="tools/list" (
    call :handle_tools_list !req_id!
) else if "!method!"=="tools/call" (
    for /f "delims=" %%n in ('echo !line! ^| jq -r ".params.name"') do set "tool_name=%%n"
    for /f "delims=" %%a in ('echo !line! ^| jq -c ".params.arguments // {}"') do set "tool_args=%%a"
    call :handle_tool_call !req_id! !tool_name! "!tool_args!"
) else (
    if not "!req_id!"=="" call :json_error !req_id! -32601 "Method not found"
)
goto :read_loop_jq

:: === Main Loop Basic (no jq) ===
:main_loop_basic
:read_loop_basic
set "line="
set /p "line="
if "!line!"=="" goto :read_loop_basic

:: Very basic method detection
echo !line! | findstr /c:"initialize" >nul && (
    echo !line! | findstr /c:"tools/list" >nul || (
        echo !line! | findstr /c:"tools/call" >nul || (
            call :handle_initialize 1
            goto :read_loop_basic
        )
    )
)
echo !line! | findstr /c:"tools/list" >nul && (
    call :handle_tools_list 1
    goto :read_loop_basic
)
echo !line! | findstr /c:"tools/call" >nul && (
    call :handle_tool_call_proxy 1 "!line!"
    goto :read_loop_basic
)
goto :read_loop_basic

:: === Handlers ===
:handle_initialize
echo {"jsonrpc":"2.0","id":%~1,"result":{"protocolVersion":"2025-03-26","capabilities":{"tools":{"listChanged":true}},"serverInfo":{"name":"mcp-bridge-cmd","version":"1.0.0"}}}
goto :eof

:handle_tools_list
echo {"jsonrpc":"2.0","id":%~1,"result":{"tools":[{"name":"find_tools","description":"Search for available tools","inputSchema":{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}},{"name":"execute_dynamic_tool","description":"Execute a tool on an upstream MCP server","inputSchema":{"type":"object","properties":{"tool_name":{"type":"string"},"arguments":{"type":"object"}},"required":["tool_name"]}},{"name":"stream_write_file","description":"Write content to a file","inputSchema":{"type":"object","properties":{"file_path":{"type":"string"},"content":{"type":"string"},"mode":{"type":"string"}},"required":["file_path"]}}]}}
goto :eof

:handle_tool_call
set "tc_id=%~1"
set "tc_name=%~2"
set "tc_args=%~3"

if "!tc_name!"=="stream_write_file" (
    call :handle_stream_write !tc_id! "!tc_args!"
) else (
    call :proxy_to_orchestrator !tc_id! !tc_name! "!tc_args!"
)
goto :eof

:handle_stream_write
set "sw_id=%~1"
set "sw_args=%~2"
for /f "delims=" %%p in ('echo !sw_args! ^| jq -r ".file_path // empty"') do set "sw_path=%%p"
for /f "delims=" %%c in ('echo !sw_args! ^| jq -r ".content // empty"') do set "sw_content=%%c"
if "!sw_path!"=="" (
    call :json_error !sw_id! -1 "file_path is required"
    goto :eof
)
echo !sw_content!> "!sw_path!"
echo {"jsonrpc":"2.0","id":!sw_id!,"result":{"content":[{"type":"text","text":"{\"status\":\"ok\",\"path\":\"!sw_path!\"}"}]}}
goto :eof

:handle_tool_call_proxy
set "proxy_id=%~1"
set "proxy_line=%~2"
curl -s -m %BRIDGE_TIMEOUT% -X POST -H "Content-Type: application/json" %AUTH_HEADER% -d "!proxy_line!" "%ORCHESTRATOR_URL%/mcp" 2>nul
goto :eof

:proxy_to_orchestrator
set "po_id=%~1"
set "po_name=%~2"
set "po_args=%~3"
set "po_body={\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"tools/call\",\"params\":{\"name\":\"!po_name!\",\"arguments\":!po_args!}}"
for /f "delims=" %%r in ('curl -s -m %BRIDGE_TIMEOUT% -X POST -H "Content-Type: application/json" %AUTH_HEADER% -d "!po_body!" "%ORCHESTRATOR_URL%/mcp" 2^>nul') do set "po_response=%%r"
if "!po_response!"=="" (
    call :json_error !po_id! -1 "No response from orchestrator"
) else (
    for /f "delims=" %%v in ('echo !po_response! ^| jq -c ".result // .error"') do set "po_result=%%v"
    echo {"jsonrpc":"2.0","id":!po_id!,"result":!po_result!}
)
goto :eof

:: === HTTP ===
:initialize_session
set "init_body={\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"mcp-bridge-cmd\",\"version\":\"1.0.0\"}}}"
for /f "delims=" %%r in ('curl -s -m %BRIDGE_TIMEOUT% -X POST -H "Content-Type: application/json" %AUTH_HEADER% -d "!init_body!" "%ORCHESTRATOR_URL%/mcp" 2^>nul') do set "init_response=%%r"
if "!init_response!"=="" exit /b 1
echo !init_response! | findstr /c:"result" >nul
if !errorlevel! equ 0 (
    set "STATE=CONNECTED"
    echo [mcp-bridge] State: DISCONNECTED -^> CONNECTED (reason: initialized) >&2
    exit /b 0
)
exit /b 1

:: === Helpers ===
:json_error
echo {"jsonrpc":"2.0","id":%~1,"error":{"code":%~2,"message":"%~3"}}
goto :eof

:usage
echo MCP Bridge Client (CMD) v%VERSION%
echo Usage: mcp-bridge.cmd [OPTIONS]
echo Options:
echo   --url URL         Orchestrator URL (default: http://localhost:8080)
echo   --timeout SEC     Request timeout (default: 30)
echo   --ping-interval   Health check interval (default: 30, 0=disabled)
echo   --help            Show this help
goto :eof
