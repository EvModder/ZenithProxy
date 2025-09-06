package com.zenith.command.brigadier;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zenith.mc.Registry;
import com.zenith.mc.RegistryData;
import com.zenith.mc.biome.Biome;
import com.zenith.mc.biome.BiomeRegistry;
import com.zenith.mc.enchantment.EnchantmentData;
import com.zenith.mc.enchantment.EnchantmentRegistry;
import com.zenith.mc.entity.EntityData;
import com.zenith.mc.entity.EntityRegistry;
import lombok.Data;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.CommandProperties;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.ResourceProperties;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Data
public class RegistryDataArgument<T extends RegistryData> implements SerializableArgumentType<RegistryData> {
    public static final SimpleCommandExceptionType INVALID_REGISTRY_KEY = new SimpleCommandExceptionType(
        new LiteralMessage("Invalid registry key"));

    private final Key registryKey;
    // Supplier indirection needed for dynamic registries
    private final Supplier<Registry<T>> registry;

    public static <T extends RegistryData> RegistryDataArgument<T> key(String registryKey, Registry<T> registry) {
        return new RegistryDataArgument<>(Key.key(registryKey), () -> registry);
    }

    public static <T extends RegistryData> T getRegistryData(final com.mojang.brigadier.context.CommandContext<com.zenith.command.api.CommandContext> context, String name, Class<T> registryType) {
        return context.getArgument(name, registryType);
    }

    public static RegistryDataArgument<EntityData> entity() {
        return key("entity_type", EntityRegistry.REGISTRY);
    }

    public static EntityData getEntity(final com.mojang.brigadier.context.CommandContext<com.zenith.command.api.CommandContext> context, String name) {
        return getRegistryData(context, name, EntityData.class);
    }

    public static RegistryDataArgument<EnchantmentData> enchantment() {
        return key("enchantment_type", EnchantmentRegistry.REGISTRY.getLoadedRegistry());
    }

    public static EnchantmentData getEnchantment(final com.mojang.brigadier.context.CommandContext<com.zenith.command.api.CommandContext> context, String name) {
        return getRegistryData(context, name, EnchantmentData.class);
    }

    public static RegistryDataArgument<Biome> biome() {
        return key("worldgen/biome", BiomeRegistry.REGISTRY.getLoadedRegistry());
    }

    public static Biome getBiome(final com.mojang.brigadier.context.CommandContext<com.zenith.command.api.CommandContext> context, String name) {
        return getRegistryData(context, name, Biome.class);
    }

    @Override
    public T parse(final StringReader reader) throws CommandSyntaxException {
        var key = ResourceLocationArgument.read(reader);
        var data = registry.get().get(key.value());
        if (data == null) {
            throw INVALID_REGISTRY_KEY.create();
        }
        return data;
    }

    @Override
    public @NonNull CommandParser commandParser() {
        return CommandParser.RESOURCE_KEY;
    }

    @Override
    public @Nullable CommandProperties commandProperties() {
        return new ResourceProperties(registryKey.asString());
    }

    public static <T extends RegistryData> CompletableFuture<Suggestions> listRegistrySuggestions(final CommandContext context, final SuggestionsBuilder builder, Registry<T> registry) {
        String input = builder.getRemainingLowerCase();
        if (Key.parseable(input)) {
            var key = Key.key(input);
            input = key.value();
        }
        for (var val : registry.getIdMap().values()) {
            if (val.name().startsWith(input)) {
                builder.suggest(val.name());
            }
        }
        return builder.buildFuture();
    }

    @Override
    public CompletableFuture<Suggestions> listSuggestions(final CommandContext context, final SuggestionsBuilder builder) {
        return listRegistrySuggestions(context, builder, registry.get());
    }
}
