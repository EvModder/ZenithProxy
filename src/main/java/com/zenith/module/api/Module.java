package com.zenith.module.api;

import com.github.rfresh2.EventConsumer;
import com.zenith.network.codec.PacketCodecRegistries;
import com.zenith.network.codec.PacketHandlerCodec;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.zenith.Globals.EVENT_BUS;

/**
 * Module system base class.
 */
@Getter
public abstract class Module extends ModuleUtils {
    boolean enabled = false;
    @Nullable PacketHandlerCodec clientPacketHandlerCodec = null;
    @Nullable PacketHandlerCodec serverPacketHandlerCodec = null;

    public Module() {}

    public synchronized void enable() {
        try {
            if (!enabled) {
                subscribeEvents();
                enabled = true;
                clientPacketHandlerCodec = registerClientPacketHandlerCodec();
                if (clientPacketHandlerCodec != null) {
                    PacketCodecRegistries.CLIENT_REGISTRY.register(clientPacketHandlerCodec);
                }
                serverPacketHandlerCodec = registerServerPacketHandlerCodec();
                if (serverPacketHandlerCodec != null) {
                    PacketCodecRegistries.SERVER_REGISTRY.register(serverPacketHandlerCodec);
                }
                onEnable();
                debug("Enabled");
            }
        } catch (Exception e) {
            error("Error enabling module", e);
            disable();
        }
    }

    public synchronized void disable() {
        try {
            if (enabled) {
                enabled = false;
                unsubscribeEvents();
                if (clientPacketHandlerCodec != null) {
                    PacketCodecRegistries.CLIENT_REGISTRY.unregister(clientPacketHandlerCodec);
                }
                if (serverPacketHandlerCodec != null) {
                    PacketCodecRegistries.SERVER_REGISTRY.unregister(serverPacketHandlerCodec);
                }
                onDisable();
                debug("Disabled");
            }
        } catch (Exception e) {
            error("Error disabling module", e);
        }
    }

    public synchronized void setEnabled(boolean enabled) {
        if (enabled) {
            enable();
        } else {
            disable();
        }
    }

    public abstract boolean enabledSetting();

    public synchronized void syncEnabledFromConfig() {
        setEnabled(enabledSetting());
    }

    public void onEnable() { }

    public void onDisable() { }

    public void subscribeEvents() {
        EVENT_BUS.subscribe(this, registerEvents().toArray(new EventConsumer[0]));
    }

    public List<EventConsumer<?>> registerEvents() {
        return Collections.emptyList();
    }

    public void unsubscribeEvents() {
        EVENT_BUS.unsubscribe(this);
    }

    public @Nullable PacketHandlerCodec registerClientPacketHandlerCodec() {
        return null;
    }

    public @Nullable PacketHandlerCodec registerServerPacketHandlerCodec() {
        return null;
    }
}
