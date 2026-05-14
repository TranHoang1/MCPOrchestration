# ─────────────────────────────────────────────────────────────
# MCP Orchestrator Server — Start Script (PowerShell)
# Loads .env file, then starts the orchestrator JAR.
# Usage: .\start-orchestrator.ps1 [-Port 9180] [-DbHost localhost]
# ─────────────────────────────────────────────────────────────

param(
    [int]$Port,
    [string]$Config,
    [string]$DbHost,
    [int]$DbPort,
    [string]$DbName,
    [string]$DbUser,
    [string]$DbPass,
    [string]$EmbeddingUrl
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# === Load .env file ===
$EnvFile = Join-Path $ScriptDir ".env"
if (-not (Test-Path $EnvFile)) {
    $Example = Join-Path $ScriptDir ".env.example"
    if (Test-Path $Example) {
        Write-Host "[orchestrator] .env not found, copying from .env.example"
        Copy-Item $Example $EnvFile
    } else {
        Write-Host "[orchestrator] WARNING: No .env file found, using system environment only"
    }
}

if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#")) {
            $parts = $line -split "=", 2
            if ($parts.Count -eq 2) {
                [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), "Process")
            }
        }
    }
    Write-Host "[orchestrator] Loaded environment from .env"
}

# === Apply defaults ===
if (-not $env:ORCHESTRATOR_PORT) { $env:ORCHESTRATOR_PORT = "9180" }
if (-not $env:ORCHESTRATOR_TRANSPORT) { $env:ORCHESTRATOR_TRANSPORT = "http-streamable" }
if (-not $env:DB_HOST) { $env:DB_HOST = "localhost" }
if (-not $env:DB_PORT) { $env:DB_PORT = "5432" }
if (-not $env:DB_NAME) { $env:DB_NAME = "jira_assistant" }
if (-not $env:DB_USERNAME) { $env:DB_USERNAME = "postgres" }
if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = "postgres" }
if (-not $env:EMBEDDING_PROVIDER) { $env:EMBEDDING_PROVIDER = "ollama" }
if (-not $env:EMBEDDING_MODEL) { $env:EMBEDDING_MODEL = "nomic-embed-text:latest" }
if (-not $env:EMBEDDING_BASE_URL) { $env:EMBEDDING_BASE_URL = "http://localhost:11434" }
if (-not $env:EMBEDDING_DIMENSIONS) { $env:EMBEDDING_DIMENSIONS = "768" }
if (-not $env:EMBEDDING_API_KEY) { $env:EMBEDDING_API_KEY = "unused" }

# === Override from CLI params ===
if ($Port) { $env:ORCHESTRATOR_PORT = $Port }
if ($DbHost) { $env:DB_HOST = $DbHost }
if ($DbPort) { $env:DB_PORT = $DbPort }
if ($DbName) { $env:DB_NAME = $DbName }
if ($DbUser) { $env:DB_USERNAME = $DbUser }
if ($DbPass) { $env:DB_PASSWORD = $DbPass }
if ($EmbeddingUrl) { $env:EMBEDDING_BASE_URL = $EmbeddingUrl }

# === Resolve composite vars ===
if (-not $env:DB_URL) {
    $env:DB_URL = "jdbc:postgresql://$($env:DB_HOST):$($env:DB_PORT)/$($env:DB_NAME)"
}

# === Config file ===
$ConfigFile = if ($Config) { $Config } else { Join-Path $ScriptDir "application.yml" }

# === Display config ===
Write-Host "[orchestrator] ──────────────────────────────────────"
Write-Host "[orchestrator] Port:      $($env:ORCHESTRATOR_PORT)"
Write-Host "[orchestrator] Transport: $($env:ORCHESTRATOR_TRANSPORT)"
Write-Host "[orchestrator] DB:        $($env:DB_HOST):$($env:DB_PORT)/$($env:DB_NAME)"
Write-Host "[orchestrator] Embedding: $($env:EMBEDDING_BASE_URL) ($($env:EMBEDDING_MODEL))"
Write-Host "[orchestrator] Config:    $ConfigFile"
Write-Host "[orchestrator] ──────────────────────────────────────"

# === Start server ===
$JarPath = Join-Path $ScriptDir "mcp-orchestrator-all.jar"
java -jar $JarPath --config $ConfigFile
