package com.butchercraft.world.identity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldIdentityModelTest {
    @Test
    void countyAndWorldDefensivelyCopyLists() {
        Settlement settlement = new Settlement("oak_village", "Oak Village", "oak_county", SettlementType.VILLAGE);
        List<Settlement> settlements = new ArrayList<>();
        settlements.add(settlement);
        County county = new County("oak_county", "Oak County", "oak_region", settlements);
        settlements.clear();

        List<County> counties = new ArrayList<>();
        counties.add(county);
        WorldIdentity identity = new WorldIdentity(
                WorldIdentity.CURRENT_SCHEMA_VERSION,
                "world_test",
                1L,
                region("oak_region"),
                counties
        );
        counties.clear();

        assertEquals(1, county.settlements().size());
        assertEquals(1, identity.counties().size());
        assertEquals(4, identity.commercialProperties().size());
        assertEquals(4, identity.businesses().size());
        assertEquals(4, identity.families().size());
        assertEquals(4, identity.historicalPersons().size());
        assertEquals(4, identity.ownershipEntities().size());
        assertEquals(4, identity.ownershipHistories().size());
        assertEquals(4, identity.supplyNetwork().supplyRelationships().size());
        assertEquals(1, identity.supplyNetwork().distributionTerritories().size());
        assertEquals(0, identity.supplyNetwork().distributionRoutes().size());
        assertThrows(UnsupportedOperationException.class, () -> county.settlements().add(settlement));
        assertThrows(UnsupportedOperationException.class, () -> identity.counties().add(county));
        assertThrows(UnsupportedOperationException.class, () -> identity.commercialProperties().clear());
        assertThrows(UnsupportedOperationException.class, () -> identity.businesses().clear());
        assertThrows(UnsupportedOperationException.class, () -> identity.families().clear());
        assertThrows(UnsupportedOperationException.class, () -> identity.historicalPersons().clear());
        assertThrows(UnsupportedOperationException.class, () -> identity.ownershipEntities().clear());
        assertThrows(UnsupportedOperationException.class, () -> identity.ownershipHistories().clear());
        assertThrows(UnsupportedOperationException.class, () -> identity.supplyNetwork().supplyRelationships().clear());
    }

    @Test
    void modelRejectsIncompleteOrInconsistentIdentity() {
        Region region = region("region");
        Settlement settlement = new Settlement("settlement", "Settlement", "county", SettlementType.HAMLET);
        County county = new County("county", "County", "region", List.of(settlement));

        assertThrows(IllegalArgumentException.class, () -> new Region(" ", "Region", "Description", "Agriculture", "Economy", "Culture", "profile"));
        assertThrows(NullPointerException.class, () -> new Settlement("settlement", "Settlement", "other", null));
        assertThrows(IllegalArgumentException.class, () -> new County("county", "County", "region", List.of(
                new Settlement("settlement", "Settlement", "wrong_county", SettlementType.HAMLET)
        )));
        assertThrows(IllegalArgumentException.class, () -> new WorldIdentity(WorldIdentity.CURRENT_SCHEMA_VERSION + 1, "world", 1L, region, List.of(county)));
        assertThrows(IllegalArgumentException.class, () -> new WorldIdentity(
                WorldIdentity.CURRENT_SCHEMA_VERSION,
                "world",
                1L,
                region("other_region"),
                List.of(county)
        ));
    }

    @Test
    void worldFlattensSettlementsInCountyOrder() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(77L);

        assertEquals("hamlet", identity.settlements().get(0).type().serializedName());
        assertEquals("regional_city", identity.settlements().get(identity.settlements().size() - 1).type().serializedName());
        assertTrue(identity.settlements().stream().map(Settlement::id).distinct().count() == identity.settlements().size());
    }

    @Test
    void worldReturnsCommercialPropertiesForSettlement() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(88L);
        Settlement settlement = identity.settlements().getFirst();

        assertEquals(4, identity.commercialPropertiesForSettlement(settlement.id()).size());
    }

    @Test
    void worldReturnsBusinessesForSettlementAndProperty() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(88L);
        Settlement settlement = identity.settlements().getFirst();

        assertEquals(3, identity.businessesForSettlement(settlement.id()).size());
        assertEquals(1, identity.businessesForProperty(identity.businessesForSettlement(settlement.id()).getFirst().primaryPropertyId()).size());
        assertEquals(1, identity.ownershipHistoriesForBusiness(identity.businessesForSettlement(settlement.id()).getFirst().id().value()).size());
        assertEquals(1, identity.supplyRelationshipsForBusiness(identity.businessesForSettlement(settlement.id()).getFirst().id().value()).stream()
                .filter(relationship -> relationship.customerBusinessId().equals(identity.businessesForSettlement(settlement.id()).getFirst().id()))
                .count());
        assertEquals(1, identity.tradeTerritoriesForSettlement(settlement.id()).size());
        assertTrue(identity.distributionRoutesForSettlement(settlement.id()).size() >= 1);
    }

    private static Region region(String id) {
        return new Region(id, "Region", "Description", "Agriculture", "Economy", "Culture", "profile");
    }
}
