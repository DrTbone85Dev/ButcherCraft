package com.butchercraft.integration.employee;

import com.butchercraft.entity.employee.EmployeeEntity;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.workstation.WorkstationFailure;
import com.butchercraft.workstation.WorkstationProductionRequestResult;
import com.butchercraft.workstation.WorkstationProductionSnapshot;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.WorkstationTickContext;
import com.butchercraft.workstation.reservation.WorkstationReservationRecord;
import com.butchercraft.workstation.reservation.WorkstationReservationState;
import com.butchercraft.world.EmployeeService;
import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.WorkstationReservationService;
import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.ExecutionOperationSnapshot;
import com.butchercraft.world.execution.ExecutionStatus;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.employee.EmployeePresenceObservation;
import com.butchercraft.world.workforce.employee.EmployeePresenceState;
import com.butchercraft.world.workforce.employee.EmployeeStatus;
import com.butchercraft.world.workforce.employee.EmployeeWorkstationOperationState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

/**
 * Narrow Minecraft integration for the IM-027 Grinder operation slice.
 */
public final class EmployeeWorkstationOperationService {
    public static final EmployeeWorkstationOperationService INSTANCE = new EmployeeWorkstationOperationService();

    private static final String GRINDER_TYPE = "grinder";
    private static final String BEEF_TRIM_PRODUCT_ID = BuiltInDefinitionIds.BEEF_TRIM.toString();
    private static final String BEEF_RECIPE_ID = BuiltInDefinitionIds.GRIND_BEEF.toString();
    private static final long COMPLETE_VISIBILITY_TICKS = 20L;

    private EmployeeWorkstationOperationService() {
    }

    public RequestResult request(EmployeeEntity employee) {
        Objects.requireNonNull(employee, "employee");
        if (!(employee.level() instanceof ServerLevel level)) {
            return RequestResult.rejected(RequestStatus.RESERVATION_MISSING_OR_INVALID,
                    "employee is not in an authoritative server world");
        }
        EmployeeId employeeId;
        try {
            employeeId = new EmployeeId(employee.employeeIdValue());
        } catch (IllegalArgumentException exception) {
            return RequestResult.rejected(RequestStatus.EMPLOYEE_NOT_PRESENT,
                    "employee entity has no valid Employee Identity");
        }

        var observed = EmployeeService.INSTANCE.observe(level.getServer(), employeeId);
        if (!observed.succeeded()) {
            return RequestResult.rejected(RequestStatus.EMPLOYEE_NOT_PRESENT,
                    observed.failure().orElseThrow().detail());
        }
        EmployeePresenceObservation observation = observed.orThrow();
        if (observation.status() != EmployeeStatus.ACTIVE
                || observation.presenceState() != EmployeePresenceState.PRESENT
                || !observation.plantOpen()) {
            return RequestResult.rejected(RequestStatus.EMPLOYEE_NOT_PRESENT, observation.reason());
        }

        Optional<WorkstationReservationRecord> reservation = WorkstationReservationService.INSTANCE
                .managerFor(level.getServer())
                .findByEmployee(employeeId.value());
        if (reservation.isEmpty()) {
            return RequestResult.rejected(RequestStatus.RESERVATION_MISSING_OR_INVALID,
                    "employee has no active workstation reservation");
        }
        WorkstationReservationRecord value = reservation.orElseThrow();
        if (!GRINDER_TYPE.equals(value.workstationType())) {
            return RequestResult.rejected(RequestStatus.UNSUPPORTED_WORKSTATION,
                    "reserved workstation is not a Grinder");
        }
        if (value.state() != WorkstationReservationState.EMPLOYEE_ARRIVED) {
            return RequestResult.rejected(RequestStatus.RESERVATION_MISSING_OR_INVALID,
                    "reservation state is " + value.state().serializedName());
        }
        if (!WorkstationReservationService.INSTANCE.isWithinOperatingTolerance(
                level,
                value,
                employee.position()
        )) {
            return RequestResult.rejected(RequestStatus.EMPLOYEE_NOT_AT_WORKSTATION,
                    "employee is outside the reserved Grinder operating tolerance");
        }
        BlockPos workstationPos = workstationPos(value);
        if (!(level.getBlockEntity(workstationPos) instanceof GrinderBlockEntity grinder) || grinder.isRemoved()) {
            return RequestResult.rejected(RequestStatus.RESERVATION_MISSING_OR_INVALID,
                    "reserved Grinder is missing or invalid");
        }

        String reservationKey = reservationKey(value);
        if (!employee.workstationOperationReservationKey().isBlank()
                && !employee.workstationOperationReservationKey().equals(reservationKey)) {
            if (employee.workstationOperationState().active()) {
                return RequestResult.rejected(RequestStatus.ALREADY_REQUESTED,
                        "employee already has an active workstation operation");
            }
            employee.resetWorkstationOperation();
        }
        if (employee.workstationOperationState() == EmployeeWorkstationOperationState.OPERATION_COMPLETE) {
            employee.finishWorkstationOperation();
        }
        if (employee.workstationOperationState() != EmployeeWorkstationOperationState.IDLE) {
            return priorRequestResult(employee);
        }

        employee.beginWorkstationOperation(
                reservationKey,
                value.workstationIdentity(),
                workstationPos,
                value.state().serializedName(),
                BEEF_RECIPE_ID
        );
        return requestOperation(level, employee, value);
    }

