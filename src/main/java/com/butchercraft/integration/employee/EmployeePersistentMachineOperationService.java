package com.butchercraft.integration.employee;

import com.butchercraft.entity.employee.EmployeeEntity;
import com.butchercraft.integration.machine.PoweredMachineRunControlCode;
import com.butchercraft.integration.machine.PoweredMachineRunControlResult;
import com.butchercraft.integration.machine.grinder.GrinderContinuousRunService;
import com.butchercraft.integration.machine.grinder.GrinderRunControlResult;
import com.butchercraft.integration.machine.pattyformer.PattyFormerContinuousRunService;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.pattyformer.PattyFormerBlockEntity;
import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.workstation.block.AbstractProcessingWorkstationBlockEntity;
import com.butchercraft.workstation.endpoint.WorkstationInstanceLifecycle;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRecord;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReferenceResult;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointService;
import com.butchercraft.workstation.operation.MachineOperatingPolicy;
import com.butchercraft.workstation.operation.MachineOperatingState;
import com.butchercraft.workstation.reservation.WorkstationReservationRecord;
import com.butchercraft.workstation.reservation.WorkstationReservationResult;
import com.butchercraft.workstation.reservation.WorkstationReservationRole;
import com.butchercraft.workstation.reservation.WorkstationReservationState;
import com.butchercraft.world.EmployeeMachineOperationAssignmentService;
import com.butchercraft.world.EmployeeMaterialHandlingService;
import com.butchercraft.world.EmployeeService;
import com.butchercraft.world.ExecutionMachineRunService;
import com.butchercraft.world.WorkstationReservationService;
import com.butchercraft.world.WorldIdentityService;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.checkpoint.StartupMutationGateService;
import com.butchercraft.world.execution.MachineRunChildState;
import com.butchercraft.world.execution.MachineRunIdentity;
import com.butchercraft.world.execution.MachineRunLifecycle;
import com.butchercraft.world.execution.MachineRunRecord;
import com.butchercraft.world.execution.MachineRunRegistry;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import com.butchercraft.world.materialhandling.MaterialTransferLifecycle;
import com.butchercraft.world.materialhandling.MaterialTransferView;
import com.butchercraft.world.materialhandling.runtime.MaterialHandlingService;
import com.butchercraft.world.workforce.employee.EmployeeEntityLink;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.employee.EmployeePresenceObservation;
import com.butchercraft.world.workforce.employee.EmployeePresenceState;
import com.butchercraft.world.workforce.employee.EmployeeRecord;
import com.butchercraft.world.workforce.employee.EmployeeStatus;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationAssignment;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationAssignmentManager;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationAssignmentState;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationFailure;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationFailureCode;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Workforce-owned IM-032B assignment coordination over canonical Machine Run controls. */
public final class EmployeePersistentMachineOperationService {
    public static final EmployeePersistentMachineOperationService INSTANCE =
            new EmployeePersistentMachineOperationService();

    private static final String GRINDER = "grinder";
    private static final String PATTY_FORMER = "patty_former";
    private static final String POLICY = MachineOperatingPolicy.poweredContinuousExplicitStop().policyIdentity();

    private EmployeePersistentMachineOperationService() {
    }

    public AssignmentResult request(ServerLevel level, EmployeeId employeeId, BlockPos workstationPos, int target) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(workstationPos, "workstationPos");
        if (target <= 0) return AssignmentResult.rejected(AssignmentStatus.INVALID_TARGET,
                "target quantity must be positive");
        if (!mutationPermitted(level.getServer())) return AssignmentResult.rejected(
                AssignmentStatus.RECOVERY_REQUIRED, "startup recovery authority blocks Workforce mutation");

        EmployeeRecord employee = EmployeeService.INSTANCE.managerFor(level.getServer()).find(employeeId).orElse(null);
        EmployeePresenceObservation presence = EmployeeService.INSTANCE.observe(level.getServer(), employeeId)
                .value().orElse(null);
        if (employee == null || presence == null || employee.entityLink().isEmpty()) {
            return AssignmentResult.rejected(AssignmentStatus.EMPLOYEE_UNAVAILABLE,
                    "employee is not present in authoritative Workforce state");
        }
        if (employee.status() != EmployeeStatus.ACTIVE
                || presence.presenceState() != EmployeePresenceState.PRESENT
                || presence.assignedDepartmentId().isEmpty()) {
            return AssignmentResult.rejected(AssignmentStatus.EMPLOYEE_UNAVAILABLE, presence.reason());
        }
        if (!presence.plantOpen()) {
            return AssignmentResult.rejected(AssignmentStatus.PLANT_CLOSED, presence.reason());
        }
        if (!employee.entityLink().orElseThrow().dimensionIdentity().equals(EmployeeService.dimensionIdentity(level))) {
            return AssignmentResult.rejected(AssignmentStatus.EMPLOYEE_UNAVAILABLE,
                    "employee and requested workstation are in different dimensions");
        }
        Optional<EmployeeMaterialHandlingAssignment> handling = EmployeeMaterialHandlingService.INSTANCE
                .managerFor(level.getServer()).activeFor(employeeId);
        if (handling.isPresent()) {
            return AssignmentResult.rejected(AssignmentStatus.ASSIGNMENT_CONFLICT,
                    "employee already has an active Material Handling assignment");
        }
        Optional<EmployeeEntity> entity = entity(level, employee);
        if (entity.filter(value -> value.workstationOperationState().active()).isPresent()) {
            return AssignmentResult.rejected(AssignmentStatus.ASSIGNMENT_CONFLICT,
                    "employee is finishing a historical one-cycle operation");
        }

