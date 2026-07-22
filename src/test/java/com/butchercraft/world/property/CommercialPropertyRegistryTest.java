package com.butchercraft.world.property;

import com.butchercraft.world.identity.Settlement;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommercialPropertyRegistryTest {
    private final WorldIdentity identity = new WorldIdentityGenerator().generate(321L);
    private final CommercialPropertyRegistry registry = CommercialPropertyRegistry.of(
            identity.commercialProperties(),
            identity.settlements()
    );

    @Test
    void registryLoadsGeneratedPropertiesAndPreservesDeterministicOrdering() {
        assertEquals(28, registry.size());
        assertEquals(registry.properties().stream()
                .map(property -> property.id().value())
                .sorted(Comparator.naturalOrder())
                .toList(), registry.properties().stream().map(property -> property.id().value()).toList());
    }

    @Test
    void registryOrderingDoesNotDependOnInputOrder() {
        CommercialPropertyRegistry reversed = CommercialPropertyRegistry.of(
                identity.commercialProperties().reversed(),
                identity.settlements().reversed()
        );

        assertEquals(registry.properties(), reversed.properties());
    }

    @Test
    void lookupByIdSettlementTypeStatusConditionAndSearchWorks() {
        CommercialProperty property = registry.properties().getFirst();
        Settlement settlement = identity.settlements().stream()
                .filter(candidate -> candidate.id().equals(property.settlementId()))
                .findFirst()
                .orElseThrow();

        assertTrue(registry.contains(property.id()));
        assertEquals(property, registry.find(property.id()).orElseThrow());
        assertEquals(property, registry.find(property.id().value()).orElseThrow());
        assertTrue(registry.findBySettlement(settlement.id()).contains(property));
        assertTrue(registry.findByPropertyType(property.propertyType()).contains(property));
        assertTrue(registry.findByStatus(property.status()).contains(property));
        assertTrue(registry.findByCondition(property.condition()).contains(property));
        assertTrue(registry.search(settlement.displayName()).contains(property));
        assertFalse(registry.search("   ").contains(property));
    }
}
