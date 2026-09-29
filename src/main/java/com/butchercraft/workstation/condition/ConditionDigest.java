package com.butchercraft.workstation.condition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Length-prefixed, ordered encoding; never depends on presentation or object identity. */
public final class ConditionDigest {
    private final MessageDigest digest;

    public ConditionDigest(String domain) {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
        add(domain);
    }

    public ConditionDigest add(String value) {
        byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
        add(bytes.length);
        digest.update(bytes);
        return this;
    }

    public ConditionDigest add(long value) {
        for (int shift = 56; shift >= 0; shift -= 8) digest.update((byte) (value >>> shift));
        return this;
    }

    public ConditionDigest add(boolean value) {
        digest.update((byte) (value ? 1 : 0));
        return this;
    }

    public String finish() {
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    public static String text(String value, String field) {
        if (Objects.requireNonNull(value, field).isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public static String identity(String domain, String... fields) {
        ConditionDigest digest = new ConditionDigest(domain);
        for (String field : fields) digest.add(field);
        return domain + "/" + digest.finish().substring("sha256:".length());
    }
}
