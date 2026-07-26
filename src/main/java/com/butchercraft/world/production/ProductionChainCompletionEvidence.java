package com.butchercraft.world.production;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public record ProductionChainCompletionEvidence(
        int schemaVersion,
        ProductionRunId runId,
        String chainIdentity,
        String firstStepIdentity,
        String firstStepCompletionEvidenceIdentity,
        String firstStepOutputProductIdentity,
        String secondStepIdentity,
        String secondStepCompletionEvidenceIdentity,
        String secondStepInputProductIdentity,
        long completedSimulationTick,
        String evidenceIdentity,
        String evidenceContentDigest
) {
    private static final String EVIDENCE_PREFIX = "butchercraft:production_chain_completion/v";

    public ProductionChainCompletionEvidence {
        schemaVersion = ProductionValidation.requireSchema(schemaVersion, "production chain completion evidence");
        runId = Objects.requireNonNull(runId, "runId");
        chainIdentity = ProductionValidation.requireExternalIdentity(chainIdentity, "Production chain identity");
        firstStepIdentity = ProductionValidation.requireExternalIdentity(
                firstStepIdentity,
                "First Production chain step identity"
        );
        firstStepCompletionEvidenceIdentity = ProductionValidation.requireExternalIdentity(
                firstStepCompletionEvidenceIdentity,
                "First Production chain step completion evidence identity"
        );
        firstStepOutputProductIdentity = ProductionValidation.requireExternalIdentity(
                firstStepOutputProductIdentity,
                "First Production chain output product identity"
        );
        secondStepIdentity = ProductionValidation.requireExternalIdentity(
                secondStepIdentity,
                "Second Production chain step identity"
        );
        secondStepCompletionEvidenceIdentity = ProductionValidation.requireExternalIdentity(
                secondStepCompletionEvidenceIdentity,
                "Second Production chain step completion evidence identity"
        );
        secondStepInputProductIdentity = ProductionValidation.requireExternalIdentity(
                secondStepInputProductIdentity,
                "Second Production chain input product identity"
        );
        if (!firstStepOutputProductIdentity.equals(secondStepInputProductIdentity)) {
            throw new IllegalArgumentException("Production chain completion evidence product flow mismatch");
        }
        if (completedSimulationTick < 0L) {
            throw new IllegalArgumentException("Production chain completion tick must not be negative");
        }
        evidenceIdentity = ProductionValidation.requireExternalIdentity(
                evidenceIdentity,
                "Production chain completion evidence identity"
        );
        evidenceContentDigest = ProductionValidation.requireDigest(
                evidenceContentDigest,
                "Production chain completion evidence digest"
        );
        String expectedPrefix = EVIDENCE_PREFIX + ProductionSchema.CURRENT_VERSION + "/";
        if (!evidenceIdentity.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("Production chain completion evidence identity has unsupported prefix");
        }
    }

    public static ProductionChainCompletionEvidence published(
            ProductionRunId runId,
            ProductionWorkstationChain chain,
            long completedSimulationTick
    ) {
        Objects.requireNonNull(chain, "chain");
        if (!chain.allStepsComplete()) {
            throw new IllegalArgumentException("Production chain completion requires all steps to be complete");
        }
        ProductionWorkstationChainStep first = chain.steps().get(0);
        ProductionWorkstationChainStep second = chain.steps().get(1);
        ProductionChainCompletionEvidence seed = new ProductionChainCompletionEvidence(
                ProductionSchema.CURRENT_VERSION,
                runId,
                chain.chainIdentity(),
                first.stepIdentity(),
                first.completionEvidence().orElseThrow().evidenceIdentity(),
                first.outputProductIdentity(),
                second.stepIdentity(),
                second.completionEvidence().orElseThrow().evidenceIdentity(),
                second.inputProductIdentity(),
                completedSimulationTick,
                EVIDENCE_PREFIX + ProductionSchema.CURRENT_VERSION + "/" + "0".repeat(64),
                zeroDigest()
        );
        String digest = seed.calculateDigest();
        return new ProductionChainCompletionEvidence(
                seed.schemaVersion,
                seed.runId,
                seed.chainIdentity,
                seed.firstStepIdentity,
                seed.firstStepCompletionEvidenceIdentity,
                seed.firstStepOutputProductIdentity,
                seed.secondStepIdentity,
                seed.secondStepCompletionEvidenceIdentity,
                seed.secondStepInputProductIdentity,
                seed.completedSimulationTick,
                EVIDENCE_PREFIX + ProductionSchema.CURRENT_VERSION + "/" + digestIdSuffix(digest),
                digest
        );
    }

    public boolean digestMatches() {
        return evidenceContentDigest.equals(calculateDigest());
    }

    public String calculateDigest() {
        CanonicalDigest digest = CanonicalDigest.create("butchercraft:production_chain_completion")
                .add(schemaVersion)
                .add(runId.value())
                .add(chainIdentity)
                .add(firstStepIdentity)
                .add(firstStepCompletionEvidenceIdentity)
                .add(firstStepOutputProductIdentity)
                .add(secondStepIdentity)
                .add(secondStepCompletionEvidenceIdentity)
                .add(secondStepInputProductIdentity)
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
