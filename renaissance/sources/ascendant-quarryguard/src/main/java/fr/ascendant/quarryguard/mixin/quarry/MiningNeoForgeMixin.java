package fr.ascendant.quarryguard.mixin.quarry;

import com.yogpc.qp.machine.QpEntity;
import com.yogpc.qp.machine.QuarryFakePlayerCommon;
import com.yogpc.qp.machine.advquarry.AdvQuarryEntity;
import com.yogpc.qp.machine.quarry.QuarryEntity;
import fr.ascendant.quarryguard.GuardHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.yogpc.qp.neoforge.MiningNeoForge", remap = false)
public abstract class MiningNeoForgeMixin {
    @Inject(method = "getQuarryFakePlayer(Lcom/yogpc/qp/machine/QpEntity;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/server/level/ServerPlayer;",
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void quarryguard$ownerFakePlayer(QpEntity machine, ServerLevel level, BlockPos target,
                                             CallbackInfoReturnable<ServerPlayer> cir) {
        if (machine instanceof QuarryEntity || machine instanceof AdvQuarryEntity) {
            ServerPlayer player = GuardHooks.fakePlayer(machine, level);
            if (player != null) {
                QuarryFakePlayerCommon.setDirection(player, Direction.DOWN);
                cir.setReturnValue(player);
            }
        }
    }
}
