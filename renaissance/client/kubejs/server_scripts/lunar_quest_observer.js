// LUNAR002: current native component proof, no fluid transfer and no arrival grant.
;(function() {
  var bridge = global.lunarQuestObserver
  if (!bridge || bridge.contract !== 1) throw new Error('[Lunar quests] Missing startup bridge; full restart required')
  var Player = Java.loadClass('net.minecraft.server.level.ServerPlayer')
  var FakePlayer = Java.loadClass('net.neoforged.neoforge.common.util.FakePlayer')
  var QuestFile = Java.loadClass('dev.ftb.mods.ftbquests.quest.ServerQuestFile')
  var Custom = Java.loadClass('dev.ftb.mods.ftbquests.quest.task.CustomTask')
  var Long = Java.loadClass('java.lang.Long')
  var Registries = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries')
  var DataManagers = Java.loadClass('earth.terrarium.adastra.common.registry.ModDataManagers')
  var FluidUtils = Java.loadClass('earth.terrarium.adastra.common.utils.FluidUtils')
  var taskId = Long.valueOf('6C13030000000009', 16)
  var owner = { warned: false }
  bridge.owner = owner

  function fullSuit(stack) {
    if (!stack || stack.isEmpty() || stack.getCount() !== 1
        || String(Registries.ITEM.getKey(stack.getItem())) !== 'ad_astra:space_suit') return false
    // Reading the same component backing SpaceSuitItem.getFluids: no context mutation.
    var data = stack['get(net.minecraft.core.component.DataComponentType)'](DataManagers.FLUID_CONTENTS.componentType())
    if (data === null) return false
    var tanks = data.stacks()
    if (tanks.size() !== 1) return false
    var tank = tanks.get(0)
    if (tank === null || tank.isEmpty()
        || String(Registries.FLUID.getKey(tank.resource().getType())) !== 'ad_astra:oxygen') return false
    var amount = Number(tank.amount())
    // Native capacity getter performs only capability lookup + getLimit(0, BLANK).
    var capacity = Number(FluidUtils['getTankCapacity(net.minecraft.world.item.ItemStack)'](stack))
    return isFinite(amount) && isFinite(capacity) && capacity > 0 && capacity <= 9007199254740991
      && Math.floor(capacity) === capacity && amount === capacity
  }

  function observe(player) {
    if (bridge.owner !== owner || global.lunarQuestObserver !== bridge
        || !(player instanceof Player) || player instanceof FakePlayer
        || !player.isAlive() || player.isSpectator() || player.isCreative()) return
    var server = player.getServer()
    if (server === null || !server.isSameThread()
        || server.getPlayerList().getPlayer(player.getUUID()) !== player) return
    var file = QuestFile.INSTANCE
    if (file === null || file.isLoading()) return
    var maybeTeam = file.getTeamData(player)
    if (!maybeTeam.isPresent()) return
    var team = maybeTeam.get()
    if (!file.isPlayerOnTeam(player, team)) return
    var task = file.getTask(taskId)
    if (!(task instanceof Custom) || String(task.getCodeString()) !== '6C13030000000009'
        || String(task.getQuestChapter().getCodeString()) !== '6C13010000000001'
        || String(task.getQuest().getCodeString()) !== '6C13020000000006'
        || task.getQuest().getTasks().size() !== 1 || !task.getQuest().getRewards().isEmpty())
      throw new Error('Oxygen quest target absent or changed')
    if (team.isCompleted(task) || !team.canStartTasks(task.getQuest())) return
    var inventory = player.inventory
    var size = Number(inventory.slots)
    if (!isFinite(size) || Math.floor(size) !== size || size < 1 || size > 41) throw new Error('Unexpected player inventory size')
    // Fixed player inventory only: 36 slots + 4 armour + offhand, no nested containers.
    for (var slot = 0; slot < size; slot++) {
      if (!fullSuit(inventory.getStackInSlot(slot))) continue
      // No deferred evidence/cache: membership and prerequisites are checked at the write.
      if (file.getTeamData(player).orElse(null) !== team || !file.isPlayerOnTeam(player, team)
          || !team.canStartTasks(task.getQuest()) || team.isCompleted(task)) return
      team.setProgress(task, 1)
      return
    }
  }
  PlayerEvents.tick(function(event) {
    if (bridge.owner !== owner) return
    var player = event.getEntity()
    if (player.tickCount % 20 !== 0) return
    try { observe(player) } catch (error) {
      if (!owner.warned) {
        owner.warned = true
        console.error('[Lunar quests] Oxygen observation failed; no completion: ' + String(error))
      }
    }
  })
  ServerEvents.unloaded(function() { if (bridge.owner === owner) bridge.owner = null })
})()
