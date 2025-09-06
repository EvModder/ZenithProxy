package com.zenith.command.brigadier;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.zenith.command.api.CommandContext;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.jspecify.annotations.NonNull;

public class ResourceLocationArgument implements SerializableArgumentType<Key> {
    public static final SimpleCommandExceptionType INVALID_RESOURCE_LOCATION = new SimpleCommandExceptionType(
        new LiteralMessage("Invalid resource location"));

    public static ResourceLocationArgument id() {
        return new ResourceLocationArgument();
    }

    public static Key getId(final com.mojang.brigadier.context.CommandContext<CommandContext> context, String name) {
        return context.getArgument(name, Key.class);
    }

    @Override
    public Key parse(final StringReader reader) throws CommandSyntaxException {
        return read(reader);
    }

    public static Key read(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();
        while (reader.canRead()
            && (
                Key.allowedInNamespace(reader.peek())
                || Key.allowedInValue(reader.peek())
                || reader.peek() == ':'
            )) {
            reader.skip();
        }
        String s = reader.getString().substring(i, reader.getCursor());
        try {
            return Key.key(s);
        } catch (Exception e) {
            reader.setCursor(i);
            throw INVALID_RESOURCE_LOCATION.create();
        }
    }

    @Override
    public @NonNull CommandParser commandParser() {
        return CommandParser.RESOURCE_LOCATION;
    }
}
