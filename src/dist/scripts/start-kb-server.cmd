@echo off
REM ─────────────────────────────────────────────────────────────
REM KB Server — Start Script (Windows)
REM Loads .env file, then starts the kb-server JAR.
REM Usage: start-kb-server.cmd [--port 9181] [--config kb-server.yml]
REM ─────────────────────────────────────────────────────────────

setlocal enabledelayedexpansion

REM === Load .env file (if exists) ===
set "ENV_FILE=%~dp0.env"
if not exist "%ENV_FILE%" (
    if exist "%~dp0.env.example" (
        echo [kb-server] .env not found, copying from .env.example
        copy "%~dp0.env.example" "%ENV_FILE%" >nul
    ) else (
        echo [kb-server] WARNING: No .env file found, using system environment only
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
    echo [kb-server] Loaded environment from .env
)

REM === Default values (fallback if not in .env or system env) ===
if not defined KB_SERVER_PORT set "KB_SERVER_PORT=9181"
if not defined KB_SERVER_TRANSPORT set "KB_SERVER_TRANSPORT=stdio"
if not defined DB_HOST set "DB_HOST=localhost"
if not defined DB_PORT set "DB_PORT=5432"
if not defined DB_NAME set "DB_NAME=jira_assistant"
if not defined DB_SCHEMA set "DB_SCHEMA=public"
if not defined DB_USERNAME set "DB_USERNAME=postgres"
if not defined DB_PASSWORD set "DB_PASSWORD=postgres"
if not defined EMBEDDING_PROVIDER set "EMBEDDING_PROVIDER=ollama"
if not defined EMBEDDING_MODEL set "EMBEDDING_MODEL=nomic-embed-text"
if not defined EMBEDDING_BASE_URL set "EMBEDDING_BASE_URL=http://localhost:11434"
if not defined EMBEDDING_DIMENSIONS set "EMBEDDING_DIMENSIONS=768"
if not defined VECTOR_DB_PROVIDER set "VECTOR_DB_PROVIDER=pgvector"
if not defined VECTOR_DB_COLLECTION set "VECTOR_DB_COLLECTION=kb_entries"
if not defined SEGMENTATION_PROVIDER set "SEGMENTATION_PROVIDER=ollama"
if not defined SEGMENTATION_MODEL set "SEGMENTATION_MODEL=llama3"
if not defined KB_ENCRYPTION_KEY set "KB_ENCRYPTION_KEY=sMARARO7oHOnD6W2bCPYNSk2F552azl2d1dyVHLG6+w="
if not defined KB_BR_ENCRYPTION_KEY set "KB_BR_ENCRYPTION_KEY=sMARARO7oHOnD6W2bCPYNSk2F552azl2d1dyVHLG6+w="

REM === Parse CLI arguments (override env) ===
:parse_args
if "%~1"=="" goto :done_args
if "%~1"=="--port" (
    set "KB_SERVER_PORT=%~2"
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
if "%~1"=="--encryption-key" (
    set "KB_ENCRYPTION_KEY=%~2"
    shift & shift & goto :parse_args
)
shift & goto :parse_args
:done_args

REM === Resolve composite vars ===
if not defined DB_URL set "DB_URL=jdbc:postgresql://%DB_HOST%:%DB_PORT%/%DB_NAME%"

REM === Config file ===
if not defined CONFIG_FILE set "CONFIG_FILE=%~dp0kb-server.yml"

REM === Display config ===
echo [kb-server] ──────────────────────────────────────
echo [kb-server] Port:       %KB_SERVER_PORT%
echo [kb-server] Transport:  %KB_SERVER_TRANSPORT%
echo [kb-server] DB:         %DB_HOST%:%DB_PORT%/%DB_NAME% (schema: %DB_SCHEMA%)
echo [kb-server] Embedding:  %EMBEDDING_BASE_URL% (%EMBEDDING_MODEL%)
echo [kb-server] Vector DB:  %VECTOR_DB_PROVIDER% / %VECTOR_DB_COLLECTION%
echo [kb-server] Config:     %CONFIG_FILE%
echo [kb-server] ──────────────────────────────────────

REM === Start server ===
java -jar "%~dp0kb-server-all.jar" --config "%CONFIG_FILE%"
