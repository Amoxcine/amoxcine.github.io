param([string]$JavaHome = 'C:/Users/avets/AppData/Roaming/PrismLauncher/java/java-runtime-delta')
$ErrorActionPreference = 'Stop'
$cp = @('../rc-lab/mods/adastra-1.21.1-1.16.24-neoforge.jar', '../renaissance-controller/build/dependencies/0000-neoforge-21.1.248-server.jar', '../renaissance-controller/build/dependencies/0002-server-1.21.1-20240808.144430-srg.jar') -join ';'
$dest = Join-Path $PSScriptRoot 'evidence/recovery'
New-Item -ItemType Directory -Path $dest -Force | Out-Null
foreach ($type in @('earth.terrarium.adastra.common.handlers.LaunchingDimensionHandler', 'earth.terrarium.adastra.common.utils.PlatformUtils', 'earth.terrarium.adastra.common.entities.vehicles.Rocket', 'net.minecraft.server.level.ServerPlayer', 'net.minecraft.world.entity.Entity')) {
 & (Join-Path $JavaHome 'bin/javap.exe') -p -c -classpath $cp $type | Out-File (Join-Path $dest "$type.txt") -Encoding utf8
 if ($LASTEXITCODE -ne 0) { throw "Inspection failed: $type" }
}
