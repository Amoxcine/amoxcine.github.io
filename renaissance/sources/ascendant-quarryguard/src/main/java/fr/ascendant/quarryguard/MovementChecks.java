package fr.ascendant.quarryguard;

import com.yogpc.qp.machine.PowerEntity;
import com.direwolf20.buildinggadgets2.common.items.GadgetCutPaste;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import java.util.ArrayList;
import java.util.List;
import mekanism.common.item.block.ItemBlockCardboardBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Exact Cardboard Box capture path; BG2 remains a separate deferred-queue test. */
public final class MovementChecks {
    private static final String OWNER = "ascendant_quarryguard_owner";

    private MovementChecks() { }

    public static String run(MinecraftServer server) throws Exception {
        LabSupport.requireLab(server);
        var level = server.overworld();
        var claims = (ClaimedChunkManagerImpl) FTBChunksAPI.api().getManager();
        var teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        List<String> evidence = new ArrayList<>();
        var registered = BuiltInRegistries.ITEM.get(ResourceLocation.parse("mekanism:cardboard_box"));
        LabSupport.check(registered instanceof ItemBlockCardboardBox, "Mekanism Cardboard Box item unavailable");
        ItemBlockCardboardBox cardboard = (ItemBlockCardboardBox) registered;

        for (int variant = 0; variant < 2; variant++) {
            String id = variant == 0 ? "quarry" : "adv_quarry";
            BlockPos pos = new BlockPos(7204 + 64 * variant, 65, 7215);
            ChunkDimPos claimPos = new ChunkDimPos(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
            LabSupport.check(level.getBlockState(pos).isAir() && level.getBlockEntity(pos) == null
                    && level.getBlockState(pos.below()).isAir() && claims.getChunk(claimPos) == null,
                    "movement fixture is not empty");
            var owner = LabSupport.actor(server, teams, "QGMoveOwner");
            var enemy = LabSupport.actor(server, teams, "QGMoveEnemy");
            var ownerData = claims.getOrCreateData(teams.getPlayerTeamForPlayerID(owner.getUUID()).orElseThrow());
            ownerData.setExtraClaimChunks(2);
            ownerData.updateLimits();
            ownerData.getTeam().setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PRIVATE);
            boolean ownsClaim = false;
            try {
                BlockEntity placed = LabSupport.place(level, owner, pos, id);
                PowerEntity machine = (PowerEntity) placed;
                machine.setEnergy(12_345, false);
                CompoundTag original = machine.saveWithFullMetadata(level.registryAccess());
                LabSupport.check(original.hasUUID(OWNER) && original.getUUID(OWNER).equals(owner.getUUID()), "owner fixture missing");
                LabSupport.check(ownerData.claim(server.createCommandSourceStack().withSuppressedOutput(), claimPos, false).isSuccess(),
                        "owner claim failed");
                ownsClaim = true;

                ItemStack enemyBox = new ItemStack(cardboard);
                InteractionResult denied = capture(cardboard, enemyBox, enemy, pos);
                LabSupport.check(denied == InteractionResult.FAIL && enemyBox.getCount() == 1,
                        "hostile Cardboard Box capture was not refused before consumption");
                LabSupport.check(level.getBlockEntity(pos) == machine
                        && original.equals(machine.saveWithFullMetadata(level.registryAccess())),
                        "hostile capture changed quarry or NBT");
                LabSupport.check(!GadgetCutPaste.customCutValidation(machine.getBlockState(), level, owner, pos),
                        "relocation tag did not refuse Building Gadgets cut validation");

                ItemStack ownerBox = new ItemStack(cardboard);
                InteractionResult ownerDenied = capture(cardboard, ownerBox, owner, pos);
                LabSupport.check(ownerDenied == InteractionResult.FAIL && ownerBox.getCount() == 1,
                        "relocation tag did not refuse owner Cardboard Box capture");
                LabSupport.check(level.getBlockEntity(pos) == machine
                        && original.equals(machine.saveWithFullMetadata(level.registryAccess())),
                        "owner capture refusal changed quarry or NBT");
                evidence.add(id + ": hostile capture refused by FTB; owner Cardboard/BG2 cut refused by relocation tag; NBT unchanged");
            } finally {
                if (ownsClaim) {
                    var current = claims.getChunk(claimPos);
                    LabSupport.check(current != null && current.getTeamData().getTeamId().equals(ownerData.getTeamId()),
                            "refusing to remove a changed movement claim");
                    LabSupport.check(ownerData.unclaim(server.createCommandSourceStack().withSuppressedOutput(), claimPos, false, true).isSuccess(),
                            "owner unclaim failed");
                }
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
                GuardHooks.afterPlace();
            }
        }
        return "native Cardboard Box checks: " + String.join("; ", evidence)
                + ". BG2 scheduled-cut validator exercised directly; no queue mutation ran because validation refuses the target.";
    }

    private static InteractionResult capture(ItemBlockCardboardBox item, ItemStack stack,
                                              net.minecraft.server.level.ServerPlayer actor, BlockPos pos) {
        actor.setShiftKeyDown(false);
        actor.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return item.onItemUseFirst(stack, new UseOnContext(actor, InteractionHand.MAIN_HAND, hit));
    }
}
