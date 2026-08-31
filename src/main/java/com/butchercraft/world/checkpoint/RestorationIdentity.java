package com.butchercraft.world.checkpoint;

import java.util.Objects;
import java.util.Optional;

public record RestorationIdentity(String value) {
    public static final int SCHEMA_VERSION = 1;
    private static final String PREFIX = "butchercraft:checkpoint_restoration/v1/";

    public RestorationIdentity {
        value = CheckpointValidation.id(value, "restorationIdentity");
        if (!value.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Restoration Identity has unsupported prefix");
        }
    }

    public static RestorationIdentity derive(
            WorldIdentityRootReference world,
            CheckpointGenerationManifest manifest,
            CheckpointHeadRecord sourceHead,
            RestorationSource source,
            Optional<LegacySplitRecoveryResult> recoveryResult
    ) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(sourceHead, "sourceHead");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(recoveryResult, "recoveryResult");
        CheckpointCanonicalDigest digest = CheckpointCanonicalDigest.create(
                "butchercraft:checkpoint_restoration_identity"
        ).add(SCHEMA_VERSION)
                .add(world.identity())
                .add(world.schemaVersion())
                .add(world.rootDigest())
                .add(manifest.generationId().canonicalValue())
                .add(manifest.manifestDigest())
                .add(sourceHead.headSequence())
                .add(sourceHead.headRecordDigest())
                .add(source.name())
                .add(manifest.platformDeterminismManifest().identity())
                .add(manifest.platformDeterminismManifest().schemaVersion())
                .add(manifest.platformDeterminismManifest().manifestDigest())
                .add(recoveryResult.isPresent());
        recoveryResult.ifPresent(result -> digest
                .add(result.resultIdentity())
                .add(result.contentDigest())
                .add(result.recoveryIdentity().value()));
        return new RestorationIdentity(PREFIX + digest.finish().substring("sha256:".length()));
    }
}