    public void tick(EmployeeEntity employee) {
        Objects.requireNonNull(employee, "employee");
        if (!(employee.level() instanceof ServerLevel level)) {
            return;
        }
        EmployeeId employeeId;
        try {
            employeeId = new EmployeeId(employee.employeeIdValue());
        } catch (IllegalArgumentException exception) {
            return;
        }

        Optional<WorkstationReservationRecord> reservation = WorkstationReservationService.INSTANCE
                .managerFor(level.getServer())
                .findByEmployee(employeeId.value());
        if (reservation.isEmpty()) {
            handleMissingReservation(level, employee);
            return;
        }

        WorkstationReservationRecord value = reservation.orElseThrow();
        String reservationKey = reservationKey(value);
        if (!employee.workstationOperationReservationKey().isBlank()
                && !employee.workstationOperationReservationKey().equals(reservationKey)) {
            if (employee.workstationOperationState().active()) {
                employee.markWorkstationOperationFailure("replaced", "reservation_lost");
                return;
            }
            employee.resetWorkstationOperation();
        }
        employee.refreshWorkstationOperationReservation(value.state().serializedName());

        if (!GRINDER_TYPE.equals(value.workstationType())) {
            return;
        }
        if (employee.workstationOperationState() == EmployeeWorkstationOperationState.OPERATION_COMPLETE) {
            if (level.getGameTime() - employee.workstationOperationStateTick() >= COMPLETE_VISIBILITY_TICKS) {
                employee.finishWorkstationOperation();
            }
            return;
        }
        if (employee.workstationOperationState() == EmployeeWorkstationOperationState.FAILURE) {
            return;
        }
        if (value.state() != WorkstationReservationState.EMPLOYEE_ARRIVED) {
            if (employee.workstationOperationState().active()) {
                employee.markWorkstationOperationFailure(value.state().serializedName(), "reservation_lost");
            }
            return;
        }
        if (employee.workstationOperationState() == EmployeeWorkstationOperationState.IDLE
                || employee.workstationOperationState() == EmployeeWorkstationOperationState.PREPARING) {
            return;
        }
        if (employee.workstationOperationState() == EmployeeWorkstationOperationState.OPERATING) {
            employee.markWorkstationOperationWaiting(value.state().serializedName());
            return;
        }
        if (employee.workstationOperationState() == EmployeeWorkstationOperationState.WAITING_FOR_COMPLETION) {
            observeOperation(level, employee, value);
        }
    }

