package com.butchercraft.world.checkpoint;

public record PlatformDeterminismManifestReference(
        String identity,
        int schemaVersion,
        String manifestDigest
) implements Comparable<PlatformDeterminismManifestReference> {
    public PlatformDeterminismManifestReference {
        identity = CheckpointValidation.id(identity, "platformDeterminismManifestIdentity");
        schemaVersion = CheckpointValidation.positive(schemaVersion, "schemaVersion");
        manifestDigest = CheckpointValidation.digest(manifestDigest, "manifestDigest");
    }

    @Override
    public int compareTo(PlatformDeterminismManifestReference other) {
        int identityComparison = identity.compareTo(other.identity);
        if (identityComparison != 0) {
            return identityComparison;
        }
        int schemaComparison = Integer.compare(schemaVersion, other.schemaVersion);
        if (schemaComparison != 0) {
            return schemaComparison;
        }
        return manifestDigest.compareTo(other.manifestDigest);
    }
}
