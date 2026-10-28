package com.zenith.module.impl;

import com.github.rfresh2.EventConsumer;
import com.zenith.Proxy;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.module.*;
import com.zenith.event.player.PlayerDisconnectedEvent;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.module.api.Module;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import com.zenith.network.codec.PacketHandlerCodec;
import com.zenith.network.codec.PacketHandlerStateCodec;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EntityEvent;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundDamageEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;

import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

public class AutoDisconnect extends Module {
    public static final String AUTODISCONNECT_REASON_PREFIX = "[AutoDisconnect] ";
    private volatile ClientSession pendingTotemSession;
    private ClientSession healthSession;
    private float lastHealth = Float.NaN;
    private boolean onlineHealthSeen;

    public AutoDisconnect() {
        super();
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(WeatherChangeEvent.class, this::handleWeatherChangeEvent),
            of(PlayerDisconnectedEvent.class, this::handleProxyClientDisconnectedEvent),
            of(ServerPlayerInVisualRangeEvent.class, this::handleNewPlayerInVisualRangeEvent),
            of(AutoReconnectDecisionEvent.class, this::handleAutoReconnectDecisionEvent)
        );
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.utility.actions.autoDisconnect.enabled;
    }

    boolean handleHealthPacket(ClientboundSetHealthPacket packet, ClientSession session) {
        if (session != Proxy.getInstance().getClient() || session.isDisconnecting() || session.isTerminalState()
            || !Float.isFinite(packet.getHealth())) return true;
        if (healthSession != session) {
            healthSession = session;
            lastHealth = Float.NaN;
            onlineHealthSeen = false;
        }
        float previousHealth = lastHealth;
        boolean hadOnlineHealth = onlineHealthSeen;
        lastHealth = packet.getHealth();
        onlineHealthSeen = session.isOnline() && !session.isInQueue();
        var config = CONFIG.client.extra.utility.actions.autoDisconnect;
        if (!enabledSetting() || !config.healthDisconnect) return true;
        if (config.healthDisconnectPaused) {
            // Queue/initial-login health is not evidence that the real player recovered or took damage.
            if (!onlineHealthSeen) return true;
            if (lastHealth <= config.health && (!hadOnlineHealth || lastHealth >= previousHealth)) return true;
            setHealthDisconnectPaused(false);
        }
        checkHealth(lastHealth);
        return true;
    }

    boolean handleDamagePacket(ClientboundDamageEventPacket packet, ClientSession session) {
        var config = CONFIG.client.extra.utility.actions.autoDisconnect;
        if (session != Proxy.getInstance().getClient() || session.isDisconnecting() || session.isTerminalState()
            || session.isInQueue() || packet.getEntityId() != CACHE.getPlayerCache().getEntityId()
            || !enabledSetting() || !config.healthDisconnect || !config.healthDisconnectPaused) return true;
        setHealthDisconnectPaused(false);
        // A damage packet can precede the health packet, or change only absorption hearts.
        if (healthSession == session && !Float.isNaN(lastHealth)) checkHealth(lastHealth);
        return true;
    }

    private void checkHealth(float health) {
        var config = CONFIG.client.extra.utility.actions.autoDisconnect;
        if (health <= config.health && playerConnectedCheck()) {
            if (!Proxy.getInstance().isPrio()) {
                setHealthDisconnectPaused(true);
            }
            info("Health: {} <= {}", health, config.health);
            EVENT_BUS.postAsync(new HealthAutoDisconnectEvent());
            doDisconnect("Health: " + health + " <= " + config.health);
        }
    }

    protected void setHealthDisconnectPaused(boolean paused) {
        CONFIG.client.extra.utility.actions.autoDisconnect.healthDisconnectPaused = paused;
        saveConfigAsync();
    }

    public void handleWeatherChangeEvent(final WeatherChangeEvent event) {
        if (!CONFIG.client.extra.utility.actions.autoDisconnect.thunder) return;
        if (CACHE.getChunkCache().isRaining()
            && CACHE.getChunkCache().getThunderStrength() > 0.0f
            && playerConnectedCheck()) {
            info("Thunder disconnect");
            doDisconnect("Thunder");
        }
    }

    public void handleProxyClientDisconnectedEvent(PlayerDisconnectedEvent event) {
        if (!CONFIG.client.extra.utility.actions.autoDisconnect.autoClientDisconnect) return;
        info("Auto Client Disconnect");
        doDisconnect("Auto Client Disconnect");
    }

    public void handleNewPlayerInVisualRangeEvent(ServerPlayerInVisualRangeEvent event) {
        if (!CONFIG.client.extra.utility.actions.autoDisconnect.onUnknownPlayerInVisualRange) return;
        var playerUUID = event.playerEntity().getUuid();
        if (PLAYER_LISTS.getFriendsList().contains(playerUUID)
            || PLAYER_LISTS.getWhitelist().contains(playerUUID)
            || PLAYER_LISTS.getSpectatorWhitelist().contains(playerUUID)
            || !playerConnectedCheck()
        ) return;
        info("Unknown Player: {} [{}]", event.playerEntry().getProfile());
        doDisconnect("Unknown Player: " + event.playerEntry().getProfile().getName());
    }

    @Override
    public PacketHandlerCodec registerClientPacketHandlerCodec() {
        return PacketHandlerCodec.clientBuilder().setId("autodisconnect").setPriority(-5)
            .state(ProtocolState.GAME, PacketHandlerStateCodec.clientBuilder()
                .inbound(ClientboundSetHealthPacket.class,
                    (ClientEventLoopPacketHandler<ClientboundSetHealthPacket, ClientSession>) this::handleHealthPacket)
                .inbound(ClientboundDamageEventPacket.class,
                    (ClientEventLoopPacketHandler<ClientboundDamageEventPacket, ClientSession>) this::handleDamagePacket)
                .inbound(ClientboundEntityEventPacket.class,
                    (ClientEventLoopPacketHandler<ClientboundEntityEventPacket, ClientSession>) this::handleTotemPacket)
                .inbound(ClientboundContainerSetSlotPacket.class, inventoryCheck())
                .inbound(ClientboundContainerSetContentPacket.class, inventoryCheck())
                .inbound(ClientboundSetPlayerInventoryPacket.class, inventoryCheck())
                .build()).build();
    }

    @Override
    public void onDisable() {
        pendingTotemSession = null;
        healthSession = null;
        lastHealth = Float.NaN;
        onlineHealthSeen = false;
    }

    boolean handleTotemPacket(ClientboundEntityEventPacket packet, ClientSession session) {
        if (packet.getEvent() == EntityEvent.TOTEM_OF_UNDYING_MAKE_SOUND
            && packet.getEntityId() == CACHE.getPlayerCache().getEntityId()
            && session == Proxy.getInstance().getClient()
            && enabledSetting() && CONFIG.client.extra.utility.actions.autoDisconnect.onTotemPop
            && playerConnectedCheck()) {
            pendingTotemSession = session;
            checkTotems(session);
        }
        return true;
    }

    private <P extends Packet> ClientEventLoopPacketHandler<P, ClientSession> inventoryCheck() {
        // Run after the standard handlers update the inventory cache on this same
        // event loop. A pop may arrive before or after its consumed-slot update.
        return (packet, session) -> {
            checkTotems(session);
            return true;
        };
    }

    void checkTotems(ClientSession session) {
        if (session == null || pendingTotemSession != session || session != Proxy.getInstance().getClient()) return;
        if (!enabledSetting() || !CONFIG.client.extra.utility.actions.autoDisconnect.onTotemPop || !playerConnectedCheck()) {
            pendingTotemSession = null;
            return;
        }
        int remaining = countTotems();
        if (remaining < CONFIG.client.extra.utility.actions.autoDisconnect.minTotemsRemaining) {
            pendingTotemSession = null;
            doDisconnect("Totem Pop - " + remaining + " remaining");
        }
    }

    private boolean playerConnectedCheck() {
        if (Proxy.getInstance().hasActivePlayer()) {
            var whilePlayerConnected = CONFIG.client.extra.utility.actions.autoDisconnect.whilePlayerConnected;
            if (!whilePlayerConnected)
                debug("Not disconnecting because a player is connected and whilePlayerConnected setting is disabled");
            return whilePlayerConnected;
        }
        return true;
    }

    protected void doDisconnect(String reason) {
        Proxy.getInstance().disconnect(AUTODISCONNECT_REASON_PREFIX + reason);
    }

    private void handleAutoReconnectDecisionEvent(AutoReconnectDecisionEvent event) {
        if (isAutoDisconnectReason(event.getDisconnectEvent().reason())
            && (CONFIG.client.extra.utility.actions.autoDisconnect.cancelAutoReconnect || Proxy.getInstance().isPrio())
        ) {
            event.setCancelled(true);
        }
    }

    public static boolean isAutoDisconnectReason(String reason) {
        return reason.startsWith(AUTODISCONNECT_REASON_PREFIX);
    }

    private int countTotems() {
        var count = 0;
        for (ItemStack item : CACHE.getPlayerCache().getPlayerInventory()) {
            if (item != Container.EMPTY_STACK && item.getId() == ItemRegistry.TOTEM_OF_UNDYING.id())
                count++;
        }
        return count;
    }
}
