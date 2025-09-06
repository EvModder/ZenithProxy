package com.zenith.command.brigadier;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zenith.mc.item.ItemData;
import com.zenith.mc.item.ItemRegistry;
import lombok.Data;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

/**
 * Does not support the full vanilla item arg syntax with data components like `minecraft:stone{Count:1b}`
 * only supports parsing from an item name
 */
@Data
public class ItemArgument implements SerializableArgumentType<ItemData> {
    public static final SimpleCommandExceptionType INVALID_ITEM_EXCEPTION = new SimpleCommandExceptionType(
        new LiteralMessage("Invalid item")
    );

    public static ItemArgument item() {
        return new ItemArgument();
    }

    @Override
    public ItemData parse(final StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();
        var itemName = readItemString(reader);
        if (itemName.contains(":")) {
            // may look like minecraft:item_name
            var itemNameNamespaceSplit = itemName.split(":");
            if (itemNameNamespaceSplit.length != 2) {
                reader.setCursor(i);
                throw INVALID_ITEM_EXCEPTION.create();
            }
            itemName = itemNameNamespaceSplit[1];
        }
        var itemData = ItemRegistry.REGISTRY.get(itemName);
        if (itemData == null) {
            reader.setCursor(i);
            throw INVALID_ITEM_EXCEPTION.create();
        }
        return itemData;
    }

    public static ItemData getItem(final CommandContext<com.zenith.command.api.CommandContext> context, String name) {
        return context.getArgument(name, ItemData.class);
    }

    private String readItemString(StringReader reader) throws CommandSyntaxException {
        final int start = reader.getCursor();
        while (reader.canRead() && isAllowedInItemString(reader.peek())) {
            reader.skip();
        }
        if (reader.getCursor() == start) {
            reader.setCursor(start);
            throw INVALID_ITEM_EXCEPTION.createWithContext(reader);
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    private static boolean isAllowedInItemString(final char c) {
        return c >= 'A' && c <= 'Z'
            || c >= 'a' && c <= 'z'
            || c == '_' || c == ':';
    }

    @Override
    public @NonNull CommandParser commandParser() {
        return CommandParser.ITEM_STACK;
    }

    @Override
    public CompletableFuture<Suggestions> listSuggestions(final com.mojang.brigadier.context.CommandContext context, final SuggestionsBuilder builder) {
        return RegistryDataArgument.listRegistrySuggestions(context, builder, ItemRegistry.REGISTRY);
    }
}
