package com.butchercraft.workstation.operation;

import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.MachineRunIdentity;
import com.butchercraft.world.execution.MachineStartAuthorizationEvidence;
import com.butchercraft.world.execution.MachineStopAuthorizationEvidence;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record MachineOperatingRecord(
        int schemaVersion,
        MachineWorkstationReference workstation,
        MachineOperatingPolicy policy,
        MachineOperatingState state,
        long revision,
        long transitionSequence,
        Optional<MachineRunIdentity> currentRunIdentity,
        Optional<MachineOperatingState> priorRestartState,
        Optional<ExecutionOperationId> activeChildOperationId,
        Optional<String> startAuthorizationIdentity,
        Optional<String> stopAuthorizationIdentity,
        long stateEntrySimulationTick,
        long lastTransitionSimulationTick,
        long lastObservedSimulationTick,
        Map<MachineOperatingState, Long> completedStateDurations,
        MachineEndpointAvailability endpointAvailability,
        Optional<String> eligibilityIdentity,
        Optional<String> blockageReason,
        Optional<MachineOperatingResultCode> recoveryCode,
        Optional<String> recoveryDetail,
        String contentDigest
) implements Comparable<MachineOperatingRecord> {
    public MachineOperatingRecord {
        if (schemaVersion != MachineOperatingSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported machine operating record schema: " + schemaVersion);
        }
        workstation = Objects.requireNonNull(workstation, "workstation");
        policy = Objects.requireNonNull(policy, "policy");
        if (!policy.kind().supportsPersistentRun()) {
            throw new IllegalArgumentException("Manual-discrete policy cannot own persistent machine operating state");
        }
        state = Objects.requireNonNull(state, "state");
        if (revision < 0L || transitionSequence < 0L) {
            throw new IllegalArgumentException("Machine operating revisions must not be negative");
        }
        currentRunIdentity = Objects.requireNonNull(currentRunIdentity, "currentRunIdentity");
        priorRestartState = Objects.requireNonNull(priorRestartState, "priorRestartState");
        activeChildOperationId = Objects.requireNonNull(activeChildOperationId, "activeChildOperationId");
        startAuthorizationIdentity = Objects.requireNonNull(startAuthorizationIdentity, "startAuthorizationIdentity")
                .map(value -> MachineOperatingValidation.id(value, "Machine START authorization identity"));
        stopAuthorizationIdentity = Objects.requireNonNull(stopAuthorizationIdentity, "stopAuthorizationIdentity")
                .map(value -> MachineOperatingValidation.id(value, "Machine STOP authorization identity"));
        if (state == MachineOperatingState.OFF && (currentRunIdentity.isPresent() || activeChildOperationId.isPresent())) {
            throw new IllegalArgumentException("OFF machine cannot reference an active Run or child");
        }
        if (state == MachineOperatingState.STARTING && currentRunIdentity.isPresent()) {
            throw new IllegalArgumentException("STARTING machine cannot publish a Run before Execution accepts it");
        }
        if ((state.powered() || state == MachineOperatingState.RESTART_REQUIRED)
                && currentRunIdentity.isEmpty()) {
            throw new IllegalArgumentException("Powered or restart-required machine must reference its exact Run");
        }
        if (state == MachineOperatingState.STOPPING && stopAuthorizationIdentity.isEmpty()) {
            throw new IllegalArgumentException("STOPPING machine requires exact STOP evidence");
        }
        if (state == MachineOperatingState.RESTART_REQUIRED && priorRestartState.isEmpty()) {
            throw new IllegalArgumentException("RESTART_REQUIRED machine must retain its prior operating state");
        }
        if (state != MachineOperatingState.RESTART_REQUIRED && priorRestartState.isPresent()) {
            throw new IllegalArgumentException("Only RESTART_REQUIRED may retain prior restart state");
        }
        if (stateEntrySimulationTick < 0L || lastTransitionSimulationTick < 0L
                || lastObservedSimulationTick < 0L) {
            throw new IllegalArgumentException("Machine operating ticks must not be negative");
        }
        if (lastTransitionSimulationTick < stateEntrySimulationTick
                || lastObservedSimulationTick < lastTransitionSimulationTick) {
            throw new IllegalArgumentException("Machine operating ticks are not monotonic");
        }
        EnumMap<MachineOperatingState, Long> durations = new EnumMap<>(MachineOperatingState.class);
        Objects.requireNonNull(completedStateDurations, "completedStateDurations").forEach((key, value) -> {
            Objects.requireNonNull(key, "duration state");
            if (value == null || value < 0L) throw new IllegalArgumentException("State duration must not be negative");
            durations.put(key, value);
        });
        completedStateDurations = Map.copyOf(durations);
        endpointAvailability = Objects.requireNonNull(endpointAvailability, "endpointAvailability");
        eligibilityIdentity = Objects.requireNonNull(eligibilityIdentity, "eligibilityIdentity")
                .map(value -> MachineOperatingValidation.id(value, "Machine eligibility identity"));
        blockageReason = Objects.requireNonNull(blockageReason, "blockageReason")
                .map(value -> MachineOperatingValidation.text(value, "Machine blockage reason"));
        recoveryCode = Objects.requireNonNull(recoveryCode, "recoveryCode");
        recoveryDetail = Objects.requireNonNull(recoveryDetail, "recoveryDetail")
                .map(value -> MachineOperatingValidation.text(value, "Machine recovery detail"));
        if (state == MachineOperatingState.RECOVERY_REQUIRED && recoveryCode.isEmpty()) {
            throw new IllegalArgumentException("RECOVERY_REQUIRED machine requires a typed reason");
        }
        contentDigest = MachineOperatingValidation.digest(contentDigest, "Machine operating content digest");
        if (!contentDigest.equals(contentDigest(
                workstation,
                policy,
                state,
                revision,
                transitionSequence,
                currentRunIdentity,
                priorRestartState,
                activeChildOperationId,
                startAuthorizationIdentity,
                stopAuthorizationIdentity,
                stateEntrySimulationTick,
                lastTransitionSimulationTick,
                lastObservedSimulationTick,
                completedStateDurations,
                endpointAvailability,
                eligibilityIdentity,
                blockageReason,
                recoveryCode,
                recoveryDetail
        ))) {
            throw new IllegalArgumentException("Machine operating record content digest mismatch");
        }
    }

    public static MachineOperatingRecord starting(
            MachineWorkstationReference workstation,
            MachineOperatingPolicy policy,
            MachineStartAuthorizationEvidence startEvidence,
            long tick
    ) {
        if (!startEvidence.workstationInstanceIdentity().equals(workstation.instanceId().value())
                || !startEvidence.operatingPolicyIdentity().equals(policy.policyIdentity())) {
            throw new IllegalArgumentException("Machine START evidence does not match Workstation policy");
        }
        return build(
                workstation,
                policy,
                MachineOperatingState.STARTING,
                1L,
                1L,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(startEvidence.authorizationIdentity()),
                Optional.empty(),
                tick,
                tick,
                tick,
                Map.of(),
                MachineEndpointAvailability.AVAILABLE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public MachineOperatingRecord prepareNextStart(
            MachineStartAuthorizationEvidence startEvidence,
            long tick
    ) {
        if (state != MachineOperatingState.OFF || currentRunIdentity.isPresent()) {
            throw new IllegalStateException("Only an OFF machine may prepare a later START");
        }
        if (!startEvidence.workstationInstanceIdentity().equals(workstation.instanceId().value())
                || !startEvidence.operatingPolicyIdentity().equals(policy.policyIdentity())
                || startEvidence.expectedOperatingRevision() != revision) {
            throw new IllegalArgumentException("Later Machine START evidence is stale or targets another machine");
        }
        return transition(
                MachineOperatingState.STARTING,
                tick,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(startEvidence.authorizationIdentity()),
                Optional.empty(),
                endpointAvailability,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public MachineOperatingRecord activate(
            MachineRunIdentity runIdentity,
            String startIdentity,
            long tick
    ) {
        if (state == MachineOperatingState.RUNNING
                && currentRunIdentity.filter(runIdentity::equals).isPresent()) return this;
        if (state != MachineOperatingState.STARTING
                || startAuthorizationIdentity.filter(startIdentity::equals).isEmpty()) {
            throw new IllegalStateException("Machine START activation does not match prepared Workstation evidence");
        }
        return transition(
                MachineOperatingState.RUNNING,
                tick,
                Optional.of(runIdentity),
                Optional.empty(),
                activeChildOperationId,
                startAuthorizationIdentity,
                stopAuthorizationIdentity,
                endpointAvailability,
                eligibilityIdentity,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public MachineOperatingRecord abandonUncommittedStart(String startIdentity, long tick) {
        if (state == MachineOperatingState.OFF
                && startAuthorizationIdentity.filter(startIdentity::equals).isPresent()) return this;
        if (state != MachineOperatingState.STARTING
                || startAuthorizationIdentity.filter(startIdentity::equals).isEmpty()
                || currentRunIdentity.isPresent()) {
            throw new IllegalStateException("Only the exact uncommitted Machine START may return to OFF");
        }
        return transition(
                MachineOperatingState.OFF,
                tick,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                startAuthorizationIdentity,
                Optional.empty(),
                endpointAvailability,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public MachineOperatingRecord publishOperationalState(
            MachineOperatingState next,
            MachineRunIdentity runIdentity,
            Optional<String> eligibility,
            Optional<String> blockage,
            long tick
    ) {
        if (next != MachineOperatingState.RUNNING
                && next != MachineOperatingState.RUNNING_EMPTY
                && next != MachineOperatingState.OUTPUT_BLOCKED) {
            throw new IllegalArgumentException("Requested state is not an active machine operating state");
        }
        requireCurrentRun(runIdentity);
        if (activeChildOperationId.isPresent() && next != MachineOperatingState.RUNNING) {
            throw new IllegalStateException("Machine with active child cannot enter empty or blocked state");
        }
        return transition(
                next,
                tick,
                currentRunIdentity,
                Optional.empty(),
                activeChildOperationId,
                startAuthorizationIdentity,
                stopAuthorizationIdentity,
                endpointAvailability,
                eligibility,
                blockage,
                Optional.empty(),
                Optional.empty()
        );
    }

    public MachineOperatingRecord requestStop(MachineStopAuthorizationEvidence evidence, long tick) {
        requireCurrentRun(evidence.targetRunIdentity());
        if (state == MachineOperatingState.STOPPING) {
            if (stopAuthorizationIdentity.filter(evidence.authorizationIdentity()::equals).isPresent()) return this;
            throw new IllegalStateException("Machine already has conflicting STOP evidence");
        }
        return transition(
                MachineOperatingState.STOPPING,
                tick,
                currentRunIdentity,
                Optional.empty(),
                activeChildOperationId,
                startAuthorizationIdentity,
                Optional.of(evidence.authorizationIdentity()),
                endpointAvailability,
                eligibilityIdentity,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public MachineOperatingRecord completeStop(MachineRunIdentity runIdentity, long tick) {
        requireCurrentRun(runIdentity);
        if (activeChildOperationId.isPresent()) {
            throw new IllegalStateException("Machine cannot publish OFF before its child reaches a safe boundary");
        }
        return transition(
                MachineOperatingState.OFF,
                tick,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                startAuthorizationIdentity,
                stopAuthorizationIdentity,
                endpointAvailability,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public MachineOperatingRecord suspendForRestart(long tick) {
        if (state == MachineOperatingState.RESTART_REQUIRED) return this;
        if (!state.powered()) return this;
        MachineOperatingState prior = state;
        return transition(
                MachineOperatingState.RESTART_REQUIRED,
                tick,
                currentRunIdentity,
                Optional.of(prior),
                activeChildOperationId,
                startAuthorizationIdentity,
                stopAuthorizationIdentity,
                endpointAvailability,
                eligibilityIdentity,
                blockageReason,
                Optional.empty(),
                Optional.empty()
        );
    }

    public MachineOperatingRecord resume(MachineRunIdentity runIdentity, long tick) {
        requireCurrentRun(runIdentity);
        if (state != MachineOperatingState.RESTART_REQUIRED
                || activeChildOperationId.isPresent()
                || endpointAvailability != MachineEndpointAvailability.AVAILABLE) {
            throw new IllegalStateException("Machine cannot resume without resolved restart evidence");
        }
        MachineOperatingState target = priorRestartState.orElseThrow();
        if (target == MachineOperatingState.STOPPING) target = MachineOperatingState.STOPPING;
        return transition(
                target,
                tick,
                currentRunIdentity,
                Optional.empty(),
                Optional.empty(),
                startAuthorizationIdentity,
                stopAuthorizationIdentity,
                endpointAvailability,
                eligibilityIdentity,
                blockageReason,
                Optional.empty(),
                Optional.empty()
        );
    }

    public MachineOperatingRecord bindChild(
            MachineRunIdentity runIdentity,
            ExecutionOperationId operationId,
            long tick
    ) {
        requireCurrentRun(runIdentity);
        if (!state.canAdmitChild() || endpointAvailability != MachineEndpointAvailability.AVAILABLE) {
            throw new IllegalStateException("Machine state or endpoint availability blocks child admission");
        }
        if (activeChildOperationId.isPresent()) {
            if (activeChildOperationId.filter(operationId::equals).isPresent()) return this;
            throw new IllegalStateException("Machine already references another active child");
        }
        return updateWithoutStateTransition(
                tick,
                Optional.of(operationId),
                endpointAvailability,
                eligibilityIdentity,
                blockageReason,
                recoveryCode,
                recoveryDetail
        );
    }

    public MachineOperatingRecord clearChild(ExecutionOperationId operationId, long tick) {
        if (activeChildOperationId.isEmpty()) return this;
        if (activeChildOperationId.filter(operationId::equals).isEmpty()) {
            throw new IllegalStateException("Machine child result targets another operation");
        }
        return updateWithoutStateTransition(
                tick,
                Optional.empty(),
                endpointAvailability,
                eligibilityIdentity,
                blockageReason,
                recoveryCode,
                recoveryDetail
        );
    }

    public MachineOperatingRecord availability(MachineEndpointAvailability availability, long tick) {
        Objects.requireNonNull(availability, "availability");
        if (endpointAvailability == availability) return this;
        if (availability == MachineEndpointAvailability.REPLACED && currentRunIdentity.isPresent()) {
            return recoveryRequired(
                    MachineOperatingResultCode.REPLACEMENT_INSTANCE,
                    "Active Machine Run references a replaced Workstation instance",
                    availability,
                    tick
            );
        }
        return updateWithoutStateTransition(
                tick,
                activeChildOperationId,
                availability,
                eligibilityIdentity,
                blockageReason,
                recoveryCode,
                recoveryDetail
        );
    }

    public MachineOperatingRecord recoveryRequired(
            MachineOperatingResultCode code,
            String detail,
            MachineEndpointAvailability availability,
            long tick
    ) {
        if (state == MachineOperatingState.RECOVERY_REQUIRED
                && recoveryCode.filter(code::equals).isPresent()
                && recoveryDetail.filter(detail::equals).isPresent()) return this;
        return transition(
                MachineOperatingState.RECOVERY_REQUIRED,
                tick,
                currentRunIdentity,
                Optional.empty(),
                activeChildOperationId,
                startAuthorizationIdentity,
                stopAuthorizationIdentity,
                availability,
                eligibilityIdentity,
                blockageReason,
                Optional.of(code),
                Optional.of(detail)
        );
    }

    public long durationAt(MachineOperatingState queriedState, long tick) {
        if (tick < lastObservedSimulationTick) {
            throw new IllegalArgumentException("Machine duration query tick cannot move backward");
        }
        long completed = completedStateDurations.getOrDefault(queriedState, 0L);
        return state == queriedState
                ? Math.addExact(completed, tick - stateEntrySimulationTick)
                : completed;
    }

    private MachineOperatingRecord transition(
            MachineOperatingState next,
            long tick,
            Optional<MachineRunIdentity> nextRun,
            Optional<MachineOperatingState> nextPriorState,
            Optional<ExecutionOperationId> nextChild,
            Optional<String> nextStart,
            Optional<String> nextStop,
            MachineEndpointAvailability nextAvailability,
            Optional<String> nextEligibility,
            Optional<String> nextBlockage,
            Optional<MachineOperatingResultCode> nextRecoveryCode,
            Optional<String> nextRecoveryDetail
    ) {
        if (tick < lastObservedSimulationTick) {
            throw new IllegalArgumentException("Machine operating transition tick cannot move backward");
        }
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateException("Invalid machine operating transition: " + state + " -> " + next);
        }
        if (next == state) {
            return updateWithoutStateTransition(
                    tick,
                    nextChild,
                    nextAvailability,
                    nextEligibility,
                    nextBlockage,
                    nextRecoveryCode,
                    nextRecoveryDetail
            );
        }
        EnumMap<MachineOperatingState, Long> durations = new EnumMap<>(MachineOperatingState.class);
        durations.putAll(completedStateDurations);
        durations.merge(state, tick - stateEntrySimulationTick, Math::addExact);
        return build(
                workstation,
                policy,
                next,
                Math.addExact(revision, 1L),
                Math.addExact(transitionSequence, 1L),
                nextRun,
                nextPriorState,
                nextChild,
                nextStart,
                nextStop,
                tick,
                tick,
                tick,
                durations,
                nextAvailability,
                nextEligibility,
                nextBlockage,
                nextRecoveryCode,
                nextRecoveryDetail
        );
    }

    private MachineOperatingRecord updateWithoutStateTransition(
            long tick,
            Optional<ExecutionOperationId> nextChild,
            MachineEndpointAvailability nextAvailability,
            Optional<String> nextEligibility,
            Optional<String> nextBlockage,
            Optional<MachineOperatingResultCode> nextRecoveryCode,
            Optional<String> nextRecoveryDetail
    ) {
        if (tick < lastObservedSimulationTick) {
            throw new IllegalArgumentException("Machine operating observation tick cannot move backward");
        }
        return build(
                workstation,
                policy,
                state,
                Math.addExact(revision, 1L),
                transitionSequence,
                currentRunIdentity,
                priorRestartState,
                nextChild,
                startAuthorizationIdentity,
                stopAuthorizationIdentity,
                stateEntrySimulationTick,
                lastTransitionSimulationTick,
                tick,
                completedStateDurations,
                nextAvailability,
                nextEligibility,
                nextBlockage,
                nextRecoveryCode,
                nextRecoveryDetail
        );
    }

    private void requireCurrentRun(MachineRunIdentity runIdentity) {
        if (currentRunIdentity.filter(runIdentity::equals).isEmpty()) {
            throw new IllegalArgumentException("Machine operating state references another Machine Run");
        }
    }

    public String calculateContentDigest() {
        return contentDigest(
                workstation,
                policy,
                state,
                revision,
                transitionSequence,
                currentRunIdentity,
                priorRestartState,
                activeChildOperationId,
                startAuthorizationIdentity,
                stopAuthorizationIdentity,
                stateEntrySimulationTick,
                lastTransitionSimulationTick,
                lastObservedSimulationTick,
                completedStateDurations,
                endpointAvailability,
                eligibilityIdentity,
                blockageReason,
                recoveryCode,
                recoveryDetail
        );
    }

    private static MachineOperatingRecord build(
            MachineWorkstationReference workstation,
            MachineOperatingPolicy policy,
            MachineOperatingState state,
            long revision,
            long transitionSequence,
            Optional<MachineRunIdentity> currentRunIdentity,
            Optional<MachineOperatingState> priorRestartState,
            Optional<ExecutionOperationId> activeChildOperationId,
            Optional<String> startAuthorizationIdentity,
            Optional<String> stopAuthorizationIdentity,
            long stateEntrySimulationTick,
            long lastTransitionSimulationTick,
            long lastObservedSimulationTick,
            Map<MachineOperatingState, Long> completedStateDurations,
            MachineEndpointAvailability endpointAvailability,
            Optional<String> eligibilityIdentity,
            Optional<String> blockageReason,
            Optional<MachineOperatingResultCode> recoveryCode,
            Optional<String> recoveryDetail
    ) {
        String digest = contentDigest(
                workstation,
                policy,
                state,
                revision,
                transitionSequence,
                currentRunIdentity,
                priorRestartState,
                activeChildOperationId,
                startAuthorizationIdentity,
                stopAuthorizationIdentity,
                stateEntrySimulationTick,
                lastTransitionSimulationTick,
                lastObservedSimulationTick,
                completedStateDurations,
                endpointAvailability,
                eligibilityIdentity,
                blockageReason,
                recoveryCode,
                recoveryDetail
        );
        return new MachineOperatingRecord(
                MachineOperatingSchema.CURRENT_VERSION,
                workstation,
                policy,
                state,
                revision,
                transitionSequence,
                currentRunIdentity,
                priorRestartState,
                activeChildOperationId,
                startAuthorizationIdentity,
                stopAuthorizationIdentity,
                stateEntrySimulationTick,
                lastTransitionSimulationTick,
                lastObservedSimulationTick,
                completedStateDurations,
                endpointAvailability,
                eligibilityIdentity,
                blockageReason,
                recoveryCode,
                recoveryDetail,
                digest
        );
    }

    private static String contentDigest(
            MachineWorkstationReference workstation,
            MachineOperatingPolicy policy,
            MachineOperatingState state,
            long revision,
            long transitionSequence,
            Optional<MachineRunIdentity> currentRunIdentity,
            Optional<MachineOperatingState> priorRestartState,
            Optional<ExecutionOperationId> activeChildOperationId,
            Optional<String> startAuthorizationIdentity,
            Optional<String> stopAuthorizationIdentity,
            long stateEntrySimulationTick,
            long lastTransitionSimulationTick,
            long lastObservedSimulationTick,
            Map<MachineOperatingState, Long> completedStateDurations,
            MachineEndpointAvailability endpointAvailability,
            Optional<String> eligibilityIdentity,
            Optional<String> blockageReason,
            Optional<MachineOperatingResultCode> recoveryCode,
            Optional<String> recoveryDetail
    ) {
        MachineOperatingDigest digest = MachineOperatingDigest.create("butchercraft:machine_operating_record")
                .add(MachineOperatingSchema.CURRENT_VERSION)
                .add(workstation.instanceId().value())
                .add(workstation.endpointKey().canonicalValue())
                .add(workstation.generation())
                .add(workstation.allocationConfigurationIdentity())
                .add(policy.policyIdentity())
                .add(policy.kind().serializedName())
                .add(policy.configurationIdentity())
                .add(state.serializedName())
                .add(revision)
                .add(transitionSequence)
                .add(currentRunIdentity.isPresent());
        currentRunIdentity.ifPresent(value -> digest.add(value.value()));
        digest.add(priorRestartState.isPresent());
        priorRestartState.ifPresent(value -> digest.add(value.serializedName()));
        digest.add(activeChildOperationId.isPresent());
        activeChildOperationId.ifPresent(value -> digest.add(value.value()));
        digest.add(startAuthorizationIdentity.isPresent());
        startAuthorizationIdentity.ifPresent(digest::add);
        digest.add(stopAuthorizationIdentity.isPresent());
        stopAuthorizationIdentity.ifPresent(digest::add);
        digest.add(stateEntrySimulationTick)
                .add(lastTransitionSimulationTick)
                .add(lastObservedSimulationTick);
        for (MachineOperatingState durationState : MachineOperatingState.values()) {
            digest.add(durationState.serializedName())
                    .add(completedStateDurations.getOrDefault(durationState, 0L));
        }
        digest.add(endpointAvailability.serializedName())
                .add(eligibilityIdentity.isPresent());
        eligibilityIdentity.ifPresent(digest::add);
        digest.add(blockageReason.isPresent());
        blockageReason.ifPresent(digest::add);
        digest.add(recoveryCode.isPresent());
        recoveryCode.ifPresent(value -> digest.add(value.serializedName()));
        digest.add(recoveryDetail.isPresent());
        recoveryDetail.ifPresent(digest::add);
        return digest.finish();
    }

    @Override
    public int compareTo(MachineOperatingRecord other) {
        return workstation.compareTo(other.workstation);
    }
}
