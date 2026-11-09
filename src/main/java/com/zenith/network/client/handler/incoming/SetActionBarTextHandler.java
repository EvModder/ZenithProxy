package com.zenith.network.client.handler.incoming;

import com.zenith.Proxy;
import com.zenith.event.chat.ActionBarTextEvent;
import com.zenith.event.server.ServerRestartingEvent;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import com.zenith.util.ComponentSerializer;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetActionBarTextPacket;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static com.zenith.Globals.CLIENT_LOG;
import static com.zenith.Globals.EVENT_BUS;

public class SetActionBarTextHandler implements ClientEventLoopPacketHandler<ClientboundSetActionBarTextPacket, ClientSession> {
    private Instant lastRestartEvent = Instant.EPOCH;

    @Override
    public boolean applyAsync(final ClientboundSetActionBarTextPacket packet, final ClientSession session) {
        EVENT_BUS.postAsync(new ActionBarTextEvent(ComponentSerializer.serializePlain(packet.getText())));
        if (Proxy.getInstance().isOn2b2t()) parse2bRestart(packet, session);
        return true;
    }

    private void parse2bRestart(ClientboundSetActionBarTextPacket serverTitlePacket, final ClientSession session) {
        try {
            Optional.of(serverTitlePacket)
                .map(title -> ComponentSerializer.serializePlain(title.getText()))
                .filter(text -> text.toLowerCase().contains("restart"))
                .ifPresent(text -> {
                    if (lastRestartEvent.isBefore(Instant.now().minus(1, ChronoUnit.MINUTES))) {
                        lastRestartEvent = Instant.now();
                        EVENT_BUS.postAsync(new ServerRestartingEvent(text));
                    }
                });
        } catch (final Exception e) {
            CLIENT_LOG.warn("Error parsing restart message from title packet", e);
        }
    }
}
