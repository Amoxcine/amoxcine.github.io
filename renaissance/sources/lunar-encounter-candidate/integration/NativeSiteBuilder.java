package fr.ascendant.lunar.encounter;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;

/** Parent-only bootstrap. No terrain clearing, destructive replacement or chunk loading. */
public final class NativeSiteBuilder {
    private NativeSiteBuilder() {}
    public static void allowed(ServerLevel level,BlockPos center) {
        if(!level.getServer().isSameThread()||!level.dimension().location().toString().equals(LunarConfig.DIMENSION)
            ||Math.abs((long)center.getX())>29_999_900||Math.abs((long)center.getZ())>29_999_900
            ||center.getY()<level.getMinBuildHeight()+2||center.getY()>level.getMaxBuildHeight()-10)
            throw new IllegalStateException("Invalid lunar site bounds/thread");
        if(!FTBChunksAPI.api().isManagerLoaded())throw new IllegalStateException("Claims unavailable");
        var claims=FTBChunksAPI.api().getManager();
        for(int x=(center.getX()-32)>>4;x<=(center.getX()+32)>>4;x++)for(int z=(center.getZ()-32)>>4;z<=(center.getZ()+32)>>4;z++) {
            if(!level.hasChunk(x,z))throw new IllegalStateException("Preflight refuses unloaded chunk "+x+","+z+"; visit/load normally first");
            if(claims.getChunk(new ChunkDimPos(level.dimension(),x,z))!=null)throw new IllegalStateException("Preflight refuses claim at chunk "+x+","+z);
        }
        if(!level.getWorldBorder().isWithinBounds(center.offset(-32,0,-32))||!level.getWorldBorder().isWithinBounds(center.offset(32,0,32)))
            throw new IllegalStateException("Site outside world border");
    }
    public static List<SiteBlueprint.Placement> preflight(ServerLevel level,BlockPos center) {
        allowed(level,center);
        return SiteBlueprint.plan((x,y,z)->{
            BlockPos p=center.offset(x,y,z);var state=level.getBlockState(p);
            if(level.getBlockEntity(p)!=null)return SiteBlueprint.Cell.OBSTRUCTION;
            if(state.isAir())return SiteBlueprint.Cell.AIR;
            if(state.is(Blocks.COPPER_BLOCK))return SiteBlueprint.Cell.COPPER;
            return y==-1&&state.isCollisionShapeFullBlock(level,p)?SiteBlueprint.Cell.SOLID_FLOOR:SiteBlueprint.Cell.OBSTRUCTION;
        });
    }
    public static int build(ServerLevel level,BlockPos center) {
        var plan=preflight(level,center);
        allowed(level,center);
        int placed=0;
        for(var placement:plan) {
            var offset=placement.position();var p=center.offset(offset.x(),offset.y(),offset.z());
            if(!level.hasChunk(p.getX()>>4,p.getZ()>>4)||!level.getBlockState(p).isAir()||level.getBlockEntity(p)!=null)
                throw new IllegalStateException("Site changed after preflight at "+p+"; stopped after "+placed+" additive placements, no rollback/deletion");
            if(!level.setBlock(p,block(placement.material()),2))throw new IllegalStateException("Placement refused at "+p+"; previously added blocks retained");
            placed++;
        }
        return placed;
    }
    private static BlockState block(SiteBlueprint.Material material) {
        return switch(material) {
            case BASALT->Blocks.POLISHED_BASALT.defaultBlockState();
            case BORDER->Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
            case LETTER->Blocks.WHITE_CONCRETE.defaultBlockState();
            case CORE->Blocks.CYAN_CONCRETE.defaultBlockState();
            case COPPER->Blocks.COPPER_BLOCK.defaultBlockState();
        };
    }
}
