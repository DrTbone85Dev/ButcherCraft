package com.butchercraft.world.business.runtime;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.CONFIGURATION;
import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.allDays;
import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.shift;
import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.state;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessRuntimeRegistryTest {
    @Test
    void registryCreatesRuntimeStatesForGeneratedBusinesses() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(4_321L);
        BusinessRuntimeRegistry registry = BusinessRuntimeRegistry.fromBusinesses(identity.businesses(), CONFIGURATION);

        assertEquals(identity.businesses().size(), registry.size());
        assertEquals(
                identity.businesses().stream().map(business -> business.id().value()).sorted().toList(),
                registry.states().stream().map(state -> state.businessId().value()).toList()
        );
        assertThrows(UnsupportedOperationException.class, () -> registry.states().clear());
        assertTrue(registry.contains(identity.businesses().getFirst().id()));
        assertTrue(registry.find(identity.businesses().getFirst().id()).isPresent());
    }

    @Test
    void registryRejectsDuplicateRuntimeBusinessIds() {
        BusinessRuntimeState first = state("alpha_market", allDays(8, 17), List.of(shift("day", 8, 17, 2)), 4);
        BusinessRuntimeState duplicate = state("alpha_market", allDays(8, 17), List.of(shift("day", 8, 17, 2)), 4);

        assertThrows(IllegalArgumentException.class, () -> BusinessRuntimeRegistry.of(List.of(first, duplicate)));
    }

    @Test
    void registryRejectsUnknownBusinessReferences() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(9_001L);
        BusinessRuntimeState unknown = state("missing_business", allDays(8, 17), List.of(shift("day", 8, 17, 2)), 4);
        BusinessRuntimeRegistry registry = BusinessRuntimeRegistry.of(List.of(unknown));

        assertThrows(IllegalArgumentException.class, () -> registry.validateReferences(identity.businesses()));
    }

    @Test
    void runtimeValidationRejectsInvalidWorkforceAndShiftState() {
        BusinessRuntimeState invalidShiftCapacity = state(
                "capacity_test",
                allDays(8, 17),
                List.of(shift("day", 8, 17, 5)),
                4
        );

        assertThrows(IllegalArgumentException.class, () ->
                BusinessRuntimeRegistry.of(List.of(invalidShiftCapacity)).validate(CONFIGURATION));
        assertThrows(IllegalArgumentException.class, () -> new BusinessRuntimeState(
                new BusinessId("active_closed"),
                BusinessOperationalStatus.CLOSED,
                false,
                Optional.of("day"),
                4,
                1,
                false,
                0L,
                allDays(8, 17),
                List.of(shift("day", 8, 17, 1)),
                BusinessRuntimeSchema.CURRENT_VERSION
        ));
    }
}
