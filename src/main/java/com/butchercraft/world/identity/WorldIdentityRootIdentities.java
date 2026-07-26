package com.butchercraft.world.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class WorldIdentityRootIdentities {
    private static final String ROOT_ID_PREFIX = "butchercraft:world_identity/";

    private WorldIdentityRootIdentities() {
    }

    public static WorldIdentityRootIdentity from(WorldIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        return new WorldIdentityRootIdentity(
                ROOT_ID_PREFIX + identity.id(),
                identity.schemaVersion(),
                digest(identity)
        );
    }

    public static String digest(WorldIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        CanonicalDigest digest = new CanonicalDigest("butchercraft:world_identity/root_digest");
        digest.add(identity.schemaVersion())
                .add(identity.id())
                .add(identity.worldSeed())
                .add(identity.region().toString())
                .add(identity.counties().toString())
                .add(identity.commercialProperties().toString())
                .add(identity.businesses().toString())
                .add(identity.families().toString())
                .add(identity.historicalPersons().toString())
                .add(identity.ownershipEntities().toString())
                .add(identity.ownershipHistories().toString())
                .add(identity.supplyNetwork().toString());
        return digest.finish();
    }

    private static final class CanonicalDigest {
        private final MessageDigest digest;

        private CanonicalDigest(String domain) {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
            add(domain);
        }

        private CanonicalDigest add(String value) {
            byte[] bytes = Objects.requireNonNull(value, "digestValue").getBytes(StandardCharsets.UTF_8);
            digest.update((byte) 0);
            digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(bytes);
            return this;
        }

        private CanonicalDigest add(long value) {
            return add(Long.toString(value));
        }

        private CanonicalDigest add(int value) {
            return add(Integer.toString(value));
        }

        private String finish() {
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        }
    }
}
