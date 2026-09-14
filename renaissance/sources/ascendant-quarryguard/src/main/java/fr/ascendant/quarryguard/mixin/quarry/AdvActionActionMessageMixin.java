package fr.ascendant.quarryguard.mixin.quarry;

import com.yogpc.qp.machine.advquarry.AdvActionActionMessage;
import com.yogpc.qp.machine.advquarry.AdvQuarryEntity;
import fr.ascendant.quarryguard.GuardHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AdvActionActionMessage.class, remap = false)
public abstract class AdvActionActionMessageMixin {
    @Shadow @Final private BlockPos pos;
    @Shadow @Final private ResourceKey<Level> dim;

    @Inject(method = "onReceive(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;)V",
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void quarryguard$authorizePacket(Level level, Player player, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        if (!(player instanceof ServerPlayer sender) || sender.level() != level
            || !level.dimension().equals(dim) || pos == null || !level.hasChunkAt(pos)) {
            ci.cancel();
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof AdvQuarryEntity machine) || !machine.enabled
            || !GuardHooks.mayConfigure(sender, machine)) {
            ci.cancel();
        }
    }

    @Inject(method = "onReceive(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;)V",
        at = @At(value = "FIELD",
            target = "Lcom/yogpc/qp/machine/advquarry/AdvQuarryEntity;workConfig:Lcom/yogpc/qp/machine/advquarry/WorkConfig;",
            opcode = Opcodes.PUTFIELD, remap = false), cancellable = true, remap = false, require = 1)
    private void quarryguard$authorizeQuickStart(Level level, Player player, CallbackInfo ci) {
        // The only workConfig write is QUICK_START, before startQuarryWork().
        if (!level.isClientSide() && (!(level.getBlockEntity(pos) instanceof AdvQuarryEntity machine)
            || !GuardHooks.mayWork(machine))) {
            ci.cancel();
        }
    }
}
