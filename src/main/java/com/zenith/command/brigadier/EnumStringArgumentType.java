package com.zenith.command.brigadier;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import lombok.Getter;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.CommandProperties;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.StringProperties;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

@Getter
public class EnumStringArgumentType implements SerializableArgumentType<String> {
    private final String[] values;

    public EnumStringArgumentType(final String[] values) {
        this.values = values;
    }

    public static EnumStringArgumentType enumStrings(String... strings) {
        return new EnumStringArgumentType(strings);
    }

    public static EnumStringArgumentType enumStrings(Collection<String> strings) {
        return enumStrings(strings.toArray(new String[0]));
    }

    public static EnumStringArgumentType enumStrings(Enum<?>[] enumValues) {
        String[] names = new String[enumValues.length];
        for (int i = 0; i < enumValues.length; i++) {
            names[i] = enumValues[i].name().toLowerCase();
        }
        return enumStrings(names);
    }

    public static String getEnumString(final CommandContext<com.zenith.command.api.CommandContext> context, final String name) throws CommandSyntaxException {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(final StringReader reader) throws CommandSyntaxException {
        final String value = reader.readUnquotedString();
        for (final String val : values) {
            if (val.equalsIgnoreCase(value)) {
                return val;
            }
        }
        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect().createWithContext(reader, value);
    }

    @Override
    public CompletableFuture<Suggestions> listSuggestions(final CommandContext context, final SuggestionsBuilder builder) {
        for (final String val : values) {
            if (val.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(val);
            }
        }
        return builder.buildFuture();
    }

    @Override
    public @NonNull CommandParser commandParser() {
        return CommandParser.STRING;
    }

    @Override
    public @Nullable CommandProperties commandProperties() {
        return StringProperties.SINGLE_WORD;
    }

    @Override
    public boolean askServerForSuggestions() {
        return true;
    }
}
