package com.zenith.module.impl;

import com.zenith.Proxy;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.actions.InventoryAction;
import com.zenith.mc.food.FoodRegistry;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.network.server.ServerSession;
import com.zenith.util.RequestFuture;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.junit.jupiter.api.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static com.zenith.Globals.*;
import static org.junit.jupiter.api.Assertions.*;

class AutoEatTest {
    private final TestAutoEat eat = new TestAutoEat();
    private final ItemStack sword = new ItemStack(ItemRegistry.NETHERITE_SWORD.id(), 1);
    private final ItemStack food = new ItemStack(FoodRegistry.GOLDEN_CARROT.id(), 64);
    private List<ItemStack> previousInventory;
    private int previousSlot, previousFood;
    private float previousHealth;
    private GameMode previousGameMode;

    @BeforeEach void setup() {
        var player = CACHE.getPlayerCache();
        previousInventory = new ArrayList<>(player.getPlayerInventory());
        previousSlot = player.getHeldItemSlot();
        previousFood = player.getThePlayer().getFood();
        previousHealth = player.getThePlayer().getHealth();
        previousGameMode = player.getGameMode();
        player.getPlayerInventory().replaceAll(item -> null);
        player.setHeldItemSlot(2);
        player.setGameMode(GameMode.SURVIVAL);
        player.getThePlayer().setFood(5).setHealth(20f);
        slot(38, sword);
    }

    @AfterEach void restore() {
        var player = CACHE.getPlayerCache();
        for (int i = 0; i < previousInventory.size(); i++) slot(i, previousInventory.get(i));
        player.setHeldItemSlot(previousSlot);
        player.setGameMode(previousGameMode);
        player.getThePlayer().setFood(previousFood).setHealth(previousHealth);
    }

    private static void slot(int index, ItemStack stack) { CACHE.getPlayerCache().setInventorySlot(0, stack, index); }
    private static ItemStack slot(int index) { return CACHE.getPlayerCache().getPlayerInventory().get(index); }
    private void equip() {
        assertEquals(AbstractInventoryModule.ActionState.SWAPPING, eat.doInventoryActionsV2().state());
        eat.execute();
    }

    @Test void hotbarFoodUsesOnlySelectionPacketsAndRestoresPreviousSlot() {
        slot(44, food);
        slot(20, food);
        var layout = new ArrayList<>(CACHE.getPlayerCache().getPlayerInventory());
        equip();
        assertEquals(8, CACHE.getPlayerCache().getHeldItemSlot());
        assertEquals(layout, CACHE.getPlayerCache().getPlayerInventory());
        assertTrue(eat.restoreFoodSelection());
        eat.execute();
        assertEquals(2, CACHE.getPlayerCache().getHeldItemSlot());
        assertEquals(layout, CACHE.getPlayerCache().getPlayerInventory());
        assertTrue(eat.sent.stream().allMatch(p -> p instanceof ServerboundSetCarriedItemPacket));
    }

    @Test void mainInventoryFoodTemporarilySwapsWithSelectedSlotNotSlotOne() {
        slot(20, food);
        equip();
        assertEquals(food, slot(38));
        assertEquals(sword, slot(20));
        assertNull(slot(36));
        slot(38, new ItemStack(food.getId(), 63));
        assertTrue(eat.restoreFoodSelection());
        eat.execute();
        assertEquals(sword, slot(38));
        assertEquals(63, slot(20).getAmount());
        assertEquals(2, CACHE.getPlayerCache().getHeldItemSlot());
    }

    @Test void consumingLastFoodStillRestoresDisplacedSword() {
        slot(20, new ItemStack(food.getId(), 1));
        equip();
        slot(38, null);
        eat.restoreFoodSelection();
        eat.execute();
        assertEquals(sword, slot(38));
        assertNull(slot(20));
        assertFalse(eat.restoreFoodSelection());
    }

