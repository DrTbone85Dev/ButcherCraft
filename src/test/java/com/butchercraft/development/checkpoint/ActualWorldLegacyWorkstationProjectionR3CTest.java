package com.butchercraft.development.checkpoint;

import com.butchercraft.workstation.projection.LegacyWorkstationProjectionAnalysis;
import com.butchercraft.workstation.projection.LegacyWorkstationProjectionAuthorization;
import com.butchercraft.workstation.projection.LegacyWorkstationProjectionClassification;
import com.butchercraft.workstation.projection.LegacyWorkstationProjectionPublicationReport;
import com.butchercraft.world.checkpoint.RecoveryOperatorAuthority;
import com.butchercraft.world.checkpoint.RecoveryOperatorEvidence;
import net.minecraft.core.RegistryAccess;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActualWorldLegacyWorkstationProjectionR3CTest {
    private static final int REQUIRED_WORKSTATIONS = 6;

    @Test
    void configuredDisposableHistoricalWorldProducesSixExactCandidatesReadOnly() {
        Path world = configuredWorld();
        LegacyWorkstationProjectionAnalysis analysis = new LegacyWorkstationProjectionAdminTool()
                .analyzeReadOnly(world, RegistryAccess.EMPTY, List.of());

        assertEquals(REQUIRED_WORKSTATIONS, analysis.entries().size());
        assertTrue(analysis.proofComplete(), analysis.blockers().toString());
        assertTrue(analysis.entries().stream().allMatch(entry ->
                entry.classification() == LegacyWorkstationProjectionClassification.PROOF_COMPLETE
                        || entry.classification() == LegacyWorkstationProjectionClassification.ALREADY_AVAILABLE));
        long available = analysis.entries().stream().filter(entry ->
                entry.classification() == LegacyWorkstationProjectionClassification.ALREADY_AVAILABLE).count();
        assertTrue(available == 0L || available == REQUIRED_WORKSTATIONS);
        assertTrue(analysis.candidates().stream().allMatch(candidate ->
                candidate.projection().projectionRevision() == 1L
                        && candidate.provenance().equals("LEGACY_BOOTSTRAP_FROM_PROVEN_STATE")));
    }

    @Test
    void configuredDisposableHistoricalWorldPublishesOnlyWithExactOperatorAuthorization() {
        Assumptions.assumeTrue(Boolean.getBoolean("butchercraft.r3cPublish"),
                "Set -Pbutchercraft.r3cPublish=true for explicit disposable publication");
        Path world = configuredWorld();
        LegacyWorkstationProjectionAdminTool tool = new LegacyWorkstationProjectionAdminTool();
        LegacyWorkstationProjectionAnalysis analysis = tool.analyzeReadOnly(world, RegistryAccess.EMPTY, List.of());
        if (analysis.candidates().isEmpty()) {
            assertTrue(analysis.entries().stream().allMatch(entry ->
                    entry.classification() == LegacyWorkstationProjectionClassification.ALREADY_AVAILABLE));
            return;
        }
        var authorization = tool.authorizeExact(analysis, new RecoveryOperatorEvidence(
                "butchercraft:operator/im_031c_r3c",
                RecoveryOperatorAuthority.ADMINISTRATOR,
                "butchercraft:operator_evidence/im_031c_r3c/actual_historical_disposable",
                "sha256:6ebcba00cf68fc2b73bce40bb9b529660c43b6bff764b7d197bc8d9768a611d1"
        ));
        LegacyWorkstationProjectionPublicationReport report = tool.publishExact(
                world, RegistryAccess.EMPTY, List.of(), authorization);

        assertTrue(report.successful(), report.detail());
        assertEquals(REQUIRED_WORKSTATIONS, report.projections().size());
        assertTrue(Files.isRegularFile(report.bootstrapEvidencePath()));
    }

    @Test
    void configuredDisposablePublishesCompleteSameTickSuccessorWithoutNativeMutation() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("butchercraft.r3cSuccessor"),
                "Set -Pbutchercraft.r3cSuccessor=true for explicit successor publication");
        Path world = configuredWorld();
        LegacyWorkstationProjectionAnalysis analysis = new LegacyWorkstationProjectionAdminTool()
                .analyzeReadOnly(world, RegistryAccess.EMPTY, List.of());
        assertTrue(analysis.proofComplete(), analysis.blockers().toString());
        assertTrue(analysis.candidates().isEmpty(), "Durable projection publication must precede successor");
        var authorization = LegacyWorkstationProjectionAuthorization.authorize(
                analysis,
                LegacyWorkstationProjectionAuthorization.Disposition.AUTHORIZE_PROOF_COMPLETE_PUBLICATION,
                new RecoveryOperatorEvidence(
                        "butchercraft:operator/im_031c_r3c_successor",
                        RecoveryOperatorAuthority.ADMINISTRATOR,
                        "butchercraft:operator_evidence/im_031c_r3c/actual_historical_successor",
                        "sha256:3bbf36350f5034137b8a018b4a77169b9504f78ca409b854fe00ebda8a70ff41"
                )
        );
        Path generationOne = world.resolve(
                "butchercraft/checkpoints/generations/schema_1_sequence_00000000000000000001_tick_39872");
        String generationOneBefore = treeDigest(generationOne);
        Map<String, String> nativeBefore = nativeOwnerDigests(world);

        LegacyWorkstationProjectionSuccessorReport report =
                new LegacyWorkstationProjectionSuccessorService().publish(
                        world, RegistryAccess.EMPTY, List.of(), authorization);

        assertEquals("butchercraft:checkpoint/00000000000000000001/39872",
                report.predecessorGenerationId().canonicalValue());
        assertEquals("butchercraft:checkpoint/00000000000000000002/39872",
                report.successorGenerationId().canonicalValue());
        assertEquals(17, report.participantCount());
        assertEquals(16, report.preservedOwnerPayloadCount());
        assertEquals(REQUIRED_WORKSTATIONS, report.workstationSnapshot().requiredProjectionCount());
        assertEquals(REQUIRED_WORKSTATIONS, report.workstationSnapshot().availableProjectionCount());
        assertEquals(0, report.workstationSnapshot().loadedProjectionCount());
        assertEquals(REQUIRED_WORKSTATIONS, report.workstationSnapshot().unloadedProjectionCount());
        assertTrue(report.successorWorkstationReport().restorable());
        assertTrue(!report.historicalWorkstationReport().restorable());
        assertEquals(generationOneBefore, treeDigest(generationOne));
        assertEquals(nativeBefore, nativeOwnerDigests(world));
    }

    private Path configuredWorld() {
        String configured = System.getProperty("butchercraft.r3cWorldRoot");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(),
                "Set -Pbutchercraft.r3cWorldRoot for explicit actual-world R3C validation");
        Path world = Path.of(configured).toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isDirectory(world), "Configured R3C disposable world does not exist");
        return world;
    }

    private Map<String, String> nativeOwnerDigests(Path world) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        try (var paths = Files.walk(world)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(candidate -> !candidate.startsWith(world.resolve("butchercraft/checkpoints")))
                    .filter(candidate -> !candidate.startsWith(world.resolve("butchercraft/workstations")))
                    .filter(candidate -> !candidate.getFileName().toString().equals("session.lock"))
                    .sorted().toList()) {
                result.put(world.relativize(path).toString().replace('\\', '/'),
                        com.butchercraft.world.checkpoint.CheckpointSnapshotDigest.sha256(Files.readAllBytes(path)));
            }
        }
        return result;
    }

    private String treeDigest(Path root) throws Exception {
        List<String> values = new java.util.ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                byte[] bytes = Files.readAllBytes(path);
                values.add(root.relativize(path).toString().replace('\\', '/') + "\t" + bytes.length + "\t"
                        + com.butchercraft.world.checkpoint.CheckpointSnapshotDigest.sha256(bytes));
            }
        }
        return com.butchercraft.world.checkpoint.CheckpointSnapshotDigest.sha256(
                String.join("\n", values).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
