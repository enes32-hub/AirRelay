<#
.SYNOPSIS
    Verify a built APK against the release checklist before it is installed or published.

.DESCRIPTION
    Checks the things that silently produce an unusable release:

    * the shipped ABIs are exactly the ones requested (an x86_64-only release is a bug),
    * the APK is signed, and by which certificate,
    * the native libraries satisfy the 16 KB page-size requirement (Android 15+ devices),
    * the package identity is the frozen one and the version metadata is sane,
    * the release build is not debuggable.

    It does not install anything and does not certify functionality; step 3 of
    docs/RELEASE_RUNBOOK.md covers the on-device run.

.PARAMETER Apk
    Path to the APK to verify.

.PARAMETER ExpectedAbis
    Comma-separated ABI list that must appear in the APK exactly.

.PARAMETER ExpectRelease
    Fail when the APK is debuggable or when the signature is the Android debug key.

.EXAMPLE
    pwsh -File scripts/verify_release_artifact.ps1 -Apk C:\AirRelayRelease\app\build\outputs\apk\release\app-release.apk -ExpectedAbis 'arm64-v8a,armeabi-v7a' -ExpectRelease
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [string]$ExpectedAbis = '',
    [switch]$ExpectRelease
)

$ErrorActionPreference = 'Stop'
$checks = @()

function Add-Check([string]$Name, [bool]$Ok, [string]$Detail) {
    $script:checks += [pscustomobject]@{ Check = $Name; Ok = $Ok; Detail = $Detail }
    $colour = if ($Ok) { 'Green' } else { 'Red' }
    Write-Host ("  {0,-34} {1}  {2}" -f $Name, $(if ($Ok) { 'PASS' } else { 'FAIL' }), $Detail) -ForegroundColor $colour
}

if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) { throw "APK not found: $Apk" }
$apkFull = [System.IO.Path]::GetFullPath($Apk)
Write-Host "Verifying $apkFull" -ForegroundColor Cyan
Write-Host ("  size: {0:N2} MB" -f ((Get-Item -LiteralPath $apkFull).Length / 1MB))

$sdk = Join-Path (Join-Path $env:USERPROFILE 'AppData\Local') 'Android\Sdk'
$buildTools = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory -ErrorAction SilentlyContinue |
    Sort-Object { [version]($_.Name -replace '[^0-9.]', '') } -Descending
if (-not $buildTools) { throw "No Android build-tools found under $sdk" }
$tools = $buildTools[0].FullName
$aapt = Join-Path $tools 'aapt2.exe'
$apksigner = Join-Path $tools 'apksigner.bat'
$zipalign = Join-Path $tools 'zipalign.exe'
Write-Host "  build-tools: $($buildTools[0].Name)"

$badging = & $aapt dump badging $apkFull 2>&1 | Out-String

$packageLine = ($badging -split "`n" | Where-Object { $_ -like 'package:*' } | Select-Object -First 1)
$applicationId = if ($packageLine -match "name='([^']+)'") { $Matches[1] } else { '' }
Add-Check 'application id frozen' ($applicationId -eq 'com.airrelay.receiver') $applicationId

if ($packageLine -match "versionCode='(\d+)'") { $versionCode = [int]$Matches[1] } else { $versionCode = -1 }
if ($packageLine -match "versionName='([^']+)'") { $versionName = $Matches[1] } else { $versionName = '' }
# The versionCode only has to increase relative to builds that were already distributed: Android
# refuses an update whose code is not strictly higher. This is the first public release, so a small
# positive code is correct; a later release must raise it (e.g. 1 -> 2).
Add-Check 'versionCode is a positive integer' ($versionCode -ge 1) "versionCode=$versionCode"
Add-Check 'version name set' ($versionName -ne '') $versionName

$nativeLine = ($badging -split "`n" | Where-Object { $_ -like 'native-code:*' } | Select-Object -First 1)
# aapt2 prints one quoted ABI per entry: native-code: 'arm64-v8a' 'armeabi-v7a'
$abis = @()
if ($nativeLine) {
    $abis = @([regex]::Matches($nativeLine, "'([^']+)'") | ForEach-Object { $_.Groups[1].Value })
}
if ($ExpectedAbis) {
    $expected = $ExpectedAbis.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ }
    $missing = @($expected | Where-Object { $_ -notin $abis })
    $extra = @($abis | Where-Object { $_ -notin $expected })
    Add-Check 'shipped ABIs exact' (($missing.Count -eq 0) -and ($extra.Count -eq 0)) ("got " + ($abis -join ',') + "; missing " + ($missing -join ',') + "; extra " + ($extra -join ','))
} else {
    Add-Check 'native ABIs present' ($abis.Count -gt 0) ($abis -join ',')
}

$debuggable = ($badging -split "`n" | Where-Object { $_ -like '*application-debuggable*' }).Count -gt 0
if ($ExpectRelease) { Add-Check 'not debuggable' (-not $debuggable) $(if ($debuggable) { 'application-debuggable present' } else { 'no debuggable flag' }) }

& $apksigner verify --print-certs $apkFull 2>&1 | Out-Null
$signOk = $LASTEXITCODE -eq 0
$certOutput = & $apksigner verify --print-certs $apkFull 2>&1 | Out-String
$signer = if ($certOutput -match 'certificate DN: ([^\r\n]+)') { $Matches[1].Trim() } else { '' }
Add-Check 'signature verifies' $signOk $signer
if ($ExpectRelease) {
    $isDebugKey = $certOutput -match 'CN=Android Debug'
    Add-Check 'signed with release key' (-not $isDebugKey) $(if ($isDebugKey) { 'Android Debug certificate' } else { 'not the debug certificate' })
}

& $zipalign -c -P 16 -v 4 $apkFull 2>&1 | Out-Null
Add-Check '16 KB page alignment' ($LASTEXITCODE -eq 0) 'zipalign -c -P 16'

Write-Host ''
$failed = @($checks | Where-Object { -not $_.Ok })
if ($failed.Count -gt 0) {
    Write-Host "$($failed.Count) check(s) FAILED." -ForegroundColor Red
    exit 1
}
Write-Host "All artifact checks passed." -ForegroundColor Green