    @Test void consumptionContainersReturnToTheFoodSlot() {
        for (var leftover : new ItemStack[]{new ItemStack(ItemRegistry.BOWL.id(), 1),
            new ItemStack(ItemRegistry.GLASS_BOTTLE.id(), 1)}) {
            eat.reset();
            slot(20, food);
            equip();
            slot(38, leftover);
            eat.restoreFoodSelection();
            eat.execute();
            assertEquals(sword, slot(38));
            assertEquals(leftover, slot(20));
        }
    }

    @Test void emptyHandAndConsumedFoodDoNotLeaveAnEndlessRestore() {
        slot(38, null);
        slot(20, new ItemStack(food.getId(), 1));
        equip();
        slot(38, null);
        eat.restoreFoodSelection();
        assertNull(eat.execute());
        assertFalse(eat.restoreFoodSelection());
    }

    @Test void foodAlreadyInEitherHandNeedsNoInventoryActions() {
        slot(45, food);
        assertEquals(AbstractInventoryModule.ActionState.ITEM_IN_HAND, eat.doInventoryActionsV2().state());
        assertEquals(Hand.OFF_HAND, eat.getHand());
        assertTrue(eat.pending.isEmpty());
        assertFalse(eat.restoreFoodSelection());
        slot(45, null);
        slot(38, food);
        assertEquals(AbstractInventoryModule.ActionState.ITEM_IN_HAND, eat.doInventoryActionsV2().state());
        assertEquals(Hand.MAIN_HAND, eat.getHand());
        assertTrue(eat.pending.isEmpty());
    }

    @Test void consumableOffhandTakesPrecedenceEvenWhenBothHandsHaveFood() {
        slot(45, food);
        slot(38, food);
        assertEquals(AbstractInventoryModule.ActionState.ITEM_IN_HAND, eat.doInventoryActionsV2().state());
        assertEquals(Hand.OFF_HAND, eat.getHand());
        assertTrue(eat.pending.isEmpty());
        assertFalse(eat.restoreFoodSelection());
    }

    @Test void rejectedEquipAndRestoreRequestsCanBeRetried() {
        slot(44, food);
        eat.rejectNext = true;
        assertFalse(eat.doInventoryActionsV2().inventoryActionFuture().getNow());
        assertFalse(eat.restoreFoodSelection());
        assertEquals(2, CACHE.getPlayerCache().getHeldItemSlot());
        equip();
        eat.rejectNext = true;
        assertTrue(eat.restoreFoodSelection());
        assertTrue(eat.restoreFoodSelection());
        eat.execute();
        assertEquals(2, CACHE.getPlayerCache().getHeldItemSlot());
    }

    @Test void resetCancelsQueuedEquipAndRestoreActions() {
        slot(44, food);
        eat.doInventoryActionsV2();
        eat.reset();
        assertNull(eat.execute());
        equip();
        eat.restoreFoodSelection();
        eat.reset();
        assertNull(eat.execute());
        assertEquals(8, CACHE.getPlayerCache().getHeldItemSlot());
    }

    @Test void anotherSelectionIsNotOverriddenByRestore() {
        slot(44, food);
        equip();
        eat.restoreFoodSelection();
        CACHE.getPlayerCache().setHeldItemSlot(5);
        assertNull(eat.execute());
        assertEquals(5, CACHE.getPlayerCache().getHeldItemSlot());
    }

    @Test void unrelatedInventoryChangesAreNotOverwritten() {
        slot(20, food);
        equip();
        slot(20, new ItemStack(ItemRegistry.STONE.id(), 1));
        eat.restoreFoodSelection();
        assertNull(eat.execute());
        assertEquals(ItemRegistry.STONE.id(), slot(20).getId());
    }

    @Test void fullInventoryResyncBeforeEquipCannotSwapAnUnrelatedItem() {
        slot(20, food);
        eat.doInventoryActionsV2();
        var resync = CACHE.getPlayerCache().getPlayerInventory().toArray(ItemStack[]::new);
        resync[20] = new ItemStack(ItemRegistry.STONE.id(), 1);
        CACHE.getPlayerCache().setInventory(0, resync);
        assertNull(eat.execute());
        assertEquals(sword, slot(38));
        assertEquals(ItemRegistry.STONE.id(), slot(20).getId());
    }

