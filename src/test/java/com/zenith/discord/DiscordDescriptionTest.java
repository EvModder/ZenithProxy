package com.zenith.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.ApplicationInfo;
import net.dv8tion.jda.api.managers.ApplicationManager;
import net.dv8tion.jda.api.requests.RestAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.zenith.Globals.*;
import static org.junit.jupiter.api.Assertions.*;

class DiscordDescriptionTest {
    private final DiscordBot bot = new DiscordBot();
    private final boolean previousManageDescription = CONFIG.discord.manageDescription;
    private final boolean previousManageNickname = CONFIG.discord.manageNickname;
    private final String previousUsername = CONFIG.authentication.username;

    @BeforeEach void configure() {
        CONFIG.discord.manageDescription = true;
        CONFIG.discord.manageNickname = false;
        CONFIG.authentication.username = "EvDoc";
    }

    @AfterEach void restore() {
        CONFIG.discord.manageDescription = previousManageDescription;
        CONFIG.discord.manageNickname = previousManageNickname;
        CONFIG.authentication.username = previousUsername;
        EVENT_BUS.unsubscribe(bot);
    }

    @Test void delayedRestCallsNeverBlockOrAccumulate() throws Exception {
        var session = new Session();
        use(session);
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(bot::updateBotInfo).get(1, TimeUnit.SECONDS);
            for (int i = 0; i < 100; i++) bot.updateBotInfo();
            assertEquals(1, session.reads);
            session.retrieve.complete(info("previous description"));
            assertEquals(1, session.writes);
            assertFalse(session.update.isDone());
            for (int i = 0; i < 100; i++) bot.updateBotInfo();
            assertEquals(1, session.reads);
            session.update.complete(null);
            bot.updateBotInfo();
            assertEquals(2, session.reads);
        }
    }

    @Test void failuresReleaseTheGate() throws Exception {
        var session = new Session();
        use(session);
        bot.updateBotInfo();
        session.retrieve.completeExceptionally(new IllegalStateException("test retrieval failure"));
        session.retrieve = new CompletableFuture<>();
        bot.updateBotInfo();
        assertEquals(2, session.reads);
        session.retrieve.complete(info("previous description"));
        session.update.completeExceptionally(new IllegalStateException("test update failure"));
        session.retrieve = new CompletableFuture<>();
        bot.updateBotInfo();
        assertEquals(3, session.reads);
        session.retrieve.complete(info("**Online**:\n  EvD "));
        session.submissionFailure = true;
        bot.updateBotInfo();
        assertEquals(4, session.reads);
        session.submissionFailure = false;
        bot.updateBotInfo();
        assertEquals(5, session.reads);
    }

    @Test void staleSessionCannotWriteOrReleaseNewSessionsGate() throws Exception {
        var old = new Session();
        use(old);
        bot.updateBotInfo();
        var current = new Session();
        use(current);
        bot.updateBotInfo();
        assertEquals(1, current.reads);
        old.retrieve.complete(info("old description"));
        assertEquals(0, old.writes);
        assertEquals(0, current.writes);
        bot.updateBotInfo();
        assertEquals(1, current.reads);
        current.retrieve.complete(info("current description"));
        assertEquals(1, current.writes);
        current.update.complete(null);
        bot.updateBotInfo();
        assertEquals(2, current.reads);
    }

    @Test void shutdownAndDisabledManagementDiscardPendingRetrieval() throws Exception {
        var session = new Session();
        use(session);
        bot.updateBotInfo();
        session.status = JDA.Status.SHUTTING_DOWN;
        session.retrieve.complete(info("previous description"));
        assertEquals(0, session.writes);
        session.status = JDA.Status.CONNECTED;
        session.retrieve = new CompletableFuture<>();
        bot.updateBotInfo();
        CONFIG.discord.manageDescription = false;
        session.retrieve.complete(info("previous description"));
        assertEquals(0, session.writes);
    }

    @Test void unchangedDescriptionSkipsWriteAndDirectSetterIsAsync() throws Exception {
        var session = new Session();
        use(session);
        session.retrieve.complete(info("**Online**:\n  EvD "));
        bot.updateBotInfo();
        assertEquals(0, session.writes);
        bot.updateBotInfo();
        assertEquals(2, session.reads);
        bot.setBotDescription("manual description");
        assertEquals(1, session.writes);
        assertFalse(session.update.isDone());
    }

    private void use(Session session) throws Exception {
        var field = DiscordBot.class.getDeclaredField("jda");
        field.setAccessible(true);
        field.set(bot, session.jda);
    }

    private static ApplicationInfo info(String description) {
        return stub(ApplicationInfo.class, (p, m, a) -> {
            if (m.getName().equals("getDescription")) return description;
            throw new AssertionError(m);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static final class Session {
        private CompletableFuture<ApplicationInfo> retrieve = new CompletableFuture<>();
        private final CompletableFuture<Void> update = new CompletableFuture<>();
        private int reads;
        private int writes;
        private boolean submissionFailure;
        private JDA.Status status = JDA.Status.CONNECTED;
        private final RestAction<?> retrieval = stub(RestAction.class, (p, m, a) -> {
            if (m.getName().equals("submit")) {
                if (submissionFailure) throw new IllegalStateException("test submission failure");
                return retrieve;
            }
            throw new AssertionError(m);
        });
        private final ApplicationManager manager = stub(ApplicationManager.class, (p, m, a) -> switch (m.getName()) {
            case "setDescription" -> { writes++; yield p; }
            case "submit" -> update;
            case "queue" -> {
                @SuppressWarnings("unchecked") var failure = (Consumer<Throwable>) a[1];
                update.whenComplete((ignored, error) -> { if (error != null) failure.accept(error); });
                yield null;
            }
            default -> throw new AssertionError(m);
        });
        private final JDA jda = stub(JDA.class, (p, m, a) -> switch (m.getName()) {
            case "getStatus" -> status;
            case "retrieveApplicationInfo" -> { reads++; yield retrieval; }
            case "getApplicationManager" -> manager;
            default -> throw new AssertionError(m);
        });
    }
}
