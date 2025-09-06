package com.zenith.feature.gui.elements;

import com.zenith.feature.gui.Gui;
import com.zenith.feature.gui.Page;
import com.zenith.mc.item.ItemRegistry;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@EqualsAndHashCode
@ToString
@NullMarked
public class ItemSlot implements Slot {
    @Setter
    private @Nullable ItemStack item;
    private final ItemSlotTickHandler onTick;

    public ItemSlot(@Nullable ItemStack item) {
        this.item = item;
        this.onTick = emptyTickHandler;
    }

    public ItemSlot(@Nullable ItemStack item, final ItemSlotTickHandler onTick) {
        this.item = item;
        this.onTick = onTick;
    }

    @Override
    public @Nullable ItemStack item() {
        return item;
    }

    @Override
    public void tick(Gui gui, Page page, int index) {
        onTick.tick(this, gui, page, index);
    }

    public static ItemSlot empty() {
        return new ItemSlot(new ItemStack(ItemRegistry.AIR.id(), 1));
    }

    public static final ItemSlotTickHandler emptyTickHandler = (slot, gui, page, index) -> {};
}
