package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.*;
import com.zenith.discord.Embed;

import static com.zenith.Globals.CONFIG;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.zenith.command.brigadier.ToggleArgumentType.*;

public class PearlNotificationsCommand extends Command {
    @Override public CommandUsage commandUsage() {
        return CommandUsage.builder().name("pearlNotifications").category(CommandCategory.MODULE)
            .description("""
                Notify about long-distance or cross-dimension teleports accompanied by our own ender-pearl damage within one second.
                Ignores login, death respawn, short/loaded-range moves, and teleports without pearl-damage evidence.
                No alert is sent if the server suppresses pearl damage.
                Within a dimension, the distance threshold is at least 256 blocks and the server view distance.
                Coordinates follow the Discord reportCoords setting. Controller and spectator connections suppress alerts by default.
                """)
            .usageLines("on/off", "whileClientConnected on/off", "minDistance <blocks>").build();
    }

    @Override public LiteralArgumentBuilder<CommandContext> register() {
        return command("pearlNotifications").requires(Command::validateAccountOwner)
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.pearlNotifications.enabled = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Pearl Notifications " + toggleStrCaps(CONFIG.client.extra.pearlNotifications.enabled));
            }))
            .then(literal("whileClientConnected").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.pearlNotifications.whileClientConnected = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Pearl Notifications While Client Connected "
                    + toggleStrCaps(CONFIG.client.extra.pearlNotifications.whileClientConnected));
            })))
            .then(literal("minDistance").then(argument("blocks", integer(256)).executes(c -> {
                CONFIG.client.extra.pearlNotifications.minDistance = getInteger(c, "blocks");
                c.getSource().getEmbed().title("Pearl Notification Minimum Distance Updated");
            })));
    }

    @Override public void defaultEmbed(Embed embed) {
        var config = CONFIG.client.extra.pearlNotifications;
        embed.addField("Enabled", toggleStr(config.enabled))
            .addField("While Client Connected", toggleStr(config.whileClientConnected))
            .addField("Minimum Distance", config.minDistance).primaryColor();
    }
}
