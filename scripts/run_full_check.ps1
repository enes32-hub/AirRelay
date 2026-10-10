<#
.SYNOPSIS
    Run every repository check that does not need a device, in one command.

.DESCRIPTION
    Order matters: provenance first (it can invalidate everything else), then the acceptance
    bookkeeping, then the generated artifacts, then the unit tests.

    The preparation gate is expected to pass; the release gate is expected to stay blocked until
    real acceptance evidence exists. Pass -Release to require the release gate to pass instead,
    which is what a release run must do.

.EXAMPLE
    pwsh -File scripts/run_full_check.ps1
    pwsh -File scripts/run_full_check.ps1 -Release
#>
[CmdletBinding()]
param(
    [string]$Python = 'python',
    [switch]$Release
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
$results = @()

function Invoke-Check {
    param([string]$Name, [string[]]$Arguments, [int[]]$ExpectedExit = @(0), [switch]$Capture)
    Write-Host ""
    Write-Host "=== $Name ===" -ForegroundColor Cyan
    $output = & $Python @Arguments 2>&1
    $code = $LASTEXITCODE
    $output | ForEach-Object { Write-Host "  $_" }
    $ok = $ExpectedExit -contains $code
    $script:results += [pscustomobject]@{ Check = $Name; Exit = $code; Ok = $ok }
    if (-not $ok) { Write-Host "  -> unexpected exit code $code (expected $($ExpectedExit -join '/'))" -ForegroundColor Red }
    return $ok
}

try {
    Invoke-Check 'Provenance and source checks' @('scripts/productization/check_source.py') | Out-Null
    Invoke-Check 'Acceptance bookkeeping (preparation)' @('scripts/check_readiness.py') | Out-Null
    Invoke-Check 'Generated SBOM is current' @('tools/generate_sbom.py', '--check') | Out-Null
    Invoke-Check 'Unit tests' @('-m', 'unittest', 'discover', '-s', 'tests') | Out-Null

    if ($Release) {
        Invoke-Check 'Release gate (must pass)' @('scripts/check_readiness.py', '--release') | Out-Null
    } else {
        Write-Host ""
        Write-Host "=== Release gate (informational) ===" -ForegroundColor Cyan
        $null = & $Python 'scripts/check_readiness.py' '--release' 2>&1
        $releaseCode = $LASTEXITCODE
        if ($releaseCode -eq 0) {
            Write-Host "  release gate passed" -ForegroundColor Green
        } else {
            Write-Host "  release gate blocked (exit $releaseCode) - expected until acceptance evidence exists" -ForegroundColor Yellow
        }
    }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "=== Summary ===" -ForegroundColor Cyan
$results | ForEach-Object {
    $label = if ($_.Ok) { 'PASS' } else { 'FAIL' }
    $colour = if ($_.Ok) { 'Green' } else { 'Red' }
    Write-Host ("  {0,-40} {1} (exit {2})" -f $_.Check, $label, $_.Exit) -ForegroundColor $colour
}
$failed = @($results | Where-Object { -not $_.Ok })
if ($failed.Count -gt 0) {
    Write-Host ""
    Write-Host "$($failed.Count) check(s) failed." -ForegroundColor Red
    exit 1
}
Write-Host ""
Write-Host "All non-device checks passed." -ForegroundColor Green
