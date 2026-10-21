param([string]$ToolRoot='C:/Users/Guillermo/.codex/build-tools/evvdroid',[int]$Jobs=2)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot
$gitRoot='C:/Users/Guillermo/.cache/codex-runtimes/codex-primary-runtime/dependencies/native/git'
$pythonRoot='C:/Users/Guillermo/.cache/codex-runtimes/codex-primary-runtime/dependencies/python'
$env:JAVA_HOME="$ToolRoot/jdk21/jdk-21.0.12.1+1"
$env:ANDROID_HOME="$ToolRoot/sdk"
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:ANDROID_NDK_HOME="$ToolRoot/sdk/ndk/26.1.10909125"
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:/codex-gradle-no-unix-sockets'
$env:PATH="$ToolRoot;$ToolRoot/usr/bin;$pythonRoot;$gitRoot/usr/bin;$gitRoot/mingw64/bin;$env:PATH"
$env:MSYSTEM='MSYS'
$env:PYTHONHOME=$pythonRoot
$env:PYTHONUTF8='1'
$env:MAKEFLAGS="SHELL=$ToolRoot/bash.exe"
$env:JOBS="$Jobs"
Push-Location $project
try {
    & "$ToolRoot/bash.exe" native/build-native.sh *> build-android-native.log
    if ($LASTEXITCODE) {Get-Content build-android-native.log -Tail 35;throw 'Falló compilación Android nativa.'}
    & ./gradlew.bat :app:assemblePrincipalDebug -x buildNative '-Pevvdroid.ndkVersion=26.1.10909125' '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8' '-Pkotlin.incremental=false' '-Pkotlin.compiler.execution.strategy=in-process' --max-workers=2 --console=plain *> build-android-current.log
    if ($LASTEXITCODE) {Get-Content build-android-current.log -Tail 35;throw 'Falló APK Principal.'}
    & "$pythonRoot/python.exe" -X utf8 ../tools/verify-android-delivery.py
    if ($LASTEXITCODE) {throw 'Falló validación de bibliotecas Android.'}
    $signature=& "$ToolRoot/sdk/build-tools/36.0.0/apksigner.bat" verify --verbose --print-certs app/build/outputs/apk/principal/debug/app-principal-debug.apk
    if ($LASTEXITCODE) {throw 'Falló firma APK.'}
    $signature | Write-Output
    $certificate=[regex]::Match(($signature -join "`n"),'certificate SHA-256 digest: ([0-9a-f]+)').Groups[1].Value
    if ($certificate -ne '44f2735b03e57db0becdcf817e1d59f38b64376c20dde6eedba6fb15c4ec6621') {throw 'La firma cambió frente a la APK anterior.'}
    @{certificate_sha256=$certificate;verified=$true} | ConvertTo-Json | Set-Content '../build/upstream-review-20261012/android-signature.json' -Encoding utf8
} finally {Pop-Location}
