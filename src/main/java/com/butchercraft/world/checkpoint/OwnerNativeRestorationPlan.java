package com.butchercraft.world.checkpoint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record OwnerNativeRestorationPlan(
        CheckpointOwnerId ownerId,
        String snapshotIdentity,
        String snapshotContentDigest,
        int ownerSchemaVersion,
        List<NativeFile> nativeFiles,
        Optional<byte[]> workstationProjection,
        RecoveryMutationGate mutationGate,
        List<String> policyBRunIdentities,
        String logicalContentDigest
) implements Comparable<OwnerNativeRestorationPlan> {
    private static final RecoveryMutationGate OPEN_GATE =
            new RecoveryMutationGate(1, false, List.of(), List.of());

    public OwnerNativeRestorationPlan {
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        snapshotIdentity = CheckpointValidation.id(snapshotIdentity, "snapshotIdentity");
        snapshotContentDigest = CheckpointValidation.digest(snapshotContentDigest, "snapshotContentDigest");
        ownerSchemaVersion = CheckpointValidation.positive(ownerSchemaVersion, "ownerSchemaVersion");
        nativeFiles = Objects.requireNonNull(nativeFiles, "nativeFiles").stream()
                .map(value -> Objects.requireNonNull(value, "nativeFile"))
                .sorted()
                .toList();
        for (int index = 1; index < nativeFiles.size(); index++) {
            if (nativeFiles.get(index - 1).logicalName().equals(nativeFiles.get(index).logicalName())) {
                throw new IllegalArgumentException("Duplicate native restoration file");
            }
        }
        workstationProjection = Objects.requireNonNull(workstationProjection, "workstationProjection")
                .map(byte[]::clone);
        mutationGate = Objects.requireNonNull(mutationGate, "mutationGate");
        policyBRunIdentities = Objects.requireNonNull(policyBRunIdentities, "policyBRunIdentities").stream()
                .map(value -> CheckpointValidation.id(value, "policyBRunIdentity"))
                .distinct().sorted().toList();
        logicalContentDigest = CheckpointValidation.digest(logicalContentDigest, "logicalContentDigest");
        String expected = calculateLogicalDigest(
                ownerId, nativeFiles, workstationProjection, mutationGate, policyBRunIdentities);
        if (!logicalContentDigest.equals(expected)) {
            throw new IllegalArgumentException("Owner native restoration plan digest is not canonical");
        }
    }

    public static OwnerNativeRestorationPlan create(
            OwnerSnapshotDescriptor descriptor,
            List<NativeFile> nativeFiles,
            Optional<byte[]> workstationProjection
    ) {
        return create(descriptor, descriptor.snapshotSchemaVersion(), nativeFiles, workstationProjection);
    }

    public static OwnerNativeRestorationPlan create(
            OwnerSnapshotDescriptor descriptor,
            int ownerSchemaVersion,
            List<NativeFile> nativeFiles,
            Optional<byte[]> workstationProjection
    ) {
        return create(
                descriptor,
                ownerSchemaVersion,
                nativeFiles,
                workstationProjection,
                OPEN_GATE,
                List.of()
        );
    }

    public static OwnerNativeRestorationPlan create(
            OwnerSnapshotDescriptor descriptor,
            int ownerSchemaVersion,
            List<NativeFile> nativeFiles,
            Optional<byte[]> workstationProjection,
            RecoveryMutationGate mutationGate,
            List<String> policyBRunIdentities
    ) {
        Objects.requireNonNull(descriptor, "descriptor");
        List<NativeFile> files = List.copyOf(nativeFiles);
        Optional<byte[]> projection = workstationProjection.map(byte[]::clone);
        RecoveryMutationGate gate = Objects.requireNonNull(mutationGate, "mutationGate");
        List<String> policyB = List.copyOf(policyBRunIdentities);
        return new OwnerNativeRestorationPlan(
                descriptor.ownerId(),
                descriptor.snapshotIdentity(),
                descriptor.contentDigest(),
                ownerSchemaVersion,
                files,
                projection,
                gate,
                policyB,
                calculateLogicalDigest(descriptor.ownerId(), files, projection, gate, policyB)
        );
    }

    public OwnerNativeRestorationPlan withObservedPreconditions(Path ownerRoot) throws IOException {
        Objects.requireNonNull(ownerRoot, "ownerRoot");
        try {
            List<NativeFile> files = nativeFiles.stream().map(file -> {
                Path target = ownerRoot.resolve(file.targetRelativePath()).normalize();
                if (!target.startsWith(ownerRoot.normalize())) {
                    throw new IllegalArgumentException("Native restoration target escapes the owner root");
                }
                try {
                    Optional<String> observedDigest = Files.exists(target)
                            ? Optional.of(CheckpointSnapshotDigest.sha256(Files.readAllBytes(target)))
                            : Optional.empty();
                    return file.withObservedPreRestorationDigest(observedDigest);
                } catch (IOException exception) {
                    throw new PreconditionReadException(exception);
                }
            }).toList();
            return new OwnerNativeRestorationPlan(
                    ownerId,
                    snapshotIdentity,
                    snapshotContentDigest,
                    ownerSchemaVersion,
                    files,
                    workstationProjection,
                    mutationGate,
                    policyBRunIdentities,
                    calculateLogicalDigest(
                            ownerId, files, workstationProjection, mutationGate, policyBRunIdentities)
            );
        } catch (PreconditionReadException exception) {
            throw exception.cause;
        }
    }

    @Override
    public Optional<byte[]> workstationProjection() {
        return workstationProjection.map(byte[]::clone);
    }

    @Override
    public int compareTo(OwnerNativeRestorationPlan other) {
        return ownerId.compareTo(Objects.requireNonNull(other, "other").ownerId);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof OwnerNativeRestorationPlan value)) return false;
        return ownerId.equals(value.ownerId)
                && snapshotIdentity.equals(value.snapshotIdentity)
                && snapshotContentDigest.equals(value.snapshotContentDigest)
                && ownerSchemaVersion == value.ownerSchemaVersion
                && nativeFiles.equals(value.nativeFiles)
                && optionalBytesEqual(workstationProjection, value.workstationProjection)
                && mutationGate.equals(value.mutationGate)
                && policyBRunIdentities.equals(value.policyBRunIdentities)
                && logicalContentDigest.equals(value.logicalContentDigest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                ownerId,
                snapshotIdentity,
                snapshotContentDigest,
                ownerSchemaVersion,
                nativeFiles,
                workstationProjection.map(Arrays::hashCode).orElse(0),
                mutationGate,
                policyBRunIdentities,
                logicalContentDigest
        );
    }

    private static String calculateLogicalDigest(
            CheckpointOwnerId ownerId,
            List<NativeFile> files,
            Optional<byte[]> projection,
            RecoveryMutationGate mutationGate,
            List<String> policyBRunIdentities
    ) {
        CheckpointCanonicalDigest digest = CheckpointCanonicalDigest.create(
                "butchercraft:owner_native_restoration_plan"
        ).add(1).add(ownerId.value()).add(files.size());
        files.stream().sorted().forEach(file -> digest
                .add(file.logicalName())
                .add(file.targetRelativePath())
                .add(file.contentDigest())
                .add(file.bytes().length)
                .add(file.observedPreRestorationDigest().isPresent())
                .add(file.observedPreRestorationDigest().orElse("")));
        digest.add(projection.isPresent());
        projection.ifPresent(bytes -> digest.add(CheckpointSnapshotDigest.sha256(bytes)));
        digest.add(mutationGate.wholeWorldConsequentialMutationBlocked())
                .add(mutationGate.blockedAuthorityIdentities().size());
        mutationGate.blockedAuthorityIdentities().forEach(digest::add);
        digest.add(mutationGate.blockIdentities().size());
        mutationGate.blockIdentities().forEach(digest::add);
        List<String> policyB = policyBRunIdentities.stream().distinct().sorted().toList();
        digest.add(policyB.size());
        policyB.forEach(digest::add);
        return digest.finish();
    }

    private static boolean optionalBytesEqual(Optional<byte[]> left, Optional<byte[]> right) {
        if (left.isPresent() != right.isPresent()) return false;
        return left.isEmpty() || Arrays.equals(left.orElseThrow(), right.orElseThrow());
    }

    public record NativeFile(
            String logicalName,
            String targetRelativePath,
            byte[] bytes,
            String contentDigest,
            Optional<String> observedPreRestorationDigest
    ) implements Comparable<NativeFile> {
        public NativeFile {
            logicalName = CheckpointValidation.text(logicalName, "nativeFileLogicalName");
            targetRelativePath = CheckpointValidation.text(targetRelativePath, "targetRelativePath")
                    .replace('\\', '/');
            Path path = Path.of(targetRelativePath).normalize();
            if (path.isAbsolute() || path.startsWith("..") || !path.toString().replace('\\', '/').equals(targetRelativePath)) {
                throw new IllegalArgumentException("Native restoration target must be a normalized relative path");
            }
            bytes = Objects.requireNonNull(bytes, "bytes").clone();
            contentDigest = CheckpointValidation.digest(contentDigest, "nativeFileContentDigest");
            observedPreRestorationDigest = CheckpointValidation.optionalDigest(
                    Objects.requireNonNull(observedPreRestorationDigest, "observedPreRestorationDigest"),
                    "observedPreRestorationDigest"
            );
            if (!CheckpointSnapshotDigest.sha256(bytes).equals(contentDigest)) {
                throw new IllegalArgumentException("Native restoration file digest mismatch");
            }
        }

        public static NativeFile of(String logicalName, String targetRelativePath, byte[] bytes) {
            byte[] frozen = Objects.requireNonNull(bytes, "bytes").clone();
            return new NativeFile(
                    logicalName,
                    targetRelativePath,
                    frozen,
                    CheckpointSnapshotDigest.sha256(frozen),
                    Optional.empty()
            );
        }

        public NativeFile withObservedPreRestorationDigest(Optional<String> observedDigest) {
            return new NativeFile(logicalName, targetRelativePath, bytes, contentDigest, observedDigest);
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public int compareTo(NativeFile other) {
            return logicalName.compareTo(Objects.requireNonNull(other, "other").logicalName);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof NativeFile value
                    && logicalName.equals(value.logicalName)
                    && targetRelativePath.equals(value.targetRelativePath)
                    && Arrays.equals(bytes, value.bytes)
                    && contentDigest.equals(value.contentDigest)
                    && observedPreRestorationDigest.equals(value.observedPreRestorationDigest);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    logicalName,
                    targetRelativePath,
                    Arrays.hashCode(bytes),
                    contentDigest,
                    observedPreRestorationDigest
            );
        }
    }

    private static final class PreconditionReadException extends RuntimeException {
        private final IOException cause;

        private PreconditionReadException(IOException cause) {
            super(cause);
            this.cause = cause;
        }
    }
}
