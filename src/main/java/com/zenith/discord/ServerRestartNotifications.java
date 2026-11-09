package com.zenith.discord;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.zenith.Globals.DISCORD_LOG;

/** Coordinates restart notices across local proxies connected to the same server and Discord channel. */
final class ServerRestartNotifications {
    // Do not open/close another descriptor for a file this JVM already has locked.
    private static final Set<Path> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private final Supplier<Path> stateFile;
    private final Supplier<String> countdownExpression;
    private final Function<Embed, CompletableFuture<?>> send;
    private final LongSupplier clock;
    private long lastFailureLog;
    private String patternSource;
    private Pattern countdownPattern;

    ServerRestartNotifications(Supplier<Path> stateFile, Supplier<String> countdownExpression,
                               Function<Embed, CompletableFuture<?>> send, LongSupplier clock) {
        this.stateFile = stateFile;
        this.countdownExpression = countdownExpression;
        this.send = send;
        this.clock = clock;
    }

    static Path stateFile(String directory, String channelId, String host, int port) {
        Long.parseUnsignedLong(channelId);
        String normalizedHost = host.strip().toLowerCase(Locale.ROOT);
        if (normalizedHost.endsWith(".")) normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
        var serverId = UUID.nameUUIDFromBytes((normalizedHost + ":" + port).getBytes(StandardCharsets.UTF_8));
        var parent = directory.isBlank()
            ? Path.of(System.getProperty("java.io.tmpdir"), "zenithproxy-restarts-"
                + Integer.toUnsignedString(System.getProperty("user.name", "").hashCode()))
            : Path.of(directory);
        return parent.resolve(channelId + "-" + serverId + ".state");
    }

    void countdown(String message) {
        var pattern = countdownPattern();
        if (pattern == null || message.length() > 4096) return;
        var matcher = pattern.matcher(message.trim());
        if (!matcher.matches()) return;
        try {
            long remaining = Long.parseLong(matcher.group("remaining"));
            if (remaining <= 0 || remaining > 3600) return;
            var matchedUnit = matcher.group("unit");
            if (matchedUnit == null) return;
            var unit = matchedUnit.toLowerCase(Locale.ROOT);
            if (unit.equals("minute") || unit.equals("minutes")) remaining *= 60;
            else if (!unit.equals("second") && !unit.equals("seconds")) return;
            if (remaining > 3600) return;
            attempt(remaining, message);
        } catch (NumberFormatException ignored) {
            // Ignore malformed server input, including values too large for a long.
        } catch (IllegalArgumentException e) {
            logFailure(new IllegalArgumentException("Restart countdown pattern requires remaining and unit groups"));
        }
    }

    private synchronized Pattern countdownPattern() {
        String expression = countdownExpression.get();
        if (!java.util.Objects.equals(expression, patternSource)) {
            patternSource = expression;
            countdownPattern = null;
            try {
                if (expression != null && !expression.isBlank()) countdownPattern = Pattern.compile(expression);
            } catch (IllegalArgumentException e) {
                logFailure(new IllegalArgumentException("Invalid restart countdown pattern"));
            }
        }
        return countdownPattern;
    }

    void restarted() {
        attempt(-1, "Restart underway.");
    }

    void disconnected(String reason, String expression) {
        if (expression == null || expression.isBlank() || reason.length() > 4096) return;
        try {
            if (Pattern.compile(expression).matcher(reason.trim()).matches()) restarted();
        } catch (IllegalArgumentException e) {
            logFailure(new IllegalArgumentException("Invalid restart disconnect pattern"));
        }
    }

    private void attempt(long remaining, String message) {
        Path path = null;
        FileChannel channel = null;
        boolean owned = false;
        try {
            var configured = stateFile.get().toAbsolutePath().normalize();
            Files.createDirectories(configured.getParent());
            path = configured.getParent().toRealPath().resolve(configured.getFileName());
            if (!IN_FLIGHT.add(path)) return;
            owned = true;
            channel = FileChannel.open(path, Set.of(StandardOpenOption.CREATE, StandardOpenOption.READ,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS));
            var lock = channel.tryLock();
            if (lock == null) return;
            var state = read(channel);
            long now = clock.getAsLong();
            int stage = remaining < 0 ? 2 : remaining <= 60 ? 1 : 0;
            long deadline = state.deadline;
            if (remaining < 0) {
                // A queue transition alone is not proof of a restart: require a matching recent countdown.
                if (deadline == 0 || now < deadline - 10_000 || now > deadline + 120_000) return;
            } else {
                long announcedDeadline = now + remaining * 1000;
                if (deadline == 0 || now > deadline + 120_000 || announcedDeadline > deadline + 300_000) {
                    state = new State(announcedDeadline, -1);
                }
                deadline = state.deadline;
            }
            if (stage <= state.stage) return;
            var next = new State(deadline, stage);
            var embed = Embed.builder().title("Server Restart").errorColor()
                .description(stage == 1 ? "Restarting within 1 minute." : message);
            var pendingChannel = channel;
            var pendingPath = path;
            // Keep the OS lock until Discord acknowledges delivery; failures/crashes release it for a peer.
            send.apply(embed).whenComplete((ignored, failure) -> {
                try {
                    if (failure == null) write(pendingChannel, next);
                    else logFailure(failure);
                } catch (Exception e) {
                    logFailure(e);
                } finally {
                    close(pendingChannel);
                    IN_FLIGHT.remove(pendingPath);
                }
            });
            channel = null;
            owned = false;
        } catch (Exception e) {
            logFailure(e);
        } finally {
            close(channel);
            if (owned) IN_FLIGHT.remove(path);
        }
    }

    private record State(long deadline, int stage) { }

    private State read(FileChannel channel) throws IOException {
        if (channel.size() != 16) {
            if (channel.size() != 0) logFailure(new IOException("Resetting incomplete restart notification state"));
            return new State(0, -1);
        }
        var data = ByteBuffer.allocate(16);
        while (data.hasRemaining()) {
            if (channel.read(data) < 0) throw new IOException("Incomplete restart notification state");
        }
        data.flip();
        if (data.getInt() != 1) throw new IOException("Unknown restart notification state version");
        long deadline = data.getLong();
        int stage = data.getInt();
        if (deadline < 0 || stage < 0 || stage > 2) {
            logFailure(new IOException("Resetting invalid restart notification state"));
            return new State(0, -1);
        }
        return new State(deadline, stage);
    }

    private static void write(FileChannel channel, State state) throws IOException {
        var data = ByteBuffer.allocate(16).putInt(1).putLong(state.deadline).putInt(state.stage).flip();
        channel.position(0);
        while (data.hasRemaining()) channel.write(data);
        channel.truncate(16);
        channel.force(false);
    }

    private static void close(FileChannel channel) {
        if (channel == null) return;
        try {
            channel.close();
        } catch (IOException e) {
            DISCORD_LOG.warn("Could not close restart notification state", e);
        }
    }

    private synchronized void logFailure(Throwable failure) {
        long now = clock.getAsLong();
        if (lastFailureLog != 0 && now - lastFailureLog < 60_000) return;
        lastFailureLog = now;
        DISCORD_LOG.warn("Could not coordinate/send the shared restart notification: {}", failure.toString());
    }
}
