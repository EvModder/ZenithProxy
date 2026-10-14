package com.zenith.discord;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class DeathNotificationsTest {
    @Test void joinsBothEventOrdersAndSendsOnlyOnce() {
        for (boolean chatFirst : new boolean[]{false, true}) {
            var sent = new ArrayList<Embed>();
            var deaths = new DeathNotifications(sent::add, () -> 0);
            var embed = Embed.builder().title("Player Death").addField("Coordinates", "hidden");
            if (chatFirst) deaths.message("Player was slain");
            deaths.death(embed);
            if (!chatFirst) deaths.message("Player was slain");
            assertTrue(sent.isEmpty());
            deaths.flush(embed);
            deaths.flush(embed);
            assertEquals(1, sent.size());
            assertEquals("Player was slain", sent.getFirst().fields().getLast().value());
            assertEquals("hidden", sent.getFirst().fields().getFirst().value());
        }
    }

    @Test void staleChatCannotAttachToNewDeath() {
        var time = new AtomicLong();
        var sent = new ArrayList<Embed>();
        var deaths = new DeathNotifications(sent::add, time::get);
        deaths.message("Old death");
        time.set(3_000_000_000L);
        var embed = Embed.builder().title("Player Death");
        deaths.death(embed);
        deaths.flush(embed);
        assertTrue(sent.getFirst().fields().isEmpty());
    }

    @Test void consecutiveDeathsAndOldTimersStaySeparate() {
        var sent = new ArrayList<Embed>();
        var deaths = new DeathNotifications(sent::add, () -> 0);
        var first = Embed.builder().title("First");
        var second = Embed.builder().title("Second");
        deaths.death(first);
        deaths.message("First death");
        deaths.death(second);
        deaths.flush(first);
        deaths.message("Second death");
        deaths.flush(second);
        assertEquals(2, sent.size());
        assertEquals("First death", first.fields().getFirst().value());
        assertEquals("Second death", second.fields().getFirst().value());
    }

    @Test void customChatReplacesPacketMessage() {
        var deaths = new DeathNotifications(e -> {}, () -> 0);
        var embed = Embed.builder().addField("Message", "Vanilla");
        deaths.message("Custom");
        deaths.death(embed);
        assertEquals(1, embed.fields().size());
        assertEquals("Custom", embed.fields().getFirst().value());
    }
}
