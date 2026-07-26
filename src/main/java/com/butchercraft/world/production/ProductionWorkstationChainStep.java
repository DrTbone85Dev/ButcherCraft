package com.butchercraft.world.production;

import java.util.Objects;
import java.util.Optional;

public record ProductionWorkstationChainStep(
        int schemaVersion,
        String stepIdentity,
        int stepOrder,
        String expectedWorkstationType,
        String processIdentity,
        String inputProductIdentity,
        String outputProductIdentity,
        ProductionChainStepStatus status,
        Optional<String> workstationIdentity,
        Optional<String> executionOperationIdentity,
        Optional<ProductionWorkstationCompletionEvidence> completionEvidence
) {
    public ProductionWorkstationChainStep {
        schemaVersion = ProductionValidation.requireSchema(schemaVersion, "production workstation chain step");
        stepIdentity = ProductionValidation.requireExternalIdentity(stepIdentity, "Production chain step identity");
        if (stepOrder < 0) {
            throw new IllegalArgumentException("Production chain step order must not be negative");
        }
        expectedWorkstationType = ProductionValidation.requireExternalIdentity(
                expectedWorkstationType,
                "Production chain workstation type"
        );
        processIdentity = ProductionValidation.requireExternalIdentity(processIdentity, "Production process identity");
        inputProductIdentity = ProductionValidation.requireExternalIdentity(
                inputProductIdentity,
                "Production input product identity"
        );
        outputProductIdentity = ProductionValidation.requireExternalIdentity(
                outputProductIdentity,
                "Production output product identity"
        );
        status = Objects.requireNonNull(status, "status");
        workstationIdentity = Objects.requireNonNull(workstationIdentity, "workstationIdentity")
                .map(value -> ProductionValidation.requireExternalIdentity(
                        value,
                        "Production workstation identity"
                ));
        executionOperationIdentity = Objects.requireNonNull(executionOperationIdentity, "executionOperationIdentity")
                .map(value -> ProductionValidation.requireExternalIdentity(
                        value,
                        "Execution operation identity"
                ));
        completionEvidence = Objects.requireNonNull(completionEvidence, "completionEvidence");
        final String normalizedProcessIdentity = processIdentity;
        final Optional<String> normalizedWorkstationIdentity = workstationIdentity;
        final Optional<String> normalizedExecutionOperationIdentity = executionOperationIdentity;
        if ((status == ProductionChainStepStatus.ASSIGNED
                || status == ProductionChainStepStatus.RUNNING
                || status == ProductionChainStepStatus.COMPLETE)
                && workstationIdentity.isEmpty()) {
            throw new IllegalArgumentException("Assigned Production chain steps require a workstation identity");
        }
        if ((status == ProductionChainStepStatus.RUNNING || status == ProductionChainStepStatus.COMPLETE)
                && executionOperationIdentity.isEmpty()) {
            throw new IllegalArgumentException("Running Production chain steps require an Execution operation identity");
        }
        completionEvidence.ifPresent(evidence -> {
            if (!evidence.digestMatches()) {
                throw new IllegalArgumentException("Production chain step completion evidence digest mismatch");
            }
            if (normalizedWorkstationIdentity.isEmpty()
                    || !normalizedWorkstationIdentity.orElseThrow().equals(evidence.workstationIdentity())
                    || !normalizedProcessIdentity.equals(evidence.processIdentity())) {
                throw new IllegalArgumentException("Production chain step completion evidence target mismatch");
            }
            if (normalizedExecutionOperationIdentity.isEmpty()
                    || !normalizedExecutionOperationIdentity.orElseThrow().equals(evidence.executionOperationIdentity())) {
                throw new IllegalArgumentException("Production chain step completion requires its Execution operation");
            }
        });
        if (status == ProductionChainStepStatus.COMPLETE && completionEvidence.isEmpty()) {
            throw new IllegalArgumentException("Complete Production chain steps require completion evidence");
        }
    }

    public static ProductionWorkstationChainStep awaiting(
            String stepIdentity,
            int stepOrder,
            String expectedWorkstationType,
            String processIdentity,
            String inputProductIdentity,
            String outputProductIdentity
    ) {
        return new ProductionWorkstationChainStep(
                ProductionSchema.CURRENT_VERSION,
                stepIdentity,
                stepOrder,
                expectedWorkstationType,
                processIdentity,
                inputProductIdentity,
                outputProductIdentity,
                ProductionChainStepStatus.AWAITING_ASSIGNMENT,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public boolean executionStarted() {
        return executionOperationIdentity.isPresent();
    }

    public boolean sameTarget(String workstationIdentity, String processIdentity) {
        String requiredWorkstation = ProductionValidation.requireExternalIdentity(
                workstationIdentity,
                "Production workstation identity"
        );
        String requiredProcess = ProductionValidation.requireExternalIdentity(
                processIdentity,
                "Production process identity"
        );
        return this.workstationIdentity.filter(requiredWorkstation::equals).isPresent()
                && this.processIdentity.equals(requiredProcess);
    }

    public ProductionWorkstationChainStep withAssignment(String workstationIdentity) {
        String normalizedWorkstation = ProductionValidation.requireExternalIdentity(
                workstationIdentity,
                "Production workstation identity"
        );
        if (this.workstationIdentity.isPresent()
                && !this.workstationIdentity.orElseThrow().equals(normalizedWorkstation)) {
            throw new IllegalStateException("Production chain step is already assigned to a different workstation");
        }
        if (completionEvidence.isPresent()) {
            return this;
        }
        return new ProductionWorkstationChainStep(
                schemaVersion,
                stepIdentity,
                stepOrder,
                expectedWorkstationType,
                processIdentity,
                inputProductIdentity,
                outputProductIdentity,
                executionOperationIdentity.isPresent()
                        ? ProductionChainStepStatus.RUNNING
                        : ProductionChainStepStatus.ASSIGNED,
                Optional.of(normalizedWorkstation),
                executionOperationIdentity,
                completionEvidence
        );
    }

    public ProductionWorkstationChainStep withExecutionOperation(
            String workstationIdentity,
            String processIdentity,
            String executionOperationIdentity
    ) {
        String normalizedExecution = ProductionValidation.requireExternalIdentity(
                executionOperationIdentity,
                "Execution operation identity"
        );
        if (!this.processIdentity.equals(ProductionValidation.requireExternalIdentity(
                processIdentity,
                "Production process identity"
        ))) {
            throw new IllegalStateException("Production chain step process mismatch");
        }
        ProductionWorkstationChainStep assigned = withAssignment(workstationIdentity);
        if (assigned.executionOperationIdentity.isPresent()
                && !assigned.executionOperationIdentity.orElseThrow().equals(normalizedExecution)) {
            throw new IllegalStateException("Production chain step is bound to a different Execution operation");
        }
        return new ProductionWorkstationChainStep(
                schemaVersion,
                stepIdentity,
                stepOrder,
                expectedWorkstationType,
                this.processIdentity,
                inputProductIdentity,
                outputProductIdentity,
                completionEvidence.isPresent() ? ProductionChainStepStatus.COMPLETE : ProductionChainStepStatus.RUNNING,
                assigned.workstationIdentity,
                Optional.of(normalizedExecution),
                completionEvidence
        );
    }

    public ProductionWorkstationChainStep withCompletionEvidence(
            ProductionWorkstationCompletionEvidence completionEvidence
    ) {
        ProductionWorkstationCompletionEvidence evidence = Objects.requireNonNull(
                completionEvidence,
                "completionEvidence"
        );
        if (this.completionEvidence.isPresent() && !this.completionEvidence.orElseThrow().equals(evidence)) {
            throw new IllegalStateException("Production chain step already has different completion evidence");
        }
        ProductionWorkstationChainStep bound = withExecutionOperation(
                evidence.workstationIdentity(),
                evidence.processIdentity(),
                evidence.executionOperationIdentity()
        );
        return new ProductionWorkstationChainStep(
                schemaVersion,
                stepIdentity,
                stepOrder,
                expectedWorkstationType,
                processIdentity,
                inputProductIdentity,
                outputProductIdentity,
                ProductionChainStepStatus.COMPLETE,
                bound.workstationIdentity,
                bound.executionOperationIdentity,
                Optional.of(evidence)
        );
    }

    public ProductionWorkstationChainStep withFailure(ProductionChainStepStatus terminalStatus) {
        if (terminalStatus != ProductionChainStepStatus.FAILED
                && terminalStatus != ProductionChainStepStatus.UNKNOWN_OUTCOME) {
            throw new IllegalArgumentException("Production chain step failure status is invalid");
        }
        return new ProductionWorkstationChainStep(
                schemaVersion,
                stepIdentity,
                stepOrder,
                expectedWorkstationType,
                processIdentity,
                inputProductIdentity,
                outputProductIdentity,
                terminalStatus,
                workstationIdentity,
                executionOperationIdentity,
                completionEvidence
        );
    }
}
