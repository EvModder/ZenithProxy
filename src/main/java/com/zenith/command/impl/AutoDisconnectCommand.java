package com.zenith.command.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.module.impl.AutoDisconnect;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class AutoDisconnectCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("autoDisconnect")
            .category(CommandCategory.MODULE)
            .description("""
            Configures the AutoDisconnect module.
            
            Every mode and setting requires the module to be enabled to be active.
            
            Modes:
            
              * Health: Disconnects when health is at or below a set threshold level.
                Non-priority health logouts pause this check until recovery or fresh damage.
                Use `health on` to rearm it manually.
              * Thunder: Disconnects during thunderstorms (i.e. avoid lightning burning down bases)
              * Unknown Player: Disconnects when a player not on the friends list, whitelist, or spectator whitelist is in visual range
              * TotemPop: Disconnects when your totem is popped
            Multiple modes can be enabled, they are non-exclusive
            
            Settings non-exclusive to modes:
              * WhilePlayerConnected: If AutoDisconnect should disconnect while a player is controlling the proxy account
              * AutoClientDisconnect: Disconnects when the controlling player disconnects
              * CancelAutoReconnect: Cancels AutoReconnect when AutoDisconnect is triggered. If the proxy account has prio this is ignored and AutoReconnect is always cancelled
            """)
            .usageLines(
                "on/off",
                "health on/off",
                "health <integer>",
                "thunder on/off",
                "unknownPlayer on/off",
                "totemPop on/off",
                "totemPop minTotemsRemaining <count>",
                "whilePlayerConnected on/off",
                "autoClientDisconnect on/off",
                "cancelAutoReconnect on/off"
            )
            .aliases(
                "autoLog"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autoDisconnect")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.utility.actions.autoDisconnect.enabled = getToggle(c, "toggle");
                MODULE.get(AutoDisconnect.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("AutoDisconnect " + toggleStrCaps(CONFIG.client.extra.utility.actions.autoDisconnect.enabled));
            }))
            .then(literal("health")
                .then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.utility.actions.autoDisconnect.healthDisconnect = getToggle(c, "toggle");
                    CONFIG.client.extra.utility.actions.autoDisconnect.healthDisconnectPaused = false;
                    c.getSource().getEmbed()
                        .title("Health Disconnect " + toggleStrCaps(CONFIG.client.extra.utility.actions.autoDisconnect.healthDisconnect));
                }))
                .then(argument("healthLevel", integer(1, 19)).executes(c -> {
                    CONFIG.client.extra.utility.actions.autoDisconnect.health = IntegerArgumentType.getInteger(c, "healthLevel");
                    c.getSource().getEmbed()
                        .title("AutoDisconnect Health Level Updated!");
                })))
            .then(literal("cancelAutoReconnect")
                .then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.utility.actions.autoDisconnect.cancelAutoReconnect = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                        .title("AutoDisconnect Cancel AutoReconnect " + toggleStrCaps(CONFIG.client.extra.utility.actions.autoDisconnect.cancelAutoReconnect));
                })))
            .then(literal("autoClientDisconnect")
                .then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.utility.actions.autoDisconnect.autoClientDisconnect = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                        .title("AutoDisconnect Auto Client Disconnect " + toggleStrCaps(CONFIG.client.extra.utility.actions.autoDisconnect.autoClientDisconnect));
                })))
            .then(literal("thunder")
                .then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.utility.actions.autoDisconnect.thunder = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                        .title("AutoDisconnect Thunder " + toggleStrCaps(CONFIG.client.extra.utility.actions.autoDisconnect.thunder));
                })))
            .then(literal("unknownPlayer")
                .then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.utility.actions.autoDisconnect.onUnknownPlayerInVisualRange = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                        .title("AutoDisconnect Unknown Player " + toggleStrCaps(CONFIG.client.extra.utility.actions.autoDisconnect.onUnknownPlayerInVisualRange));
                })))
            .then(literal("whilePlayerConnected")
                .then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.utility.actions.autoDisconnect.whilePlayerConnected = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                        .title("AutoDisconnect While Player Connected " + toggleStrCaps(CONFIG.client.extra.utility.actions.autoDisconnect.whilePlayerConnected));
                })))
            .then(literal("totemPop")
                .then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.utility.actions.autoDisconnect.onTotemPop = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                        .title("AutoDisconnect Totem Pop " + toggleStrCaps(CONFIG.client.extra.utility.actions.autoDisconnect.onTotemPop));
                }))
                .then(literal("minTotemsRemaining").then(argument("count", integer(1)).executes(c -> {
                    CONFIG.client.extra.utility.actions.autoDisconnect.minTotemsRemaining = getInteger(c, "count");
                    c.getSource().getEmbed()
                        .title("Min Totems Remaining Set");
                }))));
    }

    @Override
    public void defaultEmbed(final Embed builder) {
        builder
            .addField("AutoDisconnect", toggleStr(CONFIG.client.extra.utility.actions.autoDisconnect.enabled))
            .addField("Health Disconnect", toggleStr(CONFIG.client.extra.utility.actions.autoDisconnect.healthDisconnect))
            .addField("Health Check Paused", toggleStr(CONFIG.client.extra.utility.actions.autoDisconnect.healthDisconnectPaused))
            .addField("Health Level", CONFIG.client.extra.utility.actions.autoDisconnect.health)
            .addField("Thunder", toggleStr(CONFIG.client.extra.utility.actions.autoDisconnect.thunder))
            .addField("Unknown Player", toggleStr(CONFIG.client.extra.utility.actions.autoDisconnect.onUnknownPlayerInVisualRange))
            .addField("Totem Pop", toggleStr(CONFIG.client.extra.utility.actions.autoDisconnect.onTotemPop))
            .addField("Min Totems Remaining", CONFIG.client.extra.utility.actions.autoDisconnect.minTotemsRemaining)
            .addField("While Player Connected", toggleStr(CONFIG.client.extra.utility.actions.autoDisconnect.whilePlayerConnected))
            .addField("Auto Client Disconnect", toggleStr(CONFIG.client.extra.utility.actions.autoDisconnect.autoClientDisconnect))
            .addField("Cancel AutoReconnect", toggleStr(CONFIG.client.extra.utility.actions.autoDisconnect.cancelAutoReconnect))
            .primaryColor();
    }
}
