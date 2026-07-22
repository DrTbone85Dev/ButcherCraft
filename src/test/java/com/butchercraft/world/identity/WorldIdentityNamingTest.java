package com.butchercraft.world.identity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldIdentityNamingTest {
    private final RegionCatalog catalog = RegionCatalog.builtIn();
    private final WorldIdentityGenerator generator = new WorldIdentityGenerator(catalog);

    @Test
    void sameSeedProducesIdenticalNames() {
        WorldIdentity first = generator.generate(9_001L);
        WorldIdentity second = generator.generate(9_001L);

        assertEquals(first.counties().stream().map(County::displayName).toList(),
                second.counties().stream().map(County::displayName).toList());
        assertEquals(first.settlements().stream().map(Settlement::displayName).toList(),
                second.settlements().stream().map(Settlement::displayName).toList());
    }

    @Test
    void nameSelectionDoesNotDependOnGenerationOrder() {
        RegionDefinition prairie = catalog.find(BuiltInRegionCatalog.PRAIRIE_COMMONWEALTH).orElseThrow();

        String settlementBFirst = generator.selectName(
                123L,
                prairie,
                NamingRole.SETTLEMENT_COUNTY_TOWN,
                "prairie_commonwealth_market_county_county_town"
        );
        String settlementASecond = generator.selectName(
                123L,
                prairie,
                NamingRole.SETTLEMENT_MARKET_VILLAGE,
                "prairie_commonwealth_market_county_market_village"
        );

        String settlementAFirst = generator.selectName(
                123L,
                prairie,
                NamingRole.SETTLEMENT_MARKET_VILLAGE,
                "prairie_commonwealth_market_county_market_village"
        );
        String settlementBSecond = generator.selectName(
                123L,
                prairie,
                NamingRole.SETTLEMENT_COUNTY_TOWN,
                "prairie_commonwealth_market_county_county_town"
        );

        assertEquals(settlementAFirst, settlementASecond);
        assertEquals(settlementBFirst, settlementBSecond);
    }

    @Test
    void countyAndSettlementNamesDoNotDuplicateWithinGeneratedIdentity() {
        WorldIdentity identity = generator.generate(7_777L);

        Set<String> countyNames = new HashSet<>();
        for (County county : identity.counties()) {
            assertTrue(countyNames.add(county.displayName()), "Duplicate county name: " + county.displayName());
        }

        Set<String> settlementNames = new HashSet<>();
        for (Settlement settlement : identity.settlements()) {
            assertTrue(settlementNames.add(settlement.displayName()), "Duplicate settlement name: " + settlement.displayName());
        }
    }

    @Test
    void regionSpecificNamingProfilesAreConsumed() {
        RegionDefinition prairie = catalog.find(BuiltInRegionCatalog.PRAIRIE_COMMONWEALTH).orElseThrow();
        RegionDefinition iron = catalog.find(BuiltInRegionCatalog.IRON_VALLEY).orElseThrow();

        String prairieName = generator.selectName(42L, prairie, NamingRole.COUNTY_PRIMARY, "primary_county");
        String ironName = generator.selectName(42L, iron, NamingRole.COUNTY_PRIMARY, "primary_county");

        assertTrue(catalog.namingProfile(prairie).namesFor(NamingRole.COUNTY_PRIMARY).contains(prairieName));
        assertTrue(catalog.namingProfile(iron).namesFor(NamingRole.COUNTY_PRIMARY).contains(ironName));
        assertNotEquals(prairieName, ironName);
    }
}
