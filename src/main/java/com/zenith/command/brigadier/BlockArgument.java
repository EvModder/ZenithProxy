package com.zenith.command.brigadier;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zenith.command.api.CommandContext;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockRegistry;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public class BlockArgument implements SerializableArgumentType<Block> {
    public static final SimpleCommandExceptionType BLOCK_NOT_FOUND = new SimpleCommandExceptionType(
            new LiteralMessage("Block type not found"));
    public static final SimpleCommandExceptionType UNSUPPORTED_ARGUMENT = new SimpleCommandExceptionType(
            new LiteralMessage("Block state and data tag arguments are not supported."));

    public static BlockArgument block() {
        return new BlockArgument();
    }

    public static Block getBlock(final com.mojang.brigadier.context.CommandContext<CommandContext> context, String name) {
        return context.getArgument(name, Block.class);
    }

    @Override
    public Block parse(final StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();

        if (reader.canRead() && reader.peek() == '#') {
            reader.setCursor(i);
            throw UNSUPPORTED_ARGUMENT.create();
        }

        Key key = ResourceLocationArgument.read(reader);
        Block block = BlockRegistry.REGISTRY.get(key.value());
        if (block == null) {
            reader.setCursor(i);
            throw BLOCK_NOT_FOUND.create();
        }
        if (reader.canRead() && reader.peek() == '[') {
            reader.setCursor(i);
            throw UNSUPPORTED_ARGUMENT.create();
        }
        return block;
    }

    @Override
    public @NonNull CommandParser commandParser() {
        return CommandParser.BLOCK_STATE;
    }

    @Override
    public CompletableFuture<Suggestions> listSuggestions(final com.mojang.brigadier.context.CommandContext context, final SuggestionsBuilder builder) {
        return RegistryDataArgument.listRegistrySuggestions(context, builder, BlockRegistry.REGISTRY);
    }
}
