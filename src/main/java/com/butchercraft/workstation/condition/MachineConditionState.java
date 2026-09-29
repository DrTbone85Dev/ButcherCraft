package com.butchercraft.workstation.condition;

import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record MachineConditionState(
        int schemaVersion, WorkstationInstanceId instanceId, MachineConditionPolicy policy,
        long revision, long mechanicalLoss, long serviceDebt,
        List<ConditionExposureAccount> exposureAccounts, Optional<ConditionActiveExposure> activeExposure,
        Optional<ConditionFault> fault, long lastAccountedTick, Suspension suspension,
        boolean unprovenTailDiscarded, ConditionInitializationEvidence initializationEvidence,
        long effectCount, Optional<String> lastEffectIdentity, String digest
) {
    public enum Suspension { NONE, STOPPED, UNLOADED, RESTART_REQUIRED, DISCONTINUITY, RECOVERY_BLOCKED, RETIRED }
    public enum Band { HEALTHY, SERVICE_SOON, SERVICE_DUE, CRITICAL, FAULTED }
    public enum Eligibility { ALLOWED, SERVICE_WARNING, DENIED_BY_FAULT, EXPOSURE_SETTLEMENT_REQUIRED, RETIRED }

    public MachineConditionState {
        if (schemaVersion != 1) throw new UnsupportedConditionSchemaException("machine condition", schemaVersion);
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(policy, "policy");
        if (revision <= 0 || mechanicalLoss < 0 || mechanicalLoss > policy.maximumLoss()
                || serviceDebt < 0 || lastAccountedTick < 0 || effectCount < 0) {
            throw new IllegalArgumentException("Invalid authoritative condition bounds");
        }
        exposureAccounts = Objects.requireNonNull(exposureAccounts, "exposureAccounts").stream().sorted().toList();
        if (exposureAccounts.size() > ConditionExposureType.values().length
                || exposureAccounts.stream().map(ConditionExposureAccount::type).distinct().count()
                != exposureAccounts.size()) throw new IllegalArgumentException("Duplicate condition exposure account");
        activeExposure = Objects.requireNonNull(activeExposure, "activeExposure");
        fault = Objects.requireNonNull(fault, "fault");
        Objects.requireNonNull(suspension, "suspension");
        Objects.requireNonNull(initializationEvidence, "initializationEvidence");
        if (!initializationEvidence.instanceId().equals(instanceId)) {
            throw new IllegalArgumentException("Condition initialization targets another instance");
        }
        lastEffectIdentity = Objects.requireNonNull(lastEffectIdentity, "lastEffectIdentity");
        if ((effectCount == 0) != lastEffectIdentity.isEmpty()) {
            throw new IllegalArgumentException("Condition effect count/reference mismatch");
        }
        if (activeExposure.isPresent()) {
            ConditionActiveExposure active = activeExposure.orElseThrow();
            if (!active.policyIdentity().equals(policy.identity()) || policy.exposure(active.type()).isEmpty()
                    || active.openingConditionRevision() > revision || active.accountedThroughTick() != lastAccountedTick
                    || suspension != Suspension.NONE || fault.isPresent()) {
                throw new IllegalArgumentException("Active condition exposure is incoherent");
            }
        }
        if (fault.isPresent()) {
            ConditionFault value = fault.orElseThrow();
            if (!value.instanceId().equals(instanceId) || value.conditionRevision() > revision
                    || value.authoritativeTick() > lastAccountedTick) {
                throw new IllegalArgumentException("Condition fault identity/revision mismatch");
            }
        }
        String expected = calculateDigest(schemaVersion, instanceId, policy, revision, mechanicalLoss, serviceDebt,
                exposureAccounts, activeExposure, fault, lastAccountedTick, suspension, unprovenTailDiscarded,
                initializationEvidence, effectCount, lastEffectIdentity);
        if (!expected.equals(digest)) throw new IllegalArgumentException("Machine condition digest mismatch");
    }

    public static MachineConditionState healthy(WorkstationInstanceId instance, MachineConditionPolicy policy,
            ConditionInitializationEvidence initializationEvidence, long tick) {
        return create(instance, policy, 1, 0, 0, List.of(), Optional.empty(), Optional.empty(), tick,
                Suspension.NONE, false, initializationEvidence, 0, Optional.empty());
    }

    public static MachineConditionState create(
            WorkstationInstanceId instance, MachineConditionPolicy policy, long revision, long loss, long debt,
            List<ConditionExposureAccount> accounts, Optional<ConditionActiveExposure> active,
            Optional<ConditionFault> fault, long tick, Suspension suspension, boolean tailDiscarded,
            ConditionInitializationEvidence initialization, long count, Optional<String> effect
    ) {
        List<ConditionExposureAccount> ordered = accounts.stream().sorted().toList();
        return new MachineConditionState(1, instance, policy, revision, loss, debt, ordered, active, fault,
                tick, suspension, tailDiscarded, initialization, count, effect,
                calculateDigest(1, instance, policy, revision, loss, debt, ordered, active, fault, tick,
                        suspension, tailDiscarded, initialization, count, effect));
    }

    public ConditionExposureAccount account(ConditionExposureType type) {
        return exposureAccounts.stream().filter(value -> value.type() == type).findFirst()
                .orElseGet(() -> ConditionExposureAccount.empty(type));
    }

    public Band band() {
        if (fault.isPresent()) return Band.FAULTED;
        if (mechanicalLoss >= policy.criticalLoss()) return Band.CRITICAL;
        if (serviceDebt >= policy.serviceDue()) return Band.SERVICE_DUE;
        if (serviceDebt >= policy.serviceSoon()) return Band.SERVICE_SOON;
        return Band.HEALTHY;
    }

    public Eligibility eligibility() {
        if (suspension == Suspension.RETIRED) return Eligibility.RETIRED;
        if (fault.isPresent()) return Eligibility.DENIED_BY_FAULT;
        if (activeExposure.isPresent()) return Eligibility.EXPOSURE_SETTLEMENT_REQUIRED;
        return band() == Band.SERVICE_DUE || band() == Band.SERVICE_SOON
                ? Eligibility.SERVICE_WARNING : Eligibility.ALLOWED;
    }

    public boolean permitsStop() { return true; }

    public boolean permitsService(boolean childInFlight, boolean terminalRun, boolean restartWithoutChild) {
        return !childInFlight && activeExposure.isEmpty() && (terminalRun || restartWithoutChild);
    }

    MachineConditionState successor(String effect, long loss, long debt, List<ConditionExposureAccount> accounts,
            Optional<ConditionActiveExposure> active, Optional<ConditionFault> nextFault, long tick,
            Suspension nextSuspension, boolean tailDiscarded) {
        if (tick < lastAccountedTick) throw new IllegalArgumentException("Condition time regressed");
        return create(instanceId, policy, Math.incrementExact(revision), loss, debt, accounts, active, nextFault,
                tick, nextSuspension, tailDiscarded, initializationEvidence, Math.incrementExact(effectCount),
                Optional.of(ConditionDigest.text(effect, "effect")));
    }

    private static String calculateDigest(int schema, WorkstationInstanceId instance, MachineConditionPolicy policy,
            long revision, long loss, long debt, List<ConditionExposureAccount> accounts,
            Optional<ConditionActiveExposure> active, Optional<ConditionFault> fault, long tick,
            Suspension suspension, boolean tailDiscarded, ConditionInitializationEvidence initialization,
            long count, Optional<String> effect) {
        ConditionDigest digest = new ConditionDigest("butchercraft:machine_condition/v1")
                .add(schema).add(instance.value()).add(policy.identity()).add(revision).add(loss).add(debt)
                .add(accounts.size());
        accounts.forEach(account -> account.digestInto(digest));
        digest.add(active.isPresent());
        active.ifPresent(value -> value.digestInto(digest));
        digest.add(fault.isPresent());
        fault.ifPresent(value -> digest.add(value.identity()));
        digest.add(tick).add(suspension.name()).add(tailDiscarded).add(initialization.identity()).add(count)
                .add(effect.isPresent());
        effect.ifPresent(digest::add);
        return digest.finish();
    }
}
