package com.butchercraft.workstation.condition;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Candidate arithmetic only. Publication, inventory, Runs and time remain with their existing owners. */
public final class WorkstationConditionEngine {
    private WorkstationConditionEngine() { }

    public static MachineConditionState successfulProcessing(MachineConditionState before, String effect, long tick) {
        Objects.requireNonNull(before, "before");
        if (before.fault().isPresent() || before.activeExposure().isPresent()
                || before.suspension() == MachineConditionState.Suspension.RETIRED) {
            throw new IllegalStateException("Condition denies processing; settle exposure before child admission");
        }
        return ConditionProcessingPreparation.of(before).complete(effect, tick);
    }

    public static MachineConditionState openExposure(MachineConditionState before, String effect,
            ConditionExposureType type, String operatingTransitionIdentity, long operatingRevision,
            String contiguousLoadedProof, long tick, boolean childInFlight) {
        if (childInFlight || before.activeExposure().isPresent() || before.fault().isPresent()
                || before.suspension() == MachineConditionState.Suspension.RETIRED) {
            throw new IllegalStateException("Exposure requires a reconciled no-child mechanical transition");
        }
        before.policy().exposure(type).orElseThrow(() -> new IllegalArgumentException("Exposure policy is not enabled"));
        String identity = ConditionDigest.identity("butchercraft:condition_exposure/v1", before.instanceId().value(),
                effect, before.policy().identity(), Long.toString(Math.incrementExact(before.revision())),
                operatingTransitionIdentity, Long.toString(operatingRevision), contiguousLoadedProof, Long.toString(tick));
        ConditionActiveExposure active = new ConditionActiveExposure(identity, type, before.policy().identity(),
                operatingTransitionIdentity, operatingRevision, contiguousLoadedProof, tick, tick,
                Math.incrementExact(before.revision()));
        return before.successor(effect, before.mechanicalLoss(), before.serviceDebt(), before.exposureAccounts(),
                Optional.of(active), before.fault(), tick, MachineConditionState.Suspension.NONE,
                before.unprovenTailDiscarded());
    }

    public static MachineConditionState settle(MachineConditionState before, String effect,
            String contiguousLoadedProof, long throughTick) {
        ConditionActiveExposure active = before.activeExposure()
                .orElseThrow(() -> new IllegalStateException("No active condition exposure"));
        if (!active.availabilityProofIdentity().equals(contiguousLoadedProof)) {
            throw new IllegalStateException("Exposure availability discontinuity requires suspension, not settlement");
        }
        if (throughTick < active.accountedThroughTick()) throw new IllegalArgumentException("Exposure tick regression");
        ConditionExposurePolicy policy = before.policy().exposure(active.type()).orElseThrow();
        ConditionExposureAccount account = before.account(active.type());
        long duration = Math.subtractExact(throughTick, active.accountedThroughTick());
        long effectiveDuration = duration;
        if (before.policy().faultLoss().isPresent() && policy.lossNumerator() > 0
                && lossAfter(before, account, policy, duration) >= before.policy().faultLoss().orElseThrow()) {
            // Find the first semantic threshold tick, not the tick when a poll happens to observe it.
            long low = 0;
            long high = duration;
            while (low < high) {
                long middle = low + (high - low) / 2;
                if (lossAfter(before, account, policy, middle) >= before.policy().faultLoss().orElseThrow()) high = middle;
                else low = middle + 1;
            }
            effectiveDuration = low;
        }
        long graceRemaining = Math.max(0, policy.graceTicks() - Math.min(policy.graceTicks(), account.graceProgress()));
        long charged = Math.max(0, effectiveDuration - graceRemaining);
        ConditionRemainder.Accumulation accumulation = account.remainder()
                .accumulate(charged, policy.lossNumerator(), policy.lossDenominator());
        long loss = saturatedAdd(before.mechanicalLoss(), accumulation.whole(), before.policy().maximumLoss());
        long cutoff = Math.addExact(active.accountedThroughTick(), effectiveDuration);
        ConditionExposureAccount updated = new ConditionExposureAccount(active.type(),
                Math.addExact(account.eligibleTicks(), effectiveDuration),
                Math.addExact(account.graceProgress(), effectiveDuration), accumulation.remainder());
        List<ConditionExposureAccount> accounts = new ArrayList<>(before.exposureAccounts());
        accounts.removeIf(value -> value.type() == active.type());
        accounts.add(updated);
        Optional<ConditionFault> fault = fault(before, loss, effect, cutoff);
        return before.successor(effect, loss, before.serviceDebt(), accounts,
                fault.isPresent() ? Optional.empty() : Optional.of(active.accountedThrough(cutoff)), fault, cutoff,
                MachineConditionState.Suspension.NONE, before.unprovenTailDiscarded());
    }

    public static MachineConditionState closeExposure(MachineConditionState before, String effect,
            String contiguousLoadedProof, long throughTick, MachineConditionState.Suspension reason) {
        MachineConditionState settled = settle(before, effect, contiguousLoadedProof, throughTick);
        return MachineConditionState.create(settled.instanceId(), settled.policy(), settled.revision(),
                settled.mechanicalLoss(), settled.serviceDebt(), settled.exposureAccounts(), Optional.empty(),
                settled.fault(), settled.lastAccountedTick(), reason, settled.unprovenTailDiscarded(),
                settled.initializationEvidence(), settled.effectCount(), settled.lastEffectIdentity());
    }

