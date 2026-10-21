param([string]$ToolRoot='C:\Users\Guillermo\.codex\build-tools\evvdroid', [int]$Jobs=1, [switch]$SkipPrepare, [switch]$SkipPackage)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot
$native=Join-Path $project 'native/openevv'
$compiler=(Get-ChildItem $ToolRoot -Directory -Filter 'llvm-mingw-*-ucrt-x86_64' | Select-Object -Last 1).FullName
$gitRoot='C:\Users\Guillermo\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\git'
$pythonRoot='C:\Users\Guillermo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python'
$env:PATH="$compiler\bin;$ToolRoot;$ToolRoot\usr\bin;$pythonRoot;$gitRoot\usr\bin;$gitRoot\mingw64\bin;$compiler\busybox\bin;$env:PATH"
$env:MSYSTEM='MSYS'
$env:PYTHONHOME=$pythonRoot
$env:MAKEFLAGS="SHELL=$($ToolRoot.Replace('\','/'))/bash.exe"
if (!$SkipPrepare) {
 & "$pythonRoot\python.exe" -X utf8 (Join-Path $project 'nvda-es/prepare.py')
 if ($LASTEXITCODE) {throw 'Falló la preparación del controlador.'}
 & "$pythonRoot\python.exe" -X utf8 (Join-Path $project 'nvda-es/make-docs.py')
 if ($LASTEXITCODE) {throw 'Falló la generación de documentación.'}
}
New-Item -ItemType Directory -Force (Join-Path $project 'nvda-es/addon/synthDrivers/openevv_es_engine') | Out-Null
$tags='eses esus enus engb dede frfr frca itit'
$languages=($tags.Split(' ') | ForEach-Object {'lang/'+$_}) -join ' '
Push-Location $native
try {
 foreach ($patch in Get-ChildItem (Join-Path $project 'native/patches') -Filter '*.patch' | Sort-Object Name) {
  & git apply --reverse --check $patch.FullName 2>$null
  if ($LASTEXITCODE -eq 0) {continue}
  & git apply --check $patch.FullName
  if ($LASTEXITCODE) {throw "No se puede aplicar el parche $($patch.Name)."}
  & git apply --whitespace=nowarn $patch.FullName
  if ($LASTEXITCODE) {throw "Falló el parche $($patch.Name)."}
 }
 & "$ToolRoot\usr\bin\make.exe" -f Makefile -f ../android.mk -j $Jobs desktop-objects 'BUILD=build/nvda-es' "LANGS=$languages" 'CC=x86_64-w64-mingw32-clang' 'CCWIN=x86_64-w64-mingw32-clang' 'CFLAGS=-fsigned-char' 2>&1 | Out-File (Join-Path $project 'build-nvda-es.log') -Encoding utf8
 if ($LASTEXITCODE) {throw 'Falló la compilación nativa. Consultar build-nvda-es.log.'}
 $objects=Get-ChildItem ('build/nvda-es/objwin-c/'+$tags.Replace(' ','-')) -Filter '*.o'
 $includes=@('include','src')+@(Get-ChildItem src -Directory | ForEach-Object {$_.FullName; Get-ChildItem $_.FullName -Directory | ForEach-Object FullName})+@($languages.Split(' '))
 # Keep upstream's Windows identity/version resource in the localized DLL.
 $resource='build/nvda-es/eci.res'
 & "$compiler\bin\x86_64-w64-mingw32-windres.exe" -DEVV_MAJOR=0 -DEVV_MINOR=5 -DEVV_PATCH=0 -I lib lib/eci.rc -O coff -o $resource
 if ($LASTEXITCODE) {throw 'Falló el recurso de versión de la DLL.'}
 $rsp=@('-O2','-fno-strict-aliasing','-std=gnu99','-fsigned-char','-DEVV_ARENA=1','-DEVV_NO_BYTECODE','-static','-shared')+@($includes | ForEach-Object {'-I"'+$_.Replace('\','/')+'"'})+@('lib/eci_api.c',('"'+(Join-Path $project 'nvda-es/native/extra.c').Replace('\','/')+'"'))+@($objects | ForEach-Object {'"'+$_.FullName.Replace('\','/')+'"'})+@('-o',('"'+(Join-Path $project 'nvda-es/addon/synthDrivers/openevv_es_engine/eci.dll').Replace('\','/')+'"'))
 $rsp+=@($resource)
 $rsp | Set-Content 'build/nvda-es/link.rsp' -Encoding utf8
 & "$compiler\bin\x86_64-w64-mingw32-clang.exe" '@build/nvda-es/link.rsp'
 if ($LASTEXITCODE) {throw 'Falló el enlace DLL.'}
 if (!$SkipPackage) {
  & (Join-Path $project 'tools/verify-updated-native.ps1') -ToolRoot $ToolRoot
  $builtDll=Join-Path $project 'nvda-es/addon/synthDrivers/openevv_es_engine/eci.dll'
  foreach ($nativeTest in @('dictlangs.py','pauses.py')) {
   & "$pythonRoot\python.exe" -X utf8 (Join-Path $native "test/lib/$nativeTest") $builtDll
   if ($LASTEXITCODE) {throw "Falló la prueba nativa $nativeTest."}
  }
  foreach ($integrationTest in @('latest-features.py','panel-reset.py','export-mode.py')) {
   & "$pythonRoot\python.exe" -X utf8 (Join-Path $project "nvda-es/tests/$integrationTest")
   if ($LASTEXITCODE) {throw "Falló la prueba del controlador $integrationTest."}
  }
  & "$pythonRoot\python.exe" -X utf8 (Join-Path $project 'nvda-es/package.py')
  if ($LASTEXITCODE) {throw 'Falló el empaquetado.'}
 }
} finally {Pop-Location}
