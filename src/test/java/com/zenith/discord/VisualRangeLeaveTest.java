package com.zenith.discord;

import com.zenith.Proxy;
import com.zenith.cache.data.entity.EntityPlayer;
import com.zenith.event.module.ServerPlayerLeftVisualRangeEvent;
import com.zenith.event.module.VisualRangeLeaveEvent;
import com.zenith.module.impl.VisualRange;
import com.zenith.network.client.ClientSession;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;
import static org.junit.jupiter.api.Assertions.*;

class VisualRangeLeaveTest {
    private final TestSession session = new TestSession();
    private final TestSession replacement = new TestSession();
    private final EntityPlayer player = new EntityPlayer();
    private final PlayerListEntry entry = new PlayerListEntry("observer", UUID.randomUUID());
    private Field clientField;
    private ClientSession previous;
    private boolean leaveAlert, ignoreFriends, replay;

    @BeforeEach void setup() throws Exception {
        clientField = Proxy.class.getDeclaredField("client");
        clientField.setAccessible(true);
        previous = (ClientSession) clientField.get(Proxy.getInstance());
        clientField.set(Proxy.getInstance(), session);
        player.setUuid(entry.getProfileId());
        var config = CONFIG.client.extra.visualRange;
        leaveAlert = config.leaveAlert;
        ignoreFriends = config.ignoreFriends;
        replay = config.replayRecording;
        config.leaveAlert = true;
        config.ignoreFriends = false;
        config.replayRecording = false;
    }

    @AfterEach void restore() throws Exception {
        EVENT_BUS.unsubscribe(this);
        clientField.set(Proxy.getInstance(), previous);
        for (var created : List.of(session, replacement)) created.getClientEventLoop()
            .shutdownGracefully(0, 0, java.util.concurrent.TimeUnit.SECONDS).syncUninterruptibly();
        var config = CONFIG.client.extra.visualRange;
        config.leaveAlert = leaveAlert;
        config.ignoreFriends = ignoreFriends;
        config.replayRecording = replay;
    }

    private VisualRangeLeaveEvent leave() {
        return new VisualRangeLeaveEvent(entry, player, false, session);
    }

    @Test void genuineLeaveKeepsItsSourceSessionAndRemainsNotifiable() {
        List<VisualRangeLeaveEvent> events = new ArrayList<>();
        EVENT_BUS.subscribe(this, of(VisualRangeLeaveEvent.class, events::add));
        new VisualRange().handlePlayerLeftVisualRangeEvent(new ServerPlayerLeftVisualRangeEvent(entry, player, session));
        assertEquals(1, events.size());
        assertSame(session, events.getFirst().session());
        assertTrue(NotificationEventListener.shouldNotifyVisualRangeLeave(events.getFirst()));
    }

    @Test void disconnectStartingBeforeDeliverySuppressesQueuedLeave() {
        var pending = leave();
        assertTrue(NotificationEventListener.shouldNotifyVisualRangeLeave(pending));
        session.callDisconnecting(Component.text("test logout"), null);
        assertTrue(session.isDisconnecting());
        assertFalse(NotificationEventListener.shouldNotifyVisualRangeLeave(pending));
    }

    @Test void oldSessionCannotNotifyAfterReconnect() throws Exception {
        var pending = leave();
        clientField.set(Proxy.getInstance(), replacement);
        assertFalse(NotificationEventListener.shouldNotifyVisualRangeLeave(pending));
        List<VisualRangeLeaveEvent> events = new ArrayList<>();
        EVENT_BUS.subscribe(this, of(VisualRangeLeaveEvent.class, events::add));
        new VisualRange().handlePlayerLeftVisualRangeEvent(new ServerPlayerLeftVisualRangeEvent(entry, player, session));
        assertTrue(events.isEmpty());
    }

    @Test void offlineClosedAndUnknownSessionsAreNotNotifiable() {
        session.online = false;
        assertFalse(NotificationEventListener.shouldNotifyVisualRangeLeave(leave()));
        session.online = true;
        session.connected = false;
        assertFalse(NotificationEventListener.shouldNotifyVisualRangeLeave(leave()));
        session.connected = true;
        session.setTerminalState(true);
        assertFalse(NotificationEventListener.shouldNotifyVisualRangeLeave(leave()));
        assertFalse(NotificationEventListener.shouldNotifyVisualRangeLeave(new VisualRangeLeaveEvent(entry, player, false, null)));
    }

    private static class TestSession extends ClientSession {
        boolean connected = true;
        boolean online = true;

        TestSession() { super("localhost", 25565, null, new MinecraftProtocol(), null); }
        @Override public boolean isConnected() { return connected; }
        @Override public boolean isOnline() { return online; }
    }
}
