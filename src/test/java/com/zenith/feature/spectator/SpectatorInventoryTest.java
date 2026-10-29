package com.zenith.feature.spectator;

import com.zenith.Proxy;
import com.zenith.mc.item.ContainerTypeInfoRegistry;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.network.client.handler.incoming.inventory.ContainerSetContentHandler;
import com.zenith.network.client.handler.incoming.inventory.ContainerSetSlotHandler;
import com.zenith.network.client.handler.incoming.inventory.SetPlayerInventoryHandler;
import com.zenith.network.client.handler.postoutgoing.PostOutgoingSetCarriedItemHandler;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.PacketCodecRegistries;
import com.zenith.network.server.ServerSession;
import com.zenith.util.config.Config;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.*;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHeldSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.*;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.zenith.Globals.*;
import static org.junit.jupiter.api.Assertions.*;

class SpectatorInventoryTest {
    private final TestSession spectator = new TestSession(true);
    private final TestSession controller = new TestSession(false);
    private final ItemStack sword = new ItemStack(ItemRegistry.NETHERITE_SWORD.id(), 1);
    private final ItemStack food = new ItemStack(ItemRegistry.GOLDEN_CARROT.id(), 64);
    private boolean previousSetting;
    private int previousHeld;
    private List<ItemStack> previousInventory;
    private ItemStack previousMouse;
    private ServerSession[] previousConnections;

    @BeforeEach void setup() {
        assertEquals(0, CACHE.getPlayerCache().getInventoryCache().getOpenContainerId());
        previousSetting = CONFIG.server.spectator.inventory;
        previousHeld = CACHE.getPlayerCache().getHeldItemSlot();
        previousInventory = new ArrayList<>(CACHE.getPlayerCache().getPlayerInventory());
        previousMouse = CACHE.getPlayerCache().getInventoryCache().getMouseStack();
        CACHE.getPlayerCache().getInventoryCache().reset();
        CONFIG.server.spectator.inventory = true;
        CACHE.getPlayerCache().setHeldItemSlot(3);
        slot(39, sword);
        slot(9, food);
        slot(5, new ItemStack(ItemRegistry.DIAMOND_HELMET.id(), 1));
        slot(45, new ItemStack(ItemRegistry.TOTEM_OF_UNDYING.id(), 1));
        var connections = Proxy.getInstance().getActiveConnections();
        previousConnections = connections.getArray();
        connections.clear();
        connections.add(spectator);
        connections.add(controller);
    }

    @AfterEach void restore() {
        CONFIG.server.spectator.inventory = previousSetting;
        var player = CACHE.getPlayerCache();
        player.getInventoryCache().reset();
        player.setInventory(0, previousInventory.toArray(ItemStack[]::new));
        player.getInventoryCache().setMouseStack(previousMouse);
        player.setHeldItemSlot(previousHeld);
        var connections = Proxy.getInstance().getActiveConnections();
        connections.clear();
        for (var connection : previousConnections) connections.add(connection);
    }

    private static void slot(int slot, ItemStack item) { CACHE.getPlayerCache().setInventorySlot(0, item, slot); }
    private static ClientboundContainerSetContentPacket content(List<Packet> packets) {
        return packets.stream().filter(ClientboundContainerSetContentPacket.class::isInstance)
            .map(ClientboundContainerSetContentPacket.class::cast).reduce((a, b) -> b).orElseThrow();
    }

    @Test void defaultsOnButDisabledViewIsEmpty() {
        assertTrue(new Config.Server.Spectator().inventory);
        CONFIG.server.spectator.inventory = false;
        SpectatorInventory.syncAll();
        assertTrue(spectator.sent.isEmpty());
        SpectatorInventory.sync(spectator);
        for (var item : content(spectator.sent).getItems()) assertNull(item);
    }

