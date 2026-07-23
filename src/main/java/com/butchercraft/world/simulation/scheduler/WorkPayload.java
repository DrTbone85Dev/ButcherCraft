package com.butchercraft.world.simulation.scheduler;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record WorkPayload(List<WorkPayloadEntry> entries) {
    private static final int MAXIMUM_ENTRIES = 64;
    private static final int MAXIMUM_CANONICAL_CHARACTERS = 8_192;
    private static final WorkPayload EMPTY = new WorkPayload(List.of());

    public WorkPayload {
        entries = Objects.requireNonNull(entries, "entries").stream()
                .map(entry -> Objects.requireNonNull(entry, "entry")).sorted().toList();
        if (entries.size() > MAXIMUM_ENTRIES) throw new IllegalArgumentException("Payload has too many entries");
        Set<String> keys = new HashSet<>();
        int characters = 0;
        for (WorkPayloadEntry entry : entries) {
            if (!keys.add(entry.key())) throw new IllegalArgumentException("Duplicate payload key: " + entry.key());
            characters = Math.addExact(characters, Math.addExact(entry.key().length(), entry.canonicalValue().length()));
        }
        if (characters > MAXIMUM_CANONICAL_CHARACTERS) {
            throw new IllegalArgumentException("Payload exceeds canonical size limit");
        }
        entries = List.copyOf(entries);
    }

    public static WorkPayload empty() { return EMPTY; }
    public Optional<WorkPayloadEntry> find(String key) {
        return entries.stream().filter(entry -> entry.key().equals(Objects.requireNonNull(key, "key"))).findFirst();
    }
}
