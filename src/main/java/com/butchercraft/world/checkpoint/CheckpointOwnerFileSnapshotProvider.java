package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Adapts an owner-controlled immutable file snapshot to the platform checkpoint contract. */
public final class CheckpointOwnerFileSnapshotProvider implements CheckpointOwnerSnapshotProvider {
    private final CheckpointOwnerId ownerId;
    private final String configurationIdentity;
    private final Supplier<CheckpointOwnerFileSnapshot> snapshotSupplier;

    public CheckpointOwnerFileSnapshotProvider(
            CheckpointOwnerId ownerId,
            String configurationIdentity,
            Supplier<CheckpointOwnerFileSnapshot> snapshotSupplier
    ) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.configurationIdentity = CheckpointValidation.id(configurationIdentity, "configurationIdentity");
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
    }

    @Override
    public CheckpointOwnerId ownerId() {
        return ownerId;
    }

    @Override
    public CheckpointOwnerSnapshotCaptureResult capture(CheckpointOwnerSnapshotContext context) {
        try {
            CheckpointOwnerFileSnapshot frozen = Objects.requireNonNull(snapshotSupplier.get(), "ownerSnapshot");
            if (!frozen.ownerId().equals(ownerId)) {
                throw new IllegalArgumentException("Owner file snapshot belongs to another owner");
            }
            byte[] bytes = CheckpointOwnerFileBundleCodec.encode(frozen);
            String digest = CheckpointSnapshotDigest.sha256(bytes);
            String ownerPath = ownerId.value().substring(ownerId.value().indexOf(':') + 1);
            OwnerSnapshotDescriptor descriptor = new OwnerSnapshotDescriptor(
                    ownerId,
                    CheckpointOwnerFileBundleCodec.SCHEMA_VERSION,
                    "butchercraft:live_owner_snapshot/" + ownerPath + "/"
                            + digest.substring("sha256:".length()),
                    digest,
                    CheckpointSnapshotParticipation.REQUIRED,
                    configurationIdentity,
                    context.worldIdentityRoot(),
                    context.generationId(),
                    context.authoritativeSimulationTick(),
                    frozen.ownerSequence()
            );
            CheckpointOwnerSnapshotPayload payload = CheckpointOwnerSnapshotPayload.of(descriptor, bytes);
            return CheckpointOwnerSnapshotCaptureResult.captured(new CheckpointCapturedOwnerSnapshot(
                    payload,
                    new CheckpointOwnerValidationMetadata(ownerId, Map.of(
                            CheckpointOwnerSnapshotCoordinator.CONFIGURATION_IDENTITY_KEY, configurationIdentity,
                            CheckpointOwnerSnapshotCoordinator.SNAPSHOT_IDENTITY_KEY, descriptor.snapshotIdentity()
                    ))
            ));
        } catch (CheckpointOwnerSnapshotRejectedException exception) {
            return CheckpointOwnerSnapshotCaptureResult.failed(ownerId, exception.failures());
        } catch (RuntimeException exception) {
            return CheckpointOwnerSnapshotCaptureResult.failed(ownerId, List.of(new CheckpointFailure(
                    CheckpointFailureCode.OWNER_SNAPSHOT_SERIALIZATION_FAILURE,
                    ownerId.value(),
                    exception.getMessage() == null ? "Owner checkpoint snapshot failed" : exception.getMessage()
            )));
        }
    }
}
