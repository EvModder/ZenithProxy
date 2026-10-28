package com.zenith.module.impl;

import com.zenith.Proxy;
import com.zenith.cache.data.inventory.Container;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.network.client.ClientSession;
import com.zenith.network.client.handler.incoming.inventory.ContainerSetContentHandler;
import com.zenith.network.client.handler.incoming.inventory.ContainerSetSlotHandler;
import com.zenith.network.client.handler.incoming.inventory.SetPlayerInventoryHandler;
import com.zenith.network.server.ServerSession;
import com.zenith.plugin.DefaultGsonConfigSerializer;
import com.zenith.util.config.Config;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EntityEvent;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundDamageEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.*;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.zenith.Globals.*;
import static org.junit.jupiter.api.Assertions.*;

class AutoDisconnectTest {
    private final List<String> disconnects = new ArrayList<>();
    private final AutoDisconnect module = new AutoDisconnect() {
        @Override protected void doDisconnect(String reason) { disconnects.add(reason); }
        @Override protected void setHealthDisconnectPaused(boolean paused) {
            CONFIG.client.extra.utility.actions.autoDisconnect.healthDisconnectPaused = paused;
        }
    };
    private ClientSession session;
    private ClientSession previous;
    private Field clientField;
    private Field prioField;
    private Object previousPrioState;
    private final List<ClientSession> sessions = new ArrayList<>();
    private List<ItemStack> inventory;
    private boolean enabled, totemPop;
    private int minimum;
    private boolean healthEnabled, healthPaused, whileConnected, prio;
    private int healthThreshold;

    @BeforeEach
    void setup() throws Exception {
        var config = CONFIG.client.extra.utility.actions.autoDisconnect;
        enabled = config.enabled;
        totemPop = config.onTotemPop;
        minimum = config.minTotemsRemaining;
        healthEnabled = config.healthDisconnect;
        healthPaused = config.healthDisconnectPaused;
        whileConnected = config.whilePlayerConnected;
        healthThreshold = config.health;
        prio = CONFIG.authentication.prio;
        config.healthDisconnect = true;
        config.healthDisconnectPaused = false;
        config.whilePlayerConnected = false;
        config.health = 5;
        CONFIG.authentication.prio = false;
        prioField = Proxy.class.getDeclaredField("isPrio");
        prioField.setAccessible(true);
        previousPrioState = prioField.get(Proxy.getInstance());
        prioField.set(Proxy.getInstance(), Optional.empty());
        config.enabled = config.onTotemPop = true;
        config.minTotemsRemaining = 1;
        inventory = new ArrayList<>(CACHE.getPlayerCache().getPlayerInventory());
        for (int i = 0; i < inventory.size(); i++) setSlot(i, Container.EMPTY_STACK);
        clientField = Proxy.class.getDeclaredField("client");
        clientField.setAccessible(true);
        previous = (ClientSession) clientField.get(Proxy.getInstance());
        freshSession();
    }

    private void freshSession() throws Exception {
        var protocol = new MinecraftProtocol();
        protocol.setInboundState(ProtocolState.GAME);
        // Never connect: only exercise the local packet event loop and cache.
        session = new ClientSession("localhost", 25565, null, protocol, null) {
            @Override public boolean isOnline() { return !isInQueue(); }
        };
        sessions.add(session);
        clientField.set(Proxy.getInstance(), session);
    }

