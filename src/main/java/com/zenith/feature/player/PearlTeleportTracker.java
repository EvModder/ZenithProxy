package com.zenith.feature.player;

import com.zenith.event.client.PearlTeleportEvent;
import java.util.function.LongSupplier;

/** Correlates our pearl damage with one nearby teleport, in either packet order. */
public final class PearlTeleportTracker {
    private static final long WINDOW_NANOS = 1_000_000_000L;
    private final LongSupplier clock;
    private PearlTeleportEvent pending;
    private long teleportTime;
    private long damageTime;
    private boolean hasDamage;

    public PearlTeleportTracker() { this(System::nanoTime); }
    PearlTeleportTracker(LongSupplier clock) { this.clock = clock; }
    public record Position(String dimension, double x, double y, double z) {
        public boolean finite() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
        }
    }

    private boolean positioned;
    private boolean dead;
    private Position beforeRespawn;

    public void reset() {
        positioned = false;
        dead = false;
        beforeRespawn = null;
        clearCorrelation();
    }

    private void clearCorrelation() {
        pending = null;
        hasDamage = false;
    }

    public void died() { dead = true; clearCorrelation(); }

    public PearlTeleportEvent pearlDamage() {
        if (dead) return null;
        long now = clock.getAsLong();
        if (pending != null && now - teleportTime <= WINDOW_NANOS) {
            var result = pending;
            clearCorrelation();
            return result;
        }
        pending = null;
        damageTime = now;
        hasDamage = true;
        return null;
    }

    public void respawn(Position from, boolean alive) {
        if (!alive) died();
        // Preserve the original location across servers' two-packet dimension changes.
        if (beforeRespawn == null) beforeRespawn = from;
    }

    public PearlTeleportEvent teleport(Position from, Position to, boolean destinationLoaded, double threshold, boolean notifyAllowed) {
        var origin = beforeRespawn == null ? from : beforeRespawn;
        boolean eligible = positioned && !dead;
        positioned = true;
        dead = false;
        beforeRespawn = null;
        long now = clock.getAsLong();
        boolean matched = hasDamage && now - damageTime <= WINDOW_NANOS;
        clearCorrelation();
        if (!eligible || !notifyAllowed || !qualifies(origin, to, destinationLoaded, threshold)) return null;
        var event = new PearlTeleportEvent(origin, to);
        if (matched) return event;
        pending = event;
        teleportTime = now;
        return null;
    }

    static boolean qualifies(Position from, Position to, boolean destinationLoaded, double threshold) {
        if (!from.finite() || !to.finite()) return false;
        if (!from.dimension.equals(to.dimension)) return true;
        double minimum = Math.max(256, threshold);
        if (destinationLoaded) return false;
        return Math.hypot(Math.hypot(to.x - from.x, to.z - from.z), to.y - from.y) > minimum;
    }
}
