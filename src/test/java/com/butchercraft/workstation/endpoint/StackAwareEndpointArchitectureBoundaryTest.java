package com.butchercraft.workstation.endpoint;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StackAwareEndpointArchitectureBoundaryTest {
    @Test
    void schema2WorkstationServiceDoesNotDependOnCustodyWorkforceExecutionOrScheduler() throws IOException {
        String service = source("src/main/java/com/butchercraft/workstation/endpoint/runtime/"
                + "StackAwareWorkstationEndpointService.java");

        assertFalse(service.contains("world.materialhandling"));
        assertFalse(service.contains("world.workforce"));
        assertFalse(service.contains("world.execution"));
        assertFalse(service.contains("world.simulation.scheduler"));
        assertTrue(service.contains("WorkstationStackMutationPlan"));
        assertTrue(service.contains("StackAwareEndpointObservationResult"));
        assertTrue(service.contains("StackAwareEndpointPreparationResult"));
        assertTrue(service.contains("storage.save(candidate)"));
    }

    @Test
    void materialHandlingSchema2StoresEvidenceButDoesNotMutateWorkstationInventory() throws IOException {
        String record = source("src/main/java/com/butchercraft/world/materialhandling/MaterialTransferRecordV2.java");
        String storage = source("src/main/java/com/butchercraft/world/materialhandling/persistence/"
                + "MaterialHandlingStorageV2.java");

        assertTrue(record.contains("WorkstationEndpointObservationV2"));
        assertTrue(record.contains("WorkstationEndpointPreparationV2"));
        assertTrue(record.contains("WorkstationEndpointOwnerResultV2"));
        assertFalse(record.contains("WorkstationInventory"));
        assertFalse(storage.contains("setStackInSlot"));
        assertFalse(storage.contains("ItemStackHandler"));
    }

    @Test
    void liveActivationRetainsSchema1HistoryAndUsesSchema2ForNewApprovedEffects() throws IOException {
        String endpoint = source("src/main/java/com/butchercraft/workstation/endpoint/runtime/"
                + "StackAwareWorkstationEndpointRuntimeService.java");
        String material = source("src/main/java/com/butchercraft/world/materialhandling/"
                + "MaterialHandlingSchema.java");
        String materialService = source("src/main/java/com/butchercraft/world/materialhandling/runtime/"
                + "MaterialHandlingService.java");

        assertTrue(endpoint.contains("observeWithdrawal"));
        assertTrue(endpoint.contains("StackAwareWorkstationEndpointService"));
        assertTrue(material.contains("CURRENT_VERSION = 1"));
        assertTrue(material.contains("STACK_AWARE_SCHEMA_VERSION = 2"));
        assertTrue(materialService.contains("immutableLegacySchema1Runtime"));
    }

    @Test
    void instanceIdentitySchemaIsSeparatedFromEndpointProtocolSchema() throws IOException {
        String schema = source("src/main/java/com/butchercraft/workstation/endpoint/WorkstationEndpointSchema.java");
        String identity = source("src/main/java/com/butchercraft/workstation/endpoint/WorkstationInstanceId.java");

        assertTrue(schema.contains("INSTANCE_SCHEMA_VERSION = 1"));
        assertTrue(schema.contains("STACK_AWARE_ENDPOINT_PROTOCOL_VERSION = 2"));
        assertFalse(identity.contains("STACK_AWARE_ENDPOINT_PROTOCOL_VERSION"));
    }

    private static String source(String path) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(path));
    }
}
