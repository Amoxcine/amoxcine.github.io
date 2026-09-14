[CmdletBinding()]
param(
    [string]$JavaHome = 'C:/Users/avets/AppData/Roaming/PrismLauncher/java/java-runtime-delta',
    [string]$Mods = 'C:/Users/avets/AppData/Roaming/PrismLauncher/instances/Ascendant-Renaissance-PRE-RC/minecraft/mods'
)
$ErrorActionPreference = 'Stop'
$version = '0.1.1'
$original = Join-Path $PSScriptRoot 'dist/lunar-extra-restrictions-0.1.0-candidate.jar'
$originalHash = '50FEC7DD7732CDABE5C9EA113B3F8D60E432A63CC36A2CB0F4FAC2D9F2A35B10'
if ((Get-FileHash $original).Hash -ne $originalHash) { throw 'Preserved 0.1.0 differs' }
$deps = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../aventure-20260905/renaissance-controller/build/dependencies'))
$pins = Get-Content -Raw (Join-Path $PSScriptRoot 'pins.json') | ConvertFrom-Json
$cached = @(Get-ChildItem -LiteralPath $deps -Filter '*.jar' | Sort-Object Name)
$before = @{}
foreach ($file in $cached) { $before[$file.FullName] = (Get-FileHash -LiteralPath $file.FullName).Hash }
foreach ($pin in $pins.mods.PSObject.Properties) {
    $match = @($cached | Where-Object { $_.Name.EndsWith('-' + $pin.Name, [StringComparison]::Ordinal) })
    if ($match.Count -ne 1 -or $before[$match[0].FullName] -ne $pin.Value -or
        (Get-FileHash -LiteralPath (Join-Path $Mods $pin.Name)).Hash -ne $pin.Value) {
        throw "Pinned current/cache binary mismatch: $($pin.Name)"
    }
}
foreach ($pin in $pins.runtime.PSObject.Properties) {
    if ($before[(Join-Path $deps $pin.Name)] -ne $pin.Value) { throw "Runtime pin mismatch: $($pin.Name)" }
}
& (Join-Path $PSScriptRoot 'inspect.ps1')
$build = Join-Path $PSScriptRoot ('build/' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff'))
$classes = Join-Path $build 'classes'
$tests = Join-Path $build 'tests'
$temp = Join-Path $build 'tmp'
[void](New-Item -ItemType Directory -Force $classes, $tests, $temp)
$addon = Join-Path $PSScriptRoot 'build/inspection/ae2addonlib-1.0.3-1.21.1.jar'
$localDeps = Join-Path $PSScriptRoot 'build/dependencies'
[void](New-Item -ItemType Directory -Force $localDeps)
# Windows JDK zipfs cannot close archives on this read-only cache mount.
# Only private, byte-identical compile copies are written; never repair the cache.
$needed = @($cached | Where-Object {
    [int]$_.Name.Substring(0,4) -lt 94 -or
    $pins.mods.PSObject.Properties.Name -contains $_.Name.Substring(5) -or
    $_.Name -match 'curios-|geckolib-|guideme-|Patchouli-'
})
$privateJars = @()
foreach ($file in $needed) {
    $target = Join-Path $localDeps $file.Name
    if (-not (Test-Path $target) -or (Get-FileHash $target).Hash -ne $before[$file.FullName]) {
        Copy-Item -LiteralPath $file.FullName -Destination $target
    }
    if ((Get-FileHash $target).Hash -ne $before[$file.FullName]) { throw "Private compile copy mismatch: $target" }
    $privateJars += $target
}
$cp = (@($privateJars) + @(Get-ChildItem (Join-Path $PSScriptRoot 'build/inspection') -Filter '*.jar' | Select-Object -ExpandProperty FullName)) -join ';'
$utf8 = [Text.UTF8Encoding]::new($false)
function Quote([string]$value) { '"' + $value.Replace('\', '/') + '"' }
function RunJava([string]$tool, [string]$name, [string[]]$arguments) {
    $argfile = Join-Path $build ($name + '.args')
    [IO.File]::WriteAllLines($argfile, $arguments, $utf8)
    & (Join-Path $JavaHome ('bin/' + $tool + '.exe')) ('@' + $argfile) 2>&1 |
        Tee-Object -FilePath (Join-Path $build ($name + '.log'))
    if ($LASTEXITCODE -ne 0) { throw "$name failed ($LASTEXITCODE)" }
}
Push-Location -LiteralPath $build
try {
    $sources = @(Get-ChildItem (Join-Path $PSScriptRoot 'src/main/java') -Recurse -Filter '*.java')
    RunJava 'javac' 'compile' (@('--release','21','-encoding','UTF-8','-proc:none','-classpath', (Quote $cp),'-d',(Quote $classes)) + @($sources | ForEach-Object { Quote $_.FullName }))
    Copy-Item (Join-Path $PSScriptRoot 'src/main/resources/*') -Destination $classes -Recurse
    $testSources = @(Get-ChildItem (Join-Path $PSScriptRoot 'src/test/java') -Recurse -Filter '*.java')
    RunJava 'javac' 'test-compile' (@('--release','21','-encoding','UTF-8','-proc:none','-classpath',(Quote ($classes + ';' + $cp)),'-d',(Quote $tests)) + @($testSources | ForEach-Object { Quote $_.FullName }))
    foreach ($suite in @('RulesTest','ContractTest','PortalContractTest')) {
        RunJava 'java' $suite @((Quote ('-Djava.io.tmpdir=' + $temp)), '-classpath', (Quote ($classes + ';' + $tests + ';' + $cp)), ('local.lunar.' + $suite), (Quote $classes))
    }
    $output = Join-Path $build ("lunar-extra-restrictions-$version-candidate.jar")
    & (Join-Path $JavaHome 'bin/jar.exe') --create --date '2026-09-13T00:00:00Z' --file $output -C $classes .
    if ($LASTEXITCODE -ne 0) { throw 'Packaging failed' }
    RunJava 'java' 'PackagingTest' @((Quote ('-Djava.io.tmpdir=' + $temp)), '-classpath', (Quote ($classes + ';' + $tests + ';' + $cp)), 'local.lunar.PackagingTest', (Quote $output), (Quote $classes))
    foreach ($file in $cached) {
        if ((Get-FileHash -LiteralPath $file.FullName).Hash -ne $before[$file.FullName]) { throw "Shared cache changed: $($file.Name)" }
    }
    foreach ($pin in $pins.mods.PSObject.Properties) {
        if ((Get-FileHash -LiteralPath (Join-Path $Mods $pin.Name)).Hash -ne $pin.Value) { throw "Current jar changed: $($pin.Name)" }
    }
    $inputs = @($sources + $testSources + (Get-ChildItem (Join-Path $PSScriptRoot 'src/main/resources') -Recurse -File) +
        (Get-Item $PSCommandPath) + (Get-Item (Join-Path $PSScriptRoot 'pins.json')) + (Get-Item (Join-Path $PSScriptRoot 'inspect.ps1')))
    $inputs | ForEach-Object { [pscustomobject]@{ File=$_.FullName; SHA256=(Get-FileHash $_.FullName).Hash } } |
        Export-Csv -NoTypeInformation (Join-Path $build 'sources-sha256.csv')
    $dist = Join-Path $PSScriptRoot 'dist'
    [void](New-Item -ItemType Directory -Force $dist)
    $published = Join-Path $dist ([IO.Path]::GetFileName($output))
    if (Test-Path $published) { throw 'Versioned dist artifact already exists; never overwrite published candidates' }
    if ((Get-FileHash $original).Hash -ne $originalHash) { throw 'Preserved 0.1.0 changed' }
    Copy-Item -LiteralPath $output -Destination $published
    if ((Get-FileHash $published).Hash -ne (Get-FileHash $output).Hash) { throw 'Artifact copy mismatch' }
    [pscustomobject]@{
        artifact=$published; buildArtifact=$output; sha256=(Get-FileHash $output).Hash; builtUtc=[DateTime]::UtcNow.ToString('o');
        tests=@('RulesTest','ContractTest','PortalContractTest','PackagingTest'); sharedDependencyCount=$cached.Count;
        preserved0010Sha256=$originalHash;
        sharedDependenciesUnchanged=$true; targetPrismJarsUnchanged=$true;
        minecraftBooted=$false; installed=$false; nativeJoinTested=$false
    } | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $build 'result.json')
    Copy-Item (Join-Path $build 'result.json') (Join-Path $dist ("result-$version.json"))
    Get-Content (Join-Path $build 'result.json')
} finally { Pop-Location }