    private static RequestResult requestOperation(
            ServerLevel level,
            EmployeeEntity employee,
            WorkstationReservationRecord reservation
    ) {
        BlockPos workstationPos = workstationPos(reservation);
        if (!(level.getBlockEntity(workstationPos) instanceof GrinderBlockEntity grinder) || grinder.isRemoved()) {
            employee.markWorkstationOperationFailure(reservation.state().serializedName(), "workstation_removed");
            return RequestResult.rejected(RequestStatus.RESERVATION_MISSING_OR_INVALID,
                    "reserved Grinder was removed");
        }
        if (!grinder.inventory().output().isEmpty()) {
            employee.markWorkstationOperationFailure(reservation.state().serializedName(), "blocked_output");
            return RequestResult.rejected(RequestStatus.BLOCKED_OUTPUT, "Grinder output is occupied");
        }
        ItemStack input = grinder.inventory().input();
        if (input.isEmpty()) {
            employee.markWorkstationOperationFailure(reservation.state().serializedName(), "missing_beef_trim");
            return RequestResult.rejected(RequestStatus.MISSING_INPUT, "Grinder input contains no Beef Trim");
        }
        var productData = ProductStackAdapter.readProductData(input);
        if (!productData.succeeded() || !productData.orThrow().productTypeId().equals(BEEF_TRIM_PRODUCT_ID)) {
            employee.markWorkstationOperationFailure(reservation.state().serializedName(), "invalid_recipe");
            return RequestResult.rejected(RequestStatus.INVALID_RECIPE,
                    "Grinder input is not valid Beef Trim for butchercraft:grind_beef");
        }
        if (grinder.workstationState() != WorkstationState.IDLE
                && grinder.workstationState() != WorkstationState.READY) {
            String failure = failureForOccupiedState(grinder);
            employee.markWorkstationOperationFailure(
                    reservation.state().serializedName(),
                    failure
            );
            return requestResultForFailure(failure, "Grinder is already processing or awaiting output clearance");
        }

        WorkstationProductionRequestResult result = grinder.requestEmployeeProcessing(
                new WorkstationTickContext(level, workstationPos)
        );
        if (!result.accepted()) {
            String failure = employeeFailure(result.failure().orElseThrow());
            employee.markWorkstationOperationFailure(
                    reservation.state().serializedName(),
                    failure
            );
            return requestResultForFailure(failure, result.failure().orElseThrow().developerExplanation());
        }
        WorkstationProductionSnapshot snapshot = result.snapshot();
        if (snapshot.selectedOperationId().isEmpty()
                || !snapshot.selectedOperationId().orElseThrow().equals(BuiltInDefinitionIds.GRIND_BEEF)) {
            employee.markWorkstationOperationFailure(reservation.state().serializedName(), "invalid_recipe");
            return RequestResult.rejected(RequestStatus.INVALID_RECIPE,
                    "Grinder selected an operation other than butchercraft:grind_beef");
        }
        if (snapshot.activeExecutionOperationId().isEmpty()) {
            employee.markWorkstationOperationFailure(reservation.state().serializedName(), "execution_rejected");
            return RequestResult.rejected(RequestStatus.EXECUTION_REJECTED,
                    "Grinder did not publish an active Execution operation");
        }
        employee.markWorkstationOperationOperating(
                snapshot.activeExecutionOperationId().orElseThrow().value(),
                reservation.state().serializedName()
        );
        return RequestResult.accepted(snapshot.activeExecutionOperationId().orElseThrow().value());
    }

