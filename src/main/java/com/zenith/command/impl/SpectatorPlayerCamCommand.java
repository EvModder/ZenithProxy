package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.cache.data.entity.Entity;
import com.zenith.command.api.*;
import com.zenith.feature.spectator.SpectatorSync;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSetCameraPacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.util.ComponentSerializer.minimessage;

public class SpectatorPlayerCamCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("playerCam")
            .category(CommandCategory.MANAGE)
            .description("Toggles spectators between player and entity cameras. Only usable by spectators")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("playerCam").requires(c -> Command.validateCommandSource(c, CommandSources.SPECTATOR)).executes(c -> {
            var session = c.getSource().getInGamePlayerInfo().session();
            final Entity existingTarget = session.getCameraTarget();
            if (existingTarget != null) {
                session.setCameraTarget(null);
                session.sendAsync(new ClientboundSetCameraPacket(session.getSpectatorSelfEntityId()));
                SpectatorSync.syncSpectatorPositionToEntity(session, existingTarget);
                session.sendAsyncMessage(minimessage("<blue>Exited playercam!"));
            } else {
                session.setCameraTarget(CACHE.getPlayerCache().getThePlayer());
                session.sendAsync(new ClientboundSetCameraPacket(CACHE.getPlayerCache().getEntityId()));
                var connections = Proxy.getInstance().getActiveConnections().getArray();
                for (int i = 0; i < connections.length; i++) {
                    var connection = connections[i];
                    connection.sendAsync(session.getEntityRemovalPacket());
                }
                session.sendAsyncMessage(minimessage("<blue>Entered playercam!"));
            }
            c.getSource().setNoOutput(true);
        });
    }
}
