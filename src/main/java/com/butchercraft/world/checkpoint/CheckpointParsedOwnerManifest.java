package com.butchercraft.world.checkpoint;

record CheckpointParsedOwnerManifest(
        OwnerSnapshotDescriptor descriptor,
        long payloadLength,
        String payloadDigest
) {
    CheckpointParsedOwnerManifest {
        if (payloadLength < 0L) {
            throw new IllegalArgumentException("payloadLength must not be negative: " + payloadLength);
        }
        payloadDigest = CheckpointValidation.digest(payloadDigest, "payloadDigest");
    }
}
