<#
.SYNOPSIS
    Create the AirRelay release signing key and wire it into local.properties.

.DESCRIPTION
    The release keystore identifies every future update of this application. Two rules matter:

    1. Back it up. Losing it means no existing installation can ever be updated again; the only
       recovery is publishing under a new application id.
    2. Never regenerate it. A different key cannot update installations made with the old one.

    The password is read interactively and is never echoed, never written to the console and never
    passed on a command line (where other processes could read it). It is stored only in
    local.properties, which is gitignored, because that is the file the Gradle build reads.

.PARAMETER KeystorePath
    Where to create the keystore. Defaults to keystore/airrelay-release.jks (gitignored).

.PARAMETER Alias
    Key alias. Defaults to airrelay.

.PARAMETER ValidityDays
    Certificate validity. Defaults to 10000 days (about 27 years), as stores require a long window.

.EXAMPLE
    pwsh -File scripts/create_release_keystore.ps1
#>
[CmdletBinding()]
param(
    [string]$KeystorePath = 'keystore/airrelay-release.jks',
    [string]$Alias = 'airrelay',
    [int]$ValidityDays = 10000,
    [string]$DistinguishedName = 'CN=AirRelay, OU=Receiver, O=AirRelay, C=TR'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
    if (-not (Test-Path -LiteralPath $keytool -PathType Leaf)) {
        $candidate = Get-Command keytool -ErrorAction SilentlyContinue
        if (-not $candidate) { throw "keytool not found. Set JAVA_HOME to a JDK 17 installation." }
        $keytool = $candidate.Source
    }

    $keystoreFull = [System.IO.Path]::GetFullPath((Join-Path $root $KeystorePath))
    $exists = Test-Path -LiteralPath $keystoreFull
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $keystoreFull) | Out-Null

    Write-Host 'The password protects the release key. Use a long one and store it in a password manager.' -ForegroundColor Yellow
    if ($exists) {
        # An existing key must never be regenerated: a new key cannot update installations signed
        # with the old one. Re-running this script only verifies the password and (re)wires
        # local.properties, which is what makes it safe to run twice.
        Write-Host "Keystore already exists: $keystoreFull" -ForegroundColor Yellow
        Write-Host 'Verifying the password and re-wiring local.properties; the key itself is untouched.' -ForegroundColor Yellow
        $secure = Read-Host -Prompt 'Existing keystore password' -AsSecureString
        $plain = [System.Net.NetworkCredential]::new('', $secure).Password
        & $keytool -list -keystore $keystoreFull -alias $Alias -storepass $plain 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'That password does not open the existing keystore; nothing was changed.' }
        Write-Host 'Password verified.' -ForegroundColor Green
    } else {
        $secure = Read-Host -Prompt 'Release keystore password' -AsSecureString
        $again = Read-Host -Prompt 'Repeat the password' -AsSecureString
        $plain = [System.Net.NetworkCredential]::new('', $secure).Password
        $plainAgain = [System.Net.NetworkCredential]::new('', $again).Password
        if ($plain -ne $plainAgain) { throw 'The passwords do not match.' }
        if ($plain.Length -lt 12) { throw 'Use at least 12 characters for a release key.' }

        Write-Host "Generating the key (RSA 4096, $ValidityDays days)..." -ForegroundColor Cyan
        & $keytool -genkeypair -v -keystore $keystoreFull -alias $Alias -keyalg RSA -keysize 4096 `
            -validity $ValidityDays -dname $DistinguishedName -storepass $plain -keypass $plain
        if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }
    }

    $localProperties = Join-Path $root 'local.properties'
    # Get-Content returns a fixed-size array; adding to it throws "Collection was of a fixed size".
    # Build a real List and copy the lines into it.
    $lines = [System.Collections.Generic.List[string]]::new()
    if (Test-Path -LiteralPath $localProperties) {
        foreach ($line in (Get-Content -LiteralPath $localProperties)) { $lines.Add([string]$line) }
    }
    $keystorePosix = $keystoreFull.Replace('\', '/')
    foreach ($entry in @("storeFile=$keystorePosix", "storePassword=$plain", "keyAlias=$Alias", "keyPassword=$plain")) {
        $key = ($entry -split '=', 2)[0]
        $index = -1
        for ($i = 0; $i -lt $lines.Count; $i++) {
            if ($lines[$i] -match "^\s*$([regex]::Escape($key))\s*=") { $index = $i; break }
        }
        if ($index -ge 0) { $lines[$index] = $entry } else { $lines.Add($entry) }
    }
    Set-Content -LiteralPath $localProperties -Value $lines -Encoding utf8

    Write-Host ''
    Write-Host 'Release signing is configured.' -ForegroundColor Green
    Write-Host "  keystore : $keystoreFull"
    Write-Host "  alias    : $Alias"
    Write-Host "  wired to : local.properties (gitignored)"
    Write-Host ''
    Write-Host 'Back the keystore up somewhere safe now, together with the password.' -ForegroundColor Yellow
    Write-Host 'Build and verify with:' -ForegroundColor Cyan
    Write-Host '  .\scripts\build_receiver.ps1 -Variant Release -Abis "arm64-v8a,armeabi-v7a"'
    Write-Host '  apksigner verify --print-certs <apk>'
} finally {
    Pop-Location
}
