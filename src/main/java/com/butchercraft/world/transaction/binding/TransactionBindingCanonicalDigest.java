package com.butchercraft.world.transaction.binding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

final class TransactionBindingCanonicalDigest {
    private final MessageDigest digest;

    private TransactionBindingCanonicalDigest(String domain) {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        add(domain);
    }

    static TransactionBindingCanonicalDigest create(String domain) {
        return new TransactionBindingCanonicalDigest(TransactionBindingValidation.id(domain, "digestDomain"));
    }

    TransactionBindingCanonicalDigest add(String value) {
        byte[] bytes = Objects.requireNonNull(value, "digestValue").getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 0);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ':');
        digest.update(bytes);
        return this;
    }

    TransactionBindingCanonicalDigest add(int value) {
        return add(Integer.toString(value));
    }

    TransactionBindingCanonicalDigest add(long value) {
        return add(Long.toString(value));
    }

    TransactionBindingCanonicalDigest add(boolean value) {
        return add(Boolean.toString(value));
    }

    String finish() {
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }
}
