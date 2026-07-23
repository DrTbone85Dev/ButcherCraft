package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

final class AllocationSetEvaluator {
    AllocationSetEvaluationResult evaluate(
            int canonicalPosition,
            AllocationSetDefinition set,
            AllocationCycleInput input,
            WorkingCapacityLedger ledger
    ) {
        AllocationSetDefinition candidate = AllocationValidation.required(set, "set");
        AllocationCycleInput source = AllocationValidation.required(input, "input");
        String parentBefore = ledger.digest();
        if (candidate.expirationSimulationTick().isPresent()
                && candidate.expirationSimulationTick().getAsLong()
                < source.context().simulationTick()) {
            AllocationCycleFailure failure = new AllocationCycleFailure(
                    AllocationCycleFailureCode.SET_EXPIRED,
                    AllocationCycleFailureScope.ALLOCATION_SET,
                    candidate.id().value(),
                    "AllocationSet expired before this cycle"
            );
            return failedUnevaluated(
                    canonicalPosition,
                    candidate,
                    source,
                    parentBefore,
                    failure
            );
        }

        WorkingCapacityLedger.Branch branch = ledger.branch();
        List<AllocationRequirementEvaluation> evaluations = new ArrayList<>();
        List<AllocationCommitmentDefinition> commitments = new ArrayList<>();
        List<AllocationConflictRecord> conflicts = new ArrayList<>();
        List<AllocationCycleFailure> failures = new ArrayList<>();
        List<RequirementDefinition> requirements = candidate.requirementIds().stream()
                .map(id -> source.definitions().findRequirement(id).orElseThrow())
                .sorted()
                .toList();

        AllocationSetEvaluationOutcome outcome = AllocationSetEvaluationOutcome.ALLOCATABLE;
        int evaluatedCount = 0;
        for (RequirementDefinition requirement : requirements) {
            String before = branch.digest();
            LedgerReservationResult reservation = branch.reserve(
                    source.context().cycleId(),
                    candidate.id(),
                    requirement
            );
            evaluatedCount++;
            if (reservation.accepted()) {
                AllocationLedgerEntryView selected = reservation.entry().orElseThrow();
                AllocationCommitmentDefinition commitment =
                        AllocationCommitmentDefinition.create(
                                source.context().cycleId(),
                                requirement,
                                selected.capacityKey().resourceId(),
                                selected.capacityId(),
                                requirement.requiredQuantity(),
                                source.context().simulationTick(),
                                candidate.expirationSimulationTick(),
                                observationReferences(selected),
                                AllocationMetadata.empty()
                        );
                commitments.add(commitment);
                evaluations.add(new AllocationRequirementEvaluation(
                        requirement.id(),
                        AllocationRequirementEvaluationOutcome.SATISFIED,
                        Optional.of(selected.capacityKey().resourceId()),
                        Optional.of(selected.capacityId()),
                        Optional.of(requirement.requiredQuantity()),
                        Optional.empty(),
                        Optional.empty(),
                        before,
                        branch.digest()
                ));
                continue;
            }

            AllocationCycleFailure failure = reservation.failure().orElseThrow();
            failures.add(failure);
            reservation.conflict().ifPresent(conflicts::add);
            boolean waiting = failure.code()
                    == AllocationCycleFailureCode.CAPACITY_UNAVAILABLE
                    || failure.code() == AllocationCycleFailureCode.EXCLUSIVE_CONFLICT;
            outcome = waiting
                    ? AllocationSetEvaluationOutcome.WAITING
                    : AllocationSetEvaluationOutcome.FAILED;
            evaluations.add(new AllocationRequirementEvaluation(
                    requirement.id(),
                    waiting
                            ? AllocationRequirementEvaluationOutcome.WAITING
                            : AllocationRequirementEvaluationOutcome.REJECTED,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(failure),
                    reservation.conflict(),
                    before,
                    before
            ));
            break;
        }

        if (outcome != AllocationSetEvaluationOutcome.ALLOCATABLE) {
            String discardedDigest = parentBefore;
            for (int index = evaluatedCount; index < requirements.size(); index++) {
                evaluations.add(new AllocationRequirementEvaluation(
                        requirements.get(index).id(),
                        AllocationRequirementEvaluationOutcome.NOT_EVALUATED,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        discardedDigest,
                        discardedDigest
                ));
            }
            return new AllocationSetEvaluationResult(
                    candidate.id(),
                    outcome,
                    evaluations,
                    List.of(),
                    conflicts,
                    failures,
                    parentBefore,
                    parentBefore,
                    canonicalPosition
            );
        }

        branch.merge();
        String parentAfter = ledger.digest();
        return new AllocationSetEvaluationResult(
                candidate.id(),
                outcome,
                evaluations,
                commitments,
                conflicts,
                failures,
                parentBefore,
                parentAfter,
                canonicalPosition
        );
    }

    private static AllocationSetEvaluationResult failedUnevaluated(
            int canonicalPosition,
            AllocationSetDefinition set,
            AllocationCycleInput input,
            String ledgerDigest,
            AllocationCycleFailure failure
    ) {
        List<AllocationRequirementEvaluation> evaluations =
                set.requirementIds().stream()
                        .map(id -> new AllocationRequirementEvaluation(
                                input.definitions().findRequirement(id)
                                        .orElseThrow().id(),
                                AllocationRequirementEvaluationOutcome.NOT_EVALUATED,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                ledgerDigest,
                                ledgerDigest
                        ))
                        .toList();
        return new AllocationSetEvaluationResult(
                set.id(),
                AllocationSetEvaluationOutcome.FAILED,
                evaluations,
                List.of(),
                List.of(),
                List.of(failure),
                ledgerDigest,
                ledgerDigest,
                canonicalPosition
        );
    }

    private static List<ExternalReference> observationReferences(
            AllocationLedgerEntryView selected
    ) {
        TreeSet<ExternalReference> references = new TreeSet<>();
        references.add(selected.resourceObservationReference());
        references.add(selected.capacityObservationReference());
        return List.copyOf(references);
    }
}
