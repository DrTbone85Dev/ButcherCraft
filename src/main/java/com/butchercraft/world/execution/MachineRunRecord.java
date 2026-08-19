package com.butchercraft.world.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record MachineRunRecord(
        int schemaVersion,
        MachineRunIdentity runIdentity,
        String worldIdentity,
        String workstationInstanceIdentity,
        long generation,
        MachineStartAuthorizationEvidence startEvidence,
        String operatingPolicyIdentity,
        String configurationIdentity,
        MachineRunLifecycle lifecycle,
        long revision,
        long createdSimulationTick,
        long lastUpdatedSimulationTick,
        long nextChildSequence,
        Optional<MachineRunChildRecord> currentChild,
        List<MachineRunChildRecord> terminalChildren,
        Optional<MachineStopAuthorizationEvidence> stopEvidence,
        Optional<MachineRunResultCode> recoveryCode,
        Optional<String> recoveryDetail,
        String contentDigest
) implements Comparable<MachineRunRecord> {
    public MachineRunRecord {
        if (schemaVersion != MachineRunSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Machine Run record schema: " + schemaVersion);
        }
        runIdentity = Objects.requireNonNull(runIdentity, "runIdentity");
        worldIdentity = ExecutionValidation.requireId(worldIdentity, "Machine Run World Identity");
        workstationInstanceIdentity = ExecutionValidation.requireId(
                workstationInstanceIdentity,
                "Machine Run Workstation Instance Identity"
        );
        if (generation <= 0L) throw new IllegalArgumentException("Machine Run generation must be positive");
        startEvidence = Objects.requireNonNull(startEvidence, "startEvidence");
        operatingPolicyIdentity = ExecutionValidation.requireId(
                operatingPolicyIdentity,
                "Machine operating policy identity"
        );
        configurationIdentity = ExecutionValidation.requireId(
                configurationIdentity,
                "Machine Run configuration identity"
        );
        lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        if (revision < 0L) throw new IllegalArgumentException("Machine Run revision must not be negative");
        createdSimulationTick = ExecutionValidation.requireTick(createdSimulationTick, "Machine Run created tick");
        lastUpdatedSimulationTick = ExecutionValidation.requireTick(
                lastUpdatedSimulationTick,
                "Machine Run updated tick"
        );
        if (lastUpdatedSimulationTick < createdSimulationTick) {
            throw new IllegalArgumentException("Machine Run updated tick precedes creation");
        }
        if (nextChildSequence <= 0L) {
            throw new IllegalArgumentException("Next Machine Run child sequence must be positive");
        }
        currentChild = Objects.requireNonNull(currentChild, "currentChild");
        if (currentChild.isPresent()) {
            MachineRunChildRecord child = currentChild.orElseThrow();
            if (!child.runIdentity().equals(runIdentity) || child.state().terminal()) {
                throw new IllegalArgumentException("Current Machine Run child must be nonterminal and belong to the Run");
            }
        }
        terminalChildren = Objects.requireNonNull(terminalChildren, "terminalChildren").stream().sorted().toList();
        long priorSequence = 0L;
        for (MachineRunChildRecord child : terminalChildren) {
            if (!child.runIdentity().equals(runIdentity) || !child.state().terminal()) {
                throw new IllegalArgumentException("Terminal Machine Run child is invalid");
            }
            if (child.sequence() <= priorSequence) {
                throw new IllegalArgumentException("Machine Run child sequences must be unique and ordered");
            }
            priorSequence = child.sequence();
        }
        long highestSequence = currentChild.map(MachineRunChildRecord::sequence).orElse(priorSequence);
        if (nextChildSequence <= Math.max(priorSequence, highestSequence)) {
            throw new IllegalArgumentException("Next Machine Run child sequence has regressed");
        }
        stopEvidence = Objects.requireNonNull(stopEvidence, "stopEvidence");
        if (stopEvidence.isPresent()) {
            MachineStopAuthorizationEvidence stop = stopEvidence.orElseThrow();
            if (!stop.targetRunIdentity().equals(runIdentity)
                    || !stop.workstationInstanceIdentity().equals(workstationInstanceIdentity)) {
                throw new IllegalArgumentException("Machine STOP evidence targets another Run or Workstation instance");
            }
        }
        if ((lifecycle == MachineRunLifecycle.STOP_REQUESTED || lifecycle == MachineRunLifecycle.STOPPED)
                && stopEvidence.isEmpty()) {
            throw new IllegalArgumentException("Stopped Machine Run lifecycle requires STOP evidence");
        }
        recoveryCode = Objects.requireNonNull(recoveryCode, "recoveryCode");
        recoveryDetail = Objects.requireNonNull(recoveryDetail, "recoveryDetail")
                .map(value -> ExecutionValidation.requireText(value, "Machine Run recovery detail", 2_048));
        if (lifecycle == MachineRunLifecycle.RECOVERY_REQUIRED && recoveryCode.isEmpty()) {
            throw new IllegalArgumentException("Recovery-required Machine Run requires a typed reason");
        }
        MachineRunIdentity expected = MachineRunIdentity.create(
                worldIdentity,
                workstationInstanceIdentity,
                generation,
                startEvidence.authorizationIdentity(),
                operatingPolicyIdentity,
                configurationIdentity
        );
        if (!expected.equals(runIdentity)) {
            throw new IllegalArgumentException("Machine Run Identity does not match canonical inputs");
        }
        if (!startEvidence.worldIdentity().equals(worldIdentity)
                || !startEvidence.workstationInstanceIdentity().equals(workstationInstanceIdentity)
                || !startEvidence.operatingPolicyIdentity().equals(operatingPolicyIdentity)) {
            throw new IllegalArgumentException("Machine START evidence does not match the Run");
        }
        contentDigest = ExecutionValidation.requireDigest(contentDigest, "Machine Run record content digest");
        if (!contentDigest.equals(contentDigest(
                schemaVersion,
                runIdentity,
                worldIdentity,
                workstationInstanceIdentity,
                generation,
                startEvidence,
                operatingPolicyIdentity,
                configurationIdentity,
                lifecycle,
                revision,
                createdSimulationTick,
                lastUpdatedSimulationTick,
                nextChildSequence,
                currentChild,
                terminalChildren,
                stopEvidence,
                recoveryCode,
                recoveryDetail
        ))) {
            throw new IllegalArgumentException("Machine Run record content digest mismatch");
        }
    }

    public static MachineRunRecord create(
            long generation,
            MachineStartAuthorizationEvidence evidence,
            String configurationIdentity,
            long tick
    ) {
        MachineRunIdentity identity = MachineRunIdentity.create(
                evidence.worldIdentity(),
                evidence.workstationInstanceIdentity(),
                generation,
                evidence.authorizationIdentity(),
                evidence.operatingPolicyIdentity(),
                configurationIdentity
        );
        return build(
                identity,
                evidence.worldIdentity(),
                evidence.workstationInstanceIdentity(),
                generation,
                evidence,
                evidence.operatingPolicyIdentity(),
                configurationIdentity,
                MachineRunLifecycle.AUTHORIZED,
                0L,
                tick,
                tick,
                1L,
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public MachineRunRecord requestStop(MachineStopAuthorizationEvidence evidence, long tick) {
        Objects.requireNonNull(evidence, "evidence");
        if (stopEvidence.isPresent()) {
            if (stopEvidence.orElseThrow().equals(evidence)) return this;
            throw new IllegalStateException("Machine Run already has conflicting STOP evidence");
        }
        return transition(
                MachineRunLifecycle.STOP_REQUESTED,
                tick,
                currentChild,
                terminalChildren,
                Optional.of(evidence),
                Optional.empty(),
                Optional.empty()
        );
    }

    public MachineRunRecord suspendForRestart(long tick) {
        if (lifecycle == MachineRunLifecycle.SUSPENDED_RESTART_REQUIRED) return this;
        if (lifecycle != MachineRunLifecycle.AUTHORIZED) return this;
        return transition(
                MachineRunLifecycle.SUSPENDED_RESTART_REQUIRED,
                tick,
                currentChild,
                terminalChildren,
                stopEvidence,
                Optional.empty(),
                Optional.empty()
        );
    }

    public MachineRunRecord resume(long tick) {
        if (lifecycle == MachineRunLifecycle.AUTHORIZED) return this;
        if (lifecycle != MachineRunLifecycle.SUSPENDED_RESTART_REQUIRED) {
            throw new IllegalStateException("Only a restart-suspended Machine Run may resume");
        }
        if (currentChild.isPresent()) {
            throw new IllegalStateException("Machine Run with unresolved child cannot resume");
        }
        return transition(
                MachineRunLifecycle.AUTHORIZED,
                tick,
                Optional.empty(),
                terminalChildren,
                stopEvidence,
                Optional.empty(),
                Optional.empty()
        );
    }

    public MachineRunRecord stopped(long tick) {
        if (lifecycle == MachineRunLifecycle.STOPPED) return this;
        if (lifecycle != MachineRunLifecycle.STOP_REQUESTED || currentChild.isPresent()) {
            throw new IllegalStateException("Machine Run may stop only after STOP and a safe child boundary");
        }
        return transition(
                MachineRunLifecycle.STOPPED,
                tick,
                Optional.empty(),
                terminalChildren,
                stopEvidence,
                Optional.empty(),
                Optional.empty()
        );
    }

    public MachineRunRecord recoveryRequired(MachineRunResultCode code, String detail, long tick) {
        if (lifecycle == MachineRunLifecycle.RECOVERY_REQUIRED
                && recoveryCode.filter(code::equals).isPresent()
                && recoveryDetail.filter(detail::equals).isPresent()) return this;
        return transition(
                MachineRunLifecycle.RECOVERY_REQUIRED,
                tick,
                currentChild,
                terminalChildren,
                stopEvidence,
                Optional.of(code),
                Optional.of(detail)
        );
    }

    public MachineRunRecord prepareChild(ExecutionAuthorizationEvidence authorization, long tick) {
        requireMonotonicTick(tick);
        Objects.requireNonNull(authorization, "authorization");
        MachineRunChildRecord candidate = MachineRunChildRecord.prepared(
                runIdentity,
                nextChildSequence,
                workstationInstanceIdentity,
                authorization,
                tick
        );
        Optional<MachineRunChildRecord> existing = childForOperation(candidate.operationId());
        if (existing.isPresent()) {
            if (existing.orElseThrow().authorizationContentDigest()
                    .equals(candidate.authorizationContentDigest())) return this;
            throw new IllegalStateException("Machine Run child operation identity conflict");
        }
        if (!lifecycle.authorizesChildren()) {
            throw new IllegalStateException("Machine Run lifecycle does not authorize child admission");
        }
        if (currentChild.isPresent()) {
            throw new IllegalStateException("Machine Run already has a nonterminal child");
        }
        String sequenceIdentity = childSequenceIdentity(nextChildSequence);
        List<String> inputs = authorization.explicitInputIdentities();
        if (!authorization.worldIdentity().equals(worldIdentity)
                || !inputs.contains(runIdentity.value())
                || !inputs.contains(workstationInstanceIdentity)
                || !inputs.contains(sequenceIdentity)
                || !inputs.contains(operatingPolicyIdentity)) {
            throw new IllegalArgumentException("Execution child authorization is not bound to the exact Machine Run");
        }
        return build(
                runIdentity,
                worldIdentity,
                workstationInstanceIdentity,
                generation,
                startEvidence,
                operatingPolicyIdentity,
                configurationIdentity,
                lifecycle,
                Math.addExact(revision, 1L),
                createdSimulationTick,
                tick,
                Math.addExact(nextChildSequence, 1L),
                Optional.of(candidate),
                terminalChildren,
                stopEvidence,
                recoveryCode,
                recoveryDetail
        );
    }

    public MachineRunRecord admitPreparedChild(ExecutionOperationSnapshot operation, long tick) {
        requireMonotonicTick(tick);
        Objects.requireNonNull(operation, "operation");
        MachineRunChildRecord child = currentChild.orElseThrow(() ->
                new IllegalStateException("Machine Run has no prepared child"));
        if (!child.operationId().equals(operation.operationId())
                || !child.authorizationContentDigest().equals(
                operation.authorizationEvidence().authorizationContentDigest())) {
            throw new IllegalArgumentException("Execution operation does not match the prepared Machine Run child");
        }
        if (child.state() == MachineRunChildState.ADMITTED) return this;
        MachineRunChildRecord admitted = child.admitted(tick);
        return build(
                runIdentity,
                worldIdentity,
                workstationInstanceIdentity,
                generation,
                startEvidence,
                operatingPolicyIdentity,
                configurationIdentity,
                lifecycle,
                Math.addExact(revision, 1L),
                createdSimulationTick,
                tick,
                nextChildSequence,
                Optional.of(admitted),
                terminalChildren,
                stopEvidence,
                recoveryCode,
                recoveryDetail
        );
    }

    public MachineRunRecord cancelPreparedChild(
            ExecutionOperationId operationId,
            long tick,
            MachineRunResultCode failureCode
    ) {
        requireMonotonicTick(tick);
        MachineRunChildRecord child = currentChild.orElseThrow(() ->
                new IllegalStateException("Machine Run has no prepared child to cancel"));
        if (child.state() != MachineRunChildState.PREPARED || !child.operationId().equals(operationId)) {
            throw new IllegalStateException("Only the exact pre-admission Machine Run child may be cancelled");
        }
        MachineRunChildRecord cancelled = child.terminal(
                MachineRunChildState.CANCELLED,
                tick,
                Optional.empty(),
                Optional.of(Objects.requireNonNull(failureCode, "failureCode"))
        );
        List<MachineRunChildRecord> history = new ArrayList<>(terminalChildren);
        history.add(cancelled);
        return build(
                runIdentity,
                worldIdentity,
                workstationInstanceIdentity,
                generation,
                startEvidence,
                operatingPolicyIdentity,
                configurationIdentity,
                lifecycle,
                Math.addExact(revision, 1L),
                createdSimulationTick,
                tick,
                nextChildSequence,
                Optional.empty(),
                history,
                stopEvidence,
                recoveryCode,
                recoveryDetail
        );
    }

    public MachineRunRecord observeChild(
            ExecutionOperationSnapshot operation,
            long tick
    ) {
        requireMonotonicTick(tick);
        Objects.requireNonNull(operation, "operation");
        Optional<MachineRunChildRecord> existing = childForOperation(operation.operationId());
        if (existing.isPresent() && existing.orElseThrow().state().terminal()) return this;
        MachineRunChildRecord child = currentChild.orElseThrow(() ->
                new IllegalStateException("Machine Run has no active child to observe"));
        if (!child.operationId().equals(operation.operationId())) {
            throw new IllegalArgumentException("Execution result belongs to another Machine Run child");
        }
        if (!operation.status().terminal()) {
            throw new IllegalArgumentException("Execution child result is not terminal");
        }
        MachineRunChildState terminalState = switch (operation.status()) {
            case SUCCEEDED -> MachineRunChildState.COMPLETED;
            case CANCELLED_BEFORE_START -> MachineRunChildState.CANCELLED;
            case UNKNOWN_OUTCOME -> MachineRunChildState.UNKNOWN_OUTCOME;
            case REJECTED, FAILED -> MachineRunChildState.FAILED;
            default -> throw new IllegalArgumentException("Execution child result is not terminal");
        };
        Optional<String> evidence = operation.resultEvidence().map(ExecutionResultEvidence::evidenceIdentity);
        Optional<MachineRunResultCode> failure = terminalState == MachineRunChildState.COMPLETED
                ? Optional.empty()
                : Optional.of(terminalState == MachineRunChildState.UNKNOWN_OUTCOME
                        ? MachineRunResultCode.RECOVERY_REQUIRED
                        : MachineRunResultCode.CHILD_RESULT_CONFLICT);
        MachineRunChildRecord terminal = child.terminal(terminalState, tick, evidence, failure);
        List<MachineRunChildRecord> history = new ArrayList<>(terminalChildren);
        history.add(terminal);
        MachineRunLifecycle nextLifecycle = terminalState == MachineRunChildState.UNKNOWN_OUTCOME
                ? MachineRunLifecycle.RECOVERY_REQUIRED
                : lifecycle;
        Optional<MachineRunResultCode> nextRecovery = nextLifecycle == MachineRunLifecycle.RECOVERY_REQUIRED
                ? Optional.of(MachineRunResultCode.RECOVERY_REQUIRED)
                : recoveryCode;
        Optional<String> nextDetail = nextLifecycle == MachineRunLifecycle.RECOVERY_REQUIRED
                ? Optional.of("Machine Run child has Unknown Outcome")
                : recoveryDetail;
        return build(
                runIdentity,
                worldIdentity,
                workstationInstanceIdentity,
                generation,
                startEvidence,
                operatingPolicyIdentity,
                configurationIdentity,
                nextLifecycle,
                Math.addExact(revision, 1L),
                createdSimulationTick,
                tick,
                nextChildSequence,
                Optional.empty(),
                history,
                stopEvidence,
                nextRecovery,
                nextDetail
        );
    }

    public Optional<MachineRunChildRecord> childForOperation(ExecutionOperationId operationId) {
        if (currentChild.filter(child -> child.operationId().equals(operationId)).isPresent()) return currentChild;
        return terminalChildren.stream().filter(child -> child.operationId().equals(operationId)).findFirst();
    }

    public static String childSequenceIdentity(long sequence) {
        if (sequence <= 0L) throw new IllegalArgumentException("Machine Run child sequence must be positive");
        return "butchercraft:machine_run_child_sequence/" + sequence;
    }

    private MachineRunRecord transition(
            MachineRunLifecycle next,
            long tick,
            Optional<MachineRunChildRecord> nextChild,
            List<MachineRunChildRecord> nextHistory,
            Optional<MachineStopAuthorizationEvidence> nextStop,
            Optional<MachineRunResultCode> nextRecovery,
            Optional<String> nextDetail
    ) {
        requireMonotonicTick(tick);
        if (!lifecycle.canTransitionTo(next)) {
            throw new IllegalStateException("Invalid Machine Run transition: " + lifecycle + " -> " + next);
        }
        return build(
                runIdentity,
                worldIdentity,
                workstationInstanceIdentity,
                generation,
                startEvidence,
                operatingPolicyIdentity,
                configurationIdentity,
                next,
                Math.addExact(revision, 1L),
                createdSimulationTick,
                tick,
                nextChildSequence,
                nextChild,
                nextHistory,
                nextStop,
                nextRecovery,
                nextDetail
        );
    }

    private void requireMonotonicTick(long tick) {
        ExecutionValidation.requireTick(tick, "Machine Run transition tick");
        if (tick < lastUpdatedSimulationTick) {
            throw new IllegalArgumentException("Machine Run transition tick cannot move backward");
        }
    }

    public String calculateContentDigest() {
        return contentDigest(
                schemaVersion,
                runIdentity,
                worldIdentity,
                workstationInstanceIdentity,
                generation,
                startEvidence,
                operatingPolicyIdentity,
                configurationIdentity,
                lifecycle,
                revision,
                createdSimulationTick,
                lastUpdatedSimulationTick,
                nextChildSequence,
                currentChild,
                terminalChildren,
                stopEvidence,
                recoveryCode,
                recoveryDetail
        );
    }

    private static String contentDigest(
            int schemaVersion,
            MachineRunIdentity runIdentity,
            String worldIdentity,
            String workstationInstanceIdentity,
            long generation,
            MachineStartAuthorizationEvidence startEvidence,
            String policyIdentity,
            String configurationIdentity,
            MachineRunLifecycle lifecycle,
            long revision,
            long createdTick,
            long updatedTick,
            long nextChildSequence,
            Optional<MachineRunChildRecord> currentChild,
            List<MachineRunChildRecord> terminalChildren,
            Optional<MachineStopAuthorizationEvidence> stopEvidence,
            Optional<MachineRunResultCode> recoveryCode,
            Optional<String> recoveryDetail
    ) {
        ExecutionCanonicalDigest digest = ExecutionCanonicalDigest.create("butchercraft:machine_run_record")
                .add(schemaVersion)
                .add(runIdentity.value())
                .add(worldIdentity)
                .add(workstationInstanceIdentity)
                .add(generation)
                .add(startEvidence.authorizationIdentity())
                .add(startEvidence.contentDigest())
                .add(policyIdentity)
                .add(configurationIdentity)
                .add(lifecycle.serializedName())
                .add(revision)
                .add(createdTick)
                .add(updatedTick)
                .add(nextChildSequence)
                .add(currentChild.isPresent());
        currentChild.ifPresent(child -> digest.add(child.contentDigest()));
        digest.add(terminalChildren.size());
        terminalChildren.forEach(child -> digest.add(child.contentDigest()));
        digest.add(stopEvidence.isPresent());
        stopEvidence.ifPresent(stop -> digest.add(stop.contentDigest()));
        digest.add(recoveryCode.isPresent());
        recoveryCode.ifPresent(code -> digest.add(code.serializedName()));
        digest.add(recoveryDetail.isPresent());
        recoveryDetail.ifPresent(digest::add);
        return digest.finish();
    }

    private static MachineRunRecord build(
            MachineRunIdentity identity,
            String worldIdentity,
            String workstationInstanceIdentity,
            long generation,
            MachineStartAuthorizationEvidence startEvidence,
            String policyIdentity,
            String configurationIdentity,
            MachineRunLifecycle lifecycle,
            long revision,
            long createdTick,
            long updatedTick,
            long nextChildSequence,
            Optional<MachineRunChildRecord> currentChild,
            List<MachineRunChildRecord> terminalChildren,
            Optional<MachineStopAuthorizationEvidence> stopEvidence,
            Optional<MachineRunResultCode> recoveryCode,
            Optional<String> recoveryDetail
    ) {
        String digest = contentDigest(
                MachineRunSchema.CURRENT_VERSION,
                identity,
                worldIdentity,
                workstationInstanceIdentity,
                generation,
                startEvidence,
                policyIdentity,
                configurationIdentity,
                lifecycle,
                revision,
                createdTick,
                updatedTick,
                nextChildSequence,
                currentChild,
                terminalChildren,
                stopEvidence,
                recoveryCode,
                recoveryDetail
        );
        return new MachineRunRecord(
                MachineRunSchema.CURRENT_VERSION,
                identity,
                worldIdentity,
                workstationInstanceIdentity,
                generation,
                startEvidence,
                policyIdentity,
                configurationIdentity,
                lifecycle,
                revision,
                createdTick,
                updatedTick,
                nextChildSequence,
                currentChild,
                terminalChildren,
                stopEvidence,
                recoveryCode,
                recoveryDetail,
                digest
        );
    }

    @Override
    public int compareTo(MachineRunRecord other) {
        return runIdentity.compareTo(other.runIdentity);
    }
}
