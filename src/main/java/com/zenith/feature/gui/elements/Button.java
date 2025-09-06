package com.zenith.feature.gui.elements;

import com.zenith.feature.gui.ContainerClick;
import com.zenith.feature.gui.Gui;
import com.zenith.feature.gui.Page;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jspecify.annotations.NullMarked;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NullMarked
public class Button extends ItemSlot {
    private final ButtonClickHandler onClick;

    public Button(ItemStack item, ButtonClickHandler onClick) {
        super(item);
        this.onClick = onClick;
    }

    public Button(ItemStack item, ButtonClickHandler onClick, ItemSlotTickHandler tickHandler) {
        super(item, tickHandler);
        this.onClick = onClick;
    }

    public void click(final Gui gui, final Page page, final ContainerClick containerClick) {
        onClick.accept(this, gui, page, containerClick);
    }
}
