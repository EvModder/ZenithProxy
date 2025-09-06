package com.zenith.feature.gui;

import com.google.common.collect.Lists;
import com.zenith.feature.gui.elements.ItemSlot;
import com.zenith.feature.gui.elements.Slot;
import com.zenith.mc.item.ContainerTypeInfoRegistry;
import com.zenith.network.server.ServerSession;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@NullMarked
public class GuiBuilder {
    private final List<Page> pages = new ArrayList<>();
    private @Nullable ServerSession session;

    private GuiBuilder() {}

    public static GuiBuilder create() {
        return new GuiBuilder();
    }

    public static GuiPageBuilder createPage() {
        return GuiPageBuilder.create();
    }

    public GuiBuilder session(ServerSession session) {
        this.session = session;
        return this;
    }

    public GuiBuilder pages(List<Page> pages) {
        this.pages.clear();
        this.pages.addAll(pages);
        return this;
    }

    public GuiBuilder addPage(Page page) {
        this.pages.add(page);
        return this;
    }

    public GuiBuilder paginate(Component title, List<List<Slot>> contentsPerPage) {
        if (contentsPerPage.isEmpty()) {
            throw new IllegalArgumentException("Contents cannot be null or empty");
        }
        for (int pageIndex = 0; pageIndex < contentsPerPage.size(); pageIndex++) {
            final var contents = contentsPerPage.get(pageIndex);
            ContainerType containerType;
            int contentSize = contents.size();
            if (contentSize < 18) {
                containerType = ContainerType.GENERIC_9X3;
            } else {
                containerType = ContainerType.GENERIC_9X6;
            }

            // bottom row reserved for page forward/back button
            var totalSlots = ContainerTypeInfoRegistry.REGISTRY.get(containerType).topSlots();
            int slotsPerPage = ContainerTypeInfoRegistry.REGISTRY.get(containerType).topSlots() - 9;

            if (contents.size() > slotsPerPage) {
                throw new IllegalArgumentException("Contents size on page index " + pageIndex + " exceeds the maximum slots per page: " + slotsPerPage);
            }
            List<Slot> pageContents = new ArrayList<>(contents);
            for (int j = pageContents.size(); j < totalSlots; j++) {
                pageContents.add(ItemSlot.empty()); // Fill remaining slots with empty slots
            }
            GuiPageBuilder pageBuilder = GuiPageBuilder.create()
                .id("page_" + pageIndex)
                .containerType(containerType)
                .title(title.append(Component.text(" - Page " + (pageIndex + 1) + " / " + contentsPerPage.size())))
                .contents(pageContents)
                .slot(totalSlots - 7, SlotBuilder.create()
                    .playerHead("Previous Page",
                        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWRiZWIwZmJjNWU2NjAxZmU1MDQ3MDJjYWZmZGFhYzBlOGVhMTllMTFiY2FkZmJlZTBkMThjODlmNDZiYzFmZCJ9fX0=")
                    .previousPageButton()
                    .build())
                .slot(totalSlots - 3, SlotBuilder.create()
                    .playerHead("Next Page",
                        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWI5Y2VjYzIxZDY3MGMxZmYyNzc4MTc2MjI1ZTI4NTBlMmVlMmY3Y2Y1NDEzYmIxNTY2N2Q5OGRiYjNjZjhiNSJ9fX0=")
                    .nextPageButton()
                    .build());
            this.pages.add(pageBuilder.build());
        }

        return this;
    }

    public GuiBuilder paginateList(Component title, List<Slot> contents) {
        if (contents.isEmpty()) {
            throw new IllegalArgumentException("Contents cannot be null or empty");
        }
        // bottom row reserved for page forward/back button
        int slotsPerPage = 54 - 9;
        return paginate(title, Lists.partition(contents, slotsPerPage));
    }

    public Gui build() {
        Objects.requireNonNull(session, "Session cannot be null");
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("Must have at least one page");
        }
        return new Gui(pages, session);
    }

    public static class GuiPageBuilder {
        private @Nullable String id;
        private @Nullable ContainerType containerType;
        private @Nullable Component title;
        private List<Slot> contents = new ArrayList<>();

        public static GuiPageBuilder create() {
            return new GuiPageBuilder();
        }

        public GuiPageBuilder id(String id) {
            this.id = id;
            return this;
        }

        public GuiPageBuilder containerType(ContainerType containerType) {
            this.containerType = containerType;
            var type = ContainerTypeInfoRegistry.REGISTRY.get(containerType);
            var newContents = new ArrayList<Slot>(type.topSlots());
            for (int i = 0; i < type.topSlots(); i++) {
                if (i < contents.size()) {
                    newContents.add(contents.get(i));
                } else {
                    newContents.add(ItemSlot.empty()); // Fill remaining slots with empty slots
                }
            }
            this.contents = newContents;
            return this;
        }

        public GuiPageBuilder title(Component title) {
            this.title = title;
            return this;
        }

        public GuiPageBuilder contents(List<Slot> contents) {
            this.contents = contents;
            return this;
        }

        public GuiPageBuilder slotsRange(int indexMin, int indexMax, Slot slot) {
            if (indexMin < 0 || indexMax < 0 || indexMin >= contents.size() || indexMax >= contents.size()) {
                throw new IndexOutOfBoundsException("Index out of bounds for content: " + indexMin + ", " + indexMax);
            }
            if (indexMin > indexMax) {
                throw new IllegalArgumentException("indexMin must be less than or equal to indexMax");
            }
            for (int index = indexMin; index <= indexMax; index++) {
                slot(index, slot);
            }
            return this;
        }

        public GuiPageBuilder slot(int index, Slot slot) {
            if (index < 0 || index >= contents.size()) {
                throw new IndexOutOfBoundsException("Index out of bounds for content: " + index);
            }
            contents.set(index, slot);
            return this;
        }

        public Page build() {
            if (id == null || containerType == null || title == null) {
                throw new IllegalStateException("All fields must be set before building the GUI.");
            }
            int slotCount = ContainerTypeInfoRegistry.REGISTRY.get(containerType).topSlots();
            if (contents.size() != slotCount) {
                throw new IllegalArgumentException("Contents size must match the slot count for the container type: " + slotCount);
            }
            return new Page(containerType, title, contents);
        }
    }
}
