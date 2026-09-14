param([string]$JavaHome = 'C:/Users/avets/AppData/Roaming/PrismLauncher/java/java-runtime-delta')
$ErrorActionPreference = 'Stop'
$cp = @('../rc-lab/mods/Apotheosis-1.21.1-8.7.0.jar', '../renaissance-controller/build/dependencies/0000-neoforge-21.1.248-server.jar', '../renaissance-controller/build/dependencies/0002-server-1.21.1-20240808.144430-srg.jar') -join ';'
$dest = Join-Path $PSScriptRoot 'evidence/gem'
New-Item -ItemType Directory -Path $dest -Force | Out-Null
foreach ($type in @('dev.shadowsoffire.apotheosis.socket.gem.GemItem', 'dev.shadowsoffire.apotheosis.Apoth$Items', 'net.minecraft.core.component.DataComponents', 'net.minecraft.world.item.Item$Properties', 'net.minecraft.world.item.enchantment.ItemEnchantments', 'net.minecraft.world.item.component.ItemAttributeModifiers')) {
 & (Join-Path $JavaHome 'bin/javap.exe') -p -c -classpath $cp $type | Out-File (Join-Path $dest "$type.txt") -Encoding utf8
 if ($LASTEXITCODE -ne 0) { throw "Inspection failed: $type" }
}
