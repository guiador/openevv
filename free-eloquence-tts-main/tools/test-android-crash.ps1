param(
    [string]$ToolRoot="${env:USERPROFILE}/.codex/build-tools/evvdroid",
    [switch]$BuildOnly,
    [switch]$SkipBuild,
    [string]$TestClass='com.blindtechnexus.freeeloquencetts.CrashMessageTest'
)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot
$env:JAVA_HOME="$ToolRoot/jdk21/jdk-21.0.12.1+1"
$env:ANDROID_HOME="$ToolRoot/sdk"
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:/codex-gradle-no-unix-sockets'
$adb="$ToolRoot/sdk/platform-tools/adb.exe"
Push-Location $project
try {
    if (!$SkipBuild) {
        & ./gradlew.bat :app:assemblePrincipalDebugAndroidTest -x buildNative '-Pevvdroid.ndkVersion=26.1.10909125' '-Dorg.gradle.jvmargs=-Xmx1152m -Xms128m -XX:ActiveProcessorCount=2 -XX:+UseSerialGC -Dfile.encoding=UTF-8' '-Pkotlin.incremental=false' '-Pkotlin.compiler.execution.strategy=in-process' --max-workers=1 --no-daemon --console=plain *> build-android-crash-test.log
        if ($LASTEXITCODE) {Get-Content build-android-crash-test.log -Tail 45; throw 'Falló APK de pruebas.'}
    }
    if (!$BuildOnly) {
        & $adb install -r app/build/outputs/apk/androidTest/principal/debug/app-principal-debug-androidTest.apk
        if ($LASTEXITCODE) {throw 'Falló instalación de las pruebas.'}
        & $adb shell am instrument -w -r -e class $TestClass org.openevv.android.principal.test/androidx.test.runner.AndroidJUnitRunner | Tee-Object -FilePath build-android-crash-device.log
        if ($LASTEXITCODE) {throw 'Falló la instrumentación.'}
        $result=Get-Content build-android-crash-device.log -Raw
        if ($result -notmatch 'OK \(\d+ tests?\)' -or $result -match 'FAILURES|Process crashed|INSTRUMENTATION_FAILED') {throw 'Las pruebas no terminaron aprobadas.'}
    }
} finally {Pop-Location}
