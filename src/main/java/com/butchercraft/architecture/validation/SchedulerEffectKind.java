package com.butchercraft.architecture.validation;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum SchedulerEffectKind {
    READ_ONLY,
    IDEMPOTENT,
    TRANSACTION_BACKED,
    NON_REPEATABLE;

    public static boolean isKnown(String token) {
        String normalized = token == null ? "" : token.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).anyMatch(kind -> kind.name().equals(normalized));
    }

    public static List<String> tokens() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
