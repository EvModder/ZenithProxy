package com.zenith.command.brigadier;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zenith.command.api.CommandContext;
import com.zenith.mc.dimension.DimensionData;
import com.zenith.mc.dimension.DimensionRegistry;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public class DimensionArgument implements SerializableArgumentType<DimensionData> {
    public static final SimpleCommandExceptionType INVALID_DIMENSION_EXCEPTION = new SimpleCommandExceptionType(
        new LiteralMessage("Invalid dimension")
    );

    public static DimensionArgument dimension() {
        return new DimensionArgument();
    }

    public static DimensionData getDimension(final com.mojang.brigadier.context.CommandContext<CommandContext> context, String name) {
        return context.getArgument(name, DimensionData.class);
    }

    @Override
    public DimensionData parse(final StringReader stringReader) throws CommandSyntaxException {
        final String dimensionString = readDimensionString(stringReader);
        DimensionData dimensionData = DimensionRegistry.REGISTRY.get(dimensionString);
        if (dimensionData == null) {
            throw INVALID_DIMENSION_EXCEPTION.createWithContext(stringReader);
        }
        return dimensionData;
    }

    private String readDimensionString(StringReader reader) throws CommandSyntaxException {
        final int start = reader.getCursor();
        while (reader.canRead() && isAllowedInDimensionKeyString(reader.peek())) {
            reader.skip();
        }
        if (reader.getCursor() == start) {
            reader.setCursor(start);
            throw INVALID_DIMENSION_EXCEPTION.createWithContext(reader);
        }
        var dimensionString = reader.getString().substring(start, reader.getCursor());
        // cut off the namespace if it exists
        if (dimensionString.contains(":")) {
            dimensionString = dimensionString.substring(dimensionString.indexOf(":") + 1);
        }
        return dimensionString;
    }

    private static boolean isAllowedInDimensionKeyString(final char c) {
        return c >= 'A' && c <= 'Z'
            || c >= 'a' && c <= 'z'
            || c == '_' || c == ':';
    }

    @Override
    public @NonNull CommandParser commandParser() {
        return CommandParser.DIMENSION;
    }

    @Override
    public CompletableFuture<Suggestions> listSuggestions(final com.mojang.brigadier.context.CommandContext context, final SuggestionsBuilder builder) {
        return RegistryDataArgument.listRegistrySuggestions(context, builder, DimensionRegistry.REGISTRY.getLoadedRegistry());
    }
}
