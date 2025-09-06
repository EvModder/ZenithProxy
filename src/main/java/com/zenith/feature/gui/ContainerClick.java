package com.zenith.feature.gui;

import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;

public record ContainerClick(int slot, ContainerActionType actionType, ContainerAction actionParam) {
    public boolean isLeftOrRightClick() {
        return actionType == ContainerActionType.CLICK_ITEM && actionParam instanceof ClickItemAction;
    }

    public boolean isLeftClick() {
        return actionType == ContainerActionType.CLICK_ITEM && actionParam instanceof ClickItemAction action && action == ClickItemAction.LEFT_CLICK;
    }

    public boolean isRightClick() {
        return actionType == ContainerActionType.CLICK_ITEM && actionParam instanceof ClickItemAction action && action == ClickItemAction.RIGHT_CLICK;
    }
}
