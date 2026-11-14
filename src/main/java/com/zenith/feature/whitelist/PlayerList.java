package com.zenith.feature.whitelist;

import org.geysermc.mcprotocollib.auth.GameProfile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.zenith.feature.whitelist.PlayerListsManager.createPlayerListEntry;

public record PlayerList(
    String name,
    ArrayList<PlayerEntry> entries // reference to list in Config
) {
    /** @param added false if the player was already on the list */
    public record AddResult(PlayerEntry entry, boolean added) {}

    // empty if the profile lookup failed
    public synchronized Optional<AddResult> addOrFind(final String username) {
        return createPlayerListEntry(username).map(entry -> {
            final boolean added = !contains(entry.getUuid());
            if (added) entries.add(entry);
            return new AddResult(entry, added);
        });
    }

    public synchronized Optional<PlayerEntry> add(final String username) {
        return addOrFind(username).map(AddResult::entry);
    }

    // returns true if the account was added. false if the account was already present
    public synchronized boolean add(final String username, final UUID uuid) {
        if (contains(uuid)) return false;
        entries.add(new PlayerEntry(username, uuid, Instant.now().getEpochSecond()));
        return true;
    }

    // returns true if the account was removed. false if the account was not present
    public synchronized boolean remove(final String username) {
        return this.entries.removeIf(entry -> entry.getUsername().equalsIgnoreCase(username));
    }

    public synchronized void remove(final UUID uuid) {
        this.entries.removeIf(entry -> entry.getUuid().equals(uuid));
    }

    public synchronized void clear() {
        entries.clear();
    }

    // todo: these lookups could be sped up with secondary hashmaps
    //  we'd have to be very careful to keep those in sync
    //  as is, this shouldn't be too impactful for the extra complexity. O(n) loops are generally pretty fast at small sizes

    public synchronized boolean contains(final GameProfile clientGameProfile) {
        for (int i = 0; i < entries.size(); i++) {
            final PlayerEntry entry = entries.get(i);
            if (entry.getUuid().equals(clientGameProfile.getId())) {
                // player is present
                // let's update their player profile
                entry.setLastRefreshed(Instant.now().getEpochSecond());
                entry.setUsername(clientGameProfile.getName());
                return true;
            }
        }
        return false;
    }

    public synchronized boolean contains(final UUID uuid) {
        for (int i = 0; i < entries.size(); i++) {
            final PlayerEntry entry = entries.get(i);
            if (Objects.equals(entry.getUuid(), uuid))
                return true;
        }
        return false;
    }

    public synchronized boolean contains(final String name) {
        for (int i = 0; i < entries.size(); i++) {
            final PlayerEntry entry = entries.get(i);
            if (name.equalsIgnoreCase(entry.getUsername()))
                return true;
        }
        return false;
    }
}
