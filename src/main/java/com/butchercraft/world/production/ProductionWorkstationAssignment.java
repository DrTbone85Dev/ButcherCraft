package com.butchercraft.world.production;

import java.util.Objects;
import java.util.Optional;

public record ProductionWorkstationAssignment(
        int schemaVersion,
        String workstationIdentity,
        String processIdentity,
        Optional<String> executionOperationIdentity,
        Optional<ProductionWorkstationCompletionEvidence> completionEvidence
) {
    public ProductionWorkstationAssignment {
        schemaVersion = ProductionValidation.requireSchema(schemaVersion, "production workstation assignment");
        workstationIdentity = ProductionValidation.requireExternalIdentity(
                workstationIdentity,
                "Production workstation identity"
        );
        processIdentity = ProductionValidation.requireExternalIdentity(processIdentity, "Production process identity");
        executionOperationIdentity = Objects.requireNonNull(executionOperationIdentity, "executionOperationIdentity")
                .map(value -> ProductionValidation.requireExternalIdentity(value, "Execution operation identity"));
        completionEvidence = Objects.requireNonNull(completionEvidence, "completionEvidence");
        String normalizedWorkstationIdentity = workstationIdentity;
        String normalizedProcessIdentity = processIdentity;
        Optional<String> normalizedExecutionOperationIdentity = executionOperationIdentity;
        completionEvidence.ifPresent(evidence -> {
            if (!evidence.digestMatches()) {
                throw new IllegalArgumentException("Production workstation completion evidence digest mismatch");
            }
            if (!evidence.workstationIdentity().equals(normalizedWorkstationIdentity)
                    || !evidence.processIdentity().equals(normalizedProcessIdentity)) {
                throw new IllegalArgumentException("Production workstation completion evidence target mismatch");
            }
            if (normalizedExecutionOperationIdentity.isEmpty()
                    || !normalizedExecutionOperationIdentity.orElseThrow()
                    .equals(evidence.executionOperationIdentity())) {
                throw new IllegalArgumentException("Production workstation completion requires its Execution operation");
            }
        });
    }

    public static ProductionWorkstationAssignment assigned(String workstationIdentity, String processIdentity) {
        return new ProductionWorkstationAssignment(
                ProductionSchema.CURRENT_VERSION,
                workstationIdentity,
                processIdentity,
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
        return this.workstationIdentity.equals(requiredWorkstation) && this.processIdentity.equals(requiredProcess);
    }

    public ProductionWorkstationAssignment withExecutionOperation(String executionOperationIdentity) {
        String normalized = ProductionValidation.requireExternalIdentity(
                executionOperationIdentity,
                "Execution operation identity"
        );
        if (this.executionOperationIdentity.isPresent()
                && !this.executionOperationIdentity.orElseThrow().equals(normalized)) {
            throw new IllegalStateException("Production workstation assignment is bound to a different Execution operation");
        }
        return new ProductionWorkstationAssignment(
                schemaVersion,
                workstationIdentity,
                processIdentity,
                Optional.of(normalized),
                completionEvidence
        );
    }

    public ProductionWorkstationAssignment withCompletionEvidence(
            ProductionWorkstationCompletionEvidence completionEvidence
    ) {
        ProductionWorkstationCompletionEvidence evidence = Objects.requireNonNull(
                completionEvidence,
                "completionEvidence"
        );
        if (this.completionEvidence.isPresent() && !this.completionEvidence.orElseThrow().equals(evidence)) {
            throw new IllegalStateException("Production workstation assignment already has different completion evidence");
        }
        return new ProductionWorkstationAssignment(
                schemaVersion,
                workstationIdentity,
                processIdentity,
                Optional.of(evidence.executionOperationIdentity()),
                Optional.of(evidence)
        );
    }
}