    @AfterEach
    void restore() throws Exception {
        for (var created : sessions) created.getClientEventLoop()
            .shutdownGracefully(0, 0, java.util.concurrent.TimeUnit.SECONDS).syncUninterruptibly();
        clientField.set(Proxy.getInstance(), previous);
        prioField.set(Proxy.getInstance(), previousPrioState);
        for (int i = 0; i < inventory.size(); i++) setSlot(i, inventory.get(i));
        var config = CONFIG.client.extra.utility.actions.autoDisconnect;
        config.enabled = enabled;
        config.onTotemPop = totemPop;
        config.minTotemsRemaining = minimum;
        config.healthDisconnect = healthEnabled;
        config.healthDisconnectPaused = healthPaused;
        config.whilePlayerConnected = whileConnected;
        config.health = healthThreshold;
        CONFIG.authentication.prio = prio;
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

    private void health(float value) {
        module.handleHealthPacket(new ClientboundSetHealthPacket(value, 20, 5), session);
    }

    private void damage(int entityId) {
        module.handleDamagePacket(new ClientboundDamageEventPacket(entityId, 1, -1, -1, null), session);
    }

    @Test void nonPriorityLogoutPausesAcrossReconnectUntilFreshHealthLoss() throws Exception {
        health(4);
        assertEquals(1, disconnects.size());
        assertTrue(CONFIG.client.extra.utility.actions.autoDisconnect.healthDisconnectPaused);
        module.onDisable();
        freshSession();
        health(1);
        health(1);
        health(3);
        assertEquals(1, disconnects.size());
        health(2);
        assertEquals(2, disconnects.size());
        assertTrue(CONFIG.client.extra.utility.actions.autoDisconnect.healthDisconnectPaused);
    }

    @Test void queueHealthCannotRearmAndRecoveryAboveThresholdCan() throws Exception {
        health(4);
        freshSession();
        session.setInQueue(true);
        health(20);
        damage(CACHE.getPlayerCache().getEntityId());
        assertTrue(CONFIG.client.extra.utility.actions.autoDisconnect.healthDisconnectPaused);
        session.setInQueue(false);
        health(1);
        assertEquals(1, disconnects.size());
        health(5);
        assertTrue(CONFIG.client.extra.utility.actions.autoDisconnect.healthDisconnectPaused);
        health(6);
        assertFalse(CONFIG.client.extra.utility.actions.autoDisconnect.healthDisconnectPaused);
        health(5);
        assertEquals(2, disconnects.size());
    }

    @Test void ownFreshDamageRearmsButOtherPlayersDamageDoesNot() throws Exception {
        health(4);
        freshSession();
        health(4);
        damage(CACHE.getPlayerCache().getEntityId() + 1);
        assertEquals(1, disconnects.size());
        damage(CACHE.getPlayerCache().getEntityId());
        assertEquals(2, disconnects.size());
    }

    @Test void damageBeforeInitialHealthRearmsWithoutUsingStaleCache() throws Exception {
        health(4);
        freshSession();
        damage(CACHE.getPlayerCache().getEntityId());
        assertEquals(1, disconnects.size());
        assertFalse(CONFIG.client.extra.utility.actions.autoDisconnect.healthDisconnectPaused);
        health(3);
        assertEquals(2, disconnects.size());
    }

    @Test void priorityLogoutsKeepNormalProtection() throws Exception {
        CONFIG.authentication.prio = true;
        health(4);
        assertFalse(CONFIG.client.extra.utility.actions.autoDisconnect.healthDisconnectPaused);
        freshSession();
        health(4);
        assertEquals(2, disconnects.size());
    }

    @Test void manualOffIsNeverAutomaticallyReenabled() {
        var config = CONFIG.client.extra.utility.actions.autoDisconnect;
        config.healthDisconnect = false;
        config.healthDisconnectPaused = true;
        health(20);
        health(1);
        damage(CACHE.getPlayerCache().getEntityId());
        assertFalse(config.healthDisconnect);
        assertTrue(disconnects.isEmpty());
    }

    @Test void controllerGateRemainsEffective() {
        var proxy = Proxy.getInstance();
        var previousPlayer = proxy.getCurrentPlayer().get();
        var controller = new ServerSession("localhost", 0, new MinecraftProtocol(), null) {
            @Override public boolean isLoggedIn() { return true; }
        };
        try {
            proxy.getCurrentPlayer().set(controller);
            health(1);
            assertTrue(disconnects.isEmpty());
            assertFalse(CONFIG.client.extra.utility.actions.autoDisconnect.healthDisconnectPaused);
            CONFIG.client.extra.utility.actions.autoDisconnect.whilePlayerConnected = true;
            health(1);
            assertEquals(1, disconnects.size());
        } finally {
            proxy.getCurrentPlayer().set(previousPlayer);
        }
    }

    @Test void pausedHealthNeverDisablesTotemProtection() {
        CONFIG.client.extra.utility.actions.autoDisconnect.healthDisconnectPaused = true;
        pop();
        assertEquals(List.of("Totem Pop - 0 remaining"), disconnects);
    }

    @Test void closingAndStaleSessionsCannotTriggerHealthChecks() throws Exception {
        var stale = session;
        freshSession();
        module.handleHealthPacket(new ClientboundSetHealthPacket(1, 20, 5), stale);
        session.setDisconnecting(true);
        health(1);
        assertTrue(disconnects.isEmpty());
    }

    @Test void automaticPauseSurvivesConfigSerialization() {
        var config = new Config();
        config.client.extra.utility.actions.autoDisconnect.healthDisconnectPaused = true;
        var writer = new StringWriter();
        DefaultGsonConfigSerializer.INSTANCE.write(config, writer);
        var restored = DefaultGsonConfigSerializer.INSTANCE.read(Config.class, new StringReader(writer.toString()));
        assertTrue(restored.client.extra.utility.actions.autoDisconnect.healthDisconnectPaused);
        assertTrue(restored.client.extra.utility.actions.autoDisconnect.healthDisconnect);
    }

    @Test void invalidHealthCannotRearmProtection() {
        CONFIG.client.extra.utility.actions.autoDisconnect.healthDisconnectPaused = true;
        health(Float.NaN);
        health(Float.POSITIVE_INFINITY);
        assertTrue(CONFIG.client.extra.utility.actions.autoDisconnect.healthDisconnectPaused);
        assertTrue(disconnects.isEmpty());
    }
}
