package com.butchercraft.productioncontrol;

import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.world.business.runtime.BusinessRuntimeObservationSnapshot;
import com.butchercraft.world.business.runtime.BusinessScheduleBoundary;
import com.butchercraft.world.production.ProductionChainStepStatus;
import com.butchercraft.world.production.ProductionDeadline;
import com.butchercraft.world.production.ProductionDeadlineStatus;
import com.butchercraft.world.production.ProductionFailureCode;
import com.butchercraft.world.production.ProductionRunId;
import com.butchercraft.world.production.ProductionRunSnapshot;
import com.butchercraft.world.production.ProductionRunStatus;
import com.butchercraft.world.production.ProductionWorkstationChain;
import com.butchercraft.world.production.ProductionWorkstationChainStatus;
import com.butchercraft.world.production.ProductionWorkstationChainStep;

import java.util.Objects;
import java.util.Optional;

public record ProductionOrderStatusSnapshot(
        boolean hasRun,
        boolean staleReference,
        ProductionRunStatus runStatus,
        ProductionWorkstationChainStatus chainStatus,
        ProductionChainStepStatus grinderStepStatus,
        ProductionChainStepStatus pattyFormerStepStatus,
        boolean grinderAssigned,
        boolean pattyFormerAssigned,
        boolean grinderMissing,
        boolean pattyFormerMissing,
        int grinderProgressPercent,
        int pattyFormerProgressPercent,
        WorkstationState grinderWorkstationState,
        WorkstationState pattyFormerWorkstationState,
        boolean canCancel,
        Optional<ProductionFailureCode> failureCode,
        ProductionOrderNextAction nextAction,
        boolean businessObserved,
        boolean plantOpen,
        int businessDayOfWeekOrdinal,
        int businessHour,
        int businessMinute,
        int activeShiftDisplayCode,
        int nextShiftDisplayCode,
        boolean hasDeadline,
        ProductionDeadlineStatus deadlineStatus,
        int deadlineDayOfWeekOrdinal,
        int deadlineHour,
        int deadlineMinute,
        int deadlineDeltaMinutes
) {
    public ProductionOrderStatusSnapshot {
        runStatus = Objects.requireNonNull(runStatus, "runStatus");
        chainStatus = Objects.requireNonNull(chainStatus, "chainStatus");
        grinderStepStatus = Objects.requireNonNull(grinderStepStatus, "grinderStepStatus");
        pattyFormerStepStatus = Objects.requireNonNull(pattyFormerStepStatus, "pattyFormerStepStatus");
        grinderProgressPercent = clampPercent(grinderProgressPercent);
        pattyFormerProgressPercent = clampPercent(pattyFormerProgressPercent);
        grinderWorkstationState = Objects.requireNonNull(grinderWorkstationState, "grinderWorkstationState");
        pattyFormerWorkstationState = Objects.requireNonNull(pattyFormerWorkstationState, "pattyFormerWorkstationState");
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        nextAction = Objects.requireNonNull(nextAction, "nextAction");
        businessDayOfWeekOrdinal = Math.max(0, businessDayOfWeekOrdinal);
        businessHour = Math.max(0, Math.min(23, businessHour));
        businessMinute = Math.max(0, Math.min(59, businessMinute));
        activeShiftDisplayCode = Math.max(0, activeShiftDisplayCode);
        nextShiftDisplayCode = Math.max(0, nextShiftDisplayCode);
        deadlineStatus = Objects.requireNonNull(deadlineStatus, "deadlineStatus");
        deadlineDayOfWeekOrdinal = Math.max(0, deadlineDayOfWeekOrdinal);
        deadlineHour = Math.max(0, Math.min(23, deadlineHour));
        deadlineMinute = Math.max(0, Math.min(59, deadlineMinute));
    }

    public static ProductionOrderStatusSnapshot empty() {
        return empty(Optional.empty());
    }

    public static ProductionOrderStatusSnapshot empty(Optional<BusinessRuntimeObservationSnapshot> business) {
        return base(false, false, ProductionRunStatus.PLANNED,
                ProductionWorkstationChainStatus.AWAITING_GRINDER_ASSIGNMENT,
                ProductionOrderNextAction.CREATE_RUN, business, Optional.empty(), Optional.empty());
    }

    public static ProductionOrderStatusSnapshot stale() {
        return stale(Optional.empty());
    }

    public static ProductionOrderStatusSnapshot stale(Optional<BusinessRuntimeObservationSnapshot> business) {
        return base(false, true, ProductionRunStatus.FAILED,
                ProductionWorkstationChainStatus.UNKNOWN_OUTCOME,
                ProductionOrderNextAction.STALE_REFERENCE, business, Optional.empty(), Optional.empty());
    }

    public static ProductionOrderStatusSnapshot fromRun(
            ProductionRunSnapshot run,
            WorkstationObservation grinder,
            WorkstationObservation pattyFormer
    ) {
        return fromRun(run, grinder, pattyFormer, Optional.empty());
    }

    public static ProductionOrderStatusSnapshot fromRun(
            ProductionRunSnapshot run,
            WorkstationObservation grinder,
            WorkstationObservation pattyFormer,
            Optional<BusinessRuntimeObservationSnapshot> business
    ) {
        Objects.requireNonNull(run, "run");
        ProductionWorkstationChain chain = run.workstationChain().orElse(null);
        if (chain == null) {
            return create(
                    true,
                    false,
                    run.status(),
                    ProductionWorkstationChainStatus.UNKNOWN_OUTCOME,
                    ProductionChainStepStatus.UNKNOWN_OUTCOME,
                    ProductionChainStepStatus.UNKNOWN_OUTCOME,
                    false,
                    false,
                    false,
                    false,
                    0,
                    0,
                    WorkstationState.IDLE,
                    WorkstationState.IDLE,
                    false,
                    run.failureCode(),
                    ProductionOrderNextAction.UNKNOWN_OUTCOME,
                    business,
                    run.deadline(),
                    Optional.of(run.id())
            );
        }

        ProductionWorkstationChainStep grinderStep = chain.steps().get(0);
        ProductionWorkstationChainStep pattyStep = chain.steps().get(1);
        WorkstationObservation grinderView = Objects.requireNonNull(grinder, "grinder");
        WorkstationObservation pattyView = Objects.requireNonNull(pattyFormer, "pattyFormer");
        ProductionOrderNextAction action = nextAction(run, chain, grinderStep, pattyStep, grinderView, pattyView);
        return create(
                true,
                false,
                run.status(),
                chain.status(),
                grinderStep.status(),
                pattyStep.status(),
                grinderStep.workstationIdentity().isPresent(),
                pattyStep.workstationIdentity().isPresent(),
                grinderView.missing(),
                pattyView.missing(),
                grinderView.progressPercent(),
                pattyView.progressPercent(),
                grinderView.workstationState(),
                pattyView.workstationState(),
                !run.status().isTerminal() && !chain.hasStartedExecution(),
                run.failureCode(),
                action,
                business,
                run.deadline(),
                Optional.of(run.id())
        );
    }

    private static ProductionOrderStatusSnapshot base(
            boolean hasRun,
            boolean staleReference,
            ProductionRunStatus runStatus,
            ProductionWorkstationChainStatus chainStatus,
            ProductionOrderNextAction nextAction,
            Optional<BusinessRuntimeObservationSnapshot> business,
            Optional<ProductionDeadline> deadline,
            Optional<ProductionRunId> runId
    ) {
        return create(
                hasRun,
                staleReference,
                runStatus,
                chainStatus,
                ProductionChainStepStatus.AWAITING_ASSIGNMENT,
                ProductionChainStepStatus.AWAITING_ASSIGNMENT,
                false,
                false,
                false,
                false,
                0,
                0,
                WorkstationState.IDLE,
                WorkstationState.IDLE,
                false,
                Optional.empty(),
                nextAction,
                business,
                deadline,
                runId
        );
    }

    private static ProductionOrderStatusSnapshot create(
            boolean hasRun,
            boolean staleReference,
            ProductionRunStatus runStatus,
            ProductionWorkstationChainStatus chainStatus,
            ProductionChainStepStatus grinderStepStatus,
            ProductionChainStepStatus pattyFormerStepStatus,
            boolean grinderAssigned,
            boolean pattyFormerAssigned,
            boolean grinderMissing,
            boolean pattyFormerMissing,
            int grinderProgressPercent,
            int pattyFormerProgressPercent,
            WorkstationState grinderWorkstationState,
            WorkstationState pattyFormerWorkstationState,
            boolean canCancel,
            Optional<ProductionFailureCode> failureCode,
            ProductionOrderNextAction nextAction,
            Optional<BusinessRuntimeObservationSnapshot> business,
            Optional<ProductionDeadline> deadline,
            Optional<ProductionRunId> runId
    ) {
        return new ProductionOrderStatusSnapshot(
                hasRun,
                staleReference,
                runStatus,
                chainStatus,
                grinderStepStatus,
                pattyFormerStepStatus,
                grinderAssigned,
                pattyFormerAssigned,
                grinderMissing,
                pattyFormerMissing,
                grinderProgressPercent,
                pattyFormerProgressPercent,
                grinderWorkstationState,
                pattyFormerWorkstationState,
                canCancel,
                failureCode,
                nextAction,
                business.isPresent(),
                business.map(BusinessRuntimeObservationSnapshot::plantOpen).orElse(false),
                business.map(value -> value.calendar().dayOfWeek().ordinal()).orElse(0),
                business.map(value -> value.calendar().timeOfDay().hour()).orElse(0),
                business.map(value -> value.calendar().timeOfDay().minute()).orElse(0),
                business.flatMap(BusinessRuntimeObservationSnapshot::activeShift)
                        .map(ProductionOrderStatusSnapshot::shiftDisplayCode)
                        .orElse(0),
                business.flatMap(BusinessRuntimeObservationSnapshot::nextShift)
                        .map(ProductionOrderStatusSnapshot::shiftDisplayCode)
                        .orElse(0),
                deadline.isPresent(),
                deadline.map(ProductionDeadline::status).orElse(ProductionDeadlineStatus.NO_DEADLINE),
                deadline.map(value -> value.dayOfWeek().ordinal()).orElse(0),
                deadline.map(value -> value.businessTime().hour()).orElse(0),
                deadline.map(value -> value.businessTime().minute()).orElse(0),
                deadlineDeltaMinutes(deadline, business, runId)
        );
    }

    private static int shiftDisplayCode(BusinessScheduleBoundary boundary) {
        String display = boundary.displayName();
        if ("Day Shift".equals(display)) {
            return 1;
        }
        if ("Evening Shift".equals(display)) {
            return 2;
        }
        return 3;
    }

    private static int deadlineDeltaMinutes(
            Optional<ProductionDeadline> deadline,
            Optional<BusinessRuntimeObservationSnapshot> business,
            Optional<ProductionRunId> runId
    ) {
        if (deadline.isEmpty() || business.isEmpty() || runId.isEmpty()) {
            return 0;
        }
        long delta = Math.subtractExact(
                deadline.orElseThrow().dueAbsoluteMinute(),
                com.butchercraft.world.business.runtime.BusinessOperatingSchedule.absoluteMinute(
                        business.orElseThrow().calendar()
                )
        );
        if (delta > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (delta < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) delta;
    }

    private static ProductionOrderNextAction nextAction(
            ProductionRunSnapshot run,
            ProductionWorkstationChain chain,
            ProductionWorkstationChainStep grinderStep,
            ProductionWorkstationChainStep pattyStep,
            WorkstationObservation grinder,
            WorkstationObservation pattyFormer
    ) {
        if (run.status() == ProductionRunStatus.CANCELLED
                || chain.status() == ProductionWorkstationChainStatus.CANCELLED_BEFORE_FIRST_EFFECT) {
            return ProductionOrderNextAction.CANCELLED;
        }
        if (chain.status() == ProductionWorkstationChainStatus.UNKNOWN_OUTCOME) {
            return ProductionOrderNextAction.UNKNOWN_OUTCOME;
        }
        Optional<ProductionOrderNextAction> blockedAction = blockedWorkstationAction(
                grinderStep, pattyStep, grinder, pattyFormer
        );
        if (blockedAction.isPresent()) {
            return blockedAction.orElseThrow();
        }
        if (run.status() == ProductionRunStatus.FAILED
                || chain.status() == ProductionWorkstationChainStatus.FAILED) {
            return ProductionOrderNextAction.FAILED;
        }
        if (run.status() == ProductionRunStatus.COMPLETED
                || chain.status() == ProductionWorkstationChainStatus.COMPLETE) {
            return pattyFormer.workstationState() == WorkstationState.COMPLETE
                    ? ProductionOrderNextAction.COLLECT_BEEF_PATTIES
                    : ProductionOrderNextAction.COMPLETE;
        }
        if (grinderStep.workstationIdentity().isEmpty()) {
            return ProductionOrderNextAction.ASSIGN_GRINDER;
        }
        if (grinderStep.status() == ProductionChainStepStatus.ASSIGNED
                && grinder.workstationState() != WorkstationState.PROCESSING
                && grinder.workstationState() != WorkstationState.COMPLETE) {
            return ProductionOrderNextAction.LOAD_BEEF_TRIM;
        }
        if (grinderStep.status() == ProductionChainStepStatus.RUNNING
                || grinder.workstationState() == WorkstationState.PROCESSING) {
            return ProductionOrderNextAction.WAIT_FOR_GRINDER;
        }
        if (grinderStep.status() == ProductionChainStepStatus.COMPLETE
                && pattyStep.workstationIdentity().isEmpty()) {
            return ProductionOrderNextAction.ASSIGN_PATTY_FORMER;
        }
        if (grinderStep.status() == ProductionChainStepStatus.COMPLETE
                && pattyStep.status() == ProductionChainStepStatus.AWAITING_ASSIGNMENT) {
            return ProductionOrderNextAction.MOVE_GROUND_BEEF;
        }
        if (pattyStep.workstationIdentity().isEmpty()) {
            return ProductionOrderNextAction.ASSIGN_PATTY_FORMER;
        }
        if (grinderStep.status() == ProductionChainStepStatus.COMPLETE
                && pattyStep.status() == ProductionChainStepStatus.ASSIGNED
                && pattyFormer.workstationState() != WorkstationState.PROCESSING
                && pattyFormer.workstationState() != WorkstationState.COMPLETE) {
            return ProductionOrderNextAction.MOVE_GROUND_BEEF;
        }
        if (pattyStep.status() == ProductionChainStepStatus.ASSIGNED
                && pattyFormer.workstationState() != WorkstationState.PROCESSING
                && pattyFormer.workstationState() != WorkstationState.COMPLETE) {
            return ProductionOrderNextAction.LOAD_GROUND_BEEF;
        }
        if (pattyStep.status() == ProductionChainStepStatus.RUNNING
                || pattyFormer.workstationState() == WorkstationState.PROCESSING) {
            return ProductionOrderNextAction.WAIT_FOR_PATTY_FORMER;
        }
        if (pattyStep.status() == ProductionChainStepStatus.COMPLETE) {
            return ProductionOrderNextAction.COLLECT_BEEF_PATTIES;
        }
        return ProductionOrderNextAction.MOVE_GROUND_BEEF;
    }

    private static Optional<ProductionOrderNextAction> blockedWorkstationAction(
            ProductionWorkstationChainStep grinderStep,
            ProductionWorkstationChainStep pattyStep,
            WorkstationObservation grinder,
            WorkstationObservation pattyFormer
    ) {
        if (grinderStep.workstationIdentity().isPresent() && outputBlocked(grinder)) {
            return Optional.of(ProductionOrderNextAction.CLEAR_GRINDER_OUTPUT);
        }
        if (pattyStep.workstationIdentity().isPresent() && outputBlocked(pattyFormer)) {
            return Optional.of(ProductionOrderNextAction.CLEAR_PATTY_FORMER_OUTPUT);
        }
        return Optional.empty();
    }

    private static boolean outputBlocked(WorkstationObservation observation) {
        return observation.failureCode()
                .map(code -> code == WorkstationFailureCode.OUTPUT_OCCUPIED
                        || code == WorkstationFailureCode.OUTPUT_INCOMPATIBLE)
                .orElse(false);
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public record WorkstationObservation(
            boolean missing,
            int progressPercent,
            WorkstationState workstationState,
            Optional<WorkstationFailureCode> failureCode
    ) {
        public WorkstationObservation {
            progressPercent = clampPercent(progressPercent);
            workstationState = Objects.requireNonNull(workstationState, "workstationState");
            failureCode = Objects.requireNonNull(failureCode, "failureCode");
        }

        public WorkstationObservation(
                boolean missing,
                int progressPercent,
                WorkstationState workstationState
        ) {
            this(missing, progressPercent, workstationState, Optional.empty());
        }

        public static WorkstationObservation unassigned() {
            return new WorkstationObservation(false, 0, WorkstationState.IDLE);
        }

        public static WorkstationObservation missingWorkstation() {
            return new WorkstationObservation(true, 0, WorkstationState.ERROR);
        }
    }
}