        MachineProfile profile = resolveProfile(level, workstationPos).orElse(null);
        if (profile == null) {
            return AssignmentResult.rejected(AssignmentStatus.UNSUPPORTED_MACHINE,
                    "target must be a loaded Grinder or Patty Former");
        }
        if (!profile.input().isEmpty() && !inputMatches(profile)) {
            return AssignmentResult.rejected(AssignmentStatus.INVALID_INPUT,
                    profile.displayName() + " input does not match " + profile.inputMaterialIdentity());
        }
        WorkstationEndpointReferenceResult endpointResult = WorkstationEndpointService.INSTANCE
                .referenceFor(level, workstationPos);
        if (!endpointResult.succeeded()) {
            return AssignmentResult.rejected(AssignmentStatus.WORKSTATION_UNAVAILABLE, endpointResult.detail());
        }
        WorkstationEndpointReference endpoint = endpointResult.reference().orElseThrow();
        EmployeeMachineOperationAssignmentManager manager = assignments(level.getServer());
        EmployeeMachineOperationAssignmentManager.CreateResult created = manager.createOrObserve(
                WorldIdentityRootIdentities.from(WorldIdentityService.INSTANCE.getOrCreate(level.getServer())),
                employeeId, endpoint, profile.machineType(), POLICY, profile.operationIdentity(),
                profile.inputMaterialIdentity(), 1, target, level.getGameTime());
        if (created.status() == EmployeeMachineOperationAssignmentManager.CreateStatus.CONFLICT) {
            return AssignmentResult.rejected(AssignmentStatus.ASSIGNMENT_CONFLICT,
                    "employee already has active assignment " + created.assignment().assignmentId().value());
        }
        if (created.status() == EmployeeMachineOperationAssignmentManager.CreateStatus.CREATED) {
            persist(level.getServer());
        }
        EmployeeMachineOperationAssignment assignment = created.assignment();
        acquireOperator(level, assignment);
        assignment = assignments(level.getServer()).find(assignment.assignmentId()).orElseThrow();
        return new AssignmentResult(created.status() == EmployeeMachineOperationAssignmentManager.CreateStatus.CREATED
                ? AssignmentStatus.ACCEPTED : AssignmentStatus.EXISTING_ASSIGNMENT,
                assignment.assignmentId().value() + " | " + assignment.state().name().toLowerCase());
    }

    public AssignmentResult cancel(ServerLevel level, EmployeeId employeeId, String reason) {
        if (!mutationPermitted(level.getServer())) return AssignmentResult.rejected(
                AssignmentStatus.RECOVERY_REQUIRED, "startup recovery authority blocks Workforce mutation");
        EmployeeMachineOperationAssignmentManager manager = assignments(level.getServer());
        EmployeeMachineOperationAssignment assignment = manager.activeFor(employeeId).orElse(null);
        if (assignment == null) {
            EmployeeMachineOperationAssignment latest = manager.latestFor(employeeId).orElse(null);
            if (latest != null && latest.state() == EmployeeMachineOperationAssignmentState.CANCELLED) {
                return new AssignmentResult(AssignmentStatus.CANCELLED,
                        reason + " | existing terminal result " + latest.assignmentId().value());
            }
            return AssignmentResult.rejected(
                    AssignmentStatus.ASSIGNMENT_NOT_FOUND, "employee has no active machine-operation assignment");
        }
        if (assignment.state() == EmployeeMachineOperationAssignmentState.RECOVERY_REQUIRED) {
            return cancelRecoveryRequired(level, assignment, reason);
        }
        if (assignment.state() != EmployeeMachineOperationAssignmentState.CANCELLATION_REQUESTED) {
            assignment = publish(level.getServer(), assignment, EmployeeMachineOperationAssignmentState.CANCELLATION_REQUESTED,
                    assignment.completedQuantity(), assignment.runIdentity(), assignment.reservationId(),
                    assignment.pendingSupplyTransferIdentity(), assignment.observedRunRevision(),
                    assignment.observedChildSequence(), Optional.empty(), level.getGameTime());
        }
        tick(level, assignment);
        EmployeeMachineOperationAssignment current = assignments(level.getServer()).find(assignment.assignmentId())
                .orElseThrow();
        return new AssignmentResult(switch (current.state()) {
            case CANCELLED -> AssignmentStatus.CANCELLED;
            case RECOVERY_REQUIRED -> AssignmentStatus.RECOVERY_REQUIRED;
            default -> AssignmentStatus.CANCELLATION_REQUESTED;
        },
                reason + " | " + current.assignmentId().value());
    }

    public void advance(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!mutationPermitted(server)) return;
        for (EmployeeMachineOperationAssignment assignment : assignments(server).assignments()) {
            if (!assignment.active()) continue;
            level(server, assignment.workstation().endpointKey().dimensionIdentity())
                    .ifPresent(value -> tick(value, assignment));
        }
    }

    public boolean mayAdmitChild(MinecraftServer server, MachineRunRecord run) {
        if (!run.startEvidence().sourceOwner().equals(EmployeeMachineOperationAssignmentService.RUN_SOURCE_OWNER)) {
            return true;
        }
        EmployeeMachineOperationAssignment assignment = assignments(server).assignments().stream()
                .filter(value -> EmployeeMachineOperationAssignmentService.startRequestIdentity(
                        value.assignmentId().value()).equals(run.startEvidence().sourceRequestIdentity()))
                .findFirst().orElse(null);
        if (assignment == null || !assignment.active()
                || assignment.state() == EmployeeMachineOperationAssignmentState.CANCELLATION_REQUESTED
                || assignment.state() == EmployeeMachineOperationAssignmentState.STOP_REQUESTED
                || assignment.state() == EmployeeMachineOperationAssignmentState.WAITING_FOR_SAFE_STOP
                || assignment.state() == EmployeeMachineOperationAssignmentState.RESTART_REQUIRED
                || assignment.state() == EmployeeMachineOperationAssignmentState.RECOVERY_REQUIRED) return false;
        if (!assignment.workstation().instanceId().value().equals(run.workstationInstanceIdentity())) return false;
        Optional<WorkstationReservationRecord> reservation = operatorReservation(server, assignment);
        if (reservation.filter(value -> value.state() == WorkstationReservationState.EMPLOYEE_ARRIVED).isEmpty()) {
            return false;
        }
        Optional<ServerLevel> level = level(server, assignment.workstation().endpointKey().dimensionIdentity());
        if (level.isEmpty()) return false;
        Optional<MachineProfile> profile = resolveProfile(level.orElseThrow(), position(assignment));
        if (profile.filter(value -> value.matches(assignment) && inputMatches(value)).isEmpty()) return false;
        EmployeeRecord employee = EmployeeService.INSTANCE.managerFor(server).find(assignment.employeeId()).orElse(null);
        Optional<EmployeeEntity> entity = employee == null ? Optional.empty() : entity(level.orElseThrow(), employee);
        if (entity.filter(value -> WorkstationReservationService.INSTANCE.isWithinOperatingTolerance(
                level.orElseThrow(), reservation.orElseThrow(), value.position())).isEmpty()) return false;
        return committedQuantity(run, assignment) < assignment.targetQuantity();
    }

    public OperationDiagnostics diagnostics(MinecraftServer server, EmployeeId employeeId) {
        EmployeeMachineOperationAssignment assignment = assignments(server).latestFor(employeeId).orElse(null);
        if (assignment == null) return OperationDiagnostics.none();
        Optional<MachineRunRecord> run = assignment.runIdentity().flatMap(value -> {
            try {
                return ExecutionMachineRunService.INSTANCE.find(server, new MachineRunIdentity(value));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        });
        String machineState = level(server, assignment.workstation().endpointKey().dimensionIdentity())
                .flatMap(value -> resolveProfile(value, position(assignment)))
                .map(EmployeePersistentMachineOperationService::operatingState)
                .map(MachineOperatingState::serializedName)
                .orElse("unloaded");
        return new OperationDiagnostics(
                assignment.assignmentId().value(), assignment.state().name().toLowerCase(), assignment.machineType(),
                assignment.workstation().instanceId().value(), assignment.targetQuantity(),
                assignment.completedQuantity(), assignment.remainingQuantity(),
                assignment.reservationId().map(value -> value.value()).orElse("none"),
                assignment.runIdentity().orElse("none"),
                run.map(value -> value.lifecycle().serializedName()).orElse("none"),
                machineState,
                run.flatMap(MachineRunRecord::currentChild).map(value -> value.state().serializedName()).orElse("none"),
                assignment.pendingSupplyTransferIdentity().orElse("none"),
                assignment.failure().map(value -> value.code().serializedName() + ": " + value.detail()).orElse("none"));
    }

    public boolean handleEmployeeRemoval(EmployeeEntity employee, boolean permanent) {
        if (!(employee.level() instanceof ServerLevel level)) return false;
        EmployeeId employeeId;
        try {
            employeeId = new EmployeeId(employee.employeeIdValue());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (!EmployeeMachineOperationAssignmentService.INSTANCE.hasActiveAssignment(level.getServer(), employeeId)) {
            return false;
        }
        if (permanent) {
            EmployeeMachineOperationAssignmentService.INSTANCE.requestCancellation(
                    level.getServer(), employeeId, "assigned employee was permanently removed");
        }
        return true;
    }

    private void tick(ServerLevel level, EmployeeMachineOperationAssignment supplied) {
        EmployeeMachineOperationAssignment assignment = assignments(level.getServer()).find(supplied.assignmentId())
                .orElse(null);
        if (assignment == null || !assignment.active()) return;
        if (assignment.state() == EmployeeMachineOperationAssignmentState.RECOVERY_REQUIRED) return;
        Optional<MachineProfile> resolvedProfile = resolveProfile(level, position(assignment));
        if (resolvedProfile.isEmpty()) {
            if (!level.hasChunkAt(position(assignment))) return;
            recovery(level, assignment, EmployeeMachineOperationFailureCode.WORKSTATION_REPLACED,
                    "exact assigned Workstation Instance is unavailable or replaced");
            return;
        }
        MachineProfile profile = resolvedProfile.orElseThrow();
        if (!profile.matches(assignment)) {
            recovery(level, assignment, EmployeeMachineOperationFailureCode.WORKSTATION_REPLACED,
                    "loaded workstation does not match the assigned instance/type/policy");
            return;
        }
        WorkstationEndpointReferenceResult endpoint = WorkstationEndpointService.INSTANCE
                .referenceFor(level, position(assignment));
        if (!endpoint.succeeded() || !endpoint.reference().orElseThrow().equals(assignment.workstation())) {
            recovery(level, assignment, EmployeeMachineOperationFailureCode.WORKSTATION_REPLACED,
                    "Workstation endpoint identity changed");
            return;
        }
        EmployeeRecord availability = EmployeeService.INSTANCE.managerFor(level.getServer())
                .find(assignment.employeeId()).orElse(null);
        EmployeePresenceObservation presence = EmployeeService.INSTANCE.observe(
                level.getServer(), assignment.employeeId()).value().orElse(null);
        if (availability == null || availability.status() != EmployeeStatus.ACTIVE || presence == null
                || presence.presenceState() != EmployeePresenceState.PRESENT
                || presence.assignedDepartmentId().isEmpty() || !presence.plantOpen()) {
            if (assignment.state() != EmployeeMachineOperationAssignmentState.CANCELLATION_REQUESTED) {
                assignment = publish(level.getServer(), assignment,
                        EmployeeMachineOperationAssignmentState.CANCELLATION_REQUESTED,
                        assignment.completedQuantity(), assignment.runIdentity(), assignment.reservationId(),
                        assignment.pendingSupplyTransferIdentity(), assignment.observedRunRevision(),
                        assignment.observedChildSequence(), Optional.empty(), level.getGameTime());
            }
            cancelAtBoundary(level, assignment, profile, runFor(level.getServer(), assignment));
            return;
        }
        Optional<String> pendingSupply = pendingSupply(level.getServer(), assignment);
        if (!assignment.pendingSupplyTransferIdentity().equals(pendingSupply)) {
            assignment = publish(level.getServer(), assignment, assignment.state(), assignment.completedQuantity(),
                    assignment.runIdentity(), assignment.reservationId(), pendingSupply,
                    assignment.observedRunRevision(), assignment.observedChildSequence(), assignment.failure(),
                    level.getGameTime());
        }

        Optional<MachineRunRecord> recoveredRun = runFor(level.getServer(), assignment);
        if (assignment.runIdentity().isEmpty() && recoveredRun.isPresent()) {
            MachineRunRecord run = recoveredRun.orElseThrow();
            assignment = publish(level.getServer(), assignment, EmployeeMachineOperationAssignmentState.START_REQUESTED,
                    completedQuantity(run, assignment), Optional.of(run.runIdentity().value()),
                    assignment.reservationId(), pendingSupply, run.revision(), observedChildSequence(run),
                    Optional.empty(), level.getGameTime());
        }

        if (assignment.state() == EmployeeMachineOperationAssignmentState.CANCELLATION_REQUESTED) {
            cancelAtBoundary(level, assignment, profile, recoveredRun);
            return;
        }

        Optional<WorkstationReservationRecord> reservation = operatorReservation(level.getServer(), assignment);
        if (reservation.isEmpty()) {
            repairOrAcquireOperator(level, assignment);
            return;
        }
        WorkstationReservationRecord operator = reservation.orElseThrow();
        if (assignment.reservationId().filter(operator.reservationId()::equals).isEmpty()) {
            assignment = publish(level.getServer(), assignment, EmployeeMachineOperationAssignmentState.NAVIGATING,
                    assignment.completedQuantity(), assignment.runIdentity(), Optional.of(operator.reservationId()),
                    pendingSupply, assignment.observedRunRevision(), assignment.observedChildSequence(),
                    Optional.empty(), level.getGameTime());
        }
        EmployeeRecord employee = EmployeeService.INSTANCE.managerFor(level.getServer())
                .find(assignment.employeeId()).orElse(null);
        if (employee == null || employee.status() != EmployeeStatus.ACTIVE || employee.entityLink().isEmpty()) return;
        Optional<EmployeeEntity> entity = entity(level, employee);
        if (entity.isEmpty()) return;
        if (operator.state() != WorkstationReservationState.EMPLOYEE_ARRIVED
                || !WorkstationReservationService.INSTANCE.isWithinOperatingTolerance(
                level, operator, entity.orElseThrow().position())) {
            publish(level.getServer(), assignment, EmployeeMachineOperationAssignmentState.NAVIGATING,
                    assignment.completedQuantity(), assignment.runIdentity(), Optional.of(operator.reservationId()),
                    pendingSupply, assignment.observedRunRevision(), assignment.observedChildSequence(),
                    Optional.empty(), level.getGameTime());
            return;
        }

        Optional<MachineRunRecord> run = runFor(level.getServer(), assignment);
        if (assignment.runIdentity().isPresent() && run.isEmpty()) {
            recovery(level, assignment, EmployeeMachineOperationFailureCode.RUN_MISSING,
                    "persisted assignment references a missing Machine Run");
            return;
        }
        if (run.isEmpty()) {
            if (!inputMatches(profile) && pendingSupply.isEmpty()) {
                publish(level.getServer(), assignment, EmployeeMachineOperationAssignmentState.WAITING_FOR_INPUT,
                        assignment.completedQuantity(), Optional.empty(), Optional.of(operator.reservationId()),
                        Optional.empty(), assignment.observedRunRevision(), assignment.observedChildSequence(),
                        Optional.empty(), level.getGameTime());
                return;
            }
            assignment = publish(level.getServer(), assignment, EmployeeMachineOperationAssignmentState.READY_TO_START,
                    assignment.completedQuantity(), Optional.empty(), Optional.of(operator.reservationId()),
                    pendingSupply, assignment.observedRunRevision(), assignment.observedChildSequence(),
                    Optional.empty(), level.getGameTime());
            MachineControlResult started = start(level, profile, assignment, operator);
            if (!started.accepted() || started.runIdentity().isEmpty()) {
                if (started.busy()) {
                    publish(level.getServer(), assignment,
                            EmployeeMachineOperationAssignmentState.WAITING_FOR_MACHINE,
                            assignment.completedQuantity(), Optional.empty(), Optional.of(operator.reservationId()),
                            pendingSupply, assignment.observedRunRevision(), assignment.observedChildSequence(),
                            Optional.empty(), level.getGameTime());
                } else {
                    recovery(level, assignment, EmployeeMachineOperationFailureCode.RECOVERY_REQUIRED,
                            started.detail());
                }
                return;
            }
            MachineRunRecord startedRun = ExecutionMachineRunService.INSTANCE
                    .find(level.getServer(), started.runIdentity().orElseThrow()).orElse(null);
            if (startedRun == null) {
                recovery(level, assignment, EmployeeMachineOperationFailureCode.RUN_MISSING,
                        "accepted START did not publish an exact Machine Run");
                return;
            }
            assignment = publish(level.getServer(), assignment,
                    EmployeeMachineOperationAssignmentState.START_REQUESTED,
                    completedQuantity(startedRun, assignment), Optional.of(startedRun.runIdentity().value()),
                    Optional.of(operator.reservationId()), pendingSupply, startedRun.revision(),
                    observedChildSequence(startedRun), Optional.empty(), level.getGameTime());
            observeRun(level, assignment, profile, startedRun, pendingSupply);
            return;
        }
        observeRun(level, assignment, profile, run.orElseThrow(), pendingSupply);
    }

    private void observeRun(
            ServerLevel level,
            EmployeeMachineOperationAssignment assignment,
            MachineProfile profile,
            MachineRunRecord run,
            Optional<String> pendingSupply
    ) {
        if (!run.workstationInstanceIdentity().equals(assignment.workstation().instanceId().value())
                || !run.startEvidence().sourceOwner().equals(EmployeeMachineOperationAssignmentService.RUN_SOURCE_OWNER)
                || !run.startEvidence().sourceRequestIdentity().equals(
                EmployeeMachineOperationAssignmentService.startRequestIdentity(assignment.assignmentId().value()))) {
            recovery(level, assignment, EmployeeMachineOperationFailureCode.RUN_RESULT_CONFLICT,
                    "assignment Run reference does not bind its exact START evidence");
            return;
        }
        int completed = completedQuantity(run, assignment);
        if (completed > assignment.targetQuantity()) {
            recovery(level, assignment, EmployeeMachineOperationFailureCode.RUN_RESULT_CONFLICT,
                    "canonical Run child evidence exceeds the finite assignment target");
            return;
        }
        long childSequence = observedChildSequence(run);
        if (run.lifecycle() == MachineRunLifecycle.RECOVERY_REQUIRED) {
            recovery(level, assignment, EmployeeMachineOperationFailureCode.RECOVERY_REQUIRED,
                    run.recoveryDetail().orElse("Machine Run requires recovery"));
            return;
        }
        if (run.lifecycle() == MachineRunLifecycle.SUSPENDED_RESTART_REQUIRED) {
            publish(level.getServer(), assignment, EmployeeMachineOperationAssignmentState.RESTART_REQUIRED,
                    completed, Optional.of(run.runIdentity().value()), assignment.reservationId(), pendingSupply,
                    run.revision(), childSequence, Optional.empty(), level.getGameTime());
            return;
        }
        if (run.lifecycle() == MachineRunLifecycle.FAILED) {
            EmployeeMachineOperationAssignment updated = publish(level.getServer(), assignment,
                    EmployeeMachineOperationAssignmentState.FAILED, completed,
                    Optional.of(run.runIdentity().value()), assignment.reservationId(), pendingSupply,
                    run.revision(), childSequence, Optional.of(new EmployeeMachineOperationFailure(
                            EmployeeMachineOperationFailureCode.RUN_RESULT_CONFLICT,
                            run.recoveryDetail().orElse("Machine Run failed before assignment completion"))),
                    level.getGameTime());
            releaseExactReservation(level.getServer(), updated, "machine operation Run failed");
            return;
        }
        if (run.lifecycle() == MachineRunLifecycle.STOPPED) {
            finishTerminalRun(level, assignment, run, completed, pendingSupply, childSequence);
            return;
        }
        if (run.lifecycle() == MachineRunLifecycle.STOP_REQUESTED) {
            EmployeeMachineOperationAssignmentState stoppingState =
                    assignment.state() == EmployeeMachineOperationAssignmentState.CANCELLATION_REQUESTED
                            ? EmployeeMachineOperationAssignmentState.CANCELLATION_REQUESTED
                            : EmployeeMachineOperationAssignmentState.WAITING_FOR_SAFE_STOP;
            publish(level.getServer(), assignment, stoppingState,
                    completed, Optional.of(run.runIdentity().value()), assignment.reservationId(), pendingSupply,
                    run.revision(), childSequence, Optional.empty(), level.getGameTime());
            return;
        }
        if (completed >= assignment.targetQuantity()) {
            requestStop(level, assignment, profile, run);
            return;
        }
        if (!profile.input().isEmpty() && !inputMatches(profile)) {
            requestStop(level, assignment, profile, run);
            return;
        }
        MachineOperatingState state = operatingState(profile);
        if (state == MachineOperatingState.RESTART_REQUIRED) {
            publish(level.getServer(), assignment, EmployeeMachineOperationAssignmentState.RESTART_REQUIRED,
                    completed, Optional.of(run.runIdentity().value()), assignment.reservationId(), pendingSupply,
                    run.revision(), childSequence, Optional.empty(), level.getGameTime());
        } else if (state == MachineOperatingState.OUTPUT_BLOCKED) {
            publish(level.getServer(), assignment, EmployeeMachineOperationAssignmentState.OUTPUT_BLOCKED,
                    completed, Optional.of(run.runIdentity().value()), assignment.reservationId(), pendingSupply,
                    run.revision(), childSequence, Optional.empty(), level.getGameTime());
        } else if (state == MachineOperatingState.RUNNING_EMPTY && run.currentChild().isEmpty()) {
            if (pendingSupply.isPresent()) {
                publish(level.getServer(), assignment, EmployeeMachineOperationAssignmentState.RUNNING_EMPTY,
                        completed, Optional.of(run.runIdentity().value()), assignment.reservationId(), pendingSupply,
                        run.revision(), childSequence, Optional.empty(), level.getGameTime());
            } else {
                requestStop(level, assignment, profile, run);
            }
        } else {
            publish(level.getServer(), assignment, EmployeeMachineOperationAssignmentState.RUNNING,
                    completed, Optional.of(run.runIdentity().value()), assignment.reservationId(), pendingSupply,
                    run.revision(), childSequence, Optional.empty(), level.getGameTime());
        }
    }

    private void requestStop(
            ServerLevel level,
            EmployeeMachineOperationAssignment assignment,
            MachineProfile profile,
            MachineRunRecord run
    ) {
        MachineControlResult stopped = stop(level, profile, assignment, run);
        if (!stopped.accepted()) {
            recovery(level, assignment, EmployeeMachineOperationFailureCode.RUN_RESULT_CONFLICT, stopped.detail());
            return;
        }
        MachineRunRecord current = ExecutionMachineRunService.INSTANCE.find(level.getServer(), run.runIdentity())
                .orElse(run);
        EmployeeMachineOperationAssignmentState state = current.lifecycle().terminal()
                ? EmployeeMachineOperationAssignmentState.WAITING_FOR_SAFE_STOP
                : EmployeeMachineOperationAssignmentState.STOP_REQUESTED;
        EmployeeMachineOperationAssignment updated = publish(level.getServer(), assignment, state,
                completedQuantity(current, assignment), Optional.of(current.runIdentity().value()),
                assignment.reservationId(), assignment.pendingSupplyTransferIdentity(), current.revision(),
                observedChildSequence(current), Optional.empty(), level.getGameTime());
        if (current.lifecycle().terminal()) {
            finishTerminalRun(level, updated, current, completedQuantity(current, updated),
                    updated.pendingSupplyTransferIdentity(), observedChildSequence(current));
        }
    }

    private void cancelAtBoundary(
            ServerLevel level,
            EmployeeMachineOperationAssignment assignment,
            MachineProfile profile,
            Optional<MachineRunRecord> run
    ) {
        if (run.isEmpty()) {
            releaseExactReservation(level.getServer(), assignment, "machine operation cancelled before START");
            publish(level.getServer(), assignment, EmployeeMachineOperationAssignmentState.CANCELLED,
                    assignment.completedQuantity(), Optional.empty(), assignment.reservationId(),
                    assignment.pendingSupplyTransferIdentity(), assignment.observedRunRevision(),
                    assignment.observedChildSequence(), Optional.empty(), level.getGameTime());
            return;
        }
        MachineRunRecord value = run.orElseThrow();
        if (value.lifecycle().terminal()) {
            finishTerminalRun(level, assignment, value, completedQuantity(value, assignment),
                    assignment.pendingSupplyTransferIdentity(), observedChildSequence(value));
            return;
        }
        if (value.lifecycle() == MachineRunLifecycle.STOP_REQUESTED) {
            publish(level.getServer(), assignment, EmployeeMachineOperationAssignmentState.CANCELLATION_REQUESTED,
                    completedQuantity(value, assignment), Optional.of(value.runIdentity().value()),
                    assignment.reservationId(), assignment.pendingSupplyTransferIdentity(), value.revision(),
                    observedChildSequence(value), Optional.empty(), level.getGameTime());
            return;
        }
        MachineControlResult stopped = stop(level, profile, assignment, value);
        if (!stopped.accepted()) {
            recovery(level, assignment, EmployeeMachineOperationFailureCode.RUN_RESULT_CONFLICT, stopped.detail());
            return;
        }
        MachineRunRecord current = ExecutionMachineRunService.INSTANCE.find(level.getServer(), value.runIdentity())
                .orElse(value);
        publish(level.getServer(), assignment, EmployeeMachineOperationAssignmentState.CANCELLATION_REQUESTED,
                completedQuantity(current, assignment), Optional.of(current.runIdentity().value()),
                assignment.reservationId(), assignment.pendingSupplyTransferIdentity(), current.revision(),
                observedChildSequence(current), Optional.empty(), level.getGameTime());
    }

    private AssignmentResult cancelRecoveryRequired(
            ServerLevel level,
            EmployeeMachineOperationAssignment assignment,
            String reason
    ) {
        if (!assignment.isConsequenceFreeReplacementCancellationCandidate()) {
            return AssignmentResult.rejected(AssignmentStatus.RECOVERY_REQUIRED,
                    "recovery state retains consequential or unresolved authority | "
                            + assignment.assignmentId().value());
        }
        if (!provesRetiredInstance(level.getServer(), assignment)) {
            return AssignmentResult.rejected(AssignmentStatus.RECOVERY_REQUIRED,
                    "exact Workstation retirement without unresolved endpoint effects is not proven | "
                            + assignment.assignmentId().value());
        }
        MachineRunRegistry runs = ExecutionMachineRunService.INSTANCE.snapshot(level.getServer());
        String assignmentIdentity = assignment.assignmentId().value();
        if (runs.startForRequest(EmployeeMachineOperationAssignmentService.RUN_SOURCE_OWNER,
                EmployeeMachineOperationAssignmentService.startRequestIdentity(assignmentIdentity)).isPresent()
                || runs.stopForRequest(EmployeeMachineOperationAssignmentService.RUN_SOURCE_OWNER,
                EmployeeMachineOperationAssignmentService.stopRequestIdentity(assignmentIdentity)).isPresent()
                || runs.activeFor(assignment.workstation().instanceId().value()).isPresent()) {
            return AssignmentResult.rejected(AssignmentStatus.RECOVERY_REQUIRED,
                    "replacement assignment still has canonical Machine Run evidence | "
                            + assignment.assignmentId().value());
        }
        ReservationClearance clearance = clearExactOrphanedReservation(level.getServer(), assignment);
        if (!clearance.safe()) {
            return AssignmentResult.rejected(AssignmentStatus.RECOVERY_REQUIRED,
                    clearance.detail() + " | " + assignment.assignmentId().value());
        }
        EmployeeMachineOperationAssignment cancelled = publish(
                level.getServer(), assignment, EmployeeMachineOperationAssignmentState.CANCELLED,
                assignment.completedQuantity(), Optional.empty(), assignment.reservationId(),
                assignment.pendingSupplyTransferIdentity(), assignment.observedRunRevision(),
                assignment.observedChildSequence(), assignment.failure(), level.getGameTime());
        return new AssignmentResult(AssignmentStatus.CANCELLED,
                reason + " | safely terminalized replaced pre-START assignment "
                        + cancelled.assignmentId().value());
    }

    private boolean provesRetiredInstance(
            MinecraftServer server,
            EmployeeMachineOperationAssignment assignment
    ) {
        // The diagnostic label alone is not retirement evidence. Never resolve or bind the replacement block.
        WorkstationInstanceRecord instance = WorkstationEndpointService.INSTANCE
                .instanceRecord(server, assignment.workstation().instanceId()).orElse(null);
        return instance != null
                && instance.worldIdentity().equals(assignment.worldIdentity())
                && instance.endpointKey().equals(assignment.workstation().endpointKey())
                && instance.generation() == assignment.workstation().generation()
                && instance.lifecycle() == WorkstationInstanceLifecycle.RETIRED
                && instance.unresolvedJournalReferences().isEmpty()
                && !WorkstationEndpointService.INSTANCE.hasUnresolvedEffects(server, instance.instanceId());
    }

    private ReservationClearance clearExactOrphanedReservation(
            MinecraftServer server,
            EmployeeMachineOperationAssignment assignment
    ) {
        List<WorkstationReservationRecord> records = WorkstationReservationService.INSTANCE.managerFor(server)
                .allRecords().stream()
                .filter(value -> value.assignmentReference()
                        .filter(assignment.assignmentId().value()::equals).isPresent())
                .toList();
        if (assignment.reservationId().isPresent()
                && records.stream().noneMatch(value -> value.reservationId().equals(
                assignment.reservationId().orElseThrow()))) {
            return ReservationClearance.blocked("referenced operator reservation evidence is missing");
        }
        if (records.stream().anyMatch(value -> !exactOrphanedReservationBinding(value, assignment))) {
            return ReservationClearance.blocked("operator reservation evidence does not bind the replaced assignment");
        }
        for (WorkstationReservationRecord record : records) {
            if (!record.active()) continue;
            WorkstationReservationResult<WorkstationReservationRecord> released =
                    WorkstationReservationService.INSTANCE.release(
                            server, record, "replaced pre-START machine-operation assignment cancelled");
            if (!released.succeeded()) {
                return ReservationClearance.blocked(
                        released.failure().orElseThrow().detail());
            }
        }
        boolean activeRemains = WorkstationReservationService.INSTANCE.managerFor(server).allRecords().stream()
                .filter(value -> value.assignmentReference()
                        .filter(assignment.assignmentId().value()::equals).isPresent())
                .anyMatch(WorkstationReservationRecord::active);
        return activeRemains
                ? ReservationClearance.blocked("exact replaced-assignment reservation remains active")
                : ReservationClearance.cleared();
    }

    private static boolean exactOrphanedReservationBinding(
            WorkstationReservationRecord reservation,
            EmployeeMachineOperationAssignment assignment
    ) {
        return reservation.exactWorkstationInstance()
                && reservation.employeeIdentity().equals(assignment.employeeId().value())
                && reservation.role() == WorkstationReservationRole.MACHINE_OPERATOR
                && reservation.assignmentReference().filter(
                        assignment.assignmentId().value()::equals).isPresent()
                && reservation.workstationIdentity().equals(assignment.workstation().instanceId().value())
                && reservation.workstationGeneration() == assignment.workstation().generation();
    }

    private void finishTerminalRun(
            ServerLevel level,
            EmployeeMachineOperationAssignment assignment,
            MachineRunRecord run,
            int completed,
            Optional<String> pendingSupply,
            long childSequence
    ) {
        boolean cancelled = assignment.state() == EmployeeMachineOperationAssignmentState.CANCELLATION_REQUESTED;
        EmployeeMachineOperationAssignmentState terminal;
        Optional<EmployeeMachineOperationFailure> failure = Optional.empty();
        if (completed >= assignment.targetQuantity()) {
            terminal = EmployeeMachineOperationAssignmentState.COMPLETED;
        } else if (cancelled) {
            terminal = EmployeeMachineOperationAssignmentState.CANCELLED;
        } else {
            terminal = EmployeeMachineOperationAssignmentState.INTERRUPTED;
            boolean playerStop = run.stopEvidence().filter(value -> !value.sourceOwner().equals(
                    EmployeeMachineOperationAssignmentService.RUN_SOURCE_OWNER)).isPresent();
            failure = Optional.of(new EmployeeMachineOperationFailure(
                    playerStop ? EmployeeMachineOperationFailureCode.PLAYER_INTERRUPTED
                            : EmployeeMachineOperationFailureCode.INPUT_UNAVAILABLE,
                    playerStop ? "Machine Run was stopped by another authorized actor"
                            : "finite assignment stopped because no authoritative pending supply remained"));
        }
        EmployeeMachineOperationAssignment updated = publish(level.getServer(), assignment, terminal, completed,
                Optional.of(run.runIdentity().value()), assignment.reservationId(), pendingSupply,
                run.revision(), childSequence, failure, level.getGameTime());
        releaseExactReservation(level.getServer(), updated, "machine operation reached terminal Run boundary");
    }

    private void acquireOperator(ServerLevel level, EmployeeMachineOperationAssignment assignment) {
        WorkstationReservationResult<WorkstationReservationRecord> result = WorkstationReservationService.INSTANCE
                .assignMachineOperator(level, assignment.employeeId(), position(assignment),
                        assignment.assignmentId().value());
        if (!result.succeeded()) {
            publish(level.getServer(), assignment, EmployeeMachineOperationAssignmentState.WAITING_FOR_RESERVATION,
                    assignment.completedQuantity(), assignment.runIdentity(), Optional.empty(),
                    assignment.pendingSupplyTransferIdentity(), assignment.observedRunRevision(),
                    assignment.observedChildSequence(), Optional.empty(), level.getGameTime());
            return;
        }
        WorkstationReservationRecord reservation = result.orThrow();
        publish(level.getServer(), assignment, EmployeeMachineOperationAssignmentState.NAVIGATING,
                assignment.completedQuantity(), assignment.runIdentity(), Optional.of(reservation.reservationId()),
                assignment.pendingSupplyTransferIdentity(), assignment.observedRunRevision(),
                assignment.observedChildSequence(), Optional.empty(), level.getGameTime());
    }

    private void repairOrAcquireOperator(ServerLevel level, EmployeeMachineOperationAssignment assignment) {
        WorkstationReservationResult<WorkstationReservationRecord> result = WorkstationReservationService.INSTANCE
                .assignMachineOperator(level, assignment.employeeId(), position(assignment),
                        assignment.assignmentId().value());
        if (!result.succeeded()) {
            if (assignment.runIdentity().isPresent()) {
                recovery(level, assignment, EmployeeMachineOperationFailureCode.RESERVATION_MISSING,
                        "active assignment Run has no exact repairable MACHINE_OPERATOR reservation");
            } else if (assignment.state() != EmployeeMachineOperationAssignmentState.WAITING_FOR_RESERVATION) {
                publish(level.getServer(), assignment,
                        EmployeeMachineOperationAssignmentState.WAITING_FOR_RESERVATION,
                        assignment.completedQuantity(), Optional.empty(), Optional.empty(),
                        assignment.pendingSupplyTransferIdentity(), assignment.observedRunRevision(),
                        assignment.observedChildSequence(), Optional.empty(), level.getGameTime());
            }
            return;
        }
        WorkstationReservationRecord reservation = result.orThrow();
        EmployeeMachineOperationAssignmentState repairedState = assignment.runIdentity().isPresent()
                ? assignment.state()
                : EmployeeMachineOperationAssignmentState.NAVIGATING;
        publish(level.getServer(), assignment, repairedState, assignment.completedQuantity(),
                assignment.runIdentity(), Optional.of(reservation.reservationId()),
                assignment.pendingSupplyTransferIdentity(), assignment.observedRunRevision(),
                assignment.observedChildSequence(), assignment.failure(), level.getGameTime());
    }

    private Optional<String> pendingSupply(MinecraftServer server, EmployeeMachineOperationAssignment assignment) {
        for (EmployeeMaterialHandlingAssignment handling : EmployeeMaterialHandlingService.INSTANCE
                .managerFor(server).assignments()) {
            if (!handling.active() || !handling.destination().equals(assignment.workstation())) continue;
            MaterialTransferView transfer = MaterialHandlingService.INSTANCE.findTransfer(server, handling.transferId())
                    .orElse(null);
            if (transfer != null
                    && transfer.materialIdentity().equals(assignment.inputMaterialIdentity())
                    && pendingLifecycle(transfer.lifecycle())) {
                return Optional.of(transfer.transferIdentity());
            }
        }
        return Optional.empty();
    }

    private static boolean pendingLifecycle(MaterialTransferLifecycle lifecycle) {
        return switch (lifecycle) {
            case REQUESTED, SOURCE_BOUND, SOURCE_WITHDRAW_PREPARED, SOURCE_WITHDRAW_COMMITTED,
                    IN_TRANSIT, DESTINATION_BOUND, DESTINATION_DEPOSIT_PREPARED -> true;
            default -> false;
        };
    }

    private Optional<MachineRunRecord> runFor(MinecraftServer server, EmployeeMachineOperationAssignment assignment) {
        if (assignment.runIdentity().isPresent()) {
            try {
                return ExecutionMachineRunService.INSTANCE.find(
                        server, new MachineRunIdentity(assignment.runIdentity().orElseThrow()));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
        return ExecutionMachineRunService.INSTANCE.snapshot(server).startForRequest(
                EmployeeMachineOperationAssignmentService.RUN_SOURCE_OWNER,
                EmployeeMachineOperationAssignmentService.startRequestIdentity(assignment.assignmentId().value()));
    }

    private static int completedQuantity(MachineRunRecord run, EmployeeMachineOperationAssignment assignment) {
        long completedChildren = run.terminalChildren().stream()
                .filter(child -> child.state() == MachineRunChildState.COMPLETED).count();
        return Math.toIntExact(Math.multiplyExact(completedChildren, assignment.inputQuantityPerChild()));
    }

    private static long committedQuantity(MachineRunRecord run, EmployeeMachineOperationAssignment assignment) {
        long completed = completedQuantity(run, assignment);
        return run.currentChild().isPresent()
                ? Math.addExact(completed, assignment.inputQuantityPerChild()) : completed;
    }

    private static long observedChildSequence(MachineRunRecord run) {
        return run.currentChild().map(value -> value.sequence())
                .orElseGet(() -> run.terminalChildren().isEmpty() ? 0L
                        : run.terminalChildren().getLast().sequence());
    }

    private MachineControlResult start(
            ServerLevel level,
            MachineProfile profile,
            EmployeeMachineOperationAssignment assignment,
            WorkstationReservationRecord reservation
    ) {
        String request = EmployeeMachineOperationAssignmentService.startRequestIdentity(
                assignment.assignmentId().value());
        if (profile.machine() instanceof GrinderBlockEntity grinder) {
            GrinderRunControlResult result = GrinderContinuousRunService.INSTANCE.startForEmployee(
                    level, grinder, EmployeeMachineOperationAssignmentService.RUN_SOURCE_OWNER, request,
                    reservation.reservationId().value());
            return MachineControlResult.from(result);
        }
        PoweredMachineRunControlResult result = PattyFormerContinuousRunService.INSTANCE.startForEmployee(
                level, (PattyFormerBlockEntity) profile.machine(),
                EmployeeMachineOperationAssignmentService.RUN_SOURCE_OWNER, request,
                reservation.reservationId().value());
        return MachineControlResult.from(result);
    }

    private MachineControlResult stop(
            ServerLevel level,
            MachineProfile profile,
            EmployeeMachineOperationAssignment assignment,
            MachineRunRecord run
    ) {
        String request = EmployeeMachineOperationAssignmentService.stopRequestIdentity(
                assignment.assignmentId().value());
        if (profile.machine() instanceof GrinderBlockEntity grinder) {
            return MachineControlResult.from(GrinderContinuousRunService.INSTANCE.stopForEmployee(
                    level, grinder, run.runIdentity(), EmployeeMachineOperationAssignmentService.RUN_SOURCE_OWNER,
                    request));
        }
        return MachineControlResult.from(PattyFormerContinuousRunService.INSTANCE.stopForEmployee(
                level, (PattyFormerBlockEntity) profile.machine(), run.runIdentity(),
                EmployeeMachineOperationAssignmentService.RUN_SOURCE_OWNER, request));
    }

    private static MachineOperatingState operatingState(MachineProfile profile) {
        if (profile.machine() instanceof GrinderBlockEntity grinder) return grinder.runStatus().operatingState();
        return ((PattyFormerBlockEntity) profile.machine()).runStatus().operatingState();
    }

    private Optional<MachineProfile> resolveProfile(ServerLevel level, BlockPos position) {
        if (level.getBlockEntity(position) instanceof GrinderBlockEntity grinder && !grinder.isRemoved()) {
            return Optional.of(new MachineProfile(GRINDER, "Grinder", BuiltInDefinitionIds.GRIND_BEEF.toString(),
                    BuiltInDefinitionIds.BEEF_TRIM.toString(), grinder, grinder.inventory().input()));
        }
        if (level.getBlockEntity(position) instanceof PattyFormerBlockEntity pattyFormer && !pattyFormer.isRemoved()) {
            return Optional.of(new MachineProfile(PATTY_FORMER, "Patty Former",
                    BuiltInDefinitionIds.FORM_BEEF_PATTIES.toString(), BuiltInDefinitionIds.GROUND_BEEF.toString(),
                    pattyFormer, pattyFormer.inventory().input()));
        }
        return Optional.empty();
    }

    private static boolean inputMatches(MachineProfile profile) {
        ItemStack stack = profile.input();
        if (stack.isEmpty()) return false;
        return ProductStackAdapter.readProductData(stack).value()
                .filter(data -> data.productTypeId().equals(profile.inputMaterialIdentity())).isPresent();
    }

    private Optional<WorkstationReservationRecord> operatorReservation(
            MinecraftServer server,
            EmployeeMachineOperationAssignment assignment
    ) {
        return WorkstationReservationService.INSTANCE.managerFor(server)
                .findByEmployee(assignment.employeeId().value())
                .filter(WorkstationReservationRecord::active)
                .filter(value -> value.role() == WorkstationReservationRole.MACHINE_OPERATOR)
                .filter(value -> value.assignmentReference().filter(
                        assignment.assignmentId().value()::equals).isPresent())
                .filter(value -> value.workstationIdentity().equals(assignment.workstation().instanceId().value()))
                .filter(value -> value.workstationGeneration() == assignment.workstation().generation());
    }

    private void releaseExactReservation(
            MinecraftServer server,
            EmployeeMachineOperationAssignment assignment,
            String reason
    ) {
        operatorReservation(server, assignment).ifPresent(value ->
                WorkstationReservationService.INSTANCE.release(server, value, reason));
    }

    private EmployeeMachineOperationAssignment recovery(
            ServerLevel level,
            EmployeeMachineOperationAssignment assignment,
            EmployeeMachineOperationFailureCode code,
            String detail
    ) {
        return publish(level.getServer(), assignment, EmployeeMachineOperationAssignmentState.RECOVERY_REQUIRED,
                assignment.completedQuantity(), assignment.runIdentity(), assignment.reservationId(),
                assignment.pendingSupplyTransferIdentity(), assignment.observedRunRevision(),
                assignment.observedChildSequence(), Optional.of(new EmployeeMachineOperationFailure(code, detail)),
                level.getGameTime());
    }

    private EmployeeMachineOperationAssignment publish(
            MinecraftServer server,
            EmployeeMachineOperationAssignment assignment,
            EmployeeMachineOperationAssignmentState state,
            int completed,
            Optional<String> run,
            Optional<com.butchercraft.workstation.reservation.WorkstationReservationId> reservation,
            Optional<String> pendingSupply,
            long runRevision,
            long childSequence,
            Optional<EmployeeMachineOperationFailure> failure,
            long tick
    ) {
        EmployeeMachineOperationAssignment updated = assignments(server).publish(
                assignment.assignmentId(), state, completed, run, reservation, pendingSupply,
                runRevision, childSequence, failure, tick);
        if (updated != assignment) persist(server);
        return updated;
    }

    private static Optional<EmployeeEntity> entity(ServerLevel level, EmployeeRecord employee) {
        EmployeeEntityLink link = employee.entityLink().orElse(null);
        if (link == null || !link.dimensionIdentity().equals(EmployeeService.dimensionIdentity(level))) {
            return Optional.empty();
        }
        Entity entity = level.getEntity(link.entityUuid());
        return entity instanceof EmployeeEntity value ? Optional.of(value) : Optional.empty();
    }

    private static Optional<ServerLevel> level(MinecraftServer server, String dimensionIdentity) {
        try {
            return Optional.ofNullable(server.getLevel(ResourceKey.create(
                    Registries.DIMENSION, ResourceLocation.parse(dimensionIdentity))));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static BlockPos position(EmployeeMachineOperationAssignment assignment) {
        return new BlockPos(assignment.workstation().endpointKey().x(), assignment.workstation().endpointKey().y(),
                assignment.workstation().endpointKey().z());
    }

    private static boolean mutationPermitted(MinecraftServer server) {
        return StartupMutationGateService.INSTANCE.permits(server, LegacySplitRecoveryParticipants.WORKFORCE)
                && StartupMutationGateService.INSTANCE.permits(server, LegacySplitRecoveryParticipants.EXECUTION)
                && StartupMutationGateService.INSTANCE.permits(server, LegacySplitRecoveryParticipants.WORKSTATION);
    }

    private static EmployeeMachineOperationAssignmentManager assignments(MinecraftServer server) {
        return EmployeeMachineOperationAssignmentService.INSTANCE.managerFor(server);
    }

    private static void persist(MinecraftServer server) {
        EmployeeMachineOperationAssignmentService.INSTANCE.persist(server);
    }

    private record MachineProfile(
            String machineType,
            String displayName,
            String operationIdentity,
            String inputMaterialIdentity,
            AbstractProcessingWorkstationBlockEntity machine,
            ItemStack input
    ) {
        private boolean matches(EmployeeMachineOperationAssignment assignment) {
            return machineType.equals(assignment.machineType())
                    && POLICY.equals(assignment.operatingPolicyIdentity())
                    && operationIdentity.equals(assignment.operationIdentity())
                    && inputMaterialIdentity.equals(assignment.inputMaterialIdentity());
        }
    }

    private record MachineControlResult(
            boolean accepted,
            boolean busy,
            Optional<MachineRunIdentity> runIdentity,
            String detail
    ) {
        private static MachineControlResult from(GrinderRunControlResult result) {
            return new MachineControlResult(result.accepted(), result.code().name().equals("BUSY"),
                    result.runIdentity(), result.detail());
        }

        private static MachineControlResult from(PoweredMachineRunControlResult result) {
            return new MachineControlResult(result.accepted(), result.code() == PoweredMachineRunControlCode.BUSY,
                    result.runIdentity(), result.detail());
        }
    }

    private record ReservationClearance(boolean safe, String detail) {
        private static ReservationClearance cleared() {
            return new ReservationClearance(true, "exact replaced-assignment reservation is absent or terminal");
        }

        private static ReservationClearance blocked(String detail) {
            return new ReservationClearance(false, detail);
        }
    }

    public enum AssignmentStatus {
        ACCEPTED,
        EXISTING_ASSIGNMENT,
        EMPLOYEE_UNAVAILABLE,
        PLANT_CLOSED,
        ASSIGNMENT_CONFLICT,
        ASSIGNMENT_NOT_FOUND,
        WORKSTATION_UNAVAILABLE,
        UNSUPPORTED_MACHINE,
        INVALID_INPUT,
        INVALID_TARGET,
        CANCELLATION_REQUESTED,
        CANCELLED,
        RECOVERY_REQUIRED
    }

    public record AssignmentResult(AssignmentStatus status, String detail) {
        public AssignmentResult {
            status = Objects.requireNonNull(status, "status");
            detail = Objects.requireNonNull(detail, "detail").strip();
            if (detail.isEmpty()) throw new IllegalArgumentException("Assignment result detail must not be blank");
        }

        public static AssignmentResult rejected(AssignmentStatus status, String detail) {
            return new AssignmentResult(status, detail);
        }

        public boolean accepted() {
            return status == AssignmentStatus.ACCEPTED || status == AssignmentStatus.EXISTING_ASSIGNMENT
                    || status == AssignmentStatus.CANCELLATION_REQUESTED || status == AssignmentStatus.CANCELLED;
        }
    }

    public record OperationDiagnostics(
            String assignmentIdentity,
            String lifecycle,
            String machineType,
            String workstationIdentity,
            int target,
            int completed,
            int remaining,
            String reservationIdentity,
            String runIdentity,
            String runLifecycle,
            String machineState,
            String childState,
            String pendingSupply,
            String failure
    ) {
        public static OperationDiagnostics none() {
            return new OperationDiagnostics("none", "idle", "none", "none", 0, 0, 0,
                    "none", "none", "none", "none", "none", "none", "none");
        }
    }
}
