package com.zenith.feature.spectator;

import com.zenith.Proxy;
import com.zenith.mc.item.ContainerTypeInfoRegistry;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHeldSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;

import java.util.List;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.CONFIG;

public final class SpectatorInventory {
    private SpectatorInventory() {}

    static List<Packet> snapshot(boolean visible) {
        var items = new ItemStack[46];
        int selected = 0;
        if (visible) {
            var player = CACHE.getPlayerCache();
            var inventory = player.getInventoryCache();
            var base = inventory.getPlayerInventory();
            for (int i = 0; i < items.length; i++) items[i] = base.getItemStack(i);
            var open = inventory.getOpenContainer();
            if (open.getContainerId() != 0) {
                // Open menus carry the freshest main/hotbar slots in their own slot numbering.
                var layout = ContainerTypeInfoRegistry.REGISTRY.get(open.getType());
                if (layout != null && open.getSize() == layout.totalSlots()) {
                    for (int i = 0; i < 36; i++) items[9 + i] = open.getItemStack(layout.topSlots() + i);
                }
            }
            for (int i = 0; i < items.length; i++) if (items[i] != null) items[i] = items[i].clone();
            selected = player.getHeldItemSlot();
        }
        // This is the spectator's local inventory, never the upstream menu or cursor.
        return List.of(new ClientboundContainerSetContentPacket(0, 0, items, null),
            new ClientboundSetHeldSlotPacket(selected));
    }

    public static void syncAll() {
        if (CONFIG.server.spectator.inventory) onClientThread(SpectatorInventory::broadcast);
    }

    private static void broadcast() {
        var spectators = Proxy.getInstance().getSpectatorConnections();
        if (spectators.isEmpty()) return;
        var packets = snapshot(CONFIG.server.spectator.inventory);
        for (var session : spectators) send(session, packets);
    }

    public static void sync(ServerSession session) {
        onClientThread(() -> {
            if (session.isSpectator() && session.isLoggedIn()) send(session, snapshot(CONFIG.server.spectator.inventory));
        });
    }

    private static void send(ServerSession session, List<Packet> packets) {
        if (!session.isSpectator() || !session.isLoggedIn()) return;
        packets.forEach(session::sendAsync);
    }

    public static void refreshAll() {
        onClientThread(SpectatorInventory::broadcast);
    }

    public static <P extends Packet> P rejectEdit(P packet, ServerSession session) {
        sync(session);
        return null;
    }

    private static void onClientThread(Runnable action) {
        var client = Proxy.getInstance().getClient();
        if (client == null || client.getClientEventLoop().inEventLoop()) action.run();
        else client.getClientEventLoop().execute(action);
    }
}
