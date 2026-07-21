package com.butchercraft.machine.packaging;

import com.butchercraft.content.ContentSnapshotService;
import com.butchercraft.machine.bandsaw.BandsawWorkstation;
import com.butchercraft.machine.grinder.GrinderWorkstation;
import com.butchercraft.packaging.definition.BuiltInPackagingRegistry;
import com.butchercraft.product.definition.BuiltInProductRegistry;
import com.butchercraft.transformation.BuiltInTransformationRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PackagingTableContentCompatibilityTest {
    @Test
    void retailProductFrameworkDoesNotRegisterPackagingTransformations() {
        assertFalse(BuiltInTransformationRegistry.BUILT_IN_RESOURCE_PATHS.stream().anyMatch(path -> path.contains("packag")));
        assertEquals(4, BuiltInPackagingRegistry.BUILT_IN_RESOURCE_PATHS.size());
        assertEquals("data/butchercraft/butchercraft/content/packaging/retail_package.json",
                BuiltInPackagingRegistry.BUILT_IN_RESOURCE_PATHS.getFirst());
        assertEquals("data/butchercraft/butchercraft/content/packaging/freezer_paper_package.json",
                BuiltInPackagingRegistry.BUILT_IN_RESOURCE_PATHS.getLast());
        assertEquals("data/butchercraft/butchercraft/content/product/retail_ground_beef.json",
                BuiltInProductRegistry.BUILT_IN_RESOURCE_PATHS.stream()
                        .filter(path -> path.endsWith("/retail_ground_beef.json"))
                        .findFirst()
                        .orElseThrow());

        var snapshot = ContentSnapshotService.loadBundledSnapshot();

        assertEquals(31, snapshot.products().size());
        assertEquals(4, snapshot.packaging().size());
        assertEquals(8, snapshot.transformations().size());
    }

    @Test
    void existingWorkstationCapabilitiesRemainUnchanged() {
        assertEquals(1, GrinderWorkstation.capability().inputSlots());
        assertEquals(1, GrinderWorkstation.capability().outputSlots());
        assertEquals(1, BandsawWorkstation.capability().inputSlots());
        assertEquals(8, BandsawWorkstation.capability().outputSlots());
    }
}
