package com.butchercraft.world.materialhandling.runtime;

import com.butchercraft.workstation.endpoint.WorkstationEndpointConfiguration;
import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialHandlingRouteTest {
    private static final WorldIdentityRootIdentity WORLD = new WorldIdentityRootIdentity(
            "butchercraft:world_identity/material_route_test",
            1,
            "sha256:" + "8".repeat(64)
    );

    @Test
    void employeeRoutesAreDeterministicFromBoundEndpointTypes() {
        MaterialHandlingService.SupportedRoute trim = MaterialHandlingService.INSTANCE.employeeRoute(
                endpoint("butchercraft:cutting_table", 1, 1L),
                endpoint("butchercraft:grinder", 2, 2L)
        ).orElseThrow();
        MaterialHandlingService.SupportedRoute groundBeef = MaterialHandlingService.INSTANCE.employeeRoute(
                endpoint("butchercraft:grinder", 2, 2L),
                endpoint("butchercraft:patty_former", 3, 3L)
        ).orElseThrow();

        assertEquals("butchercraft:beef_trim", trim.materialIdentity());
        assertEquals("butchercraft:beef_trim_test", trim.sourceItemIdentity());
        assertEquals("butchercraft:cutting_table->butchercraft:grinder", trim.routeIdentity());
        assertEquals("butchercraft:ground_beef", groundBeef.materialIdentity());
        assertEquals("butchercraft:ground_beef_test", groundBeef.sourceItemIdentity());
        assertEquals("butchercraft:grinder->butchercraft:patty_former", groundBeef.routeIdentity());
    }

    @Test
    void unsupportedPairsDoNotBecomeArbitraryItemTransport() {
        assertTrue(MaterialHandlingService.INSTANCE.employeeRoute(
                endpoint("butchercraft:cutting_table", 1, 1L),
                endpoint("butchercraft:patty_former", 3, 3L)
        ).isEmpty());
        assertTrue(MaterialHandlingService.INSTANCE.employeeRoute(
                endpoint("butchercraft:grinder", 2, 2L),
                endpoint("butchercraft:grinder", 4, 4L)
        ).isEmpty());
        assertTrue(MaterialHandlingService.INSTANCE.employeeRoute(
                endpoint("butchercraft:patty_former", 3, 3L),
                endpoint("butchercraft:grinder", 2, 2L)
        ).isEmpty());
    }

    private static WorkstationEndpointReference endpoint(String type, int x, long generation) {
        WorkstationEndpointKey key = new WorkstationEndpointKey(type, "minecraft:overworld", x, 64, 0);
        WorkstationInstanceId instanceId = WorkstationInstanceId.create(
                WORLD,
                key,
                generation,
                WorkstationEndpointConfiguration.standard().instanceAllocationConfigurationIdentity()
        );
        return new WorkstationEndpointReference(instanceId, key, generation);
    }
}
