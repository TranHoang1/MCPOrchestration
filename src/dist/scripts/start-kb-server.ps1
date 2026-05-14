# ─────────────────────────────────────────────────────────────
# KB Server — Start Script (PowerShell)
# Loads .env file, then starts the kb-server JAR.
# Usage: .\start-kb-server.ps1 [-Port 9181] [-DbHost localhost]
# ─────────────────────────────────────────────────────────────

param(
    [int]$Port,
    [string]$Config,
    [string]$DbHost,
    [int]$DbPort,
    [string]$DbName,
    [string]$DbSchema,
    [string]$DbUser,
    [string]$DbPass,
    [string]$EmbeddingUrl,
    [string]$EncryptionKey
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# === Load .env file ===
$EnvFile = Join-Path $ScriptDir ".env"
if (-not (Test-Path $EnvFile)) {
    $Example = Join-Path $ScriptDir ".env.example"
    if (Test-Path $Example) {
        Write-Host "[kb-server] .env not found, copying from .env.example"
        Copy-Item $Example $EnvFile
    } else {
        Write-Host "[kb-server] WARNING: No .env file found, using system environment only"
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
    Write-Host "[kb-server] Loaded environment from .env"
}

# === Apply defaults ===
if (-not $env:KB_SERVER_PORT) { $env:KB_SERVER_PORT = "9181" }
if (-not $env:KB_SERVER_TRANSPORT) { $env:KB_SERVER_TRANSPORT = "stdio" }
if (-not $env:DB_HOST) { $env:DB_HOST = "localhost" }
if (-not $env:DB_PORT) { $env:DB_PORT = "5432" }
if (-not $env:DB_NAME) { $env:DB_NAME = "jira_assistant" }
if (-not $env:DB_SCHEMA) { $env:DB_SCHEMA = "public" }
if (-not $env:DB_USERNAME) { $env:DB_USERNAME = "postgres" }
if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = "postgres" }
if (-not $env:EMBEDDING_PROVIDER) { $env:EMBEDDING_PROVIDER = "ollama" }
if (-not $env:EMBEDDING_MODEL) { $env:EMBEDDING_MODEL = "nomic-embed-text" }
if (-not $env:EMBEDDING_BASE_URL) { $env:EMBEDDING_BASE_URL = "http://localhost:11434" }
if (-not $env:EMBEDDING_DIMENSIONS) { $env:EMBEDDING_DIMENSIONS = "768" }
if (-not $env:VECTOR_DB_PROVIDER) { $env:VECTOR_DB_PROVIDER = "pgvector" }
if (-not $env:VECTOR_DB_COLLECTION) { $env:VECTOR_DB_COLLECTION = "kb_entries" }
if (-not $env:SEGMENTATION_PROVIDER) { $env:SEGMENTATION_PROVIDER = "ollama" }
if (-not $env:SEGMENTATION_MODEL) { $env:SEGMENTATION_MODEL = "llama3" }
if (-not $env:KB_ENCRYPTION_KEY) { $env:KB_ENCRYPTION_KEY = "sMARARO7oHOnD6W2bCPYNSk2F552azl2d1dyVHLG6+w=" }
if (-not $env:KB_BR_ENCRYPTION_KEY) { $env:KB_BR_ENCRYPTION_KEY = "sMARARO7oHOnD6W2bCPYNSk2F552azl2d1dyVHLG6+w=" }

# === Override from CLI params ===
if ($Port) { $env:KB_SERVER_PORT = $Port }
if ($DbHost) { $env:DB_HOST = $DbHost }
if ($DbPort) { $env:DB_PORT = $DbPort }
if ($DbName) { $env:DB_NAME = $DbName }
if ($DbSchema) { $env:DB_SCHEMA = $DbSchema }
if ($DbUser) { $env:DB_USERNAME = $DbUser }
if ($DbPass) { $env:DB_PASSWORD = $DbPass }
if ($EmbeddingUrl) { $env:EMBEDDING_BASE_URL = $EmbeddingUrl }
if ($EncryptionKey) { $env:KB_ENCRYPTION_KEY = $EncryptionKey }

# === Resolve composite vars ===
if (-not $env:DB_URL) {
    $env:DB_URL = "jdbc:postgresql://$($env:DB_HOST):$($env:DB_PORT)/$($env:DB_NAME)"
}

# === Config file ===
$ConfigFile = if ($Config) { $Config } else { Join-Path $ScriptDir "kb-server.yml" }

# === Display config ===
Write-Host "[kb-server] ──────────────────────────────────────"
Write-Host "[kb-server] Port:       $($env:KB_SERVER_PORT)"
Write-Host "[kb-server] Transport:  $($env:KB_SERVER_TRANSPORT)"
Write-Host "[kb-server] DB:         $($env:DB_HOST):$($env:DB_PORT)/$($env:DB_NAME) (schema: $($env:DB_SCHEMA))"
Write-Host "[kb-server] Embedding:  $($env:EMBEDDING_BASE_URL) ($($env:EMBEDDING_MODEL))"
Write-Host "[kb-server] Vector DB:  $($env:VECTOR_DB_PROVIDER) / $($env:VECTOR_DB_COLLECTION)"
Write-Host "[kb-server] Config:     $ConfigFile"
Write-Host "[kb-server] ──────────────────────────────────────"

# === Start server ===
$JarPath = Join-Path $ScriptDir "kb-server-all.jar"
java -jar $JarPath "--config=$ConfigFile"
