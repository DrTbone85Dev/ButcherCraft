package com.butchercraft.world.simulation.scheduler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

final class SchedulerCanonicalDigest {
    private final StringBuilder builder;

    private SchedulerCanonicalDigest(String domain) {
        builder = new StringBuilder(Objects.requireNonNull(domain, "domain")).append('\n');
    }

    static SchedulerCanonicalDigest create(String domain) {
        return new SchedulerCanonicalDigest(domain);
    }

    SchedulerCanonicalDigest add(String value) {
        builder.append(Objects.requireNonNull(value, "value").length())
                .append(':')
                .append(value)
                .append('\n');
        return this;
    }

    SchedulerCanonicalDigest add(long value) {
        return add(Long.toString(value));
    }

    SchedulerCanonicalDigest add(int value) {
        return add(Integer.toString(value));
    }

    SchedulerCanonicalDigest add(boolean value) {
        return add(Boolean.toString(value));
    }

    String finish() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(builder.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest algorithm is unavailable", exception);
        }
    }
}
