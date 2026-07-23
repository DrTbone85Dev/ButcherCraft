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
                Optional.empty(), Optional.empty(), 0L, ProductionSchema.CURRENT_VERSION
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
        replace(ProductionRunStatus.COMPLETED, tick, state.requiredWorkUnits(), state.executionAttemptCount(),
                OptionalLong.empty(), state.scheduledWorkId(), Optional.of(transactionId),
                Optional.empty(), Optional.empty(), state.startedTick(), OptionalLong.empty(), OptionalLong.of(tick));
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
        replace(ProductionRunStatus.CANCELLED, tick, state.currentWorkUnits(), state.executionAttemptCount(),
                OptionalLong.empty(), state.scheduledWorkId(), state.completionTransactionId(),
                Optional.of(ProductionFailureCode.INVALID_STATUS),
                Optional.of(ProductionValidation.requireText(reason, "Production cancellation reason", 2_048)),
                state.startedTick(), OptionalLong.empty(), state.completedTick());
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
        requireCurrentOrFutureTick(tick);
        state = new ProductionRunSnapshot(
                state.id(), state.planId(), status, tick, startedTick, pausedTick, completedTick,
                state.requiredWorkUnits(), progress, attempts, nextEligibleTick, workId, transactionId,
                failureCode, failureSummary, Math.addExact(state.revision(), 1L), state.schemaVersion()
        );
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
