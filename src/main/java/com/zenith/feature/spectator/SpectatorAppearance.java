package com.zenith.feature.spectator;

import com.zenith.Proxy;
import com.zenith.feature.spectator.entity.mob.SpectatorEntityCat;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.network.server.ServerSession;
import org.cloudburstmc.math.imaginary.Quaternionf;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.IntEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.type.ObjectDataComponent;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRotateHeadPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddEntityPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Viewer-specific cat/body and head lifecycle; the owner already has a native spectator head. */
public final class SpectatorAppearance {
    // Settled head bounds are 5 x 5 x 6 model pixels at 0.8 scale; leave at least 15 mm clearance.
    private static final float HEAD_SCALE = 0.66f;

    private SpectatorAppearance() { }

    public static void spawn(ServerSession subject, ServerSession viewer) {
        var packets = new ArrayList<Packet>(10);
        packets.add(subject.getEntityRemovalPacket());
        if (!visible(subject, viewer)) {
            viewer.sendEntityUpdate(packets);
            return;
        }
        var movement = movement(subject, subject.getSpectatorEntity().getRenderPosition(subject.getSpectatorPlayerCache()));
        var body = movement.position();
        if (subject.getSpectatorEntity() instanceof SpectatorEntityCat) {
            packets.add(new ClientboundAddEntityPacket(subject.getSpectatorEntityId(), subject.getSpectatorEntityUUID(),
                EntityType.CAT, body.getX(), body.getY(), body.getZ(), body.getYaw(), body.getYaw(), body.getPitch()));
        } else {
            packets.add(subject.getEntitySpawnPacket());
        }
        packets.add(subject == viewer ? subject.getSelfEntityMetadataPacket() : subject.getEntityMetadataPacket());
        if (hasHead(subject, viewer)) {
            float yaw = body.getYaw() - 180;
            packets.add(new ClientboundAddEntityPacket(subject.getSpectatorHeadEntityId(), subject.getSpectatorHeadUUID(),
                EntityType.ITEM_DISPLAY, body.getX(), body.getY() + SpectatorEntityCat.PASSENGER_HEIGHT, body.getZ(),
                yaw, yaw, 0));
            packets.add(headMetadata(subject, (ClientboundSetEntityDataPacket) movement.observer().getLast()));
            // The client positions passengers from their vehicle, rather than independently interpolating them.
            packets.add(new ClientboundSetPassengersPacket(subject.getSpectatorEntityId(),
                new int[]{subject.getSpectatorHeadEntityId()}));
        }
        packets.addAll(hasHead(subject, viewer) ? movement.observer() : movement.body());
        viewer.sendEntityUpdate(packets);
    }

    public static void refresh(ServerSession subject) {
        for (var viewer : Proxy.getInstance().getActiveConnections().getArray()) spawn(subject, viewer);
    }

    public static void move(ServerSession subject, ServerSession viewer, Vector3d position) {
        if (!visible(subject, viewer)) return;
        var movement = movement(subject, position);
        viewer.sendEntityUpdate(hasHead(subject, viewer) ? movement.observer() : movement.body());
    }

    public static void moveAll(ServerSession subject) {
        var movement = movement(subject, subject.getSpectatorEntity().getRenderPosition(subject.getSpectatorPlayerCache()));
        for (var viewer : Proxy.getInstance().getActiveConnections().getArray()) {
            if (visible(subject, viewer)) {
                viewer.sendEntityUpdate(hasHead(subject, viewer) ? movement.observer() : movement.body());
            }
        }
    }

    private record Movement(ClientboundTeleportEntityPacket position, List<Packet> body, List<Packet> observer) { }

    private static Movement movement(ServerSession subject, Vector3d position) {
        boolean cat = subject.getSpectatorEntity() instanceof SpectatorEntityCat;
        float yaw = cat ? catYaw(subject) : SpectatorSync.getDisplayYaw(subject);
        float pitch = SpectatorSync.getDisplayPitch(subject);
        var positionPacket = new ClientboundTeleportEntityPacket(subject.getSpectatorEntityId(),
            position.getX(), position.getY(), position.getZ(), 0, 0, 0,
            yaw, pitch, false);
        List<Packet> body = List.of(positionPacket, new ClientboundRotateHeadPacket(subject.getSpectatorEntityId(), yaw));
        if (!cat) return new Movement(positionPacket, body, body);
        return new Movement(positionPacket, body, List.of(body.get(0), body.get(1),
            new ClientboundMoveEntityRotPacket(subject.getSpectatorHeadEntityId(), yaw - 180, 0, false),
            headPose(subject.getSpectatorHeadEntityId(), pitch)));
    }

    private static boolean visible(ServerSession subject, ServerSession viewer) {
        return !subject.hasCameraTarget() && (subject != viewer || subject.isShowSelfEntity());
    }

    private static boolean hasHead(ServerSession subject, ServerSession viewer) {
        return subject != viewer && subject.getSpectatorEntity() instanceof SpectatorEntityCat;
    }

    private static float catYaw(ServerSession subject) {
        // Match the cat head's decoded angle before applying the display's 180-degree correction.
        // Quantizing yaw - 180 directly can put the two models in adjacent byte-angle buckets.
        float yaw = subject.getSpectatorPlayerCache().getYaw();
        return (byte) (yaw * 256.0f / 360.0f) * 360.0f / 256.0f;
    }

    private static ClientboundSetEntityDataPacket headPose(int entityId, float pitch) {
        double halfPitch = Math.toRadians(-pitch) / 2;
        var rotation = Quaternionf.from(Math.sin(halfPitch), 0, 0, Math.cos(halfPitch));
        // Center on the complete cat head bounds (model y=-0.5, z=-1), including ears and muzzle.
        // This tight fit covers settled poses; transient clipping while turning is intentional.
        var translation = rotation.rotate(0, HEAD_SCALE / 4 + 0.025, -0.05).add(0,
            SpectatorEntityCat.HEAD_PIVOT_HEIGHT - SpectatorEntityCat.PASSENGER_HEIGHT,
            -SpectatorEntityCat.HEAD_PIVOT_FORWARD);
        return new ClientboundSetEntityDataPacket(entityId, List.of(
            new ObjectEntityMetadata<>(11, MetadataTypes.VECTOR3, translation),
            new ObjectEntityMetadata<>(13, MetadataTypes.QUATERNION, rotation)));
    }

    private static ClientboundSetEntityDataPacket headMetadata(ServerSession subject, ClientboundSetEntityDataPacket pose) {
        var real = subject.getProfileCache().getProfile();
        var profile = new GameProfile(real.getId(), real.getName());
        profile.setProperties(subject.getSpectatorFakeProfileCache().getProfile().getProperties());
        var components = new DataComponents(Map.of(DataComponentTypes.PROFILE,
            new ObjectDataComponent<>(DataComponentTypes.PROFILE, profile)));
        var head = new ItemStack(ItemRegistry.PLAYER_HEAD.id(), 1, components);
        var metadata = new ArrayList<>(pose.getMetadata());
        metadata.addAll(List.of(
            new IntEntityMetadata(10, MetadataTypes.INT, 3), // match the cat's three-tick yaw interpolation
            new ObjectEntityMetadata<>(12, MetadataTypes.VECTOR3, Vector3f.from(HEAD_SCALE, HEAD_SCALE, HEAD_SCALE)),
            new ObjectEntityMetadata<>(23, MetadataTypes.ITEM, head)));
        return new ClientboundSetEntityDataPacket(subject.getSpectatorHeadEntityId(), metadata);
    }
}
