package com.zenith.discord;

import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Joins the death packet with the separate death chat sent by servers such as 2b2t. */
final class DeathNotifications {
    private static final long WINDOW_NANOS = 2_000_000_000L;
    private final Consumer<Embed> send;
    private final LongSupplier clock;
    private Embed pending;
    private String earlyMessage;
    private long earlyMessageTime;

    DeathNotifications(Consumer<Embed> send, LongSupplier clock) {
        this.send = send;
        this.clock = clock;
    }

    synchronized void death(Embed embed) {
        if (pending != null) flush(pending);
        pending = embed;
        if (earlyMessage != null && clock.getAsLong() - earlyMessageTime <= WINDOW_NANOS) {
            pending.fields().removeIf(field -> field.name().equals("Message"));
            pending.addField("Message", earlyMessage, false);
        }
        earlyMessage = null;
    }

    synchronized void message(String message) {
        if (pending != null) {
            pending.fields().removeIf(field -> field.name().equals("Message"));
            pending.addField("Message", message, false);
        } else {
            earlyMessage = message;
            earlyMessageTime = clock.getAsLong();
        }
    }

    synchronized void flush(Embed expected) {
        if (pending == null || pending != expected) return;
        pending = null;
        send.accept(expected);
    }
}
