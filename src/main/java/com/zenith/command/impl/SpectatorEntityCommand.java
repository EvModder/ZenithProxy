package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.api.*;
import com.zenith.feature.spectator.SpectatorEntityRegistry;
import com.zenith.feature.spectator.SpectatorSync;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.zenith.util.ComponentSerializer.minimessage;

public class SpectatorEntityCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("spectatorEntity")
            .category(CommandCategory.MANAGE)
            .description("Changes the current spectator entity. Only usable by spectators")
            .usageLines(
                "",
                "<entity>"
            )
            .aliases("e")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("spectatorEntity").requires(c -> Command.validateCommandSource(c, CommandSources.SPECTATOR))
            .executes(c -> {
                var session = c.getSource().getInGamePlayerInfo().session();
                session.sendAsyncMessage(minimessage("<red>Entity id's: " + String.join(", ", SpectatorEntityRegistry.getEntityIdentifiers())));
                c.getSource().setNoOutput(true);
            })
            .then(argument("entity", enumStrings(SpectatorEntityRegistry.getEntityIdentifiers())).executes(c -> {
                String entityId = getString(c, "entity");
                var session = c.getSource().getInGamePlayerInfo().session();
                boolean spectatorEntitySet = session.setSpectatorEntity(entityId);
                if (spectatorEntitySet) {
                    // respawn entity on all connections
                    var connections = Proxy.getInstance().getActiveConnections().getArray();
                    for (int i = 0; i < connections.length; i++) {
                        var connection = connections[i];
                        connection.sendAsync(new ClientboundRemoveEntitiesPacket(new int[]{session.getSpectatorEntityId()}));
                        if (!connection.equals(session) || session.isShowSelfEntity()) {
                            connection.sendAsync(session.getEntitySpawnPacket());
                            connection.sendAsync(session.getEntityMetadataPacket());
                            SpectatorSync.updateSpectatorPosition(session);
                        }
                    }
                    session.sendAsyncMessage(minimessage("<blue>Updated entity to: " + entityId));
                } else {
                    session.sendAsyncMessage(minimessage("<red>No entity found with id: " + entityId));
                    session.sendAsyncMessage(minimessage("<red>Valid id's: " + String.join(", ", SpectatorEntityRegistry.getEntityIdentifiers())));
                }
                c.getSource().setNoOutput(true);
                return OK;
        }));
    }
}
