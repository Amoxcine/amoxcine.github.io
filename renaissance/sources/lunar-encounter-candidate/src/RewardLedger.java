package fr.ascendant.lunar.encounter;

import java.util.*;

/** Logical escrow/outbox. Persist every mutation BEFORE performing its external effect. */
public final class RewardLedger {
    public enum Status { RESERVED, AVAILABLE, CLAIMING, CLAIMED, REFUNDABLE, REFUNDING, REFUNDED, REVIEW }
    public record Lot(String component, int count) {}
    public record Entry(UUID run, RaidMachine.Identity identity, RaidMachine.Group group,
                        int reservedCoolant, Status status, UUID operation, UUID claimant) {
        public Entry {
            Objects.requireNonNull(run); Objects.requireNonNull(identity); Objects.requireNonNull(group);
            Objects.requireNonNull(status);
            if (reservedCoolant < 0 || reservedCoolant > 1_000_000) throw new IllegalArgumentException("Invalid escrow");
            boolean needsOperation = status == Status.CLAIMING || status == Status.CLAIMED
                    || status == Status.REFUNDING || status == Status.REFUNDED || status == Status.REVIEW;
            if (needsOperation != (operation != null && claimant != null)) throw new IllegalArgumentException("Invalid operation");
            if (!needsOperation && (operation != null || claimant != null)) throw new IllegalArgumentException("Unexpected operation");
            if (claimant != null && !group.delegates().contains(claimant)) throw new IllegalArgumentException("Unknown claimant");
        }
        public Lot lot() { return switch (identity) {
            case SEALED_GREENHOUSE -> new Lot(LunarConfig.REWARD, LunarConfig.REWARD_COUNT);
            case REGULATOR -> new Lot("core", 4);
            case CONSERVATOR -> new Lot("matrix", 1);
        }; }
    }
    private final int capacity;
    private final Map<UUID, Entry> entries = new HashMap<>();
    public RewardLedger(int capacity) {
        if (capacity < 1 || capacity > 1_000_000) throw new IllegalArgumentException("Invalid capacity");
        this.capacity = capacity;
    }
    public void reserve(RaidMachine raid, int coolant) {
        RaidMachine.View v = raid.view();
        if (v.phase() != RaidMachine.Phase.READING || v.activeTicks() != 0 || v.generation() != 1)
            throw new IllegalStateException("Reserve before starting");
        if (entries.containsKey(v.run()) || entries.size() == capacity) throw new IllegalStateException("Run reused or ledger full");
        if (v.identity() == RaidMachine.Identity.REGULATOR && coolant <= 0) throw new IllegalArgumentException("Regulator needs escrow");
        entries.put(v.run(), new Entry(v.run(), v.identity(), v.group(), coolant, Status.RESERVED, null, null));
    }
    public boolean settle(RaidMachine raid) {
        RaidMachine.View v = raid.view();
        Entry e = required(v.run());
        if (!e.group().equals(v.group()) || e.identity() != v.identity()) throw new IllegalArgumentException("Run identity mismatch");
        if (!raid.terminal() || e.status() != Status.RESERVED) return false;
        entries.put(v.run(), change(e, v.phase() == RaidMachine.Phase.SUCCESS ? Status.AVAILABLE : Status.REFUNDABLE, null, null));
        return true;
    }
    /** Returned operation is an intent, never permission to retry an ambiguous physical insertion. */
    public Entry beginWithdrawal(UUID run, UUID delegate, UUID operation) {
        Objects.requireNonNull(operation); Objects.requireNonNull(delegate);
        Entry e = required(run);
        if (!e.group().delegates().contains(delegate)) throw new SecurityException("Not a frozen delegate");
        if (e.status() != Status.AVAILABLE && e.status() != Status.REFUNDABLE) throw new IllegalStateException("Not withdrawable");
        Entry next = change(e, e.status() == Status.AVAILABLE ? Status.CLAIMING : Status.REFUNDING, operation, delegate);
        entries.put(run, next); return next;
    }
    public void confirmWithdrawal(UUID run, UUID operation) {
        Entry e = required(run);
        if (!Objects.equals(e.operation(), operation) || (e.status() != Status.CLAIMING && e.status() != Status.REFUNDING))
            throw new IllegalStateException("No matching pending transfer");
        entries.put(run, change(e, e.status() == Status.CLAIMING ? Status.CLAIMED : Status.REFUNDED, operation, e.claimant()));
    }
    public Entry get(UUID run) { return required(run); }
    public List<Entry> snapshot() { return entries.values().stream().sorted(Comparator.comparing(e -> e.run().toString())).toList(); }
    public static RewardLedger forEntry(Entry entry) {
        RewardLedger ledger = new RewardLedger(1);
        ledger.entries.put(entry.run(), entry);
        return ledger;
    }

    /** Startup aborts pending runs; uncertain physical withdrawals are quarantined, never reissued. */
    public static RewardLedger recover(int capacity, List<Entry> persisted) {
        RewardLedger ledger = new RewardLedger(capacity);
        if (persisted.size() > capacity) throw new IllegalArgumentException("Too many persisted runs");
        for (Entry e : persisted) {
            Objects.requireNonNull(e);
            Status status = switch (e.status()) {
                case RESERVED -> Status.REFUNDABLE;
                case CLAIMING, REFUNDING -> Status.REVIEW;
                default -> e.status();
            };
            if (ledger.entries.putIfAbsent(e.run(), change(e, status, e.operation(), e.claimant())) != null)
                throw new IllegalArgumentException("Duplicate persisted run");
        }
        return ledger;
    }
    private Entry required(UUID run) {
        Entry e = entries.get(Objects.requireNonNull(run));
        if (e == null) throw new IllegalArgumentException("Unregistered run");
        return e;
    }
    private static Entry change(Entry e, Status status, UUID operation, UUID claimant) {
        return new Entry(e.run(), e.identity(), e.group(), e.reservedCoolant(), status, operation, claimant);
    }
}
