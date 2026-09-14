package fr.ascendant.quarryguard.mixin.quarry;

import com.yogpc.qp.machine.advquarry.AdvQuarryEntity;
import com.yogpc.qp.machine.misc.QuarryChunkLoader;
import com.yogpc.qp.machine.quarry.QuarryEntity;
import fr.ascendant.quarryguard.GuardHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = QuarryChunkLoader.Load.class, remap = false)
public abstract class QuarryChunkLoaderLoadMixin {
    @Shadow @Final private BlockPos pos;

    @Inject(method = "makeChunkLoaded(Lnet/minecraft/server/level/ServerLevel;)V",
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void quarryguard$beforeForce(ServerLevel level, CallbackInfo ci) {
        if (pos == null || !level.hasChunkAt(pos)) {
            ci.cancel();
            return;
        }
        BlockEntity machine = level.getBlockEntity(pos);
        if (machine == null || ((machine instanceof QuarryEntity || machine instanceof AdvQuarryEntity)
            && !GuardHooks.mayWork(machine))) {
            ci.cancel();
        }
    }
}
