package com.zenith.feature.spectator.entity;

import com.zenith.cache.data.PlayerCache;
import com.zenith.util.math.MathHelper;
import net.kyori.adventure.text.Component;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.BuiltinSound;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.SoundCategory;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public abstract class SpectatorEntity {
    /** Public API **/
    public ArrayList<EntityMetadata<?, ?>> getSelfEntityMetadata(final GameProfile spectatorRealProfile, final GameProfile spectatorFakeProfile, final int spectatorEntityId) {
        return buildMetadata(spectatorRealProfile, spectatorEntityId, true);
    }

    public ArrayList<EntityMetadata<?, ?>> getEntityMetadata(final GameProfile spectatorRealProfile, final GameProfile spectatorFakeProfile, final int spectatorEntityId) {
        return buildMetadata(spectatorRealProfile, spectatorEntityId, false);
    }

    // Optionally overridden by base classes
    public Optional<Packet> getSoundPacket(final PlayerCache playerCache) {
        return Optional.empty();
    }

    public Vector3d getRenderPosition(final PlayerCache playerCache) {
        // Keep the existing camera clearance for appearances without a model-specific anchor.
        double pitch = Math.toRadians(MathHelper.clamp(playerCache.getPitch(), -30, 30));
        double yaw = Math.toRadians(playerCache.getYaw());
        double distance = getWidth() * -0.5 - 0.5;
        return Vector3d.from(
            playerCache.getX() - Math.sin(yaw) * Math.cos(pitch) * distance,
            playerCache.getY() + 1.6 - getEyeHeight() - Math.sin(pitch) * distance,
            playerCache.getZ() + Math.cos(yaw) * Math.cos(pitch) * distance);
    }

    /** Internal API **/
    protected ArrayList<EntityMetadata<?, ?>> buildMetadata(final GameProfile spectatorRealProfile, final int spectatorEntityId, boolean self) {
        var metadata = getBaseEntityMetadata(spectatorRealProfile, spectatorEntityId);
        metadata.add(new ObjectEntityMetadata<>(2, MetadataTypes.OPTIONAL_CHAT, Optional.of(Component.text(spectatorRealProfile.getName()))));
        metadata.add(new BooleanEntityMetadata(3, MetadataTypes.BOOLEAN, !self)); // hides nametag on self entities
        return metadata;
    }

    /** Implemented by base classes **/
    protected abstract ArrayList<EntityMetadata<?, ?>> getBaseEntityMetadata(final GameProfile spectatorProfile, final int spectatorEntityId);
    public abstract Packet getSpawnPacket(final int entityId, final UUID uuid, final PlayerCache playerCache, final GameProfile gameProfile);
    // A list of all minecraft mobs with eye height, total height, and total width (on 1.20)
    // https://gist.github.com/bradcarnage/c894976345a0e57280c8619fe3ac0282
    public abstract double getEyeHeight();
    public abstract double getHeight();
    public abstract double getWidth();

    /** Convenience methods for base classes **/

    // right-sized empty list for entities without additional metadata
    protected ArrayList<EntityMetadata<?, ?>> noMetadataList() {
        return new ArrayList<>(2);
    }

    protected ArrayList<EntityMetadata<?, ?>> metadataListOf(EntityMetadata... metadata) {
        ArrayList<EntityMetadata<?, ?>> list = new ArrayList<>(5);
        for (int i = 0; i < metadata.length; i++) {
            list.add(metadata[i]);
        }
        return list;
    }

    protected ClientboundSoundPacket buildSoundPacket(final PlayerCache playerCache, BuiltinSound sound) {
        final float randFloat = ThreadLocalRandom.current().nextFloat();
        return new ClientboundSoundPacket(
            sound,
            SoundCategory.AMBIENT,
            playerCache.getX(),
            playerCache.getY(),
            playerCache.getZ(),
            1.0f - (randFloat / 2f),
            1.0f + (randFloat / 10f), // slight pitch variations
            0L
        );
    }

    protected ClientboundSoundPacket buildSoundPacket(final PlayerCache playerCache, BuiltinSound... sounds) {
        final float randFloat = ThreadLocalRandom.current().nextFloat();
        final int randInt = ThreadLocalRandom.current().nextInt(sounds.length);
        return new ClientboundSoundPacket(
            sounds[randInt],
            SoundCategory.AMBIENT,
            playerCache.getX(),
            playerCache.getY(),
            playerCache.getZ(),
            1.0f - (randFloat / 2f),
            1.0f + (randFloat / 10f), // slight pitch variations
            0L
        );
    }
}
