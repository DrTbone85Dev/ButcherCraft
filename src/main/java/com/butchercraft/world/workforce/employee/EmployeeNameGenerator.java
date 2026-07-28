package com.butchercraft.world.workforce.employee;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.List;
import java.util.Objects;

public final class EmployeeNameGenerator {
    private static final List<String> DEFAULT_GIVEN_NAMES = List.of(
            "Alex",
            "Morgan",
            "Casey",
            "Jordan",
            "Taylor",
            "Riley",
            "Jamie",
            "Avery"
    );

    private EmployeeNameGenerator() {
    }

    public static String generatedDisplayName(
            WorldIdentityRootIdentity worldIdentity,
            BusinessId businessId,
            long sequence
    ) {
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        Objects.requireNonNull(businessId, "businessId");
        if (sequence < 0L) {
            throw new IllegalArgumentException("Employee name sequence must not be negative: " + sequence);
        }
        int index = Math.floorMod((int) (worldIdentity.rootDigest().hashCode()
                + businessId.value().hashCode()
                + sequence), DEFAULT_GIVEN_NAMES.size());
        long suffix = sequence + 1L;
        return DEFAULT_GIVEN_NAMES.get(index) + " " + suffix;
    }
}
