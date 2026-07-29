package com.butchercraft.world.workforce.department;

import com.butchercraft.world.identity.WorldIdentityGenerator;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DepartmentManagerTest {
    @Test
    void assignsDepartmentAnchorWithoutChangingOtherDefinitions() {
        DepartmentManager manager = new DepartmentManager(DepartmentTestFixtures.defaults());
        DepartmentAnchor anchor = new DepartmentAnchor("minecraft:overworld", 10, 64, 12, 6);

        DepartmentRecord updated = manager.assignAnchor(DepartmentSchema.PACKAGING, anchor);

        assertEquals(anchor, updated.anchor().orElseThrow());
        assertEquals(2L, updated.recordRevision());
        assertEquals(anchor, manager.find(DepartmentSchema.PACKAGING).orElseThrow().anchor().orElseThrow());
        assertEquals(DepartmentSchema.DEFAULT_PROCESSING_RADIUS,
                manager.find(DepartmentSchema.PROCESSING).orElseThrow().anchor().orElseThrow().radius());
    }

    @Test
    void radiusChangesRequireAnExistingDepartmentAnchor() {
        DepartmentManager manager = new DepartmentManager(DepartmentTestFixtures.defaults());

        assertThrows(IllegalStateException.class, () -> manager.assignRadius(DepartmentSchema.PACKAGING, 7));

        DepartmentRecord updated = manager.assignRadius(DepartmentSchema.PROCESSING, 7);

        assertEquals(7, updated.anchor().orElseThrow().radius());
    }

    @Test
    void unknownDepartmentAnchorAssignmentFailsExplicitly() {
        DepartmentManager manager = new DepartmentManager(DepartmentTestFixtures.defaults());

        assertThrows(IllegalArgumentException.class, () -> manager.assignAnchor(
                new DepartmentId("unknown_department"),
                new DepartmentAnchor("minecraft:overworld", 1, 2, 3, 4)
        ));
    }

    @Test
    void canonicalDefinitionValidationRejectsMissingDepartmentAndWrongWorldRoot() {
        DepartmentManager missing = new DepartmentManager(new DepartmentDirectory(DepartmentRegistry.of(List.of(
                DepartmentTestFixtures.record(DepartmentSchema.PROCESSING),
                DepartmentTestFixtures.record(DepartmentSchema.PACKAGING),
                DepartmentTestFixtures.record(DepartmentSchema.SHIPPING),
                DepartmentTestFixtures.record(DepartmentSchema.OFFICE)
        )), Optional.empty()));
        var otherRoot = WorldIdentityRootIdentities.from(new WorldIdentityGenerator().generate(13579L));
        DepartmentRecord wrongRoot = new DepartmentRecord(
                DepartmentSchema.CURRENT_VERSION,
                DepartmentSchema.PROCESSING,
                otherRoot.identity(),
                otherRoot.rootDigest(),
                "Processing",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                1L
        );
        DepartmentManager wrongWorld = new DepartmentManager(new DepartmentDirectory(DepartmentRegistry.of(List.of(
                wrongRoot,
                DepartmentTestFixtures.record(DepartmentSchema.PACKAGING),
                DepartmentTestFixtures.record(DepartmentSchema.SHIPPING),
                DepartmentTestFixtures.record(DepartmentSchema.OFFICE),
                DepartmentTestFixtures.record(DepartmentSchema.MAINTENANCE)
        )), Optional.empty()));

        assertThrows(IllegalStateException.class, () ->
                missing.validateCanonicalDefinitions(DepartmentTestFixtures.WORLD_ROOT));
        assertThrows(IllegalStateException.class, () ->
                wrongWorld.validateCanonicalDefinitions(DepartmentTestFixtures.WORLD_ROOT));
    }
}
