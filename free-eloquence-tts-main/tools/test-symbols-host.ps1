param([string]$ToolRoot='C:/Users/Guillermo/.codex/build-tools/evvdroid')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot
$cache='C:/Users/Guillermo/.gradle/caches/modules-2/files-2.1'
$jars=@(
 (Get-ChildItem "$cache/org.jetbrains.kotlin/kotlin-compiler-embeddable/2.2.21" -Recurse -Filter '*.jar' | Select-Object -First 1).FullName,
 (Get-ChildItem "$cache/org.jetbrains.kotlin/kotlin-stdlib/2.2.21" -Recurse -Filter '*.jar' | Select-Object -First 1).FullName,
 (Get-ChildItem "$cache/org.jetbrains.kotlin/kotlin-script-runtime" -Recurse -Filter '*.jar' | Select-Object -Last 1).FullName,
 (Get-ChildItem "$cache/org.jetbrains.kotlin/kotlin-reflect" -Recurse -Filter '*.jar' | Select-Object -Last 1).FullName,
 (Get-ChildItem "$cache/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm" -Recurse -Filter '*.jar' | Select-Object -Last 1).FullName,
 (Get-ChildItem "$cache/org.jetbrains/annotations/13.0" -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
)
$java="$ToolRoot/jdk21/jdk-21.0.12.1+1/bin/java.exe"
$out=Join-Path $project 'build/symbols-host'
New-Item -ItemType Directory -Force $out | Out-Null
$suite=Join-Path $project 'app/src/androidTest/kotlin/com/blindtechnexus/freeeloquencetts/SymbolsTest.kt'
$test=Get-Content $suite -Raw
$test=$test -replace '(?m)^import androidx.*\r?\n','' -replace '(?m)^import org.junit.*\r?\n',''
$test=$test -replace '(?m)^@RunWith.*\r?\n','' -replace '@Test',''
$test+="`nfun assertEquals(expected: String, actual: String) { check(expected == actual) { `"Expected: `${expected}; got: `${actual}`" } }`n"
$methods=[regex]::Matches($test,'fun (\w+)\(\)') | ForEach-Object {$_.Groups[1].Value}
$calls=($methods | ForEach-Object {"suite.$_(); println(`"PASS: $_`")"}) -join "`n"
$test+="`nfun main() { val suite = SymbolsTest(); $calls }`n"
$testFile=Join-Path $out 'SymbolsHost.kt'
[IO.File]::WriteAllText($testFile,$test)
& $java -Xmx256m -cp ($jars -join ';') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath $jars[1] -d "$out/tests.jar" "$project/app/src/main/kotlin/com/blindtechnexus/freeeloquencetts/Symbols.kt" $testFile
if ($LASTEXITCODE) {throw 'Falló la compilación de símbolos.'}
& $java -Xmx128m -cp "$out/tests.jar;$($jars[1])" com.blindtechnexus.freeeloquencetts.SymbolsHostKt
if ($LASTEXITCODE) {throw 'Falló una regresión de símbolos.'}
@{passed=$methods.Count; checks=$methods; validation='Actual Kotlin Symbols and shared instrumentation assertions, executed on host JVM; no Android device'} | ConvertTo-Json -Depth 4 | Set-Content "$out/results.json" -Encoding utf8
