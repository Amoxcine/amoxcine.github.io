param([string]$JavaHome = 'C:/Users/avets/AppData/Roaming/PrismLauncher/java/java-runtime-delta')
$ErrorActionPreference = 'Stop'
$dest = Join-Path $PSScriptRoot 'build/dependencies'
New-Item -ItemType Directory -Path $dest -Force | Out-Null
$names = @('adastra-1.21.1-1.16.24-neoforge.jar', 'waystones-neoforge-1.21.1-21.1.41.jar', 'Draconic-Evolution-1.21.1-3.1.4.632.jar')
$names += @(Get-ChildItem (Join-Path $PSScriptRoot '../rc-lab/mods') -Filter '*BrandonsCore*.jar' | Select-Object -ExpandProperty Name)
foreach ($name in $names) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot "../rc-lab/mods/$name") -Destination $dest }
$evidence = Join-Path $PSScriptRoot 'evidence'
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
$cp = (@(Get-ChildItem $dest -Filter '*.jar').FullName -join ';')
$types = @(
 'earth.terrarium.adastra.common.entities.vehicles.Rocket',
 'earth.terrarium.adastra.common.network.packets.ServerboundLandPacket$Type',
 'earth.terrarium.adastra.common.utils.ModUtils',
 'earth.terrarium.adastra.common.utils.PlatformUtils',
 'net.blay09.mods.waystones.core.WaystoneTeleportManager',
 'net.blay09.mods.waystones.api.Waystone',
 'net.blay09.mods.waystones.api.EntityTeleportResult',
 'net.blay09.mods.waystones.api.error.WaystoneTeleportError$CancelledByEvent',
 'com.brandon3055.draconicevolution.items.tools.Dislocator',
 'com.brandon3055.draconicevolution.items.tools.DislocatorAdvanced',
 'com.brandon3055.draconicevolution.blocks.tileentity.TileDislocatorReceptacle',
 'com.brandon3055.draconicevolution.blocks.tileentity.TileDislocatorPedestal',
 'com.brandon3055.brandonscore.utils.TargetPos')
foreach ($type in $types) {
 & (Join-Path $JavaHome 'bin/javap.exe') -p -c -classpath $cp $type | Out-File (Join-Path $evidence "$type.txt") -Encoding utf8
 if ($LASTEXITCODE -ne 0) { throw "Inspection failed: $type" }
}
Get-ChildItem $dest -Filter '*.jar' | Get-FileHash -Algorithm SHA256 | Format-List
