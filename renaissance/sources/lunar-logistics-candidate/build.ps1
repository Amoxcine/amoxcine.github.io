#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$JavaHome = 'C:/Users/avets/AppData/Roaming/PrismLauncher/java/java-runtime-delta',
    [string]$PreRcMods = 'C:/Users/avets/AppData/Roaming/PrismLauncher/instances/Ascendant-Renaissance-PRE-RC/minecraft/mods'
)
# Adapted from reference/controller-build.ps1. No runtime writes, dependency downloads or launches.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath($PSScriptRoot)
if ((Split-Path -Leaf $root) -ne 'lunar-logistics-candidate') { throw 'Run only from lunar-logistics-candidate' }
$cache = Join-Path $root '../renaissance-controller/build/dependencies'
$nativeMods = Join-Path $root '../lunar-native-lab/mods'
$build = Join-Path $root ('build/' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff'))
$classes = Join-Path $build 'classes'
$tests = Join-Path $build 'tests'
$temp = Join-Path $build 'tmp'
$deps = Join-Path $root 'dependencies'
$javac = Join-Path $JavaHome 'bin/javac.exe'
$java = Join-Path $JavaHome 'bin/java.exe'
$jar = Join-Path $JavaHome 'bin/jar.exe'
foreach ($tool in @($javac, $java, $jar)) { if (-not (Test-Path -LiteralPath $tool)) { throw "Missing local tool $tool" } }
$pins = [ordered]@{
    'appliedenergistics2-19.2.17.jar' = '460D779A0609B81409907D9956DE8F6F70A1B0912257E3E5C3C7E75AC9630E95'
    'Mekanism-1.21.1-10.7.19.85.jar' = '004DBC9F3106F4D192AEAA1EE1190DD16EC9CA8059ED3D093B80034F4C574F43'
    'Powah-6.2.10.jar' = '0E604A7356111C1DD44A00EA42FC1AA960D9FAEB978261349DF1138FCEE4D0B4'
    'Applied-Mekanistics-1.6.3.jar' = '8946FEA39451DBCE8E709DEDBEF40A52BA337BDF7A25AC0C4B503800B1BF0773'
}
$comparison = foreach ($name in $pins.Keys) {
    $cached = @(Get-ChildItem -LiteralPath $cache -Filter "*-$name")
    if ($cached.Count -ne 1) { throw "Missing/ambiguous cache dependency $name" }
    $preHash = (Get-FileHash -LiteralPath (Join-Path $PreRcMods $name)).Hash
    $nativeHash = (Get-FileHash -LiteralPath (Join-Path $nativeMods $name)).Hash
    $cacheHash = (Get-FileHash -LiteralPath $cached[0].FullName).Hash
    $rcHash = (Get-FileHash -LiteralPath (Join-Path $root "../rc-lab/mods/$name")).Hash
    if ($preHash -ne $pins[$name] -or $nativeHash -ne $preHash -or $cacheHash -ne $preHash -or $rcHash -ne $preHash) {
        throw "PRE-RC authority mismatch: $name; re-audit required, no build"
    }
    [pscustomobject]@{Jar=$name;PRE=$preHash;Native=$nativeHash;Cache=$cacheHash;RcLab=$rcHash}
}
New-Item -ItemType Directory -Path $classes, $tests, $temp, $deps -Force | Out-Null
$comparison | Export-Csv -LiteralPath (Join-Path $build 'dependency-comparison.csv') -NoTypeInformation
# Isolate tools from the read-only cache. No writes or timestamp updates to original JARs.
$depRecords = foreach ($file in (Get-ChildItem -LiteralPath $cache -Filter '*.jar' | Sort-Object Name)) {
    $target = Join-Path $deps $file.Name
    $hash = (Get-FileHash -LiteralPath $file.FullName).Hash
    if (-not (Test-Path -LiteralPath $target)) { Copy-Item -LiteralPath $file.FullName -Destination $target }
    if ((Get-FileHash -LiteralPath $target).Hash -ne $hash) { throw "Changed candidate dependency $target" }
    [pscustomobject]@{Source=$file.FullName;Local=$target;SHA256=$hash}
}
$depRecords | Export-Csv -LiteralPath (Join-Path $build 'dependency-sha256.csv') -NoTypeInformation
$cp = $depRecords.Local -join ';'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $root 'src/main/java') -Recurse -Filter '*.java')
$testSources = @(Get-ChildItem -LiteralPath (Join-Path $root 'src/test/java') -Recurse -Filter '*.java')
function Quote-Arg([string]$value) { '"' + $value.Replace('\','/') + '"' }
function Run-Java([string]$suite, [string[]]$extra) {
    $name = $suite.Split('.')[-1]
    if ($suite.EndsWith('LunarActivationTest')) { $name += '-' + $extra[0] }
    $argsFile = Join-Path $build ($name + '.args')
    $lines = @((Quote-Arg "-Djava.io.tmpdir=$temp"), '-classpath', (Quote-Arg "$classes;$tests;$cp"), $suite)
    $lines += @($extra | ForEach-Object { Quote-Arg $_ })
    [IO.File]::WriteAllLines($argsFile, $lines)
    & $java ('@' + $argsFile) 2>&1 | Tee-Object -FilePath (Join-Path $build ($name + '.log'))
    if ($LASTEXITCODE -ne 0) { throw "Test failed: $suite" }
}
Push-Location -LiteralPath $build
try {
    foreach ($part in @('main','test')) {
        $selected = if ($part -eq 'main') { $sources } else { $testSources }
        $outputDir = if ($part -eq 'main') { $classes } else { $tests }
        $classpath = if ($part -eq 'main') { $cp } else { "$classes;$cp" }
        $argsFile = Join-Path $build "$part-javac.args"
        $lines = @('--release','21','-encoding','UTF-8','-proc:none','-classpath',(Quote-Arg $classpath),'-d',(Quote-Arg $outputDir))
        $lines += $selected.FullName | ForEach-Object { Quote-Arg $_ }
        [IO.File]::WriteAllLines($argsFile, $lines)
        & $javac ('@' + $argsFile) 2>&1 | Tee-Object -FilePath (Join-Path $build "$part-compile.log")
        if ($LASTEXITCODE -ne 0) { throw "$part compilation failed" }
    }
    Copy-Item -Path (Join-Path $root 'src/main/resources/*') -Destination $classes -Recurse
    Run-Java 'fr.ascendant.renaissance.LunarPolicyTest' @()
    foreach ($mode in @('missing','false','true','invalid')) { Run-Java 'fr.ascendant.renaissance.LunarActivationTest' @($mode) }
    Run-Java 'fr.ascendant.renaissance.QuantumBytecodeTest' @((Join-Path $deps '0107-appliedenergistics2-19.2.17.jar'))
    Run-Java 'fr.ascendant.renaissance.mekanism.MekBytecodeTest' @((Join-Path $deps '0178-Mekanism-1.21.1-10.7.19.85.jar'))
    Run-Java 'fr.ascendant.renaissance.LunarContractTest' @($classes, $deps)
    $output = Join-Path $build 'lunar-logistics-0.1.0-candidate.jar'
    & $jar --create --file $output -C $classes .
    if ($LASTEXITCODE -ne 0) { throw 'JAR creation failed' }
    foreach ($record in (Import-Csv -LiteralPath (Join-Path $root 'reference/read-only-inputs-sha256.csv'))) {
        if ((Get-FileHash -LiteralPath $record.File).Hash -ne $record.SHA256) { throw "Read-only input changed: $($record.File)" }
    }
    if ((Get-FileHash -LiteralPath (Join-Path $root '../renaissance-controller/build.ps1')).Hash -ne
        (Get-FileHash -LiteralPath (Join-Path $root 'reference/controller-build.ps1')).Hash) {
        throw 'Original controller build script differs from read-only reference snapshot'
    }
    @($sources + $testSources + (Get-Item -LiteralPath $PSCommandPath) +
        (Get-ChildItem -LiteralPath (Join-Path $root 'src/main/resources') -Recurse -File)) | ForEach-Object {
        [pscustomobject]@{File=$_.FullName;SHA256=(Get-FileHash -LiteralPath $_.FullName).Hash}
    } | Export-Csv -LiteralPath (Join-Path $build 'sources-sha256.csv') -NoTypeInformation
    Get-FileHash -LiteralPath $output
    Write-Output "PASS. Build: $build. No install, network or server launch. Mixins NOT runtime-applied. Parent handles distribution packaging."
} finally { Pop-Location }
