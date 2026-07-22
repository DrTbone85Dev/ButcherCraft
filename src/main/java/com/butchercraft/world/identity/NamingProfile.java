package com.butchercraft.world.identity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record NamingProfile(
        String id,
        String displayName,
        Map<NamingRole, List<String>> namesByRole
) {
    public NamingProfile {
        id = requireNonBlank(id, "id");
        displayName = requireNonBlank(displayName, "displayName");
        Objects.requireNonNull(namesByRole, "namesByRole");
        EnumMap<NamingRole, List<String>> copiedNames = new EnumMap<>(NamingRole.class);
        for (Map.Entry<NamingRole, List<String>> entry : namesByRole.entrySet()) {
            NamingRole role = Objects.requireNonNull(entry.getKey(), "role");
            List<String> names = List.copyOf(Objects.requireNonNull(entry.getValue(), "names"));
            if (names.isEmpty()) {
                throw new IllegalArgumentException("Naming profile " + id + " has no names for role " + role.serializedName());
            }
            for (String name : names) {
                requireNonBlank(name, "name");
            }
            copiedNames.put(role, names);
        }
        namesByRole = Map.copyOf(copiedNames);
    }

    public List<String> namesFor(NamingRole role) {
        Objects.requireNonNull(role, "role");
        List<String> names = namesByRole.get(role);
        if (names == null || names.isEmpty()) {
            throw new IllegalArgumentException("Naming profile " + id + " does not define role " + role.serializedName());
        }
        return names;
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Naming profile " + fieldName + " must not be blank");
        }
        return value;
    }
}
