package com.butchercraft.world.business;

import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessRegistryTest {
    private final WorldIdentity identity = new WorldIdentityGenerator().generate(555L);
    private final BusinessRegistry registry = BusinessRegistry.of(
            identity.businesses(),
            identity.region(),
            identity.settlements(),
            identity.commercialProperties()
    );

    @Test
    void registryLoadsGeneratedBusinessesInStableIdOrder() {
        assertEquals(identity.businesses().size(), registry.size());
        assertEquals(
                identity.businesses().stream().sorted(Comparator.comparing(business -> business.id().value())).toList(),
                registry.businesses()
        );
        assertThrows(UnsupportedOperationException.class, () -> registry.businesses().clear());
    }

    @Test
    void registryFindsBusinessesBySupportedKeys() {
        Business first = registry.businesses().getFirst();

        assertTrue(registry.contains(first.id()));
        assertEquals(first, registry.find(first.id()).orElseThrow());
        assertEquals(first, registry.find(first.id().value()).orElseThrow());
        assertTrue(registry.findByProperty(first.primaryPropertyId()).contains(first));
        assertTrue(registry.findByProperty(first.primaryPropertyId().value()).contains(first));
        assertTrue(registry.findBySettlement(first.primarySettlementId()).contains(first));
        assertTrue(registry.findByBusinessType(first.businessType()).contains(first));
        assertTrue(registry.findByStatus(first.status()).contains(first));
        assertTrue(registry.findByReputation(first.reputation()).contains(first));
    }

    @Test
    void registrySupportsSearch() {
        Business first = registry.businesses().getFirst();
        String query = first.displayName().split(" ")[0];

        assertTrue(registry.search(query).contains(first));
        assertTrue(registry.search(" ").isEmpty());
    }
}
