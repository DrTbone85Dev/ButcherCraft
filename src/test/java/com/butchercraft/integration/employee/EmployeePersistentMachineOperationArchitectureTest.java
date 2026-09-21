package com.butchercraft.integration.employee;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeePersistentMachineOperationArchitectureTest {
    @Test
    void workforceCoordinatorUsesCanonicalOwnersWithoutPrivateMutation() throws IOException {
        String service = source(
                "src/main/java/com/butchercraft/integration/employee/EmployeePersistentMachineOperationService.java");

        assertTrue(service.contains("EmployeeMachineOperationAssignmentService.INSTANCE"));
        assertTrue(service.contains("WorkstationReservationService.INSTANCE"));
        assertTrue(service.contains(".assignMachineOperator("));
        assertTrue(service.contains("GrinderContinuousRunService.INSTANCE.startForEmployee"));
        assertTrue(service.contains("PattyFormerContinuousRunService.INSTANCE.startForEmployee"));
        assertTrue(service.contains("EmployeeMaterialHandlingService.INSTANCE"));
        assertFalse(service.contains("MachineRunManager"));
        assertFalse(service.contains("SimulationSchedulerService"));
        assertFalse(service.contains("ExecutionAuthorization"));
        assertFalse(service.contains("setInputInternal"));
        assertFalse(service.contains("setOutputInternal"));
        assertFalse(service.contains("extractItem"));
        assertFalse(service.contains("insertItem"));
    }

    @Test
    void replacementCancellationRequiresExactConsequenceFreeEvidence() throws IOException {
        String service = source(
                "src/main/java/com/butchercraft/integration/employee/EmployeePersistentMachineOperationService.java");
        String assignment = source(
                "src/main/java/com/butchercraft/world/workforce/machineoperation/"
                        + "EmployeeMachineOperationAssignment.java");
        String owner = source(
                "src/main/java/com/butchercraft/world/EmployeeMachineOperationAssignmentService.java");

        assertTrue(assignment.contains("isConsequenceFreeReplacementCancellationCandidate"));
        assertTrue(assignment.contains("EmployeeMachineOperationFailureCode.WORKSTATION_REPLACED"));
        assertTrue(service.contains("provesRetiredInstance(level.getServer(), assignment)"));
        assertTrue(service.contains("instance.lifecycle() == WorkstationInstanceLifecycle.RETIRED"));
        assertTrue(service.contains("runs.startForRequest("));
        assertTrue(service.contains("runs.stopForRequest("));
        assertTrue(service.contains("runs.activeFor(assignment.workstation().instanceId().value())"));
        assertTrue(service.contains("exactOrphanedReservationBinding"));
        assertTrue(service.contains("reservation.role() == WorkstationReservationRole.MACHINE_OPERATOR"));
        assertTrue(service.contains("reservation.workstationIdentity().equals(assignment.workstation().instanceId()"));
        assertTrue(owner.contains("EmployeeMachineOperationAssignmentState.RECOVERY_REQUIRED"));
    }

    @Test
    void finiteAdmissionGateBindsExactAssignmentReservationRecipeAndRun() throws IOException {
        String service = source(
                "src/main/java/com/butchercraft/integration/employee/EmployeePersistentMachineOperationService.java");

        assertTrue(service.contains("committedQuantity(run, assignment) < assignment.targetQuantity()"));
        assertTrue(service.contains("value.matches(assignment) && inputMatches(value)"));
        assertTrue(service.contains("WorkstationReservationState.EMPLOYEE_ARRIVED"));
        assertTrue(service.contains("workstation().instanceId().value().equals(run.workstationInstanceIdentity())"));
        assertTrue(service.contains("stopForEmployee"));
    }

    @Test
    void oneGenericSynchronizedCommandSupportsBothPoweredMachines() throws IOException {
        String commands = source("src/main/java/com/butchercraft/command/ButcherCraftDiagnostics.java");

        assertTrue(commands.contains("Commands.literal(\"operate\")"));
        assertTrue(commands.contains("StringArgumentType.greedyString()"));
        assertTrue(commands.contains("<employee> \""));
        assertTrue(commands.contains("<x> <y> <z> <quantity>"));
        assertTrue(commands.contains("EmployeePersistentMachineOperationService.INSTANCE.request"));
        assertFalse(commands.contains("EmployeeReferenceArgumentType"));
    }

    @Test
    void checkpointPlacesAssignmentOnlyInWorkforceOwnerState() throws IOException {
        String participants = source(
                "src/main/java/com/butchercraft/world/checkpoint/LiveCheckpointParticipantRegistry.java");
        String restoration = source(
                "src/main/java/com/butchercraft/integration/checkpoint/NativeOwnerRestorationAdapters.java");
        String liveCoherence = source(
                "src/main/java/com/butchercraft/integration/checkpoint/LiveOwnerCoherenceAnalyzer.java");
        String restoredCoherence = source(
                "src/main/java/com/butchercraft/integration/checkpoint/NativeOwnerLogicalStateVerifier.java");
        String validator = source(
                "src/main/java/com/butchercraft/integration/checkpoint/EmployeeMachineOperationCoherenceValidator.java");

        assertTrue(participants.contains("employee_machine_operation_assignments.json"));
        assertTrue(restoration.contains("employee_machine_operation_assignments.json"));
        assertTrue(restoration.contains("CURRENT_WORKFORCE_NATIVE_FILES"));
        assertTrue(liveCoherence.contains("EmployeeMachineOperationCoherenceValidator.validate"));
        assertTrue(restoredCoherence.contains("EmployeeMachineOperationCoherenceValidator.validate"));
        assertTrue(validator.contains("WorkstationReservationRole.MACHINE_OPERATOR"));
        assertTrue(validator.contains("startRequestIdentity"));
        assertTrue(validator.contains("completedQuantity() != completedQuantity"));
    }

    private static String source(String path) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(path));
    }
}
