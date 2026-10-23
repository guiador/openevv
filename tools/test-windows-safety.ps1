param([string]$ToolRoot="${env:USERPROFILE}/.codex/build-tools/evvdroid",[int]$Jobs=1,[switch]$Only32)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$compiler=(Get-ChildItem $ToolRoot -Directory -Filter 'llvm-mingw-*' | Select-Object -Last 1).FullName
$pythonRoot="${env:USERPROFILE}/.cache/codex-runtimes/codex-primary-runtime/dependencies/python"
$gitRoot="${env:USERPROFILE}/.cache/codex-runtimes/codex-primary-runtime/dependencies/native/git"
$env:PATH="$compiler/bin;$ToolRoot;$ToolRoot/usr/bin;$pythonRoot;$gitRoot/usr/bin;$gitRoot/mingw64/bin;$env:PATH"
$env:PYTHONHOME=$pythonRoot
$env:MSYSTEM='MSYS'
$env:MAKEFLAGS="SHELL=$ToolRoot/bash.exe"
$tags='enus engb dede eses esus frfr frca itit plpl jajp'
$languages=($tags.Split(' ') | ForEach-Object {'lang/'+$_}) -join ' '
$build='build/safety-bytecode'
Push-Location $root
try {
    if ($Only32) {
        & "$ToolRoot/usr/bin/make.exe" -j $Jobs win32 "BUILD=$build" RULES=bytecode "LANGS=$languages" CCWIN32=i686-w64-mingw32-clang ARWIN32=i686-w64-mingw32-ar WINDRES32=i686-w64-mingw32-windres *> "$build-build32.log"
        if ($LASTEXITCODE) {Get-Content "$build-build32.log" -Tail 35; throw 'Falló DLL Windows x86.'}
        $env:EVV_ECI_LIB=Join-Path $root "$build/eci32.dll"
        foreach ($ending in @('delete','live','unload')) {
            & "./$build/dlltest32.exe" -e $ending -o "$build/ending32-$ending.wav" *> "$build-dll32-$ending.log"
            if ($LASTEXITCODE) {Get-Content "$build-dll32-$ending.log"; throw "Falló DLL x86 $ending"}
        }
        $hashes=@(Get-ChildItem "$build/ending32-*.wav" | Get-FileHash -Algorithm SHA256 | Select-Object -ExpandProperty Hash -Unique)
        if ($hashes.Count -ne 1) {throw 'Los cierres x86 cambiaron el audio.'}
        Write-Output 'PASS: Windows x86 DLL with delete/live/unload and identical PCM'
        return
    }
    & "$ToolRoot/usr/bin/make.exe" -j $Jobs win-probe win-dlltest "$build/eci.dll" "BUILD=$build" RULES=bytecode "LANGS=$languages" CCWIN=x86_64-w64-mingw32-clang ARWIN=x86_64-w64-mingw32-ar WINDRES=x86_64-w64-mingw32-windres *> "$build-build.log"
    if ($LASTEXITCODE) {Get-Content "$build-build.log" -Tail 35; throw 'Falló el motor Windows bytecode.'}
    $env:EVV_ECI_LIB=Join-Path $root "$build/eci.dll"
    foreach ($ending in @('delete','live','unload')) {
        & "./$build/dlltest.exe" -e $ending -o "$build/ending64-$ending.wav" *> "$build-dll64-$ending.log"
        if ($LASTEXITCODE) {Get-Content "$build-dll64-$ending.log"; throw "Falló DLL x64 $ending"}
    }
    $hashes=@(Get-ChildItem "$build/ending64-*.wav" | Get-FileHash -Algorithm SHA256 | Select-Object -ExpandProperty Hash -Unique)
    if ($hashes.Count -ne 1) {throw 'Los cierres x64 cambiaron el audio.'}
    $binary=(Join-Path $root "$build/probe-$($tags.Replace(' ','-')).exe").Replace('\','/')
    $env:EVV_MATRIX_NATIVE='/'+$binary.Substring(0,1).ToLower()+$binary.Substring(2)
    $env:EVV_JOBS="$Jobs"
    & "$pythonRoot/python.exe" tools/check-matrix-host.py $binary @($tags.Split(' ')) --engine $root --jobs 1 --output "$build/matrix.json" *> "$build-matrix.log"
    if ($LASTEXITCODE) {Get-Content "$build-matrix.log" -Tail 35; throw 'Falló matriz Windows bytecode.'}
    Get-Content "$build-matrix.log" -Tail 12
} finally {Pop-Location}
