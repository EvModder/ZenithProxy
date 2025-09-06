package com.zenith.feature.gui;

import com.zenith.cache.data.inventory.Container;
import com.zenith.feature.gui.elements.Button;
import com.zenith.feature.gui.elements.Slot;
import com.zenith.mc.item.ContainerTypeInfoRegistry;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.*;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.zenith.Globals.CACHE;

@EqualsAndHashCode
@ToString
@NullMarked
public class Page {
    private final int containerId = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, -10);
    private final ContainerType type;
    @EqualsAndHashCode.Exclude private Component title;
    private final List<Slot> contents;
    @EqualsAndHashCode.Exclude private int stateId = 1000;
    @EqualsAndHashCode.Exclude private boolean stale = false;

    public Page(
        ContainerType type,
        Component title,
        List<Slot> contents
    ) {
        this.type = type;
        this.title = title;
        this.contents = contents;
        int slotCount = ContainerTypeInfoRegistry.REGISTRY.get(type).topSlots();
        if (contents.size() != slotCount) {
            throw new IllegalArgumentException("Contents size must match the slot count for the container type: " + slotCount);
        }
    }

    public void setContents(List<Slot> contents) {
        int slotCount = ContainerTypeInfoRegistry.REGISTRY.get(type).topSlots();
        if (contents.size() != slotCount) {
            throw new IllegalArgumentException("Contents size must match the slot count for the container type: " + slotCount);
        }
        this.contents.clear();
        this.contents.addAll(contents);
        stale = true;
    }

    public void setContent(int index, Slot slot) {
        if (index < 0 || index >= contents.size()) {
            throw new IndexOutOfBoundsException("Index out of bounds for content: " + index);
        }
        contents.set(index, slot);
        stale = true;
    }

    public ContainerType type() {
        return type;
    }

    public Component title() {
        return title;
    }

    public void setTitle(final Component title) {
        this.title = title;
        stale = true;
    }

    protected void open(final Gui gui) {
        gui.session().sendAsync(new ClientboundOpenScreenPacket(containerId, type, title));
        sendContents(gui);
        stale = false;
    }

    protected void sendContents(Gui gui) {
        var typeInfo = ContainerTypeInfoRegistry.REGISTRY.get(type);
        ItemStack[] contentsArray = new ItemStack[typeInfo.totalSlots()];
        for (int i = 0; i < typeInfo.topSlots(); i++) {
            contentsArray[i] = contents.get(i).item();
        }
        // fill rest with player inventory
        var playerInventory = CACHE.getPlayerCache().getPlayerInventory();
        int playerInvIndex = 9;
        for (int i = typeInfo.topSlots(); i < typeInfo.totalSlots(); i++) {
            contentsArray[i] = playerInventory.get(playerInvIndex++);
        }
        gui.session().sendAsync(new ClientboundContainerSetContentPacket(containerId, stateId++, contentsArray, null));
    }

    protected void tick(Gui gui) {
        for (int i = 0; i < contents.size(); i++) {
            var slot = contents.get(i);
            slot.tick(gui, this, i);
        }
        if (stale) {
            open(gui);
        }
    }

    protected void onClick(final Gui gui, final ContainerClick containerClick) {
        int index = containerClick.slot();
        if (index < contents.size() && index >= 0) {
            var slot = contents.get(index);
            if (slot instanceof Button button) {
                button.click(gui, this, containerClick);
            }
            gui.session().sendAsync(new ClientboundContainerSetSlotPacket(containerId, stateId++, index, slot.item()));
            gui.session().sendAsync(new ClientboundSetCursorItemPacket(Container.EMPTY_STACK));
        } else {
            sendContents(gui);
        }
    }

    public void setStale() {
        this.stale = true;
    }

    protected void close(final Gui gui) {
        // todo: page close consumer api
        gui.session().sendAsync(new ClientboundContainerClosePacket(containerId));
    }

    protected void onPageSwitch(final Gui gui) {
        // todo: page switch consumer api
    }
}
