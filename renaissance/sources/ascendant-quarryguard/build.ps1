[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$ServerRoot,
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    throw 'JavaHome is required when JAVA_HOME is not defined.'
}
$ServerRoot = [IO.Path]::GetFullPath($ServerRoot)
$JavaHome = [IO.Path]::GetFullPath($JavaHome)
foreach ($path in @(
    (Join-Path $ServerRoot 'libraries'),
    (Join-Path $ServerRoot 'mods'),
    (Join-Path $JavaHome 'bin\javac.exe'),
    (Join-Path $JavaHome 'bin\jar.exe')
)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Required path not found: $path" }
}

$build = Join-Path $PSScriptRoot 'build'
$classes = Join-Path $build ('classes-' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff'))
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$libs = Get-ChildItem -LiteralPath (Join-Path $ServerRoot 'libraries') -Recurse -Filter '*.jar'
$mods = Get-ChildItem -LiteralPath (Join-Path $ServerRoot 'mods') -Filter '*.jar'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$neoJar = $libs | Where-Object Name -EQ 'neoforge-21.1.248-universal.jar'
if (@($neoJar).Count -ne 1) { throw 'Expected NeoForge 21.1.248 universal JAR was not found exactly once.' }
$zip = [IO.Compression.ZipFile]::OpenRead($neoJar.FullName)
try {
    $extra = $zip.Entries | Where-Object FullName -Match 'mixinextras.*jar$' | Select-Object -First 1
    if (-not $extra) { throw 'Bundled MixinExtras missing' }
    $extraPath = Join-Path $build $extra.Name
    [IO.Compression.ZipFileExtensions]::ExtractToFile($extra, $extraPath, $true)
} finally { $zip.Dispose() }
$jars = @($libs | Where-Object Name -EQ 'neoforge-21.1.248-server.jar') +
    @($libs | Where-Object Name -EQ 'neoforge-21.1.248-universal.jar') +
    @($libs | Where-Object Name -Like '*-srg.jar') + @($libs) + @($mods)
$cp = (($jars.FullName + @($extraPath)) | Select-Object -Unique) -join ';'
$sourceRoots = @((Join-Path $PSScriptRoot 'src\main\java'))
$sources = @($sourceRoots | ForEach-Object { Get-ChildItem -LiteralPath $_ -Recurse -Filter '*.java' })
if ($sources.Count -eq 0) { throw 'No Java source files found' }
$argsFile = Join-Path $build 'javac.args'
$argsLines = @('--release', '21', '-encoding', 'UTF-8', '-proc:none', '-classpath', ('"' + $cp.Replace('\','/') + '"'), '-d', ('"' + $classes.Replace('\','/') + '"'))
$argsLines += $sources.FullName | ForEach-Object { '"' + $_.Replace('\','/') + '"' }
[IO.File]::WriteAllLines($argsFile, $argsLines, [Text.UTF8Encoding]::new($false))
& (Join-Path $JavaHome 'bin\javac.exe') ('@' + $argsFile)
if ($LASTEXITCODE -ne 0) { throw "javac failed: $LASTEXITCODE" }
$resources = Join-Path $PSScriptRoot 'src\main\resources'
Copy-Item -Path (Join-Path $resources '*') -Destination $classes -Recurse -Force
$output = Join-Path $build 'ascendant-quarryguard-0.1.0-lab.jar'
& (Join-Path $JavaHome 'bin\jar.exe') --create --file $output -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed: $LASTEXITCODE" }
Get-FileHash -LiteralPath $output -Algorithm SHA256
$manifest = Join-Path $classes 'META-INF\neoforge.mods.toml'
$manifestText = Get-Content -LiteralPath $manifest -Raw
$driverText = [regex]::Replace($manifestText, '(?ms)^\[\[mixins\]\]\s*\r?\nconfig="ascendant-quarryguard\.mixins\.json"\s*', '')
if ($driverText -eq $manifestText) { throw 'Expected exact laboratory Mixin section missing' }
[IO.File]::WriteAllText($manifest, $driverText, [Text.UTF8Encoding]::new($false))
$driver = Join-Path $build 'ascendant-lab-driver-NO-PROTECTION.jar'
& (Join-Path $JavaHome 'bin\jar.exe') --create --file $driver -C $classes .
if ($LASTEXITCODE -ne 0) { throw "driver jar failed: $LASTEXITCODE" }
Get-FileHash -LiteralPath $driver -Algorithm SHA256
$pair = [ordered]@{
    buildId = Split-Path $classes -Leaf
    guarded = [ordered]@{ name = Split-Path $output -Leaf; sha256 = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash }
    baseline = [ordered]@{ name = Split-Path $driver -Leaf; sha256 = (Get-FileHash -LiteralPath $driver -Algorithm SHA256).Hash }
}
[IO.File]::WriteAllText((Join-Path $build 'build-pair.json'), ($pair | ConvertTo-Json -Depth 4), [Text.UTF8Encoding]::new($false))
