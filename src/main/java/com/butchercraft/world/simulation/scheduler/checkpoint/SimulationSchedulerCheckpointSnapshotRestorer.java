package com.butchercraft.world.simulation.scheduler.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointFailure;
import com.butchercraft.world.checkpoint.CheckpointFailureCode;
import com.butchercraft.world.checkpoint.CheckpointOwnerId;
import com.butchercraft.world.checkpoint.CheckpointOwnerRestorationCandidate;
import com.butchercraft.world.checkpoint.CheckpointOwnerRestorationPreparation;
import com.butchercraft.world.checkpoint.CheckpointOwnerRestorationPublicationResult;
import com.butchercraft.world.checkpoint.CheckpointOwnerRestorationRequest;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotCoordinator;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotRestorer;
import com.butchercraft.world.checkpoint.CheckpointOwnerValidationMetadata;
import com.butchercraft.world.checkpoint.CheckpointSnapshotDigest;
import com.butchercraft.world.simulation.scheduler.ScheduledSimulationWork;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandlerRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkRuntime;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SimulationSchedulerCheckpointSnapshotRestorer implements CheckpointOwnerSnapshotRestorer {
    private final SimulationWorkHandlerRegistry handlerRegistry;
    private final Supplier<SimulationSchedulerManager> currentScheduler;
    private final Consumer<SimulationSchedulerManager> publisher;

    public SimulationSchedulerCheckpointSnapshotRestorer(
            SimulationWorkHandlerRegistry handlerRegistry,
            Supplier<SimulationSchedulerManager> currentScheduler,
            Consumer<SimulationSchedulerManager> publisher
    ) {
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "handlerRegistry");
        this.currentScheduler = Objects.requireNonNull(currentScheduler, "currentScheduler");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    @Override
    public CheckpointOwnerId ownerId() {
        return SimulationSchedulerCheckpointSnapshotCodec.OWNER_ID;
    }

    @Override
    public CheckpointOwnerRestorationPreparation prepare(CheckpointOwnerRestorationRequest request) {
        Objects.requireNonNull(request, "request");
        if (!ownerId().equals(request.descriptor().ownerId())) {
            return failed(CheckpointFailureCode.OWNER_RESTORATION_VALIDATION_FAILURE,
                    "ownerId", "Scheduler restorer received a snapshot for a different owner");
        }
        if (request.descriptor().snapshotSchemaVersion()
                != SimulationSchedulerCheckpointSnapshotCodec.SNAPSHOT_SCHEMA_VERSION) {
            return failed(CheckpointFailureCode.UNSUPPORTED_OWNER_SNAPSHOT_SCHEMA,
                    "snapshotSchemaVersion", "Scheduler checkpoint snapshot schema is unsupported");
        }
        String digest = CheckpointSnapshotDigest.sha256(request.payloadBytes());
        if (!digest.equals(request.descriptor().contentDigest())) {
            return failed(CheckpointFailureCode.PAYLOAD_DIGEST_MISMATCH,
                    "contentDigest", "Scheduler checkpoint payload digest does not match descriptor");
        }
        if (!request.selectedGenerationId().equals(request.descriptor().generationId())) {
            return failed(CheckpointFailureCode.MIXED_GENERATION_IDENTITY,
                    "generationId", "Scheduler snapshot belongs to a different checkpoint generation");
        }
        if (!request.worldIdentityRoot().equals(request.descriptor().worldIdentityRoot())) {
            return failed(CheckpointFailureCode.WORLD_IDENTITY_MISMATCH,
                    "worldIdentityRoot", "Scheduler snapshot belongs to a different World Identity root");
        }
        try {
            SimulationSchedulerCheckpointSnapshotCodec.ParsedSchedulerSnapshot parsed =
                    SimulationSchedulerCheckpointSnapshotCodec.deserialize(request.payloadBytes(), handlerRegistry);
            if (!parsed.configurationIdentity().equals(request.descriptor().configurationIdentity())) {
                return failed(CheckpointFailureCode.PLATFORM_DETERMINISM_MANIFEST_MISMATCH,
                        "configurationIdentity", "Scheduler configuration identity does not match descriptor");
            }
            if (parsed.manager().lastFinalizedSimulationTick() != request.descriptor().representedSimulationTick()) {
                return failed(CheckpointFailureCode.CLOCK_SCHEDULER_TICK_MISMATCH,
                        "lastFinalizedSimulationTick", "Scheduler finalized tick does not match descriptor tick");
            }
            List<CheckpointFailure> invariantFailures = validateSchedulerInvariants(parsed.manager());
            if (!invariantFailures.isEmpty()) {
                return CheckpointOwnerRestorationPreparation.failed(ownerId(), invariantFailures);
            }
            CheckpointOwnerValidationMetadata metadata = new CheckpointOwnerValidationMetadata(
                    ownerId(),
                    Map.of(
                            CheckpointOwnerSnapshotCoordinator.SCHEDULER_FINALIZED_TICK_KEY,
                            Long.toString(parsed.manager().lastFinalizedSimulationTick()),
                            CheckpointOwnerSnapshotCoordinator.CONFIGURATION_IDENTITY_KEY,
                            parsed.configurationIdentity(),
                            CheckpointOwnerSnapshotCoordinator.SNAPSHOT_IDENTITY_KEY,
                            request.descriptor().snapshotIdentity()
                    )
            );
            return CheckpointOwnerRestorationPreparation.prepared(
                    new SchedulerRestorationCandidate(
                            parsed.manager(),
                            currentScheduler.get(),
                            metadata,
                            publisher
                    )
            );
        } catch (SimulationSchedulerCheckpointSnapshotCodec.UnsupportedSnapshotSchemaException exception) {
            return failed(CheckpointFailureCode.UNSUPPORTED_OWNER_SNAPSHOT_SCHEMA,
                    "snapshotSchemaVersion", "Scheduler checkpoint snapshot schema is unsupported");
        } catch (RuntimeException exception) {
            return failed(CheckpointFailureCode.SCHEDULER_INVARIANT_VIOLATION,
                    "payload", "Scheduler checkpoint snapshot could not be restored");
        }
    }

    private List<CheckpointFailure> validateSchedulerInvariants(SimulationSchedulerManager manager) {
        long finalizedTick = manager.lastFinalizedSimulationTick();
        try {
            manager.validateForPersistence();
            for (ScheduledSimulationWork work : manager.registry().definitions()) {
                if (work.origin().submissionTick() > finalizedTick) {
                    return List.of(failure(
                            CheckpointFailureCode.SCHEDULER_INVARIANT_VIOLATION,
                            work.id().value(),
                            "Scheduler Work submission tick follows the finalized Scheduler tick"
                    ));
                }
            }
            for (SimulationWorkRuntime runtime : manager.runtimeRecords()) {
                if (runtime.lastUpdatedSimulationTick() > finalizedTick) {
                    return List.of(failure(
                            CheckpointFailureCode.SCHEDULER_INVARIANT_VIOLATION,
                            runtime.workId().value(),
                            "Scheduler runtime update tick follows the finalized Scheduler tick"
                    ));
                }
            }
            return List.of();
        } catch (RuntimeException exception) {
            return List.of(failure(
                    CheckpointFailureCode.SCHEDULER_INVARIANT_VIOLATION,
                    ownerId().value(),
                    "Scheduler state violates owner persistence invariants"
            ));
        }
    }

    private CheckpointOwnerRestorationPreparation failed(
            CheckpointFailureCode code,
            String field,
            String message
    ) {
        return CheckpointOwnerRestorationPreparation.failed(ownerId(), List.of(failure(code, field, message)));
    }

    private static CheckpointFailure failure(CheckpointFailureCode code, String field, String message) {
        return new CheckpointFailure(code, field, message);
    }

    private record SchedulerRestorationCandidate(
            SimulationSchedulerManager restoredManager,
            SimulationSchedulerManager previousScheduler,
            CheckpointOwnerValidationMetadata validationMetadata,
            Consumer<SimulationSchedulerManager> publisher
    ) implements CheckpointOwnerRestorationCandidate {
        private SchedulerRestorationCandidate {
            restoredManager = Objects.requireNonNull(restoredManager, "restoredManager");
            validationMetadata = Objects.requireNonNull(validationMetadata, "validationMetadata");
            publisher = Objects.requireNonNull(publisher, "publisher");
        }

        @Override
        public CheckpointOwnerId ownerId() {
            return SimulationSchedulerCheckpointSnapshotCodec.OWNER_ID;
        }

        @Override
        public List<CheckpointFailure> validatePublication() {
            return List.of();
        }

        @Override
        public CheckpointOwnerRestorationPublicationResult publish() {
            try {
                publisher.accept(restoredManager);
                return CheckpointOwnerRestorationPublicationResult.published(ownerId());
            } catch (RuntimeException exception) {
                return CheckpointOwnerRestorationPublicationResult.failed(ownerId(), List.of(failure(
                        CheckpointFailureCode.OWNER_PUBLICATION_FAILURE,
                        ownerId().value(),
                        "Scheduler owner failed to publish restored state"
                )));
            }
        }

        @Override
        public CheckpointOwnerRestorationPublicationResult rollbackPublication() {
            try {
                publisher.accept(previousScheduler);
                return CheckpointOwnerRestorationPublicationResult.published(ownerId());
            } catch (RuntimeException exception) {
                return CheckpointOwnerRestorationPublicationResult.failed(ownerId(), List.of(failure(
                        CheckpointFailureCode.OWNER_PUBLICATION_FAILURE,
                        ownerId().value(),
                        "Scheduler owner failed to roll back restored state"
                )));
            }
        }
    }
}
