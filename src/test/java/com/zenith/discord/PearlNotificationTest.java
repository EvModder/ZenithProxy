package com.zenith.discord;

import com.zenith.event.client.PearlTeleportEvent;
import com.zenith.feature.player.PearlTeleportTracker.Position;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PearlNotificationTest {
    @Test void roundsDistanceDownToPowerOfTen() {
        var from = new Position("overworld", 0, 0, 0);
        Map.of(256d, "100", 999.9, "100", 1000d, "1,000", 1234d, "1,000",
            9999.9, "1,000", 10000d, "10,000", 1000000d, "1,000,000", 1234567d, "1,000,000")
            .forEach((distance, rounded) -> {
                var event = new PearlTeleportEvent(from, new Position("overworld", distance, 0, 0));
                assertEquals("Teleported >" + rounded + " blocks.",
                    NotificationEventListener.pearlDescription(event));
            });
        assertEquals("Teleported >1,000 blocks.", NotificationEventListener.pearlDescription(
            new PearlTeleportEvent(from, new Position("overworld", 600, 800, 0))));
    }

    @Test void crossDimensionUsesDedicatedMessage() {
        assertEquals("Cross-dimension teleport.", NotificationEventListener.pearlDescription(new PearlTeleportEvent(
            new Position("overworld", 0, 0, 0), new Position("the_nether", 0, 0, 0))));
    }
}
