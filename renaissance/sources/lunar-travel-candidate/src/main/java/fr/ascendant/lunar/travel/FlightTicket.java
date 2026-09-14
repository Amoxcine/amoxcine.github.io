package fr.ascendant.lunar.travel;

public final class FlightTicket {
    private final Object server, player, rocket;
    private final String source;
    private final long started;
    private boolean spent;
    private boolean firstArrivalReserved;

    public FlightTicket(Object server, Object player, Object rocket, String source, long started) {
        this.server = java.util.Objects.requireNonNull(server);
        this.player = java.util.Objects.requireNonNull(player);
        this.rocket = java.util.Objects.requireNonNull(rocket);
        this.source = java.util.Objects.requireNonNull(source);
        this.started = started;
    }

    public boolean matches(Object server, Object player, Object rocket, String source, String target, long now) {
        return !spent && this.server == server && this.player == player && this.rocket == rocket
            && this.source.equals(source) && now >= started && now - started <= 12000
            && TravelPolicy.rocketRoute(source, target);
    }

    public void consume() { spent = true; }
    void reserveFirstArrival() { firstArrivalReserved = true; }
    void releaseFirstArrival() { firstArrivalReserved = false; }
    boolean firstArrivalReserved() { return firstArrivalReserved && !spent; }
}
