package fr.ascendant.quarryguard;

import com.yogpc.qp.machine.Area;
import com.yogpc.qp.machine.marker.QuarryMarker;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Laboratory-only helper checks; no world writes and no command registration. */
public final class MarkerChecks {
    private static final String NORMAL = "com.yogpc.qp.machine.marker.NormalMarkerEntity$Link";
    private static final String FLEXIBLE = "com.yogpc.qp.machine.marker.FlexibleMarkerEntity$FlexibleMarkerLink";
    private static final String CHUNK = "com.yogpc.qp.machine.marker.ChunkMarkerEntity$Link";

    private MarkerChecks() { }

    /** Tests footprint extraction only, not GuardHooks placement authorization. */
    public static String run(MinecraftServer server) throws Exception {
        LabSupport.requireLab(server);
        Area area = new Area(2, 64, 2, 12, 68, 12, Direction.NORTH);
        List<BlockPos> none = MarkerFootprint.removedPositions(new QuarryMarker.StaticLink(area));
        LabSupport.check(none.isEmpty(), "StaticLink unexpectedly removes blocks");
        immutable(none);

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(16, 64, 8);
        BlockPos distant = new BlockPos(-20_000_000, 68, 20_000_000);
        List<BlockPos> positions = new ArrayList<>();
        positions.add(mutable);
        positions.add(distant);
        positions.add(new BlockPos(16, 64, 8));
        QuarryMarker.Link normal = link(NORMAL, new Class<?>[]{List.class}, positions);
        List<BlockPos> normalSnapshot = MarkerFootprint.removedPositions(normal);
        LabSupport.check(normalSnapshot.equals(List.of(new BlockPos(16, 64, 8), distant)),
            "Normal marker footprint must preserve only exact distinct positions");
        immutable(normalSnapshot);
        mutable.set(32, 64, 8);
        positions.add(new BlockPos(48, 64, 8));
        LabSupport.check(normalSnapshot.equals(List.of(new BlockPos(16, 64, 8), distant)),
            "Normal marker snapshot retained mutable native data");
        LabSupport.check(!normalSnapshot.equals(MarkerFootprint.removedPositions(normal)),
            "Re-reading a changed normal link must expose its new footprint");

        Class<?>[] flexibleTypes = {BlockPos.class, BlockPos.class, BlockPos.class, Direction.class, ItemStack.class};
        BlockPos.MutableBlockPos markerPos = new BlockPos.MutableBlockPos(16, 64, 8);
        QuarryMarker.Link flexible = link(FLEXIBLE, flexibleTypes, markerPos,
            new BlockPos(2, 64, 2), new BlockPos(12, 68, 12), Direction.NORTH, ItemStack.EMPTY);
        List<BlockPos> flexibleSnapshot = MarkerFootprint.removedPositions(flexible);
        LabSupport.check(flexibleSnapshot.equals(List.of(new BlockPos(16, 64, 8))),
            "Flexible marker removal is its physical position, not its selected area");
        immutable(flexibleSnapshot);
        markerPos.set(17, 64, 8);
        LabSupport.check(flexibleSnapshot.equals(List.of(new BlockPos(16, 64, 8))),
            "Flexible marker snapshot retained a mutable BlockPos");
        LabSupport.check(!flexibleSnapshot.equals(MarkerFootprint.removedPositions(flexible)),
            "Re-reading a moved flexible link must expose its new footprint");

        Class<?>[] chunkTypes = {BlockPos.class, int.class, int.class, int.class, int.class, ItemStack.class};
        QuarryMarker.Link chunk = link(CHUNK, chunkTypes, new BlockPos(-1, 64, 15),
            100_000, -100_000, -64, 320, ItemStack.EMPTY);
        List<BlockPos> chunkSnapshot = MarkerFootprint.removedPositions(chunk);
        LabSupport.check(chunkSnapshot.equals(List.of(new BlockPos(-1, 64, 15))),
            "Chunk marker offsets must not expand its removal footprint");
        immutable(chunkSnapshot);

        rejected(() -> MarkerFootprint.removedPositions(null), "null link");
        QuarryMarker.Link unknown = new QuarryMarker.Link() {
            @Override public Area area() { throw new AssertionError("Unknown link area evaluated"); }
            @Override public List<ItemStack> drops() { throw new AssertionError("Unknown link drops evaluated"); }
            @Override public void remove(Level level) { throw new AssertionError("Unknown link removed blocks"); }
        };
        rejected(() -> MarkerFootprint.removedPositions(unknown), "unknown link");
        QuarryMarker.Link emptyNormal = link(NORMAL, new Class<?>[]{List.class}, List.of());
        rejected(() -> MarkerFootprint.removedPositions(emptyNormal), "empty normal footprint");
        List<BlockPos> nullPositions = new ArrayList<>();
        nullPositions.add(null);
        QuarryMarker.Link nullNormal = link(NORMAL, new Class<?>[]{List.class}, nullPositions);
        rejected(() -> MarkerFootprint.removedPositions(nullNormal), "null normal position");
        QuarryMarker.Link wrongNormal = link(NORMAL, new Class<?>[]{List.class}, List.of("not a BlockPos"));
        rejected(() -> MarkerFootprint.removedPositions(wrongNormal), "non-position normal component");
        QuarryMarker.Link nullFlexible = link(FLEXIBLE, flexibleTypes, null,
            new BlockPos(2, 64, 2), new BlockPos(12, 68, 12), Direction.NORTH, ItemStack.EMPTY);
        rejected(() -> MarkerFootprint.removedPositions(nullFlexible), "null flexible position");
        QuarryMarker.Link nullChunk = link(CHUNK, chunkTypes, null, 0, 0, 64, 68, ItemStack.EMPTY);
        rejected(() -> MarkerFootprint.removedPositions(nullChunk), "null chunk position");

        return "marker helper PASS: four native link types, exact sparse positions, immutable snapshots, "
            + "changed-link re-read, unknown/malformed refusal; no placement authorization tested";
    }

    private static QuarryMarker.Link link(String name, Class<?>[] parameterTypes, Object... values) throws Exception {
        Class<?> type = Class.forName(name, true, QuarryMarker.Link.class.getClassLoader());
        Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
        LabSupport.check(constructor.trySetAccessible(), "Native marker constructor unavailable: " + name);
        return (QuarryMarker.Link) constructor.newInstance(values);
    }

    private static void immutable(List<BlockPos> positions) {
        try {
            positions.add(BlockPos.ZERO);
            throw new IllegalStateException("Marker footprint list is mutable");
        } catch (UnsupportedOperationException expected) { }
    }

    private static void rejected(Runnable operation, String description) {
        try {
            operation.run();
            throw new IllegalStateException("Marker footprint accepted " + description);
        } catch (IllegalArgumentException expected) { }
    }
}
