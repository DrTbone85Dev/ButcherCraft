package com.butchercraft.world.checkpoint;

public record WorldIdentityRootReference(
        String identity,
        int schemaVersion,
        String rootDigest
) implements Comparable<WorldIdentityRootReference> {
    public WorldIdentityRootReference {
        identity = CheckpointValidation.id(identity, "worldIdentityRoot");
        schemaVersion = CheckpointValidation.positive(schemaVersion, "schemaVersion");
        rootDigest = CheckpointValidation.digest(rootDigest, "rootDigest");
    }

    @Override
    public int compareTo(WorldIdentityRootReference other) {
        int identityComparison = identity.compareTo(other.identity);
        if (identityComparison != 0) {
            return identityComparison;
        }
        int schemaComparison = Integer.compare(schemaVersion, other.schemaVersion);
        if (schemaComparison != 0) {
            return schemaComparison;
        }
        return rootDigest.compareTo(other.rootDigest);
    }
}
