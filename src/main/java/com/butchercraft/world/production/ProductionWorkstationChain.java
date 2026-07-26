package com.butchercraft.world.production;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ProductionWorkstationChain(
        int schemaVersion,
        String chainIdentity,
        ProductionWorkstationChainStatus status,
        List<ProductionWorkstationChainStep> steps,
        Optional<ProductionChainCompletionEvidence> completionEvidence
) {
    private static final String CHAIN_PREFIX = "butchercraft:production_chain/v";
    private static final String STEP_PREFIX = "butchercraft:production_chain_step/v";

    public ProductionWorkstationChain {
        schemaVersion = ProductionValidation.requireSchema(schemaVersion, "production workstation chain");
        chainIdentity = ProductionValidation.requireExternalIdentity(chainIdentity, "Production chain identity");
        status = Objects.requireNonNull(status, "status");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        completionEvidence = Objects.requireNonNull(completionEvidence, "completionEvidence");
        if (steps.size() != 2) {
            throw new IllegalArgumentException("IM-018 Production chains are limited to exactly two ordered steps");
        }
        for (int index = 0; index < steps.size(); index++) {
            ProductionWorkstationChainStep step = steps.get(index);
            if (step.stepOrder() != index) {
                throw new IllegalArgumentException("Production chain steps must be sequential");
            }
        }
        if (!steps.get(0).outputProductIdentity().equals(steps.get(1).inputProductIdentity())) {
            throw new IllegalArgumentException("Production chain product flow does not match");
        }
        if (status == ProductionWorkstationChainStatus.COMPLETE) {
            if (completionEvidence.isEmpty()
                    || steps.stream().anyMatch(step -> step.status() != ProductionChainStepStatus.COMPLETE)) {
                throw new IllegalArgumentException("Complete Production chains require complete steps and evidence");
            }
        }
        final String normalizedChainIdentity = chainIdentity;
        completionEvidence.ifPresent(evidence -> {
            if (!evidence.chainIdentity().equals(normalizedChainIdentity)) {
                throw new IllegalArgumentException("Production chain completion evidence identity mismatch");
            }
            if (!evidence.digestMatches()) {
                throw new IllegalArgumentException("Production chain completion evidence digest mismatch");
            }
        });
    }

    public static ProductionWorkstationChain beefPattyChain(ProductionRunId runId) {
        Objects.requireNonNull(runId, "runId");
        String chainIdentity = CHAIN_PREFIX + ProductionSchema.CURRENT_VERSION + "/" + suffix(runId.value());
        return new ProductionWorkstationChain(
                ProductionSchema.CURRENT_VERSION,
                chainIdentity,
                ProductionWorkstationChainStatus.AWAITING_GRINDER_ASSIGNMENT,
                List.of(
                        ProductionWorkstationChainStep.awaiting(
                                stepIdentity(chainIdentity, "grinder"),
                                0,
                                "butchercraft:workstation/grinder",
                                "butchercraft:grind_beef",
                                "butchercraft:beef_trim",
                                "butchercraft:ground_beef"
                        ),
                        ProductionWorkstationChainStep.awaiting(
                                stepIdentity(chainIdentity, "patty_former"),
                                1,
                                "butchercraft:workstation/patty_former",
                                "butchercraft:form_beef_patties",
                                "butchercraft:ground_beef",
                                "butchercraft:beef_patties"
                        )
                ),
                Optional.empty()
        );
    }

    public ProductionWorkstationChainStep step(String stepIdentity) {
        String normalized = ProductionValidation.requireExternalIdentity(stepIdentity, "Production chain step identity");
        return steps.stream()
                .filter(step -> step.stepIdentity().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Production chain step: " + normalized));
    }

    public ProductionWorkstationChain withStepAssignment(String stepIdentity, String workstationIdentity) {
        ProductionWorkstationChainStep step = step(stepIdentity);
        return withStep(step.withAssignment(workstationIdentity), statusAfterAssignment(step.stepOrder()));
    }

    public ProductionWorkstationChain withStepExecution(
            String stepIdentity,
            String workstationIdentity,
            String processIdentity,
            String executionOperationIdentity
    ) {
        ProductionWorkstationChainStep step = step(stepIdentity);
        return withStep(
                step.withExecutionOperation(workstationIdentity, processIdentity, executionOperationIdentity),
                statusAfterExecution(step.stepOrder())
        );
    }

    public ProductionWorkstationChain withStepCompletion(
            String stepIdentity,
            ProductionWorkstationCompletionEvidence evidence
    ) {
        ProductionWorkstationChainStep step = step(stepIdentity);
        ProductionWorkstationChain updated = withStep(
                step.withCompletionEvidence(evidence),
                statusAfterCompletion(step.stepOrder())
        );
        if (updated.allStepsComplete()) {
            return updated;
        }
        return updated.withStatus(ProductionWorkstationChainStatus.AWAITING_MANUAL_TRANSFER);
    }

    public ProductionWorkstationChain withCompletionEvidence(ProductionChainCompletionEvidence evidence) {
        ProductionChainCompletionEvidence normalized = Objects.requireNonNull(evidence, "evidence");
        return new ProductionWorkstationChain(
                schemaVersion,
                chainIdentity,
                ProductionWorkstationChainStatus.COMPLETE,
                steps,
                Optional.of(normalized)
        );
    }

    public ProductionWorkstationChain withFailure(String stepIdentity, ProductionChainStepStatus stepStatus) {
        ProductionWorkstationChainStep step = step(stepIdentity);
        ProductionWorkstationChainStatus chainStatus = stepStatus == ProductionChainStepStatus.UNKNOWN_OUTCOME
                ? ProductionWorkstationChainStatus.UNKNOWN_OUTCOME
                : ProductionWorkstationChainStatus.FAILED;
        return withStep(step.withFailure(stepStatus), chainStatus);
    }

    public ProductionWorkstationChain cancelledBeforeFirstEffect() {
        if (hasStartedExecution()) {
            throw new IllegalStateException("Cannot cancel a Production chain after workstation Execution begins");
        }
        return withStatus(ProductionWorkstationChainStatus.CANCELLED_BEFORE_FIRST_EFFECT);
    }

    public boolean hasStartedExecution() {
        return steps.stream().anyMatch(ProductionWorkstationChainStep::executionStarted);
    }

    public boolean allStepsComplete() {
        return steps.stream().allMatch(step -> step.status() == ProductionChainStepStatus.COMPLETE);
    }

    public boolean productFlowMatches() {
        return steps.get(0).outputProductIdentity().equals(steps.get(1).inputProductIdentity());
    }

    private ProductionWorkstationChain withStep(
            ProductionWorkstationChainStep updated,
            ProductionWorkstationChainStatus updatedStatus
    ) {
        List<ProductionWorkstationChainStep> updatedSteps = steps.stream()
                .map(step -> step.stepIdentity().equals(updated.stepIdentity()) ? updated : step)
                .toList();
        return new ProductionWorkstationChain(
                schemaVersion,
                chainIdentity,
                updatedStatus,
                updatedSteps,
                completionEvidence
        );
    }

    private ProductionWorkstationChain withStatus(ProductionWorkstationChainStatus updatedStatus) {
        return new ProductionWorkstationChain(schemaVersion, chainIdentity, updatedStatus, steps, completionEvidence);
    }

    private static ProductionWorkstationChainStatus statusAfterAssignment(int stepOrder) {
        return stepOrder == 0
                ? ProductionWorkstationChainStatus.GRINDER_ASSIGNED
                : ProductionWorkstationChainStatus.PATTY_FORMER_ASSIGNED;
    }

    private static ProductionWorkstationChainStatus statusAfterExecution(int stepOrder) {
        return stepOrder == 0
                ? ProductionWorkstationChainStatus.GRINDER_RUNNING
                : ProductionWorkstationChainStatus.PATTY_FORMER_RUNNING;
    }

    private static ProductionWorkstationChainStatus statusAfterCompletion(int stepOrder) {
        return stepOrder == 0
                ? ProductionWorkstationChainStatus.GRINDER_COMPLETE
                : ProductionWorkstationChainStatus.PATTY_FORMER_COMPLETE;
    }

    private static String stepIdentity(String chainIdentity, String stepName) {
        String suffix = suffix(chainIdentity + "/" + stepName);
        return STEP_PREFIX + ProductionSchema.CURRENT_VERSION + "/" + suffix;
    }

    private static String suffix(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            digest.update(bytes);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
