package com.butchercraft.integration.checkpoint;

import com.butchercraft.development.checkpoint.ActualWorldLegacySplitRecoverySource;
import com.butchercraft.world.checkpoint.CheckpointFilesystemRecoveryRequest;
import com.butchercraft.world.checkpoint.CheckpointFilesystemStore;
import com.butchercraft.world.checkpoint.CheckpointOwnerFileBundleCodec;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.checkpoint.SplitSnapshotRecoveryAnalyzer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NativeOwnerRestorationAdapterOwnershipTest {
    @Test
    void currentAdaptersAssignReservationFileOnlyToWorkforce() {
        Set<String> expectedWorkstation = Set.of(
                "machine_operating_states.json",
                "workstation_endpoint_journal.json",
                "workstation_instances.json",
                "workstation_projections.json"
        );
        Set<String> expectedWorkforce = Set.of(
                "departments.json",
                "employee_records.json",
                "employee_material_handling_assignments.json",
                "workforce_definitions.json",
                "workstation_reservations.json"
        );

        assertEquals(expectedWorkstation, NativeOwnerRestorationAdapters.workstationCheckpointFileNames());
        assertEquals(expectedWorkforce, NativeOwnerRestorationAdapters.workforceCheckpointFileNames());
        assertFalse(expectedWorkstation.contains("workstation_reservations.json"));
        assertFalse(expectedWorkforce.contains("execution_operations.json"));
    }

    @Test
    void configuredGenerationTwoWorkstationBundleMatchesRestorationOwnership() {
        Path world = configuredWorld();
        var plan = new SplitSnapshotRecoveryAnalyzer().analyze(
                new ActualWorldLegacySplitRecoverySource(world).reloadReadOnly());
        var request = new CheckpointFilesystemRecoveryRequest(
                LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS,
                plan.worldIdentityRoot(),
                plan.platformDeterminismManifest()
        );
        var selection = new StartupCheckpointCandidateSelector().select(
                new CheckpointFilesystemStore(world.resolve("butchercraft/checkpoints")), request);
        var generation = selection.generation().orElseThrow();
        var workstationPayload = generation.ownerSnapshots().stream()
                .filter(snapshot -> snapshot.descriptor().ownerId().equals(
                        LegacySplitRecoveryParticipants.WORKSTATION))
                .findFirst()
                .orElseThrow();
        var bundle = CheckpointOwnerFileBundleCodec.decode(workstationPayload.payloadBytes());
        Set<String> actual = bundle.files().stream()
                .map(file -> file.logicalName())
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(2L, generation.manifest().generationId().committedSequence());
        assertEquals(Set.of(
                "machine_operating_states.json",
                "workstation_endpoint_journal.json",
                "workstation_instances.json",
                "workstation_projections.json",
                "workstation_reservations.json"
        ), actual);
    }

    private static Path configuredWorld() {
        String configured = System.getProperty("butchercraft.r4WorldRoot");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(),
                "Set -Pbutchercraft.r4WorldRoot for actual generation-2 ownership validation");
        Path world = Path.of(configured).toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isDirectory(world), "Configured R4 disposable world does not exist");
        return world;
    }
}
