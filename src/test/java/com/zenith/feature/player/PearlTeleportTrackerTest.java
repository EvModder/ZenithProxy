package com.zenith.feature.player;

import com.zenith.feature.player.PearlTeleportTracker.Position;
import com.zenith.util.config.Config;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class PearlTeleportTrackerTest {
    private final Position base = new Position("overworld", 10000, 64, 10000);
    private final Position far = new Position("overworld", 30000, 64, 30000);
    private final AtomicLong clock = new AtomicLong();

    private PearlTeleportTracker initialized() {
        var tracker = new PearlTeleportTracker(clock::get);
        assertNull(tracker.teleport(base, base, false, 256, true));
        return tracker;
    }

    @Test void defaultsAreUnattendedOnly() {
        var config = new Config.Client.Extra.PearlNotifications();
        assertTrue(config.enabled);
        assertFalse(config.whileClientConnected);
        assertEquals(256, config.minDistance);
    }

    @Test void correlatesBothOrdersOnce() {
        for (boolean damageFirst : new boolean[]{false, true}) {
            var tracker = initialized();
            if (damageFirst) assertNull(tracker.pearlDamage());
            var event = tracker.teleport(base, far, false, 256, true);
            if (!damageFirst) {
                assertNull(event);
                event = tracker.pearlDamage();
            }
            assertNotNull(event);
            assertEquals(base, event.from());
            assertEquals(far, event.to());
            assertNull(tracker.pearlDamage());
        }
    }

    @Test void noDamageMeansNoAlertIncludingPortalsAndServerTeleports() {
        var tracker = initialized();
        for (String dimension : new String[]{"overworld", "the_nether", "the_end"}) {
            assertNull(tracker.teleport(base, new Position(dimension, 30000, 64, 30000), false, 256, true));
        }
    }

    @Test void rejectsStaleEvidenceInBothOrders() {
        var tracker = initialized();
        tracker.pearlDamage();
        clock.addAndGet(1_000_000_001L);
        assertNull(tracker.teleport(base, far, false, 256, true));
        clock.addAndGet(1_000_000_001L);
        assertNull(tracker.pearlDamage());
    }

    @Test void shortLoadedOrSuppressedMovesConsumeDamage() {
        for (int mode = 0; mode < 3; mode++) {
            var tracker = initialized();
            tracker.pearlDamage();
            assertNull(tracker.teleport(base, mode == 0 ? base : far, mode == 1, 256, mode != 2));
            assertNull(tracker.teleport(base, far, false, 256, true));
        }
    }

    @Test void loginDeathAndResetClearEvidence() {
        var tracker = new PearlTeleportTracker(clock::get);
        tracker.pearlDamage();
        assertNull(tracker.teleport(base, far, false, 256, true));
        tracker.pearlDamage();
        tracker.died();
        tracker.respawn(base, true);
        assertNull(tracker.teleport(base, far, false, 256, true));
        tracker.pearlDamage();
        tracker.reset();
        assertNull(tracker.teleport(base, far, false, 256, true));
    }

    @Test void crossDimensionPearlsKeepOriginalLocation() {
        var tracker = initialized();
        tracker.respawn(base, true);
        tracker.respawn(new Position("the_nether", 0, 64, 0), true);
        tracker.pearlDamage();
        var event = tracker.teleport(new Position("the_end", 0, 64, 0),
            new Position("the_end", base.x(), base.y(), base.z()), true, 256, true);
        assertNotNull(event);
        assertEquals(base, event.from());
        assertEquals("the_end", event.to().dimension());
    }

    @Test void ignoresInvalidPositionsAndHonorsDistance() {
        assertFalse(PearlTeleportTracker.qualifies(base, new Position("overworld", Double.NaN, 64, 0), false, 256));
        for (int offset : new int[]{8, 16, 64, 128, 256}) {
            assertFalse(PearlTeleportTracker.qualifies(base, new Position("overworld", 10000 + offset, 64, 10000), false, 256));
        }
        assertFalse(PearlTeleportTracker.qualifies(base, new Position("overworld", 10400, 64, 10000), false, 512));
    }
}
