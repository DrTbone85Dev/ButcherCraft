package com.butchercraft.workstation.condition;

import com.butchercraft.workstation.projection.DurableWorkstationProjection;
import com.butchercraft.workstation.projection.WorkstationProjectionStatus;
import java.util.List;
import java.util.function.Function;

/** Mechanical fault is coherent owner state; it is deliberately not a recovery failure. */
public final class ConditionCoherenceValidator {
    public record Result(ConditionCoherenceCode code, String detail) {
        public boolean coherent() { return code == ConditionCoherenceCode.COHERENT; }
    }

    private ConditionCoherenceValidator() { }

    public static Result validate(DurableWorkstationProjection projection, MachineConditionPolicyRegistry policies,
            Function<String, ConditionEffectReceipt> resolver) {
        if (projection.condition().isEmpty()) return new Result(ConditionCoherenceCode.LEGACY_INITIALIZATION_REQUIRED,
                "Historical projection requires explicit condition initialization at a safe boundary");
        var condition = projection.condition().orElseThrow();
        if (condition.pendingOperatingTransition().isPresent()) return new Result(ConditionCoherenceCode.RECOVERY_REQUIRED,
                "Condition and operating-state publication require exact transition reconciliation");
        if (policies.forMachine(projection.endpointKey().workstationTypeIdentity()).isPresent() != condition.state().isPresent()) {
            return new Result(ConditionCoherenceCode.POLICY_CONFLICT, "Condition applicability differs from exact machine type");
        }
        if (condition.state().filter(state -> !state.instanceId().equals(projection.instanceId())).isPresent()) {
            return new Result(ConditionCoherenceCode.IDENTITY_CONFLICT, "Condition targets another instance");
        }
        if (projection.status() == WorkstationProjectionStatus.TOMBSTONED
                && condition.state().flatMap(MachineConditionState::activeExposure).isPresent()) {
            return new Result(ConditionCoherenceCode.RECOVERY_REQUIRED, "Retired machine retains an active exposure");
        }
        List<ConditionEffectReceipt> receipts;
        try {
            receipts = ConditionEvidenceClosure.verify(condition, resolver);
        } catch (UnsupportedConditionSchemaException exception) {
            return new Result(ConditionCoherenceCode.UNSUPPORTED_SCHEMA, exception.getMessage());
        } catch (RuntimeException exception) {
            return new Result(ConditionCoherenceCode.EFFECT_EVIDENCE_MISSING, exception.getMessage());
        }
        try {
            condition.state().ifPresent(state -> policies.require(state.policy().identity(), projection.endpointKey().workstationTypeIdentity()));
            receipts.forEach(receipt -> policies.require(receipt.postState().policy().identity(), projection.endpointKey().workstationTypeIdentity()));
        } catch (RuntimeException exception) {
            return new Result(ConditionCoherenceCode.POLICY_CONFLICT, exception.getMessage());
        }
        if (projection.processingOwnerResultIdentity().filter(id -> id.startsWith("butchercraft:workstation_result/v2/")).isPresent()
                && receipts.stream().noneMatch(receipt -> receipt.kind() == ConditionEffectKind.SUCCESSFUL_PROCESSING
                && ConditionProcessingCandidates.resultIdentity(receipt).equals(projection.processingOwnerResultIdentity().orElseThrow()))) {
            return new Result(ConditionCoherenceCode.EFFECT_EVIDENCE_MISSING, "Joint processing result has no exact condition receipt");
        }
        return new Result(ConditionCoherenceCode.COHERENT, "Exact condition and required evidence verified");
    }
}
