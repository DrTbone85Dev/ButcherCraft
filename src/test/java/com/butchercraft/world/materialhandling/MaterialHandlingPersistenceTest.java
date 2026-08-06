package com.butchercraft.world.materialhandling;

import com.butchercraft.workstation.endpoint.WorkstationEndpointConfiguration;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalRecord;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalState;
import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationEndpointObservation;
import com.butchercraft.workstation.endpoint.WorkstationEndpointOwnerResult;
import com.butchercraft.workstation.endpoint.WorkstationEndpointPreparation;
import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;
import com.butchercraft.workstation.endpoint.WorkstationEndpointStackPayload;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.materialhandling.persistence.MaterialHandlingStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialHandlingPersistenceTest {
    private static final WorldIdentityRootIdentity WORLD = new WorldIdentityRootIdentity(
            "butchercraft:world_identity/test",
            1,
            "sha256:" + "2".repeat(64)
    );
    private static final MaterialHandlingConfiguration CONFIG = MaterialHandlingConfiguration.standard();
    private static final WorkstationEndpointConfiguration ENDPOINT_CONFIG = WorkstationEndpointConfiguration.standard();
    private static final String MATERIAL = "butchercraft:beef_trim";
    private static final String ASSIGNMENT = "butchercraft:assignment/non_employee_integration";

    @TempDir
    Path tempDir;

    @Test
    void materialPreparationPersistsBeforeWorkstationPreparation() {
        MaterialHandlingRuntime.AllocationCandidate allocation = requested();
        MaterialTransferRecord requested = allocation.transfer();
        WorkstationEndpointObservation sourceObservation = observation(requested, true);
        MaterialHandlingRuntime runtime = transition(
                allocation.runtime(),
                requested,
                MaterialTransferLifecycle.SOURCE_BOUND,
                MaterialCustodyLocation.SOURCE_WORKSTATION,
                Optional.of(sourceObservation),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        MaterialTransferRecord sourceBound = runtime.find(requested.transferId()).orElseThrow();
        runtime = transition(
                runtime,
                sourceBound,
                MaterialTransferLifecycle.SOURCE_WITHDRAW_PREPARED,
                MaterialCustodyLocation.SOURCE_WORKSTATION,
                sourceBound.sourceObservation(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(stack()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        MaterialHandlingStorage storage = new MaterialHandlingStorage(tempDir.resolve(MaterialHandlingSchema.FILE_NAME));

        storage.save(runtime);
        MaterialTransferRecord restored = storage.loadExisting().orElseThrow()
                .find(requested.transferId())
                .orElseThrow();

        assertEquals(MaterialTransferLifecycle.SOURCE_WITHDRAW_PREPARED, restored.lifecycle());
        assertEquals(MaterialCustodyLocation.SOURCE_WORKSTATION, restored.custodyLocation().orElseThrow());
        assertTrue(restored.sourcePreparation().isEmpty());
        assertEquals(stack(), restored.exactTransferStack().orElseThrow());
    }

    @Test
    void exactCustodyPersistsInTransitWithBoundFreshnessAndOwnerResult() {
        MaterialHandlingRuntime.AllocationCandidate allocation = requested();
        MaterialTransferRecord transfer = allocation.transfer();
        WorkstationEndpointObservation sourceObservation = observation(transfer, true);
        WorkstationEndpointPreparation sourcePreparation = preparation(transfer, sourceObservation, true);
        WorkstationEndpointOwnerResult sourceResult = ownerResult(sourcePreparation);
        MaterialHandlingRuntime runtime = transition(
                allocation.runtime(),
                transfer,
                MaterialTransferLifecycle.SOURCE_BOUND,
                MaterialCustodyLocation.SOURCE_WORKSTATION,
                Optional.of(sourceObservation),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        transfer = runtime.find(transfer.transferId()).orElseThrow();
        runtime = transition(
                runtime,
                transfer,
                MaterialTransferLifecycle.SOURCE_WITHDRAW_PREPARED,
                MaterialCustodyLocation.SOURCE_WORKSTATION,
                transfer.sourceObservation(),
                Optional.of(sourcePreparation),
                Optional.empty(),
                Optional.of(stack()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        transfer = runtime.find(transfer.transferId()).orElseThrow();
        runtime = transition(
                runtime,
                transfer,
                MaterialTransferLifecycle.SOURCE_WITHDRAW_COMMITTED,
                MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME,
                transfer.sourceObservation(),
                transfer.sourcePreparation(),
                Optional.of(sourceResult),
                transfer.exactTransferStack(),
                transfer.exactTransferStack(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        transfer = runtime.find(transfer.transferId()).orElseThrow();
        runtime = transitionRetaining(runtime, transfer, MaterialTransferLifecycle.IN_TRANSIT);
        MaterialHandlingStorage storage = new MaterialHandlingStorage(tempDir.resolve(MaterialHandlingSchema.FILE_NAME));

        storage.save(runtime);
        MaterialHandlingRuntime loaded = storage.loadExisting().orElseThrow();
        MaterialTransferRecord restored = loaded.find(transfer.transferId()).orElseThrow();

        assertEquals(MaterialTransferLifecycle.IN_TRANSIT, restored.lifecycle());
        assertEquals(MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME, restored.custodyLocation().orElseThrow());
        assertEquals(stack(), restored.inTransitCustody().orElseThrow());
        assertEquals(sourceObservation.freshnessIdentity(), restored.sourceResult().orElseThrow().preFreshnessIdentity());
        assertEquals(storage.serialize(runtime), storage.serialize(loaded));
    }

    @Test
    void completedTransferRetainsEvidenceButNoAuthoritativeCustodyCopy() {
        MaterialHandlingRuntime runtime = completedRuntime();
        MaterialTransferRecord transfer = runtime.transfers().getFirst();

        assertEquals(MaterialTransferLifecycle.COMPLETED, transfer.lifecycle());
        assertEquals(MaterialCustodyLocation.DESTINATION_WORKSTATION, transfer.custodyLocation().orElseThrow());
        assertFalse(transfer.inTransitCustody().isPresent());
        assertTrue(transfer.sourceResult().isEmpty());
        assertTrue(transfer.destinationResult().isEmpty());
        MaterialTransferTerminalEvidence evidence = transfer.terminalEvidence().orElseThrow();
        assertTrue(evidence.sourceResult().isPresent());
        assertTrue(evidence.destinationResult().isPresent());
        assertEquals(stack().contentDigest(), evidence.stackContentIdentity());
        assertTrue(transfer.requestContentDigest().startsWith("sha256:"));
        String serialized = new MaterialHandlingStorage(tempDir.resolve("completed.json")).serialize(runtime);
        assertFalse(serialized.contains("encoded_stack"));
    }

    @Test
    void recoveryRequiredRetainsExactProvenCustodyAndIsCancellationEligible() {
        MaterialHandlingRuntime runtime = inTransitRuntime();
        MaterialTransferRecord transfer = runtime.transfers().getFirst();
        runtime = runtime.update(transfer.transferId(), (record, revision) -> record.transition(
                MaterialTransferLifecycle.RECOVERY_REQUIRED,
                revision,
                record.custodyLocation(),
                record.sourceObservation(),
                record.sourcePreparation(),
                record.sourceResult(),
                record.exactTransferStack(),
                record.inTransitCustody(),
                record.destinationObservation(),
                record.destinationPreparation(),
                record.destinationResult(),
                Optional.of("destination unavailable")
        ));
        MaterialTransferRecord recovery = runtime.transfers().getFirst();

        assertTrue(recovery.hasProvenMaterialHandlingCustody());
        assertEquals(stack(), recovery.inTransitCustody().orElseThrow());
        assertTrue(recovery.lifecycle().canTransitionTo(MaterialTransferLifecycle.CANCELLATION_REQUESTED));
    }

    @Test
    void cancellationSubstatesAndTerminalEvidenceRoundTripWithoutTerminalPayload() {
        MaterialHandlingStorage storage = new MaterialHandlingStorage(tempDir.resolve("cancellation.json"));
        MaterialHandlingRuntime requested = cancellationRequestedRuntime();
        assertRoundTrip(storage, requested, MaterialTransferLifecycle.CANCELLATION_REQUESTED);

        MaterialHandlingRuntime prepared = cancellationReturnPreparedRuntime(requested, true);
        assertRoundTrip(storage, prepared, MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED);

        MaterialHandlingRuntime committed = cancellationReturnCommittedRuntime(prepared);
        assertRoundTrip(storage, committed, MaterialTransferLifecycle.CANCELLATION_RETURN_COMMITTED);

        MaterialTransferRecord committedTransfer = committed.transfers().getFirst();
        MaterialHandlingRuntime cancelled = transitionRetaining(
                committed,
                committedTransfer,
                MaterialTransferLifecycle.CANCELLED
        );
        storage.save(cancelled);
        MaterialTransferRecord terminal = storage.loadExisting().orElseThrow().transfers().getFirst();

        assertEquals(MaterialTransferLifecycle.CANCELLED, terminal.lifecycle());
        assertTrue(terminal.exactTransferStack().isEmpty());
        assertTrue(terminal.inTransitCustody().isEmpty());
        assertTrue(terminal.sourceObservation().isEmpty());
        assertTrue(terminal.returnResult().isEmpty());
        assertTrue(terminal.terminalEvidence().orElseThrow().sourceResult().isPresent());
        assertTrue(terminal.terminalEvidence().orElseThrow().returnResult().isPresent());
        assertFalse(storage.serialize(cancelled).contains("encoded_stack"));
    }

    @Test
    void cancellationReturnPreparedWithoutWorkstationPreparationRoundTrips() {
        MaterialHandlingRuntime requested = cancellationRequestedRuntime();
        MaterialHandlingRuntime prepared = cancellationReturnPreparedRuntime(requested, false);
        MaterialHandlingStorage storage = new MaterialHandlingStorage(tempDir.resolve("return-observed.json"));

        assertRoundTrip(storage, prepared, MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED);
        assertTrue(prepared.transfers().getFirst().returnObservation().isPresent());
        assertTrue(prepared.transfers().getFirst().returnPreparation().isEmpty());
    }

    @Test
    void unknownOutcomeAndUnprovenRecoveryAreNotCancellationEligible() {
        MaterialHandlingRuntime.AllocationCandidate allocation = requested();
        MaterialTransferRecord transfer = allocation.transfer();
        MaterialHandlingRuntime recovery = allocation.runtime().update(
                transfer.transferId(),
                (record, revision) -> record.transition(
                        MaterialTransferLifecycle.RECOVERY_REQUIRED,
                        revision,
                        record.custodyLocation(),
                        record.sourceObservation(),
                        record.sourcePreparation(),
                        record.sourceResult(),
                        record.exactTransferStack(),
                        record.inTransitCustody(),
                        record.destinationObservation(),
                        record.destinationPreparation(),
                        record.destinationResult(),
                        Optional.of("source unavailable")
                )
        );
        MaterialTransferRecord unproven = recovery.transfers().getFirst();
        assertFalse(unproven.hasProvenMaterialHandlingCustody());

        MaterialHandlingRuntime unknown = allocation.runtime().update(
                transfer.transferId(),
                (record, revision) -> record.transition(
                        MaterialTransferLifecycle.UNKNOWN_OUTCOME,
                        revision,
                        Optional.empty(),
                        record.sourceObservation(),
                        record.sourcePreparation(),
                        record.sourceResult(),
                        record.exactTransferStack(),
                        record.inTransitCustody(),
                        record.destinationObservation(),
                        record.destinationPreparation(),
                        record.destinationResult(),
                        Optional.of("location unknown")
                )
        );
        MaterialTransferRecord ambiguous = unknown.transfers().getFirst();
        assertFalse(ambiguous.hasProvenMaterialHandlingCustody());
        assertFalse(ambiguous.lifecycle().canTransitionTo(MaterialTransferLifecycle.CANCELLATION_REQUESTED));
    }

    @Test
    void missingCanonicalReturnInvocationIdentityFailsVisibly() {
        MaterialHandlingStorage storage = new MaterialHandlingStorage(tempDir.resolve("missing-return.json"));
        String incomplete = storage.serialize(requested().runtime())
                .replaceFirst("(?m)^\\s*\"return_invocation_identity\".*\\R", "");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> storage.deserialize(incomplete)
        );

        assertTrue(failure.getMessage().contains("return_invocation_identity"));
    }

    @Test
    void unknownOutcomeDeclaresNoAuthoritativeLocation() {
        MaterialHandlingRuntime.AllocationCandidate allocation = requested();
        MaterialTransferRecord transfer = allocation.transfer();
        MaterialHandlingRuntime runtime = allocation.runtime().update(
                transfer.transferId(),
                (record, revision) -> record.transition(
                        MaterialTransferLifecycle.UNKNOWN_OUTCOME,
                        revision,
                        Optional.empty(),
                        record.sourceObservation(),
                        record.sourcePreparation(),
                        record.sourceResult(),
                        record.exactTransferStack(),
                        record.inTransitCustody(),
                        record.destinationObservation(),
                        record.destinationPreparation(),
                        record.destinationResult(),
                        Optional.of("location cannot be proven")
                )
        );

        assertTrue(runtime.transfers().getFirst().custodyLocation().isEmpty());
    }

    @Test
    void capacityAndSequenceRemainBoundedAndMonotonic() {
        MaterialHandlingRuntime empty = MaterialHandlingRuntime.empty(WORLD, CONFIG.configurationIdentity());
        MaterialHandlingRuntime.AllocationCandidate first = empty.request(
                source(), destination(), MATERIAL, 1, ASSIGNMENT, 1
        );

        assertEquals(1L, first.transfer().sequence());
        assertEquals(2L, first.runtime().nextTransferSequence());
        assertThrows(IllegalStateException.class, () -> first.runtime().request(
                source(), destination(), MATERIAL, 1, ASSIGNMENT, 1
        ));
    }

    @Test
    void unsupportedSchemaNeverFallsBackToEmptyRuntime() {
        MaterialHandlingStorage storage = new MaterialHandlingStorage(tempDir.resolve("material.json"));
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> storage.deserialize("{\"schema_version\":2}")
        );
        assertTrue(failure.getMessage().contains("Unsupported Material Handling schema"));
    }

    @Test
    void interruptedPublicationCannotInitializeAnEmptyAuthority() throws Exception {
        Path authoritative = tempDir.resolve(MaterialHandlingSchema.FILE_NAME);
        Files.writeString(authoritative.resolveSibling(MaterialHandlingSchema.FILE_NAME + ".tmp"), "interrupted");
        MaterialHandlingStorage storage = new MaterialHandlingStorage(authoritative);

        IllegalStateException failure = assertThrows(IllegalStateException.class, storage::loadExisting);

        assertTrue(failure.getMessage().contains("requires recovery"));
    }

    private static MaterialHandlingRuntime inTransitRuntime() {
        MaterialHandlingRuntime.AllocationCandidate allocation = requested();
        MaterialTransferRecord transfer = allocation.transfer();
        WorkstationEndpointObservation sourceObservation = observation(transfer, true);
        WorkstationEndpointPreparation sourcePreparation = preparation(transfer, sourceObservation, true);
        WorkstationEndpointOwnerResult sourceResult = ownerResult(sourcePreparation);
        MaterialHandlingRuntime runtime = transition(
                allocation.runtime(), transfer, MaterialTransferLifecycle.SOURCE_BOUND,
                MaterialCustodyLocation.SOURCE_WORKSTATION, Optional.of(sourceObservation), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
        transfer = runtime.find(transfer.transferId()).orElseThrow();
        runtime = transition(
                runtime, transfer, MaterialTransferLifecycle.SOURCE_WITHDRAW_PREPARED,
                MaterialCustodyLocation.SOURCE_WORKSTATION, transfer.sourceObservation(),
                Optional.of(sourcePreparation), Optional.empty(), Optional.of(stack()), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty()
        );
        transfer = runtime.find(transfer.transferId()).orElseThrow();
        runtime = transition(
                runtime, transfer, MaterialTransferLifecycle.SOURCE_WITHDRAW_COMMITTED,
                MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME, transfer.sourceObservation(),
                transfer.sourcePreparation(), Optional.of(sourceResult), transfer.exactTransferStack(),
                transfer.exactTransferStack(), Optional.empty(), Optional.empty(), Optional.empty()
        );
        transfer = runtime.find(transfer.transferId()).orElseThrow();
        return transitionRetaining(runtime, transfer, MaterialTransferLifecycle.IN_TRANSIT);
    }

    private static MaterialHandlingRuntime cancellationRequestedRuntime() {
        MaterialHandlingRuntime runtime = inTransitRuntime();
        MaterialTransferRecord transfer = runtime.transfers().getFirst();
        return runtime.update(transfer.transferId(), (record, revision) -> record.transition(
                MaterialTransferLifecycle.CANCELLATION_REQUESTED,
                revision,
                Optional.of(MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME),
                record.sourceObservation(),
                record.sourcePreparation(),
                record.sourceResult(),
                record.exactTransferStack(),
                record.inTransitCustody(),
                record.destinationObservation(),
                record.destinationPreparation(),
                record.destinationResult(),
                record.returnObservation(),
                record.returnPreparation(),
                record.returnResult(),
                Optional.of("operator cancellation")
        ));
    }

    private static MaterialHandlingRuntime cancellationReturnPreparedRuntime(
            MaterialHandlingRuntime runtime,
            boolean includePreparation
    ) {
        MaterialTransferRecord transfer = runtime.transfers().getFirst();
        WorkstationEndpointObservation observation = returnObservation(transfer);
        Optional<WorkstationEndpointPreparation> preparation = includePreparation
                ? Optional.of(returnPreparation(transfer, observation))
                : Optional.empty();
        return runtime.update(transfer.transferId(), (record, revision) -> record.transition(
                MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED,
                revision,
                Optional.of(MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME),
                record.sourceObservation(),
                record.sourcePreparation(),
                record.sourceResult(),
                record.exactTransferStack(),
                record.inTransitCustody(),
                record.destinationObservation(),
                record.destinationPreparation(),
                record.destinationResult(),
                Optional.of(observation),
                preparation,
                Optional.empty(),
                record.terminalDetail()
        ));
    }

    private static MaterialHandlingRuntime cancellationReturnCommittedRuntime(MaterialHandlingRuntime runtime) {
        MaterialTransferRecord transfer = runtime.transfers().getFirst();
        WorkstationEndpointPreparation preparation = transfer.returnPreparation().orElseThrow();
        WorkstationEndpointOwnerResult result = ownerResult(
                preparation,
                "butchercraft:test/source_only",
                "butchercraft:test/source_only",
                transfer.sourceResult().orElseThrow().journalSequence()
        );
        return runtime.update(transfer.transferId(), (record, revision) -> record.transition(
                MaterialTransferLifecycle.CANCELLATION_RETURN_COMMITTED,
                revision,
                Optional.of(MaterialCustodyLocation.SOURCE_WORKSTATION),
                record.sourceObservation(),
                record.sourcePreparation(),
                record.sourceResult(),
                record.exactTransferStack(),
                Optional.empty(),
                record.destinationObservation(),
                record.destinationPreparation(),
                record.destinationResult(),
                record.returnObservation(),
                record.returnPreparation(),
                Optional.of(result),
                record.terminalDetail()
        ));
    }

    private static void assertRoundTrip(
            MaterialHandlingStorage storage,
            MaterialHandlingRuntime runtime,
            MaterialTransferLifecycle lifecycle
    ) {
        storage.save(runtime);
        MaterialHandlingRuntime restored = storage.loadExisting().orElseThrow();
        assertEquals(runtime, restored);
        assertEquals(lifecycle, restored.transfers().getFirst().lifecycle());
        assertEquals(storage.serialize(runtime), storage.serialize(restored));
    }

    private static MaterialHandlingRuntime completedRuntime() {
        MaterialHandlingRuntime.AllocationCandidate allocation = requested();
        MaterialTransferRecord transfer = allocation.transfer();
        WorkstationEndpointObservation sourceObservation = observation(transfer, true);
        WorkstationEndpointPreparation sourcePreparation = preparation(transfer, sourceObservation, true);
        WorkstationEndpointOwnerResult sourceResult = ownerResult(sourcePreparation);
        WorkstationEndpointObservation destinationObservation = observation(transfer, false);
        WorkstationEndpointPreparation destinationPreparation = preparation(transfer, destinationObservation, false);
        WorkstationEndpointOwnerResult destinationResult = ownerResult(destinationPreparation);
        MaterialHandlingRuntime runtime = transition(
                allocation.runtime(), transfer, MaterialTransferLifecycle.SOURCE_BOUND,
                MaterialCustodyLocation.SOURCE_WORKSTATION, Optional.of(sourceObservation), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
        transfer = runtime.find(transfer.transferId()).orElseThrow();
        runtime = transition(
                runtime, transfer, MaterialTransferLifecycle.SOURCE_WITHDRAW_PREPARED,
                MaterialCustodyLocation.SOURCE_WORKSTATION, transfer.sourceObservation(), Optional.of(sourcePreparation),
                Optional.empty(), Optional.of(stack()), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
        transfer = runtime.find(transfer.transferId()).orElseThrow();
        runtime = transition(
                runtime, transfer, MaterialTransferLifecycle.SOURCE_WITHDRAW_COMMITTED,
                MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME, transfer.sourceObservation(),
                transfer.sourcePreparation(), Optional.of(sourceResult), transfer.exactTransferStack(),
                transfer.exactTransferStack(), Optional.empty(), Optional.empty(), Optional.empty()
        );
        transfer = runtime.find(transfer.transferId()).orElseThrow();
        runtime = transitionRetaining(runtime, transfer, MaterialTransferLifecycle.IN_TRANSIT);
        transfer = runtime.find(transfer.transferId()).orElseThrow();
        runtime = transition(
                runtime, transfer, MaterialTransferLifecycle.DESTINATION_BOUND,
                MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME, transfer.sourceObservation(),
                transfer.sourcePreparation(), transfer.sourceResult(), transfer.exactTransferStack(),
                transfer.inTransitCustody(), Optional.of(destinationObservation), Optional.empty(), Optional.empty()
        );
        transfer = runtime.find(transfer.transferId()).orElseThrow();
        runtime = transition(
                runtime, transfer, MaterialTransferLifecycle.DESTINATION_DEPOSIT_PREPARED,
                MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME, transfer.sourceObservation(),
                transfer.sourcePreparation(), transfer.sourceResult(), transfer.exactTransferStack(),
                transfer.inTransitCustody(), transfer.destinationObservation(), Optional.of(destinationPreparation),
                Optional.empty()
        );
        transfer = runtime.find(transfer.transferId()).orElseThrow();
        runtime = transition(
                runtime, transfer, MaterialTransferLifecycle.DESTINATION_DEPOSIT_COMMITTED,
                MaterialCustodyLocation.DESTINATION_WORKSTATION, transfer.sourceObservation(),
                transfer.sourcePreparation(), transfer.sourceResult(), transfer.exactTransferStack(), Optional.empty(),
                transfer.destinationObservation(), transfer.destinationPreparation(), Optional.of(destinationResult)
        );
        transfer = runtime.find(transfer.transferId()).orElseThrow();
        return transitionRetaining(runtime, transfer, MaterialTransferLifecycle.COMPLETED);
    }

    private static MaterialHandlingRuntime transitionRetaining(
            MaterialHandlingRuntime runtime,
            MaterialTransferRecord transfer,
            MaterialTransferLifecycle lifecycle
    ) {
        MaterialCustodyLocation location = switch (lifecycle) {
            case IN_TRANSIT -> MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME;
            case COMPLETED -> MaterialCustodyLocation.DESTINATION_WORKSTATION;
            case CANCELLED -> MaterialCustodyLocation.SOURCE_WORKSTATION;
            default -> throw new IllegalArgumentException("Unsupported retaining transition");
        };
        return transition(
                runtime, transfer, lifecycle, location, transfer.sourceObservation(), transfer.sourcePreparation(),
                transfer.sourceResult(), transfer.exactTransferStack(), transfer.inTransitCustody(),
                transfer.destinationObservation(), transfer.destinationPreparation(), transfer.destinationResult()
        );
    }

    private static MaterialHandlingRuntime transition(
            MaterialHandlingRuntime runtime,
            MaterialTransferRecord transfer,
            MaterialTransferLifecycle lifecycle,
            MaterialCustodyLocation custodyLocation,
            Optional<WorkstationEndpointObservation> sourceObservation,
            Optional<WorkstationEndpointPreparation> sourcePreparation,
            Optional<WorkstationEndpointOwnerResult> sourceResult,
            Optional<WorkstationEndpointStackPayload> exactStack,
            Optional<WorkstationEndpointStackPayload> custody,
            Optional<WorkstationEndpointObservation> destinationObservation,
            Optional<WorkstationEndpointPreparation> destinationPreparation,
            Optional<WorkstationEndpointOwnerResult> destinationResult
    ) {
        return runtime.update(transfer.transferId(), (record, revision) -> record.transition(
                lifecycle,
                revision,
                Optional.of(custodyLocation),
                sourceObservation,
                sourcePreparation,
                sourceResult,
                exactStack,
                custody,
                destinationObservation,
                destinationPreparation,
                destinationResult,
                lifecycle == MaterialTransferLifecycle.CANCELLED
                        ? Optional.of("test cancellation")
                        : Optional.empty()
        ));
    }

    private static MaterialHandlingRuntime.AllocationCandidate requested() {
        return MaterialHandlingRuntime.empty(WORLD, CONFIG.configurationIdentity()).request(
                source(), destination(), MATERIAL, 1, ASSIGNMENT, 10
        );
    }

    private static WorkstationEndpointObservation observation(MaterialTransferRecord transfer, boolean source) {
        WorkstationEndpointReference endpoint = source ? transfer.source() : transfer.destination();
        return WorkstationEndpointObservation.create(
                endpoint.instanceId(),
                source ? WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                        : WorkstationEndpointEffectKind.DESTINATION_DEPOSIT,
                0,
                stack(),
                0L,
                0L,
                source ? "butchercraft:test/source_only" : "butchercraft:test/idle",
                0L,
                ENDPOINT_CONFIG.endpointConfigurationIdentity()
        );
    }

    private static WorkstationEndpointObservation returnObservation(MaterialTransferRecord transfer) {
        WorkstationEndpointOwnerResult sourceResult = transfer.sourceResult().orElseThrow();
        return WorkstationEndpointObservation.create(
                transfer.source().instanceId(),
                WorkstationEndpointEffectKind.SOURCE_RETURN,
                0,
                stack(),
                sourceResult.postInventoryRevision(),
                sourceResult.endpointEffectRevision(),
                "butchercraft:test/source_only",
                sourceResult.journalSequence(),
                ENDPOINT_CONFIG.endpointConfigurationIdentity()
        );
    }

    private static WorkstationEndpointPreparation returnPreparation(
            MaterialTransferRecord transfer,
            WorkstationEndpointObservation observation
    ) {
        WorkstationEndpointJournalRecord requested = WorkstationEndpointJournalRecord.requested(
                2L,
                observation.instanceId(),
                transfer.returnInvocationIdentity(),
                WorkstationEndpointEffectKind.SOURCE_RETURN,
                observation.slotIndex(),
                observation.exactEffectStack(),
                observation.inventoryRevision(),
                observation.endpointEffectRevision(),
                observation.operationStateIdentity(),
                observation.operationStateIdentity(),
                observation.ownerResultJournalSequence(),
                observation.endpointConfigurationIdentity(),
                3L
        );
        return WorkstationEndpointPreparation.from(requested.transition(
                WorkstationEndpointJournalState.PREPARED,
                4L,
                requested.postInventoryRevision(),
                requested.endpointEffectRevision(),
                Optional.empty(),
                Optional.empty()
        ));
    }

    private static WorkstationEndpointPreparation preparation(
            MaterialTransferRecord transfer,
            WorkstationEndpointObservation observation,
            boolean source
    ) {
        String invocation = source ? transfer.sourceInvocationIdentity() : transfer.destinationInvocationIdentity();
        WorkstationEndpointJournalRecord requested = WorkstationEndpointJournalRecord.requested(
                source ? 1L : 2L,
                observation.instanceId(),
                invocation,
                observation.effectKind(),
                observation.slotIndex(),
                observation.exactEffectStack(),
                observation.inventoryRevision(),
                observation.endpointEffectRevision(),
                observation.operationStateIdentity(),
                source ? observation.operationStateIdentity() : "butchercraft:test/ready",
                observation.ownerResultJournalSequence(),
                observation.endpointConfigurationIdentity(),
                1L
        );
        return WorkstationEndpointPreparation.from(requested.transition(
                WorkstationEndpointJournalState.PREPARED,
                2L,
                requested.postInventoryRevision(),
                requested.endpointEffectRevision(),
                Optional.empty(),
                Optional.empty()
        ));
    }

    private static WorkstationEndpointOwnerResult ownerResult(WorkstationEndpointPreparation preparation) {
        return ownerResult(
                preparation,
                preparation.effectKind() == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                        ? "butchercraft:test/source_only"
                        : "butchercraft:test/idle",
                preparation.effectKind() == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                        ? "butchercraft:test/source_only"
                        : "butchercraft:test/ready",
                0L
        );
    }

    private static WorkstationEndpointOwnerResult ownerResult(
            WorkstationEndpointPreparation preparation,
            String preOperationState,
            String postOperationState,
            long previousOwnerResultJournalSequence
    ) {
        WorkstationEndpointJournalRecord record = WorkstationEndpointJournalRecord.requested(
                preparation.journalSequence(),
                preparation.instanceId(),
                preparation.invocationIdentity(),
                preparation.effectKind(),
                preparation.slotIndex(),
                preparation.exactStack(),
                preparation.expectedInventoryRevision(),
                preparation.expectedEndpointEffectRevision(),
                preOperationState,
                postOperationState,
                previousOwnerResultJournalSequence,
                preparation.endpointConfigurationIdentity(),
                1L
        ).transition(
                WorkstationEndpointJournalState.PREPARED,
                2L,
                preparation.expectedInventoryRevision() + 1L,
                preparation.expectedEndpointEffectRevision() + 1L,
                Optional.empty(),
                Optional.empty()
        );
        return WorkstationEndpointOwnerResult.create(record, WorkstationEndpointResultCode.APPLIED, Optional.empty());
    }

    private static WorkstationEndpointReference source() {
        return reference("butchercraft:cutting_table", 1, 1L);
    }

    private static WorkstationEndpointReference destination() {
        return reference("butchercraft:grinder", 3, 2L);
    }

    private static WorkstationEndpointReference reference(String type, int x, long generation) {
        WorkstationEndpointKey key = new WorkstationEndpointKey(type, "minecraft:overworld", x, 64, 0);
        WorkstationInstanceId id = WorkstationInstanceId.create(
                WORLD,
                key,
                generation,
                ENDPOINT_CONFIG.instanceAllocationConfigurationIdentity()
        );
        return new WorkstationEndpointReference(id, key, generation);
    }

    private static WorkstationEndpointStackPayload stack() {
        return WorkstationEndpointStackPayload.create(
                "butchercraft:item_stack_codec/v1/test",
                "butchercraft:beef_trim_test",
                1,
                "exact-beef-trim".getBytes(StandardCharsets.UTF_8)
        );
    }
}
