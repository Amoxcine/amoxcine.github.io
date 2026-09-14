package fr.ascendant.lunar.encounter;

import java.util.*;

/** Expiring command budget only; never applied to combat or earned withdrawals. */
public final class AdmissionBudget {
    public static final int ATTEMPTS = 8, WINDOW_TICKS = 200, MAX_CALLERS = 64;
    private record Attempt(long window, int count) {}
    private final Map<UUID,Attempt> attempts = new HashMap<>();

    public boolean attempt(UUID player, long tick) {
        attempts.entrySet().removeIf(e -> tick < e.getValue().window || tick - e.getValue().window >= WINDOW_TICKS);
        Attempt prior = attempts.get(player);
        if (prior == null) {
            if (attempts.size() >= MAX_CALLERS) return false;
            attempts.put(player, new Attempt(tick, 1)); return true;
        }
        if (prior.count >= ATTEMPTS) return false;
        attempts.put(player, new Attempt(prior.window, prior.count + 1)); return true;
    }

}
