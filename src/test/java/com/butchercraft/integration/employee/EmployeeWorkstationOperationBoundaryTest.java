package com.butchercraft.integration.employee;

import com.butchercraft.test.TestProjectPaths;
import com.butchercraft.world.execution.ExecutionStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeWorkstationOperationBoundaryTest {
    @Test
    void employeeCoordinatorRequestsOwnerProcessingWithoutPrivateMutationAuthority() throws IOException {
        String service = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/integration/employee/EmployeeWorkstationOperationService.java"
        ));

        assertTrue(service.contains("requestEmployeeProcessing"));
        assertTrue(service.contains("ExecutionService.INSTANCE"));
        assertTrue(service.contains("ownerResultEvidence"));
        assertFalse(service.contains("ExecutionAuthorization"));
        assertFalse(service.contains("SimulationSchedulerService"));
        assertFalse(service.contains("ProductionService"));
        assertFalse(service.contains("InventoryService"));
        assertFalse(service.contains("PattyFormer"));
        assertFalse(service.contains(".insertItem("));
        assertFalse(service.contains("setInputInternal"));
        assertFalse(service.contains("setOutputInternal"));
        assertFalse(service.contains("clearInputInternal"));
        assertFalse(service.contains("clearInputsInternal"));
    }

    @Test
    void operatorCommandUsesThePublicCoordinatorAndSynchronizedArgumentType() throws IOException {
        String command = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/command/ButcherCraftDiagnostics.java"
        ));

        assertTrue(command.contains("Commands.literal(\"operate\")"));
        assertTrue(command.contains("StringArgumentType.greedyString()"));
        assertTrue(command.contains("EmployeeWorkstationOperationService.INSTANCE.request"));
        assertFalse(command.contains("ExecutionAuthorization"));
        assertFalse(command.contains("SimulationSchedulerService"));
        assertFalse(command.contains("requestEmployeeProcessing"));
        assertFalse(command.contains("setInputInternal"));
        assertFalse(command.contains("setOutputInternal"));
    }

    @Test
    void grinderEmployeeRequestDelegatesToExistingControllerPipeline() throws IOException {
        String grinder = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/machine/grinder/GrinderBlockEntity.java"
        ));
        String entity = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/entity/employee/EmployeeEntity.java"
        ));

        assertTrue(grinder.contains("requestEmployeeProcessing"));
        assertTrue(grinder.contains("return requestProductionProcessing(tickContext);"));
        assertTrue(entity.contains("EmployeeWorkstationOperationService.INSTANCE.tick(this)"));
    }

    @Test
    void terminalExecutionOutcomesRemainExplicit() {
        assertTrue(EmployeeWorkstationOperationService.executionTerminalFailure(
                ExecutionStatus.UNKNOWN_OUTCOME).orElseThrow().equals("unknown_outcome"));
        assertTrue(EmployeeWorkstationOperationService.executionTerminalFailure(
                ExecutionStatus.REJECTED).orElseThrow().equals("execution_rejected"));
        assertTrue(EmployeeWorkstationOperationService.executionTerminalFailure(
                ExecutionStatus.FAILED).orElseThrow().equals("execution_failed"));
        assertTrue(EmployeeWorkstationOperationService.executionTerminalFailure(
                ExecutionStatus.CANCELLED_BEFORE_START).orElseThrow().equals("execution_failed"));
        assertTrue(EmployeeWorkstationOperationService.executionTerminalFailure(
                ExecutionStatus.RUNNING).isEmpty());
        assertTrue(EmployeeWorkstationOperationService.executionTerminalFailure(
                ExecutionStatus.SUCCEEDED).isEmpty());
    }
}
