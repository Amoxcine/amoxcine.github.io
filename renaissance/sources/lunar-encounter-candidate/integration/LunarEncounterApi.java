package fr.ascendant.lunar.encounter;

import java.util.*;
import java.util.function.BiFunction;
import net.minecraft.server.level.ServerPlayer;

/** Read-only server-thread observation. The live player, roster and current FTB team are authenticated. */
public final class LunarEncounterApi {
    public static final String ADVANCEMENT="ascendant_lunar_encounter:relay_complete";
    public record Completion(String chapter,String site,String encounter,UUID run,UUID team,List<UUID> participants) {
        public Completion { participants=List.copyOf(participants); }
    }
    private static BiFunction<ServerPlayer,UUID,Optional<Completion>> reader=(p,r)->Optional.empty();
    private LunarEncounterApi() {}
    static void bind(BiFunction<ServerPlayer,UUID,Optional<Completion>> value){reader=value;}
    static void close(){reader=(p,r)->Optional.empty();}
    public static Optional<Completion> completion(ServerPlayer player,UUID run){return reader.apply(player,run);}
}