    @Test void heldCursorStackIsNotDroppedToEquipFood() {
        slot(20, food);
        var inventory = CACHE.getPlayerCache().getInventoryCache();
        var previousMouse = inventory.getMouseStack();
        try {
            inventory.setMouseStack(sword);
            equip();
            assertTrue(eat.sent.isEmpty());
            assertEquals(sword, inventory.getMouseStack());
            assertEquals(sword, slot(38));
        } finally {
            inventory.setMouseStack(previousMouse);
        }
    }

    @Test void controllerTakeoverPreventsQueuedInventoryActions() {
        slot(44, food);
        eat.doInventoryActionsV2();
        var proxy = Proxy.getInstance();
        var previous = proxy.getCurrentPlayer().get();
        var controller = new ServerSession("localhost", 0, new MinecraftProtocol(), null) {
            @Override public boolean isLoggedIn() { return true; }
        };
        try {
            proxy.getCurrentPlayer().set(controller);
            assertNull(eat.execute());
        } finally {
            proxy.getCurrentPlayer().set(previous);
        }
        equip();
        eat.restoreFoodSelection();
        try {
            proxy.getCurrentPlayer().set(controller);
            assertNull(eat.execute());
        } finally {
            proxy.getCurrentPlayer().set(previous);
        }
    }

    @Test void eatingCycleRestoresWeaponEvenAfterHungerHasRecovered() {
        slot(44, food);
        eat.handleClientTick(ClientBotTick.INSTANCE);
        eat.execute();
        eat.handleClientTick(ClientBotTick.INSTANCE);
        assertTrue(eat.isEating());
        CACHE.getPlayerCache().getThePlayer().setFood(20);
        for (int i = 0; i < 50; i++) eat.handleClientTick(ClientBotTick.INSTANCE);
        eat.handleClientTick(ClientBotTick.INSTANCE);
        eat.execute();
        eat.handleClientTick(ClientBotTick.INSTANCE);
        assertEquals(2, CACHE.getPlayerCache().getHeldItemSlot());
        assertEquals(sword, slot(38));
        assertFalse(eat.isEating());
        assertEquals(1, eat.meals);
    }

    @Test void recoveryBeforeEatingStillRestoresSelection() {
        slot(44, food);
        eat.handleClientTick(ClientBotTick.INSTANCE);
        eat.execute();
        CACHE.getPlayerCache().getThePlayer().setFood(20);
        eat.handleClientTick(ClientBotTick.INSTANCE);
        eat.execute();
        assertEquals(2, CACHE.getPlayerCache().getHeldItemSlot());
        assertEquals(0, eat.meals);
    }

    private static class TestAutoEat extends AutoEat {
        record Pending(InventoryAction action, RequestFuture future) {}
        final ArrayDeque<Pending> pending = new ArrayDeque<>();
        final List<MinecraftPacket> sent = new ArrayList<>();
        boolean rejectNext;
        int meals;

        @Override RequestFuture submitInventoryAction(InventoryAction action) {
            if (rejectNext) { rejectNext = false; return RequestFuture.rejected; }
            var future = new RequestFuture();
            pending.add(new Pending(action, future));
            return future;
        }

        MinecraftPacket execute() {
            var request = pending.removeFirst();
            var packet = request.action().packet();
            if (packet instanceof ServerboundSetCarriedItemPacket held) CACHE.getPlayerCache().setHeldItemSlot(held.getSlot());
            else if (packet instanceof ServerboundContainerClickPacket click)
                CACHE.getPlayerCache().getInventoryCache().handleContainerClick(click);
            if (packet != null) sent.add(packet);
            request.future().complete(true);
            return packet;
        }

        @Override public boolean enabledSetting() { return true; }
        @Override void startEating() {
            meals++;
            try {
                var eating = AutoEat.class.getDeclaredField("isEating");
                eating.setAccessible(true);
                eating.setBoolean(this, true);
                var delay = AutoEat.class.getDeclaredField("delay");
                delay.setAccessible(true);
                delay.setInt(this, 50);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
    }
}
