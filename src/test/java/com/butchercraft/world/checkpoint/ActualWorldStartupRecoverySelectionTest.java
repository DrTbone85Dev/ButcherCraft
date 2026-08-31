package com.butchercraft.world.checkpoint;

import com.butchercraft.development.checkpoint.ActualWorldLegacySplitRecoverySource;
import com.butchercraft.integration.checkpoint.StartupCheckpointCandidateSelector;
import com.butchercraft.workstation.checkpoint.WorkstationCheckpointRestorabilityVerifier;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActualWorldStartupRecoverySelectionTest {
    private static final String GENERATION_ONE =
            "butchercraft:checkpoint/00000000000000000001/39872";
    private static final String GENERATION_ONE_DIGEST =
            "sha256:63a75e239e830e91cc1b60a4c5f8ec0560dfdec411d339f80f321afb73a116b8";
    private static final String GENERATION_TWO =
            "butchercraft:checkpoint/00000000000000000002/39872";
    private static final String GENERATION_TWO_DIGEST =
            "sha256:5555ebae9bea2b96abcca45888b803b091b88c02f58acdafe2c90be01d81ba43";

    @Test
    void configuredR3cWorldRejectsGenerationOneAndSelectsRecoverySuccessorReadOnly() throws Exception {
        Path world = configuredWorld();
        String before = treeDigest(world);
        var plan = new SplitSnapshotRecoveryAnalyzer().analyze(
                new ActualWorldLegacySplitRecoverySource(world).reloadReadOnly());
        CheckpointFilesystemRecoveryRequest request = new CheckpointFilesystemRecoveryRequest(
                LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS,
                plan.worldIdentityRoot(),
                plan.platformDeterminismManifest()
        );
        Path checkpointRoot = world.resolve("butchercraft/checkpoints");
        CheckpointFilesystemStore store = new CheckpointFilesystemStore(checkpointRoot);

        CheckpointRecoveredGeneration generationOne = store.loadCommittedGenerationReadOnly(
                request,
                CheckpointGenerationId.of(1L, 39_872L),
                GENERATION_ONE_DIGEST
        ).recoveredGeneration().orElseThrow();
        assertEquals(GENERATION_ONE, generationOne.manifest().generationId().canonicalValue());
        assertFalse(WorkstationCheckpointRestorabilityVerifier.verify(generationOne).restorable());

        var selection = new StartupCheckpointCandidateSelector().select(store, request);
        assertTrue(selection.successful(), selection.rejectionSummary());
        CheckpointRecoveredGeneration generationTwo = selection.generation().orElseThrow();
        assertEquals(GENERATION_TWO, generationTwo.manifest().generationId().canonicalValue());
        assertEquals(GENERATION_TWO_DIGEST, generationTwo.manifest().manifestDigest());
        assertTrue(selection.workstationRestorability().orElseThrow().restorable());

        LegacySplitRecoveryResult recoveryResult = StartupRecoveryService.matchingRecoveryResult(
                store, request, checkpointRoot, generationTwo).orElseThrow();
        assertEquals(generationOne.manifest().generationId(), recoveryResult.recoveryGenerationId());
        assertEquals(generationOne.manifest().manifestDigest(), recoveryResult.generationManifestDigest());
        assertEquals(9, recoveryResult.publishedAcknowledgements().size());
        assertEquals(1, recoveryResult.preservedAuthorizedWork().size());
        assertEquals(before, treeDigest(world), "Read-only R4 selection changed the disposable R3C world");
    }

    private static Path configuredWorld() {
        String configured = System.getProperty("butchercraft.r4WorldRoot");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(),
                "Set -Pbutchercraft.r4WorldRoot for explicit actual-world R4 selection validation");
        Path world = Path.of(configured).toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isDirectory(world), "Configured R4 disposable world does not exist");
        return world;
    }

    private static String treeDigest(Path root) throws Exception {
        ArrayList<String> entries = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(candidate -> !candidate.getFileName().toString().equals("session.lock"))
                    .sorted().toList()) {
                byte[] bytes = Files.readAllBytes(path);
                entries.add(root.relativize(path).toString().replace('\\', '/') + "\t"
                        + bytes.length + "\t" + CheckpointSnapshotDigest.sha256(bytes));
            }
        }
        return CheckpointSnapshotDigest.sha256(
                String.join("\n", entries).getBytes(StandardCharsets.UTF_8));
    }
}
