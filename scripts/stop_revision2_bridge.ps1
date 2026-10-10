<# Graceful stop for the existing bridge. The running bridge removes its own console redirects.
   Does not close any emulator or call the historical helper hard-coded to console 5556. #>
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$bridgeStopFile = Join-Path $projectRoot 'artifacts\emulator-airplay-bridge.stop'
Set-Content -LiteralPath $bridgeStopFile -Value 'stop' -Encoding ascii
Write-Host 'Bridge stop requested. The emulator stays open; the bridge removes its own redirects.'
