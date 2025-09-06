package com.zenith.feature.gui;

import com.zenith.event.client.ClientDisconnectEvent;
import com.zenith.event.client.ClientTickEvent;
import com.zenith.event.player.PlayerConnectionRemovedEvent;
import com.zenith.network.codec.PacketCodecRegistries;
import com.zenith.network.codec.PacketHandlerCodec;
import com.zenith.network.codec.PacketHandlerStateCodec;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClosePacket;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.EVENT_BUS;
import static com.zenith.Globals.SERVER_LOG;

@NullMarked
public class InGameGuiManager {
    private final Map<ServerSession, Gui> openGuiMap = new ConcurrentHashMap<>();

    public InGameGuiManager() {
        EVENT_BUS.subscribe(this,
            of(ClientDisconnectEvent.class, e -> openGuiMap.clear()),
            of(PlayerConnectionRemovedEvent.class, e -> openGuiMap.remove(e.serverConnection())),
            of(ClientTickEvent.class, e -> openGuiMap.values().forEach(Gui::tick))
        );
        var codec = PacketHandlerCodec.serverBuilder()
            .setId("gui")
            .setPriority(5)
            .state(ProtocolState.GAME, PacketHandlerStateCodec.serverBuilder()
                .inbound(ServerboundContainerClickPacket.class, (p, s) -> {
                    var gui = openGuiMap.get(s);
                    if (gui == null) {
                        return p;
                    }
                    var containerClick = new ContainerClick(p.getSlot(), p.getActionType(), p.getActionParam());
                    gui.onClick(containerClick);
                    return null;
                })
                .inbound(ServerboundContainerClosePacket.class, (p, s) -> {
                    var gui = openGuiMap.get(s);
                    if (gui == null) {
                        return p;
                    }
                    close(gui);
                    return null;
                })
                .outbound(ClientboundLoginPacket.class, (p, s) -> {
                    openGuiMap.remove(s);
                    return p;
                })
                .outbound(ClientboundStartConfigurationPacket.class, (p, s) -> {
                    openGuiMap.remove(s);
                    return p;
                })
                .outbound(ClientboundRespawnPacket.class, (p, s) -> {
                    openGuiMap.remove(s);
                    return p;
                })
                .build())
            .build();
        PacketCodecRegistries.SERVER_REGISTRY.register(codec);
    }

    public void open(Gui gui) {
        SERVER_LOG.info("Opening GUI {} for: {}", gui.hashCode(), gui.session().getUsername());
        synchronized (this) {
            if (openGuiMap.containsKey(gui.session())) {
                close(gui.session());
            }
            openGuiMap.put(gui.session(), gui);
        }
        gui.open();
    }

    public synchronized void close(Gui gui) {
        synchronized (this) {
            var g = openGuiMap.get(gui.session()); // nullable
            if (g != gui) {
                SERVER_LOG.warn("Tried closing gui for player: {}, but gui: {} is not open", gui.session().getUsername(), gui.hashCode());
                return;
            }
            openGuiMap.remove(gui.session());
        }
        gui.onClose();
        SERVER_LOG.info("Closed GUI {} for: {}", gui.hashCode(), gui.session().getUsername());
    }

    public void close(ServerSession session) {
        var gui = openGuiMap.get(session);
        if (gui == null) return;
        close(gui);
    }

    public @Nullable Gui getOpenGui(ServerSession session) {
        return openGuiMap.get(session);
    }

    public List<Gui> getOpenGuis() {
        return new ArrayList<>(openGuiMap.values());
    }
}
