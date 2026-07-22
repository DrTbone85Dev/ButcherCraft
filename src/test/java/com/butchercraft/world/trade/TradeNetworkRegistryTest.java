package com.butchercraft.world.trade;

import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeNetworkRegistryTest {
    private final WorldIdentity identity = new WorldIdentityGenerator().generate(555L);
    private final TradeNetworkRegistry registry = TradeNetworkRegistry.of(
            identity.supplyNetwork(),
            identity.region(),
            identity.settlements(),
            identity.businesses(),
            identity.ownershipHistories()
    );

    @Test
    void registryLoadsSupplyNetworkInStableRecordOrder() {
        assertEquals(identity.supplyNetwork().supplyRelationships().size(), registry.relationshipCount());
        assertEquals(identity.supplyNetwork().distributionTerritories().size(), registry.territoryCount());
        assertEquals(identity.supplyNetwork().distributionRoutes().size(), registry.routeCount());
        assertEquals(
                identity.supplyNetwork().supplyRelationships().stream()
                        .sorted(Comparator.comparing(relationship -> relationship.id().value()))
                        .toList(),
                registry.supplyNetwork().supplyRelationships()
        );
        assertThrows(UnsupportedOperationException.class, () -> registry.supplyNetwork().supplyRelationships().clear());
    }

    @Test
    void registryFindsRelationshipsByCoreTradeIndexes() {
        SupplyRelationship relationship = registry.supplyNetwork().supplyRelationships().getFirst();

        assertTrue(registry.contains(relationship.id()));
        assertEquals(relationship, registry.find(relationship.id()).orElseThrow());
        assertEquals(relationship, registry.find(relationship.id().value()).orElseThrow());
        assertTrue(registry.findByBusiness(relationship.customerBusinessId()).contains(relationship));
        assertTrue(registry.findByBusiness(relationship.supplierBusinessId()).contains(relationship));
        assertTrue(registry.findByManufacturer(relationship.preferredManufacturerId()).contains(relationship));
        assertTrue(registry.findByProductCategory(relationship.productCategories().getFirst()).contains(relationship));
        assertTrue(registry.findByTerritory(relationship.territoryId()).contains(relationship));
        assertTrue(registry.findByRelationshipType(relationship.relationshipType()).contains(relationship));
    }

    @Test
    void registryFindsTradeTerritoriesRoutesContractsPreferencesAndSpecializations() {
        SupplyRelationship relationship = registry.supplyNetwork().supplyRelationships().getFirst();
        DistributionTerritory territory = registry.supplyNetwork().distributionTerritories().getFirst();
        DistributionRoute route = registry.supplyNetwork().distributionRoutes().getFirst();
        TradeRegion tradeRegion = registry.supplyNetwork().tradeRegions().getFirst();
        SupplyContract contract = registry.supplyNetwork().supplyContracts().getFirst();

        assertEquals(territory, registry.findTerritory(territory.id()).orElseThrow());
        assertEquals(route, registry.findRoute(route.id()).orElseThrow());
        assertEquals(tradeRegion, registry.findTradeRegion(tradeRegion.id()).orElseThrow());
        assertEquals(contract, registry.findContract(contract.id()).orElseThrow());
        var settlementRelationships = registry.findBySettlement(territory.coveredSettlementIds().getFirst());
        assertTrue(settlementRelationships.size() > 0);
        assertTrue(settlementRelationships.stream()
                .allMatch(found -> found.supplierBusinessId() != null && found.customerBusinessId() != null));
        assertTrue(registry.findPreferredSuppliers(relationship.customerBusinessId()).stream()
                .anyMatch(preferredSupplier -> preferredSupplier.relationshipId().equals(relationship.id())));
        assertTrue(registry.findPreferredManufacturers(relationship.customerBusinessId()).stream()
                .anyMatch(preferredManufacturer -> preferredManufacturer.businessId().equals(relationship.customerBusinessId())));
        assertTrue(registry.findBusinessSpecializations(relationship.customerBusinessId()).size() > 0);
    }
}
