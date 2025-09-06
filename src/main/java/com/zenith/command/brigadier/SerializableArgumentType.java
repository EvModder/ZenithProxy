package com.zenith.command.brigadier;

import com.mojang.brigadier.arguments.ArgumentType;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.CommandProperties;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface SerializableArgumentType<T> extends ArgumentType<T> {
    @NonNull CommandParser commandParser();

    default @Nullable CommandProperties commandProperties() {
        return null;
    }

    /**
     * Whether ingame players should query the zenith mc server for command suggestions
     * i.e. for non-vanilla commands without built-in completions
     */
    default boolean askServerForSuggestions() {
        return false;
    }
}
