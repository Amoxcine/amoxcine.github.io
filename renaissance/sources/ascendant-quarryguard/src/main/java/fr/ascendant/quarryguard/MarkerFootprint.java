package fr.ascendant.quarryguard;

import com.yogpc.qp.machine.marker.QuarryMarker;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;

/** Exact removal positions for the four marker links in QuarryPlus 21.1.162. */
public final class MarkerFootprint {
    private static final String NORMAL = "com.yogpc.qp.machine.marker.NormalMarkerEntity$Link";
    private static final String FLEXIBLE = "com.yogpc.qp.machine.marker.FlexibleMarkerEntity$FlexibleMarkerLink";
    private static final String CHUNK = "com.yogpc.qp.machine.marker.ChunkMarkerEntity$Link";

    private MarkerFootprint() { }

    /**
     * Returns an immutable, deduplicated snapshot in native removal order.
     * Only StaticLink returns an empty list. No world, area, drops or removal is evaluated.
     * Recompute and compare this snapshot before consuming a previously approved link;
     * this method neither freezes that link nor grants permission for its positions.
     *
     * @throws IllegalArgumentException for null, unknown, unreadable or malformed links
     */
    public static List<BlockPos> removedPositions(QuarryMarker.Link link) {
        if (link == null) throw new IllegalArgumentException("Missing quarry marker link");
        if (link.getClass() == QuarryMarker.StaticLink.class) return List.of();

        return switch (link.getClass().getName()) {
            case NORMAL -> copyPositions(readAccessor(link, "markerPos", List.class));
            case FLEXIBLE -> List.of(copyPosition(readAccessor(link, "markerPos", BlockPos.class)));
            case CHUNK -> List.of(copyPosition(readAccessor(link, "basePos", BlockPos.class)));
            default -> throw new IllegalArgumentException("Unsupported quarry marker link: " + link.getClass().getName());
        };
    }

    private static Object readAccessor(QuarryMarker.Link link, String name, Class<?> returnType) {
        Class<?> type = link.getClass();
        try {
            if (type != Class.forName(type.getName(), false, QuarryMarker.Link.class.getClassLoader())
                || !type.isRecord()) {
                throw new IllegalArgumentException("Unexpected quarry marker implementation: " + type.getName());
            }
            // The pinned records are not all publicly accessible from this package.
            Method accessor = type.getDeclaredMethod(name);
            if (accessor.getReturnType() != returnType || !accessor.trySetAccessible()) {
                throw new IllegalArgumentException("Inaccessible quarry marker accessor: " + type.getName() + "." + name);
            }
            return accessor.invoke(link);
        } catch (ReflectiveOperationException | SecurityException error) {
            throw new IllegalArgumentException("Cannot read quarry marker footprint: " + type.getName(), error);
        }
    }

    private static List<BlockPos> copyPositions(Object value) {
        if (!(value instanceof List<?> positions) || positions.isEmpty()) {
            throw new IllegalArgumentException("Missing normal marker removal positions");
        }
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        for (Object position : positions) result.add(copyPosition(position));
        return List.copyOf(result);
    }

    private static BlockPos copyPosition(Object value) {
        if (!(value instanceof BlockPos position)) {
            throw new IllegalArgumentException("Invalid quarry marker removal position");
        }
        return new BlockPos(position.getX(), position.getY(), position.getZ());
    }
}
