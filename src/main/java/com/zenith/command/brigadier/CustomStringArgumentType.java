package com.zenith.command.brigadier;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.Data;
import lombok.Getter;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.CommandProperties;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.StringProperties;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

/**
 * Extra string argument types not included by default
 */
@Data
public class CustomStringArgumentType implements SerializableArgumentType<String> {

    private final CustomStringArgumentType.StringType type;

    private CustomStringArgumentType(final CustomStringArgumentType.StringType type) {
        this.type = type;
    }

    public static CustomStringArgumentType wordWithChars() {
        return new CustomStringArgumentType(StringType.CHAR_WORD);
    }

    public static String getString(final CommandContext<?> context, final String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(final StringReader reader) throws CommandSyntaxException {
        if (type == StringType.CHAR_WORD) {
            return readStringUntil(' ', reader);
        } else {
            return reader.readString();
        }
    }

    public String readStringUntil(char terminator, final StringReader reader) {
        final StringBuilder result = new StringBuilder();
        while (reader.canRead()) {
            final char c = reader.peek();
            if (c == terminator) break;
            result.append(reader.read());
        }
        return result.toString();
    }

    @Override
    public Collection<String> getExamples() {
        return type.getExamples();
    }

    @Override
    public @NonNull CommandParser commandParser() {
        return CommandParser.STRING;
    }

    @Override
    public @Nullable CommandProperties commandProperties() {
        return StringProperties.SINGLE_WORD;
    }

    @Getter
    public enum StringType {
        CHAR_WORD("any character not a space");

        private final Collection<String> examples;

        StringType(final String... examples) {
            this.examples = Arrays.asList(examples);
        }
    }
}
