param([string]$ToolRoot="${env:USERPROFILE}/.codex/build-tools/evvdroid",[switch]$MatrixOnly,[switch]$HostMatrix)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot
$compiler=(Get-ChildItem $ToolRoot -Directory -Filter 'llvm-mingw-*-ucrt-x86_64' | Select-Object -Last 1).FullName
$pythonRoot="${env:USERPROFILE}/.cache/codex-runtimes/codex-primary-runtime/dependencies/python"
$gitRoot="${env:USERPROFILE}/.cache/codex-runtimes/codex-primary-runtime/dependencies/native/git"
$env:PATH="$compiler/bin;$ToolRoot;$ToolRoot/usr/bin;$pythonRoot;$gitRoot/usr/bin;$gitRoot/mingw64/bin;$compiler/busybox/bin;$env:PATH"
$env:MSYSTEM='MSYS'
$native=Join-Path $project 'native/openevv'
$dll=Join-Path $project 'nvda-es/addon/synthDrivers/openevv_es_engine/eci.dll'
Push-Location $native
try {
    if (!$MatrixOnly) {
    $link=Get-Content 'build/nvda-es/link.rsp'
    $options=@($link | Where-Object {$_ -ne '-shared' -and $_ -ne 'lib/eci_api.c' -and $_ -notlike '*extra.c*' -and $_ -notlike '*eci.dll*' -and $_ -ne '-o' -and $_ -notlike '*.res'})
    # Our local API adds mode 2 (fixed native 16 kHz). Keep every audio
    # assertion unchanged, but test the first invalid mode at 3 instead of 2.
    $wide=Get-Content 'test/harness/wide.c' -Raw
    $boundary='ev_setParam(h, PARAM_WIDEBAND, 2) != -1'
    if (([regex]::Matches($wide,[regex]::Escape($boundary))).Count -ne 1) {throw 'Cambió el arnés upstream de banda ancha.'}
    $wide=$wide.Replace($boundary,'ev_setParam(h, PARAM_WIDEBAND, 3) != -1').Replace('a two was taken','a three was taken')
    [IO.File]::WriteAllText((Join-Path $native 'build/nvda-es/wide-local.c'),$wide)
    foreach ($entry in @(@('cli/probe.c','probe.exe'),@('build/nvda-es/wide-local.c','wide.exe'))) {
        $rsp=$options+@($entry[0],'-o',('build/nvda-es/'+$entry[1]))
        $rsp | Set-Content 'build/nvda-es/verify.rsp' -Encoding utf8
        & "$compiler/bin/x86_64-w64-mingw32-clang.exe" '@build/nvda-es/verify.rsp'
        if ($LASTEXITCODE) {throw "Falló enlace $($entry[1])"}
    }
    & "$compiler/bin/x86_64-w64-mingw32-clang.exe" -O2 -std=gnu99 test/lib/dll.c -static -lversion -o build/nvda-es/dlltest.exe
    if ($LASTEXITCODE) {throw 'Falló enlace dlltest.'}
    $env:EVV_ECI_LIB=$dll
    foreach ($ending in @('delete','live','unload')) {
        & ./build/nvda-es/dlltest.exe -e $ending -o "build/nvda-es/ending-$ending.wav"
        if ($LASTEXITCODE) {throw "Falló cierre DLL $ending"}
    }
    $endingHashes=@(Get-ChildItem 'build/nvda-es/ending-*.wav' | Get-FileHash -Algorithm SHA256 | Select-Object -ExpandProperty Hash -Unique)
    if ($endingHashes.Count -ne 1) {throw 'Las formas de cierre cambiaron el audio.'}
    & ./build/nvda-es/wide.exe
    if ($LASTEXITCODE) {throw 'Falló banda ancha.'}
    } else {
        if (!(Get-Content (Join-Path $project 'verify-native-current.log') -Raw).Contains('wide: passed')) {throw 'Falta evidencia de banda ancha.'}
        if ((Get-Item 'build/nvda-es/probe.exe').LastWriteTime -lt (Get-Item $dll).LastWriteTime) {throw 'El probe es anterior a la DLL.'}
    }
    $probePath=(Join-Path $native 'build/nvda-es/probe.exe').Replace('\','/')
    $env:EVV_MATRIX_NATIVE='/'+$probePath.Substring(0,1).ToLower()+$probePath.Substring(2)
    $env:EVV_JOBS='2'
    if ($HostMatrix) {
        & "$pythonRoot/python.exe" (Join-Path (Split-Path $project) 'tools/check-matrix-host.py') $probePath enus engb eses esus frfr frca dede itit --engine $native --jobs 1 --output build/nvda-es/host-matrix.json 2>&1 | Tee-Object -FilePath 'build/nvda-es/matrix-results.log'
    } else {
        & "$ToolRoot/bash.exe" test/matrix.sh check enus engb eses esus frfr frca dede itit 2>&1 | Tee-Object -FilePath 'build/nvda-es/matrix-results.log'
    }
    if ($LASTEXITCODE) {throw 'Falló matriz de regresión.'}
    $summary=Get-Content 'build/nvda-es/matrix-results.log' -Raw
    $match=[regex]::Match($summary,'matrix: (\d+) cases')
    if (!$match.Success) {throw 'No se encontró total en matriz.'}
    $report=@{passed=[int]$match.Groups[1].Value;moved=0;rules='c';languages=@('enus','engb','eses','esus','frfr','frca','dede','itit');runner='Upstream probe linked from the same C objects as the packaged DLL';dll_sha256=(Get-FileHash $dll -Algorithm SHA256).Hash.ToLower();native_commit=(Get-Content '../openevv-revision.txt' -Raw).Trim();additional_checks=@('wideband upstream harness: invalid-mode boundary 3 because local mode 2 is native16; all audio checks unchanged','Windows DLL delete, live process exit and unload with identical PCM','official DLL version resource')}
    if ($HostMatrix) {$report.runner+='; Python host driver uses the matrix.sh input/mode/audio/stdout hash contract'}
    $report | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $project 'nvda-es/tests/baseline-results.json') -Encoding utf8
} finally {Pop-Location}
