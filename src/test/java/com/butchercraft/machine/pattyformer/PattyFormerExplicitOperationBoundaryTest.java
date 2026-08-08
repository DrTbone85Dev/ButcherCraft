package com.butchercraft.machine.pattyformer;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PattyFormerExplicitOperationBoundaryTest {
    @Test
    void pattyFormerUsesExplicitStartPolicyAndExistingExecutionCoordinator() throws IOException {
        String blockEntity = source(
                "src/main/java/com/butchercraft/machine/pattyformer/PattyFormerBlockEntity.java"
        );

        assertTrue(blockEntity.contains("WorkstationOperationStartPolicy.EXPLICIT_REQUEST"));
        assertTrue(blockEntity.contains("requestPlayerProcessing"));
        assertTrue(blockEntity.contains("PattyFormerExecutionCoordinator.INSTANCE"));
        assertTrue(blockEntity.contains("implements WorkstationTransferEndpoint"));
        assertFalse(blockEntity.contains("ProductionManager"));
        assertFalse(blockEntity.contains("Employee"));
    }

    @Test
    void employeeOperationAndTransportRemainOutsidePattyFormerScope() throws IOException {
        String employeeOperation = source(
                "src/main/java/com/butchercraft/integration/employee/EmployeeWorkstationOperationService.java"
        );
        String employeeTransport = source(
                "src/main/java/com/butchercraft/world/EmployeeMaterialHandlingService.java"
        );

        assertFalse(employeeOperation.contains("PattyFormer"));
        assertFalse(employeeOperation.contains("GROUND_BEEF"));
        assertFalse(employeeTransport.contains("PattyFormer"));
        assertFalse(employeeTransport.contains("GROUND_BEEF"));
        assertFalse(employeeTransport.contains("ground_beef"));
    }

    @Test
    void explicitGateDoesNotChangeExecutionHandlerCompatibilityContract() throws IOException {
        String constants = source(
                "src/main/java/com/butchercraft/machine/pattyformer/execution/PattyFormerExecutionConstants.java"
        );

        assertTrue(constants.contains(
                "butchercraft:execution_handler/patty_former_player_operation"
        ));
        assertTrue(constants.contains(
                "butchercraft:execution_configuration/patty_former_player_operation_v1"
        ));
    }

    private static String source(String path) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(path));
    }
}
