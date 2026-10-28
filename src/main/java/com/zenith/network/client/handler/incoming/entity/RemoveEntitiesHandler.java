package com.zenith.network.client.handler.incoming.entity;

import com.zenith.cache.data.entity.Entity;
import com.zenith.cache.data.entity.EntityPlayer;
import com.zenith.event.module.ServerPlayerLeftVisualRangeEvent;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.jspecify.annotations.NonNull;

import static com.zenith.Globals.*;

public class RemoveEntitiesHandler implements ClientEventLoopPacketHandler<ClientboundRemoveEntitiesPacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundRemoveEntitiesPacket packet, @NonNull ClientSession session) {
        var entityIds = packet.getEntityIds();
        for (int i = 0; i < entityIds.length; i++) {
            final int id = entityIds[i];
            try {
                Entity removed = CACHE.getEntityCache().remove(id);
                if (removed != null) {
                    for (int passenger : removed.getPassengerIds()) {
                        final Entity passengerEntity = CACHE.getEntityCache().get(passenger);
                        if (passengerEntity != null) {
                            passengerEntity.dismountVehicle();
                        }
                    }
                    if (removed instanceof EntityPlayer player && !player.isSelfPlayer()) {
                        EVENT_BUS.postAsync(new ServerPlayerLeftVisualRangeEvent(
                            CACHE.getTabListCache()
                                .get(player.getUuid())
                                // todo: this packet seems to always be received first and we shouldn't hit the orElse, but this could change based on the server
                                .orElse(new PlayerListEntry("", player.getUuid())),
                            player,
                            session
                        ));
                    }
                }
            } catch (final Exception e) {
                CLIENT_LOG.debug("Error removing entity with ID: {}", id, e);
            }
        }
        return true;
    }
}
