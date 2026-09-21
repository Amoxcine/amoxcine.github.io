<#
Scans the same local JARs for code and content references to selected library mods.
Run after build-mod-dependency-graph.ps1; it reads mods.csv from its output directory.
#>
param([string] $GraphDirectory, [string[]] $JarDirectories)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
$patterns = [ordered]@{
    AzureLibArmor = 'mod/azure/azurelibarmor/'
    ShieldAPI = 'net/fabric_extras/shield_api/'
    FabricAPI = 'net/fabricmc/fabric/api/'
    Accessories = 'io/wispforest/accessories/'
    Patchouli = 'vazkii/patchouli/'
    MultiPiston = 'com/ldtteam/multipiston/'
}
$rows = @()
$mods = Import-Csv (Join-Path $GraphDirectory 'mods.csv')
foreach ($mod in ($mods | Where-Object { $_.jarFound -eq 'True' })) {
    $jarPath = $null
    foreach ($directory in $JarDirectories) {
        $candidate = Join-Path $directory $mod.filename
        if (Test-Path -LiteralPath $candidate) { $jarPath = $candidate; break }
    }
    if (-not $jarPath) { continue }
    $found = @{}
    $zip = [IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -like '*patchouli_books*') { $found['PatchouliBooks'] = $true }
            if ($entry.FullName -notlike '*.class') { continue }
            $stream = $entry.Open()
            $buffer = [IO.MemoryStream]::new()
            try { $stream.CopyTo($buffer); $body = [Text.Encoding]::Latin1.GetString($buffer.ToArray()) }
            finally { $buffer.Dispose(); $stream.Dispose() }
            foreach ($key in $patterns.Keys) {
                if (-not $found.ContainsKey($key) -and $body.Contains($patterns[$key])) { $found[$key] = $true }
            }
        }
    } finally { $zip.Dispose() }
    foreach ($key in $found.Keys) { $rows += [pscustomobject]@{ source = $mod.slug; reference = $key } }
}
$rows | Export-Csv (Join-Path $GraphDirectory 'class-references.csv') -NoTypeInformation -Encoding utf8
$rows | Group-Object reference | ForEach-Object { "{0}: {1}" -f $_.Name, (($_.Group.source | Sort-Object) -join ', ') }
