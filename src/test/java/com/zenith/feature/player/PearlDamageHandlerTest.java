package com.zenith.feature.player;

import com.zenith.mc.Registry;
import com.zenith.mc.damage_type.*;
import com.zenith.network.client.ClientSession;
import com.zenith.network.client.handler.incoming.entity.DamageEventHandler;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundDamageEventPacket;
import org.junit.jupiter.api.Test;
import static com.zenith.Globals.CACHE;
import static org.junit.jupiter.api.Assertions.*;

class PearlDamageHandlerTest {
    @Test void requiresOwnEntityAndServerProvidedPearlDamageType() {
        var previous = DamageTypeRegistry.REGISTRY.getLoadedRegistry();
        var registry = new Registry<DamageType>(0);
        registry.register(new DamageType(73, "ender_pearl"));
        registry.register(new DamageType(34, "player_attack"));
        DamageTypeRegistry.REGISTRY.set(registry);
        var session = new ClientSession("localhost", 25565, null, new MinecraftProtocol(), null);
        try {
            var from = new PearlTeleportTracker.Position("overworld", 0, 64, 0);
            var to = new PearlTeleportTracker.Position("overworld", 1000, 64, 0);
            int self = CACHE.getPlayerCache().getEntityId();
            var handler = new DamageEventHandler();
            for (int mode = 0; mode < 4; mode++) {
                var tracker = session.getPearlTeleportTracker();
                tracker.reset();
                tracker.teleport(from, from, false, 256, true);
                if (mode == 3) DamageTypeRegistry.REGISTRY.set(new Registry<>(0));
                handler.applyAsync(new ClientboundDamageEventPacket(mode == 1 ? self + 1 : self,
                    mode == 2 ? 34 : 73, -1, -1, null), session);
                var result = tracker.teleport(from, to, false, 256, true);
                if (mode == 0) assertNotNull(result);
                else assertNull(result);
            }
        } finally {
            session.getClientEventLoop().shutdownGracefully(0, 0, java.util.concurrent.TimeUnit.SECONDS).syncUninterruptibly();
            DamageTypeRegistry.REGISTRY.set(previous);
        }
    }
}
