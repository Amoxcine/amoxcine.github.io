// Complementary early refusals; final travel and inventory guards are still required.
(() => {
  var ServerLevel = Java.loadClass('net.minecraft.server.level.ServerLevel');
  var ServerPlayer = Java.loadClass('net.minecraft.server.level.ServerPlayer');
  var Component = Java.loadClass('net.minecraft.network.chat.Component');
  var Priority = Java.loadClass('net.neoforged.bus.api.EventPriority');
  var IronPre = Java.loadClass('io.redspace.ironsspellbooks.api.events.SpellPreCastEvent');
  var ArsPre = Java.loadClass('com.hollingsworth.arsnouveau.api.event.SpellCastEvent');
  var Blink = Java.loadClass('com.hollingsworth.arsnouveau.common.spell.effect.EffectBlink');
  var Chest = Java.loadClass('com.hollingsworth.arsnouveau.common.spell.effect.EffectEnderChest');
  var NetherPortal = Java.loadClass('net.neoforged.neoforge.event.level.BlockEvent$PortalSpawnEvent');
  var iron = ['portal', 'recall', 'teleport', 'blood_step', 'frost_step', 'thunder_step', 'summon_ender_chest'];
  var protectedLevel = level => level instanceof ServerLevel
    && ['ad_astra:moon', 'ad_astra:moon_orbit'].indexOf(String(level.dimension)) >= 0;
  var notify = entity => {
    if (entity instanceof ServerPlayer) entity.sendSystemMessage(Component.literal(
      'Quarantaine lunaire : ce transport ou acces distant est indisponible ici.'));
  };

  NativeEvents.onEvent(Priority.HIGHEST, IronPre, event => {
    if (!protectedLevel(event.getEntity().level)) return;
    var id = String(event.getSpellId());
    if (iron.indexOf(id.replace('irons_spellbooks:', '')) < 0) return;
    event.setCanceled(true);
    notify(event.getEntity());
  });
  NativeEvents.onEvent(Priority.HIGHEST, ArsPre, event => {
    if (!protectedLevel(event.getWorld())) return;
    if (event.spell.getInstanceCount(Blink.INSTANCE) < 1 && event.spell.getInstanceCount(Chest.INSTANCE) < 1) return;
    event.setCanceled(true);
    notify(event.getEntity());
  });
  NativeEvents.onEvent(Priority.HIGHEST, NetherPortal, event => {
    if (protectedLevel(event.getLevel())) event.setCanceled(true);
  });
  console.info('[LUNAR-PREFLIGHT] native spell/nether pre-events registered; no narrative portal exception');
})();
