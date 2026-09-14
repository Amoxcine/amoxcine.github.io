package fr.ascendant.quarryguard.mixin.quarry;

import com.yogpc.qp.machine.Area;
import com.yogpc.qp.machine.advquarry.AdvActionSyncMessage;
import com.yogpc.qp.machine.advquarry.AdvQuarryEntity;
import fr.ascendant.quarryguard.GuardHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AdvActionSyncMessage.class, remap = false)
public abstract class AdvActionSyncMessageMixin {
    @Shadow @Final private BlockPos pos;
    @Shadow @Final private ResourceKey<Level> dim;
    @Shadow @Final private Area area;
    @Shadow @Final private boolean syncArea;

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
            return;
        }
        String state = machine.toClientTag(new CompoundTag(), level.registryAccess()).getString("state");
        // workConfig also affects target traversal, even when syncArea is false.
        if ((!state.equals("FINISHED") && !state.equals("WAITING"))
            || !GuardHooks.maySetArea(machine, syncArea ? area : machine.getArea())) {
            ci.cancel();
        }
    }
}