    private static void observeOperation(
            ServerLevel level,
            EmployeeEntity employee,
            WorkstationReservationRecord reservation
    ) {
        BlockPos workstationPos = workstationPos(reservation);
        if (!(level.getBlockEntity(workstationPos) instanceof GrinderBlockEntity grinder) || grinder.isRemoved()) {
            employee.markWorkstationOperationFailure(reservation.state().serializedName(), "workstation_removed");
            return;
        }
        WorkstationProductionSnapshot workstation = grinder.productionSnapshot();
        if ((workstation.state() == WorkstationState.BLOCKED || workstation.state() == WorkstationState.ERROR)
                && workstation.lastFailure().isPresent()) {
            employee.markWorkstationOperationFailure(
                    reservation.state().serializedName(),
                    employeeFailure(workstation.lastFailure().orElseThrow())
            );
            return;
        }

        ExecutionOperationId operationId;
        try {
            operationId = ExecutionOperationId.of(employee.workstationOperationDiagnostics().executionId());
        } catch (IllegalArgumentException exception) {
            employee.markWorkstationOperationFailure(reservation.state().serializedName(), "execution_failed");
            return;
        }
        if (workstation.activeExecutionOperationId().isEmpty()
                || !workstation.activeExecutionOperationId().orElseThrow().equals(operationId)) {
            employee.markWorkstationOperationFailure(reservation.state().serializedName(), "execution_failed");
            return;
        }
        Optional<ExecutionOperationSnapshot> operation = ExecutionService.INSTANCE
                .managerFor(level.getServer())
                .find(operationId);
        if (operation.isEmpty()) {
            employee.markWorkstationOperationFailure(reservation.state().serializedName(), "execution_failed");
            return;
        }
        ExecutionOperationSnapshot execution = operation.orElseThrow();
        if (execution.status() == ExecutionStatus.SUCCEEDED) {
            if (workstation.ownerResultEvidence().isPresent() && execution.resultEvidence().isPresent()) {
                employee.markWorkstationOperationComplete(reservation.state().serializedName());
            } else {
                employee.markWorkstationOperationFailure(
                        reservation.state().serializedName(),
                        "execution_result_missing"
                );
            }
            return;
        }
        executionTerminalFailure(execution.status()).ifPresent(failure ->
                employee.markWorkstationOperationFailure(reservation.state().serializedName(), failure));
    }

    private static void handleMissingReservation(ServerLevel level, EmployeeEntity employee) {
        if (employee.workstationOperationState().active()) {
            boolean workstationRemoved = employee.workstationOperationPosition()
                    .map(position -> !(level.getBlockEntity(position) instanceof GrinderBlockEntity grinder)
                            || grinder.isRemoved())
                    .orElse(false);
            employee.markWorkstationOperationFailure(
                    "missing",
                    workstationRemoved ? "workstation_removed" : "reservation_lost"
            );
        }
    }

    private static String failureForOccupiedState(GrinderBlockEntity grinder) {
        if (grinder.workstationState() == WorkstationState.BLOCKED && grinder.lastFailure().isPresent()) {
            return employeeFailure(grinder.lastFailure().orElseThrow());
        }
        if (grinder.workstationState() == WorkstationState.COMPLETE || !grinder.inventory().output().isEmpty()) {
            return "blocked_output";
        }
        return "occupied_workstation";
    }

    private static String employeeFailure(WorkstationFailure failure) {
        return switch (failure.code()) {
            case NO_INPUT -> "missing_beef_trim";
            case OUTPUT_OCCUPIED, OUTPUT_INCOMPATIBLE -> "blocked_output";
            case NO_COMPATIBLE_OPERATION, MULTIPLE_COMPATIBLE_OPERATIONS, OPERATION_PROFILE_MISMATCH,
                    OPERATION_CAPABILITY_MISMATCH, INPUT_NOT_PRODUCT, MISSING_PRODUCT_DATA,
                    UNKNOWN_PRODUCT_DEFINITION, PRODUCT_DATA_MISMATCH, INPUT_QUANTITY_TOO_LOW,
                    INPUT_QUANTITY_TOO_HIGH, PROCESSING_VALIDATION_REJECTED -> "invalid_recipe";
            case TRANSACTION_ALREADY_ACTIVE -> "occupied_workstation";
            case EXECUTION_AUTHORIZATION_REJECTED, EXECUTION_DISPATCH_REJECTED -> "execution_rejected";
            case EXECUTION_OUTCOME_UNKNOWN -> "unknown_outcome";
            case EXECUTION_RESULT_REJECTED -> "execution_failed";
            default -> "workstation_" + failure.code().reasonCode();
        };
    }

