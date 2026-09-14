package local.lunar;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import local.lunar.mixin.CreateBogeyAccessor;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class TrackGuards {
    private TrackGuards() {}
    // Scoped to the synchronous native connectToPortal attempt. HEAD resets even after an exception.
    private static final ThreadLocal<Boolean> rejectedExit = new ThreadLocal<>();

    public static void beginAttempt() { rejectedExit.remove(); }
    public static void rejectExit() { rejectedExit.set(true); }
    public static boolean consumeRejectedExit() {
        boolean rejected = Boolean.TRUE.equals(rejectedExit.get());
        rejectedExit.remove();
        return rejected;
    }

    public static boolean crossing(ResourceKey<Level> first, ResourceKey<Level> second) {
        return Rules.crossing(LunarExtraRestrictions.enabledOnServerThread(), id(first), id(second));
    }

    public static boolean crossing(TrackNodeLocation first, TrackNodeLocation second) {
        return crossing(first == null ? null : first.dimension, second == null ? null : second.dimension);
    }

    private static String id(ResourceKey<Level> key) { return key == null ? null : key.location().toString(); }

    public static boolean straddling(Train train) {
        if (!LunarExtraRestrictions.enabledOnServerThread() || train == null) return false;
        Set<String> dimensions = new HashSet<>();
        for (Carriage carriage : train.carriages) collect(carriage, dimensions);
        return Rules.lunarSpan(dimensions);
    }

    public static boolean straddling(Carriage carriage) {
        if (!LunarExtraRestrictions.enabledOnServerThread()) return false;
        if (carriage.train != null) return straddling(carriage.train);
        Set<String> dimensions = new HashSet<>();
        collect(carriage, dimensions);
        return Rules.lunarSpan(dimensions);
    }

    private static void collect(Carriage carriage, Set<String> dimensions) {
        carriage.getPresentDimensions().forEach(key -> dimensions.add(id(key)));
        carriage.bogeys.forEach(bogey -> {
            if (bogey != null) {
                ((CreateBogeyAccessor) bogey).lunar$points().forEach(point -> collect(point, dimensions));
            }
        });
    }

    private static void collect(TravellingPoint point, Set<String> dimensions) {
        if (point.node1 != null) dimensions.add(id(point.node1.getLocation().dimension));
        if (point.node2 != null) dimensions.add(id(point.node2.getLocation().dimension));
    }
}