    /** No later Clock value is accepted here: a restart can close only the durable cutoff. */
    public static MachineConditionState suspendAtDurableCutoff(MachineConditionState before, String effect,
            MachineConditionState.Suspension reason) {
        if (reason != MachineConditionState.Suspension.RESTART_REQUIRED
                && reason != MachineConditionState.Suspension.DISCONTINUITY
                && reason != MachineConditionState.Suspension.RECOVERY_BLOCKED) {
            throw new IllegalArgumentException("Unproven-tail suspension requires a recovery boundary");
        }
        return before.successor(effect, before.mechanicalLoss(), before.serviceDebt(), before.exposureAccounts(),
                Optional.empty(), before.fault(), before.lastAccountedTick(), reason,
                before.unprovenTailDiscarded() || before.activeExposure().isPresent());
    }

    public static MachineConditionState changePolicy(MachineConditionState before, String effect,
            MachineConditionPolicy next, long tick) {
        if (tick < before.lastAccountedTick()) throw new IllegalArgumentException("Condition policy tick regression");
        if (before.activeExposure().isPresent()) throw new IllegalStateException("Close frozen-policy exposure first");
        if (!next.machineType().equals(before.policy().machineType())
                || next.maximumLoss() != before.policy().maximumLoss()) {
            throw new IllegalArgumentException("Condition policy transition cannot reinterpret machine or loss units");
        }
        return MachineConditionState.create(before.instanceId(), next, Math.incrementExact(before.revision()),
                before.mechanicalLoss(), before.serviceDebt(), before.exposureAccounts(), Optional.empty(), before.fault(),
                tick, before.suspension(), before.unprovenTailDiscarded(),
                before.initializationEvidence(), Math.incrementExact(before.effectCount()), Optional.of(effect));
    }

    public static MachineConditionState bindOperatingTransition(MachineConditionState before, String effect, long tick) {
        if (before.activeExposure().isPresent()) throw new IllegalStateException("Close exposure before transition binding");
        return before.successor(effect, before.mechanicalLoss(), before.serviceDebt(), before.exposureAccounts(),
                Optional.empty(), before.fault(), tick, before.suspension(), before.unprovenTailDiscarded());
    }

    public static MachineConditionState retire(MachineConditionState before, String effect, long tick, boolean loadedProof) {
        if (before.activeExposure().isPresent()) {
            if (loadedProof) return closeExposure(before, effect,
                    before.activeExposure().orElseThrow().availabilityProofIdentity(), tick, MachineConditionState.Suspension.RETIRED);
            return before.successor(effect, before.mechanicalLoss(), before.serviceDebt(), before.exposureAccounts(),
                    Optional.empty(), before.fault(), before.lastAccountedTick(), MachineConditionState.Suspension.RETIRED, true);
        }
        return before.successor(effect, before.mechanicalLoss(), before.serviceDebt(), before.exposureAccounts(),
                Optional.empty(), before.fault(), tick, MachineConditionState.Suspension.RETIRED, before.unprovenTailDiscarded());
    }

    private static long lossAfter(MachineConditionState before, ConditionExposureAccount account,
            ConditionExposurePolicy policy, long ticks) {
        long grace = Math.max(0, policy.graceTicks() - Math.min(policy.graceTicks(), account.graceProgress()));
        BigInteger numerator = BigInteger.valueOf(Math.max(0, ticks - grace))
                .multiply(BigInteger.valueOf(policy.lossNumerator()))
                .multiply(BigInteger.valueOf(account.remainder().denominator()))
                .add(BigInteger.valueOf(account.remainder().numerator()).multiply(BigInteger.valueOf(policy.lossDenominator())));
        BigInteger denominator = BigInteger.valueOf(policy.lossDenominator())
                .multiply(BigInteger.valueOf(account.remainder().denominator()));
        return BigInteger.valueOf(before.mechanicalLoss()).add(numerator.divide(denominator))
                .min(BigInteger.valueOf(before.policy().maximumLoss())).longValueExact();
    }

    public static long ticksUntilFault(MachineConditionState state) {
        if (state.activeExposure().isEmpty() || state.policy().faultLoss().isEmpty()) return Long.MAX_VALUE;
        var type = state.activeExposure().orElseThrow().type();
        var policy = state.policy().exposure(type).orElseThrow();
        if (policy.lossNumerator() == 0) return Long.MAX_VALUE;
        var account = state.account(type);
        var denominator = BigInteger.valueOf(account.remainder().denominator());
        var needed = BigInteger.valueOf(state.policy().faultLoss().orElseThrow() - state.mechanicalLoss())
                .multiply(denominator).subtract(BigInteger.valueOf(account.remainder().numerator()))
                .multiply(BigInteger.valueOf(policy.lossDenominator()));
        var perTick = BigInteger.valueOf(policy.lossNumerator()).multiply(denominator);
        var ticks = needed.add(perTick).subtract(BigInteger.ONE).divide(perTick).max(BigInteger.ZERO);
        long grace = Math.max(0, policy.graceTicks() - Math.min(policy.graceTicks(), account.graceProgress()));
        return ticks.add(BigInteger.valueOf(grace)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
    }

    private static Optional<ConditionFault> fault(MachineConditionState before, long loss, String effect, long tick) {
        if (before.fault().isPresent()) return before.fault();
        return before.policy().faultLoss().filter(threshold -> loss >= threshold).map(ignored ->
                ConditionFault.create(before.instanceId(), ConditionFault.Type.WEAR_LIMIT_REACHED, effect,
                        Math.incrementExact(before.revision()), before.policy().identity(), tick));
    }

    private static long saturatedAdd(long value, long increment, long maximum) {
        return BigInteger.valueOf(value).add(BigInteger.valueOf(increment)).min(BigInteger.valueOf(maximum)).longValueExact();
    }
}
