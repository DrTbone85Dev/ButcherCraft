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
        assertThrows(UnsupportedOperationException.class, () -> county.settlements().add(settlement));
        assertThrows(UnsupportedOperationException.class, () -> identity.counties().add(county));
        assertThrows(UnsupportedOperationException.class, () -> identity.commercialProperties().clear());
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
        assertThrows(IllegalArgumentException.class, () -> new WorldIdentity(4, "world", 1L, region, List.of(county)));
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

    private static Region region(String id) {
        return new Region(id, "Region", "Description", "Agriculture", "Economy", "Culture", "profile");
    }
}
