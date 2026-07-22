package com.butchercraft.world.identity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

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

        Set<SettlementType> settlementTypes = new HashSet<>();
        for (County county : identity.counties()) {
            assertEquals(identity.region().id(), county.regionId());
            assertFalseBlank(county.id());
            assertFalseBlank(county.displayName());
            for (Settlement settlement : county.settlements()) {
                assertEquals(county.id(), settlement.countyId());
                settlementTypes.add(settlement.type());
            }
        }

        assertTrue(settlementTypes.contains(SettlementType.HAMLET));
        assertTrue(settlementTypes.contains(SettlementType.VILLAGE));
        assertTrue(settlementTypes.contains(SettlementType.TOWN));
        assertTrue(settlementTypes.contains(SettlementType.REGIONAL_CITY));
    }

    private static void assertFalseBlank(String value) {
        assertTrue(value != null && !value.isBlank());
    }
}
