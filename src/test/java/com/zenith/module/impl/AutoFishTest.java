package com.zenith.module.impl;

import com.zenith.cache.data.entity.EntityStandard;
import com.zenith.feature.player.ClickResult;
import com.zenith.feature.player.InputRequestFuture;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.ProjectileData;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AutoFishTest {
    private final AutoFish fish = new AutoFish();

    @Test
    void soundFallbackWorksWithoutBitingMetadataButRejectsNeighbors() {
        var own = hook(100, false, 0);
        own.getMetadata().clear();
        for (double separation : new double[]{0.6, 0.8, 1, 1.2, 1.4}) {
            var neighbor = hook(101, true, separation);
            var entities = List.of(own, neighbor);
            assertTrue(AutoFish.isUnambiguousSplash(own, 100, entities, -0.125, 0.5, -0.125));
            assertFalse(AutoFish.isUnambiguousSplash(own, 100, entities, separation, 0, 0));
        }
        own.getMetadata().put(9, new BooleanEntityMetadata(9, MetadataTypes.BOOLEAN, false));
        assertTrue(AutoFish.isUnambiguousSplash(own, 100, List.of(own), 0, 0, 0));
        assertFalse(AutoFish.isUnambiguousSplash(own, 101, List.of(own), 0, 0, 0));
    }

    @Test
    void soundFallbackRejectsAmbiguousOverlappingHooksAndDistantSounds() {
        var own = hook(100, false, 0);
        var neighbor = hook(101, false, 0.2);
        assertFalse(AutoFish.isUnambiguousSplash(own, 100, List.of(own, neighbor), 0.1, 0, 0));
        assertFalse(AutoFish.isUnambiguousSplash(own, 100, List.of(own), 0, 2, 0));
        assertFalse(AutoFish.isUnambiguousSplash(null, 100, List.of(own), 0, 0, 0));
    }

    private EntityStandard hook(int owner, boolean biting, double x) {
        var hook = new EntityStandard();
        hook.setEntityType(EntityType.FISHING_BOBBER);
        hook.setObjectData(new ProjectileData(owner));
        hook.setX(x);
        hook.getMetadata().put(9, new BooleanEntityMetadata(9, MetadataTypes.BOOLEAN, biting));
        return hook;
    }

    @Test
    void nearbyAccountsCannotTriggerEachOthersReels() {
        for (double separation : new double[]{0, 0.6, 0.8, 1, 1.2, 1.4}) {
            var own = hook(100, false, 0);
            var neighbor = hook(101, true, separation);
            assertFalse(AutoFish.isOwnedBitingHook(own, 100));
            assertFalse(AutoFish.isOwnedBitingHook(neighbor, 100));
            assertTrue(AutoFish.isOwnedBitingHook(neighbor, 101));
            own.getMetadata().put(9, new BooleanEntityMetadata(9, MetadataTypes.BOOLEAN, true));
            assertTrue(AutoFish.isOwnedBitingHook(own, 100));
            assertFalse(AutoFish.isOwnedBitingHook(own, 101));
        }
    }

    @Test
    void missingOrExpiredBiteDoesNotReel() {
        assertFalse(AutoFish.isOwnedBitingHook(null, 100));
        var hook = hook(100, true, 0);
        hook.getMetadata().clear();
        assertFalse(AutoFish.isOwnedBitingHook(hook, 100));
        hook.getMetadata().put(9, new BooleanEntityMetadata(9, MetadataTypes.BOOLEAN, false));
        assertFalse(AutoFish.isOwnedBitingHook(hook, 100));
        hook.setEntityType(EntityType.ARROW);
        hook.getMetadata().put(9, new BooleanEntityMetadata(9, MetadataTypes.BOOLEAN, true));
        assertFalse(AutoFish.isOwnedBitingHook(hook, 100));
    }

    private Field field(String name) throws Exception {
        var field = AutoFish.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private boolean reel(boolean executed, boolean timedOut, int hookId) throws Exception {
        var future = new InputRequestFuture();
        if (executed) future.setClickResult(ClickResult.RightClickResult.useItem());
        var method = AutoFish.class.getDeclaredMethod("completeReel", InputRequestFuture.class, int.class, boolean.class);
        method.setAccessible(true);
        return (boolean) method.invoke(fish, future, hookId, timedOut);
    }

    @Test
    void rejectedReelPreservesHookAndCounter() throws Exception {
        field("fishHookEntityId").setInt(fish, 42);
        assertFalse(reel(false, true, 42));
        assertEquals(42, field("fishHookEntityId").getInt(fish));
        assertEquals(0, field("fishTimeoutCounter").getInt(fish));
        assertEquals(0, field("delay").getInt(fish));
    }

    @Test
    void warnsOncePerFailureStreakAndRearmsAfterBite() throws Exception {
        for (int streak = 0; streak < 2; streak++) {
            for (int i = 1; i <= 15; i++) {
                field("fishHookEntityId").setInt(fish, i);
                assertEquals(i == 5, reel(true, true, i));
                assertEquals(i, field("fishHookEntityId").getInt(fish));
                assertEquals(100, field("reelPendingTicks").getInt(fish));
            }
            field("fishHookEntityId").setInt(fish, 42);
            assertFalse(reel(true, false, 42));
            assertEquals(0, field("fishTimeoutCounter").getInt(fish));
        }
    }

    @Test
    void staleReelCannotClearNewHook() throws Exception {
        field("fishHookEntityId").setInt(fish, 43);
        assertFalse(reel(true, true, 42));
        assertEquals(43, field("fishHookEntityId").getInt(fish));
        assertEquals(0, field("fishTimeoutCounter").getInt(fish));
    }

    private boolean waitForConfirmation(boolean present) throws Exception {
        var method = AutoFish.class.getDeclaredMethod("waitForConfirmation", boolean.class);
        method.setAccessible(true);
        return (boolean) method.invoke(fish, present);
    }

    @Test
    void delayedCastDoesNotImmediatelyToggleRodAgain() throws Exception {
        field("castPendingTicks").setInt(fish, 100);
        for (int i = 0; i < 30; i++) assertTrue(waitForConfirmation(false));
        assertFalse(waitForConfirmation(true));
        assertEquals(0, field("castPendingTicks").getInt(fish));
    }

    @Test
    void reelWaitsForRemovalAndOnlyThenAllowsRecast() throws Exception {
        field("fishHookEntityId").setInt(fish, 42);
        reel(true, false, 42);
        for (int i = 0; i < 30; i++) assertTrue(waitForConfirmation(true));
        assertEquals(42, field("fishHookEntityId").getInt(fish));
        assertTrue(waitForConfirmation(false));
        assertEquals(-1, field("fishHookEntityId").getInt(fish));
        assertEquals(20, field("delay").getInt(fish));
        assertEquals(0, field("reelPendingTicks").getInt(fish));
    }

    @Test
    void missingAcknowledgementDoesNotWaitForever() throws Exception {
        field("castPendingTicks").setInt(fish, 100);
        for (int i = 0; i < 100; i++) assertTrue(waitForConfirmation(false));
        assertFalse(waitForConfirmation(false));
        field("fishHookEntityId").setInt(fish, 42);
        reel(true, true, 42);
        for (int i = 0; i < 100; i++) assertTrue(waitForConfirmation(true));
        assertFalse(waitForConfirmation(true));
        assertEquals(42, field("fishHookEntityId").getInt(fish));
    }
}
