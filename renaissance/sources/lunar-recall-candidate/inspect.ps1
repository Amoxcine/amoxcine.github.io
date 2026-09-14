param([string]$JavaHome = 'C:/Users/avets/AppData/Roaming/PrismLauncher/java/java-runtime-delta')
$ErrorActionPreference = 'Stop'
$pre = 'C:/Users/avets/AppData/Roaming/PrismLauncher/instances/Ascendant-Renaissance-PRE-RC/minecraft/mods'
$deps = Join-Path $PSScriptRoot 'dependencies'
$evidence = Join-Path $PSScriptRoot 'evidence'
New-Item -ItemType Directory -Path $deps, $evidence -Force | Out-Null
$pins = @{
 'Mekanism-1.21.1-10.7.19.85.jar' = '004DBC9F3106F4D192AEAA1EE1190DD16EC9CA8059ED3D093B80034F4C574F43'
 'youre-in-grave-danger-neoforge-2.0.13.jar' = 'DD2142A3C6A9D5B990AB36220BE482F7AA9F528755F93B8FEF8996F509DDCDA2'
}
foreach ($name in $pins.Keys) {
    $source = Join-Path $pre $name
    if ((Get-FileHash -LiteralPath $source).Hash -ne $pins[$name]) { throw "PRE target changed: $name" }
    $target = Join-Path $deps $name
    if (-not (Test-Path -LiteralPath $target)) { Copy-Item -LiteralPath $source -Destination $target }
    if ((Get-FileHash -LiteralPath $target).Hash -ne $pins[$name]) { throw "Candidate target changed: $name" }
}
$cp = ((Get-ChildItem -LiteralPath $deps -Filter '*.jar').FullName -join ';')
$types = @(
 'mekanism.api.event.MekanismTeleportEvent',
 'mekanism.api.event.MekanismTeleportEvent$GlobalTeleport',
 'mekanism.api.event.MekanismTeleportEvent$Robit',
 'mekanism.api.event.MekanismTeleportEvent$Teleporter',
 'mekanism.api.event.MekanismTeleportEvent$PortableTeleporter',
 'mekanism.common.entity.EntityRobit',
 'mekanism.common.tile.TileEntityTeleporter',
 'mekanism.common.network.to_server.PacketPortableTeleporterTeleport',
 'com.b1n_ry.yigd.item.DeathScrollItem',
 'com.b1n_ry.yigd.components.GraveComponent',
 'com.b1n_ry.yigd.events.YigdEvents$GraveClaimEvent',
 'com.b1n_ry.yigd.config.ExtraFeaturesConfig$ScrollConfig$ClickFunction',
 'com.b1n_ry.yigd.data.DeathInfoManager',
 'com.b1n_ry.yigd.events.YigdServerEventHandler')
foreach ($type in $types) {
    & (Join-Path $JavaHome 'bin/javap.exe') -p -c -s -classpath $cp $type |
        Out-File -LiteralPath (Join-Path $evidence "$type.txt") -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw "Inspection failed: $type" }
}
& (Join-Path $JavaHome 'bin/javap.exe') -p -v -classpath $cp 'com.b1n_ry.yigd.events.YigdServerEventHandler' |
    Out-File -LiteralPath (Join-Path $evidence 'YigdServerEventHandler.verbose.txt') -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw 'Event annotation inspection failed' }
Write-Output 'Pinned PRE bytecode inspection complete; no mod/server code executed.'
