package com.butchercraft.development.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointSnapshotDigest;
import com.butchercraft.world.checkpoint.PlatformDeterminismManifestReference;

import java.nio.charset.StandardCharsets;

public final class DevelopmentPlatformDeterminismManifest {
    public static final String IDENTITY =
            "butchercraft:platform_determinism/development_checkpoint_invocation_v1";
    public static final int SCHEMA_VERSION = 1;

    private DevelopmentPlatformDeterminismManifest() {
    }

    public static PlatformDeterminismManifestReference currentReference() {
        return new PlatformDeterminismManifestReference(
                IDENTITY,
                SCHEMA_VERSION,
                CheckpointSnapshotDigest.sha256(canonicalBytes())
        );
    }

    public static String guaranteeDescription() {
        return "Development-only narrow Platform Determinism Manifest reference for Clock and Scheduler checkpoints";
    }

    private static byte[] canonicalBytes() {
        return String.join("\n",
                "identity=" + IDENTITY,
                "schemaVersion=" + SCHEMA_VERSION,
                "scope=development_checkpoint_invocation",
                "fullRuntimeCollector=false",
                "owners=butchercraft:simulation_clock,butchercraft:simulation_scheduler",
                "automaticActivation=false"
        ).getBytes(StandardCharsets.UTF_8);
    }
}
