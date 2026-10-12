param([string]$ToolRoot = 'C:\Users\Guillermo\.codex\build-tools\evvdroid')
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot
$native = Join-Path $project 'native/openevv'
$compiler = Get-ChildItem $ToolRoot -Directory -Filter 'llvm-mingw-*-ucrt-x86_64' | Select-Object -Last 1
if (!$compiler) { throw 'Se necesita LLVM MinGW para Windows.' }
$compilerBin = Join-Path $compiler.FullName 'bin'
$pythonRoot = 'C:\Users\Guillermo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python'
$gitRoot = 'C:\Users\Guillermo\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\git'
$env:PATH = "$compilerBin;$ToolRoot;$ToolRoot\usr\bin;$pythonRoot;$gitRoot\usr\bin;$gitRoot\mingw64\bin;$env:PATH"
$env:MSYSTEM = 'MSYS'
Push-Location $native
try {
    & "$ToolRoot\usr\bin\make.exe" -f Makefile -f ../android.mk -j 6 desktop-objects 'BUILD=build/acoustic-host' 'LANGS=lang/eses lang/esus' 'CC=x86_64-w64-mingw32-clang' 'CCWIN=x86_64-w64-mingw32-clang' 'CFLAGS=-fsigned-char' 2>&1 | Out-File (Join-Path $project 'build-acoustic-host.log') -Encoding utf8
    if ($LASTEXITCODE) { throw 'Falló la compilación; consultar build-acoustic-host.log.' }
    $objects = Get-ChildItem 'build/acoustic-host/objwin-c/eses-esus' -Filter '*.o'
    $includePaths = @('include','src') + @(Get-ChildItem src -Directory | ForEach-Object { $_.FullName; Get-ChildItem $_.FullName -Directory | ForEach-Object FullName }) + @('lang/eses','lang/esus','rom/eses','rom/esus')
    $common = @('-O2','-std=gnu99','-fsigned-char','-DEVV_ARENA=1','-DEVV_NO_BYTECODE','-static','-Wl,--gc-sections') + @($includePaths | ForEach-Object { '-I"' + $_.Replace('\','/') + '"' })
    foreach ($front in @(@('cli/evv.c','evv.exe'), @('test/harness/rates.c','rates.exe'))) {
        $rsp = @($common) + @($front[0]) + @($objects | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' }) + @('-o',('build/acoustic-host/' + $front[1]))
        $rsp | Set-Content 'build/acoustic-host/link.rsp' -Encoding utf8
        & "$compilerBin\x86_64-w64-mingw32-clang.exe" '@build/acoustic-host/link.rsp'
        if ($LASTEXITCODE) { throw 'Falló el enlace.' }
    }
} finally { Pop-Location }
