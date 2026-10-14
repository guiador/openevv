param(
    [string]$Out = 'build/espanol-experimental/bv-tuning-01',
    [ValidateSet('friction', 'voicing')][string]$Study = 'friction',
    [int[]]$Speeds = @(180, 400),
    [string]$Reference = '',
    [string]$ToolRoot = 'C:\Users\Guillermo\.codex\build-tools\evvdroid',
    [string]$Python = 'C:\Users\Guillermo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$destination = [IO.Path]::GetFullPath((Join-Path $repo $Out))
$sourceCopy = Join-Path $destination 'engine'
$compiler = Get-ChildItem -LiteralPath $ToolRoot -Directory -Filter 'llvm-mingw-*-ucrt-x86_64' | Select-Object -Last 1
if (!$compiler) { throw 'Se necesita LLVM MinGW.' }
$gitRuntime = 'C:\Users\Guillermo\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\git'
$env:PATH = "$($compiler.FullName)\bin;$ToolRoot;$ToolRoot\usr\bin;$(Split-Path $Python);$gitRuntime\usr\bin;$gitRuntime\mingw64\bin;$env:PATH"
$env:MSYSTEM = 'MSYS'
$env:PYTHONHOME = Split-Path $Python
if (Test-Path -LiteralPath $destination) { throw 'Usar una carpeta nueva para conservar las evidencias.' }
& $Python -X utf8 (Join-Path $PSScriptRoot 'tune.py') prepare --out $sourceCopy
if ($LASTEXITCODE) { throw 'No se pudo preparar la copia del motor.' }
Push-Location $sourceCopy
try {
    $profiles = if ($Study -eq 'voicing') { @('friction', 'friction-voice3', 'friction-voice6') } else { @('original', 'friction', 'friction-duration') }
    foreach ($profile in $profiles) {
        & $Python -X utf8 (Join-Path $PSScriptRoot 'tune.py') patch --out $sourceCopy --profile $profile
        if ($LASTEXITCODE) { throw 'No se pudo preparar el perfil.' }
        & "$ToolRoot\usr\bin\make.exe" -f Makefile -f lab.mk -j 6 lab-objects 'RULES=bytecode' 'LANGS=lang/esus' 'CC=x86_64-w64-mingw32-clang' 'CCWIN=x86_64-w64-mingw32-clang' 'CFLAGS=-fsigned-char' "SHELL=$($ToolRoot.Replace('\','/'))/bash.exe" 2>&1 | Out-File (Join-Path $destination "$profile-build.log") -Encoding utf8
        if ($LASTEXITCODE) { throw "Falló la compilación de $profile; revisar el log." }
        $objects = Get-ChildItem 'build/objwin-bytecode/esus' -Filter '*.o'
        $includes = @('include','src','lang/esus') + @(Get-ChildItem src -Directory | ForEach-Object { $_.FullName; Get-ChildItem $_.FullName -Directory | ForEach-Object FullName })
        $rsp = @('-O2','-fno-strict-aliasing','-std=gnu99','-fsigned-char','-DEVV_ARENA=1','-static','-shared') + @($includes | ForEach-Object { '-I"' + $_.Replace('\','/') + '"' }) + @('lib/eci_api.c') + @($objects | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' }) + @('-o', ('"' + (Join-Path $destination "$profile.dll").Replace('\','/') + '"'))
        $rsp | Set-Content 'build/lab-link.rsp' -Encoding utf8
        & "$($compiler.FullName)\bin\x86_64-w64-mingw32-clang.exe" '@build/lab-link.rsp'
        if ($LASTEXITCODE) { throw "Falló el enlace de $profile." }
        $generateArgs = @('-X', 'utf8', (Join-Path $PSScriptRoot 'run.py'), 'generate', '--out', (Join-Path $destination $profile), '--dll', (Join-Path $destination "$profile.dll"), '--speeds') + @($Speeds | ForEach-Object { [string]$_ })
        & $Python @generateArgs
        if ($LASTEXITCODE) { throw "Falló la generación de $profile." }
    }
    if ($Study -eq 'voicing') {
        if (!$Reference) { $Reference = Join-Path $repo 'build/espanol-experimental/bv-tuning-01/friction/report.json' }
        & $Python -X utf8 (Join-Path $PSScriptRoot 'tune.py') compare-voicing --out $destination --reference $Reference --listening-speed $Speeds[0]
    } else {
        & $Python -X utf8 (Join-Path $PSScriptRoot 'tune.py') compare --out $destination --reference (Join-Path $repo 'build/espanol-experimental/bv-02/report.json')
    }
    if ($LASTEXITCODE) { throw 'La comparación requiere revisión.' }
} finally { Pop-Location }
