[CmdletBinding()]
param([switch]$Apply,[switch]$ServerStopped)
$ErrorActionPreference='Stop'
$root=(Resolve-Path $PSScriptRoot).Path
$lab=(Resolve-Path (Join-Path $root '../lunar-native-lab')).Path
if([IO.Path]::GetFileName($lab) -ne 'lunar-native-lab' -or !(Test-Path -LiteralPath (Join-Path $lab 'COPY-MANIFEST.json'))){throw 'Expected isolated lunar-native-lab copy only'}
if((Get-Item -LiteralPath $lab).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Reparse-point lab refused'}
$mods=(Resolve-Path (Join-Path $lab 'mods')).Path
if($mods -ne (Join-Path $lab 'mods') -or (Get-Item -LiteralPath $mods).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Redirected mods directory refused'}
$manifest=Get-Content -LiteralPath (Join-Path $root 'dist/artifact.json') -Raw | ConvertFrom-Json
$jar=Join-Path $root 'dist/ascendant-lunar-encounter-0.1.2-candidate.jar'
if((Get-FileHash -LiteralPath $jar).Hash -ne $manifest.sha256){throw 'Candidate artifact hash mismatch'}
$destination=Join-Path $mods ([IO.Path]::GetFileName($jar))
foreach($file in @(Get-ChildItem -LiteralPath $mods -File -Filter '*encounter*.jar')) {
    if($file.FullName -ne $destination -or (Get-FileHash -LiteralPath $file.FullName).Hash -ne $manifest.sha256){throw "Existing encounter candidate: $($file.Name). No replacement/deletion permitted"}
}
if(!$Apply){Write-Output "DRY RUN: additive candidate jar only -> $destination. No config, world, process or installed mod changed.";return}
if(!$ServerStopped){throw 'Parent must confirm -ServerStopped after shutting down this lab'}
$running=@(Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^javaw?\.exe$' -and $_.CommandLine -and $_.CommandLine.Contains('lunar-native-lab') })
if($running.Count){throw 'Lab Java process detected; refuse staging'}
if(!(Test-Path -LiteralPath $destination)){[IO.File]::Copy($jar,$destination,$false)}
if((Get-FileHash -LiteralPath $destination).Hash -ne $manifest.sha256){throw 'Staged artifact hash mismatch'}
Write-Output "Staged candidate jar only: $destination. Still default-off. Parent owns world registration, configuration and native boot."
