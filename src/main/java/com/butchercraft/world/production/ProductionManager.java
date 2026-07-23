package com.butchercraft.world.production;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.order.ContractId;
import com.butchercraft.world.economy.order.OrderId;
import com.butchercraft.world.production.scheduler.ProductionWorkTypes;
import com.butchercraft.world.simulation.scheduler.BuiltInSimulationStages;
import com.butchercraft.world.simulation.scheduler.RetryPolicy;
import com.butchercraft.world.simulation.scheduler.SimulationExecutionContext;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import com.butchercraft.world.simulation.scheduler.SimulationWorkOutcome;
import com.butchercraft.world.simulation.scheduler.SimulationWorkRequest;
import com.butchercraft.world.simulation.scheduler.SimulationWorkResult;
import com.butchercraft.world.simulation.scheduler.SimulationWorkStatus;
import com.butchercraft.world.simulation.scheduler.WorkFailureCode;
import com.butchercraft.world.simulation.scheduler.WorkOrigin;
import com.butchercraft.world.simulation.scheduler.WorkPayload;
import com.butchercraft.world.simulation.scheduler.WorkPayloadEntry;
import com.butchercraft.world.simulation.scheduler.WorkReference;
import com.butchercraft.world.simulation.scheduler.WorkSubmissionResult;
import com.butchercraft.world.transaction.EconomicTransaction;
import com.butchercraft.world.transaction.TransactionResult;
import com.butchercraft.world.transaction.TransactionStatus;
import com.butchercraft.world.transaction.TransactionId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;

public final class ProductionManager {
    private final ProductionDependencies dependencies;
    private final ProductionValidator validator;
    private final ProductionTransactionFactory transactionFactory;
    private final Map<ProductionRunId, ProductionRunRuntime> runs = new LinkedHashMap<>();
    private final Map<ProductionPlanId, ProductionRunId> runByPlan = new LinkedHashMap<>();
    private final Map<ProductionRunStatus, LinkedHashSet<ProductionRunId>> runsByStatus =
            new EnumMap<>(ProductionRunStatus.class);
    private final Map<SimulationWorkId, ProductionRunId> runByWork = new LinkedHashMap<>();
    private final Map<TransactionId, ProductionRunId> runByTransaction = new LinkedHashMap<>();
    private final NavigableMap<Long, LinkedHashSet<ProductionRunId>> completedByTick = new TreeMap<>();
    private final NavigableMap<Long, LinkedHashSet<ProductionRunId>> failedByTick = new TreeMap<>();
    private ProductionProcessRegistry processRegistry;
    private ProductionPlanRegistry planRegistry;

    public ProductionManager(ProductionDependencies dependencies) {
        this(dependencies, ProductionProcessRegistry.empty(), ProductionPlanRegistry.empty(), List.of());
    }

