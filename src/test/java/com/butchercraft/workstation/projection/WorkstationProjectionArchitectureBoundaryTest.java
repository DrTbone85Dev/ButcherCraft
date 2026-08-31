package com.butchercraft.workstation.projection;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationProjectionArchitectureBoundaryTest {
    @Test
    void projectionPersistenceUsesSharedAtomicPublicationWithoutOwningCheckpointGenerations()
            throws IOException {
        String storage = source("WorkstationProjectionStorage.java");
        String service = source("DurableWorkstationProjectionService.java");

        assertTrue(storage.contains("AtomicFilePublication.publishBytesIfDigestMatches"));
        assertTrue(storage.contains("AtomicFilePublication.requireNoInterruptedPublication"));
        assertFalse(storage.contains("Files.move("));
        assertFalse(storage.contains("StandardCopyOption"));
        assertFalse(service.contains("CheckpointGeneration"));
        assertFalse(service.contains("CheckpointFilesystemStore"));
        assertFalse(service.contains("LiveCheckpointService"));
    }

    @Test
    void projectionReadDoesNotLoadOrForceWorkstationChunks() throws IOException {
        String service = source("DurableWorkstationProjectionService.java");

        assertTrue(service.contains("if (level == null || !level.hasChunkAt(position)) return read;"));
        assertFalse(service.contains("getChunk("));
        assertFalse(service.contains("setChunkForced"));
        assertFalse(service.contains("addRegionTicket"));
        assertFalse(service.contains("forceLoad"));
    }

    @Test
    void r3bConsumesOnlyWorkstationOwnedFrozenProjectionBoundary() throws IOException {
        String registry = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/checkpoint/LiveCheckpointParticipantRegistry.java"));
        String checkpoint = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/workstation/checkpoint/"
                        + "WorkstationCheckpointProjectionService.java"));

        assertTrue(registry.contains("WorkstationCheckpointProjectionService.capture"));
        assertFalse(registry.contains("WorkstationProjectionStorage"));
        assertFalse(registry.contains("projectionRoot("));
        assertTrue(checkpoint.contains("freezeForCheckpoint"));
        assertFalse(checkpoint.substring(
                checkpoint.indexOf("public static WorkstationCheckpointProjectionSnapshot capture"),
                checkpoint.indexOf("public static void restore")).contains("getChunk("));
    }

    @Test
    void projectionDoesNotOwnMaterialHandlingExecutionSchedulerOrMachineRunState() throws IOException {
        Path root = TestProjectPaths.projectPath("src/main/java/com/butchercraft/workstation/projection");
        try (var files = Files.walk(root)) {
            var violations = files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return source.contains("MaterialHandlingService")
                                    || source.contains("SimulationSchedulerService")
                                    || source.contains("ExecutionMachineRunService")
                                    || source.contains("MachineOperatingStateStorage");
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();
            assertTrue(violations.isEmpty(), () -> "Projection authority boundary violations: " + violations);
        }
    }

    @Test
    void schema2EndpointRequiresDurableProjectionBeforeMutation() throws IOException {
        String runtime = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/workstation/endpoint/runtime/"
                        + "StackAwareWorkstationEndpointRuntimeService.java"));
        int reconciliation = runtime.indexOf(
                "DurableWorkstationProjectionService.INSTANCE.reconcileLoaded(level, workstation)");
        int activation = runtime.indexOf("endpoint.activateStackAwareEndpoint()");

        assertTrue(reconciliation >= 0, "Schema-2 endpoint must establish its durable projection");
        assertTrue(activation > reconciliation,
                "Schema-2 endpoint activation must follow durable projection reconciliation");
    }

    @Test
    void ownerEvidenceRepairCannotRegressRememberedProjectionRevision() throws IOException {
        String service = source("DurableWorkstationProjectionService.java");

        assertTrue(service.contains(
                "Math.max(durable.projectionRevision(), workstation.durableProjectionRevision())"));
    }

    @Test
    void projectionCaptureBindsRegistryAuthorityRevisionRatherThanInstanceUpdateRevision() throws IOException {
        String service = source("DurableWorkstationProjectionService.java");
        int capture = service.indexOf("private DurableWorkstationProjection capture(");
        int restore = service.indexOf("private RestoreAndVerifyResult restoreAndVerify(", capture);
        String captureSection = service.substring(capture, restore);

        assertTrue(captureSection.contains("instanceRegistrySnapshot(level.getServer())"));
        assertTrue(captureSection.contains("registry.ownerRevision()"));
        assertFalse(captureSection.contains("instance.lastUpdateRevision()"));
    }

    @Test
    void operatingStatePublicationSynchronizesLoadedProjectionReferenceAfterDurableSave() throws IOException {
        String service = source("DurableWorkstationProjectionService.java");
        int refresh = service.indexOf("public synchronized void refreshOperatingStateReference(");
        int read = service.indexOf("public synchronized WorkstationProjectionReadResult read(", refresh);
        String refreshSection = service.substring(refresh, read);

        int save = refreshSection.indexOf("storage(server).save(candidate)");
        int synchronize = refreshSection.indexOf(
                "synchronizeLoadedProjectionReference(server, instance, candidate)");

        assertTrue(save >= 0);
        assertTrue(synchronize > save,
                "The loaded block entity must remember the exact durable operating-state projection after publication");
    }

    @Test
    void loadedReconciliationRestoresDurableStateBeforeTestingPolicyBSuccessorEvidence() throws IOException {
        String service = source("DurableWorkstationProjectionService.java");
        int restore = service.indexOf("private RestoreAndVerifyResult restoreAndVerify(");
        int successor = service.indexOf("private boolean laterLiveProjectionIsProven(", restore);
        String restoreSection = service.substring(restore, successor);

        int applyDurable = restoreSection.indexOf("workstation.restoreDurableProjectionState(");
        int captureRestored = restoreSection.indexOf("DurableWorkstationProjection verified = capture(");
        int proveSuccessor = restoreSection.indexOf("laterLiveProjectionIsProven(");
        int publishRepair = restoreSection.indexOf("storage(level.getServer()).save(repaired)");

        assertTrue(applyDurable >= 0);
        assertTrue(captureRestored > applyDurable);
        assertTrue(proveSuccessor > captureRestored);
        assertTrue(publishRepair > proveSuccessor);
    }

    @Test
    void missingReferencedProjectionCannotBeReclassifiedAsLegacy() throws IOException {
        String service = source("DurableWorkstationProjectionService.java");
        int missingProjection = service.indexOf(
                "persisted.code() == WorkstationProjectionReadCode.LEGACY_UNAVAILABLE");
        int durableReferenceCheck = service.indexOf(
                "workstation.durableProjectionRevision() > 0L", missingProjection);
        int legacyBootstrap = service.indexOf(
                "bootstrapLoadedLegacy(level, workstation, instance, previouslyBound)", missingProjection);

        assertTrue(missingProjection >= 0 && durableReferenceCheck > missingProjection);
        assertTrue(legacyBootstrap > durableReferenceCheck,
                "A missing projection with an existing durable reference must fail before legacy bootstrap");
    }

    @Test
    void authorityBlockedLazyReconciliationUsesExistingIdentityWithoutEndpointReplay() throws IOException {
        String projectionService = source("DurableWorkstationProjectionService.java");
        String endpointService = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/workstation/endpoint/runtime/WorkstationEndpointService.java"));

        assertTrue(projectionService.contains(
                ": endpointService.existingReferenceForRecoveryReconciliation("));
        assertTrue(projectionService.contains(
                "if (!consequentialMutationPermitted) {\n"
                        + "                return new WorkstationProjectionReconciliationResult("));
        assertTrue(endpointService.contains(
                "if (consequentialMutationPermitted) reconcileLoadedEndpoint(level, position);"));
        assertTrue(endpointService.indexOf(
                "DurableWorkstationProjectionService\n                            .INSTANCE.reconcileLoaded(level, workstation)")
                > endpointService.indexOf(
                "if (consequentialMutationPermitted) reconcileLoadedEndpoint(level, position);"));
    }

    private static String source(String name) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/workstation/projection/" + name));
    }
}
