package com.zenith.event.module;

import com.zenith.cache.data.entity.EntityPlayer;
import com.zenith.Proxy;
import com.zenith.network.client.ClientSession;
import org.jspecify.annotations.Nullable;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;

public record ServerPlayerLeftVisualRangeEvent(PlayerListEntry playerEntry, EntityPlayer playerEntity,
                                               @Nullable ClientSession session) {
    public ServerPlayerLeftVisualRangeEvent(PlayerListEntry playerEntry, EntityPlayer playerEntity) {
        this(playerEntry, playerEntity, Proxy.getInstance().getClient());
    }
}
