package com.butchercraft.world.checkpoint;

final class CheckpointFilesystemDigest {
    private CheckpointFilesystemDigest() {
    }

    static String sha256(byte[] bytes) {
        return CheckpointSnapshotDigest.sha256(bytes);
    }
}
