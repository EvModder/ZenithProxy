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
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.jspecify.annotations.NonNull;

@Data
public class Vec2Argument implements SerializableArgumentType<Coordinates> {
    public static final SimpleCommandExceptionType ERROR_NOT_COMPLETE = new SimpleCommandExceptionType(
        new LiteralMessage(ComponentSerializer.serializePlain(Component.translatable("argument.pos2d.incomplete"))));
    private final boolean centerCorrect;

    public static Vec2Argument vec2() {
        return new Vec2Argument(true);
    }

    public static Vec2Argument vec2(boolean centerCorrect) {
        return new Vec2Argument(centerCorrect);
    }

    public static Vector2d getVec2(CommandContext<com.zenith.command.api.CommandContext> context, String name) {
        Vector3d vec3 = context.getArgument(name, Coordinates.class).getPosition(context.getSource());
        return Vector2d.from(vec3.getX(), vec3.getZ());
    }

    public Coordinates parse(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();
        if (!reader.canRead()) {
            throw ERROR_NOT_COMPLETE.createWithContext(reader);
        } else {
            WorldCoordinate worldCoordinate = WorldCoordinate.parseDouble(reader, this.centerCorrect);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                WorldCoordinate worldCoordinate2 = WorldCoordinate.parseDouble(reader, this.centerCorrect);
                return new WorldCoordinates(worldCoordinate, new WorldCoordinate(true, 0.0), worldCoordinate2);
            } else {
                reader.setCursor(i);
                throw ERROR_NOT_COMPLETE.createWithContext(reader);
            }
        }
    }

    @Override
    public @NonNull CommandParser commandParser() {
        return CommandParser.VEC2;
    }
}
