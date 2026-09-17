package com.zenith.module.impl;

import com.zenith.Proxy;
import com.zenith.cache.data.inventory.Container;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.network.client.ClientSession;
import com.zenith.network.client.handler.incoming.inventory.ContainerSetContentHandler;
import com.zenith.network.client.handler.incoming.inventory.ContainerSetSlotHandler;
import com.zenith.network.client.handler.incoming.inventory.SetPlayerInventoryHandler;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EntityEvent;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.*;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static com.zenith.Globals.*;
import static org.junit.jupiter.api.Assertions.*;

class AutoDisconnectTest {
    private final List<String> disconnects = new ArrayList<>();
    private final AutoDisconnect module = new AutoDisconnect() {
        @Override protected void doDisconnect(String reason) { disconnects.add(reason); }
    };
    private ClientSession session;
    private ClientSession previous;
    private Field clientField;
    private List<ItemStack> inventory;
    private boolean enabled, totemPop;
    private int minimum;

    @BeforeEach
    void setup() throws Exception {
        var config = CONFIG.client.extra.utility.actions.autoDisconnect;
        enabled = config.enabled;
        totemPop = config.onTotemPop;
        minimum = config.minTotemsRemaining;
        config.enabled = config.onTotemPop = true;
        config.minTotemsRemaining = 1;
        inventory = new ArrayList<>(CACHE.getPlayerCache().getPlayerInventory());
        for (int i = 0; i < inventory.size(); i++) setSlot(i, Container.EMPTY_STACK);
        var protocol = new MinecraftProtocol();
        protocol.setInboundState(ProtocolState.GAME);
        // Never connect: only exercise the local packet event loop and cache.
        session = new ClientSession("localhost", 25565, null, protocol, null);
        clientField = Proxy.class.getDeclaredField("client");
        clientField.setAccessible(true);
        previous = (ClientSession) clientField.get(Proxy.getInstance());
        clientField.set(Proxy.getInstance(), session);
    }

    @AfterEach
    void restore() throws Exception {
        session.getClientEventLoop().shutdownGracefully(0, 0, java.util.concurrent.TimeUnit.SECONDS).syncUninterruptibly();
        clientField.set(Proxy.getInstance(), previous);
        for (int i = 0; i < inventory.size(); i++) setSlot(i, inventory.get(i));
        var config = CONFIG.client.extra.utility.actions.autoDisconnect;
        config.enabled = enabled;
        config.onTotemPop = totemPop;
        config.minTotemsRemaining = minimum;
    }

    private void setSlot(int slot, ItemStack item) {
        CACHE.getPlayerCache().getInventoryCache().getPlayerInventory().setItemStack(slot, item);
    }

    private void pop() {
        module.registerClientPacketHandlerCodec().handleInbound(new ClientboundEntityEventPacket(
            CACHE.getPlayerCache().getEntityId(), EntityEvent.TOTEM_OF_UNDYING_MAKE_SOUND), session);
        session.getClientEventLoop().submit(() -> {}).syncUninterruptibly();
    }

    @Test void zeroTotemsDisconnectsInPopHandlerWithoutTimer() {
        pop();
        assertEquals(List.of("Totem Pop - 0 remaining"), disconnects);
    }

    @Test void popBeforeSlotUpdateDisconnectsOnThatUpdate() {
        setSlot(45, new ItemStack(ItemRegistry.TOTEM_OF_UNDYING.id(), 1));
        pop();
        assertTrue(disconnects.isEmpty());
        var packet = new ClientboundContainerSetSlotPacket(0, 1, 45, Container.EMPTY_STACK);
        new ContainerSetSlotHandler().apply(packet, session);
        module.registerClientPacketHandlerCodec().handleInbound(packet, session);
        session.getClientEventLoop().submit(() -> {}).syncUninterruptibly();
        assertEquals(List.of("Totem Pop - 0 remaining"), disconnects);
        module.checkTotems(session);
        assertEquals(1, disconnects.size());
    }

    @Test void playerInventoryUpdateAlsoTriggersWithoutTimer() {
        setSlot(45, new ItemStack(ItemRegistry.TOTEM_OF_UNDYING.id(), 1));
        pop();
        var packet = new ClientboundSetPlayerInventoryPacket(40, Container.EMPTY_STACK);
        new SetPlayerInventoryHandler().apply(packet, session);
        module.registerClientPacketHandlerCodec().handleInbound(packet, session);
        session.getClientEventLoop().submit(() -> {}).syncUninterruptibly();
        assertEquals(1, disconnects.size());
    }

    @Test void fullInventoryUpdateAlsoTriggersWithoutTimer() {
        setSlot(45, new ItemStack(ItemRegistry.TOTEM_OF_UNDYING.id(), 1));
        pop();
        var items = new ItemStack[inventory.size()];
        java.util.Arrays.fill(items, Container.EMPTY_STACK);
        var packet = new ClientboundContainerSetContentPacket(0, 1, items, Container.EMPTY_STACK);
        new ContainerSetContentHandler().apply(packet, session);
        module.registerClientPacketHandlerCodec().handleInbound(packet, session);
        session.getClientEventLoop().submit(() -> {}).syncUninterruptibly();
        assertEquals(1, disconnects.size());
    }

    @Test void preservesSpareTotemsAndIgnoresOtherPlayers() {
        module.handleTotemPacket(new ClientboundEntityEventPacket(CACHE.getPlayerCache().getEntityId() + 1,
            EntityEvent.TOTEM_OF_UNDYING_MAKE_SOUND), session);
        module.checkTotems(session);
        assertTrue(disconnects.isEmpty());
        setSlot(10, new ItemStack(ItemRegistry.TOTEM_OF_UNDYING.id(), 1));
        pop();
        assertTrue(disconnects.isEmpty());
    }

    @Test void disabledSettingAndStaleSessionCannotDisconnect() throws Exception {
        CONFIG.client.extra.utility.actions.autoDisconnect.onTotemPop = false;
        pop();
        assertTrue(disconnects.isEmpty());
        CONFIG.client.extra.utility.actions.autoDisconnect.onTotemPop = true;
        setSlot(45, new ItemStack(ItemRegistry.TOTEM_OF_UNDYING.id(), 1));
        pop();
        setSlot(45, Container.EMPTY_STACK);
        clientField.set(Proxy.getInstance(), null);
        module.checkTotems(session);
        assertTrue(disconnects.isEmpty());
        clientField.set(Proxy.getInstance(), session);
        module.onDisable();
        module.checkTotems(session);
        assertTrue(disconnects.isEmpty());
    }
}
