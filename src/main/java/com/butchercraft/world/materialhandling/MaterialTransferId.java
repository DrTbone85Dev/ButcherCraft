package com.butchercraft.world.materialhandling;

import com.butchercraft.world.identity.WorldIdentityRootIdentity;

public record MaterialTransferId(String value) implements Comparable<MaterialTransferId> {
    private static final String LEGACY_PREFIX = "butchercraft:material_transfer/v1/";
    private static final String STACK_AWARE_PREFIX = "butchercraft:material_transfer/v2/";

    public MaterialTransferId {
        value = MaterialHandlingValidation.id(value, "material transfer identity");
        if (!value.startsWith(LEGACY_PREFIX) && !value.startsWith(STACK_AWARE_PREFIX)) {
            throw new IllegalArgumentException("Unsupported Material Transfer identity prefix");
        }
    }

    public static MaterialTransferId create(
            WorldIdentityRootIdentity worldIdentity,
            long sequence,
            String requestContentDigest,
            String configurationIdentity
    ) {
        MaterialHandlingValidation.positive(sequence, "material transfer sequence");
        MaterialHandlingValidation.digest(requestContentDigest, "request content digest");
        String digest = MaterialHandlingDigest.create("butchercraft:material_transfer")
                .add(MaterialHandlingSchema.CURRENT_VERSION)
                .add(worldIdentity.identity())
                .add(worldIdentity.schemaVersion())
                .add(worldIdentity.rootDigest())
                .add(sequence)
                .add(requestContentDigest)
                .add(configurationIdentity)
                .finish();
        return new MaterialTransferId(LEGACY_PREFIX + MaterialHandlingDigest.suffix(digest));
    }

    @Override
    public int compareTo(MaterialTransferId other) {
        return value.compareTo(other.value);
    }
}
