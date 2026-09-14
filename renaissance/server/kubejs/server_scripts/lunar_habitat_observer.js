// LUNAR003: one clicked native distributor; no air, fluid or energy writes.
;(function() {
  var bridge = global.lunarHabitatObserver
  if (!bridge || bridge.contract !== 1) throw new Error('[Lunar habitat] Missing startup bridge')
  var Player = Java.loadClass('net.minecraft.server.level.ServerPlayer')
  var FakePlayer = Java.loadClass('net.neoforged.neoforge.common.util.FakePlayer')
  var QuestFile = Java.loadClass('dev.ftb.mods.ftbquests.quest.ServerQuestFile')
  var Custom = Java.loadClass('dev.ftb.mods.ftbquests.quest.task.CustomTask')
  var Distributor = Java.loadClass('earth.terrarium.adastra.common.blockentities.machines.OxygenDistributorBlockEntity')
  var Oxygen = Java.loadClass('earth.terrarium.adastra.api.systems.OxygenApi')
  var Long = Java.loadClass('java.lang.Long')
  var id = Long.valueOf('6C13030000000018', 16)
  var owner = { warned: false }
  bridge.owner = owner
  function observe(event) {
    if (bridge.owner !== owner || global.lunarHabitatObserver !== bridge) return
    var player = event.getEntity()
    if (!(player instanceof Player) || player instanceof FakePlayer || !player.isAlive()
        || player.isCreative() || player.isSpectator()) return
    var server = player.getServer()
    if (server === null || !server.isSameThread() || server.getPlayerList().getPlayer(player.getUUID()) !== player) return
    // KubeJS 377 exposes LevelKJS.kjs$getDimension as a ResourceLocation property.
    var level = player.level
    if (String(level.dimension) !== 'ad_astra:moon') return
    var block = event.getBlock()
    var pos = block.getPos()
    if (block.getLevel() !== level || !level.hasChunkAt(pos) || player.blockPosition().distSqr(pos) > 36) return
    var machine = level.getBlockEntity(pos)
    if (!(machine instanceof Distributor) || machine.isRemoved() || !machine.isLit()
        || Number(machine.distributedBlocksCount()) <= 0
        || Number(machine.getEnergyStorage().getStoredAmount()) <= 0
        || Number(machine.getFluidContainer().getAmount(1)) <= 0) return
    var feet = player.blockPosition()
    if (!Oxygen.API['hasOxygen(net.minecraft.world.level.Level,net.minecraft.core.BlockPos)'](level, feet)
        || !Oxygen.API['hasOxygen(net.minecraft.world.level.Level,net.minecraft.core.BlockPos)'](level, feet.above())) return
    var file = QuestFile.INSTANCE
    if (file === null || file.isLoading()) return
    var maybeTeam = file.getTeamData(player)
    if (!maybeTeam.isPresent()) return
    var team = maybeTeam.get()
    if (!file.isPlayerOnTeam(player, team)) return
    var task = file.getTask(id)
    if (!(task instanceof Custom) || String(task.getCodeString()) !== '6C13030000000018'
        || String(task.getQuest().getCodeString()) !== '6C13020000000011'
        || String(task.getQuestChapter().getCodeString()) !== '6C13010000000002'
        || task.getQuest().getTasks().size() !== 1 || !task.getQuest().getRewards().isEmpty())
      throw new Error('Habitat task absent or changed')
    if (team.isCompleted(task) || !team.canStartTasks(task.getQuest())) return
    if (file.getTeamData(player).orElse(null) !== team || !file.isPlayerOnTeam(player, team)
        || level.getBlockEntity(pos) !== machine || !team.canStartTasks(task.getQuest())) return
    team.setProgress(task, 1)
  }
  BlockEvents.rightClicked('ad_astra:oxygen_distributor', function(event) {
    try { observe(event) } catch (error) {
      if (!owner.warned) {
        owner.warned = true
        console.error('[Lunar habitat] Native observation failed; no completion: ' + String(error))
      }
    }
  })
  ServerEvents.unloaded(function() { if (bridge.owner === owner) bridge.owner = null })
})()
