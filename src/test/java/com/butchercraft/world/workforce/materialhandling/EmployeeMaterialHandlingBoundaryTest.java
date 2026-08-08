package com.butchercraft.world.workforce.materialhandling;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeMaterialHandlingBoundaryTest {
    @Test
    void workforceCoordinatorUsesMaterialHandlingAndWorkstationPublicAuthorities() throws IOException {
        String service = source("src/main/java/com/butchercraft/world/EmployeeMaterialHandlingService.java");

        assertTrue(service.contains("materialHandlingService.requestEmployeeTransfer"));
        assertTrue(service.contains("materialHandlingService.withdrawToCustody"));
        assertTrue(service.contains("materialHandlingService.depositFromCustody"));
        assertTrue(service.contains("materialHandlingService.cancel"));
        assertTrue(service.contains("reservationService.assign"));
        assertTrue(service.contains("reservationService.release"));
        assertFalse(service.contains(".insertItem("));
        assertFalse(service.contains(".extractItem("));
        assertFalse(service.contains("setInputInternal"));
        assertFalse(service.contains("setOutputInternal"));
        assertFalse(service.contains("commitPrepared"));
        assertFalse(service.contains("ExecutionService"));
        assertFalse(service.contains("SimulationSchedulerService"));
        assertFalse(service.contains("ProductionService"));
        assertFalse(service.contains("InventoryService"));
        assertFalse(service.contains("PattyFormer"));
    }

    @Test
    void workforcePersistenceContainsNoStackOrRendererAuthority() throws IOException {
        String storage = source("src/main/java/com/butchercraft/world/workforce/materialhandling/persistence/"
                + "EmployeeMaterialHandlingAssignmentStorage.java");

        assertFalse(storage.contains("ItemStack"));
        assertFalse(storage.contains("encoded_stack"));
        assertFalse(storage.contains("in_transit_custody"));
        assertFalse(storage.contains("path_nodes"));
        assertFalse(storage.contains("renderer"));
        assertTrue(storage.contains("transfer_identity"));
        assertTrue(storage.contains("source"));
        assertTrue(storage.contains("destination"));
    }

    @Test
    void carryViewUsesVanillaTrackedDataAndCannotMutateAuthority() throws IOException {
        String entity = source("src/main/java/com/butchercraft/entity/employee/EmployeeEntity.java");
        String renderer = source("src/main/java/com/butchercraft/client/renderer/EmployeeRenderer.java");

        assertTrue(entity.contains("EntityDataSerializers.ITEM_STACK"));
        assertTrue(entity.contains("applyCarryObservation"));
        assertTrue(entity.contains("observationRevision < currentRevision"));
        assertTrue(entity.contains("observationRevision == currentRevision"));
        assertTrue(entity.contains("displayStack.getCount() != 1"));
        assertTrue(renderer.contains("ItemInHandLayer"));
        assertFalse(entity.contains("TAG_CARRIED_ITEM"));
        assertFalse(entity.contains("IItemHandler"));
        assertFalse(renderer.contains("MaterialHandlingService"));
        assertFalse(renderer.contains("WorkstationEndpointService"));
    }

    @Test
    void transferCommandsUseOnlySynchronizedBuiltInArguments() throws IOException {
        String command = source("src/main/java/com/butchercraft/command/ButcherCraftDiagnostics.java");

        assertTrue(command.contains("Commands.literal(\"transfer\")"));
        assertTrue(command.contains("Commands.literal(\"transfer-status\")"));
        assertTrue(command.contains("Commands.literal(\"transfer-cancel\")"));
        assertTrue(command.contains("Commands.literal(\"preload-cutting-table-output\")"));
        assertTrue(command.contains("BlockPosArgument.blockPos()"));
        assertTrue(command.contains("preloadOutputForDevelopment"));
        assertTrue(command.contains("StringArgumentType.greedyString()"));
        assertTrue(command.contains("EmployeeMaterialHandlingService.INSTANCE.request"));
        assertFalse(command.contains("EmployeeReferenceArgumentType"));
        assertFalse(command.contains("ArgumentType<"));
        assertFalse(command.contains(".insertItem("));
        assertFalse(command.contains(".extractItem("));
    }

    @Test
    void startupRegistrationPreservesReconciliationOrder() throws IOException {
        String mod = source("src/main/java/com/butchercraft/ButcherCraft.java");

        int world = mod.indexOf("WorldIdentityService.INSTANCE::initialize");
        int workstation = mod.indexOf("WorkstationEndpointService.INSTANCE::initialize");
        int material = mod.indexOf("MaterialHandlingService.INSTANCE::initialize");
        int employee = mod.indexOf("EmployeeService.INSTANCE::initialize");
        int reservation = mod.indexOf("WorkstationReservationService.INSTANCE::initialize");
        int assignment = mod.indexOf("EmployeeMaterialHandlingService.INSTANCE::initialize");

        assertTrue(world >= 0 && world < workstation);
        assertTrue(workstation < material);
        assertTrue(material < employee);
        assertTrue(employee < reservation);
        assertTrue(reservation < assignment);
    }

    private static String source(String path) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(path));
    }
}
