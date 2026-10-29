package com.zenith.module.impl;

import com.github.rfresh2.EventConsumer;
import com.zenith.Proxy;
import com.zenith.event.client.ClientBotTick;
import com.zenith.event.module.AutoEatOutOfFoodEvent;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.InventoryAction;
import com.zenith.feature.inventory.actions.MoveToHotbarSlot;
import com.zenith.feature.inventory.actions.SetHeldItem;
import com.zenith.feature.inventory.util.InventoryUtil;
import com.zenith.feature.player.ClickTarget;
import com.zenith.feature.player.Input;
import com.zenith.feature.player.InputRequest;
import com.zenith.mc.food.FoodData;
import com.zenith.mc.food.FoodRegistry;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.util.RequestFuture;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

public class AutoEat extends AbstractInventoryModule {
    private int delay = 0;
    private Instant lastAutoEatOutOfFoodWarning = Instant.EPOCH;
    private boolean isEating = false;
    RequestFuture swapFuture = RequestFuture.rejected;
    private FoodSelection foodSelection;
    private int selectionGeneration;

    private record FoodSelection(int previousSlot, int foodSlot, int sourceSlot, ItemStack displaced, int foodId) {}

    public AutoEat() {
        super(HandRestriction.EITHER, 0);
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::handleClientTick),
            of(ClientBotTick.Starting.class, this::handleBotTickStarting),
            of(ClientBotTick.Stopped.class, this::handleBotTickStopped)
        );
    }

    @Override
    public int getPriority() {
        return Objects.requireNonNullElse(CONFIG.client.extra.autoEat.priority, 11000);
    }

    public boolean isEating() {
        return enabledSetting() && isEating;
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.autoEat.enabled;
    }

    void handleClientTick(final ClientBotTick e) {
        if (CACHE.getPlayerCache().getThePlayer().isAlive()
            && CACHE.getPlayerCache().getGameMode() != GameMode.CREATIVE
            && CACHE.getPlayerCache().getGameMode() != GameMode.SPECTATOR
        ) {
            if (delay > 0) {
                delay--;
                if (isEating) {
                    INPUTS.submit(InputRequest.noInput(this, getPriority()));
                    INVENTORY.submit(InventoryActionRequest.noAction(this, getPriority()));
                }
                return;
            }
            if (!swapFuture.isDone()) {
                INPUTS.submit(InputRequest.noInput(this, getPriority()));
                return;
            }
            if (isEating) {
                if (restoreFoodSelection()) return;
                isEating = false;
            }
            if (!playerHealthBelowThreshold()) {
                restoreFoodSelection();
                return;
            }
            var invActionResult = doInventoryActionsV2();
            switch (invActionResult.state()) {
                case ITEM_IN_HAND -> {
                    delay = invActionResult.expectedDelay();
                    startEating(); // if accepted, will set delay to 50 (the eating duration ticks)
                    INVENTORY.submit(InventoryActionRequest.noAction(this, getPriority()));
                }
                case NO_ITEM -> {
                    if (CONFIG.client.extra.autoEat.warning
                        && Instant.now().minus(Duration.ofHours(7)).isAfter(lastAutoEatOutOfFoodWarning)
                        && InventoryUtil.searchPlayerInventory(this::hasFoodIgnoreHunger) == -1
                        && Proxy.getInstance().getOnlineTimeSeconds() > 1
                    ) {
                        warn("Out of food");
                        EVENT_BUS.postAsync(new AutoEatOutOfFoodEvent());
                        lastAutoEatOutOfFoodWarning = Instant.now();
                    }
                }
                case SWAPPING -> {
                    swapFuture = invActionResult.inventoryActionFuture();
                    INPUTS.submit(InputRequest.noInput(this, getPriority()));
                }
                default -> throw new IllegalStateException("Unexpected action state: " + invActionResult.state());
            }
        } else {
            isEating = false;
            delay = 0;
            foodSelection = null;
            selectionGeneration++;
        }
    }

    @Override
    public InventoryActionResult doInventoryActionsV2() {
        if (isItemEquipped())
            return new InventoryActionResult(ActionState.ITEM_IN_HAND, RequestFuture.rejected, 0);
        if (restoreFoodSelection())
            return new InventoryActionResult(ActionState.SWAPPING, swapFuture, 0);
        int source = InventoryUtil.searchPlayerInventory(this::hasFood);
        if (source < 9 || source > 44)
            return new InventoryActionResult(ActionState.NO_ITEM, RequestFuture.rejected, 0);
        var inventory = CACHE.getPlayerCache().getPlayerInventory();
        int previousSlot = CACHE.getPlayerCache().getHeldItemSlot();
        int foodSlot = source >= 36 ? source - 36 : previousSlot;
        var food = inventory.get(source);
        var displaced = inventory.get(foodSlot + 36);
        int generation = selectionGeneration;
        var action = source >= 36 ? new SetHeldItem(foodSlot)
            : new MoveToHotbarSlot(source, MoveToHotbarAction.from(foodSlot));
        var future = submitFoodAction(() -> {
            var currentInventory = CACHE.getPlayerCache().getPlayerInventory();
            if (generation != selectionGeneration || Proxy.getInstance().hasActivePlayer()
                || CACHE.getPlayerCache().getHeldItemSlot() != previousSlot
                || !Objects.equals(currentInventory.get(source), food)
                || !Objects.equals(currentInventory.get(foodSlot + 36), displaced)) return null;
            var packet = action.packet();
            if (packet != null) foodSelection = new FoodSelection(previousSlot, foodSlot, source, displaced, food.getId());
            return packet;
        });
        return new InventoryActionResult(ActionState.SWAPPING, future, 0);
    }

    boolean restoreFoodSelection() {
        var selection = foodSelection;
        if (selection == null) return false;
        INPUTS.submit(InputRequest.noInput(this, getPriority()));
        swapFuture = submitFoodAction(() -> {
            if (foodSelection != selection || Proxy.getInstance().hasActivePlayer()) return null;
            var inventory = CACHE.getPlayerCache().getPlayerInventory();
            InventoryAction action;
            if (selection.sourceSlot() >= 36) {
                if (CACHE.getPlayerCache().getHeldItemSlot() != selection.foodSlot()) {
                    foodSelection = null;
                    return null;
                }
                action = new SetHeldItem(selection.previousSlot());
            } else {
                var remaining = inventory.get(selection.foodSlot() + 36);
                if (remaining == null && selection.displaced() == null) {
                    foodSelection = null;
                    return null;
                }
                // Consumption may leave an empty slot, a bowl, or a bottle. Do not swap unrelated items.
                if (!Objects.equals(inventory.get(selection.sourceSlot()), selection.displaced())
                    || (remaining != null && remaining.getId() != selection.foodId()
                        && remaining.getId() != ItemRegistry.BOWL.id()
                        && remaining.getId() != ItemRegistry.GLASS_BOTTLE.id())) {
                    foodSelection = null;
                    return null;
                }
                action = new MoveToHotbarSlot(selection.sourceSlot(), MoveToHotbarAction.from(selection.foodSlot()));
            }
            var packet = action.packet();
            if (packet != null) foodSelection = null;
            return packet;
        });
        return true;
    }

    private RequestFuture submitFoodAction(Supplier<MinecraftPacket> packet) {
        return submitInventoryAction(new InventoryAction() {
            @Override public int containerId() { return 0; }
            @Override public MinecraftPacket packet() { return packet.get(); }
        });
    }

    RequestFuture submitInventoryAction(InventoryAction action) {
        return INVENTORY.submit(InventoryActionRequest.builder()
            .owner(this).actions(action).priority(getPriority()).build());
    }

    void startEating() {
        if (!isItemEquipped()) return;
        var hand = getHand();
        int generation = selectionGeneration;
        INPUTS.submit(InputRequest.builder()
                .owner(this)
                .input(Input.builder()
                    .rightClick(true)
                    .hand(hand)
                    .clickTarget(ClickTarget.None.INSTANCE)
                    .clickRequiresRotation(false)
                    .build())
                .priority(getPriority())
                .build())
            .addInputExecutedListener(future -> {
                if (generation != selectionGeneration) return;
                isEating = true;
                delay = 50;
            });
    }

    public void onEnable() {
        reset();
    }

    public void onDisable() {
        reset();
    }

    void handleBotTickStarting(final ClientBotTick.Starting event) {
        reset();
    }

    void handleBotTickStopped(final ClientBotTick.Stopped event) {
        reset();
    }

    void reset() {
        delay = 0;
        lastAutoEatOutOfFoodWarning = Instant.EPOCH;
        isEating = false;
        swapFuture = RequestFuture.rejected;
        foodSelection = null;
        selectionGeneration++;
    }

    boolean playerHealthBelowThreshold() {
        return CACHE.getPlayerCache().getThePlayer().getHealth() <= CONFIG.client.extra.autoEat.healthThreshold
            || CACHE.getPlayerCache().getThePlayer().getFood() <= CONFIG.client.extra.autoEat.hungerThreshold;
    }

    @Override
    public boolean itemPredicate(final ItemStack itemStack) {
        return hasFood(itemStack);
    }

    boolean hasFood(boolean ignoreHunger, ItemStack itemStack) {
        FoodData foodData = FoodRegistry.REGISTRY.get(itemStack.getId());
        if (foodData == null) return false;
        switch (CONFIG.client.extra.autoEat.mode) {
            case BLACKLIST -> {
                if (CONFIG.client.extra.autoEat.foods.contains(foodData.name())) {
                    return false;
                }
            }
            case WHITELIST -> {
                if (!CONFIG.client.extra.autoEat.foods.contains(foodData.name())) {
                    return false;
                }
            }
            case ALL -> {}
        }
        boolean canEat = ignoreHunger || CACHE.getPlayerCache().getThePlayer().getFood() < 20;
        return (CONFIG.client.extra.autoEat.allowUnsafeFood || foodData.isSafeFood())
            && (canEat || foodData.canAlwaysEat());
    }

    boolean hasFoodIgnoreHunger(ItemStack itemStack) {
        return hasFood(true, itemStack);
    }

    boolean hasFood(ItemStack itemStack) {
        return hasFood(false, itemStack);
    }
}
