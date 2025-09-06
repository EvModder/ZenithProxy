package com.zenith.feature.gui.elements;

import com.zenith.feature.gui.ContainerClick;
import com.zenith.feature.gui.Gui;
import com.zenith.feature.gui.Page;

@FunctionalInterface
public interface ButtonClickHandler {
    void accept(Button button, Gui gui, Page page, ContainerClick containerClick);
}
