// Only ambient spawning: machinery, scripted encounters and existing mobs are untouched.
(() => {
  var Check = Java.loadClass('net.neoforged.neoforge.event.entity.living.MobSpawnEvent$PositionCheck');
  var Result = Java.loadClass('net.neoforged.neoforge.event.entity.living.MobSpawnEvent$PositionCheck$Result');
  var SpawnType = Java.loadClass('net.minecraft.world.entity.MobSpawnType');
  var Category = Java.loadClass('net.minecraft.world.entity.MobCategory');
  var BuiltIn = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries');
  var RL = Java.loadClass('net.minecraft.resources.ResourceLocation');
  var Pos = Java.loadClass('net.minecraft.core.BlockPos');
  var Oxygen = Java.loadClass('earth.terrarium.adastra.api.systems.OxygenApi');
  var Priority = Java.loadClass('net.neoforged.bus.api.EventPriority');
  var moon = 'ad_astra:moon';

  NativeEvents.onEvent(Priority.LOWEST, Check, event => {
    if (event.getResult() == Result.FAIL) return;
    if (event.getSpawnType() != SpawnType.NATURAL && event.getSpawnType() != SpawnType.CHUNK_GENERATION) return;
    var level = event.getLevel().getLevel();
    if (String(level.dimension) != moon) return;
    var mob = event.getEntity();
    var type = BuiltIn.ENTITY_TYPE.get(RL.parse(String(mob.type)));
    if (type.getCategory() != Category.MONSTER) return;
    // A native, powered oxygen volume is the habitat boundary; no world or claim scan.
    var pos = new Pos(Math.floor(event.getX()), Math.floor(event.getY()), Math.floor(event.getZ()));
    if (Oxygen.API['hasOxygen(net.minecraft.world.level.Level,net.minecraft.core.BlockPos)'](level, pos)) {
      event.setResult(Result.FAIL);
    }
  });
  console.info('[LUNAR-HABITAT] ambient protection registered; dimension=' + moon);
})();
