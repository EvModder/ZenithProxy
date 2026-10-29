package com.zenith.feature.spectator.entity.mob;

import com.zenith.cache.data.PlayerCache;
import com.zenith.mc.entity.EntityRegistry;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.BuiltinSound;

import java.util.ArrayList;
import java.util.Optional;

public class SpectatorEntityCat extends SpectatorMob {
    // Vanilla adult cat model/attachment points, unchanged between 1.21.4 and 26.3.
    public static final double HEAD_PIVOT_FORWARD = 9.0 / 16.0 * 0.8;
    public static final double HEAD_PIVOT_HEIGHT = (1.501 - 15.0 / 16.0) * 0.8;
    public static final double PASSENGER_HEIGHT = 0.5125f;
    // Preserve the level-looking head center, but do not move the body when pitch changes.
    private static final double BODY_HEIGHT = (1.501 + 0.25) * 0.9375 - HEAD_PIVOT_HEIGHT;

    @Override
    public Vector3d getRenderPosition(final PlayerCache playerCache) {
        double yaw = Math.toRadians(playerCache.getYaw());
        return Vector3d.from(
            playerCache.getX() + Math.sin(yaw) * HEAD_PIVOT_FORWARD,
            playerCache.getY() + BODY_HEIGHT,
            playerCache.getZ() - Math.cos(yaw) * HEAD_PIVOT_FORWARD);
    }

    @Override
    public ArrayList<EntityMetadata<?, ?>> getBaseEntityMetadata(final GameProfile spectatorProfile, final int spectatorEntityId) {
        return metadataListOf(
            new ObjectEntityMetadata<>(19, MetadataTypes.CAT_VARIANT, (spectatorEntityId % 10))
        );
    }

    @Override
    EntityType getType() {
        return EntityType.CAT;
    }

    @Override
    public double getEyeHeight() {
        return EntityRegistry.CAT.eyeHeight();
    }

    @Override
    public double getHeight() {
        return EntityRegistry.CAT.height();
    }

    @Override
    public double getWidth() {
        return EntityRegistry.CAT.width();
    }

    @Override
    public Optional<Packet> getSoundPacket(final PlayerCache playerCache) {
        return Optional.of(buildSoundPacket(playerCache, BuiltinSound.ENTITY_CAT_PURREOW, BuiltinSound.ENTITY_CAT_AMBIENT));
    }

}
