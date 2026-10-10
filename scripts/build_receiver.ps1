param(
    # Comma-separated ABI list. A single ABI is valid: the Xiaomi TV box is 32-bit
    # ARM only (armeabi-v7a), so building arm64 as well would double the native time.
    [string]$Abis = 'x86_64',
    [string]$PythonExe = '',
    [string]$MsysRoot = 'C:\msys64',
    [string]$BuildRoot = (Join-Path $env:LOCALAPPDATA 'AirRelay-build'),
    [ValidateSet('Debug', 'Release')]
    [string]$Variant = 'Debug',
    [switch]$WithIntegrationTests
)

$ErrorActionPreference = 'Stop'
$validAbis = @('arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86')
$abiList = @($Abis.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })
if ($abiList.Count -eq 0) { throw 'At least one ABI is required.' }
foreach ($abi in $abiList) {
    if ($abi -notin $validAbis) { throw "Unsupported ABI '$abi'. Valid values: $($validAbis -join ', ')" }
}
if ($WithIntegrationTests -and $Variant -ne 'Debug') { throw 'Integration tests require Debug variant.' }
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$buildRootFull = [System.IO.Path]::GetFullPath($BuildRoot)
if ($buildRootFull -match '[^\x00-\x7F]') {
    throw 'BuildRoot must contain ASCII characters only; the native FFmpeg/OpenSSL tools are not reliable on Unicode paths.'
}

New-Item -ItemType Directory -Force -Path $buildRootFull | Out-Null

function Copy-Tree([string]$source, [string]$destination, [string[]]$excludedDirectories = @()) {
    $arguments = @($source, $destination, '/E')
    if ($excludedDirectories.Count -gt 0) {
        $arguments += '/XD'
        $arguments += $excludedDirectories
    }
    $arguments += '/XF', '.git', 'local.properties'
    $arguments += '/NFL', '/NDL', '/NJH', '/NJS'
    & "$env:SystemRoot\System32\robocopy.exe" @arguments | Out-Null
    if ($LASTEXITCODE -gt 7) { throw "Copy failed: $source (robocopy $LASTEXITCODE)" }
}

