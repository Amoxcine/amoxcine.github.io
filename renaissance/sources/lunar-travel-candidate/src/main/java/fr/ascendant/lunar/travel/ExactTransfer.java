package fr.ascendant.lunar.travel;

/** One synchronous native changeDimension event, never a player-wide teleport permit. */
public final class ExactTransfer {
    private final Object server, player, from, to;
    private final Thread thread = Thread.currentThread();
    private boolean spent;

    public ExactTransfer(Object server, Object player, Object from, Object to) {
        this.server = java.util.Objects.requireNonNull(server);
        this.player = java.util.Objects.requireNonNull(player);
        this.from = java.util.Objects.requireNonNull(from);
        this.to = java.util.Objects.requireNonNull(to);
    }

    public boolean consume(Object server, Object player, Object from, Object to) {
        if (spent || Thread.currentThread() != thread || this.server != server || this.player != player
                || this.from != from || this.to != to) return false;
        spent = true;
        return true;
    }
}
