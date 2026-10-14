package com.zenith.event.client;

import com.zenith.feature.player.PearlTeleportTracker.Position;

public record PearlTeleportEvent(Position from, Position to) { }
