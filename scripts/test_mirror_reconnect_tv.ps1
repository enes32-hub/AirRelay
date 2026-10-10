param(
    [string]$Serial=$env:AIRRELAY_TV,
    [string]$BuildRoot=(Join-Path $env:LOCALAPPDATA 'AirRelay-build'),
    [string]$NativeDir='',
    [string]$NdkVersion='29.0.14206865',
    [string]$Adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [switch]$Baseline
)
$ErrorActionPreference='Stop'
if (-not $NativeDir) {
    $nativeRoot=Join-Path $BuildRoot 'app\.cxx\Debug'
    $NativeDir=Get-ChildItem -Path $nativeRoot -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'armeabi-v7a' } |
        Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (-not $NativeDir) { throw "Native build output not found under $nativeRoot; pass -NativeDir." }
}
$root=Join-Path $BuildRoot 'vpn-tests'
$lib=Join-Path $BuildRoot 'app\src\main\cpp\third_party\UxPlay\lib'
$src=@('logger.c','netutils.c','raop_ntp.c','mirror_buffer.c','crypto.c','utils.c','byteutils.c','compat.c') | ForEach-Object { Join-Path $lib $_ }
$clang=Join-Path $env:LOCALAPPDATA "Android\Sdk\ndk\$NdkVersion\toolchains\llvm\prebuilt\windows-x86_64\bin\armv7a-linux-androideabi24-clang.cmd"
New-Item -ItemType Directory -Force -Path $root | Out-Null
Copy-Item tests/integration/native_mirror_reconnect_test.c "$root\reconnect.c"
$mirror=if ($Baseline) { "$root\mirror-before.c" } else { "$lib\raop_rtp_mirror.c" }
$label=if ($Baseline) { 'before' } else { 'after' }
& $clang -std=c17 -D_GNU_SOURCE -DTARGET_RT_LITTLE_ENDIAN=1 -I $lib -I (Join-Path $BuildRoot 'app\src\main\cpp\third_party\libplist\include') -I "$NativeDir\third_party\openssl-cmake\openssl-build\usr\local\include" "$root\reconnect.c" $mirror @src "$NativeDir\libplist.a" -L $root -lcrypto -llog -lm '-Wl,-rpath,$ORIGIN' -o "$root\reconnect-core"
if ($LASTEXITCODE -ne 0) { throw 'Compile failed' }
& $Adb -s $Serial push "$root\reconnect-core" /data/local/tmp/airrelay-vpn-test/reconnect-core
if ($LASTEXITCODE -ne 0) { throw 'Push failed' }
& $Adb -s $Serial shell chmod 755 /data/local/tmp/airrelay-vpn-test/reconnect-core
& $Adb -s $Serial shell 'LD_LIBRARY_PATH=/data/local/tmp/airrelay-vpn-test /data/local/tmp/airrelay-vpn-test/reconnect-core' *>&1 | Tee-Object "artifacts/rc18-vpn/native-$label-verified.log"
exit $LASTEXITCODE
