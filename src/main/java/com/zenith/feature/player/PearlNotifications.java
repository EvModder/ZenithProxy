package com.zenith.feature.player;

import com.zenith.Proxy;
import com.zenith.event.client.PearlTeleportEvent;
import com.zenith.network.client.ClientSession;
import static com.zenith.Globals.*;

public final class PearlNotifications {
    private PearlNotifications() { }

    public static boolean allowed(ClientSession session) {
        var config = CONFIG.client.extra.pearlNotifications;
        return config.enabled && session.isOnline() && Proxy.getInstance().getOnlineTimeSeconds() >= 5
            && (config.whileClientConnected || (!Proxy.getInstance().hasActivePlayer()
                && Proxy.getInstance().getSpectatorConnections().stream().noneMatch(s -> s.isLoggedIn())));
    }

    public static void publish(PearlTeleportEvent event, ClientSession session) {
        if (event != null && allowed(session)) EVENT_BUS.postAsync(event);
    }
}