    public ProductionManager(
            ProductionDependencies dependencies,
            ProductionProcessRegistry processRegistry,
            ProductionPlanRegistry planRegistry,
            Collection<ProductionRunSnapshot> loadedRuns
    ) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
        this.validator = new ProductionValidator(dependencies);
        this.transactionFactory = new ProductionTransactionFactory(dependencies);
        this.processRegistry = Objects.requireNonNull(processRegistry, "processRegistry");
        this.planRegistry = Objects.requireNonNull(planRegistry, "planRegistry");
        for (ProductionRunStatus status : ProductionRunStatus.values()) {
            runsByStatus.put(status, new LinkedHashSet<>());
        }
        validateDefinitions();
        loadRuns(loadedRuns);
    }

    public synchronized ProductionProcessRegistry processRegistry() { return processRegistry; }
    public synchronized ProductionPlanRegistry planRegistry() { return planRegistry; }

    public synchronized ProductionOperationResult<ProductionProcessDefinition> registerProcess(
            ProductionProcessDefinition definition
    ) {
        Objects.requireNonNull(definition, "definition");
        if (processRegistry.contains(definition.id())) {
            return rejected(ProductionFailureCode.DUPLICATE_PROCESS_ID,
                    "Duplicate production process id", definition.id().value());
        }
        List<ProductionFailure> failures = validator.validateProcess(definition);
        if (!failures.isEmpty()) return ProductionOperationResult.rejected(failures);
        List<ProductionProcessDefinition> updated = new ArrayList<>(processRegistry.definitions());
        updated.add(definition);
        processRegistry = ProductionProcessRegistry.of(updated);
        return ProductionOperationResult.accepted(definition);
    }

    public synchronized ProductionOperationResult<List<ProductionProcessDefinition>> registerProcesses(
            Collection<ProductionProcessDefinition> definitions
    ) {
        ProductionProcessRegistryBuilder builder = ProductionProcessRegistry.builder();
        processRegistry.definitions().forEach(builder::register);
        List<ProductionProcessDefinition> added = new ArrayList<>();
        try {
            for (ProductionProcessDefinition definition : Objects.requireNonNull(definitions, "definitions")) {
                List<ProductionFailure> failures = validator.validateProcess(definition);
                if (!failures.isEmpty()) return ProductionOperationResult.rejected(failures);
                builder.register(definition);
                added.add(definition);
            }
        } catch (IllegalArgumentException exception) {
            return rejected(ProductionFailureCode.DUPLICATE_PROCESS_ID,
                    message(exception, "Invalid production process batch"), "butchercraft:production");
        }
        processRegistry = builder.build();
        return ProductionOperationResult.accepted(List.copyOf(added));
    }

    public synchronized ProductionOperationResult<ProductionRunSnapshot> registerPlan(
            ProductionPlanDefinition plan
    ) {
        if (planRegistry.contains(plan.id())) {
            return rejected(ProductionFailureCode.DUPLICATE_PLAN_ID,
                    "Duplicate production plan id", plan.id().value());
        }
        List<ProductionFailure> failures = validator.validatePlan(plan, processRegistry);
        if (!failures.isEmpty()) return ProductionOperationResult.rejected(failures);
        ProductionRunId runId = ProductionRunId.forPlan(plan.id());
        if (runs.containsKey(runId)) {
            return rejected(ProductionFailureCode.DUPLICATE_RUN_ID,
                    "Duplicate production run id", runId.value());
        }
        ProductionProcessDefinition process = processRegistry.find(plan.processId()).orElseThrow();
        long requiredWork;
        try {
            requiredWork = process.duration().requiredWorkUnits(plan.batchCount());
        } catch (ArithmeticException exception) {
            return rejected(ProductionFailureCode.VALIDATION_FAILED,
                    "Production work requirement overflow", plan.id().value());
        }
        List<ProductionPlanDefinition> plans = new ArrayList<>(planRegistry.definitions());
        plans.add(plan);
        ProductionPlanRegistry candidatePlans = ProductionPlanRegistry.of(plans);
        ProductionRunRuntime runtime = ProductionRunRuntime.planned(
                runId, plan.id(), requiredWork, plan.createdSimulationTick()
        );
        planRegistry = candidatePlans;
        runs.put(runId, runtime);
        runByPlan.put(plan.id(), runId);
        index(runtime.snapshot());
        return ProductionOperationResult.accepted(runtime.snapshot());
    }

    public synchronized ProductionOperationResult<ProductionRunSnapshot> registerAndSchedulePlan(
            ProductionPlanDefinition plan,
            SimulationSchedulerManager scheduler,
            long tick
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(scheduler, "scheduler");
        ProductionPlanDefinition existing = planRegistry.find(plan.id()).orElse(null);
        if (existing != null) {
            if (!existing.equals(plan)) {
                return rejected(ProductionFailureCode.DUPLICATE_PLAN_ID,
                        "Production plan identity already has different content", plan.id().value());
            }
            ProductionRunSnapshot run = findRunByPlan(plan.id()).orElseThrow();
            if (run.scheduledWorkId().isPresent()) {
                return ProductionOperationResult.accepted(run);
            }
            return schedule(run.id(), scheduler, tick);
        }
        ProductionOperationResult<ProductionRunSnapshot> registered = registerPlan(plan);
        if (!registered.accepted()) return registered;
        ProductionRunSnapshot run = registered.value().orElseThrow();
        try {
            ProductionOperationResult<ProductionRunSnapshot> scheduled = schedule(run.id(), scheduler, tick);
            if (scheduled.accepted()) return scheduled;
            removeUnscheduledPlan(plan.id(), run.id());
            return scheduled;
        } catch (RuntimeException exception) {
            removeUnscheduledPlan(plan.id(), run.id());
            throw exception;
        }
    }

    public synchronized List<ProductionFailure> evaluatePlanReadiness(
            ProductionPlanDefinition plan,
            long tick
    ) {
        Objects.requireNonNull(plan, "plan");
        List<ProductionFailure> definitionFailures = validator.validatePlan(plan, processRegistry);
        if (!definitionFailures.isEmpty()) return definitionFailures;
        ProductionProcessDefinition process = processRegistry.find(plan.processId()).orElseThrow();
        return validator.validateReadiness(plan, process, tick);
    }

    public synchronized Optional<ProductionRunSnapshot> findRun(ProductionRunId id) {
        ProductionRunRuntime runtime = runs.get(Objects.requireNonNull(id, "id"));
        return runtime == null ? Optional.empty() : Optional.of(runtime.snapshot());
    }

    public synchronized Optional<ProductionRunSnapshot> findRunByPlan(ProductionPlanId id) {
        ProductionRunId runId = runByPlan.get(Objects.requireNonNull(id, "id"));
        return runId == null ? Optional.empty() : findRun(runId);
    }

    public synchronized List<ProductionRunSnapshot> runs() {
        return planRegistry.definitions().stream()
                .map(plan -> runs.get(runByPlan.get(plan.id())).snapshot())
                .toList();
    }

    public synchronized List<ProductionRunSnapshot> findByStatus(ProductionRunStatus status) {
        return snapshots(runsByStatus.get(Objects.requireNonNull(status, "status")));
    }

    public synchronized List<ProductionRunSnapshot> findByProcess(ProductionProcessId id) {
        return snapshotsForPlans(planRegistry.findByProcess(id));
    }

    public synchronized List<ProductionRunSnapshot> findByActor(ActorId id) {
        return snapshotsForPlans(planRegistry.findByActor(id));
    }

    public synchronized List<ProductionRunSnapshot> findByBusiness(BusinessId id) {
        return snapshotsForPlans(planRegistry.findByBusiness(id));
    }

    public synchronized List<ProductionRunSnapshot> findByOrder(OrderId id) {
        return snapshotsForPlans(planRegistry.findByOrder(id));
    }

    public synchronized List<ProductionRunSnapshot> findByContract(ContractId id) {
        return snapshotsForPlans(planRegistry.findByContract(id));
    }

    public synchronized Optional<ProductionRunSnapshot> findByScheduledWork(SimulationWorkId id) {
        ProductionRunId runId = runByWork.get(Objects.requireNonNull(id, "id"));
        return runId == null ? Optional.empty() : findRun(runId);
    }

    public synchronized Optional<ProductionRunSnapshot> findByCompletionTransaction(TransactionId id) {
        ProductionRunId runId = runByTransaction.get(Objects.requireNonNull(id, "id"));
        return runId == null ? Optional.empty() : findRun(runId);
    }

    public synchronized List<ProductionRunSnapshot> activeRuns() {
        return runs().stream().filter(run -> !run.status().isTerminal()).toList();
    }

    public synchronized List<ProductionRunSnapshot> blockedRuns() {
        return findByStatus(ProductionRunStatus.BLOCKED);
    }

    public synchronized List<ProductionRunSnapshot> readyRuns() {
        return findByStatus(ProductionRunStatus.READY);
    }

    public synchronized List<ProductionRunSnapshot> completedBetween(long first, long last) {
        return terminalBetween(completedByTick, first, last);
    }

    public synchronized List<ProductionRunSnapshot> failedBetween(long first, long last) {
        return terminalBetween(failedByTick, first, last);
    }

    public synchronized List<ProductionRunSnapshot> overdueRuns(long tick) {
        if (tick < 0L) throw new IllegalArgumentException("Production overdue query tick must not be negative");
        return activeRuns().stream().filter(run -> planRegistry.find(run.planId()).orElseThrow()
                .latestCompletionTick().stream().anyMatch(deadline -> deadline < tick)).toList();
    }

    public synchronized ProductionOperationResult<ProductionRunSnapshot> schedule(
            ProductionRunId runId,
            SimulationSchedulerManager scheduler,
            long tick
    ) {
        Objects.requireNonNull(scheduler, "scheduler");
        ProductionRunRuntime runtime = runs.get(Objects.requireNonNull(runId, "runId"));
        if (runtime == null) return rejected(ProductionFailureCode.UNKNOWN_RUN, "Unknown production run", runId.value());
        ProductionRunSnapshot current = runtime.snapshot();
        if (current.status().isTerminal()) {
            return rejected(ProductionFailureCode.TERMINAL_RUN, "Production run is terminal", runId.value());
        }
        if (current.scheduledWorkId().isPresent()) {
            return rejected(
                    ProductionFailureCode.WORK_ALREADY_BOUND,
                    "Production run already has scheduled Work",
                    current.scheduledWorkId().orElseThrow().value()
            );
        }
        ProductionPlanDefinition plan = planRegistry.find(current.planId()).orElseThrow();
        ProductionProcessDefinition process = processRegistry.find(plan.processId()).orElseThrow();
        List<ProductionFailure> readiness = validator.validateReadiness(plan, process, tick);
        if (!readiness.isEmpty()) {
            applyRequirementFailure(runtime, readiness.getFirst(), process.executionPolicy(), tick);
            return ProductionOperationResult.rejected(readiness);
        }
        if (runtime.snapshot().status() != ProductionRunStatus.READY) {
            mutate(runtime, () -> runtime.markReady(tick));
        }
        SimulationWorkId workId = SimulationWorkId.of(runId.value() + "/work");
        SimulationWorkRequest request = SimulationWorkRequest.builder()
                .id(workId)
                .typeId(ProductionWorkTypes.PRODUCTION_RUN)
                .stageId(BuiltInSimulationStages.EXECUTION)
                .scheduledTick(Math.max(tick, plan.earliestStartTick()))
                .priority(plan.priority().schedulerPriority())
                .origin(new WorkOrigin(
                        "butchercraft:production",
                        Optional.of("butchercraft:production_run"),
                        Optional.of(runId.value()),
                        tick,
                        "butchercraft:production_manager",
                        plan.metadata().correlationId(),
                        Optional.empty()
                ))
                .payload(new WorkPayload(List.of(
                        WorkPayloadEntry.identifier(ProductionWorkTypes.RUN_ID_PAYLOAD_KEY, runId.value())
                )))
                .retryPolicy(RetryPolicy.never())
                .maximumAttempts(process.executionPolicy().maximumExecutionAttempts())
                .expirationTick(plan.latestCompletionTick().orElse(Long.MAX_VALUE))
                .references(List.of(
                        new WorkReference("butchercraft:production_plan", plan.id().value()),
                        new WorkReference("butchercraft:production_process", process.id().value()),
                        new WorkReference("butchercraft:production_run", runId.value())
                ))
                .build();
        WorkSubmissionResult submitted = scheduler.submit(request, tick);
        if (!submitted.accepted()) {
            return rejected(ProductionFailureCode.VALIDATION_FAILED,
                    submitted.messages().isEmpty() ? "Scheduler rejected production Work"
                            : submitted.messages().getFirst(),
                    runId.value());
        }
        mutate(runtime, () -> runtime.bindScheduledWork(workId, tick));
        return ProductionOperationResult.accepted(runtime.snapshot());
    }

    public synchronized SimulationWorkResult executeScheduledRun(
            ProductionRunId runId,
            SimulationExecutionContext context
    ) {
        long tick = context.authoritativeSimulationTick();
        ProductionRunRuntime runtime = runs.get(runId);
        if (runtime == null) {
            return failedWork(tick, WorkFailureCode.UNKNOWN_WORK, "Unknown production run", 1);
        }
        ProductionRunSnapshot current = runtime.snapshot();
        if (current.scheduledWorkId().isEmpty()
                || !current.scheduledWorkId().orElseThrow().equals(context.work().id())) {
            return failedWork(tick, WorkFailureCode.INVALID_PAYLOAD,
                    "Production Work does not match the run binding", 1);
        }
        if (current.status().isTerminal()) {
            return failedWork(tick, WorkFailureCode.INVALID_STATUS,
                    "Production Work references a terminal run", 1);
        }
        ProductionPlanDefinition plan = planRegistry.find(current.planId()).orElseThrow();
        ProductionProcessDefinition process = processRegistry.find(plan.processId()).orElseThrow();
        int cost = deterministicCost(process, plan);
        if (cost > context.remainingWorkUnits()) {
            return deferredWork(tick, Math.addExact(tick, 1L),
                    "Production Work deferred by deterministic budget", 0, runId);
        }
        if (plan.latestCompletionTick().isPresent() && tick > plan.latestCompletionTick().orElseThrow()) {
            mutate(runtime, () -> runtime.expire(tick));
            return completedWork(tick, cost, runId);
        }
        List<ProductionFailure> readiness = validator.validateReadiness(plan, process, tick);
        if (!readiness.isEmpty()) {
            ProductionFailure failure = readiness.getFirst();
            applyRequirementFailure(runtime, failure, process.executionPolicy(), tick);
            ProductionRunSnapshot blocked = runtime.snapshot();
            if (blocked.status().isTerminal()) {
                return failedWork(tick, WorkFailureCode.HANDLER_REJECTED, failure.message(), cost);
            }
            return deferredWork(tick, blocked.nextEligibleTick().orElseThrow(),
                    failure.message(), cost, runId);
        }
        current = runtime.snapshot();
        if (current.executionAttemptCount() >= process.executionPolicy().maximumExecutionAttempts()) {
            ProductionFailure failure = ProductionFailure.of(
                    ProductionFailureCode.RETRY_LIMIT_REACHED,
                    "Production execution attempt limit reached",
                    runId.value()
            );
            mutate(runtime, () -> runtime.fail(failure, tick));
            return failedWork(tick, WorkFailureCode.RETRY_LIMIT_REACHED, failure.message(), cost);
        }
        boolean continuing = current.status() == ProductionRunStatus.RUNNING;
        long elapsed = continuing ? tick - current.lastUpdatedSimulationTick() : 0L;
        mutate(runtime, () -> runtime.beginOrResume(tick));
        if (elapsed > 0L) {
            ProductionRunSnapshot running = runtime.snapshot();
            long remaining = running.requiredWorkUnits() - running.currentWorkUnits();
            long advance = Math.min(remaining, elapsed);
            if (advance > 0L) mutate(runtime, () -> runtime.advance(advance, tick));
        }
        current = runtime.snapshot();
        if (current.currentWorkUnits() < current.requiredWorkUnits()) {
            long remaining = current.requiredWorkUnits() - current.currentWorkUnits();
            long delay = Math.min(process.duration().progressQuantumTicks(), remaining);
            return deferredWork(tick, Math.addExact(tick, delay),
                    "Production progress remains incomplete", cost, runId);
        }
        mutate(runtime, () -> runtime.awaitTransaction(tick));
        ProductionOperationResult<EconomicTransaction> built =
                transactionFactory.build(runtime.snapshot(), plan, process, context.work().id(), tick);
        if (!built.accepted()) {
            return handleTransactionFailure(runtime, built.failures().getFirst(), process, tick, cost, runId);
        }
        EconomicTransaction transaction = built.value().orElseThrow();
        TransactionResult result = dependencies.transactionManager().submit(transaction);
        if (!result.success()) {
            ProductionFailure failure = ProductionFailure.of(
                    ProductionFailureCode.TRANSACTION_REJECTED,
                    result.validationMessages().isEmpty() ? "Production completion Transaction was rejected"
                            : result.validationMessages().getFirst(),
                    transaction.id().value()
            );
            return handleTransactionFailure(runtime, failure, process, tick, cost, runId);
        }
        EconomicTransaction applied = dependencies.transactionManager().find(transaction.id()).orElseThrow();
        if (applied.status() != TransactionStatus.APPLIED) {
            ProductionFailure failure = ProductionFailure.of(
                    ProductionFailureCode.TRANSACTION_NOT_APPLIED,
                    "Production completion Transaction did not become APPLIED",
                    transaction.id().value()
            );
            return handleTransactionFailure(runtime, failure, process, tick, cost, runId);
        }
        mutate(runtime, () -> runtime.complete(transaction.id(), tick));
        return completedWork(tick, cost, runId);
    }

    public synchronized ProductionOperationResult<ProductionRunSnapshot> cancel(
            ProductionRunId runId,
            SimulationSchedulerManager scheduler,
            long tick,
            String reason
    ) {
        ProductionRunRuntime runtime = runs.get(Objects.requireNonNull(runId, "runId"));
        if (runtime == null) return rejected(ProductionFailureCode.UNKNOWN_RUN, "Unknown production run", runId.value());
        ProductionRunSnapshot current = runtime.snapshot();
        if (current.status().isTerminal()) {
            return rejected(ProductionFailureCode.TERMINAL_RUN, "Production run is terminal", runId.value());
        }
        if (current.scheduledWorkId().isPresent()) {
            com.butchercraft.world.simulation.scheduler.SchedulerOperationResult cancelled =
                    scheduler.cancel(current.scheduledWorkId().orElseThrow(), tick, reason);
            if (!cancelled.successful()) {
                return rejected(ProductionFailureCode.VALIDATION_FAILED,
                        cancelled.messages().isEmpty() ? "Scheduler rejected production cancellation"
                                : cancelled.messages().getFirst(),
                        runId.value());
            }
        }
        mutate(runtime, () -> runtime.cancel(reason, tick));
        return ProductionOperationResult.accepted(runtime.snapshot());
    }

    public synchronized void validateSchedulerReferences(SimulationSchedulerManager scheduler) {
        Objects.requireNonNull(scheduler, "scheduler");
        for (ProductionRunSnapshot run : runs()) {
            if (run.scheduledWorkId().isEmpty()) continue;
            SimulationWorkId workId = run.scheduledWorkId().orElseThrow();
            com.butchercraft.world.simulation.scheduler.ScheduledSimulationWork work =
                    scheduler.registry().find(workId).orElseThrow(() -> new IllegalArgumentException(
                            "Production run references unknown Scheduler Work: " + workId.value()
                    ));
            if (!work.typeId().equals(ProductionWorkTypes.PRODUCTION_RUN)) {
                throw new IllegalArgumentException("Production run references a different Scheduler Work type");
            }
            SimulationWorkStatus workStatus = scheduler.runtimeFor(workId).orElseThrow().status();
            if (!run.status().isTerminal() && workStatus.isTerminal()) {
                throw new IllegalArgumentException("Active production run references terminal Scheduler Work");
            }
        }
    }

    public synchronized void validateForPersistence() {
        for (ProductionRunSnapshot run : runs()) {
            if (run.status() == ProductionRunStatus.AWAITING_TRANSACTION) {
                throw new IllegalStateException("Cannot persist Production while a Run awaits a Transaction");
            }
        }
        validateDefinitions();
        validateLoadedRunReferences();
    }

    private SimulationWorkResult handleTransactionFailure(
            ProductionRunRuntime runtime,
            ProductionFailure failure,
            ProductionProcessDefinition process,
            long tick,
            int cost,
            ProductionRunId runId
    ) {
        if (failure.code() == ProductionFailureCode.DESTINATION_CAPACITY_EXCEEDED) {
            applyRequirementFailure(runtime, failure, process.executionPolicy(), tick);
            ProductionRunSnapshot updated = runtime.snapshot();
            if (updated.status().isTerminal()) {
                return failedWork(tick, WorkFailureCode.HANDLER_REJECTED, failure.message(), cost);
            }
            return deferredWork(tick, updated.nextEligibleTick().orElseThrow(),
                    failure.message(), cost, runId);
        }
        if (process.executionPolicy().transactionFailurePolicy() == ProductionTransactionFailurePolicy.FAIL) {
            mutate(runtime, () -> runtime.fail(failure, tick));
            return failedWork(tick, WorkFailureCode.HANDLER_REJECTED, failure.message(), cost);
        }
        long next = Math.addExact(tick, process.executionPolicy().blockedRetryDelayTicks());
        mutate(runtime, () -> runtime.block(failure, next, tick));
        return deferredWork(tick, next, failure.message(), cost, runId);
    }

    private void applyRequirementFailure(
            ProductionRunRuntime runtime,
            ProductionFailure failure,
            ProductionExecutionPolicy policy,
            long tick
    ) {
        ProductionRequirementLossPolicy selected = switch (failure.code()) {
            case WORKFORCE_INSUFFICIENT, REQUIRED_POSITION_MISSING,
                    REQUIRED_CERTIFICATION_MISSING, REQUIRED_SKILL_MISSING,
                    UNKNOWN_WORKFORCE_REFERENCE -> policy.workforceLossPolicy();
            case BUSINESS_NOT_OPERATIONAL, BUSINESS_CLOSED, BUSINESS_IN_MAINTENANCE,
                    NO_ACTIVE_SHIFT, UNKNOWN_BUSINESS -> policy.businessLossPolicy();
            case DESTINATION_CAPACITY_EXCEEDED -> policy.destinationLossPolicy();
            default -> policy.inputLossPolicy();
        };
        long next = Math.addExact(tick, policy.blockedRetryDelayTicks());
        switch (selected) {
            case BLOCK -> mutate(runtime, () -> runtime.block(failure, next, tick));
            case PAUSE -> mutate(runtime, () -> runtime.pause(failure, next, tick));
            case FAIL -> mutate(runtime, () -> runtime.fail(failure, tick));
            case CANCEL -> mutate(runtime, () -> runtime.cancel(failure.message(), tick));
        }
    }

    private void validateDefinitions() {
        for (ProductionProcessDefinition process : processRegistry.definitions()) {
            List<ProductionFailure> failures = validator.validateProcess(process);
            if (!failures.isEmpty()) throw invalidPersistence("process", process.id().value(), failures);
        }
        for (ProductionPlanDefinition plan : planRegistry.definitions()) {
            List<ProductionFailure> failures = validator.validatePlan(plan, processRegistry);
            if (!failures.isEmpty()) throw invalidPersistence("plan", plan.id().value(), failures);
        }
    }

    private void removeUnscheduledPlan(ProductionPlanId planId, ProductionRunId runId) {
        ProductionRunRuntime runtime = runs.get(runId);
        if (runtime == null || runtime.snapshot().scheduledWorkId().isPresent()) {
            throw new IllegalStateException("Cannot roll back a scheduled Production plan");
        }
        unindex(runtime.snapshot());
        runs.remove(runId);
        runByPlan.remove(planId);
        planRegistry = ProductionPlanRegistry.of(planRegistry.definitions().stream()
                .filter(definition -> !definition.id().equals(planId))
                .toList());
    }

    private void loadRuns(Collection<ProductionRunSnapshot> loadedRuns) {
        for (ProductionRunSnapshot snapshot : Objects.requireNonNull(loadedRuns, "loadedRuns")) {
            ProductionRunRuntime runtime = new ProductionRunRuntime(Objects.requireNonNull(snapshot, "run"));
            if (runs.putIfAbsent(snapshot.id(), runtime) != null) {
                throw new IllegalArgumentException("Duplicate production run id: " + snapshot.id().value());
            }
            if (runByPlan.putIfAbsent(snapshot.planId(), snapshot.id()) != null) {
                throw new IllegalArgumentException("Production schema 1 permits one Run per Plan");
            }
            index(snapshot);
        }
        validateLoadedRunReferences();
    }

    private void validateLoadedRunReferences() {
        if (runs.size() != planRegistry.size()) {
            throw new IllegalArgumentException("Every production Plan requires exactly one Run");
        }
        for (ProductionPlanDefinition plan : planRegistry.definitions()) {
            ProductionRunId runId = runByPlan.get(plan.id());
            if (runId == null) throw new IllegalArgumentException("Production Plan is missing its Run");
            ProductionRunSnapshot run = runs.get(runId).snapshot();
            if (!run.planId().equals(plan.id())) {
                throw new IllegalArgumentException("Production Run references the wrong Plan");
            }
            if (run.completionTransactionId().isPresent()) {
                EconomicTransaction transaction = dependencies.transactionManager()
                        .find(run.completionTransactionId().orElseThrow())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Production Run references an unknown completion Transaction"
                        ));
                if (transaction.status() != TransactionStatus.APPLIED) {
                    throw new IllegalArgumentException("Production completion Transaction is not APPLIED");
                }
            }
        }
    }

    private void mutate(ProductionRunRuntime runtime, Runnable mutation) {
        ProductionRunSnapshot before = runtime.snapshot();
        unindex(before);
        try {
            mutation.run();
        } catch (RuntimeException exception) {
            index(before);
            throw exception;
        }
        index(runtime.snapshot());
    }

    private void index(ProductionRunSnapshot run) {
        runsByStatus.get(run.status()).add(run.id());
        run.scheduledWorkId().ifPresent(id -> {
            ProductionRunId duplicate = runByWork.putIfAbsent(id, run.id());
            if (duplicate != null && !duplicate.equals(run.id())) {
                throw new IllegalArgumentException("Scheduler Work is bound to multiple production Runs");
            }
        });
        run.completionTransactionId().ifPresent(id -> {
            ProductionRunId duplicate = runByTransaction.putIfAbsent(id, run.id());
            if (duplicate != null && !duplicate.equals(run.id())) {
                throw new IllegalArgumentException("Transaction completes multiple production Runs");
            }
        });
        if (run.status() == ProductionRunStatus.COMPLETED) {
            completedByTick.computeIfAbsent(run.completedTick().orElseThrow(),
                    ignored -> new LinkedHashSet<>()).add(run.id());
        }
        if (run.status() == ProductionRunStatus.FAILED) {
            failedByTick.computeIfAbsent(run.lastUpdatedSimulationTick(),
                    ignored -> new LinkedHashSet<>()).add(run.id());
        }
    }

    private void unindex(ProductionRunSnapshot run) {
        runsByStatus.get(run.status()).remove(run.id());
        run.scheduledWorkId().ifPresent(runByWork::remove);
        run.completionTransactionId().ifPresent(runByTransaction::remove);
        if (run.status() == ProductionRunStatus.COMPLETED) {
            removeTickIndex(completedByTick, run.completedTick().orElseThrow(), run.id());
        }
        if (run.status() == ProductionRunStatus.FAILED) {
            removeTickIndex(failedByTick, run.lastUpdatedSimulationTick(), run.id());
        }
    }

    private static void removeTickIndex(
            NavigableMap<Long, LinkedHashSet<ProductionRunId>> index,
            long tick,
            ProductionRunId id
    ) {
        LinkedHashSet<ProductionRunId> ids = index.get(tick);
        if (ids == null) return;
        ids.remove(id);
        if (ids.isEmpty()) index.remove(tick);
    }

    private List<ProductionRunSnapshot> snapshots(Collection<ProductionRunId> ids) {
        return ids.stream().map(id -> runs.get(id).snapshot()).sorted(
                java.util.Comparator.comparing(ProductionRunSnapshot::id)).toList();
    }

    private List<ProductionRunSnapshot> snapshotsForPlans(List<ProductionPlanDefinition> plans) {
        return plans.stream().map(plan -> runs.get(runByPlan.get(plan.id())).snapshot()).toList();
    }

    private List<ProductionRunSnapshot> terminalBetween(
            NavigableMap<Long, LinkedHashSet<ProductionRunId>> index,
            long first,
            long last
    ) {
        if (first < 0L || last < first) throw new IllegalArgumentException("Invalid production Run tick range");
        return index.subMap(first, true, last, true).values().stream()
                .flatMap(Collection::stream).map(id -> runs.get(id).snapshot()).toList();
    }

    private static int deterministicCost(
            ProductionProcessDefinition process,
            ProductionPlanDefinition plan
    ) {
        return Math.addExact(1, Math.addExact(
                process.inputs().size() + process.outputs().size(),
                plan.inventoryBindings().size()
        ));
    }

    private static SimulationWorkResult deferredWork(
            long tick,
            long nextTick,
            String message,
            int cost,
            ProductionRunId runId
    ) {
        return new SimulationWorkResult(
                SimulationWorkOutcome.DEFERRED,
                Optional.empty(),
                List.of(message),
                OptionalLong.of(nextTick),
                List.of(),
                resultPayload(runId),
                cost,
                tick
        );
    }

    private static SimulationWorkResult completedWork(long tick, int cost, ProductionRunId runId) {
        return new SimulationWorkResult(
                SimulationWorkOutcome.COMPLETED,
                Optional.empty(),
                List.of(),
                OptionalLong.empty(),
                List.of(),
                resultPayload(runId),
                cost,
                tick
        );
    }

    private static SimulationWorkResult failedWork(
            long tick,
            WorkFailureCode code,
            String message,
            int cost
    ) {
        return new SimulationWorkResult(
                SimulationWorkOutcome.FAILED,
                Optional.of(code),
                List.of(message),
                OptionalLong.empty(),
                List.of(),
                WorkPayload.empty(),
                cost,
                tick
        );
    }

    private static WorkPayload resultPayload(ProductionRunId runId) {
        return new WorkPayload(List.of(
                WorkPayloadEntry.identifier(ProductionWorkTypes.RUN_ID_PAYLOAD_KEY, runId.value())
        ));
    }

    private static IllegalArgumentException invalidPersistence(
            String type,
            String id,
            List<ProductionFailure> failures
    ) {
        return new IllegalArgumentException("Invalid persisted production " + type + " " + id + ": "
                + failures.stream().map(ProductionFailure::message)
                .collect(java.util.stream.Collectors.joining("; ")));
    }

    private static <T> ProductionOperationResult<T> rejected(
            ProductionFailureCode code,
            String message,
            String reference
    ) {
        return ProductionOperationResult.rejected(ProductionFailure.of(code, message, reference));
    }

    private static String message(RuntimeException exception, String fallback) {
        return exception.getMessage() == null ? fallback : exception.getMessage();
    }
}
