package com.butchercraft.machine.pattyformer;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PattyFormerExplicitOperationBoundaryTest {
    @Test
    void pattyFormerUsesPersistentRunControlAndExistingExecutionCoordinator() throws IOException {
        String blockEntity = source(
                "src/main/java/com/butchercraft/machine/pattyformer/PattyFormerBlockEntity.java"
        );
        String integration = source(
                "src/main/java/com/butchercraft/integration/machine/pattyformer/PattyFormerContinuousRunService.java"
        );

        assertTrue(blockEntity.contains("WorkstationOperationStartPolicy.EXPLICIT_REQUEST"));
        assertTrue(blockEntity.contains("requestRunProcessing"));
        assertTrue(blockEntity.contains("PattyFormerContinuousRunService.INSTANCE"));
        assertTrue(integration.contains("PattyFormerExecutionCoordinator.INSTANCE"));
        assertTrue(integration.contains("PoweredProcessingMachineRunService<PattyFormerBlockEntity>"));
        String execution = source(
                "src/main/java/com/butchercraft/machine/pattyformer/execution/PattyFormerExecutionCoordinator.java"
        );
        assertTrue(execution.contains("WorldIdentityRootIdentities.from"));
        assertTrue(blockEntity.contains("implements WorkstationTransferEndpoint"));
        assertFalse(blockEntity.contains("ProductionManager"));
        assertFalse(blockEntity.contains("Employee"));
        assertFalse(blockEntity.contains("requestPlayerProcessing"));
    }

    @Test
    void materialPresenceAndEndpointDepositCannotCreateRunAuthority() throws IOException {
        String blockEntity = source(
                "src/main/java/com/butchercraft/machine/pattyformer/PattyFormerBlockEntity.java"
        );
        String integration = source(
                "src/main/java/com/butchercraft/integration/machine/pattyformer/PattyFormerContinuousRunService.java"
        );

        String endpoint = blockEntity.substring(
                blockEntity.indexOf("public boolean endpointAccepts("),
                blockEntity.indexOf("protected AbstractContainerMenu createWorkstationMenu")
        );
        assertFalse(endpoint.contains("startRun("));
        assertFalse(endpoint.contains("coordinator"));
        assertFalse(endpoint.contains("ExecutionService"));
        assertFalse(endpoint.contains("SimulationSchedulerService"));
        assertTrue(integration.contains("public PoweredMachineRunControlResult start("));
    }

    @Test
    void diagnosticsExposeRunPolicyIdentityChildAndEligibilityWithoutNewCommand() throws IOException {
        String diagnostics = source(
                "src/main/java/com/butchercraft/command/ButcherCraftDiagnostics.java"
        );

        assertTrue(diagnostics.contains("Patty Former machine: state="));
        assertTrue(diagnostics.contains("policy=powered_continuous_explicit_stop"));
        assertTrue(diagnostics.contains("Patty Former Run: identity="));
        assertTrue(diagnostics.contains("Patty Former child: active="));
        assertTrue(diagnostics.contains("input_eligible="));
        assertTrue(diagnostics.contains("output_blocked="));
        assertTrue(diagnostics.contains("Patty Former Run recovery: "));
    }

    @Test
    void employeeTransportCannotStartPattyFormerOperation() throws IOException {
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
        assertTrue(employeeTransport.contains("materialHandlingService.employeeRoute"));
        assertTrue(employeeTransport.contains("butchercraft:ground_beef"));
        assertFalse(employeeTransport.contains("requestEmployeeProcessing"));
        assertFalse(employeeTransport.contains("ExecutionService"));
        assertFalse(employeeTransport.contains("SimulationSchedulerService"));
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
