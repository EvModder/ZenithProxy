package com.zenith.command.brigadier;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.zenith.feature.player.World;
import com.zenith.mc.block.BlockPos;
import com.zenith.util.ComponentSerializer;
import lombok.Data;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.jspecify.annotations.NonNull;

@Data
public class BlockPosArgument implements SerializableArgumentType<Coordinates> {

    public static final SimpleCommandExceptionType ERROR_NOT_LOADED = new SimpleCommandExceptionType(
        new LiteralMessage(ComponentSerializer.serializePlain(Component.translatable("argument.pos.unloaded"))));
    public static final SimpleCommandExceptionType ERROR_OUT_OF_WORLD = new SimpleCommandExceptionType(
        new LiteralMessage(ComponentSerializer.serializePlain(Component.translatable("argument.pos.outofworld"))));
    public static final SimpleCommandExceptionType ERROR_OUT_OF_BOUNDS = new SimpleCommandExceptionType(
        new LiteralMessage(ComponentSerializer.serializePlain(Component.translatable("argument.pos.outofbounds"))));

    public static BlockPosArgument blockPos() {
        return new BlockPosArgument();
    }

    public static BlockPos getLoadedBlockPos(CommandContext<com.zenith.command.api.CommandContext> context, String name) throws CommandSyntaxException {
        BlockPos blockPos = getBlockPos(context, name);
        if (!World.isChunkLoadedBlockPos(blockPos.x(), blockPos.z())) {
            throw ERROR_NOT_LOADED.create();
        } else if (!World.isInWorldBounds(blockPos.x(), blockPos.y(), blockPos.z())) {
            throw ERROR_OUT_OF_WORLD.create();
        } else {
            return blockPos;
        }
    }

    public static BlockPos getBlockPos(CommandContext<com.zenith.command.api.CommandContext> context, String name) {
        return context.getArgument(name, Coordinates.class).getBlockPos(context.getSource());
    }

    public Coordinates parse(StringReader reader) throws CommandSyntaxException {
        return reader.canRead() && reader.peek() == '^' ? LocalCoordinates.parse(reader) : WorldCoordinates.parseInt(reader);
    }

    @Override
    public @NonNull CommandParser commandParser() {
        return CommandParser.BLOCK_POS;
    }
}
