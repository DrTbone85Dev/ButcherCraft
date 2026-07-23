package com.butchercraft.architecture.validation;

import java.util.List;
import java.util.Objects;

public record RegistryDescriptor(
        String id,
        OrderingPolicy orderingPolicy,
        List<RegistryEntryDescriptor> entries
) {
    public RegistryDescriptor {
        id = Objects.requireNonNull(id, "id").strip();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(orderingPolicy, "orderingPolicy");
        entries = Objects.requireNonNull(entries, "entries").stream()
                .map(entry -> Objects.requireNonNull(entry, "entry"))
                .toList();
    }
}
