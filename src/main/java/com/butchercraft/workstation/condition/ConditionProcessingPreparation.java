package com.butchercraft.workstation.condition;

import java.util.Objects;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/** The exact pre-state and immutable policy freeze the mechanical consequence before Execution authorization. */
public record ConditionProcessingPreparation(MachineConditionState preState, String freshnessIdentity,
        Consequence consequence) {
    public record Consequence(long mechanicalLoss, long serviceDebt, List<ConditionExposureAccount> accounts,
            Optional<ConditionFault.Type> faultType) {
        public Consequence {
            accounts = List.copyOf(accounts);
            Objects.requireNonNull(faultType, "faultType");
        }
    }

    public ConditionProcessingPreparation {
        Objects.requireNonNull(preState, "preState");
        if (!freshness(preState).equals(freshnessIdentity)) throw new IllegalArgumentException("Condition freshness mismatch");
        if (preState.eligibility() == MachineConditionState.Eligibility.DENIED_BY_FAULT
                || preState.eligibility() == MachineConditionState.Eligibility.RETIRED
                || preState.eligibility() == MachineConditionState.Eligibility.EXPOSURE_SETTLEMENT_REQUIRED) {
            throw new IllegalArgumentException("Condition does not authorize child preparation");
        }
        if (!calculate(preState).equals(consequence)) throw new IllegalArgumentException("Frozen condition consequence mismatch");
    }

    public static ConditionProcessingPreparation of(MachineConditionState state) {
        return new ConditionProcessingPreparation(state, freshness(state), calculate(state));
    }

    private static Consequence calculate(MachineConditionState before) {
        long loss = BigInteger.valueOf(before.mechanicalLoss()).add(BigInteger.valueOf(before.policy().processingLoss()))
                .min(BigInteger.valueOf(before.policy().maximumLoss())).longValueExact();
        long debt = Math.addExact(before.serviceDebt(), before.policy().processingServiceDebt());
        var accounts = before.exposureAccounts().stream().map(account ->
                before.policy().exposure(account.type()).filter(ConditionExposurePolicy::resetGraceOnSuccessfulProcessing)
                        .map(ignored -> new ConditionExposureAccount(account.type(), account.eligibleTicks(), 0,
                                account.remainder())).orElse(account)).toList();
        return new Consequence(loss, debt, accounts, before.policy().faultLoss().filter(threshold -> loss >= threshold)
                .map(ignored -> ConditionFault.Type.WEAR_LIMIT_REACHED));
    }

    public MachineConditionState complete(String effect, long authoritativeTick) {
        var fault = consequence.faultType().map(type -> ConditionFault.create(preState.instanceId(), type, effect,
                Math.incrementExact(preState.revision()), preState.policy().identity(), authoritativeTick));
        return preState.successor(effect, consequence.mechanicalLoss(), consequence.serviceDebt(), consequence.accounts(),
                Optional.empty(), fault, authoritativeTick, MachineConditionState.Suspension.NONE, preState.unprovenTailDiscarded());
    }

    private static String freshness(MachineConditionState state) {
        return ConditionDigest.identity("butchercraft:condition_freshness/v1", state.instanceId().value(),
                Long.toString(state.revision()), state.digest(), state.policy().identity(),
                Long.toString(state.policy().processingLoss()), Long.toString(state.policy().processingServiceDebt()));
    }
}
