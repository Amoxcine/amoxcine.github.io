[CmdletBinding()]
param([string]$JavaHome = 'C:/Users/avets/AppData/Roaming/PrismLauncher/java/java-runtime-delta')
$ErrorActionPreference = 'Stop'
$base = Join-Path $PSScriptRoot '../renaissance-controller/build/dependencies'
$jars = @(Get-ChildItem -LiteralPath $base -Filter '*.jar' | Where-Object { $_.Name -notmatch 'ascendant' } | Sort-Object Name)
if ($jars.Count -lt 100) { throw 'Pinned local dependency cache missing; no network fallback.' }
$pins = @{
 'resourcefullib-neoforge-1.21-3.0.12.jar' = '5E36F2C69DE008DC5795F730C84AB767688F15C810944B585485349A0C911261'
 'Apotheosis-1.21.1-8.7.0.jar' = '7309165A2096F441A5B042836616B98EC260E2D8631E85774279DBAD3B1982B7'
 'FarmersDelight-1.21.1-1.3.3.jar' = '5DFE78EC57CFC793952611C77CB92D0D66765DA5BD1AC326BA85FAA4C236B78D'
 'curios-neoforge-9.5.1+1.21.1.jar' = 'A45DF2125C26219974ABA7507FFC9AFE7B83ACC941A386AF3FAACB1CC0056FDE'
 'accessories-neoforge-1.1.0-beta.53+1.21.1.jar' = '10017A3DA78EA63E9ECE27A1CA32F8CF490362F348778CF8CB759E7282F3BEB0'
 'common-storage-lib-neoforge-1.21.1-0.0.10.jar' = '921BD8D255A65B5E21A5C74AA661D1EA4ED034FEBCFBB5939DA054CE08F9D109'
 'adastra-1.21.1-1.16.24-neoforge.jar' = 'C631DEEE98B7A0149C5CD07648EFFE78B6488ADD255B6CC7B23FA12EB91B1E81'
 'waystones-neoforge-1.21.1-21.1.41.jar' = '3D38C91A114A3C6D29767C0C7216633263816CAEF8D3B91600C8E061B3BAA590'
 'Draconic-Evolution-1.21.1-3.1.4.632.jar' = '623D7D58E58428A206015B56BF67387C79FF6D97F7221CFF23B1DAD0BED9544E'
 'BrandonsCore-1.21.1-3.2.1.309.jar' = '076B44DE51C606E6CDD89694AC0A879FC028D4F0103880EF44F47DBCC69F1D23'
}
$extra = @()
foreach ($name in ($pins.Keys | Sort-Object)) {
 $path = Join-Path $PSScriptRoot "../rc-lab/mods/$name"
 if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $pins[$name]) { throw "Pinned binary changed: $name" }
 $extra += (Get-Item -LiteralPath $path).FullName
}
$extra += @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '../candidate-dependencies') -Filter '*.jar' |
    Where-Object { $_.Name -notmatch 'ascendant|adastra' } | Select-Object -ExpandProperty FullName)
$nested = Join-Path $PSScriptRoot 'build/cargo-dependencies'
New-Item -ItemType Directory -Path $nested -Force | Out-Null
$archive = [IO.Compression.ZipFile]::OpenRead((Join-Path $PSScriptRoot '../rc-lab/mods/common-storage-lib-neoforge-1.21.1-0.0.10.jar'))
try {
 foreach ($part in @('data', 'lookup', 'resources')) {
  $name = "common-storage-lib-$part-neoforge-1.21.1-0.0.10.jar"
  [IO.Compression.ZipFileExtensions]::ExtractToFile($archive.GetEntry("META-INF/jars/$name"), (Join-Path $nested $name), $true)
 }
} finally { $archive.Dispose() }
$extra += @(Get-ChildItem -LiteralPath $nested -Filter '*.jar' | Select-Object -ExpandProperty FullName)
$cp = (($extra + @($jars.FullName) | Select-Object -Unique) -join ';').Replace('\', '/')
$build = Join-Path $PSScriptRoot ('build/' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff'))
$classes = Join-Path $build 'classes'
$tests = Join-Path $build 'tests'
New-Item -ItemType Directory -Path $classes, $tests -Force | Out-Null
function Compile([string]$sourceRoot, [string]$output, [string]$classpath, [string]$argName) {
 $sources = @(Get-ChildItem -LiteralPath $sourceRoot -Filter '*.java' -Recurse | Sort-Object FullName)
 $lines = @('--release', '21', '-encoding', 'UTF-8', '-proc:none', '-classpath', ('"' + $classpath + '"'),
    '-d', ('"' + $output.Replace('\', '/') + '"'))
 $lines += $sources.FullName | ForEach-Object { '"' + $_.Replace('\', '/') + '"' }
 $argsFile = Join-Path $build $argName
 [IO.File]::WriteAllLines($argsFile, $lines, [Text.UTF8Encoding]::new($false))
 & (Join-Path $JavaHome 'bin/javac.exe') ('@' + $argsFile)
 if ($LASTEXITCODE -ne 0) { throw "Compilation failed: $sourceRoot" }
}
Compile (Join-Path $PSScriptRoot 'src/main/java') $classes $cp 'main.args'
Compile (Join-Path $PSScriptRoot 'src/test/java') $tests ($classes.Replace('\', '/') + ';' + $cp) 'test.args'
$testcp = "$tests;$classes;$cp"
foreach ($test in @('PolicyTest', 'HookShapeTest', 'FuelBucketContractTest', 'GemDefaultsContractTest', 'ArrivalLedgerTest', 'FlightJournalTest', 'StarterKitTest')) {
 $runArgs = @('-classpath', ('"' + $testcp.Replace('\', '/') + '"'), "fr.ascendant.lunar.travel.$test")
 if ($test -eq 'HookShapeTest') { $runArgs += '"' + $classes.Replace('\', '/') + '"' }
 if ($test -eq 'ArrivalLedgerTest') { $runArgs += '"' + (Join-Path $build 'ledger-test').Replace('\', '/') + '"' }
 if ($test -eq 'FlightJournalTest') { $runArgs += '"' + (Join-Path $build 'flight-test').Replace('\', '/') + '"' }
 if ($test -eq 'StarterKitTest') { $runArgs += '"' + (Join-Path $PSScriptRoot '../lunar-survival-candidate/STARTER_KIT.json').Replace('\', '/') + '"' }
 $runFile = Join-Path $build "$test.args"
 [IO.File]::WriteAllLines($runFile, $runArgs, [Text.UTF8Encoding]::new($false))
 & (Join-Path $JavaHome 'bin/java.exe') ('@' + $runFile)
 if ($LASTEXITCODE -ne 0) { throw "Tests failed: $test" }
}
Copy-Item -Path (Join-Path $PSScriptRoot 'src/main/resources/*') -Destination $classes -Recurse
$output = Join-Path $build 'ascendant-lunar-travel-0.0.5-candidate.jar'
& (Join-Path $JavaHome 'bin/jar.exe') --create --file $output -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Packaging failed.' }
$hash = Get-FileHash -LiteralPath $output -Algorithm SHA256
$hash | Format-List
@("JAR=$output", "SHA256=$($hash.Hash)", 'No installation/server. Policy + bytecode checks only; runtime NOT QUALIFIED.') |
    Set-Content -LiteralPath (Join-Path $build 'result.txt') -Encoding utf8
