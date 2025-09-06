package com.zenith.command.brigadier;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.zenith.util.ComponentSerializer;
import lombok.Data;
import net.kyori.adventure.text.Component;
import org.cloudburstmc.math.vector.Vector2d;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.jspecify.annotations.NonNull;

@Data
public class RotationArgument implements SerializableArgumentType<Coordinates> {
    public static final SimpleCommandExceptionType ERROR_NOT_COMPLETE = new SimpleCommandExceptionType(
        new LiteralMessage(ComponentSerializer.serializePlain(Component.translatable("argument.rotation.incomplete"))));

    public static RotationArgument rotation() {
        return new RotationArgument();
    }

    public static Vector2d getRotation(CommandContext<com.zenith.command.api.CommandContext> context, String name) {
        return context.getArgument(name, Coordinates.class).getRotation(context.getSource());
    }

    public Coordinates parse(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();
        if (!reader.canRead()) {
            throw ERROR_NOT_COMPLETE.createWithContext(reader);
        } else {
            WorldCoordinate worldCoordinate = WorldCoordinate.parseDouble(reader, false);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                WorldCoordinate worldCoordinate2 = WorldCoordinate.parseDouble(reader, false);
                return new WorldCoordinates(worldCoordinate2, worldCoordinate, new WorldCoordinate(true, 0.0));
            } else {
                reader.setCursor(i);
                throw ERROR_NOT_COMPLETE.createWithContext(reader);
            }
        }
    }

    @Override
    public @NonNull CommandParser commandParser() {
        return CommandParser.ROTATION;
    }
}