    @Test void initialViewIncludesInventoryArmorOffhandAndSelectedSlotWithoutCursor() {
        CACHE.getPlayerCache().getInventoryCache().setMouseStack(food);
        SpectatorInventory.sync(spectator);
        var view = content(spectator.sent);
        assertEquals(0, view.getContainerId());
        assertEquals(46, view.getItems().length);
        for (int i : new int[]{5, 9, 39, 45})
            assertEquals(CACHE.getPlayerCache().getPlayerInventory().get(i), view.getItems()[i]);
        assertNull(view.getCarriedItem());
        assertEquals(3, ((ClientboundSetHeldSlotPacket) spectator.sent.getLast()).getSlot());
        assertTrue(controller.sent.isEmpty());
    }

    @Test void queuedViewDoesNotShareMutableStackAmountsWithCache() {
        var view = content(SpectatorInventory.snapshot(true));
        assertNotSame(food, view.getItems()[9]);
        food.setAmount(1);
        assertEquals(64, view.getItems()[9].getAmount());
    }

    @Test void upstreamSlotBulkAndPlayerInventoryUpdatesRefreshTheView() {
        new ContainerSetSlotHandler().applyAsync(new ClientboundContainerSetSlotPacket(0, 1, 9, null), null);
        assertNull(content(spectator.sent).getItems()[9]);
        var items = new ItemStack[46];
        items[10] = food;
        new ContainerSetContentHandler().applyAsync(new ClientboundContainerSetContentPacket(0, 2, items, sword), null);
        assertEquals(food, content(spectator.sent).getItems()[10]);
        assertNull(content(spectator.sent).getCarriedItem());
        new SetPlayerInventoryHandler().applyAsync(new ClientboundSetPlayerInventoryPacket(0, sword), null);
        assertEquals(sword, content(spectator.sent).getItems()[36]);
        assertTrue(controller.sent.isEmpty());
    }

    @Test void botOrControllerHotbarChangesRefreshTheSelectedSlot() {
        new PostOutgoingSetCarriedItemHandler().applyAsync(new ServerboundSetCarriedItemPacket(7), null);
        assertEquals(7, ((ClientboundSetHeldSlotPacket) spectator.sent.getLast()).getSlot());
        assertTrue(controller.sent.isEmpty());
    }

    @Test void openMenusMapOnlyTheirPlayerSlotsNotTheirContainerContents() {
        for (var type : new ContainerType[]{ContainerType.GENERIC_9X3, ContainerType.CRAFTING, ContainerType.FURNACE}) {
            var inventory = CACHE.getPlayerCache().getInventoryCache();
            inventory.openContainer(4, type, Component.text("test"));
            var layout = ContainerTypeInfoRegistry.REGISTRY.get(type);
            var items = new ItemStack[layout.totalSlots()];
            items[0] = new ItemStack(ItemRegistry.DIAMOND.id(), 64);
            items[layout.topSlots()] = food;
            items[layout.topSlots() + 27] = sword;
            inventory.setInventory(4, items);
            var view = content(SpectatorInventory.snapshot(true));
            assertEquals(food, view.getItems()[9]);
            assertEquals(sword, view.getItems()[36]);
            assertEquals(ItemRegistry.DIAMOND_HELMET.id(), view.getItems()[5].getId());
            assertEquals(ItemRegistry.TOTEM_OF_UNDYING.id(), view.getItems()[45].getId());
            assertNull(view.getItems()[0]);
            assertEquals(0, view.getContainerId());
            inventory.closeContainer(4);
        }
    }

    @Test void togglingRefreshesExistingSpectatorsAndClearsWhenDisabled() {
        SpectatorInventory.refreshAll();
        assertEquals(food, content(spectator.sent).getItems()[9]);
        CONFIG.server.spectator.inventory = false;
        SpectatorInventory.refreshAll();
        for (var item : content(spectator.sent).getItems()) assertNull(item);
        assertTrue(controller.sent.isEmpty());
    }

    @Test void disconnectedCacheCanBeClearedWithoutLeavingOldItemsVisible() {
        SpectatorInventory.syncAll();
        CACHE.getPlayerCache().getInventoryCache().reset();
        SpectatorInventory.syncAll();
        for (var item : content(spectator.sent).getItems()) assertNull(item);
    }

