package com.butchercraft.world.identity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldIdentityGeneratorTest {
    private final WorldIdentityGenerator generator = new WorldIdentityGenerator();

    @Test
    void sameSeedGeneratesIdenticalWorldIdentity() {
        WorldIdentity first = generator.generate(12_345L);
        WorldIdentity second = generator.generate(12_345L);

        assertEquals(first, second);
    }

    @Test
    void differentSeedsGenerateDifferentWorldIdentity() {
        WorldIdentity first = generator.generate(12_345L);
        WorldIdentity second = generator.generate(67_890L);

        assertNotEquals(first.id(), second.id());
        assertNotEquals(first, second);
    }

    @Test
    void generatedIdentityContainsWorldRegionCountyAndSettlementHierarchy() {
        WorldIdentity identity = generator.generate(42L);

        assertEquals(WorldIdentity.CURRENT_SCHEMA_VERSION, identity.schemaVersion());
        assertEquals(42L, identity.worldSeed());
        assertTrue(identity.id().startsWith("world_"));
        assertEquals(3, identity.counties().size());
        assertEquals(7, identity.settlements().size());
        assertEquals(28, identity.commercialProperties().size());
        assertEquals(26, identity.businesses().size());
        assertTrue(RegionCatalog.builtIn().contains(identity.region().id()));
        assertFalseBlank(identity.region().description());
        assertFalseBlank(identity.region().culturalIdentity());
        assertFalseBlank(identity.region().namingProfileId());

        Set<SettlementType> settlementTypes = new HashSet<>();
        for (County county : identity.counties()) {
            assertEquals(identity.region().id(), county.regionId());
            assertFalseBlank(county.id());
            assertFalseBlank(county.displayName());
            for (Settlement settlement : county.settlements()) {
                assertEquals(county.id(), settlement.countyId());
                assertEquals(4, identity.commercialPropertiesForSettlement(settlement.id()).size());
                int expectedBusinesses = settlement.type() == SettlementType.HAMLET ? 3 : 4;
                assertEquals(expectedBusinesses, identity.businessesForSettlement(settlement.id()).size());
                settlementTypes.add(settlement.type());
            }
        }

        assertTrue(settlementTypes.contains(SettlementType.HAMLET));
        assertTrue(settlementTypes.contains(SettlementType.VILLAGE));
        assertTrue(settlementTypes.contains(SettlementType.TOWN));
        assertTrue(settlementTypes.contains(SettlementType.REGIONAL_CITY));
    }

    @Test
    void broadSeedSampleCanSelectEveryBuiltInRegion() {
        Set<String> selectedRegionIds = LongStream.range(0, 10_000L)
                .mapToObj(seed -> generator.selectRegion(seed).id())
                .collect(Collectors.toSet());

        assertEquals(RegionCatalog.builtIn().size(), selectedRegionIds.size());
        assertTrue(selectedRegionIds.contains(BuiltInRegionCatalog.PRAIRIE_COMMONWEALTH));
        assertTrue(selectedRegionIds.contains(BuiltInRegionCatalog.IRON_VALLEY));
        assertTrue(selectedRegionIds.contains(BuiltInRegionCatalog.GREAT_RIVER_BASIN));
        assertTrue(selectedRegionIds.contains(BuiltInRegionCatalog.HIGH_PLAINS_TERRITORY));
        assertTrue(selectedRegionIds.contains(BuiltInRegionCatalog.TIMBER_RIDGE));
    }

    @Test
    void regionSelectionDoesNotDependOnCatalogConstructionOrder() {
        RegionCatalog builtIn = RegionCatalog.builtIn();
        RegionCatalog reversed = RegionCatalog.of(
                builtIn.regions().reversed(),
                builtIn.namingProfiles().reversed()
        );
        WorldIdentityGenerator reversedGenerator = new WorldIdentityGenerator(reversed);

        for (long seed = 0L; seed < 200L; seed++) {
            assertEquals(generator.selectRegion(seed), reversedGenerator.selectRegion(seed));
        }
    }

    private static void assertFalseBlank(String value) {
        assertTrue(value != null && !value.isBlank());
    }
}
