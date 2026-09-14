[CmdletBinding()]
param([string]$JavaHome = 'C:/Users/avets/AppData/Roaming/PrismLauncher/java/java-runtime-delta')
$ErrorActionPreference = 'Stop'
$mods = 'C:/Users/avets/AppData/Roaming/PrismLauncher/instances/Ascendant-Renaissance-PRE-RC/minecraft/mods'
$ad = Join-Path $mods 'adastra-1.21.1-1.16.24-neoforge.jar'
if ((Get-FileHash -LiteralPath $ad).Hash -ne 'C631DEEE98B7A0149C5CD07648EFFE78B6488ADD255B6CC7B23FA12EB91B1E81') { throw 'Ad Astra pin mismatch' }
$storage = Join-Path $mods 'common-storage-lib-neoforge-1.21.1-0.0.10.jar'
if ((Get-FileHash -LiteralPath $storage).Hash -ne '921BD8D255A65B5E21A5C74AA661D1EA4ED034FEBCFBB5939DA054CE08F9D109') { throw 'Common Storage pin mismatch' }
$jars = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '../../renaissance-controller/build/dependencies') -Filter '*.jar' | Where-Object { $_.Name -notmatch 'ascendant' } | Sort-Object Name)
if ($jars.Count -lt 100) { throw 'Local dependency cache missing; no network fallback' }
$extra = @($ad,$storage)
$extra += @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '../../candidate-dependencies') -Filter '*.jar' | Where-Object { $_.Name -notmatch 'ascendant|adastra' } | Select-Object -ExpandProperty FullName)
$extra += @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '../../lunar-travel-candidate/build/cargo-dependencies') -Filter '*.jar' | Select-Object -ExpandProperty FullName)
$cp = (($extra + @($jars.FullName) | Select-Object -Unique) -join ';').Replace('\','/')
$build = Join-Path $PSScriptRoot ('build/' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff'))
$classes = Join-Path $build 'classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Filter '*.java' -Recurse | Sort-Object FullName)
$argsFile = Join-Path $build 'compile.args'
$lines = @('--release','21','-encoding','UTF-8','-proc:none','-classpath',('"'+$cp+'"'),'-d',('"'+$classes.Replace('\','/')+'"'))
$lines += $sources.FullName | ForEach-Object { '"'+$_.Replace('\','/')+'"' }
[IO.File]::WriteAllLines($argsFile,$lines,[Text.UTF8Encoding]::new($false))
& (Join-Path $JavaHome 'bin/javac.exe') ('@'+$argsFile)
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
$testFile = Join-Path $build 'test.args'
$testArgs = @('-classpath',('"'+$classes.Replace('\','/')+';'+$cp+'"'),'fr.ascendant.etrionic.GuardTest',('"'+$classes.Replace('\','/')+'"'),('"'+$ad.Replace('\','/')+'"'))
[IO.File]::WriteAllLines($testFile,$testArgs,[Text.UTF8Encoding]::new($false))
$result = @(& (Join-Path $JavaHome 'bin/java.exe') ('@'+$testFile))
if ($LASTEXITCODE -ne 0) { throw 'Policy/bytecode tests failed' }
$result
$mixinJson = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'src/main/resources/ascendant-etrionic-guard.mixins.json') -Raw | ConvertFrom-Json
if (!$mixinJson.required -or $mixinJson.mixins.Count -ne 1 -or $mixinJson.injectors.defaultRequire -ne 1) { throw 'Required mixin fail-fast guard missing' }
Copy-Item -Path (Join-Path $PSScriptRoot 'src/main/resources/*') -Destination $classes -Recurse
$output = Join-Path $build 'ascendant-etrionic-guard-0.0.1-candidate.jar'
& (Join-Path $JavaHome 'bin/jar.exe') --create --file $output -C $classes fr/ascendant/etrionic/EtrionicGuard.class -C $classes fr/ascendant/etrionic/GuardPolicy.class -C $classes fr/ascendant/etrionic/mixin -C $classes META-INF -C $classes ascendant-etrionic-guard.mixins.json
if ($LASTEXITCODE -ne 0) { throw 'Packaging failed' }
$hash = (Get-FileHash -LiteralPath $output).Hash
$result += @("JAR=$output","SHA256=$hash",'PASS_STATIC_ONLY: native Mixin application/BLASTING veto/ALLOYING nonregression pending parent. Default OFF. No install or boot.')
$result | Set-Content -LiteralPath (Join-Path $build 'result.txt') -Encoding utf8
$result | Select-Object -Last 3
