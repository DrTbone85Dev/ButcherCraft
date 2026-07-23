package com.butchercraft.world.simulation.scheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class SimulationSchedulerManager {
    private final SimulationStageRegistry stageRegistry;
    private final SimulationWorkHandlerRegistry handlerRegistry;
    private final Map<SimulationWorkId, SimulationWorkRuntime> runtimes = new LinkedHashMap<>();
    private final Map<SimulationWorkStatus, LinkedHashSet<SimulationWorkId>> byStatus =
            new EnumMap<>(SimulationWorkStatus.class);
    private final NavigableMap<Long, LinkedHashSet<SimulationWorkId>> byNextEligibleTick = new TreeMap<>();
    private SimulationSchedulerRegistry registry;
    private long nextSubmissionSequence;
    private long lastFinalizedSimulationTick;

    public SimulationSchedulerManager(
            SimulationStageRegistry stageRegistry,
            SimulationWorkHandlerRegistry handlerRegistry,
            long initialFinalizedSimulationTick
    ) {
        this(stageRegistry, handlerRegistry, SimulationSchedulerRegistry.empty(), List.of(), 0L,
                initialFinalizedSimulationTick);
    }

    public SimulationSchedulerManager(
            SimulationStageRegistry stageRegistry,
            SimulationWorkHandlerRegistry handlerRegistry,
            SimulationSchedulerRegistry registry,
            Collection<SimulationWorkRuntime> runtimeRecords,
            long nextSubmissionSequence,
            long lastFinalizedSimulationTick
    ) {
        this.stageRegistry = Objects.requireNonNull(stageRegistry, "stageRegistry");
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "handlerRegistry");
        this.registry = Objects.requireNonNull(registry, "registry");
        if (nextSubmissionSequence < 0L) {
            throw new IllegalArgumentException("Next submission sequence must not be negative");
        }
        this.nextSubmissionSequence = nextSubmissionSequence;
        this.lastFinalizedSimulationTick = SchedulerValidation.requireTick(
                lastFinalizedSimulationTick, "Last finalized scheduler tick"
        );
        for (SimulationWorkStatus status : SimulationWorkStatus.values()) {
            byStatus.put(status, new LinkedHashSet<>());
        }
        validateDefinitions();
        loadRuntimes(runtimeRecords);
    }

    public synchronized SimulationStageRegistry stageRegistry() { return stageRegistry; }
    public synchronized SimulationWorkHandlerRegistry handlerRegistry() { return handlerRegistry; }
    public synchronized SimulationSchedulerRegistry registry() { return registry; }
    public synchronized long nextSubmissionSequence() { return nextSubmissionSequence; }
    public synchronized long lastFinalizedSimulationTick() { return lastFinalizedSimulationTick; }

    public synchronized List<SimulationWorkRuntime> runtimeRecords() {
        return registry.definitions().stream().map(work -> runtimes.get(work.id()).snapshot()).toList();
    }

    public synchronized Optional<SimulationWorkRuntime> runtimeFor(SimulationWorkId id) {
        SimulationWorkRuntime runtime = runtimes.get(Objects.requireNonNull(id, "id"));
        return runtime == null ? Optional.empty() : Optional.of(runtime.snapshot());
    }

    public synchronized List<ScheduledSimulationWork> findByStatus(SimulationWorkStatus status) {
        return byStatus.get(Objects.requireNonNull(status, "status")).stream()
                .map(id -> registry.find(id).orElseThrow()).sorted(ordering()).toList();
    }

    public synchronized List<ScheduledSimulationWork> findByType(SimulationWorkTypeId id) {
        return registry.findByType(id);
    }

    public synchronized List<ScheduledSimulationWork> findByStage(SimulationStageId id) {
        return registry.findByStage(id);
    }

    public synchronized List<ScheduledSimulationWork> findByOriginSubsystem(String id) {
        return registry.findByOriginSubsystem(id);
    }

    public synchronized List<ScheduledSimulationWork> findByCorrelationId(String id) {
        return registry.findByCorrelationId(id);
    }

    public synchronized List<ScheduledSimulationWork> findByReference(WorkReference reference) {
        return registry.findByReference(reference);
    }

    public synchronized List<ScheduledSimulationWork> findScheduledBetween(long firstTick, long lastTick) {
        return registry.findScheduledBetween(firstTick, lastTick);
    }

    public synchronized List<ScheduledSimulationWork> findDueAt(long tick) {
        SchedulerValidation.requireTick(tick, "Due-work query tick");
        LinkedHashSet<SimulationWorkId> ids = new LinkedHashSet<>();
        byNextEligibleTick.headMap(tick, true).values().forEach(ids::addAll);
        return ids.stream().map(id -> registry.find(id).orElseThrow()).sorted(ordering()).toList();
    }

    public synchronized List<ScheduledSimulationWork> findEligibleAt(long tick) {
        SchedulerValidation.requireTick(tick, "Eligible-work query tick");
        LinkedHashSet<SimulationWorkId> ids = new LinkedHashSet<>(byStatus.get(SimulationWorkStatus.ELIGIBLE));
        byNextEligibleTick.headMap(tick, true).values().forEach(ids::addAll);
        return ids.stream().map(id -> registry.find(id).orElseThrow())
                .filter(work -> work.expirationTick().isEmpty() || tick <= work.expirationTick().orElseThrow())
                .sorted(ordering()).toList();
    }

    public synchronized WorkSubmissionResult submit(SimulationWorkRequest request, long authoritativeTick) {
        WorkBatchSubmissionResult result = submitBatch(List.of(request), authoritativeTick, 0);
        if (!result.accepted()) {
            return WorkSubmissionResult.rejected(result.failureCode().orElseThrow(), result.messages().getFirst());
        }
        return WorkSubmissionResult.accepted(result.work().getFirst());
    }

    synchronized WorkBatchSubmissionResult submitBatch(
            List<SimulationWorkRequest> requests,
            long authoritativeTick,
            int generationDepth
    ) {
        SchedulerValidation.requireTick(authoritativeTick, "Submission tick");
        if (generationDepth < 0) throw new IllegalArgumentException("Generation depth must not be negative");
        List<SimulationWorkRequest> batch = List.copyOf(Objects.requireNonNull(requests, "requests"));
        if (batch.isEmpty()) return WorkBatchSubmissionResult.accepted(List.of());
        Set<SimulationWorkId> ids = new LinkedHashSet<>();
        for (SimulationWorkRequest request : batch) {
            if (request == null) {
                return WorkBatchSubmissionResult.rejected(WorkFailureCode.VALIDATION_FAILED,
                        "Generated work batch contains a null request");
            }
            WorkBatchSubmissionResult validation = validateRequest(request, authoritativeTick, ids);
            if (validation != null) return validation;
        }
        long sequenceAfterBatch;
        try {
            sequenceAfterBatch = Math.addExact(nextSubmissionSequence, batch.size());
        } catch (ArithmeticException exception) {
            return WorkBatchSubmissionResult.rejected(WorkFailureCode.INTERNAL_INVARIANT_VIOLATION,
                    "Authoritative submission sequence overflow");
        }
        List<ScheduledSimulationWork> scheduled = new ArrayList<>(batch.size());
        long sequence = nextSubmissionSequence;
        for (SimulationWorkRequest request : batch) {
            scheduled.add(ScheduledSimulationWork.fromRequest(request, sequence++, generationDepth));
        }
        List<ScheduledSimulationWork> updated = new ArrayList<>(registry.definitions());
        updated.addAll(scheduled);
        SimulationSchedulerRegistry candidate = SimulationSchedulerRegistry.of(updated);
        for (ScheduledSimulationWork work : scheduled) {
            SimulationWorkRuntime runtime = SimulationWorkRuntime.scheduled(work);
            runtimes.put(work.id(), runtime);
            index(runtime);
        }
        registry = candidate;
        nextSubmissionSequence = sequenceAfterBatch;
        return WorkBatchSubmissionResult.accepted(scheduled);
    }

    public synchronized SchedulerOperationResult cancel(SimulationWorkId id, long tick, String reason) {
        SimulationWorkRuntime runtime = runtimes.get(Objects.requireNonNull(id, "id"));
        if (runtime == null) {
            return SchedulerOperationResult.failure(WorkFailureCode.UNKNOWN_WORK, "Unknown simulation work: " + id);
        }
        if (runtime.status().isTerminal() || runtime.status() == SimulationWorkStatus.RUNNING) {
            return SchedulerOperationResult.failure(WorkFailureCode.INVALID_STATUS,
                    "Work cannot be cancelled from status " + runtime.status());
        }
        if (tick < runtime.lastUpdatedSimulationTick()) {
            return SchedulerOperationResult.failure(WorkFailureCode.BACKWARD_TICK,
                    "Cancellation tick precedes the work runtime tick");
        }
        change(runtime, () -> runtime.cancel(tick, reason));
        return SchedulerOperationResult.success();
    }

    synchronized EligibilityUpdate promoteDue(long tick, int maximumTransitions) {
        SchedulerValidation.requireTick(tick, "Eligibility tick");
        if (maximumTransitions < 0) throw new IllegalArgumentException("Transition limit must not be negative");
        int promoted = 0;
        int expired = 0;
        List<SimulationWorkId> due = new ArrayList<>();
        outer:
        for (Map.Entry<Long, LinkedHashSet<SimulationWorkId>> entry
                : byNextEligibleTick.headMap(tick, true).entrySet()) {
            for (SimulationWorkId id : entry.getValue()) {
                if (due.size() >= maximumTransitions) break outer;
                due.add(id);
            }
        }
        for (SimulationWorkId id : due) {
            ScheduledSimulationWork work = registry.find(id).orElseThrow();
            SimulationWorkRuntime runtime = runtimes.get(id);
            if (work.expirationTick().isPresent() && tick > work.expirationTick().orElseThrow()) {
                change(runtime, () -> runtime.expire(tick));
                expired++;
            } else {
                change(runtime, () -> runtime.makeEligible(tick));
                promoted++;
            }
        }
        return new EligibilityUpdate(promoted, expired);
    }

    synchronized List<ScheduledSimulationWork> eligibleForStage(SimulationStageId stageId) {
        return byStatus.get(SimulationWorkStatus.ELIGIBLE).stream()
                .map(id -> registry.find(id).orElseThrow())
                .filter(work -> work.stageId().equals(stageId)).sorted(ordering()).toList();
    }

    synchronized boolean hasEligibleWork() {
        return !byStatus.get(SimulationWorkStatus.ELIGIBLE).isEmpty();
    }

    synchronized boolean hasDueWork(long tick) {
        return !byNextEligibleTick.headMap(tick, true).isEmpty();
    }

    synchronized SimulationWorkRuntime start(SimulationWorkId id, long tick) {
        SimulationWorkRuntime runtime = requiredRuntime(id);
        change(runtime, () -> runtime.start(tick));
        return runtime.snapshot();
    }

    synchronized void complete(SimulationWorkId id, long tick, WorkPayload summary) {
        SimulationWorkRuntime runtime = requiredRuntime(id);
        change(runtime, () -> runtime.complete(tick, summary));
    }

    synchronized void defer(SimulationWorkId id, long tick, long nextTick, String reason) {
        SimulationWorkRuntime runtime = requiredRuntime(id);
        change(runtime, () -> runtime.defer(tick, nextTick, reason));
    }

    synchronized void retry(
            SimulationWorkId id, long tick, long nextTick, WorkFailureCode code, String reason
    ) {
        SimulationWorkRuntime runtime = requiredRuntime(id);
        change(runtime, () -> runtime.retry(tick, nextTick, code, reason));
    }

    synchronized void fail(SimulationWorkId id, long tick, WorkFailureCode code, String reason) {
        SimulationWorkRuntime runtime = requiredRuntime(id);
        change(runtime, () -> runtime.fail(tick, code, reason));
    }

    synchronized void finalizeTick(long tick) {
        long expected = Math.addExact(lastFinalizedSimulationTick, 1L);
        if (tick != expected) {
            throw new IllegalStateException("Scheduler tick finalization expected " + expected + " but received " + tick);
        }
        lastFinalizedSimulationTick = tick;
    }

    public synchronized boolean hasRunningWork() {
        return !byStatus.get(SimulationWorkStatus.RUNNING).isEmpty();
    }

    public synchronized void validateForPersistence() {
        if (hasRunningWork()) throw new IllegalStateException("Cannot persist scheduler while work is RUNNING");
        for (ScheduledSimulationWork work : registry.definitions()) {
            requiredRuntime(work.id()).validateAgainst(work);
        }
    }

    private WorkBatchSubmissionResult validateRequest(
            SimulationWorkRequest request, long authoritativeTick, Set<SimulationWorkId> ids
    ) {
        if (request.origin().submissionTick() != authoritativeTick) {
            return WorkBatchSubmissionResult.rejected(WorkFailureCode.INVALID_TICK,
                    "Work origin submission tick must equal the authoritative submission tick");
        }
        if (request.scheduledTick() < authoritativeTick) {
            return WorkBatchSubmissionResult.rejected(WorkFailureCode.BACKWARD_TICK,
                    "Work cannot be scheduled before the authoritative submission tick");
        }
        if (!stageRegistry.contains(request.stageId())) {
            return WorkBatchSubmissionResult.rejected(WorkFailureCode.UNKNOWN_STAGE,
                    "Unknown simulation stage: " + request.stageId().value());
        }
        SimulationWorkHandler handler = handlerRegistry.find(request.typeId()).orElse(null);
        if (handler == null) {
            return WorkBatchSubmissionResult.rejected(WorkFailureCode.HANDLER_NOT_REGISTERED,
                    "No handler registered for work type: " + request.typeId().value());
        }
        if (registry.contains(request.id()) || !ids.add(request.id())) {
            return WorkBatchSubmissionResult.rejected(WorkFailureCode.DUPLICATE_WORK_ID,
                    "Duplicate simulation work id: " + request.id().value());
        }
        ScheduledSimulationWork candidate = ScheduledSimulationWork.fromRequest(request, nextSubmissionSequence, 0);
        WorkValidationResult validation;
        try {
            validation = Objects.requireNonNull(handler.validate(candidate), "handler validation result");
        } catch (RuntimeException exception) {
            return WorkBatchSubmissionResult.rejected(WorkFailureCode.HANDLER_EXCEPTION,
                    "Handler validation failed unexpectedly: " + exception.getClass().getSimpleName());
        }
        if (!validation.accepted()) {
            String message = validation.messages().isEmpty() ? "Handler rejected work payload"
                    : validation.messages().getFirst();
            return WorkBatchSubmissionResult.rejected(validation.failureCode().orElse(WorkFailureCode.INVALID_PAYLOAD),
                    message);
        }
        return null;
    }

    private void validateDefinitions() {
        long maximumSequence = -1L;
        for (ScheduledSimulationWork work : registry.definitions()) {
            if (!stageRegistry.contains(work.stageId())) {
                throw new IllegalArgumentException("Persisted work references unknown stage: " + work.stageId());
            }
            SimulationWorkHandler handler = handlerRegistry.find(work.typeId()).orElseThrow(() ->
                    new IllegalArgumentException("Persisted work has no registered handler: " + work.typeId()));
            WorkValidationResult result = Objects.requireNonNull(handler.validate(work), "handler validation result");
            if (!result.accepted()) {
                throw new IllegalArgumentException("Persisted work payload rejected: " + work.id());
            }
            maximumSequence = Math.max(maximumSequence, work.authoritativeSubmissionSequence());
        }
        if (maximumSequence >= nextSubmissionSequence) {
            throw new IllegalArgumentException("Next submission sequence must exceed every persisted sequence");
        }
    }

    private void loadRuntimes(Collection<SimulationWorkRuntime> records) {
        for (SimulationWorkRuntime runtime : Objects.requireNonNull(records, "runtimeRecords")) {
            SimulationWorkRuntime value = Objects.requireNonNull(runtime, "runtime").snapshot();
            ScheduledSimulationWork work = registry.find(value.workId()).orElseThrow(() ->
                    new IllegalArgumentException("Runtime references unknown work: " + value.workId()));
            value.validateAgainst(work);
            if (runtimes.putIfAbsent(value.workId(), value) != null) {
                throw new IllegalArgumentException("Duplicate runtime for work: " + value.workId());
            }
            index(value);
        }
        if (runtimes.size() != registry.size()) {
            throw new IllegalArgumentException("Every scheduled work definition requires exactly one runtime");
        }
    }

    private Comparator<ScheduledSimulationWork> ordering() {
        return Comparator
                .comparingInt((ScheduledSimulationWork work) -> stageRegistry.find(work.stageId())
                        .orElseThrow().executionOrder())
                .thenComparingLong(ScheduledSimulationWork::scheduledTick)
                .thenComparing(Comparator.comparingInt((ScheduledSimulationWork work) -> work.priority().rank())
                        .reversed())
                .thenComparingLong(ScheduledSimulationWork::authoritativeSubmissionSequence)
                .thenComparing(ScheduledSimulationWork::id);
    }

    private SimulationWorkRuntime requiredRuntime(SimulationWorkId id) {
        SimulationWorkRuntime runtime = runtimes.get(Objects.requireNonNull(id, "id"));
        if (runtime == null) throw new IllegalArgumentException("Unknown simulation work: " + id);
        return runtime;
    }

    private void change(SimulationWorkRuntime runtime, Runnable mutation) {
        unindex(runtime);
        try {
            mutation.run();
        } finally {
            index(runtime);
        }
    }

    private void index(SimulationWorkRuntime runtime) {
        byStatus.get(runtime.status()).add(runtime.workId());
        runtime.nextEligibleTick().ifPresent(tick ->
                byNextEligibleTick.computeIfAbsent(tick, ignored -> new LinkedHashSet<>()).add(runtime.workId()));
    }

    private void unindex(SimulationWorkRuntime runtime) {
        byStatus.get(runtime.status()).remove(runtime.workId());
        runtime.nextEligibleTick().ifPresent(tick -> {
            LinkedHashSet<SimulationWorkId> ids = byNextEligibleTick.get(tick);
            if (ids != null) {
                ids.remove(runtime.workId());
                if (ids.isEmpty()) byNextEligibleTick.remove(tick);
            }
        });
    }
}
