package com.butchercraft.world.planning;

import java.util.Objects;
import java.util.Set;

public record PlanningSelectionPolicy(
        PlanningPolicyId id,
        int policyVersion,
        Set<NeedType> supportedNeedTypes,
        Set<PlanCategory> supportedCategories,
        String comparatorContractId,
        boolean allowOverproduction,
        boolean aggregateCompatibleNeeds,
        boolean splitByProcessBatch,
        boolean immediateAndShortExecutableOnly,
        int perNeedApprovalLimit,
        int perCycleApprovalLimit,
        int schemaVersion
) {
    public static final PlanningPolicyId DEFAULT_ID =
            PlanningPolicyId.of("butchercraft:default_business_production_planning");

    public PlanningSelectionPolicy {
        Objects.requireNonNull(id);
        if (policyVersion <= 0) throw new IllegalArgumentException("Planning policy version must be positive");
        supportedNeedTypes = Set.copyOf(supportedNeedTypes);
        supportedCategories = Set.copyOf(supportedCategories);
        comparatorContractId = PlanningValidation.id(comparatorContractId, "Planning comparator contract");
        if (perNeedApprovalLimit <= 0 || perCycleApprovalLimit <= 0) {
            throw new IllegalArgumentException("Planning approval limits must be positive");
        }
        schemaVersion = PlanningValidation.schema(schemaVersion);
    }

    public static PlanningSelectionPolicy standard() {
        return new PlanningSelectionPolicy(
                DEFAULT_ID, 1, Set.of(NeedType.OUTSTANDING_ORDER_LINE),
                Set.of(PlanCategory.PRODUCTION), "butchercraft:planning_candidate_v1",
                true, false, true, true, 16, 5_000, PlanningValidation.SCHEMA_VERSION
        );
    }
}
