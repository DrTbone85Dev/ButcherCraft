package com.butchercraft.world.production;

import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import com.butchercraft.world.transaction.TransactionId;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public final class ProductionRunRuntime {
    private ProductionRunSnapshot state;

    public ProductionRunRuntime(ProductionRunSnapshot state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public static ProductionRunRuntime planned(
            ProductionRunId id,
            ProductionPlanId planId,
            long requiredWorkUnits,
            long simulationTick
    ) {
        return new ProductionRunRuntime(new ProductionRunSnapshot(
                id, planId, ProductionRunStatus.PLANNED, requireTick(simulationTick),
                OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty(),
                requiredWorkUnits, 0L, 0, OptionalLong.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), 0L, ProductionSchema.CURRENT_VERSION
        ));
    }

    public synchronized ProductionRunSnapshot snapshot() {
        return state;
    }

    public synchronized void markReady(long tick) {
        requireTransition(ProductionRunStatus.READY, ProductionRunStatus.PLANNED, ProductionRunStatus.BLOCKED);
        replace(ProductionRunStatus.READY, tick, state.currentWorkUnits(), state.executionAttemptCount(),
                OptionalLong.empty(), state.scheduledWorkId(), state.completionTransactionId(),
                Optional.empty(), Optional.empty(), state.startedTick(), OptionalLong.empty(), state.completedTick());
    }

    public synchronized void bindScheduledWork(SimulationWorkId workId, long tick) {
        Objects.requireNonNull(workId, "workId");
        requireTransition(ProductionRunStatus.SCHEDULED, ProductionRunStatus.READY);
        if (state.scheduledWorkId().isPresent()) {
            throw new IllegalStateException("Production run already has scheduled Work");
        }
        replace(ProductionRunStatus.SCHEDULED, tick, state.currentWorkUnits(), state.executionAttemptCount(),
                OptionalLong.of(tick), Optional.of(workId), state.completionTransactionId(),
                Optional.empty(), Optional.empty(), state.startedTick(), OptionalLong.empty(), state.completedTick());
    }

    public synchronized void beginOrResume(long tick) {
        requireTransition(
                ProductionRunStatus.RUNNING,
                ProductionRunStatus.SCHEDULED,
                ProductionRunStatus.BLOCKED,
                ProductionRunStatus.PAUSED,
                ProductionRunStatus.RUNNING
        );
        OptionalLong started = state.startedTick().isPresent() ? state.startedTick() : OptionalLong.of(requireTick(tick));
        replace(ProductionRunStatus.RUNNING, tick, state.currentWorkUnits(),
                Math.addExact(state.executionAttemptCount(), 1), OptionalLong.empty(),
                state.scheduledWorkId(), state.completionTransactionId(), Optional.empty(), Optional.empty(),
                started, OptionalLong.empty(), state.completedTick());
    }

    public synchronized void advance(long workUnits, long tick) {
        if (state.status() != ProductionRunStatus.RUNNING) {
            throw new IllegalStateException("Production progress requires a running Run");
        }
        if (workUnits <= 0L) throw new IllegalArgumentException("Production progress must be positive");
        long next = Math.addExact(state.currentWorkUnits(), workUnits);
        if (next > state.requiredWorkUnits()) {
            throw new IllegalArgumentException("Production progress exceeds required work");
        }
        replace(ProductionRunStatus.RUNNING, tick, next, state.executionAttemptCount(),
                OptionalLong.empty(), state.scheduledWorkId(), state.completionTransactionId(),
                Optional.empty(), Optional.empty(), state.startedTick(), OptionalLong.empty(), state.completedTick());
    }

    public synchronized void block(ProductionFailure failure, long nextTick, long tick) {
        requireNonterminal();
        if (nextTick <= tick) throw new IllegalArgumentException("Blocked reevaluation tick must be in the future");
        replace(ProductionRunStatus.BLOCKED, tick, state.currentWorkUnits(), state.executionAttemptCount(),
                OptionalLong.of(nextTick), state.scheduledWorkId(), state.completionTransactionId(),
                Optional.of(failure.code()), Optional.of(failure.message()), state.startedTick(),
                OptionalLong.empty(), state.completedTick());
    }

    public synchronized void pause(ProductionFailure failure, long nextTick, long tick) {
        requireNonterminal();
        if (nextTick <= tick) throw new IllegalArgumentException("Paused reevaluation tick must be in the future");
        replace(ProductionRunStatus.PAUSED, tick, state.currentWorkUnits(), state.executionAttemptCount(),
                OptionalLong.of(nextTick), state.scheduledWorkId(), state.completionTransactionId(),
                Optional.of(failure.code()), Optional.of(failure.message()), state.startedTick(),
                OptionalLong.of(tick), state.completedTick());
    }

    public synchronized void awaitTransaction(long tick) {
        if (state.workstationAssignment().isPresent() || state.workstationChain().isPresent()) {
            throw new IllegalStateException("Workstation-assigned Production runs do not await Transactions");
        }
        if (state.status() != ProductionRunStatus.RUNNING || state.currentWorkUnits() != state.requiredWorkUnits()) {
            throw new IllegalStateException("Production transaction requires complete progress");
        }
        replace(ProductionRunStatus.AWAITING_TRANSACTION, tick, state.currentWorkUnits(),
                state.executionAttemptCount(), OptionalLong.empty(), state.scheduledWorkId(),
                state.completionTransactionId(), Optional.empty(), Optional.empty(), state.startedTick(),
                OptionalLong.empty(), state.completedTick());
    }

    public synchronized void complete(TransactionId transactionId, long tick) {
        Objects.requireNonNull(transactionId, "transactionId");
        requireTransition(ProductionRunStatus.COMPLETED, ProductionRunStatus.AWAITING_TRANSACTION);
        if (state.completionTransactionId().isPresent()) {
            throw new IllegalStateException("Production completion Transaction is already recorded");
        }
        if (state.workstationAssignment().flatMap(ProductionWorkstationAssignment::completionEvidence).isPresent()) {
            throw new IllegalStateException("Production run already has workstation completion evidence");
        }
        if (state.workstationChain().flatMap(ProductionWorkstationChain::completionEvidence).isPresent()) {
            throw new IllegalStateException("Production run already has chain completion evidence");
        }
        replace(ProductionRunStatus.COMPLETED, tick, state.requiredWorkUnits(), state.executionAttemptCount(),
                OptionalLong.empty(), state.scheduledWorkId(), Optional.of(transactionId),
                Optional.empty(), Optional.empty(), state.startedTick(), OptionalLong.empty(), OptionalLong.of(tick));
    }

    public synchronized void assignWorkstation(ProductionWorkstationAssignment assignment, long tick) {
        Objects.requireNonNull(assignment, "assignment");
        requireNonterminal();
        if (state.scheduledWorkId().isPresent()
                || state.completionTransactionId().isPresent()
                || state.workstationChain().isPresent()) {
            throw new IllegalStateException("Production run already has a different completion path");
        }
        if (state.status() == ProductionRunStatus.AWAITING_TRANSACTION
                || state.status() == ProductionRunStatus.SCHEDULED
                || state.status() == ProductionRunStatus.RUNNING
                || state.status() == ProductionRunStatus.PAUSED) {
            throw new IllegalStateException("Production run is not eligible for workstation assignment");
        }
        if (state.workstationAssignment().isPresent()) {
            ProductionWorkstationAssignment existing = state.workstationAssignment().orElseThrow();
            if (existing.sameTarget(assignment.workstationIdentity(), assignment.processIdentity())
                    && !existing.executionStarted()) {
                return;
            }
            throw new IllegalStateException("Production run already has a workstation assignment");
        }
        ProductionRunStatus nextStatus = state.status() == ProductionRunStatus.PLANNED
                || state.status() == ProductionRunStatus.BLOCKED
                ? ProductionRunStatus.READY
                : state.status();
        replace(nextStatus, tick, state.currentWorkUnits(), state.executionAttemptCount(),
                OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                state.startedTick(), OptionalLong.empty(), state.completedTick(), Optional.of(assignment));
    }

    public synchronized void assignWorkstationChain(ProductionWorkstationChain chain, long tick) {
        Objects.requireNonNull(chain, "chain");
        requireNonterminal();
        if (state.scheduledWorkId().isPresent()
                || state.completionTransactionId().isPresent()
                || state.workstationAssignment().isPresent()) {
            throw new IllegalStateException("Production run already has a different completion path");
        }
        if (state.status() == ProductionRunStatus.AWAITING_TRANSACTION
                || state.status() == ProductionRunStatus.SCHEDULED
                || state.status() == ProductionRunStatus.RUNNING
                || state.status() == ProductionRunStatus.PAUSED) {
            throw new IllegalStateException("Production run is not eligible for workstation chain assignment");
        }
        if (state.workstationChain().isPresent()) {
            if (state.workstationChain().orElseThrow().equals(chain)) {
                return;
            }
            throw new IllegalStateException("Production run already has a workstation chain");
        }
        ProductionRunStatus nextStatus = state.status() == ProductionRunStatus.PLANNED
                || state.status() == ProductionRunStatus.BLOCKED
                ? ProductionRunStatus.READY
                : state.status();
        replace(nextStatus, tick, state.currentWorkUnits(), state.executionAttemptCount(),
                OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                state.startedTick(), OptionalLong.empty(), state.completedTick(), Optional.empty(), Optional.of(chain));
    }

    public synchronized void assignWorkstationChainStep(
            String stepIdentity,
            String workstationIdentity,
            String processIdentity,
            long tick
    ) {
        requireNonterminal();
        ProductionWorkstationChain chain = requireWorkstationChain();
        ProductionWorkstationChainStep step = chain.step(stepIdentity);
        if (!step.processIdentity().equals(ProductionValidation.requireExternalIdentity(
                processIdentity,
                "Production process identity"
        ))) {
            throw new IllegalStateException("Production chain step process mismatch");
        }
        ProductionWorkstationChain updated = chain.withStepAssignment(stepIdentity, workstationIdentity);
        replace(ProductionRunStatus.READY, tick, state.currentWorkUnits(), state.executionAttemptCount(),
                OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                state.startedTick(), OptionalLong.empty(), state.completedTick(), Optional.empty(), Optional.of(updated));
    }

    public synchronized void bindWorkstationChainExecution(
            String stepIdentity,
            String workstationIdentity,
            String processIdentity,
            String executionOperationIdentity,
            long tick
    ) {
        requireNonterminal();
        ProductionWorkstationChain chain = requireWorkstationChain();
        ProductionWorkstationChainStep step = chain.step(stepIdentity);
        boolean alreadyBound = step.executionOperationIdentity()
                .filter(executionOperationIdentity::equals)
                .isPresent();
        ProductionWorkstationChain updated = chain.withStepExecution(
                stepIdentity,
                workstationIdentity,
                processIdentity,
                executionOperationIdentity
        );
        OptionalLong started = state.startedTick().isPresent() ? state.startedTick() : OptionalLong.of(requireTick(tick));
        replace(ProductionRunStatus.RUNNING, tick, state.currentWorkUnits(),
                alreadyBound ? state.executionAttemptCount() : Math.addExact(state.executionAttemptCount(), 1),
                OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                started, OptionalLong.empty(), state.completedTick(), Optional.empty(), Optional.of(updated));
    }

    public synchronized void completeWorkstationChainStep(
            String stepIdentity,
            ProductionWorkstationCompletionEvidence evidence,
            long tick
    ) {
        Objects.requireNonNull(evidence, "evidence");
        ProductionWorkstationChain chain = requireWorkstationChain();
        ProductionWorkstationChainStep currentStep = chain.step(stepIdentity);
        if (state.status() == ProductionRunStatus.COMPLETED
                && currentStep.completionEvidence().filter(evidence::equals).isPresent()) {
            return;
        }
        requireNonterminal();
        if (!evidence.runId().equals(state.id())) {
            throw new IllegalStateException("Production chain completion references a different Run");
        }
        if (evidence.completedSimulationTick() != requireTick(tick)) {
            throw new IllegalArgumentException("Production chain step completion tick mismatch");
        }
        ProductionWorkstationChain updated = chain.withStepCompletion(stepIdentity, evidence);
        if (updated.allStepsComplete()) {
            ProductionChainCompletionEvidence completion =
                    ProductionChainCompletionEvidence.published(state.id(), updated, tick);
            updated = updated.withCompletionEvidence(completion);
            replace(ProductionRunStatus.COMPLETED, tick, state.requiredWorkUnits(), state.executionAttemptCount(),
                    OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    state.startedTick().isPresent() ? state.startedTick() : OptionalLong.of(tick),
                    OptionalLong.empty(), OptionalLong.of(tick), Optional.empty(), Optional.of(updated));
            return;
        }
        replace(ProductionRunStatus.READY, tick, chainProgressUnits(updated), state.executionAttemptCount(),
                OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                state.startedTick().isPresent() ? state.startedTick() : OptionalLong.of(tick),
                OptionalLong.empty(), state.completedTick(), Optional.empty(), Optional.of(updated));
    }

    public synchronized void failWorkstationChainStep(
            String stepIdentity,
            ProductionFailure failure,
            boolean unknownOutcome,
            long tick
    ) {
        requireNonterminal();
        Objects.requireNonNull(failure, "failure");
        ProductionWorkstationChain chain = requireWorkstationChain();
        ProductionWorkstationChain updated = chain.withFailure(
                stepIdentity,
                unknownOutcome ? ProductionChainStepStatus.UNKNOWN_OUTCOME : ProductionChainStepStatus.FAILED
        );
        replace(ProductionRunStatus.FAILED, tick, state.currentWorkUnits(), state.executionAttemptCount(),
                OptionalLong.empty(), Optional.empty(), Optional.empty(),
                Optional.of(failure.code()), Optional.of(failure.message()), state.startedTick(),
                OptionalLong.empty(), state.completedTick(), Optional.empty(), Optional.of(updated));
    }

    public synchronized void bindWorkstationExecution(
            String workstationIdentity,
            String processIdentity,
            String executionOperationIdentity,
            long tick
    ) {
        requireNonterminal();
        ProductionWorkstationAssignment assignment = requireWorkstationAssignment(workstationIdentity, processIdentity);
        ProductionWorkstationAssignment updated = assignment.withExecutionOperation(executionOperationIdentity);
        boolean alreadyBound = assignment.executionOperationIdentity().isPresent();
        OptionalLong started = state.startedTick().isPresent() ? state.startedTick() : OptionalLong.of(requireTick(tick));
        replace(ProductionRunStatus.RUNNING, tick, state.currentWorkUnits(),
                alreadyBound ? state.executionAttemptCount() : Math.addExact(state.executionAttemptCount(), 1),
                OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                started, OptionalLong.empty(), state.completedTick(), Optional.of(updated));
    }

    public synchronized void completeFromWorkstation(
            ProductionWorkstationCompletionEvidence evidence,
            long tick
    ) {
        Objects.requireNonNull(evidence, "evidence");
        if (state.status() == ProductionRunStatus.COMPLETED
                && state.workstationAssignment().flatMap(ProductionWorkstationAssignment::completionEvidence)
                .filter(evidence::equals)
                .isPresent()) {
            return;
        }
        requireNonterminal();
        if (!evidence.runId().equals(state.id())) {
            throw new IllegalStateException("Production workstation completion references a different Run");
        }
        if (evidence.completedSimulationTick() != requireTick(tick)) {
            throw new IllegalArgumentException("Production workstation completion tick mismatch");
        }
        ProductionWorkstationAssignment assignment = requireWorkstationAssignment(
                evidence.workstationIdentity(),
                evidence.processIdentity()
        );
        ProductionWorkstationAssignment updated = assignment.withCompletionEvidence(evidence);
        replace(ProductionRunStatus.COMPLETED, tick, state.requiredWorkUnits(), state.executionAttemptCount(),
                OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                state.startedTick().isPresent() ? state.startedTick() : OptionalLong.of(tick),
                OptionalLong.empty(), OptionalLong.of(tick), Optional.of(updated));
    }

    public synchronized void fail(ProductionFailure failure, long tick) {
        requireNonterminal();
        replace(ProductionRunStatus.FAILED, tick, state.currentWorkUnits(), state.executionAttemptCount(),
                OptionalLong.empty(), state.scheduledWorkId(), state.completionTransactionId(),
                Optional.of(failure.code()), Optional.of(failure.message()), state.startedTick(),
                OptionalLong.empty(), state.completedTick());
    }

    public synchronized void cancel(String reason, long tick) {
        requireNonterminal();
        if (state.workstationAssignment().filter(ProductionWorkstationAssignment::executionStarted).isPresent()) {
            throw new IllegalStateException("Workstation-owned processing has already begun");
        }
        if (state.workstationChain().filter(ProductionWorkstationChain::hasStartedExecution).isPresent()) {
            throw new IllegalStateException("Workstation-owned processing has already begun");
        }
        Optional<ProductionWorkstationChain> cancelledChain =
                state.workstationChain().map(ProductionWorkstationChain::cancelledBeforeFirstEffect);
        replace(ProductionRunStatus.CANCELLED, tick, state.currentWorkUnits(), state.executionAttemptCount(),
                OptionalLong.empty(), state.scheduledWorkId(), state.completionTransactionId(),
                Optional.of(ProductionFailureCode.INVALID_STATUS),
                Optional.of(ProductionValidation.requireText(reason, "Production cancellation reason", 2_048)),
                state.startedTick(), OptionalLong.empty(), state.completedTick(),
                state.workstationAssignment(), cancelledChain);
    }

    public synchronized void expire(long tick) {
        requireNonterminal();
        replace(ProductionRunStatus.EXPIRED, tick, state.currentWorkUnits(), state.executionAttemptCount(),
                OptionalLong.empty(), state.scheduledWorkId(), state.completionTransactionId(),
                Optional.of(ProductionFailureCode.RUN_EXPIRED), Optional.of("Production run expired"),
                state.startedTick(), OptionalLong.empty(), state.completedTick());
    }

    private void requireTransition(ProductionRunStatus target, ProductionRunStatus... allowed) {
        requireNonterminal();
        for (ProductionRunStatus status : allowed) {
            if (state.status() == status) return;
        }
        throw new IllegalStateException("Invalid production status transition: " + state.status() + " -> " + target);
    }

    private void requireNonterminal() {
        if (state.status().isTerminal()) throw new IllegalStateException("Production run is terminal");
    }

    private void replace(
            ProductionRunStatus status,
            long tick,
            long progress,
            int attempts,
            OptionalLong nextEligibleTick,
            Optional<SimulationWorkId> workId,
            Optional<TransactionId> transactionId,
            Optional<ProductionFailureCode> failureCode,
            Optional<String> failureSummary,
            OptionalLong startedTick,
            OptionalLong pausedTick,
            OptionalLong completedTick
    ) {
        replace(status, tick, progress, attempts, nextEligibleTick, workId, transactionId, failureCode,
                failureSummary, startedTick, pausedTick, completedTick, state.workstationAssignment(),
                state.workstationChain());
    }

    private void replace(
            ProductionRunStatus status,
            long tick,
            long progress,
            int attempts,
            OptionalLong nextEligibleTick,
            Optional<SimulationWorkId> workId,
            Optional<TransactionId> transactionId,
            Optional<ProductionFailureCode> failureCode,
            Optional<String> failureSummary,
            OptionalLong startedTick,
            OptionalLong pausedTick,
            OptionalLong completedTick,
            Optional<ProductionWorkstationAssignment> workstationAssignment
    ) {
        replace(status, tick, progress, attempts, nextEligibleTick, workId, transactionId, failureCode,
                failureSummary, startedTick, pausedTick, completedTick, workstationAssignment,
                state.workstationChain());
    }

    private void replace(
            ProductionRunStatus status,
            long tick,
            long progress,
            int attempts,
            OptionalLong nextEligibleTick,
            Optional<SimulationWorkId> workId,
            Optional<TransactionId> transactionId,
            Optional<ProductionFailureCode> failureCode,
            Optional<String> failureSummary,
            OptionalLong startedTick,
            OptionalLong pausedTick,
            OptionalLong completedTick,
            Optional<ProductionWorkstationAssignment> workstationAssignment,
            Optional<ProductionWorkstationChain> workstationChain
    ) {
        requireCurrentOrFutureTick(tick);
        state = new ProductionRunSnapshot(
                state.id(), state.planId(), status, tick, startedTick, pausedTick, completedTick,
                state.requiredWorkUnits(), progress, attempts, nextEligibleTick, workId, transactionId,
                workstationAssignment, workstationChain, failureCode, failureSummary, Math.addExact(state.revision(), 1L),
                state.schemaVersion()
        );
    }

    private ProductionWorkstationAssignment requireWorkstationAssignment(
            String workstationIdentity,
            String processIdentity
    ) {
        ProductionWorkstationAssignment assignment = state.workstationAssignment()
                .orElseThrow(() -> new IllegalStateException("Production run has no workstation assignment"));
        if (!assignment.sameTarget(workstationIdentity, processIdentity)) {
            throw new IllegalStateException("Production workstation assignment target mismatch");
        }
        return assignment;
    }

    private ProductionWorkstationChain requireWorkstationChain() {
        return state.workstationChain()
                .orElseThrow(() -> new IllegalStateException("Production run has no workstation chain"));
    }

    private long chainProgressUnits(ProductionWorkstationChain chain) {
        long completeSteps = chain.steps().stream()
                .filter(step -> step.status() == ProductionChainStepStatus.COMPLETE)
                .count();
        return state.requiredWorkUnits() * completeSteps / chain.steps().size();
    }

    private void requireCurrentOrFutureTick(long tick) {
        requireTick(tick);
        if (tick < state.lastUpdatedSimulationTick()) {
            throw new IllegalArgumentException("Production simulation tick must not move backward");
        }
    }

    private static long requireTick(long tick) {
        if (tick < 0L) throw new IllegalArgumentException("Production simulation tick must not be negative");
        return tick;
    }
}
