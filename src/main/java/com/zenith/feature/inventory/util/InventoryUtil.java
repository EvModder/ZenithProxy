package com.zenith.feature.inventory.util;

import com.zenith.cache.data.inventory.Container;
import com.zenith.mc.block.Block;
import com.zenith.mc.item.ItemData;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.mc.item.ToolTag;
import com.zenith.mc.item.ToolType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;

import static com.zenith.Globals.BOT;
import static com.zenith.Globals.CACHE;

public class InventoryUtil {
    private InventoryUtil() {}

    public static int findHotbarSlot(int preferredSlot) {
        var inventory = CACHE.getPlayerCache().getPlayerInventory();
        if (inventory.get(preferredSlot + 36) == Container.EMPTY_STACK) return preferredSlot;
        for (int i = 36; i <= 44; i++) {
            if (inventory.get(i) == Container.EMPTY_STACK) return i - 36;
        }
        return preferredSlot;
    }

    // returns -1 if not found
    // only searches player inventory
    public static int searchPlayerInventory(Predicate<ItemStack> predicate) {
        List<ItemStack> playerInventory = CACHE.getPlayerCache().getPlayerInventory();

        if (CACHE.getPlayerCache().getInventoryCache().getOpenContainer().getContainerId() == 0) {
            // first check offhand
            var offhandStack = playerInventory.get(45);
            if (offhandStack != Container.EMPTY_STACK && predicate.test(offhandStack)) {
                return 45;
            }
        }

        // then hotbar
        for (int i = 36; i <= 44; i++) {
            ItemStack itemStack = playerInventory.get(i);
            if (itemStack == Container.EMPTY_STACK) continue;
            if (predicate.test(itemStack)) {
                return i;
            }
        }

        // then main inventory
        for (int i = 9; i <= 35; i++) {
            ItemStack itemStack = playerInventory.get(i);
            if (itemStack == Container.EMPTY_STACK) continue;
            if (predicate.test(itemStack)) {
                return i;
            }
        }

        return -1;
    }

    // returns -1 if not found
    public static int searchOpenContainer(Predicate<ItemStack> predicate) {
        var container = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (container.getContainerId() == 0) return searchPlayerInventory(predicate);
        // first check hotbar
        for (int i = container.getSize() - 9; i < container.getSize(); i++) {
            ItemStack itemStack = container.getItemStack(i);
            if (itemStack == Container.EMPTY_STACK) continue;
            if (predicate.test(itemStack)) {
                return i;
            }
        }

        // then main container inventory
        for (int i = 0; i < container.getSize() - 9; i++) {
            ItemStack itemStack = container.getItemStack(i);
            if (itemStack == Container.EMPTY_STACK) continue;
            if (predicate.test(itemStack)) {
                return i;
            }
        }

        return -1;
    }

    // returns -1 if not found
    public static int bestToolAgainst(Block block) {
        int bestInd = -1;
        double bestSpeed = -1;
        var container = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        for (int i = container.getSize() - 1; i >= 0; i--) {
            ItemStack itemStack =  container.getItemStack(i);
            if (itemStack == Container.EMPTY_STACK) continue;
            ItemData itemData = ItemRegistry.REGISTRY.get(itemStack.getId());
            ToolTag toolTag = itemData.toolTag();
            if (toolTag == null) continue;
            if (toolTag.type() != ToolType.PICKAXE) continue;
            double speed = BOT.getInteractions().blockBreakSpeed(block, itemStack);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestInd = i;
            }
        }
        return bestInd;
    }
}
