package com.butchercraft.world;

import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.checkpoint.StartupMutationGateService;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationAssignmentDirectory;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationAssignmentManager;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationAssignmentSchema;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationAssignmentState;
import com.butchercraft.world.workforce.machineoperation.persistence.EmployeeMachineOperationAssignmentStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class EmployeeMachineOperationAssignmentService {
    public static final EmployeeMachineOperationAssignmentService INSTANCE =
            new EmployeeMachineOperationAssignmentService();

    private final AtomicReference<ActiveAssignments> active = new AtomicReference<>();
    public static final String RUN_SOURCE_OWNER = "butchercraft:workforce_machine_operation";

    private EmployeeMachineOperationAssignmentService() {
    }

    public void initialize(ServerStartedEvent event) {
        managerFor(event.getServer());
    }

    public void save(ServerStoppingEvent event) {
        ActiveAssignments current = active.get();
        if (current != null && current.server() == event.getServer()) {
            current.storage().save(current.manager().directory());
            active.compareAndSet(current, null);
        }
    }

    public EmployeeMachineOperationAssignmentManager managerFor(MinecraftServer server) {
        return load(server).manager();
    }

    public Optional<EmployeeMachineOperationAssignmentManager> currentManager() {
        return Optional.ofNullable(active.get()).map(ActiveAssignments::manager);
    }

    public boolean hasActiveAssignment(
            MinecraftServer server,
            com.butchercraft.world.workforce.employee.EmployeeId employeeId
    ) {
        return managerFor(server).activeFor(employeeId).isPresent();
    }

    public boolean requestCancellation(
            MinecraftServer server,
            com.butchercraft.world.workforce.employee.EmployeeId employeeId,
            String reason
    ) {
        requireMutation(server);
        var assignment = managerFor(server).activeFor(employeeId).orElse(null);
        if (assignment == null) return false;
        if (assignment.state() == EmployeeMachineOperationAssignmentState.RECOVERY_REQUIRED) {
            return true;
        }
        if (assignment.state() != EmployeeMachineOperationAssignmentState.CANCELLATION_REQUESTED) {
            long tick = Math.max(assignment.lastUpdatedTick(), server.overworld().getGameTime());
            managerFor(server).publish(
                    assignment.assignmentId(), EmployeeMachineOperationAssignmentState.CANCELLATION_REQUESTED,
                    assignment.completedQuantity(), assignment.runIdentity(), assignment.reservationId(),
                    assignment.pendingSupplyTransferIdentity(), assignment.observedRunRevision(),
                    assignment.observedChildSequence(), Optional.empty(), tick);
            persist(server);
        }
        return true;
    }

    public void persist(MinecraftServer server) {
        requireMutation(server);
        ActiveAssignments runtime = load(server);
        runtime.storage().save(runtime.manager().directory());
    }

    public String checkpointSnapshotJson(MinecraftServer server) {
        ActiveAssignments runtime = load(server);
        return runtime.storage().serialize(runtime.manager().directory());
    }

    public static String startRequestIdentity(String assignmentIdentity) {
        return Objects.requireNonNull(assignmentIdentity, "assignmentIdentity") + "/start";
    }

    public static String stopRequestIdentity(String assignmentIdentity) {
        return Objects.requireNonNull(assignmentIdentity, "assignmentIdentity") + "/stop";
    }

    public void resetGameTestAssignments(MinecraftServer server) {
        requireGameTestServer(server);
        requireMutation(server);
        ActiveAssignments current = load(server);
        ActiveAssignments reset = new ActiveAssignments(
                server,
                current.storage(),
                new EmployeeMachineOperationAssignmentManager(EmployeeMachineOperationAssignmentDirectory.empty())
        );
        active.set(reset);
        reset.storage().save(reset.manager().directory());
    }

    public static Path assignmentFile(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                .resolve(EmployeeMachineOperationAssignmentSchema.DIRECTORY_NAME)
                .resolve(EmployeeMachineOperationAssignmentSchema.FILE_NAME)
                .toAbsolutePath().normalize();
    }

    private ActiveAssignments load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveAssignments existing = active.get();
        if (existing != null && existing.server() == server) return existing;
        if (existing != null) existing.storage().save(existing.manager().directory());
        EmployeeMachineOperationAssignmentStorage storage =
                new EmployeeMachineOperationAssignmentStorage(assignmentFile(server));
        ActiveAssignments created = new ActiveAssignments(
                server, storage, new EmployeeMachineOperationAssignmentManager(storage.load()));
        active.set(created);
        return created;
    }

    private static void requireMutation(MinecraftServer server) {
        StartupMutationGateService.INSTANCE.require(server, LegacySplitRecoveryParticipants.WORKFORCE);
    }

    private static void requireGameTestServer(MinecraftServer server) {
        if (!Objects.requireNonNull(server, "server").getClass().getName().contains("GameTestServer")) {
            throw new IllegalStateException("Employee machine-operation GameTest helpers require GameTestServer");
        }
    }

    private record ActiveAssignments(
            MinecraftServer server,
            EmployeeMachineOperationAssignmentStorage storage,
            EmployeeMachineOperationAssignmentManager manager
    ) {
    }
}
