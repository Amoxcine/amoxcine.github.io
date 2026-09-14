param([string]$JavaHome = 'C:/Users/avets/AppData/Roaming/PrismLauncher/java/java-runtime-delta')
$ErrorActionPreference = 'Stop'
$mods = Join-Path $PSScriptRoot '../rc-lab/mods'
$nested = Join-Path $PSScriptRoot 'build/cargo-dependencies'
New-Item -ItemType Directory -Path $nested -Force | Out-Null
$archive = [IO.Compression.ZipFile]::OpenRead((Join-Path $mods 'common-storage-lib-neoforge-1.21.1-0.0.10.jar'))
try {
 foreach ($part in @('data', 'lookup', 'resources')) {
  $name = "common-storage-lib-$part-neoforge-1.21.1-0.0.10.jar"
  [IO.Compression.ZipFileExtensions]::ExtractToFile($archive.GetEntry("META-INF/jars/$name"), (Join-Path $nested $name), $true)
 }
} finally { $archive.Dispose() }
$names = @('adastra-1.21.1-1.16.24-neoforge.jar', 'common-storage-lib-neoforge-1.21.1-0.0.10.jar',
 'curios-neoforge-9.5.1+1.21.1.jar', 'accessories-neoforge-1.1.0-beta.53+1.21.1.jar')
$cp = (@($names | ForEach-Object { (Join-Path $mods $_) }) +
 (Join-Path $PSScriptRoot '../renaissance-controller/build/dependencies/0000-neoforge-21.1.248-server.jar') +
 (Join-Path $PSScriptRoot '../renaissance-controller/build/dependencies/0001-neoforge-21.1.248-universal.jar')) -join ';'
$cp += ';' + (@(Get-ChildItem $nested -Filter '*.jar').FullName -join ';')
$output = Join-Path $PSScriptRoot 'evidence/cargo'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$types = @(
 'top.theillusivec4.curios.api.CuriosApi', 'top.theillusivec4.curios.api.type.capability.ICuriosItemHandler',
 'top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler', 'top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler',
 'io.wispforest.accessories.api.AccessoriesCapability', 'io.wispforest.accessories.api.AccessoriesContainer',
 'io.wispforest.accessories.impl.ExpandedSimpleContainer', 'io.wispforest.accessories.api.AccessoriesHolder',
 'earth.terrarium.adastra.common.registry.ModDataManagers',
 'earth.terrarium.common_storage_lib.data.DataManager', 'earth.terrarium.common_storage_lib.fluid.util.FluidStorageData',
 'earth.terrarium.common_storage_lib.resources.ResourceStack', 'earth.terrarium.common_storage_lib.resources.fluid.FluidResource',
 'earth.terrarium.adastra.common.entities.vehicles.Rocket', 'earth.terrarium.adastra.common.utils.FluidUtils',
 'net.minecraft.world.item.ItemStack', 'net.minecraft.world.item.component.ItemContainerContents',
 'net.minecraft.world.item.component.BundleContents', 'net.minecraft.world.item.component.ChargedProjectiles',
 'io.wispforest.accessories.impl.AccessoriesHolderImpl', 'top.theillusivec4.curios.common.capability.CuriosInventory',
 'net.neoforged.neoforge.capabilities.Capabilities$ItemHandler', 'net.minecraft.core.component.DataComponentMap')
foreach ($type in $types) {
 & (Join-Path $JavaHome 'bin/javap.exe') -p -c -classpath $cp $type | Out-File (Join-Path $output "$type.txt") -Encoding utf8
 if ($LASTEXITCODE -ne 0) { Write-Warning "Inspection unavailable: $type" }
}
$names | ForEach-Object { Get-FileHash (Join-Path $mods $_) -Algorithm SHA256 } | Format-List
