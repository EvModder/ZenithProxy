package com.zenith.module.impl;

import com.github.rfresh2.EventConsumer;
import com.zenith.cache.data.entity.Entity;
import com.zenith.cache.data.entity.EntityStandard;
import com.zenith.cache.data.inventory.Container;
import com.zenith.discord.Embed;
import com.zenith.event.client.ClientBotTick;
import com.zenith.event.module.EntityFishHookSpawnEvent;
import com.zenith.event.module.SplashSoundEffectEvent;
import com.zenith.feature.autofish.AutoFishAddEntityHandler;
import com.zenith.feature.autofish.AutoFishSoundHandler;
import com.zenith.feature.player.*;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.network.codec.PacketHandlerCodec;
import com.zenith.network.codec.PacketHandlerStateCodec;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.ProjectileData;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

public class AutoFish extends AbstractInventoryModule {
    private int fishHookEntityId = -1;
    private Hand rodHand = Hand.MAIN_HAND;
    private int delay = 0;
    private Instant castTime = Instant.EPOCH;
    private int fishTimeoutCounter = 0;
    private boolean hookedItemDuringTimeouts;
    private int recoveryItemId = -1;
    private int itemRecoveryAttempts;
    private boolean itemWaitNotified;
    private int soundBiteTicks = 0;
    private int castPendingTicks = 0;
    private int reelPendingTicks = 0;
    private static final int CONFIRMATION_WAIT_TICKS = 100;

