package com.zenith.event.module;

import com.zenith.cache.data.entity.EntityPlayer;
import com.zenith.Proxy;
import com.zenith.network.client.ClientSession;
import org.jspecify.annotations.Nullable;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;

public record VisualRangeLeaveEvent(PlayerListEntry playerEntry, EntityPlayer playerEntity, boolean isFriend,
                                    @Nullable ClientSession session) {
    public VisualRangeLeaveEvent(PlayerListEntry playerEntry, EntityPlayer playerEntity, boolean isFriend) {
        this(playerEntry, playerEntity, isFriend, Proxy.getInstance().getClient());
    }
}
