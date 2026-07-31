# Fetches the stable Stripe CLI webhook signing secret and writes it to .env
# as STRIPE_WEBHOOK_SECRET (Option A for local Docker).
#
# Usage (from repo root):
#   powershell -File scripts/fetch-stripe-webhook-secret.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env"

if (-not (Test-Path $envFile)) {
    throw ".env not found at $envFile"
}

$secretKeyLine = Get-Content $envFile | Where-Object { $_ -match '^STRIPE_SECRET_KEY=' } | Select-Object -First 1
if (-not $secretKeyLine) {
    throw "STRIPE_SECRET_KEY is missing from .env"
}

$stripeSecretKey = $secretKeyLine.Substring("STRIPE_SECRET_KEY=".Length).Trim()
if ([string]::IsNullOrWhiteSpace($stripeSecretKey)) {
    throw "STRIPE_SECRET_KEY is empty in .env. Paste your sk_test key first."
}

Write-Host "Fetching Stripe CLI webhook signing secret..."

$ErrorActionPreference = "Continue"
docker pull stripe/stripe-cli:latest | Out-Host
$raw = docker run --rm `
    -e "STRIPE_API_KEY=$stripeSecretKey" `
    stripe/stripe-cli:latest `
    listen --print-secret 2>&1 | Out-String
$exitCode = $LASTEXITCODE
$ErrorActionPreference = "Stop"

if ($exitCode -ne 0) {
    Write-Host $raw
    throw "stripe listen --print-secret failed (exit $exitCode)"
}

$match = [regex]::Match($raw, 'whsec_[A-Za-z0-9]+')
if (-not $match.Success) {
    Write-Host $raw
    throw "Could not find whsec_... in Stripe CLI output"
}

$webhookSecret = $match.Value

$lines = Get-Content $envFile
$updated = $false
$newLines = foreach ($line in $lines) {
    if ($line -match '^STRIPE_WEBHOOK_SECRET=') {
        $updated = $true
        "STRIPE_WEBHOOK_SECRET=$webhookSecret"
    } else {
        $line
    }
}

if (-not $updated) {
    $newLines += ""
    $newLines += "STRIPE_WEBHOOK_SECRET=$webhookSecret"
}

$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllLines($envFile, $newLines, $utf8NoBom)

Write-Host ""
Write-Host "STRIPE_WEBHOOK_SECRET (written to .env):"
Write-Host $webhookSecret
Write-Host ""
Write-Host "Compose passes this into the app as STRIPE_WEBHOOK_SECRET."
Write-Host "Restart with: docker compose up -d --build"
