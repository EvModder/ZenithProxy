package com.zenith.network.client.handler.incoming.entity;

import com.zenith.cache.data.entity.Entity;
import com.zenith.cache.data.entity.EntityPlayer;
import com.zenith.event.module.ServerPlayerAttackedUsEvent;
import com.zenith.feature.player.PearlNotifications;
import com.zenith.mc.damage_type.DamageTypeRegistry;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundDamageEventPacket;

import static com.zenith.Globals.*;

public class DamageEventHandler implements ClientEventLoopPacketHandler<ClientboundDamageEventPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundDamageEventPacket packet, final ClientSession session) {
        var pearlDamage = DamageTypeRegistry.REGISTRY.get("ender_pearl");
        if (pearlDamage != null && packet.getSourceTypeId() == pearlDamage.id()
            && packet.getEntityId() == CACHE.getPlayerCache().getEntityId()) {
            PearlNotifications.publish(session.getPearlTeleportTracker().pearlDamage(), session);
        }
        var playerAttackDamageType = DamageTypeRegistry.REGISTRY.get("player_attack");
        if (playerAttackDamageType != null && packet.getSourceTypeId() == playerAttackDamageType.id()) {
            if (packet.getEntityId() == CACHE.getPlayerCache().getEntityId()) {
                Entity attacker = CACHE.getEntityCache().get(packet.getSourceCauseId());
                if (attacker instanceof EntityPlayer attackerPlayer && attackerPlayer.getEntityId() != CACHE.getPlayerCache().getEntityId()) {
                    EVENT_BUS.postAsync(new ServerPlayerAttackedUsEvent(attackerPlayer, packet.getSourcePosition()));
                    CLIENT_LOG.debug("Player {} attacked us", attackerPlayer.getEntityId());
                }
            }
        }
        return true;
    }
}
