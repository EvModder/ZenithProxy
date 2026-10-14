package com.zenith.network.client.handler.incoming;

import com.zenith.Proxy;
import com.zenith.cache.data.PlayerCache;
import com.zenith.feature.player.PearlTeleportTracker.Position;
import com.zenith.feature.player.World;
import com.zenith.feature.player.PearlNotifications;
import com.zenith.feature.spectator.SpectatorSync;
import com.zenith.module.impl.AntiAFK;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.jspecify.annotations.NonNull;

import static com.zenith.Globals.*;
import static java.util.Objects.isNull;

public class PlayerPositionHandler implements ClientEventLoopPacketHandler<ClientboundPlayerPositionPacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundPlayerPositionPacket packet, @NonNull ClientSession session) {
        PlayerCache cache = CACHE.getPlayerCache();
        var from = new Position(World.getCurrentDimension().name(), cache.getX(), cache.getY(), cache.getZ());
        var teleportQueue = cache.getTeleportQueue();
        cache.getTeleportQueue().add(packet);
        while (teleportQueue.size() > 100) {
            var tp = teleportQueue.poll();
            CLIENT_LOG.debug("Teleport queue larger than 100, dropping oldest entry. Dropped teleport: {} Last teleport: {}", tp.getId(), packet.getId());
        }
        cache
            .setRespawning(false)
            .setX((packet.getRelatives().contains(PositionElement.X) ? cache.getX() : 0.0d) + packet.getX())
            .setY((packet.getRelatives().contains(PositionElement.Y) ? cache.getY() : 0.0d) + packet.getY())
            .setZ((packet.getRelatives().contains(PositionElement.Z) ? cache.getZ() : 0.0d) + packet.getZ())
            .setVelX((packet.getRelatives().contains(PositionElement.DELTA_X) ? cache.getVelX() : 0.0d) + packet.getDeltaX())
            .setVelY((packet.getRelatives().contains(PositionElement.DELTA_Y) ? cache.getVelY() : 0.0d) + packet.getDeltaY())
            .setVelZ((packet.getRelatives().contains(PositionElement.DELTA_Z) ? cache.getVelZ() : 0.0d) + packet.getDeltaZ())
            .setYaw((packet.getRelatives().contains(PositionElement.Y_ROT) ? cache.getYaw() : 0.0f) + packet.getYaw())
            .setPitch((packet.getRelatives().contains(PositionElement.X_ROT) ? cache.getPitch() : 0.0f) + packet.getPitch());
        ServerSession currentPlayer = Proxy.getInstance().getCurrentPlayer().get();
        var to = new Position(World.getCurrentDimension().name(), cache.getX(), cache.getY(), cache.getZ());
        var settings = CONFIG.client.extra.pearlNotifications;
        var event = session.getPearlTeleportTracker().teleport(from, to,
            World.isChunkLoadedBlockPos((int) Math.floor(to.x()), (int) Math.floor(to.z())),
            Math.max(settings.minDistance, CACHE.getChunkCache().getServerViewDistance() * 16.0), PearlNotifications.allowed(session));
        PearlNotifications.publish(event, session);
        if (isNull(currentPlayer) || !currentPlayer.isLoggedIn()) {
            BOT.handlePlayerPosRotate(packet.getId());
        } else {
            CLIENT_LOG.debug("Passing teleport {} through to current player", packet.getId());
        }
        BARITONE.onPlayerPosRotate();
        SpectatorSync.syncPlayerPositionWithSpectators();
        MODULE.get(AntiAFK.class).handlePlayerPosRotate();
        return true;
    }
}
