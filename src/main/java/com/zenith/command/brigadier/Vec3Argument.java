package com.zenith.command.brigadier;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.zenith.util.ComponentSerializer;
import lombok.Data;
import net.kyori.adventure.text.Component;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.jspecify.annotations.NonNull;

@Data
public class Vec3Argument implements SerializableArgumentType<Coordinates> {
    public static final SimpleCommandExceptionType ERROR_NOT_COMPLETE = new SimpleCommandExceptionType(
        new LiteralMessage(ComponentSerializer.serializePlain(Component.translatable("argument.pos3d.incomplete"))));
    public static final SimpleCommandExceptionType ERROR_MIXED_TYPE = new SimpleCommandExceptionType(
        new LiteralMessage(ComponentSerializer.serializePlain(Component.translatable("argument.pos.mixed"))));
    private final boolean centerCorrect;

    public static Vec3Argument vec3() {
        return new Vec3Argument(true);
    }

    public static Vec3Argument vec3(boolean centerCorrect) {
        return new Vec3Argument(centerCorrect);
    }

    public static Vector3d getVec3(CommandContext<com.zenith.command.api.CommandContext> context, String name) {
        return context.getArgument(name, Coordinates.class).getPosition(context.getSource());
    }

    public static Coordinates getCoordinates(CommandContext<com.zenith.command.api.CommandContext> context, String name) {
        return context.getArgument(name, Coordinates.class);
    }

    public Coordinates parse(StringReader reader) throws CommandSyntaxException {
        return reader.canRead() && reader.peek() == '^' ? LocalCoordinates.parse(reader) : WorldCoordinates.parseDouble(reader, this.centerCorrect);
    }

    @Override
    public @NonNull CommandParser commandParser() {
        return CommandParser.VEC3;
    }
}
