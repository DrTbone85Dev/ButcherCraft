package com.butchercraft.workstation.projection;

import com.butchercraft.persistence.AtomicFilePublication;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.WorkstationInstanceLifecycle;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRecord;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRegistry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class WorkstationProjectionStorage {
    private static final String LABEL = "Workstation durable per-instance projection";
    private static final int MAXIMUM_CONCURRENT_PUBLICATION_ATTEMPTS = 8;

    private final Path rootDirectory;
    private final WorkstationProjectionCodec codec;

    public WorkstationProjectionStorage(Path rootDirectory) {
        this(rootDirectory, new WorkstationProjectionCodec());
    }

    public WorkstationProjectionStorage(Path rootDirectory, WorkstationProjectionCodec codec) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory").toAbsolutePath().normalize();
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public Path rootDirectory() {
        return rootDirectory;
    }

    public Path pathFor(WorkstationInstanceId instanceId) {
        return pathUnder(rootDirectory, instanceId);
    }

    public Path pathFor(DurableWorkstationProjection projection) {
        return projection.schemaVersion() == WorkstationProjectionSchema.LEGACY_VERSION
                ? legacyPath(projection.instanceId()) : pathFor(projection.instanceId());
    }

    private Path legacyPath(WorkstationInstanceId instanceId) {
        Path legacy = rootDirectory.getFileName().toString().equals(WorkstationProjectionSchema.SCHEMA_DIRECTORY_NAME)
                ? rootDirectory.resolveSibling(WorkstationProjectionSchema.LEGACY_SCHEMA_DIRECTORY_NAME) : rootDirectory;
        return pathUnder(legacy, instanceId);
    }

    private Path readPath(WorkstationInstanceId instanceId) {
        Path current = pathFor(instanceId);
        AtomicFilePublication.requireNoInterruptedPublication(current, LABEL);
        return Files.exists(current) ? current : legacyPath(instanceId);
    }

    private static Path pathUnder(Path root, WorkstationInstanceId instanceId) {
        String hash = sha256(Objects.requireNonNull(instanceId, "instanceId").value());
        Path path = root.resolve(hash.substring(0, 2)).resolve(hash.substring(2, 4))
                .resolve(hash + ".json").toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new IllegalStateException("Projection path escaped Workstation-owned persistence root");
        }
        return path;
    }

    public FrozenWorkstationProjectionSnapshot save(DurableWorkstationProjection projection) {
        byte[] frozen = codec.freeze(Objects.requireNonNull(projection, "projection"));
        if (frozen.length > WorkstationProjectionSchema.MAXIMUM_RECORD_BYTES) {
            throw new IllegalStateException("Durable Workstation projection exceeds bounded record size");
        }
        Path target = pathFor(projection);
        for (int attempt = 1; attempt <= MAXIMUM_CONCURRENT_PUBLICATION_ATTEMPTS; attempt++) {
            AtomicFilePublication.requireNoInterruptedPublication(target, LABEL);
            Optional<byte[]> currentBytes = Files.exists(target)
                    ? Optional.of(AtomicFilePublication.readBytes(target, LABEL))
                    : Optional.empty();
            Path baselinePath = readPath(projection.instanceId());
            Optional<byte[]> baseline = Files.exists(baselinePath)
                    ? Optional.of(AtomicFilePublication.readBytes(baselinePath, LABEL)) : Optional.empty();
            if (baseline.isPresent()) {
                DurableWorkstationProjection current = codec.decode(baseline.orElseThrow());
                if (!current.instanceId().equals(projection.instanceId())) {
                    throw new IllegalStateException("Projection target contains another Workstation Instance Identity");
                }
                if (current.schemaVersion() > projection.schemaVersion()) {
                    throw new IllegalStateException("Durable Workstation projection schema cannot regress");
                }
                current.condition().ifPresent(prior -> {
                    var next = projection.condition().orElseThrow();
                    if (prior.applicability() != next.applicability()) {
                        throw new IllegalStateException("Condition applicability cannot change for the same instance");
                    }
                    prior.state().ifPresent(before -> {
                        var after = next.state().orElseThrow();
                        if (after.revision() < before.revision()
                                || after.effectCount() < before.effectCount()
                                || after.lastAccountedTick() < before.lastAccountedTick()
                                || !after.initializationEvidence().equals(before.initializationEvidence())
                                || (after.revision() == before.revision() && !after.equals(before))) {
                            throw new IllegalStateException("Condition revision or immutable initialization conflicts");
                        }
                    });
                });
                if (current.schemaVersion() == WorkstationProjectionSchema.LEGACY_VERSION
                        && projection.schemaVersion() == WorkstationProjectionSchema.CURRENT_VERSION) {
                    preserveLegacySource(current, baseline.orElseThrow());
                }
                if (current.projectionRevision() > projection.projectionRevision()) {
                    throw new IllegalStateException("Durable Workstation projection revision cannot regress");
                }
                if (current.projectionRevision() == projection.projectionRevision()) {
                    if (current.equals(projection)) return frozen(projection, frozen);
                    throw new IllegalStateException(
                            "Durable Workstation projection revision conflicts with different state");
                }
            }
            try {
                AtomicFilePublication.publishBytesIfDigestMatches(
                        target,
                        currentBytes.map(WorkstationProjectionStorage::publicationDigest),
                        frozen,
                        LABEL
                );
                DurableWorkstationProjection verified = codec.decode(
                        AtomicFilePublication.readBytes(target, LABEL));
                if (!verified.equals(projection)) {
                    throw new IllegalStateException(
                            "Durable Workstation projection failed semantic read-back verification");
                }
                return frozen(projection, frozen);
            } catch (IllegalStateException concurrentAdvance) {
                if (attempt == MAXIMUM_CONCURRENT_PUBLICATION_ATTEMPTS) {
                    throw new IllegalStateException(
                            "Durable Workstation projection publication did not stabilize", concurrentAdvance);
                }
            }
        }
        throw new IllegalStateException("Unreachable durable Workstation projection publication state");
    }

    public WorkstationProjectionReadResult read(WorkstationInstanceRecord expected) {
        Objects.requireNonNull(expected, "expected");
        Path path = readPath(expected.instanceId());
        try {
            AtomicFilePublication.requireNoInterruptedPublication(path, LABEL);
            if (!Files.exists(path)) {
                return WorkstationProjectionReadResult.unavailable(
                        expected.instanceId(),
                        expected.lifecycle() == WorkstationInstanceLifecycle.RETIRED
                                ? WorkstationProjectionReadCode.RECOVERY_REQUIRED
                                : WorkstationProjectionReadCode.LEGACY_UNAVAILABLE,
                        expected.lifecycle() == WorkstationInstanceLifecycle.RETIRED
                                ? "Retired Workstation has no durable tombstone"
                                : "Active legacy Workstation has no durable projection"
                );
            }
            DurableWorkstationProjection projection = codec.decode(AtomicFilePublication.readBytes(path, LABEL));
            if (!matches(expected, projection)) {
                return WorkstationProjectionReadResult.unavailable(
                        expected.instanceId(), WorkstationProjectionReadCode.IDENTITY_CONFLICT,
                        "Durable projection identity binding differs from Workstation instance authority");
            }
            if (expected.lifecycle() == WorkstationInstanceLifecycle.IDENTITY_CONFLICT) {
                return WorkstationProjectionReadResult.unavailable(
                        expected.instanceId(), WorkstationProjectionReadCode.IDENTITY_CONFLICT,
                        "Workstation instance authority is identity-conflicted");
            }
            if (expected.lifecycle() == WorkstationInstanceLifecycle.RECOVERY_REQUIRED) {
                return WorkstationProjectionReadResult.unavailable(
                        expected.instanceId(), WorkstationProjectionReadCode.RECOVERY_REQUIRED,
                        "Workstation instance authority requires recovery");
            }
            if (expected.lifecycle() == WorkstationInstanceLifecycle.RETIRED
                    && projection.status() != WorkstationProjectionStatus.TOMBSTONED) {
                return WorkstationProjectionReadResult.unavailable(
                        expected.instanceId(), WorkstationProjectionReadCode.RECOVERY_REQUIRED,
                        "Retired Workstation retains an active durable projection");
            }
            if (expected.lifecycle() != WorkstationInstanceLifecycle.RETIRED
                    && projection.status() == WorkstationProjectionStatus.TOMBSTONED) {
                return WorkstationProjectionReadResult.unavailable(
                        expected.instanceId(), WorkstationProjectionReadCode.IDENTITY_CONFLICT,
                        "Active Workstation instance resolves to a tombstoned projection");
            }
            return WorkstationProjectionReadResult.available(projection);
        } catch (UnsupportedWorkstationProjectionSchemaException
                 | com.butchercraft.workstation.condition.UnsupportedConditionSchemaException exception) {
            return WorkstationProjectionReadResult.unavailable(
                    expected.instanceId(), WorkstationProjectionReadCode.UNSUPPORTED_SCHEMA, exception.getMessage());
        } catch (RuntimeException exception) {
            return WorkstationProjectionReadResult.unavailable(
                    expected.instanceId(), WorkstationProjectionReadCode.CORRUPT,
                    "Durable Workstation projection cannot be verified: " + exception.getMessage());
        }
    }

    public DurableWorkstationProjection readForRetirement(WorkstationInstanceRecord expected) {
        Objects.requireNonNull(expected, "expected");
        Path path = readPath(expected.instanceId());
        AtomicFilePublication.requireNoInterruptedPublication(path, LABEL);
        if (!Files.exists(path)) return null;
        DurableWorkstationProjection projection = codec.decode(AtomicFilePublication.readBytes(path, LABEL));
        if (!matches(expected, projection)) {
            throw new IllegalStateException("Retirement projection identity binding differs from instance authority");
        }
        return projection;
    }

    public List<WorkstationProjectionReadResult> enumerate(WorkstationInstanceRegistry registry) {
        return Objects.requireNonNull(registry, "registry").records().stream().sorted().map(this::read).sorted().toList();
    }

    public FrozenWorkstationProjectionSnapshot freezeForCheckpoint(WorkstationInstanceRecord expected) {
        WorkstationProjectionReadResult result = read(expected);
        DurableWorkstationProjection projection = result.projection().orElseThrow(() ->
                new IllegalStateException("Projection is not checkpoint-readable: " + result.code() + ": " + result.detail()));
        byte[] frozen = AtomicFilePublication.readBytes(readPath(expected.instanceId()), LABEL);
        DurableWorkstationProjection reread = codec.decode(frozen);
        if (!reread.equals(projection)) {
            throw new IllegalStateException("Projection changed while freezing checkpoint-read candidate");
        }
        return new FrozenWorkstationProjectionSnapshot(
                projection.instanceId(), projection.projectionRevision(), projection.stateDigest(), frozen);
    }

    public long size(WorkstationInstanceId instanceId) {
        try {
            return Files.size(readPath(instanceId));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to read durable Workstation projection size", exception);
        }
    }

    private static boolean matches(WorkstationInstanceRecord expected, DurableWorkstationProjection projection) {
        return projection.instanceId().equals(expected.instanceId())
                && projection.worldIdentity().equals(expected.worldIdentity())
                && projection.endpointKey().equals(expected.endpointKey())
                && projection.instanceGeneration() == expected.generation()
                && projection.instanceAllocationConfigurationIdentity()
                .equals(expected.allocationConfigurationIdentity());
    }

    private static FrozenWorkstationProjectionSnapshot frozen(
            DurableWorkstationProjection projection,
            byte[] bytes
    ) {
        return new FrozenWorkstationProjectionSnapshot(
                projection.instanceId(), projection.projectionRevision(), projection.stateDigest(), bytes);
    }

    private void preserveLegacySource(DurableWorkstationProjection source, byte[] bytes) {
        String hash = publicationDigest(bytes).substring("sha256:".length());
        Path target = rootDirectory.resolve("legacy_sources").resolve(sha256(source.instanceId().value()))
                .resolve(hash + ".json");
        AtomicFilePublication.requireNoInterruptedPublication(target, LABEL);
        if (!Files.exists(target)) {
            AtomicFilePublication.publishBytesIfDigestMatches(target, Optional.empty(), bytes, LABEL);
        }
        if (!java.util.Arrays.equals(bytes, AtomicFilePublication.readBytes(target, LABEL))) {
            throw new IllegalStateException("Immutable pre-condition projection source conflicts");
        }
    }

    private static String publicationDigest(byte[] bytes) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
