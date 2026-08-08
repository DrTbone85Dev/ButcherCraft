package com.butchercraft.world;

import com.butchercraft.entity.employee.EmployeeEntity;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointObservationResult;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReferenceResult;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointService;
import com.butchercraft.workstation.reservation.WorkstationReservationRecord;
import com.butchercraft.workstation.reservation.WorkstationReservationResult;
import com.butchercraft.workstation.reservation.WorkstationReservationState;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import com.butchercraft.world.materialhandling.MaterialCustodyLocation;
import com.butchercraft.world.materialhandling.MaterialTransferLifecycle;
import com.butchercraft.world.materialhandling.MaterialTransferRecord;
import com.butchercraft.world.materialhandling.runtime.MaterialHandlingService;
import com.butchercraft.world.materialhandling.runtime.MaterialHandlingTransferResult;
import com.butchercraft.world.workforce.employee.EmployeeEntityLink;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.employee.EmployeePresenceObservation;
import com.butchercraft.world.workforce.employee.EmployeePresenceState;
import com.butchercraft.world.workforce.employee.EmployeeRecord;
import com.butchercraft.world.workforce.employee.EmployeeStatus;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignment;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignmentId;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignmentManager;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignmentSchema;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignmentState;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingFailure;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingFailureCode;
import com.butchercraft.world.workforce.materialhandling.persistence.EmployeeMaterialHandlingAssignmentStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class EmployeeMaterialHandlingService {
    public static final EmployeeMaterialHandlingService INSTANCE = new EmployeeMaterialHandlingService(
            EmployeeService.INSTANCE,
            WorkstationReservationService.INSTANCE,
            MaterialHandlingService.INSTANCE,
            WorkstationEndpointService.INSTANCE,
            WorldIdentityService.INSTANCE
    );

    private static final String CUTTING_TABLE_TYPE = "butchercraft:cutting_table";
    private static final String GRINDER_TYPE = "butchercraft:grinder";
    private static final int MAX_RESERVATION_WAIT_ATTEMPTS = 5;
    private static final long RESERVATION_RETRY_INTERVAL_TICKS = 40L;

    private final EmployeeService employeeService;
    private final WorkstationReservationService reservationService;
    private final MaterialHandlingService materialHandlingService;
    private final WorkstationEndpointService endpointService;
    private final WorldIdentityService worldIdentityService;
    private final AtomicReference<ActiveAssignments> active = new AtomicReference<>();

    EmployeeMaterialHandlingService(
            EmployeeService employeeService,
            WorkstationReservationService reservationService,
            MaterialHandlingService materialHandlingService,
            WorkstationEndpointService endpointService,
            WorldIdentityService worldIdentityService
    ) {
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService");
        this.reservationService = Objects.requireNonNull(reservationService, "reservationService");
        this.materialHandlingService = Objects.requireNonNull(materialHandlingService, "materialHandlingService");
        this.endpointService = Objects.requireNonNull(endpointService, "endpointService");
        this.worldIdentityService = Objects.requireNonNull(worldIdentityService, "worldIdentityService");
    }

    public void initialize(ServerStartedEvent event) {
        ActiveAssignments runtime = load(event.getServer());
        reconcileLoadedAssignments(event.getServer(), runtime);
    }

    public void save(ServerStoppingEvent event) {
        ActiveAssignments current = active.get();
        if (current != null && current.server() == event.getServer()) {
            current.storage().save(current.manager().directory());
            active.compareAndSet(current, null);
        }
    }

    public AssignmentResult request(
            ServerLevel level,
            EmployeeId employeeId,
            BlockPos sourcePosition,
            BlockPos destinationPosition
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(employeeId, "employeeId");
        sourcePosition = Objects.requireNonNull(sourcePosition, "sourcePosition").immutable();
        destinationPosition = Objects.requireNonNull(destinationPosition, "destinationPosition").immutable();
        ActiveAssignments runtime = load(level.getServer());

        WorkstationEndpointReferenceResult sourceReference = endpointService.referenceFor(level, sourcePosition);
        if (!sourceReference.succeeded()) {
            return AssignmentResult.rejected(
                    AssignmentStatus.INVALID_SOURCE,
                    Optional.empty(),
                    "Source endpoint unavailable: " + sourceReference.detail()
            );
        }
        WorkstationEndpointReference source = sourceReference.reference().orElseThrow();
        if (!CUTTING_TABLE_TYPE.equals(source.endpointKey().workstationTypeIdentity())) {
            return AssignmentResult.rejected(
                    AssignmentStatus.INVALID_SOURCE,
                    Optional.empty(),
                    "Source must be a Cutting Table"
            );
        }
        WorkstationEndpointReferenceResult destinationReference = endpointService.referenceFor(level, destinationPosition);
        if (!destinationReference.succeeded()) {
            return AssignmentResult.rejected(
                    AssignmentStatus.INVALID_DESTINATION,
                    Optional.empty(),
                    "Destination endpoint unavailable: " + destinationReference.detail()
            );
        }
        WorkstationEndpointReference destination = destinationReference.reference().orElseThrow();
        if (!GRINDER_TYPE.equals(destination.endpointKey().workstationTypeIdentity())) {
            return AssignmentResult.rejected(
                    AssignmentStatus.INVALID_DESTINATION,
                    Optional.empty(),
                    "Destination must be a Grinder"
            );
        }
        String dimension = EmployeeService.dimensionIdentity(level);
        if (!source.endpointKey().dimensionIdentity().equals(dimension)
                || !destination.endpointKey().dimensionIdentity().equals(dimension)) {
            return AssignmentResult.rejected(
                    AssignmentStatus.INVALID_DESTINATION,
                    Optional.empty(),
                    "Both endpoints must be in the executing player's current dimension"
            );
        }

        Optional<EmployeeMaterialHandlingAssignment> previous = runtime.manager().latestFor(employeeId);
        if (previous.filter(value -> value.binds(source, destination)).isPresent()) {
            return AssignmentResult.observed(previous.orElseThrow());
        }
        if (runtime.manager().activeFor(employeeId).isPresent()) {
            return AssignmentResult.rejected(
                    AssignmentStatus.ASSIGNMENT_CONFLICT,
                    runtime.manager().activeFor(employeeId),
                    "Employee already has an active Material Handling assignment"
            );
        }

        Availability availability = availability(level, employeeId);
        if (!availability.available()) {
            return AssignmentResult.rejected(availability.status(), Optional.empty(), availability.detail());
        }
        if (reservationService.managerFor(level.getServer()).findByEmployee(employeeId.value()).isPresent()) {
            return AssignmentResult.rejected(
                    AssignmentStatus.RESERVATION_CONFLICT,
                    Optional.empty(),
                    "Employee already holds a workstation reservation"
            );
        }

        WorkstationEndpointObservationResult sourceObservation = endpointService.observeWithdrawalOne(
                level,
                sourcePosition
        );
        if (!sourceObservation.succeeded()) {
            AssignmentStatus status = sourceObservation.code() == WorkstationEndpointResultCode.SOURCE_EMPTY
                    ? AssignmentStatus.SOURCE_EMPTY
                    : AssignmentStatus.INVALID_SOURCE;
            return AssignmentResult.rejected(status, Optional.empty(), sourceObservation.detail());
        }
        if (!sourceObservation.observation().orElseThrow().exactEffectStack().itemIdentity()
                .equals(BuiltInRegistries.ITEM.getKey(ModItems.BEEF_TRIM.get()).toString())) {
            return AssignmentResult.rejected(
                    AssignmentStatus.INVALID_SOURCE,
                    Optional.empty(),
                    "Cutting Table source must contain exactly one Beef Trim"
            );
        }

        MaterialHandlingTransferResult transferRequest = materialHandlingService.requestEmployeeTransfer(
                level,
                sourcePosition,
                destinationPosition,
                employeeId.value()
        );
        if (!transferRequest.succeeded()) {
            return materialFailure(Optional.empty(), transferRequest);
        }
        MaterialTransferRecord transfer = transferRequest.transfer().orElseThrow();
        EmployeeMaterialHandlingAssignmentManager.CreateResult created = runtime.manager().createOrObserve(
                WorldIdentityRootIdentities.from(worldIdentityService.getOrCreate(level.getServer())),
                employeeId,
                transfer.transferId(),
                source,
                destination,
                level.getGameTime()
        );
        runtime.storage().save(runtime.manager().directory());
        if (created.status() == EmployeeMaterialHandlingAssignmentManager.CreateStatus.CONFLICT) {
            materialHandlingService.cancel(level, transfer.transferId(), "Workforce assignment conflict");
            return AssignmentResult.rejected(
                    AssignmentStatus.ASSIGNMENT_CONFLICT,
                    Optional.of(created.assignment()),
                    "Employee assignment conflict"
            );
        }
        EmployeeMaterialHandlingAssignment assignment = transition(
                runtime,
                created.assignment(),
                EmployeeMaterialHandlingAssignmentState.WALKING_TO_SOURCE,
                Optional.empty()
        );
        WorkstationReservationResult<WorkstationReservationRecord> reservation = reservationService.assign(
                level,
                employeeId,
                sourcePosition
        );
        if (!reservation.succeeded()) {
            materialHandlingService.cancel(level, transfer.transferId(), "Source reservation was rejected");
            assignment = transition(
                    runtime,
                    assignment,
                    EmployeeMaterialHandlingAssignmentState.FAILED,
                    failure(EmployeeMaterialHandlingFailureCode.SOURCE_RESERVATION_FAILED,
                            reservation.failure().orElseThrow().detail())
            );
            return AssignmentResult.rejected(
                    AssignmentStatus.RESERVATION_CONFLICT,
                    Optional.of(assignment),
                    reservation.failure().orElseThrow().detail()
            );
        }
        entity(level, availability.record().orElseThrow()).ifPresent(employeeService::synchronizeEntity);
        return AssignmentResult.accepted(assignment);
    }

    public AssignmentResult cancel(ServerLevel level, EmployeeId employeeId, String reason) {
        ActiveAssignments runtime = load(level.getServer());
        EmployeeMaterialHandlingAssignment assignment = runtime.manager().activeFor(employeeId).orElse(null);
        if (assignment == null) {
            Optional<EmployeeMaterialHandlingAssignment> latest = runtime.manager().latestFor(employeeId);
            if (latest.isPresent() && latest.orElseThrow().state() == EmployeeMaterialHandlingAssignmentState.COMPLETED) {
                return AssignmentResult.completed(latest.orElseThrow(), "Transfer already completed");
            }
            if (latest.isPresent() && latest.orElseThrow().state() == EmployeeMaterialHandlingAssignmentState.CANCELLED) {
                return AssignmentResult.cancelled(latest.orElseThrow(), "Assignment already cancelled");
            }
            return AssignmentResult.rejected(AssignmentStatus.ASSIGNMENT_NOT_FOUND, latest,
                    "Employee has no active Material Handling assignment");
        }
        MaterialTransferRecord transfer = materialHandlingService.findTransfer(level.getServer(), assignment.transferId())
                .orElse(null);
        if (transfer == null) {
            EmployeeMaterialHandlingAssignment blocked = recovery(runtime, assignment,
                    EmployeeMaterialHandlingFailureCode.RECOVERY_REQUIRED,
                    "Authoritative Material Transfer is missing");
            return AssignmentResult.rejected(AssignmentStatus.RECOVERY_REQUIRED, Optional.of(blocked),
                    "Authoritative Material Transfer is missing");
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.UNKNOWN_OUTCOME) {
            EmployeeMaterialHandlingAssignment blocked = recovery(runtime, assignment,
                    EmployeeMaterialHandlingFailureCode.UNKNOWN_OUTCOME,
                    "Material Transfer has Unknown Outcome and cannot be cancelled");
            return AssignmentResult.rejected(AssignmentStatus.UNKNOWN_OUTCOME, Optional.of(blocked),
                    "Material Transfer has Unknown Outcome and requires reconciliation");
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.COMPLETED) {
            EmployeeMaterialHandlingAssignment completed = complete(runtime, assignment);
            return AssignmentResult.completed(completed, "Transfer already completed");
        }
        if (assignment.state() != EmployeeMaterialHandlingAssignmentState.CANCELLATION_REQUESTED) {
            assignment = transition(
                    runtime,
                    assignment,
                    EmployeeMaterialHandlingAssignmentState.CANCELLATION_REQUESTED,
                    Optional.empty()
            );
        }
        Optional<EmployeeEntity> employee = employeeFor(level, employeeId);
        if (employee.isPresent()) {
            advanceCancellation(level, runtime, assignment, transfer, employee.orElseThrow());
        } else if (transfer.hasProvenMaterialHandlingCustody()) {
            assignment = recovery(runtime, assignment,
                    EmployeeMaterialHandlingFailureCode.RECOVERY_REQUIRED,
                    "Employee is unavailable while Material Handling retains custody");
        } else {
            assignment = cancelBeforeCustody(level, runtime, assignment, transfer);
        }
        EmployeeMaterialHandlingAssignment current = runtime.manager().latestFor(employeeId).orElse(assignment);
        return current.state() == EmployeeMaterialHandlingAssignmentState.CANCELLED
                ? AssignmentResult.cancelled(current, "Assignment cancelled")
                : current.state() == EmployeeMaterialHandlingAssignmentState.RECOVERY_REQUIRED
                ? AssignmentResult.rejected(AssignmentStatus.RECOVERY_REQUIRED, Optional.of(current),
                current.failure().map(EmployeeMaterialHandlingFailure::detail).orElse("Recovery required"))
                : AssignmentResult.cancellationRequested(current, Objects.requireNonNull(reason, "reason").strip());
    }

    public void tick(EmployeeEntity employee) {
        if (!(employee.level() instanceof ServerLevel level)) {
            return;
        }
        EmployeeId employeeId;
        try {
            employeeId = new EmployeeId(employee.employeeIdValue());
        } catch (IllegalArgumentException exception) {
            employee.clearCarryObservation(Long.MAX_VALUE);
            return;
        }
        ActiveAssignments runtime = load(level.getServer());
        EmployeeMaterialHandlingAssignment assignment = runtime.manager().activeFor(employeeId).orElse(null);
        if (assignment == null) {
            runtime.manager().latestFor(employeeId)
                    .ifPresent(value -> employee.clearCarryObservation(value.revision()));
            return;
        }
        MaterialTransferRecord transfer = materialHandlingService.findTransfer(level.getServer(), assignment.transferId())
                .orElse(null);
        if (transfer == null || !transfer.source().equals(assignment.source())
                || !transfer.destination().equals(assignment.destination())
                || transfer.employeeReference().filter(employeeId.value()::equals).isEmpty()) {
            recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.RECOVERY_REQUIRED,
                    "Assignment and authoritative Material Transfer do not match");
            employee.clearCarryObservation(assignment.revision() + 1L);
            return;
        }
        refreshCarry(employee, assignment, transfer);
        if (assignment.state() == EmployeeMaterialHandlingAssignmentState.RECOVERY_REQUIRED) {
            return;
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.UNKNOWN_OUTCOME) {
            recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.UNKNOWN_OUTCOME,
                    "Material Transfer entered Unknown Outcome");
            employee.clearCarryObservation(assignment.revision() + 1L);
            return;
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.COMPLETED) {
            complete(runtime, assignment);
            employee.clearCarryObservation(runtime.manager().latestFor(employeeId).orElseThrow().revision());
            return;
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.CANCELLED) {
            EmployeeMaterialHandlingAssignment cancelled = transition(
                    runtime,
                    assignment,
                    EmployeeMaterialHandlingAssignmentState.CANCELLED,
                    Optional.empty()
            );
            employee.clearCarryObservation(cancelled.revision());
            return;
        }
        Availability availability = availability(level, employeeId);
        if (!availability.available()) {
            handleUnavailable(level, runtime, assignment, transfer, availability);
            return;
        }
        switch (assignment.state()) {
            case IDLE -> transition(runtime, assignment,
                    EmployeeMaterialHandlingAssignmentState.WALKING_TO_SOURCE, Optional.empty());
            case WALKING_TO_SOURCE, WAITING_FOR_SOURCE_RESERVATION ->
                    advanceSource(level, runtime, assignment, transfer, employee);
            case WITHDRAWAL_REQUESTED -> observeWithdrawal(level, runtime, assignment, transfer, employee);
            case CARRYING_TO_DESTINATION, WAITING_FOR_DESTINATION_RESERVATION ->
                    advanceDestination(level, runtime, assignment, transfer, employee);
            case DEPOSIT_REQUESTED -> observeDeposit(level, runtime, assignment, transfer, employee);
            case CANCELLATION_REQUESTED -> advanceCancellation(level, runtime, assignment, transfer, employee);
            case COMPLETED, CANCELLED, RECOVERY_REQUIRED, FAILED -> {
                // Terminal and operator-gated states perform no automatic consequential work.
            }
        }
    }

    public void handleNavigationFailure(EmployeeEntity employee, String reason) {
        if (!(employee.level() instanceof ServerLevel level)) {
            return;
        }
        EmployeeId employeeId;
        try {
            employeeId = new EmployeeId(employee.employeeIdValue());
        } catch (IllegalArgumentException exception) {
            return;
        }
        ActiveAssignments runtime = load(level.getServer());
        EmployeeMaterialHandlingAssignment assignment = runtime.manager().activeFor(employeeId).orElse(null);
        if (assignment == null) {
            return;
        }
        MaterialTransferRecord transfer = materialHandlingService.findTransfer(level.getServer(), assignment.transferId())
                .orElse(null);
        boolean sourceTrip = assignment.state() == EmployeeMaterialHandlingAssignmentState.WALKING_TO_SOURCE
                || assignment.state() == EmployeeMaterialHandlingAssignmentState.WAITING_FOR_SOURCE_RESERVATION
                || assignment.state() == EmployeeMaterialHandlingAssignmentState.WITHDRAWAL_REQUESTED;
        if (transfer != null && transfer.hasProvenMaterialHandlingCustody()) {
            recovery(runtime, assignment,
                    sourceTrip ? EmployeeMaterialHandlingFailureCode.SOURCE_UNREACHABLE
                            : EmployeeMaterialHandlingFailureCode.DESTINATION_UNREACHABLE,
                    "Navigation failed while custody remained proven: " + reason);
        } else if (transfer != null) {
            materialHandlingService.cancel(level, transfer.transferId(), "Employee navigation failed before custody");
            transition(runtime, assignment, EmployeeMaterialHandlingAssignmentState.FAILED,
                    failure(EmployeeMaterialHandlingFailureCode.SOURCE_UNREACHABLE,
                            "Navigation failed before withdrawal: " + reason));
        }
    }

    public void handleEmployeeRemoval(EmployeeEntity employee) {
        if (!(employee.level() instanceof ServerLevel level)) {
            return;
        }
        EmployeeId employeeId;
        try {
            employeeId = new EmployeeId(employee.employeeIdValue());
        } catch (IllegalArgumentException exception) {
            return;
        }
        ActiveAssignments runtime = load(level.getServer());
        EmployeeMaterialHandlingAssignment assignment = runtime.manager().activeFor(employeeId).orElse(null);
        if (assignment == null) {
            return;
        }
        MaterialTransferRecord transfer = materialHandlingService.findTransfer(level.getServer(), assignment.transferId())
                .orElse(null);
        if (transfer != null && transfer.hasProvenMaterialHandlingCustody()) {
            recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.RECOVERY_REQUIRED,
                    "Employee was removed while Material Handling retained custody");
        } else if (transfer != null) {
            materialHandlingService.cancel(level, transfer.transferId(), "Employee removed before custody");
            transition(runtime, assignment, EmployeeMaterialHandlingAssignmentState.FAILED,
                    failure(EmployeeMaterialHandlingFailureCode.EMPLOYEE_UNAVAILABLE,
                            "Employee was removed before withdrawal"));
        }
    }

    public Optional<EmployeeMaterialHandlingAssignment> latestFor(MinecraftServer server, EmployeeId employeeId) {
        return load(server).manager().latestFor(employeeId);
    }

    public Optional<EmployeeMaterialHandlingAssignment> activeFor(MinecraftServer server, EmployeeId employeeId) {
        return load(server).manager().activeFor(employeeId);
    }

    public EmployeeMaterialHandlingAssignmentManager managerFor(MinecraftServer server) {
        return load(server).manager();
    }

    public TransferDiagnostics diagnostics(ServerLevel level, EmployeeId employeeId) {
        Optional<EmployeeMaterialHandlingAssignment> assignment = latestFor(level.getServer(), employeeId);
        if (assignment.isEmpty()) {
            return TransferDiagnostics.none(employeeId);
        }
        EmployeeMaterialHandlingAssignment value = assignment.orElseThrow();
        Optional<MaterialTransferRecord> transfer = materialHandlingService.findTransfer(
                level.getServer(),
                value.transferId()
        );
        Optional<WorkstationReservationRecord> reservation = reservationService.managerFor(level.getServer())
                .findByEmployee(employeeId.value());
        Optional<EmployeeEntity> employee = employeeFor(level, employeeId);
        return new TransferDiagnostics(
                value.assignmentId().value(),
                value.transferId().value(),
                value.state().name().toLowerCase(java.util.Locale.ROOT),
                transfer.map(record -> record.lifecycle().name().toLowerCase(java.util.Locale.ROOT)).orElse("missing"),
                formatEndpoint(value.source()),
                formatEndpoint(value.destination()),
                reservation.map(record -> record.workstationType() + ":" + record.state().serializedName())
                        .orElse("none"),
                employee.map(entity -> entity.navigationStateValue() + ":" + entity.navigationDiagnostics().destinationType())
                        .orElse("unloaded"),
                transfer.flatMap(MaterialTransferRecord::custodyLocation).map(Enum::name)
                        .orElse("unproven"),
                employee.map(entity -> entity.getMainHandItem().isEmpty()
                        ? "none"
                        : BuiltInRegistries.ITEM.getKey(entity.getMainHandItem().getItem()).toString()).orElse("none"),
                employee.map(EmployeeEntity::carryObservationRevision).orElse(0L),
                value.failure().map(failure -> failure.code().serializedName() + ":" + failure.detail()).orElse("none"),
                requiredOperatorAction(value, transfer)
        );
    }

    public void resetGameTestAssignments(MinecraftServer server) {
        requireGameTestServer(server);
        ActiveAssignments existing = load(server);
        ActiveAssignments reset = new ActiveAssignments(
                server,
                existing.storage(),
                EmployeeMaterialHandlingAssignmentManager.empty(),
                new HashMap<>()
        );
        active.set(reset);
        reset.storage().save(reset.manager().directory());
    }

    public void reloadGameTestAssignments(MinecraftServer server) {
        requireGameTestServer(server);
        ActiveAssignments existing = load(server);
        existing.storage().save(existing.manager().directory());
        ActiveAssignments reloaded = new ActiveAssignments(
                server,
                existing.storage(),
                new EmployeeMaterialHandlingAssignmentManager(existing.storage().load()),
                new HashMap<>()
        );
        active.set(reloaded);
        reconcileLoadedAssignments(server, reloaded);
    }

    public static Path assignmentFile(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                .resolve(EmployeeMaterialHandlingAssignmentSchema.DIRECTORY_NAME)
                .resolve(EmployeeMaterialHandlingAssignmentSchema.FILE_NAME)
                .toAbsolutePath()
                .normalize();
    }

    private void advanceSource(
            ServerLevel level,
            ActiveAssignments runtime,
            EmployeeMaterialHandlingAssignment assignment,
            MaterialTransferRecord transfer,
            EmployeeEntity employee
    ) {
        if (!currentEndpoint(level, assignment.source())) {
            failBeforeCustodyOrRecover(level, runtime, assignment, transfer,
                    EmployeeMaterialHandlingFailureCode.SOURCE_ENDPOINT_REPLACED,
                    "Cutting Table endpoint was removed or replaced");
            return;
        }
        Optional<WorkstationReservationRecord> reservation = reservationService.managerFor(level.getServer())
                .findByEmployee(assignment.employeeId().value());
        if (reservation.isEmpty() || !isSourceReservation(reservation.orElseThrow(), assignment)) {
            if (!retryReservation(level, runtime, assignment, position(assignment.source()), true)) {
                return;
            }
            assignment = runtime.manager().find(assignment.assignmentId()).orElseThrow();
            reservation = reservationService.managerFor(level.getServer())
                    .findByEmployee(assignment.employeeId().value());
        }
        if (reservation.isEmpty()) {
            return;
        }
        WorkstationReservationRecord activeReservation = reservation.orElseThrow();
        if (activeReservation.state() != WorkstationReservationState.EMPLOYEE_ARRIVED
                || !reservationService.isWithinOperatingTolerance(level, activeReservation, employee.position())) {
            return;
        }
        EmployeeMaterialHandlingAssignment requested = transition(
                runtime,
                assignment,
                EmployeeMaterialHandlingAssignmentState.WITHDRAWAL_REQUESTED,
                Optional.empty()
        );
        MaterialHandlingTransferResult result = materialHandlingService.withdrawToCustody(
                level,
                requested.transferId()
        );
        MaterialTransferRecord current = result.transfer().orElse(transfer);
        observeWithdrawal(level, runtime, requested, current, employee);
    }

    private void observeWithdrawal(
            ServerLevel level,
            ActiveAssignments runtime,
            EmployeeMaterialHandlingAssignment assignment,
            MaterialTransferRecord transfer,
            EmployeeEntity employee
    ) {
        MaterialTransferRecord current = materialHandlingService.findTransfer(level.getServer(), assignment.transferId())
                .orElse(transfer);
        if (current.hasProvenMaterialHandlingCustody()) {
            EmployeeMaterialHandlingAssignment carrying = transition(
                    runtime,
                    assignment,
                    EmployeeMaterialHandlingAssignmentState.CARRYING_TO_DESTINATION,
                    Optional.empty()
            );
            refreshCarry(employee, carrying, current);
            releaseSourceThenAcquireDestination(level, runtime, carrying);
            return;
        }
        if (current.lifecycle() == MaterialTransferLifecycle.SOURCE_WITHDRAW_PREPARED) {
            recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.RECOVERY_REQUIRED,
                    "Prepared source effect lacks proven owner result; operator reconciliation is required");
            return;
        }
        if (current.lifecycle() == MaterialTransferLifecycle.RECOVERY_REQUIRED) {
            recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.RECOVERY_REQUIRED,
                    current.terminalDetail().orElse("Source withdrawal requires recovery"));
            return;
        }
        if (current.lifecycle() == MaterialTransferLifecycle.UNKNOWN_OUTCOME) {
            recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.UNKNOWN_OUTCOME,
                    current.terminalDetail().orElse("Source withdrawal has Unknown Outcome"));
            return;
        }
        if (current.lifecycle() == MaterialTransferLifecycle.FAILED) {
            transition(runtime, assignment, EmployeeMaterialHandlingAssignmentState.FAILED,
                    failure(EmployeeMaterialHandlingFailureCode.WITHDRAWAL_REJECTED,
                            current.terminalDetail().orElse("Source withdrawal was rejected")));
        }
    }

    private void releaseSourceThenAcquireDestination(
            ServerLevel level,
            ActiveAssignments runtime,
            EmployeeMaterialHandlingAssignment assignment
    ) {
        Optional<WorkstationReservationRecord> reservation = reservationService.managerFor(level.getServer())
                .findByEmployee(assignment.employeeId().value());
        if (reservation.isPresent() && isSourceReservation(reservation.orElseThrow(), assignment)) {
            WorkstationReservationResult<WorkstationReservationRecord> released = reservationService.release(
                    level.getServer(),
                    assignment.employeeId(),
                    "Material Handling custody accepted; source reservation released"
            );
            if (!released.succeeded()) {
                recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.RECOVERY_REQUIRED,
                        "Source reservation release could not be proven");
                return;
            }
        }
        Optional<WorkstationReservationRecord> remaining = reservationService.managerFor(level.getServer())
                .findByEmployee(assignment.employeeId().value());
        if (remaining.isPresent()) {
            recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.RESERVATION_LOST,
                    "Employee still holds a conflicting reservation after source release");
            return;
        }
        WorkstationReservationResult<WorkstationReservationRecord> destination = reservationService.assign(
                level,
                assignment.employeeId(),
                position(assignment.destination())
        );
        if (!destination.succeeded()) {
            transition(runtime, assignment,
                    EmployeeMaterialHandlingAssignmentState.WAITING_FOR_DESTINATION_RESERVATION,
                    failure(EmployeeMaterialHandlingFailureCode.DESTINATION_RESERVATION_FAILED,
                            destination.failure().orElseThrow().detail()));
        }
    }

    private void advanceDestination(
            ServerLevel level,
            ActiveAssignments runtime,
            EmployeeMaterialHandlingAssignment assignment,
            MaterialTransferRecord transfer,
            EmployeeEntity employee
    ) {
        if (!transfer.hasProvenMaterialHandlingCustody()) {
            recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.CUSTODY_NOT_PROVEN,
                    "Destination travel cannot continue without proven Material Handling custody");
            employee.clearCarryObservation(assignment.revision() + 1L);
            return;
        }
        if (!currentEndpoint(level, assignment.destination())) {
            reservationService.invalidateByEmployee(
                    level.getServer(),
                    assignment.employeeId(),
                    "Grinder endpoint was removed or replaced while Material Handling retained custody"
            );
            recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.DESTINATION_ENDPOINT_REPLACED,
                    "Grinder endpoint was removed or replaced while custody remained proven");
            return;
        }
        Optional<WorkstationReservationRecord> reservation = reservationService.managerFor(level.getServer())
                .findByEmployee(assignment.employeeId().value());
        if (reservation.isEmpty() || !isDestinationReservation(reservation.orElseThrow(), assignment)) {
            if (!retryReservation(level, runtime, assignment, position(assignment.destination()), false)) {
                return;
            }
            reservation = reservationService.managerFor(level.getServer())
                    .findByEmployee(assignment.employeeId().value());
        }
        if (reservation.isEmpty()) {
            return;
        }
        WorkstationReservationRecord activeReservation = reservation.orElseThrow();
        if (assignment.state() == EmployeeMaterialHandlingAssignmentState.WAITING_FOR_DESTINATION_RESERVATION) {
            assignment = transition(runtime, assignment,
                    EmployeeMaterialHandlingAssignmentState.CARRYING_TO_DESTINATION, Optional.empty());
        }
        if (activeReservation.state() != WorkstationReservationState.EMPLOYEE_ARRIVED
                || !reservationService.isWithinOperatingTolerance(level, activeReservation, employee.position())) {
            return;
        }
        EmployeeMaterialHandlingAssignment requested = transition(
                runtime,
                assignment,
                EmployeeMaterialHandlingAssignmentState.DEPOSIT_REQUESTED,
                Optional.empty()
        );
        MaterialHandlingTransferResult result = materialHandlingService.depositFromCustody(
                level,
                requested.transferId()
        );
        observeDeposit(level, runtime, requested, result.transfer().orElse(transfer), employee);
    }

    private void observeDeposit(
            ServerLevel level,
            ActiveAssignments runtime,
            EmployeeMaterialHandlingAssignment assignment,
            MaterialTransferRecord transfer,
            EmployeeEntity employee
    ) {
        MaterialTransferRecord current = materialHandlingService.findTransfer(level.getServer(), assignment.transferId())
                .orElse(transfer);
        if (current.lifecycle() == MaterialTransferLifecycle.COMPLETED) {
            EmployeeMaterialHandlingAssignment completed = complete(runtime, assignment);
            employee.clearCarryObservation(completed.revision());
            return;
        }
        if (current.lifecycle() == MaterialTransferLifecycle.DESTINATION_DEPOSIT_PREPARED) {
            recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.RECOVERY_REQUIRED,
                    "Prepared destination effect lacks proven owner result; operator reconciliation is required");
            return;
        }
        if (current.lifecycle() == MaterialTransferLifecycle.RECOVERY_REQUIRED) {
            recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.RECOVERY_REQUIRED,
                    current.terminalDetail().orElse("Destination deposit requires recovery"));
            return;
        }
        if (current.lifecycle() == MaterialTransferLifecycle.UNKNOWN_OUTCOME) {
            recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.UNKNOWN_OUTCOME,
                    current.terminalDetail().orElse("Destination deposit has Unknown Outcome"));
        } else if (current.lifecycle() == MaterialTransferLifecycle.FAILED) {
            transition(runtime, assignment, EmployeeMaterialHandlingAssignmentState.FAILED,
                    failure(EmployeeMaterialHandlingFailureCode.DEPOSIT_REJECTED,
                            current.terminalDetail().orElse("Destination deposit was rejected")));
        }
    }

    private void advanceCancellation(
            ServerLevel level,
            ActiveAssignments runtime,
            EmployeeMaterialHandlingAssignment assignment,
            MaterialTransferRecord transfer,
            EmployeeEntity employee
    ) {
        if (transfer.lifecycle() == MaterialTransferLifecycle.COMPLETED) {
            EmployeeMaterialHandlingAssignment completed = complete(runtime, assignment);
            employee.clearCarryObservation(completed.revision());
            return;
        }
        if (!transfer.hasProvenMaterialHandlingCustody()) {
            EmployeeMaterialHandlingAssignment cancelled = cancelBeforeCustody(level, runtime, assignment, transfer);
            employee.clearCarryObservation(cancelled.revision());
            return;
        }
        refreshCarry(employee, assignment, transfer);
        if (!currentEndpoint(level, assignment.source())) {
            reservationService.invalidateByEmployee(
                    level.getServer(),
                    assignment.employeeId(),
                    "Cutting Table endpoint was removed or replaced during cancellation"
            );
            recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.SOURCE_ENDPOINT_REPLACED,
                    "Cutting Table endpoint was removed or replaced while custody remained proven");
            return;
        }
        Optional<WorkstationReservationRecord> reservation = reservationService.managerFor(level.getServer())
                .findByEmployee(assignment.employeeId().value());
        if (reservation.isPresent() && !isSourceReservation(reservation.orElseThrow(), assignment)) {
            reservationService.release(level.getServer(), assignment.employeeId(),
                    "Cancellation returning custody to source");
            reservation = Optional.empty();
        }
        if (reservation.isEmpty()) {
            WorkstationReservationResult<WorkstationReservationRecord> source = reservationService.assign(
                    level,
                    assignment.employeeId(),
                    position(assignment.source())
            );
            if (!source.succeeded()) {
                return;
            }
            reservation = source.value();
            employeeService.synchronizeEntity(employee);
        }
        WorkstationReservationRecord sourceReservation = reservation.orElseThrow();
        if (!isSourceReservation(sourceReservation, assignment)
                || sourceReservation.state() != WorkstationReservationState.EMPLOYEE_ARRIVED
                || !reservationService.isWithinOperatingTolerance(level, sourceReservation, employee.position())) {
            return;
        }
        MaterialHandlingTransferResult cancelled = materialHandlingService.cancel(
                level,
                transfer.transferId(),
                assignment.failure().map(EmployeeMaterialHandlingFailure::detail).orElse("Employee transfer cancelled")
        );
        MaterialTransferRecord current = cancelled.transfer().orElse(transfer);
        if (cancelled.succeeded() && current.lifecycle() == MaterialTransferLifecycle.CANCELLED) {
            reservationService.release(level.getServer(), assignment.employeeId(), "Material returned to Cutting Table");
            EmployeeMaterialHandlingAssignment terminal = transition(
                    runtime,
                    assignment,
                    EmployeeMaterialHandlingAssignmentState.CANCELLED,
                    Optional.empty()
            );
            employee.clearCarryObservation(terminal.revision());
            return;
        }
        if (current.lifecycle() == MaterialTransferLifecycle.UNKNOWN_OUTCOME) {
            recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.UNKNOWN_OUTCOME,
                    current.terminalDetail().orElse("Source return has Unknown Outcome"));
        } else if (current.lifecycle() == MaterialTransferLifecycle.RECOVERY_REQUIRED) {
            recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.RECOVERY_REQUIRED,
                    current.terminalDetail().orElse("Source return requires recovery"));
        }
    }

    private EmployeeMaterialHandlingAssignment cancelBeforeCustody(
            ServerLevel level,
            ActiveAssignments runtime,
            EmployeeMaterialHandlingAssignment assignment,
            MaterialTransferRecord transfer
    ) {
        MaterialHandlingTransferResult cancelled = materialHandlingService.cancel(
                level,
                transfer.transferId(),
                assignment.failure().map(EmployeeMaterialHandlingFailure::detail).orElse("Employee transfer cancelled")
        );
        reservationService.managerFor(level.getServer()).findByEmployee(assignment.employeeId().value())
                .ifPresent(ignored -> reservationService.release(
                        level.getServer(), assignment.employeeId(), "Employee transfer cancelled before custody"));
        if (cancelled.succeeded()
                && cancelled.transfer().filter(value -> value.lifecycle() == MaterialTransferLifecycle.CANCELLED).isPresent()) {
            return transition(runtime, assignment, EmployeeMaterialHandlingAssignmentState.CANCELLED, Optional.empty());
        }
        return recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.CANCELLATION_FAILED,
                cancelled.detail());
    }

    private boolean retryReservation(
            ServerLevel level,
            ActiveAssignments runtime,
            EmployeeMaterialHandlingAssignment assignment,
            BlockPos position,
            boolean source
    ) {
        WaitState wait = runtime.waitStates().computeIfAbsent(
                assignment.assignmentId(),
                ignored -> new WaitState(0, 0L)
        );
        long gameTime = level.getGameTime();
        if (gameTime < wait.nextAttemptTick()) {
            return false;
        }
        if (wait.attempts() >= MAX_RESERVATION_WAIT_ATTEMPTS) {
            if (source) {
                materialHandlingService.findTransfer(level.getServer(), assignment.transferId())
                        .ifPresent(transfer -> failBeforeCustodyOrRecover(
                                level, runtime, assignment, transfer,
                                EmployeeMaterialHandlingFailureCode.SOURCE_RESERVATION_FAILED,
                                "Source reservation wait exhausted"
                        ));
            } else {
                recovery(runtime, assignment,
                        EmployeeMaterialHandlingFailureCode.DESTINATION_RESERVATION_FAILED,
                        "Destination reservation wait exhausted while custody remained proven");
            }
            runtime.waitStates().remove(assignment.assignmentId());
            return false;
        }
        runtime.waitStates().put(
                assignment.assignmentId(),
                new WaitState(wait.attempts() + 1, gameTime + RESERVATION_RETRY_INTERVAL_TICKS)
        );
        WorkstationReservationResult<WorkstationReservationRecord> result = reservationService.assign(
                level,
                assignment.employeeId(),
                position
        );
        if (!result.succeeded()) {
            EmployeeMaterialHandlingAssignmentState waiting = source
                    ? EmployeeMaterialHandlingAssignmentState.WAITING_FOR_SOURCE_RESERVATION
                    : EmployeeMaterialHandlingAssignmentState.WAITING_FOR_DESTINATION_RESERVATION;
            if (assignment.state() != waiting) {
                transition(runtime, assignment, waiting,
                        failure(source
                                        ? EmployeeMaterialHandlingFailureCode.SOURCE_RESERVATION_FAILED
                                        : EmployeeMaterialHandlingFailureCode.DESTINATION_RESERVATION_FAILED,
                                result.failure().orElseThrow().detail()));
            }
            return false;
        }
        runtime.waitStates().remove(assignment.assignmentId());
        if (source && assignment.state() == EmployeeMaterialHandlingAssignmentState.WAITING_FOR_SOURCE_RESERVATION) {
            transition(runtime, assignment, EmployeeMaterialHandlingAssignmentState.WALKING_TO_SOURCE, Optional.empty());
        }
        return true;
    }

    private void handleUnavailable(
            ServerLevel level,
            ActiveAssignments runtime,
            EmployeeMaterialHandlingAssignment assignment,
            MaterialTransferRecord transfer,
            Availability availability
    ) {
        reservationService.invalidateByEmployee(level.getServer(), assignment.employeeId(), availability.detail());
        EmployeeMaterialHandlingFailureCode code = switch (availability.status()) {
            case PLANT_CLOSED -> EmployeeMaterialHandlingFailureCode.PLANT_CLOSED;
            case EMPLOYEE_OFF_SHIFT -> EmployeeMaterialHandlingFailureCode.EMPLOYEE_OFF_SHIFT;
            default -> EmployeeMaterialHandlingFailureCode.EMPLOYEE_UNAVAILABLE;
        };
        if (transfer.hasProvenMaterialHandlingCustody()) {
            recovery(runtime, assignment, code,
                    availability.detail() + "; Material Handling custody remains proven");
        } else {
            materialHandlingService.cancel(level, transfer.transferId(), availability.detail());
            transition(runtime, assignment, EmployeeMaterialHandlingAssignmentState.FAILED,
                    failure(code, availability.detail()));
        }
    }

    private void failBeforeCustodyOrRecover(
            ServerLevel level,
            ActiveAssignments runtime,
            EmployeeMaterialHandlingAssignment assignment,
            MaterialTransferRecord transfer,
            EmployeeMaterialHandlingFailureCode code,
            String detail
    ) {
        reservationService.invalidateByEmployee(level.getServer(), assignment.employeeId(), detail);
        if (transfer.hasProvenMaterialHandlingCustody()) {
            recovery(runtime, assignment, code, detail);
        } else {
            materialHandlingService.cancel(level, transfer.transferId(), detail);
            transition(runtime, assignment, EmployeeMaterialHandlingAssignmentState.FAILED, failure(code, detail));
        }
    }

    private EmployeeMaterialHandlingAssignment complete(
            ActiveAssignments runtime,
            EmployeeMaterialHandlingAssignment assignment
    ) {
        if (assignment.state() == EmployeeMaterialHandlingAssignmentState.COMPLETED) {
            return assignment;
        }
        return transition(runtime, assignment, EmployeeMaterialHandlingAssignmentState.COMPLETED, Optional.empty());
    }

    private EmployeeMaterialHandlingAssignment recovery(
            ActiveAssignments runtime,
            EmployeeMaterialHandlingAssignment assignment,
            EmployeeMaterialHandlingFailureCode code,
            String detail
    ) {
        if (assignment.state() == EmployeeMaterialHandlingAssignmentState.RECOVERY_REQUIRED
                && assignment.failure().map(EmployeeMaterialHandlingFailure::code).filter(code::equals).isPresent()
                && assignment.failure().map(EmployeeMaterialHandlingFailure::detail).filter(detail::equals).isPresent()) {
            return assignment;
        }
        return transition(runtime, assignment, EmployeeMaterialHandlingAssignmentState.RECOVERY_REQUIRED,
                failure(code, detail));
    }

    private EmployeeMaterialHandlingAssignment transition(
            ActiveAssignments runtime,
            EmployeeMaterialHandlingAssignment assignment,
            EmployeeMaterialHandlingAssignmentState state,
            Optional<EmployeeMaterialHandlingFailure> failure
    ) {
        EmployeeMaterialHandlingAssignment updated = runtime.manager().transition(
                assignment.assignmentId(),
                state,
                failure
        );
        runtime.storage().save(runtime.manager().directory());
        return updated;
    }

    private void refreshCarry(
            EmployeeEntity employee,
            EmployeeMaterialHandlingAssignment assignment,
            MaterialTransferRecord transfer
    ) {
        Optional<ItemStack> display = materialHandlingService.carryDisplayStack(
                employee.level().getServer(),
                assignment.transferId()
        );
        if (display.isPresent() && transfer.hasProvenMaterialHandlingCustody()) {
            employee.applyCarryObservation(
                    assignment.transferId().value(),
                    display.orElseThrow(),
                    transfer.lifecycle().name().toLowerCase(java.util.Locale.ROOT),
                    assignment.revision()
            );
        } else {
            employee.clearCarryObservation(assignment.revision());
        }
    }

    private Availability availability(ServerLevel level, EmployeeId employeeId) {
        EmployeeRecord record = employeeService.managerFor(level.getServer()).find(employeeId).orElse(null);
        if (record == null) {
            return Availability.unavailable(AssignmentStatus.EMPLOYEE_UNAVAILABLE, "Employee not found");
        }
        if (record.status() != EmployeeStatus.ACTIVE) {
            return Availability.unavailable(AssignmentStatus.EMPLOYEE_UNAVAILABLE, "Employee is not active");
        }
        EmployeePresenceObservation observation = employeeService.observe(level.getServer(), employeeId)
                .value().orElse(null);
        if (observation == null) {
            return Availability.unavailable(AssignmentStatus.EMPLOYEE_UNAVAILABLE,
                    "Employee presence observation is unavailable");
        }
        if (!observation.plantOpen()) {
            return Availability.unavailable(AssignmentStatus.PLANT_CLOSED, "Plant is closed");
        }
        if (observation.presenceState() != EmployeePresenceState.PRESENT) {
            return Availability.unavailable(AssignmentStatus.EMPLOYEE_UNAVAILABLE, "Employee is not present");
        }
        if (observation.assignedDepartmentId().isEmpty()) {
            return Availability.unavailable(AssignmentStatus.EMPLOYEE_UNAVAILABLE,
                    "Employee has no assigned department");
        }
        if (observation.activeShiftIdentity().isEmpty()) {
            return Availability.unavailable(AssignmentStatus.EMPLOYEE_OFF_SHIFT,
                    "Employee is not assigned to the active shift");
        }
        if (record.entityLink().isEmpty()
                || !record.entityLink().orElseThrow().dimensionIdentity().equals(EmployeeService.dimensionIdentity(level))
                || entity(level, record).isEmpty()) {
            return Availability.unavailable(AssignmentStatus.EMPLOYEE_UNAVAILABLE,
                    "Employee entity is not present in this dimension");
        }
        return Availability.available(record, observation);
    }

    private boolean currentEndpoint(ServerLevel level, WorkstationEndpointReference expected) {
        WorkstationEndpointReferenceResult current = endpointService.referenceFor(level, position(expected));
        return current.succeeded() && current.reference().filter(expected::equals).isPresent();
    }

    private static boolean isSourceReservation(
            WorkstationReservationRecord reservation,
            EmployeeMaterialHandlingAssignment assignment
    ) {
        return reservation.workstationType().equals("cutting_table")
                && reservation.workstationIdentity().equals(assignment.source().instanceId().value());
    }

    private static boolean isDestinationReservation(
            WorkstationReservationRecord reservation,
            EmployeeMaterialHandlingAssignment assignment
    ) {
        WorkstationEndpointReference destination = assignment.destination();
        return reservation.workstationType().equals("grinder")
                && reservation.dimensionIdentity().equals(destination.endpointKey().dimensionIdentity())
                && reservation.workstationX() == destination.endpointKey().x()
                && reservation.workstationY() == destination.endpointKey().y()
                && reservation.workstationZ() == destination.endpointKey().z();
    }

    private static BlockPos position(WorkstationEndpointReference reference) {
        return new BlockPos(
                reference.endpointKey().x(),
                reference.endpointKey().y(),
                reference.endpointKey().z()
        );
    }

    private static String formatEndpoint(WorkstationEndpointReference reference) {
        return reference.endpointKey().workstationTypeIdentity() + "@"
                + reference.endpointKey().dimensionIdentity() + ":"
                + reference.endpointKey().x() + ","
                + reference.endpointKey().y() + ","
                + reference.endpointKey().z() + "#" + reference.generation();
    }

    private static String requiredOperatorAction(
            EmployeeMaterialHandlingAssignment assignment,
            Optional<MaterialTransferRecord> transfer
    ) {
        if (transfer.filter(value -> value.lifecycle() == MaterialTransferLifecycle.UNKNOWN_OUTCOME).isPresent()) {
            return "reconcile_unknown_outcome";
        }
        return switch (assignment.state()) {
            case RECOVERY_REQUIRED -> transfer.filter(MaterialTransferRecord::hasProvenMaterialHandlingCustody)
                    .map(ignored -> "cancel_or_reconcile")
                    .orElse("reconcile");
            case WAITING_FOR_SOURCE_RESERVATION, WAITING_FOR_DESTINATION_RESERVATION -> "resolve_reservation_conflict";
            case FAILED -> "inspect_failure";
            default -> "none";
        };
    }

    private static Optional<EmployeeMaterialHandlingFailure> failure(
            EmployeeMaterialHandlingFailureCode code,
            String detail
    ) {
        return Optional.of(new EmployeeMaterialHandlingFailure(code, detail));
    }

    private AssignmentResult materialFailure(
            Optional<EmployeeMaterialHandlingAssignment> assignment,
            MaterialHandlingTransferResult result
    ) {
        MaterialTransferLifecycle lifecycle = result.transfer().map(MaterialTransferRecord::lifecycle).orElse(null);
        if (lifecycle == MaterialTransferLifecycle.UNKNOWN_OUTCOME) {
            return AssignmentResult.rejected(AssignmentStatus.UNKNOWN_OUTCOME, assignment, result.detail());
        }
        if (lifecycle == MaterialTransferLifecycle.RECOVERY_REQUIRED) {
            return AssignmentResult.rejected(AssignmentStatus.RECOVERY_REQUIRED, assignment, result.detail());
        }
        return AssignmentResult.rejected(AssignmentStatus.TRANSFER_REJECTED, assignment, result.detail());
    }

    private Optional<EmployeeEntity> employeeFor(ServerLevel level, EmployeeId employeeId) {
        return employeeService.managerFor(level.getServer()).find(employeeId).flatMap(record -> entity(level, record));
    }

    private static Optional<EmployeeEntity> entity(ServerLevel level, EmployeeRecord record) {
        Optional<EmployeeEntityLink> link = record.entityLink();
        if (link.isEmpty()) {
            return Optional.empty();
        }
        Entity entity = level.getEntity(link.orElseThrow().entityUuid());
        return entity instanceof EmployeeEntity employee && !employee.isRemoved()
                ? Optional.of(employee)
                : Optional.empty();
    }

    private static void requireGameTestServer(MinecraftServer server) {
        String className = Objects.requireNonNull(server, "server").getClass().getName();
        if (!className.contains("GameTestServer")) {
            throw new IllegalStateException("Employee Material Handling GameTest helper requires GameTestServer");
        }
    }

    private ActiveAssignments load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveAssignments existing = active.get();
        if (existing != null && existing.server() == server) {
            return existing;
        }
        if (existing != null) {
            existing.storage().save(existing.manager().directory());
        }
        EmployeeMaterialHandlingAssignmentStorage storage =
                new EmployeeMaterialHandlingAssignmentStorage(assignmentFile(server));
        ActiveAssignments created = new ActiveAssignments(
                server,
                storage,
                new EmployeeMaterialHandlingAssignmentManager(storage.load()),
                new HashMap<>()
        );
        active.set(created);
        return created;
    }

    private void reconcileLoadedAssignments(MinecraftServer server, ActiveAssignments runtime) {
        for (EmployeeMaterialHandlingAssignment assignment : runtime.manager().assignments()) {
            if (!assignment.active()) {
                continue;
            }
            MaterialTransferRecord transfer = materialHandlingService.findTransfer(server, assignment.transferId())
                    .orElse(null);
            if (transfer == null || !transfer.source().equals(assignment.source())
                    || !transfer.destination().equals(assignment.destination())
                    || transfer.employeeReference().filter(assignment.employeeId().value()::equals).isEmpty()) {
                recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.RECOVERY_REQUIRED,
                        "Persisted assignment does not match an authoritative Material Transfer");
                continue;
            }
            if (transfer.lifecycle() == MaterialTransferLifecycle.COMPLETED) {
                complete(runtime, assignment);
            } else if (transfer.lifecycle() == MaterialTransferLifecycle.CANCELLED) {
                transition(runtime, assignment, EmployeeMaterialHandlingAssignmentState.CANCELLED, Optional.empty());
            } else if (transfer.lifecycle() == MaterialTransferLifecycle.UNKNOWN_OUTCOME) {
                recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.UNKNOWN_OUTCOME,
                        "Material Transfer has Unknown Outcome after restart");
            } else if ((assignment.state() == EmployeeMaterialHandlingAssignmentState.WITHDRAWAL_REQUESTED
                    && transfer.lifecycle() == MaterialTransferLifecycle.SOURCE_WITHDRAW_PREPARED)
                    || (assignment.state() == EmployeeMaterialHandlingAssignmentState.DEPOSIT_REQUESTED
                    && transfer.lifecycle() == MaterialTransferLifecycle.DESTINATION_DEPOSIT_PREPARED)) {
                recovery(runtime, assignment, EmployeeMaterialHandlingFailureCode.RECOVERY_REQUIRED,
                        "Prepared endpoint effect requires owner-result reconciliation before movement resumes");
            } else if (transfer.hasProvenMaterialHandlingCustody()
                    && assignment.state() != EmployeeMaterialHandlingAssignmentState.CANCELLATION_REQUESTED
                    && assignment.state() != EmployeeMaterialHandlingAssignmentState.CARRYING_TO_DESTINATION
                    && assignment.state() != EmployeeMaterialHandlingAssignmentState.WAITING_FOR_DESTINATION_RESERVATION
                    && assignment.state() != EmployeeMaterialHandlingAssignmentState.DEPOSIT_REQUESTED) {
                transition(runtime, assignment,
                        EmployeeMaterialHandlingAssignmentState.CARRYING_TO_DESTINATION, Optional.empty());
            }
        }
    }

    private record ActiveAssignments(
            MinecraftServer server,
            EmployeeMaterialHandlingAssignmentStorage storage,
            EmployeeMaterialHandlingAssignmentManager manager,
            Map<EmployeeMaterialHandlingAssignmentId, WaitState> waitStates
    ) {
        private ActiveAssignments {
            server = Objects.requireNonNull(server, "server");
            storage = Objects.requireNonNull(storage, "storage");
            manager = Objects.requireNonNull(manager, "manager");
            waitStates = Objects.requireNonNull(waitStates, "waitStates");
        }
    }

    private record WaitState(int attempts, long nextAttemptTick) {
    }

    private record Availability(
            boolean available,
            AssignmentStatus status,
            String detail,
            Optional<EmployeeRecord> record,
            Optional<EmployeePresenceObservation> observation
    ) {
        static Availability available(EmployeeRecord record, EmployeePresenceObservation observation) {
            return new Availability(true, AssignmentStatus.ASSIGNMENT_ACCEPTED, "available",
                    Optional.of(record), Optional.of(observation));
        }

        static Availability unavailable(AssignmentStatus status, String detail) {
            return new Availability(false, status, detail, Optional.empty(), Optional.empty());
        }
    }

    public record AssignmentResult(
            AssignmentStatus status,
            Optional<EmployeeMaterialHandlingAssignment> assignment,
            String detail
    ) {
        public AssignmentResult {
            status = Objects.requireNonNull(status, "status");
            assignment = Objects.requireNonNull(assignment, "assignment");
            detail = Objects.requireNonNull(detail, "detail");
        }

        static AssignmentResult accepted(EmployeeMaterialHandlingAssignment assignment) {
            return new AssignmentResult(AssignmentStatus.ASSIGNMENT_ACCEPTED, Optional.of(assignment),
                    "Employee transfer assignment accepted");
        }

        static AssignmentResult observed(EmployeeMaterialHandlingAssignment assignment) {
            return new AssignmentResult(AssignmentStatus.EXISTING_IDENTICAL_ASSIGNMENT, Optional.of(assignment),
                    "Existing identical employee transfer assignment observed");
        }

        static AssignmentResult cancelled(EmployeeMaterialHandlingAssignment assignment, String detail) {
            return new AssignmentResult(AssignmentStatus.CANCELLED, Optional.of(assignment), detail);
        }

        static AssignmentResult completed(EmployeeMaterialHandlingAssignment assignment, String detail) {
            return new AssignmentResult(AssignmentStatus.COMPLETED, Optional.of(assignment), detail);
        }

        static AssignmentResult cancellationRequested(
                EmployeeMaterialHandlingAssignment assignment,
                String detail
        ) {
            return new AssignmentResult(
                    AssignmentStatus.CANCELLATION_REQUESTED,
                    Optional.of(assignment),
                    detail.isBlank() ? "Employee transfer cancellation requested" : detail
            );
        }

        static AssignmentResult rejected(
                AssignmentStatus status,
                Optional<EmployeeMaterialHandlingAssignment> assignment,
                String detail
        ) {
            return new AssignmentResult(status, assignment, detail);
        }

        public boolean accepted() {
            return status == AssignmentStatus.ASSIGNMENT_ACCEPTED
                    || status == AssignmentStatus.EXISTING_IDENTICAL_ASSIGNMENT;
        }
    }

    public enum AssignmentStatus {
        ASSIGNMENT_ACCEPTED,
        EXISTING_IDENTICAL_ASSIGNMENT,
        EMPLOYEE_UNAVAILABLE,
        EMPLOYEE_OFF_SHIFT,
        PLANT_CLOSED,
        ASSIGNMENT_CONFLICT,
        ASSIGNMENT_NOT_FOUND,
        INVALID_SOURCE,
        INVALID_DESTINATION,
        SOURCE_EMPTY,
        RESERVATION_CONFLICT,
        TRANSFER_REJECTED,
        RECOVERY_REQUIRED,
        UNKNOWN_OUTCOME,
        CANCELLATION_REQUESTED,
        CANCELLED,
        COMPLETED
    }

    public record TransferDiagnostics(
            String assignmentIdentity,
            String transferIdentity,
            String assignmentState,
            String materialHandlingLifecycle,
            String source,
            String destination,
            String activeReservation,
            String navigationTarget,
            String custodyLocation,
            String displayedItem,
            long displayRevision,
            String failure,
            String requiredOperatorAction
    ) {
        static TransferDiagnostics none(EmployeeId employeeId) {
            return new TransferDiagnostics(
                    "none", "none", "idle", "none", "none", "none", "none", "none",
                    "unproven", "none", 0L, "none", "none"
            );
        }
    }
}
