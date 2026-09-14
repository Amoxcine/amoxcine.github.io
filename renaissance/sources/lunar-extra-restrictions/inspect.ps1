$ErrorActionPreference = 'Stop'
$deps = Join-Path $PSScriptRoot '../aventure-20260905/renaissance-controller/build/dependencies'
$out = Join-Path $PSScriptRoot 'build/inspection'
[void](New-Item -ItemType Directory -Force $out)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$advanced = Get-ChildItem $deps -Filter '*-AdvancedAE-1.6.12-1.21.1.jar' | Select-Object -First 1
$zip = [IO.Compression.ZipFile]::OpenRead($advanced.FullName)
try {
    $entry = $zip.GetEntry('META-INF/jarjar/ae2addonlib-1.0.3-1.21.1.jar')
    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $out 'ae2addonlib-1.0.3-1.21.1.jar'), $true)
} finally { $zip.Dispose() }
$create = Get-ChildItem $deps -Filter '*-create-1.21.1-6.0.10.jar' | Select-Object -First 1
$zip = [IO.Compression.ZipFile]::OpenRead($create.FullName)
try {
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName.StartsWith('META-INF/jarjar/') -and $entry.FullName.EndsWith('.jar')) {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $out ([IO.Path]::GetFileName($entry.FullName))), $true)
        }
    }
} finally { $zip.Dispose() }
