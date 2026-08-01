package com.butchercraft.world.workforce.department;

import com.butchercraft.world.identity.WorldIdentityGenerator;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.Optional;

final class DepartmentTestFixtures {
    static final WorldIdentityRootIdentity WORLD_ROOT =
            WorldIdentityRootIdentities.from(new WorldIdentityGenerator().generate(24680L));

    private DepartmentTestFixtures() {
    }

    static DepartmentDirectory defaults() {
        return BuiltInDepartmentDefinitions.defaults(WORLD_ROOT);
    }

    static DepartmentRecord record(DepartmentId departmentId) {
        return new DepartmentRecord(
                DepartmentSchema.CURRENT_VERSION,
                departmentId,
                WORLD_ROOT.identity(),
                WORLD_ROOT.rootDigest(),
                departmentId.value(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                1L
        );
    }
}
