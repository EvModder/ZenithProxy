package com.zenith.discord;

import com.zenith.event.client.ClientDisconnectEvent;
import com.zenith.module.impl.AutoDisconnect;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.zenith.Globals.CONFIG;
import static org.junit.jupiter.api.Assertions.*;

class DisconnectNotificationTest {
    private boolean mentionOnDisconnect;

    @BeforeEach void setup() {
        mentionOnDisconnect = CONFIG.discord.mentionRoleOnDisconnect;
        CONFIG.discord.mentionRoleOnDisconnect = false;
    }

    @AfterEach void restore() {
        CONFIG.discord.mentionRoleOnDisconnect = mentionOnDisconnect;
    }

    private static void assertNotified(String reason, Duration online, boolean inQueue) {
        var listener = new TestListener();
        listener.handleDisconnectEvent(new ClientDisconnectEvent(
            reason, Duration.ofDays(2).plus(online), online, inQueue, inQueue ? 10 : 0));
        assertEquals(1, listener.sent.size());
        var embed = listener.sent.getFirst();
        assertEquals(CONFIG.authentication.username + " disconnected", embed.title());
        assertTrue(embed.fields().stream().anyMatch(f -> f.name().equals("Reason") && f.value().equals(reason)));
    }

    @Test void safetyLogoutsAreReportedImmediately() {
        for (String reason : new String[]{"Health: 1.0 <= 5", "Totem Pop - 1 remaining",
            "Thunder", "Unknown Player: visitor", "Auto Client Disconnect"}) {
            for (Duration online : new Duration[]{Duration.ZERO, Duration.ofSeconds(9), Duration.ofSeconds(179)}) {
                assertNotified(AutoDisconnect.AUTODISCONNECT_REASON_PREFIX + reason, online, false);
            }
        }
    }

    @Test void ordinaryDisconnectsAreReportedRegardlessOfDurationOrQueueState() {
        for (Duration online : new Duration[]{Duration.ZERO, Duration.ofSeconds(9), Duration.ofSeconds(179),
            Duration.ofMinutes(3), Duration.ofMinutes(4)}) {
            for (boolean inQueue : new boolean[]{false, true}) {
                assertNotified("Connection reset by peer", online, inQueue);
            }
        }
    }

    @Test void shortDisconnectsStillUseTheMentionSetting() {
        CONFIG.discord.mentionRoleOnDisconnect = true;
        assertNotified("Connection reset by peer", Duration.ZERO, true);
    }

    private static class TestListener extends NotificationEventListener {
        final List<Embed> sent = new ArrayList<>();
        @Override public void sendEmbedMessage(Embed embed) { sent.add(embed); }
        @Override public void sendEmbedMessage(String message, Embed embed) { sent.add(embed); }
        @Override public void updatePresence() { }
    }
}
