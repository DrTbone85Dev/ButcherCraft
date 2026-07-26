package com.butchercraft.world.production;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public record ProductionWorkstationCompletionEvidence(
        int schemaVersion,
        ProductionRunId runId,
        String workstationIdentity,
        String processIdentity,
        String executionOperationIdentity,
        String executionTerminalStatus,
        String ownerResultIdentity,
        String ownerResultContentDigest,
        String executionResultEvidenceIdentity,
        String executionResultContentDigest,
        long completedSimulationTick,
        String evidenceIdentity,
        String evidenceContentDigest
) {
    private static final String EVIDENCE_PREFIX = "butchercraft:production_completion/v";

    public ProductionWorkstationCompletionEvidence {
        schemaVersion = ProductionValidation.requireSchema(schemaVersion, "production workstation completion evidence");
        runId = Objects.requireNonNull(runId, "runId");
        workstationIdentity = ProductionValidation.requireExternalIdentity(
                workstationIdentity,
                "Production workstation identity"
        );
        processIdentity = ProductionValidation.requireExternalIdentity(processIdentity, "Production process identity");
        executionOperationIdentity = ProductionValidation.requireExternalIdentity(
                executionOperationIdentity,
                "Execution operation identity"
        );
        executionTerminalStatus = ProductionValidation.requireExternalIdentity(
                executionTerminalStatus,
                "Execution terminal status identity"
        );
        ownerResultIdentity = ProductionValidation.requireExternalIdentity(
                ownerResultIdentity,
                "Workstation owner result identity"
        );
        ownerResultContentDigest = ProductionValidation.requireDigest(
                ownerResultContentDigest,
                "Workstation owner result content digest"
        );
        executionResultEvidenceIdentity = ProductionValidation.requireExternalIdentity(
                executionResultEvidenceIdentity,
                "Execution result evidence identity"
        );
        executionResultContentDigest = ProductionValidation.requireDigest(
                executionResultContentDigest,
                "Execution result content digest"
        );
        if (completedSimulationTick < 0L) {
            throw new IllegalArgumentException("Production workstation completion tick must not be negative");
        }
        evidenceIdentity = ProductionValidation.requireExternalIdentity(
                evidenceIdentity,
                "Production workstation completion evidence identity"
        );
        evidenceContentDigest = ProductionValidation.requireDigest(
                evidenceContentDigest,
                "Production workstation completion evidence digest"
        );
        String expectedPrefix = EVIDENCE_PREFIX + ProductionSchema.CURRENT_VERSION + "/";
        if (!evidenceIdentity.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("Production workstation completion evidence identity has unsupported prefix");
        }
    }

    public static ProductionWorkstationCompletionEvidence published(
            ProductionRunId runId,
            String workstationIdentity,
            String processIdentity,
            String executionOperationIdentity,
            String executionTerminalStatus,
            String ownerResultIdentity,
            String ownerResultContentDigest,
            String executionResultEvidenceIdentity,
            String executionResultContentDigest,
            long completedSimulationTick
    ) {
        ProductionWorkstationCompletionEvidence seed = new ProductionWorkstationCompletionEvidence(
                ProductionSchema.CURRENT_VERSION,
                runId,
                workstationIdentity,
                processIdentity,
                executionOperationIdentity,
                executionTerminalStatus,
                ownerResultIdentity,
                ownerResultContentDigest,
                executionResultEvidenceIdentity,
                executionResultContentDigest,
                completedSimulationTick,
                EVIDENCE_PREFIX + ProductionSchema.CURRENT_VERSION + "/" + "0".repeat(64),
                zeroDigest()
        );
        String digest = seed.calculateDigest();
        return new ProductionWorkstationCompletionEvidence(
                seed.schemaVersion,
                seed.runId,
                seed.workstationIdentity,
                seed.processIdentity,
                seed.executionOperationIdentity,
                seed.executionTerminalStatus,
                seed.ownerResultIdentity,
                seed.ownerResultContentDigest,
                seed.executionResultEvidenceIdentity,
                seed.executionResultContentDigest,
                seed.completedSimulationTick,
                EVIDENCE_PREFIX + ProductionSchema.CURRENT_VERSION + "/" + digestIdSuffix(digest),
                digest
        );
    }

    public boolean digestMatches() {
        return evidenceContentDigest.equals(calculateDigest());
    }

    public String calculateDigest() {
        CanonicalDigest digest = CanonicalDigest.create("butchercraft:production_workstation_completion");
        digest.add(schemaVersion)
                .add(runId.value())
                .add(workstationIdentity)
                .add(processIdentity)
                .add(executionOperationIdentity)
                .add(executionTerminalStatus)
                .add(ownerResultIdentity)
                .add(ownerResultContentDigest)
                .add(executionResultEvidenceIdentity)
                .add(executionResultContentDigest)
                .add(completedSimulationTick);
        return digest.finish();
    }

    private static String zeroDigest() {
        return "sha256:" + "0".repeat(64);
    }

    private static String digestIdSuffix(String digest) {
        return ProductionValidation.requireDigest(digest, "digest").substring("sha256:".length());
    }

    private static final class CanonicalDigest {
        private final MessageDigest digest;

        private CanonicalDigest(String domain) {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is required", exception);
            }
            add(domain);
        }

        static CanonicalDigest create(String domain) {
            return new CanonicalDigest(domain);
        }

        CanonicalDigest add(String value) {
            byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
            digest.update((byte) bytes.length);
            digest.update((byte) (bytes.length >>> 8));
            digest.update((byte) (bytes.length >>> 16));
            digest.update((byte) (bytes.length >>> 24));
            digest.update(bytes);
            return this;
        }

        CanonicalDigest add(long value) {
            return add(Long.toString(value));
        }

        CanonicalDigest add(int value) {
            return add(Integer.toString(value));
        }

        String finish() {
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        }
    }
}