    private static RequestResult requestResultForFailure(String failure, String detail) {
        return switch (failure) {
            case "missing_beef_trim" -> RequestResult.rejected(RequestStatus.MISSING_INPUT, detail);
            case "invalid_recipe" -> RequestResult.rejected(RequestStatus.INVALID_RECIPE, detail);
            case "blocked_output" -> RequestResult.rejected(RequestStatus.BLOCKED_OUTPUT, detail);
            case "occupied_workstation" -> RequestResult.rejected(RequestStatus.ALREADY_REQUESTED, detail);
            case "execution_rejected" -> RequestResult.rejected(RequestStatus.EXECUTION_REJECTED, detail);
            case "unknown_outcome" -> RequestResult.rejected(RequestStatus.UNKNOWN_OUTCOME, detail);
            default -> RequestResult.rejected(RequestStatus.RECOVERY_REQUIRED, detail);
        };
    }

    private static RequestResult priorRequestResult(EmployeeEntity employee) {
        String failure = employee.workstationOperationDiagnostics().failure();
        if (failure.equals("unknown_outcome")) {
            return RequestResult.rejected(RequestStatus.UNKNOWN_OUTCOME,
                    "previous Execution outcome is unknown; recovery is required");
        }
        if (failure.equals("execution_failed") || failure.equals("execution_result_missing")) {
            return RequestResult.rejected(RequestStatus.RECOVERY_REQUIRED,
                    "previous Execution did not publish a complete authoritative result");
        }
        return RequestResult.rejected(RequestStatus.ALREADY_REQUESTED,
                "operation was already requested for this reservation");
    }

    static Optional<String> executionTerminalFailure(ExecutionStatus status) {
        return switch (Objects.requireNonNull(status, "status")) {
            case UNKNOWN_OUTCOME -> Optional.of("unknown_outcome");
            case REJECTED -> Optional.of("execution_rejected");
            case FAILED, CANCELLED_BEFORE_START -> Optional.of("execution_failed");
            default -> Optional.empty();
        };
    }

    private static BlockPos workstationPos(WorkstationReservationRecord reservation) {
        return new BlockPos(
                reservation.workstationX(),
                reservation.workstationY(),
                reservation.workstationZ()
        );
    }

    private static String reservationKey(WorkstationReservationRecord reservation) {
        return reservation.employeeIdentity() + "|" + reservation.workstationIdentity() + "|" + reservation.createdTick();
    }

    public enum RequestStatus {
        ACCEPTED,
        EMPLOYEE_NOT_PRESENT,
        EMPLOYEE_NOT_AT_WORKSTATION,
        RESERVATION_MISSING_OR_INVALID,
        UNSUPPORTED_WORKSTATION,
        MISSING_INPUT,
        INVALID_RECIPE,
        BLOCKED_OUTPUT,
        ALREADY_REQUESTED,
        EXECUTION_REJECTED,
        UNKNOWN_OUTCOME,
        RECOVERY_REQUIRED
    }

    public record RequestResult(RequestStatus status, String detail) {
        public RequestResult {
            status = Objects.requireNonNull(status, "status");
            detail = Objects.requireNonNull(detail, "detail").strip();
            if (detail.isEmpty()) {
                throw new IllegalArgumentException("Employee operation request detail must not be blank");
            }
        }

        public static RequestResult accepted(String executionIdentity) {
            return new RequestResult(RequestStatus.ACCEPTED,
                    "Execution " + Objects.requireNonNull(executionIdentity, "executionIdentity"));
        }

        public static RequestResult rejected(RequestStatus status, String detail) {
            if (status == RequestStatus.ACCEPTED) {
                throw new IllegalArgumentException("Rejected employee operation result cannot be accepted");
            }
            return new RequestResult(status, detail);
        }

        public boolean accepted() {
            return status == RequestStatus.ACCEPTED;
        }
    }
}
