package fr.ascendant.lunar.encounter;

import java.io.IOException;
import java.util.*;
import java.util.function.*;

/** Shared event/authorization/ownership policies, independent of the native runtime. */
public final class EncounterSafety {
    public static final int MAX_DIMENSIONS = 64;
    private EncounterSafety() {}

    public static boolean contain(Runnable action, Consumer<RuntimeException> close) {
        try { action.run(); return true; }
        catch (RuntimeException failure) {
            try { close.accept(failure); }
            catch (RuntimeException cleanupFailure) {
                if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
            }
            return false;
        }
    }

    public static RaidMachine.Result damageAuthorized(RaidMachine raid, UUID player,
            RaidMachine.Token token, double amount, BooleanSupplier authorized) {
        if (!authorized.getAsBoolean()) { raid.actorLost(); return RaidMachine.Result.IGNORED; }
        return raid.damage(player, token, amount);
    }

    public static void requireAuthorization(BooleanSupplier authorized) {
        if (!authorized.getAsBoolean()) throw new SecurityException("Arena authorization/terrain revoked");
    }

    public static void beginAuthorized(WithdrawalJournal journal, UUID run, UUID delegate,
            UUID operation, BooleanSupplier authorized) throws IOException {
        requireAuthorization(authorized);
        journal.begin(run, delegate, operation);
    }

    public static <W> List<W> dimensions(Iterable<W> worlds) {
        List<W> result = new ArrayList<>();
        for (W world : worlds) {
            if (result.size() == MAX_DIMENSIONS) throw new IllegalStateException("Dimension cleanup bound exceeded");
            result.add(world);
        }
        return List.copyOf(result);
    }

    public static boolean matchingOwner(UUID expectedRun, UUID marker) {
        if (expectedRun == null) return false;
        if (!expectedRun.equals(marker)) throw new IllegalStateException("Owned actor marker mismatch; manual review required");
        return true;
    }

    public static boolean recoverJoin(UUID expectedRun, UUID marker, boolean homeDimension, boolean active) {
        return matchingOwner(expectedRun, marker) && !(homeDimension && active);
    }

    /** A marker is a reason to suspend admission, never proof authorizing deletion. */
    public static void recoverEntityJoin(boolean marked, UUID marker, boolean active, boolean homeDimension,
            boolean readable, Supplier<UUID> lookupOwner, Runnable cancel, Runnable discard) {
        if (!readable) {
            if (marked || active) cancel.run();
            return;
        }
        UUID expectedRun = null;
        try {
            expectedRun = lookupOwner.get();
            if (expectedRun == null) {
                if (marked || active) throw new IllegalStateException("Encounter actor has no durable owner; admission suspended");
                return;
            }
            if (recoverJoin(expectedRun, marker, homeDimension, active)) {
                cancel.run();
                discard.run();
            }
        } catch (RuntimeException failure) {
            if (marked || active || expectedRun != null) cancel.run();
            throw failure;
        }
    }

    /** Exact UUID lookups only, including all loaded dimensions; continue after one bad actor copy. */
    public static <W,E> void discardExact(UUID actor, UUID run, Iterable<W> worlds,
            BiFunction<W,UUID,E> lookup, Function<E,UUID> marker, Consumer<E> discard) {
        RuntimeException failure = null;
        for (W world : dimensions(worlds)) {
            try {
                E entity = lookup.apply(world, actor);
                if (entity != null && matchingOwner(run, marker.apply(entity))) discard.accept(entity);
            } catch (RuntimeException ex) {
                if (failure == null) failure = ex;
                else if (failure != ex) failure.addSuppressed(ex);
            }
        }
        if (failure != null) throw failure;
    }
}