    public AutoFish() {
        super(HandRestriction.EITHER, 2);
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(EntityFishHookSpawnEvent.class, this::handleEntityFishHookSpawnEvent),
            of(SplashSoundEffectEvent.class, this::handleSplashSoundEffectEvent),
            of(ClientBotTick.class, this::handleClientTick),
            of(ClientBotTick.Starting.class, this::handleBotTickStarting),
            of(ClientBotTick.Stopped.class, this::handleBotTickStopped)
        );
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.autoFish.enabled;
    }

    @Override
    public int getPriority() {
        return Objects.requireNonNullElse(CONFIG.client.extra.autoFish.priority, 2000);
    }

    @Override
    public PacketHandlerCodec registerClientPacketHandlerCodec() {
        return PacketHandlerCodec.clientBuilder()
            .setId("autofish")
            .setPriority(-5) // after standard client packet handlers
            .state(ProtocolState.GAME, PacketHandlerStateCodec.clientBuilder()
                .inbound(ClientboundAddEntityPacket.class, new AutoFishAddEntityHandler())
                .inbound(ClientboundSoundPacket.class, new AutoFishSoundHandler())
                .build())
            .build();
    }

    public void handleBotTickStarting(final ClientBotTick.Starting event) {
        reset();
    }

    public void handleBotTickStopped(final ClientBotTick.Stopped event) {
        reset();
    }

    private synchronized void reset() {
        fishHookEntityId = -1;
        delay = 0;
        castTime = Instant.EPOCH;
        fishTimeoutCounter = 0;
        hookedItemDuringTimeouts = false;
        recoveryItemId = -1;
        itemRecoveryAttempts = 0;
        itemWaitNotified = false;
        soundBiteTicks = 0;
        castPendingTicks = 0;
        reelPendingTicks = 0;
    }

    public void handleEntityFishHookSpawnEvent(final EntityFishHookSpawnEvent event) {
        try {
            if (event.getOwnerEntityId() != CACHE.getPlayerCache().getEntityId()) return;
            fishHookEntityId = event.fishHookObject().getEntityId();
            soundBiteTicks = 0;
            castPendingTicks = 0;
            castTime = Instant.now();
        } catch (final Exception e) {
            error("Failed to handle EntityFishHookSpawnEvent", e);
        }
    }

    public void handleSplashSoundEffectEvent(SplashSoundEffectEvent event) {
        var packet = event.packet();
        if (isUnambiguousSplash(CACHE.getEntityCache().get(fishHookEntityId),
            CACHE.getPlayerCache().getEntityId(), CACHE.getEntityCache().getEntities().values(),
            packet.getX(), packet.getY(), packet.getZ())) {
            soundBiteTicks = 10;
        }
    }

    static boolean isUnambiguousSplash(Entity own, int ownerId, Iterable<? extends Entity> entities,
                                       double x, double y, double z) {
        if (!isOwnedHook(own, ownerId) || !matchesSplash(own, x, y, z)) return false;
        for (Entity other : entities) {
            if (other != own && other.getEntityType() == EntityType.FISHING_BOBBER
                && matchesSplash(other, x, y, z)) return false;
        }
        return true;
    }

    private static boolean matchesSplash(Entity hook, double x, double y, double z) {
        // Sound coordinates are quantized to 1/8 block; allow bobbing vertically,
        // but keep horizontal tolerance narrow for adjacent fishing accounts.
        return Math.abs(hook.getX() - x) <= 0.3 && Math.abs(hook.getZ() - z) <= 0.3
            && Math.abs(hook.getY() - y) <= 1;
    }

    public void handleClientTick(final ClientBotTick event) {
        if (waitForConfirmation(isFishing())) return;
        boolean soundBite = soundBiteTicks > 0;
        if (soundBite) soundBiteTicks--;
        if (delay > 0) {
            delay--;
            return;
        }
        if (!isFishing()) itemWaitNotified = false;
        if (handleHookedItem()) return;
        // Metadata is primary; a short-lived, unambiguous splash also works when
        // servers strip or freeze the biting flag. Neither signal bypasses ownership.
        if (isOwnedBitingHook(CACHE.getEntityCache().get(fishHookEntityId), CACHE.getPlayerCache().getEntityId())
            || (soundBite && isFishing())) {
            if (!switchToFishingRod() || !isRodInHand()) return;
            int hookId = fishHookEntityId;
            requestUseRod(false).addInputExecutedListener(future -> completeReel(future, hookId, false));
            return;
        }
        if (!isFishing()
            && switchToFishingRod()
            && isRodInHand()) {
            requestUseRod(true).addInputExecutedListener(future -> {
                if (future.getClickResult() instanceof ClickResult.RightClickResult rightClickResult) {
                    if (rightClickResult.getType() == ClickResult.RightClickResult.RightClickType.USE_ITEM) {
                        castTime = Instant.now();
                        castPendingTicks = CONFIRMATION_WAIT_TICKS;
                    }
                }
            });
        }
        if (isFishing() && (itemWaitNotified || Instant.now().getEpochSecond() - castTime.getEpochSecond() > 60)) {
            if (!switchToFishingRod() || !isRodInHand()) return;
            int hookId = fishHookEntityId;
            requestUseRod(false).addInputExecutedListener(future -> {
                if (completeReel(future, hookId, true)) {
                    discordNotification(timeoutWarning());
                }
            });
        }
    }

    private boolean handleHookedItem() {
        int targetId = hookedEntityId(CACHE.getEntityCache().get(fishHookEntityId), CACHE.getPlayerCache().getEntityId());
        var target = CACHE.getEntityCache().get(targetId);
        if (target == null || target.getEntityType() != EntityType.ITEM) return false;
        if (targetId != recoveryItemId) {
            recoveryItemId = targetId;
            itemRecoveryAttempts = 0;
            itemWaitNotified = false;
        }
        if (itemRecoveryAttempts >= 2) {
            if (!itemWaitNotified) {
                itemWaitNotified = true;
                discordNotification(Embed.builder().title("Fishing obstructed")
                    .description("The same dropped item is still hooked after two reel attempts. Waiting for it to detach or disappear; check the casting path.")
                    .errorColor());
            }
            return true;
        }
        if (switchToFishingRod() && isRodInHand()) {
            int hookId = fishHookEntityId;
            requestUseRod(false).addInputExecutedListener(future -> completeItemReel(future, hookId, targetId));
        }
        return true;
    }

    private void completeItemReel(InputRequestFuture future, int hookId, int targetId) {
        if (targetId == recoveryItemId && itemRecoveryAttempts < 2 && beginReel(future, hookId)) {
            itemRecoveryAttempts++;
            hookedItemDuringTimeouts = true;
        }
    }

    private Embed timeoutWarning() {
        return Embed.builder()
            .title("Fishing recovery warning")
            .description("Five timeout-recovery attempts without a detected bite. "
                + (hookedItemDuringTimeouts
                    ? "A dropped item was hooked during this streak; check item collection and the casting path. "
                    : "Check the fishing spot or server lag. ")
                + "Retrying automatically; further warnings are suppressed until a bite is handled.")
            .errorColor();
    }

    static int hookedEntityId(Entity hook, int ownerId) {
        if (!isOwnedHook(hook, ownerId)) return -1;
        // Minecraft 1.21.4 DATA_HOOKED_ENTITY encodes the target ID plus one.
        var encoded = hook.getMetadataValue(8, MetadataTypes.INT, Integer.class);
        return encoded != null && encoded > 0 ? encoded - 1 : -1;
    }

    private boolean beginReel(InputRequestFuture future, int hookId) {
        if (fishHookEntityId != hookId
            || !(future.getClickResult() instanceof ClickResult.RightClickResult result)
            || result.getType() != ClickResult.RightClickResult.RightClickType.USE_ITEM) return false;
        // A sent use-item packet is not a server acknowledgement. Do not cast
        // again until removal is observed; a delayed reel could otherwise toggle it back.
        reelPendingTicks = CONFIRMATION_WAIT_TICKS;
        soundBiteTicks = 0;
        itemWaitNotified = false;
        return true;
    }

    private boolean completeReel(InputRequestFuture future, int hookId, boolean timedOut) {
        if (!beginReel(future, hookId)) return false;
        if (!timedOut) {
            fishTimeoutCounter = 0;
            hookedItemDuringTimeouts = false;
            return false;
        }
        if (fishTimeoutCounter >= 5) return false;
        var hook = CACHE.getEntityCache().get(hookId);
        var target = CACHE.getEntityCache().get(hookedEntityId(hook, CACHE.getPlayerCache().getEntityId()));
        hookedItemDuringTimeouts |= target != null && target.getEntityType() == EntityType.ITEM;
        return ++fishTimeoutCounter == 5;
    }

    private boolean waitForConfirmation(boolean hookPresent) {
        if (reelPendingTicks > 0) {
            if (hookPresent) {
                reelPendingTicks--;
            } else {
                reelPendingTicks = 0;
                fishHookEntityId = -1;
                delay = 20;
            }
            return true;
        }
        if (castPendingTicks > 0) {
            if (hookPresent) castPendingTicks = 0;
            else {
                castPendingTicks--;
                return true;
            }
        }
        return false;
    }

    private boolean isRodInHand() {
        return switch (rodHand) {
            case MAIN_HAND -> itemPredicate(CACHE.getPlayerCache().getEquipment(EquipmentSlot.MAIN_HAND));
            case OFF_HAND -> itemPredicate(CACHE.getPlayerCache().getEquipment(EquipmentSlot.OFF_HAND));
            case null -> false;
        };
    }

    private InputRequestFuture requestUseRod(boolean cast) {
        return INPUTS.submit(InputRequest.builder()
            .owner(this)
            .input(Input.builder()
                .rightClick(true)
                .clickTarget(ClickTarget.None.INSTANCE)
                .hand(rodHand)
                .clickRequiresRotation(cast)
                .build())
            .yaw(CONFIG.client.extra.autoFish.yaw)
            .pitch(CONFIG.client.extra.autoFish.pitch)
            .priority(getPriority())
            .build());
    }

    public boolean switchToFishingRod() {
        delay = doInventoryActions();
        if (getHand() != null && delay == 0) {
            rodHand = getHand();
            return true;
        }
        return false;
    }

    private boolean isFishing() {
        final Entity cachedEntity = CACHE.getEntityCache().get(fishHookEntityId);
        return isOwnedHook(cachedEntity, CACHE.getPlayerCache().getEntityId());
    }

    private static boolean isOwnedHook(Entity entity, int ownerId) {
        return entity instanceof EntityStandard standard
            && standard.getEntityType() == EntityType.FISHING_BOBBER
            && standard.getObjectData() instanceof ProjectileData projectile
            && projectile.getOwnerId() == ownerId;
    }

    static boolean isOwnedBitingHook(Entity entity, int ownerId) {
        // Minecraft 1.21.4 FishingHook.DATA_BITING (after hooked entity at index 8).
        return isOwnedHook(entity, ownerId)
            && Boolean.TRUE.equals(entity.getMetadataValue(9, MetadataTypes.BOOLEAN, Boolean.class));
    }

    @Override
    public boolean itemPredicate(final ItemStack itemStack) {
        return itemStack != Container.EMPTY_STACK && itemStack.getId() == ItemRegistry.FISHING_ROD.id();
    }
}
