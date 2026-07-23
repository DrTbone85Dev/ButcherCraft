package com.butchercraft.world.allocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class AllocationCanonicalDigest {
    private final MessageDigest digest;

    private AllocationCanonicalDigest(String domain) {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        add(domain);
    }

    static AllocationCanonicalDigest create(String domain) {
        return new AllocationCanonicalDigest(
                AllocationValidation.id(domain, "digestDomain")
        );
    }

    AllocationCanonicalDigest add(String value) {
        byte[] bytes = AllocationValidation.required(value, "digestValue")
                .getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 0);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ':');
        digest.update(bytes);
        return this;
    }

    AllocationCanonicalDigest add(long value) {
        return add(Long.toString(value));
    }

    AllocationCanonicalDigest add(int value) {
        return add(Integer.toString(value));
    }

    AllocationCanonicalDigest add(boolean value) {
        return add(Boolean.toString(value));
    }

    String finish() {
        return HexFormat.of().formatHex(digest.digest());
    }

    static String validate(String value, String field) {
        String digest = AllocationValidation.required(value, field);
        if (!digest.matches("[0-9a-f]{64}")) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.INVALID_RESULT,
                    AllocationCycleFailureScope.CYCLE,
                    field,
                    field + " must be a lowercase SHA-256 digest"
            );
        }
        return digest;
    }
}
