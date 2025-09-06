package com.zenith.feature.gui;

import com.zenith.network.server.ServerSession;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NullMarked;

import java.util.List;

@NullMarked
@Data
@Accessors(fluent=true)
public class Gui {
    final List<Page> pages;
    final ServerSession session;
    @Setter(AccessLevel.PRIVATE) @EqualsAndHashCode.Exclude int activePageIndex;
    @Setter(AccessLevel.PRIVATE) @EqualsAndHashCode.Exclude Page activePage;
    @Setter(AccessLevel.PRIVATE) @EqualsAndHashCode.Exclude boolean active = false;

    public Gui(List<Page> pages, ServerSession session) {
        this.session = session;
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("Pages list cannot be empty");
        }
        this.pages = pages;
        this.activePageIndex = 0;
        this.activePage = pages.getFirst();
    }

    protected void open() {
        if (active) return;
        active = true;
        activePage.open(this);
    }

    public void setPage(int index) {
        if (index < 0 || index >= pages.size()) {
            throw new IndexOutOfBoundsException("Page index out of bounds: " + index);
        }
        activePage.onPageSwitch(this);
        activePageIndex = index;
        activePage = pages.get(activePageIndex);
        if (active) activePage.open(this);
    }

    public void setPage(Page page) {
        int index = pages.indexOf(page);
        if (index == -1) {
            throw new IllegalArgumentException("Page index not found: " + index);
        }
        setPage(index);
    }

    public void nextPage() {
        if (!active) return;
        int nextIndex = activePageIndex + 1;
        if (nextIndex < pages.size()) {
            setPage(nextIndex);
        }
    }

    public void previousPage() {
        if (!active) return;
        int prevIndex = activePageIndex - 1;
        if (prevIndex >= 0) {
            setPage(prevIndex);
        }
    }

    protected void tick() {
        if (!active) return;
        activePage.tick(this);
    }

    protected void onClick(final ContainerClick containerClick) {
        if (!active) return;
        activePage.onClick(this, containerClick);
    }

    protected void onClose() {
        if (!active) return;
        activePage.close(this);
        active = false;
    }
}