# robocopy /E copies but never deletes, so a source file that was removed or renamed lingers in
# the build workspace and keeps compiling. That produced a real failure: deleted screens still
# referenced string resources that had been removed with them, so only the workspace copy failed.
# Purge stale files from copied source subtrees only; build caches (app/build, app/.cxx,
# tools/toolchain-probe) are never touched.
function Remove-StaleTree([string]$source, [string]$destination, [string[]]$excludedDirectories = @()) {
    if (-not (Test-Path -LiteralPath $destination -PathType Container)) { return }
    $srcRoot = (Resolve-Path -LiteralPath $source).Path.TrimEnd('\')
    $dstRoot = (Resolve-Path -LiteralPath $destination).Path.TrimEnd('\')
    $removed = 0
    foreach ($file in (Get-ChildItem -LiteralPath $dstRoot -Recurse -File -Force)) {
        $rel = $file.FullName.Substring($dstRoot.Length).TrimStart('\')
        $skip = $false
        foreach ($excluded in $excludedDirectories) {
            if ($rel -like "$excluded\*") { $skip = $true; break }
        }
        if ($skip) { continue }
        if (-not (Test-Path -LiteralPath (Join-Path $srcRoot $rel) -PathType Leaf)) {
            Remove-Item -LiteralPath $file.FullName -Force
            $removed++
        }
    }
    foreach ($dir in (Get-ChildItem -LiteralPath $dstRoot -Recurse -Directory -Force |
            Sort-Object { $_.FullName.Length } -Descending)) {
        if ($excludedDirectories -contains $dir.Name) { continue }
        if (-not (Get-ChildItem -LiteralPath $dir.FullName -Force)) { Remove-Item -LiteralPath $dir.FullName -Force }
    }
    if ($removed -gt 0) { Write-Host "  purged $removed stale file(s) from $destination" }
}

foreach ($name in @('settings.gradle.kts', 'build.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat', 'local.properties')) {
    Copy-Item -LiteralPath (Join-Path $projectRoot $name) -Destination (Join-Path $buildRootFull $name) -Force
}
Copy-Tree (Join-Path $projectRoot 'gradle') (Join-Path $buildRootFull 'gradle')
Copy-Tree (Join-Path $projectRoot 'tools') (Join-Path $buildRootFull 'tools') @('toolchain-probe')
Copy-Tree (Join-Path $projectRoot 'app') (Join-Path $buildRootFull 'app') @('build', '.cxx', '.gradle', '.kotlin')
Remove-StaleTree (Join-Path $projectRoot 'app\src') (Join-Path $buildRootFull 'app\src')
if (Test-Path -LiteralPath (Join-Path $projectRoot 'app\libs') -PathType Container) {
    Remove-StaleTree (Join-Path $projectRoot 'app\libs') (Join-Path $buildRootFull 'app\libs')
}
Remove-StaleTree (Join-Path $projectRoot 'gradle') (Join-Path $buildRootFull 'gradle')
Remove-StaleTree (Join-Path $projectRoot 'tools') (Join-Path $buildRootFull 'tools') @('toolchain-probe')


if (-not (Test-Path -LiteralPath (Join-Path $MsysRoot 'usr\bin\bash.exe') -PathType Leaf)) {
    throw "MSYS2 bash not found: $MsysRoot"
}

$env:Path = (Join-Path $MsysRoot 'usr\bin') + ';' + $env:Path
# Codex and similar sandboxed launchers can virtualize LOCALAPPDATA.  CMake
# writes its own executable path into ExternalProject rules, so configure it
# with the real SDK location before Gradle creates the native build graph.
$realLocalAppData = Join-Path $env:USERPROFILE 'AppData\Local'
$androidSdk = Join-Path $realLocalAppData 'Android\Sdk'
if (-not (Test-Path -LiteralPath $androidSdk -PathType Container)) {
    throw "Android SDK not found: $androidSdk"
}
$env:LOCALAPPDATA = $realLocalAppData
$env:ANDROID_HOME = $androidSdk
$env:ANDROID_SDK_ROOT = $androidSdk
if ([string]::IsNullOrWhiteSpace($PythonExe)) {
    $pythonCandidates = Get-ChildItem -LiteralPath (Join-Path $realLocalAppData 'Programs\Python') -Filter python.exe -Recurse -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending
    $PythonExe = $pythonCandidates | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $PythonExe -or -not (Test-Path -LiteralPath $PythonExe -PathType Leaf)) {
    throw "Python not found: $PythonExe"
}
$PythonExe = [System.IO.Path]::GetFullPath($PythonExe)
$cmakeHome = Join-Path $androidSdk 'cmake\3.22.1'
if (-not (Test-Path -LiteralPath (Join-Path $cmakeHome 'bin\cmake.exe') -PathType Leaf)) {
    throw "Android SDK CMake not found: $cmakeHome"
}
# AGP otherwise resolves CMAKE_COMMAND from a sandbox-virtualized app-data
# directory while generating ExternalProject commands.
$escapedSdk = $androidSdk -replace '\\', '\\'
$escapedCmake = $cmakeHome -replace '\\', '\\'
# Release signing properties are read from local.properties by app/build.gradle.kts.
# The build workspace copy must not silently drop them.
$signingKeys = @('storeFile', 'storePassword', 'keyAlias', 'keyPassword')
$signingLines = @()
$sourceLocalProperties = Join-Path $projectRoot 'local.properties'
if (Test-Path -LiteralPath $sourceLocalProperties -PathType Leaf) {
    foreach ($line in (Get-Content -LiteralPath $sourceLocalProperties)) {
        foreach ($key in $signingKeys) {
            if ($line -match "^\s*$key\s*=") { $signingLines += $line.Trim() }
        }
    }
}
Set-Content -LiteralPath (Join-Path $buildRootFull 'local.properties') -Encoding ascii -Value (@(
    "sdk.dir=$escapedSdk",
    "cmake.dir=$escapedCmake"
) + $signingLines)
Push-Location $buildRootFull
try {
    $buildTasks = @(':app:assemble' + $Variant)
    if ($WithIntegrationTests) { $buildTasks += ':app:assembleDebugAndroidTest' }
    & .\gradlew.bat @buildTasks '--no-daemon' '--console=plain' "-Pairrelay.abis=$Abis" "-Pairrelay.python=$PythonExe" ("-Pairrelay.msys.root=" + ($MsysRoot -replace '\\', '/')) ("-Pairrelay.cmake.command=" + ((Join-Path $cmakeHome 'bin\cmake.exe') -replace '\\', '/'))
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed: $LASTEXITCODE" }
} finally {
    Pop-Location
}

$apkFolder = Join-Path $buildRootFull ('app\build\outputs\apk\' + $Variant.ToLowerInvariant())
Write-Host "APK folder: $apkFolder"

