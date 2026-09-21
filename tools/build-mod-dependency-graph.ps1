<#
Builds a dependency graph for a Packwiz pack from the metadata inside local NeoForge JARs.
The generated CSV and DOT files belong in an ignored output directory.
Missing JARs and dependencies loaded through code must be checked separately.
#>
param(
    [Parameter(Mandatory)] [string] $PackRoot,
    [Parameter(Mandatory)] [string[]] $JarDirectories,
    [Parameter(Mandatory)] [string] $OutputDirectory
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression

function Get-TomlValue([string] $text, [string] $key) {
    $match = [regex]::Match($text, '(?m)^\s*' + [regex]::Escape($key) + '\s*=\s*"([^"]+)"')
    if ($match.Success) { return $match.Groups[1].Value }
    return $null
}

$jars = @{}
foreach ($directory in $JarDirectories) {
    foreach ($jar in (Get-ChildItem -LiteralPath $directory -File -Filter '*.jar' -ErrorAction SilentlyContinue)) {
        if (-not $jars.ContainsKey($jar.Name)) { $jars[$jar.Name] = $jar.FullName }
    }
}

$mods = @()
foreach ($file in (Get-ChildItem -LiteralPath (Join-Path $PackRoot 'mods') -File -Filter '*.pw.toml')) {
    $source = Get-Content -LiteralPath $file.FullName -Raw
    $mods += [pscustomobject]@{
        slug = $file.Name -replace '\.pw\.toml$', ''
        name = Get-TomlValue $source 'name'
        filename = Get-TomlValue $source 'filename'
        side = Get-TomlValue $source 'side'
        path = $null
        ids = @()
    }
}

$rawEdges = @()
foreach ($mod in $mods) {
    if (-not $jars.ContainsKey($mod.filename)) { continue }
    $mod.path = $jars[$mod.filename]
    $zip = [IO.Compression.ZipFile]::OpenRead($mod.path)
    try {
        $entry = $zip.GetEntry('META-INF/neoforge.mods.toml')
        if (-not $entry) { $entry = $zip.GetEntry('META-INF/mods.toml') }
        if (-not $entry) { continue }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $metadata = $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $zip.Dispose() }

    if ($metadata -match '(?m)^\s*modLoader\s*=\s*"(klf|kotlinforforge)"') {
        $rawEdges += [pscustomobject]@{ source = $mod.slug; id = $Matches[1]; type = 'required' }
    }

    $section = ''
    $dependencyId = $null
    $dependencyType = $null
    foreach ($line in ($metadata -split "`r?`n")) {
        if ($line -match '^\s*\[\[mods\]\]') {
            $section = 'mod'
            continue
        }
        if ($line -match '^\s*\[\[dependencies\.([^\]]+)\]\]') {
            if ($dependencyId) {
                $rawEdges += [pscustomobject]@{ source = $mod.slug; id = $dependencyId; type = $dependencyType }
            }
            $section = 'dependency'
            $dependencyId = $null
            $dependencyType = 'unknown'
            continue
        }
        if ($line -match '^\s*\[') {
            if ($section -eq 'dependency' -and $dependencyId) {
                $rawEdges += [pscustomobject]@{ source = $mod.slug; id = $dependencyId; type = $dependencyType }
            }
            $section = ''
            $dependencyId = $null
            continue
        }
        if ($section -eq 'mod' -and $line -match '^\s*modId\s*=\s*["'']([^"'']+)["'']') {
            $mod.ids += $Matches[1]
        }
        if ($section -eq 'dependency') {
            if ($line -match '^\s*modId\s*=\s*["'']([^"'']+)["'']') { $dependencyId = $Matches[1] }
            if ($line -match '^\s*type\s*=\s*["'']([^"'']+)["'']') { $dependencyType = $Matches[1] }
            if ($line -match '^\s*mandatory\s*=\s*(true|false)') {
                $dependencyType = if ($Matches[1] -eq 'true') { 'required' } else { 'optional' }
            }
        }
    }
    if ($section -eq 'dependency' -and $dependencyId) {
        $rawEdges += [pscustomobject]@{ source = $mod.slug; id = $dependencyId; type = $dependencyType }
    }
    foreach ($inline in [regex]::Matches($metadata, '\{[^}]*\bmodId\s*=\s*["'']([^"'']+)["''][^}]*\}')) {
        $decl = $inline.Value
        $kind = 'unknown'
        if ($decl -match '\btype\s*=\s*["'']([^"'']+)["'']') { $kind = $Matches[1] }
        elseif ($decl -match '\bmandatory\s*=\s*(true|false)') {
            $kind = if ($Matches[1] -eq 'true') { 'required' } else { 'optional' }
        }
        $rawEdges += [pscustomobject]@{ source = $mod.slug; id = $inline.Groups[1].Value; type = $kind }
    }
}

# Kotlin for Forge keeps its NeoForge mod descriptor inside a nested JAR.
foreach ($mod in $mods) {
    if ($mod.slug -eq 'kotlin-for-forge' -and $mod.path -and $mod.ids.Count -eq 0) {
        $mod.ids = @('kotlinforforge')
    }
}

$byId = @{}
foreach ($mod in $mods) { foreach ($id in $mod.ids) { $byId[$id] = $mod.slug } }
$edges = @($rawEdges | Where-Object { $byId.ContainsKey($_.id) -and $_.type -notmatch '^(incompatible|discouraged)$' } | ForEach-Object {
    [pscustomobject]@{ source = $_.source; target = $byId[$_.id]; type = $_.type; modId = $_.id }
})

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$mods | Select-Object slug,name,filename,side,@{Name='jarFound';Expression={[bool]$_.path}},@{Name='modIds';Expression={$_.ids -join ','}} |
    Export-Csv -LiteralPath (Join-Path $OutputDirectory 'mods.csv') -NoTypeInformation -Encoding utf8
$edges | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'dependencies.csv') -NoTypeInformation -Encoding utf8
$rawEdges | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'raw-dependencies.csv') -NoTypeInformation -Encoding utf8

$dot = [Collections.Generic.List[string]]::new()
$dot.Add('digraph mods {')
$dot.Add('  rankdir=LR;')
$dot.Add('  node [shape=box, fontname="Arial"];')
foreach ($mod in $mods) {
    $label = $mod.name.Replace('"', '\"')
    $dot.Add('  "' + $mod.slug + '" [label="' + $label + '"];')
}
foreach ($edge in $edges) {
    $style = if ($edge.type -eq 'required') { 'solid' } else { 'dashed' }
    $dot.Add('  "' + $edge.source + '" -> "' + $edge.target + '" [style=' + $style + '];')
}
$dot.Add('}')
[IO.File]::WriteAllLines((Join-Path $OutputDirectory 'dependencies.dot'), $dot)
Write-Output "Mods: $($mods.Count); JAR matched: $(($mods | Where-Object path).Count); internal edges: $($edges.Count)"
