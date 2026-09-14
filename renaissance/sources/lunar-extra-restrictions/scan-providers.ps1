$ErrorActionPreference = 'Stop'
$mods = 'C:/Users/avets/AppData/Roaming/PrismLauncher/instances/Ascendant-Renaissance-PRE-RC/minecraft/mods'
$out = Join-Path $PSScriptRoot 'build/inspection/provider-scan.json'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$script:matches = [Collections.Generic.List[string]]::new()
$script:classes = 0
function ScanZip($zip, [string]$prefix, [bool]$nested) {
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName.EndsWith('.class') -or ($nested -and $entry.FullName.EndsWith('.jar'))) {
            $stream = $entry.Open()
            $memory = [IO.MemoryStream]::new()
            try {
                $stream.CopyTo($memory)
                if ($entry.FullName.EndsWith('.class')) {
                    $script:classes++
                    $content = [Text.Encoding]::UTF8.GetString($memory.ToArray())
                    if ($content.Contains('com/simibubi/create/api/contraption/train/PortalTrackProvider') -or
                        $content.Contains('com.simibubi.create.api.contraption.train.PortalTrackProvider')) {
                        $script:matches.Add($prefix + '!/' + $entry.FullName)
                    }
                } else {
                    $memory.Position = 0
                    $child = [IO.Compression.ZipArchive]::new($memory, [IO.Compression.ZipArchiveMode]::Read, $true)
                    try { ScanZip $child ($prefix + '!/' + $entry.FullName) $false } finally { $child.Dispose() }
                }
            } finally { $stream.Dispose(); $memory.Dispose() }
        }
    }
}
$inputs = @()
foreach ($file in (Get-ChildItem -LiteralPath $mods -Filter '*.jar' | Sort-Object Name)) {
    $inputs += [pscustomobject]@{file=$file.Name; sha256=(Get-FileHash $file.FullName).Hash}
    $zip = [IO.Compression.ZipFile]::OpenRead($file.FullName)
    try { ScanZip $zip $file.Name $true } finally { $zip.Dispose() }
}
[pscustomobject]@{
    scannedUtc=[DateTime]::UtcNow.ToString('o'); topLevelJars=$inputs.Count; classes=$script:classes;
    depth='top-level classes plus first-level nested jars';
    limitation='Static binary name references only; not proof against reflection-generated names or runtime registrations';
    references=@($script:matches); inputs=$inputs
} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $out -Encoding UTF8
"Scanned $($inputs.Count) jars and $script:classes classes; $($script:matches.Count) provider references."