    @Test void spectatorEditsAreBlockedAndResyncedWhileControllerEditsPassThrough() {
        List<Packet> edits = new ArrayList<>();
        edits.add(new ServerboundContainerClickPacket(0, 0, 9, ContainerActionType.CLICK_ITEM,
            ClickItemAction.LEFT_CLICK, food, new Int2ObjectArrayMap<>()));
        edits.add(new ServerboundSetCreativeModeSlotPacket((short) 9, sword));
        edits.add(new ServerboundSetCarriedItemPacket(7));
        for (var action : new PlayerAction[]{PlayerAction.DROP_ITEM, PlayerAction.DROP_ALL_ITEMS, PlayerAction.SWAP_ITEM_WITH_OFFHAND})
            edits.add(new ServerboundPlayerActionPacket(action, 0, 0, 0, Direction.DOWN, 0));
        for (boolean enabled : new boolean[]{false, true}) {
            CONFIG.server.spectator.inventory = enabled;
            for (var edit : edits) {
                spectator.sent.clear();
                assertNull(PacketCodecRegistries.SERVER_REGISTRY.handleInbound(edit, spectator));
                assertEquals(2, spectator.sent.size());
                assertEquals(food, CACHE.getPlayerCache().getPlayerInventory().get(9));
                assertEquals(3, CACHE.getPlayerCache().getHeldItemSlot());
                assertSame(edit, PacketCodecRegistries.SERVER_REGISTRY.handleInbound(edit, controller));
            }
        }
    }

    @Test void usePlaceAndContainerCloseCannotReachUpstreamFromASpectator() {
        for (Packet packet : List.of(new ServerboundUseItemPacket(Hand.MAIN_HAND, 0, 0, 0),
            new ServerboundUseItemOnPacket(0, 0, 0, Direction.UP, Hand.MAIN_HAND, 0, 0, 0, false, false, 0),
            new ServerboundContainerClosePacket(0))) {
            assertNull(PacketCodecRegistries.SERVER_REGISTRY.handleInbound(packet, spectator));
            assertSame(packet, PacketCodecRegistries.SERVER_REGISTRY.handleInbound(packet, controller));
        }
    }

    @Test void rawUpstreamInventoryAndCursorPacketsStayFiltered() {
        for (var type : List.of(ClientboundContainerSetContentPacket.class, ClientboundContainerSetSlotPacket.class,
            ClientboundSetPlayerInventoryPacket.class, ClientboundSetCursorItemPacket.class, ClientboundSetHeldSlotPacket.class))
            assertTrue(PacketCodecRegistries.SPECTATOR_PACKET_FILTER.contains(type));
    }

    @Test void refreshAndLoginSnapshotsRunOnTheClientEventLoop() throws Exception {
        var field = Proxy.class.getDeclaredField("client");
        field.setAccessible(true);
        var previous = field.get(Proxy.getInstance());
        var client = new ClientSession("localhost", 25565, null, new MinecraftProtocol(), null);
        var loop = client.getClientEventLoop();
        try {
            field.set(Proxy.getInstance(), client);
            SpectatorInventory.sync(spectator);
            loop.submit(() -> {}).syncUninterruptibly();
            assertTrue(loop.inEventLoop(spectator.lastSendThread));
            assertEquals(food, content(spectator.sent).getItems()[9]);
            CONFIG.server.spectator.inventory = false;
            SpectatorInventory.refreshAll();
            loop.submit(() -> {}).syncUninterruptibly();
            for (var item : content(spectator.sent).getItems()) assertNull(item);
        } finally {
            field.set(Proxy.getInstance(), previous);
            loop.shutdownGracefully(0, 0, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    private static class TestSession extends ServerSession {
        final List<Packet> sent = new ArrayList<>();
        final boolean spectator;
        Thread lastSendThread;
        TestSession(boolean spectator) {
            super("localhost", 0, new MinecraftProtocol(), null);
            this.spectator = spectator;
            getPacketProtocol().setInboundState(ProtocolState.GAME);
        }
        @Override public boolean isSpectator() { return spectator; }
        @Override public boolean isLoggedIn() { return true; }
        @Override public void sendAsync(Packet packet) {
            lastSendThread = Thread.currentThread();
            sent.add(packet);
        }
    }
}
