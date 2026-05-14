@echo off
REM ─────────────────────────────────────────────────────────────
REM MCP Orchestrator Server — Start Script (Windows)
REM Loads .env file, then starts the orchestrator JAR.
REM Usage: start-orchestrator.cmd [--port 9180] [--config application.yml]
REM ─────────────────────────────────────────────────────────────

setlocal enabledelayedexpansion

REM === Load .env file (if exists) ===
set "ENV_FILE=%~dp0.env"
if not exist "%ENV_FILE%" (
    if exist "%~dp0.env.example" (
        echo [orchestrator] .env not found, copying from .env.example
        copy "%~dp0.env.example" "%ENV_FILE%" >nul
    ) else (
        echo [orchestrator] WARNING: No .env file found, using system environment only
    )
)

if exist "%ENV_FILE%" (
    for /f "usebackq tokens=1,* delims==" %%a in ("%ENV_FILE%") do (
        set "line=%%a"
        if not "!line:~0,1!"=="#" (
            if not "%%a"=="" (
                set "%%a=%%b"
            )
        )
    )
    echo [orchestrator] Loaded environment from .env
)

REM === Default values (fallback if not in .env or system env) ===
if not defined ORCHESTRATOR_PORT set "ORCHESTRATOR_PORT=9180"
if not defined ORCHESTRATOR_TRANSPORT set "ORCHESTRATOR_TRANSPORT=http-streamable"
if not defined DB_HOST set "DB_HOST=localhost"
if not defined DB_PORT set "DB_PORT=5432"
if not defined DB_NAME set "DB_NAME=jira_assistant"
if not defined DB_USERNAME set "DB_USERNAME=postgres"
if not defined DB_PASSWORD set "DB_PASSWORD=postgres"
if not defined EMBEDDING_PROVIDER set "EMBEDDING_PROVIDER=ollama"
if not defined EMBEDDING_MODEL set "EMBEDDING_MODEL=nomic-embed-text:latest"
if not defined EMBEDDING_BASE_URL set "EMBEDDING_BASE_URL=http://localhost:11434"
if not defined EMBEDDING_DIMENSIONS set "EMBEDDING_DIMENSIONS=768"
if not defined EMBEDDING_API_KEY set "EMBEDDING_API_KEY=unused"

REM === Parse CLI arguments (override env) ===
:parse_args
if "%~1"=="" goto :done_args
if "%~1"=="--port" (
    set "ORCHESTRATOR_PORT=%~2"
    shift & shift & goto :parse_args
)
if "%~1"=="--config" (
    set "CONFIG_FILE=%~2"
    shift & shift & goto :parse_args
)
if "%~1"=="--db-host" (
    set "DB_HOST=%~2"
    shift & shift & goto :parse_args
)
if "%~1"=="--db-port" (
    set "DB_PORT=%~2"
    shift & shift & goto :parse_args
)
if "%~1"=="--db-name" (
    set "DB_NAME=%~2"
    shift & shift & goto :parse_args
)
if "%~1"=="--db-user" (
    set "DB_USERNAME=%~2"
    shift & shift & goto :parse_args
)
if "%~1"=="--db-pass" (
    set "DB_PASSWORD=%~2"
    shift & shift & goto :parse_args
)
if "%~1"=="--embedding-url" (
    set "EMBEDDING_BASE_URL=%~2"
    shift & shift & goto :parse_args
)
shift & goto :parse_args
:done_args

REM === Resolve composite vars ===
if not defined DB_URL set "DB_URL=jdbc:postgresql://%DB_HOST%:%DB_PORT%/%DB_NAME%"

REM === Config file ===
if not defined CONFIG_FILE set "CONFIG_FILE=%~dp0application.yml"

REM === Display config ===
echo [orchestrator] ──────────────────────────────────────
echo [orchestrator] Port:      %ORCHESTRATOR_PORT%
echo [orchestrator] Transport: %ORCHESTRATOR_TRANSPORT%
echo [orchestrator] DB:        %DB_HOST%:%DB_PORT%/%DB_NAME%
echo [orchestrator] Embedding: %EMBEDDING_BASE_URL% (%EMBEDDING_MODEL%)
echo [orchestrator] Config:    %CONFIG_FILE%
echo [orchestrator] ──────────────────────────────────────

REM === Start server ===
java -jar "%~dp0mcp-orchestrator-all.jar" "--config=%CONFIG_FILE%"
