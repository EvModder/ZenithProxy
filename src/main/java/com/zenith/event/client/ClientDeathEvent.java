package com.zenith.event.client;

public record ClientDeathEvent(String coordinates, String dimension, String message) {
    public ClientDeathEvent() {
        this(null, null, null);
    }
}
