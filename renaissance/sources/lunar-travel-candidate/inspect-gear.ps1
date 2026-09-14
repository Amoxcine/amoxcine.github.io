param([string]$JavaHome = 'C:/Users/avets/AppData/Roaming/PrismLauncher/java/java-runtime-delta')
$ErrorActionPreference = 'Stop'
$cp = @('../rc-lab/mods/Apotheosis-1.21.1-8.7.0.jar','../rc-lab/mods/Draconic-Evolution-1.21.1-3.1.4.632.jar','../rc-lab/mods/irons_spellbooks-1.21.1-3.16.3.jar') -join ';'
$dest = Join-Path $PSScriptRoot 'evidence/gear'
New-Item -ItemType Directory -Path $dest -Force | Out-Null
foreach ($type in @('dev.shadowsoffire.apotheosis.Apoth$Components', 'dev.shadowsoffire.apotheosis.affix.ItemAffixes', 'dev.shadowsoffire.apotheosis.socket.SocketedGems', 'dev.shadowsoffire.apotheosis.socket.gem.GemInstance', 'dev.shadowsoffire.apotheosis.socket.gem.UnsocketedGem', 'com.brandon3055.draconicevolution.init.ItemData', 'com.brandon3055.draconicevolution.api.modules.lib.ModuleHostContainer', 'com.brandon3055.draconicevolution.api.modules.lib.ModuleHostImpl', 'com.brandon3055.draconicevolution.api.modules.lib.ModuleEntity', 'com.brandon3055.draconicevolution.items.equipment.ModularChestpiece', 'io.redspace.ironsspellbooks.item.weapons.StaffItem')) {
 & (Join-Path $JavaHome 'bin/javap.exe') -p -c -classpath $cp $type | Out-File (Join-Path $dest "$type.txt") -Encoding utf8
 if ($LASTEXITCODE -ne 0) { throw "Inspection failed: $type" }
}
