[CmdletBinding()]
param([string]$JavaHome = 'C:/Users/avets/AppData/Roaming/PrismLauncher/java/java-runtime-delta')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ((Split-Path -Leaf $PSScriptRoot) -ne 'lunar-recall-candidate') { throw 'Wrong scope' }
$pre = 'C:/Users/avets/AppData/Roaming/PrismLauncher/instances/Ascendant-Renaissance-PRE-RC/minecraft/mods'
$deps = Join-Path $PSScriptRoot 'dependencies'
New-Item -ItemType Directory -Path $deps -Force | Out-Null
$pins = [ordered]@{
 'Mekanism-1.21.1-10.7.19.85.jar' = '004DBC9F3106F4D192AEAA1EE1190DD16EC9CA8059ED3D093B80034F4C574F43'
 'youre-in-grave-danger-neoforge-2.0.13.jar' = 'DD2142A3C6A9D5B990AB36220BE482F7AA9F528755F93B8FEF8996F509DDCDA2'
}
$targetJars = foreach ($name in $pins.Keys) {
    $source = Join-Path $pre $name
    if ((Get-FileHash -LiteralPath $source).Hash -ne $pins[$name]) { throw "Current PRE target changed: $name" }
    $target = Join-Path $deps $name
    if (-not (Test-Path -LiteralPath $target)) { Copy-Item -LiteralPath $source -Destination $target }
    if ((Get-FileHash -LiteralPath $target).Hash -ne $pins[$name]) { throw "Candidate dependency changed: $name" }
    $target
}
$base = Join-Path $PSScriptRoot '../renaissance-controller/build/dependencies'
$libs = @(Get-ChildItem -LiteralPath $base -Filter '*.jar' | Where-Object Name -NotMatch 'ascendant|Mekanism-1|youre-in-grave' | Sort-Object Name)
foreach ($required in @('0000-neoforge-21.1.248-server.jar','0001-neoforge-21.1.248-universal.jar')) {
    if ($required -notin $libs.Name) { throw "Missing pinned local compiler dependency $required" }
}
$cp = (($targetJars + $libs.FullName) -join ';').Replace('\','/')
$build = Join-Path $PSScriptRoot ('build/' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff'))
$classes = Join-Path $build 'classes'
$tests = Join-Path $build 'tests'
$temp = Join-Path $build 'tmp'
New-Item -ItemType Directory -Path $classes, $tests, $temp -Force | Out-Null
$main = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src/main/java') -Recurse -Filter '*.java')
$test = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src/test/java') -Recurse -Filter '*.java')
function Quote([string]$value) { '"' + $value.Replace('\','/') + '"' }
Push-Location -LiteralPath $build
try {
    foreach ($phase in @('main','test')) {
        $sources = if ($phase -eq 'main') { $main } else { $test }
        $out = if ($phase -eq 'main') { $classes } else { $tests }
        $classpath = if ($phase -eq 'main') { $cp } else { "$classes;$cp" }
        $argsFile = Join-Path $build "$phase.args"
        $lines = @('--release','21','-encoding','UTF-8','-proc:none','-classpath',(Quote $classpath),'-d',(Quote $out))
        $lines += $sources.FullName | ForEach-Object { Quote $_ }
        [IO.File]::WriteAllLines($argsFile, $lines)
        & (Join-Path $JavaHome 'bin/javac.exe') ('@' + $argsFile) 2>&1 | Tee-Object -FilePath (Join-Path $build "$phase-compile.log")
        if ($LASTEXITCODE -ne 0) { throw "$phase compilation failed" }
    }
    Copy-Item -Path (Join-Path $PSScriptRoot 'src/main/resources/*') -Destination $classes -Recurse
    foreach ($suite in @('PolicyTest','EventBusTest','NativeContractTest')) {
        $runFile = Join-Path $build "$suite.args"
        $run = @((Quote "-Djava.io.tmpdir=$temp"),'-classpath',(Quote "$tests;$classes;$cp"),"fr.ascendant.lunar.recall.$suite")
        if ($suite -eq 'NativeContractTest') { $run += @((Quote $classes), (Quote $deps)) }
        [IO.File]::WriteAllLines($runFile, $run)
        & (Join-Path $JavaHome 'bin/java.exe') ('@' + $runFile) 2>&1 | Tee-Object -FilePath (Join-Path $build "$suite.log")
        if ($LASTEXITCODE -ne 0) { throw "$suite failed" }
    }
    $output = Join-Path $build 'ascendant-lunar-recall-0.1.0-candidate.jar'
    & (Join-Path $JavaHome 'bin/jar.exe') --create --file $output -C $classes .
    if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed' }
    @($main + $test + (Get-Item -LiteralPath $PSCommandPath) +
        (Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src/main/resources') -Recurse -File) +
        (Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'config') -File)) | ForEach-Object {
        [pscustomobject]@{File=$_.FullName;SHA256=(Get-FileHash -LiteralPath $_.FullName).Hash}
    } | Export-Csv -LiteralPath (Join-Path $build 'sources-sha256.csv') -NoTypeInformation
    foreach ($name in $pins.Keys) {
        if ((Get-FileHash -LiteralPath (Join-Path $pre $name)).Hash -ne $pins[$name]) { throw "Read-only PRE target changed: $name" }
    }
    @($targetJars + $libs.FullName) | ForEach-Object {
        [pscustomobject]@{File=$_;SHA256=(Get-FileHash -LiteralPath $_).Hash}
    } | Export-Csv -LiteralPath (Join-Path $build 'dependencies-sha256.csv') -NoTypeInformation
    $hash = (Get-FileHash -LiteralPath $output).Hash
    [IO.File]::WriteAllLines((Join-Path $build 'result.txt'), @("JAR=$output", "SHA256=$hash", 'Offline tests only; no install/boot/world access. Native mixins NOT APPLIED.'))
    Write-Output "PASS JAR=$output SHA256=$hash; no install/server; writes confined to lunar-recall-candidate"
} finally { Pop-Location }
