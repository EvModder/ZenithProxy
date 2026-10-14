package com.zenith.network.client.handler.incoming;

import com.zenith.event.client.ClientDeathEvent;
import com.zenith.feature.player.World;
import com.zenith.util.ComponentSerializer;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerCombatKillPacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.EVENT_BUS;
import static com.zenith.command.impl.StatusCommand.getCoordinates;

public class PlayerCombatKillHandler implements ClientEventLoopPacketHandler<ClientboundPlayerCombatKillPacket, ClientSession> {

    @Override
    public boolean applyAsync(ClientboundPlayerCombatKillPacket packet, ClientSession session) {
        if (packet.getPlayerId() == CACHE.getPlayerCache().getEntityId()) {
            session.getPearlTeleportTracker().died();
            EVENT_BUS.postAsync(new ClientDeathEvent(
                getCoordinates(CACHE.getPlayerCache().getThePlayer()),
                World.getCurrentDimension().name(),
                ComponentSerializer.serializePlain(packet.getMessage())));
        }
        return true;
    }
}
