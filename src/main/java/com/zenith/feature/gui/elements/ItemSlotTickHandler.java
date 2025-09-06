package com.zenith.feature.gui.elements;

import com.zenith.feature.gui.Gui;
import com.zenith.feature.gui.Page;

@FunctionalInterface
public interface ItemSlotTickHandler {
    void tick(ItemSlot slot, Gui gui, Page page, int index);
}
