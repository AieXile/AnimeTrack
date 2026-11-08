# Subscription identity refactor: local preflight checks.
# The server source lives under server/, so these checks can run in CI too.
# Run this before deploying the server module.
#
# Usage (repository root):
#   powershell -ExecutionPolicy Bypass -File docs/migrations/subscription-identity/run-checks.ps1

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
$serverDir = Join-Path $root 'server\subscriptions'
$testDir = Join-Path $PSScriptRoot 'tests'

if (-not (Test-Path $serverDir)) {
    Write-Error "Missing server directory: $serverDir"
}

Write-Host '[1/3] node --check'
$failed = 0
Get-ChildItem -Recurse -Filter *.js -Path $serverDir, $testDir | ForEach-Object {
    node --check $_.FullName
    if ($LASTEXITCODE -ne 0) {
        Write-Host ('  FAIL ' + $_.FullName) -ForegroundColor Red
        $failed = 1
    }
}
if ($failed -ne 0) { exit 1 }

Write-Host '[2/3] scenario tests'
node (Join-Path $testDir 'identity.test.js')
if ($LASTEXITCODE -ne 0) { exit 1 }
node (Join-Path $testDir 'serviceScenarios.test.js')
if ($LASTEXITCODE -ne 0) { exit 1 }

Write-Host '[3/3] user_subscriptions write SQL scan'
# Runtime writes must go through subscriptionRepository.js. schema.js owns DDL/cleanup,
# and backfill.js is the one-off migration script (bulk UPDATE for performance).
$allowed = @('subscriptionRepository.js', 'schema.js', 'backfill.js')
$pattern = '(INSERT|UPDATE|DELETE)\s+(INTO\s+|FROM\s+)?user_subscriptions'
Get-ChildItem -Filter *.js -Path $serverDir | ForEach-Object {
    if ($allowed -contains $_.Name) { return }
    $hits = Select-String -Path $_.FullName -Pattern $pattern -AllMatches
    if ($hits) {
        Write-Host ('  FAIL ' + $_.Name + ' writes user_subscriptions directly:') -ForegroundColor Red
        foreach ($hit in $hits) {
            Write-Host ('    ' + $hit.LineNumber + ': ' + $hit.Line.Trim())
        }
        $failed = 1
    }
}
if ($failed -ne 0) { exit 1 }

Write-Host 'All checks passed' -ForegroundColor Green
