package com.butchercraft.world.inventory.freshness;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

final class InventoryFreshnessCanonicalDigest {
    private final MessageDigest digest;

    private InventoryFreshnessCanonicalDigest(String domain) {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        add(domain);
    }

    static InventoryFreshnessCanonicalDigest create(String domain) {
        return new InventoryFreshnessCanonicalDigest(
                InventoryFreshnessValidation.id(domain, "digestDomain")
        );
    }

    InventoryFreshnessCanonicalDigest add(String value) {
        byte[] bytes = Objects.requireNonNull(value, "digestValue").getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 0);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ':');
        digest.update(bytes);
        return this;
    }

    InventoryFreshnessCanonicalDigest add(int value) {
        return add(Integer.toString(value));
    }

    InventoryFreshnessCanonicalDigest add(long value) {
        return add(Long.toString(value));
    }

    InventoryFreshnessCanonicalDigest add(boolean value) {
        return add(Boolean.toString(value));
    }

    String finish() {
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }
}
