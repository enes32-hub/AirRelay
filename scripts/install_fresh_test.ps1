param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$Apk,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)
$ErrorActionPreference = 'Stop'
$package = 'com.airrelay.receiver'
if (!(Test-Path -LiteralPath $Apk -PathType Leaf)) { throw "APK not found: $Apk" }
if (!(Test-Path -LiteralPath $Adb -PathType Leaf)) { throw "ADB not found: $Adb" }
function Invoke-Device([string[]]$DeviceArgs) {
    $result = & $Adb -s $Serial @DeviceArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    $result
}
if ((Invoke-Device @('get-state')) -notcontains 'device') { throw 'The specified device is not authorized.' }
Invoke-Device @('install', '-r', (Resolve-Path -LiteralPath $Apk).Path)
Invoke-Device @('shell', 'am', 'force-stop', $package)
$cleared = Invoke-Device @('shell', 'pm', 'clear', $package)
if ($cleared -notcontains 'Success') { throw "App data was not cleared: $cleared" }
Invoke-Device @('shell', 'cmd', 'appops', 'set', $package, 'SYSTEM_ALERT_WINDOW', 'default')
$api = [int]((Invoke-Device @('shell', 'getprop', 'ro.build.version.sdk')) -join '')
if ($api -ge 33) {
    Invoke-Device @('shell', 'pm', 'revoke', $package, 'android.permission.POST_NOTIFICATIONS')
    Invoke-Device @('shell', 'pm', 'clear-permission-flags', $package, 'android.permission.POST_NOTIFICATIONS', 'user-set', 'user-fixed')
}
Invoke-Device @('shell', 'am', 'start', '-f', '0x10008000', '-n', "$package/io.github.jqssun.airplay.MainActivity")
Write-Output 'Fresh test installation ready. AirRelay settings, pairings and permission choices were reset.'
