package com.butchercraft.world.property;

import com.butchercraft.world.identity.Settlement;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommercialPropertyGenerationTest {
    @Test
    void sameSeedProducesIdenticalCommercialProperties() {
        WorldIdentity first = new WorldIdentityGenerator().generate(12_345L);
        WorldIdentity second = new WorldIdentityGenerator().generate(12_345L);

        assertEquals(first.commercialProperties(), second.commercialProperties());
    }

    @Test
    void generationDoesNotDependOnSettlementIterationOrder() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(9_876L);
        List<CommercialProperty> normal = BuiltInCommercialPropertyCatalog.generate(identity.worldSeed(), identity.settlements());
        List<CommercialProperty> reversed = BuiltInCommercialPropertyCatalog.generate(identity.worldSeed(), identity.settlements().reversed());

        assertEquals(normal, reversed);
    }

    @Test
    void generatorCreatesFourPropertiesForEverySettlement() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(42L);

        assertEquals(identity.settlements().size() * BuiltInCommercialPropertyCatalog.PROPERTIES_PER_SETTLEMENT,
                identity.commercialProperties().size());
        for (Settlement settlement : identity.settlements()) {
            assertEquals(4, identity.commercialPropertiesForSettlement(settlement.id()).size(), settlement.id());
        }
    }

    @Test
    void generatedPropertyTypeDistributionIsStableForCurrentSettlementLayout() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(42L);
        Map<CommercialPropertyType, Long> distribution = identity.commercialProperties().stream()
                .collect(Collectors.groupingBy(CommercialProperty::propertyType, Collectors.counting()));

        assertEquals(Map.of(
                CommercialPropertyType.FAMILY_BUTCHER_SHOP, 7L,
                CommercialPropertyType.VACANT_STOREFRONT, 7L,
                CommercialPropertyType.EMPTY_COMMERCIAL_LOT, 2L,
                CommercialPropertyType.LOCKER_PLANT, 4L,
                CommercialPropertyType.WAREHOUSE, 2L,
                CommercialPropertyType.INDUSTRIAL_BUILDING, 2L,
                CommercialPropertyType.COLD_STORAGE_FACILITY, 3L,
                CommercialPropertyType.DISTRIBUTION_CENTER, 1L
        ), distribution);
    }

    @Test
    void broadSeedSampleCanProduceEveryStatusAndCondition() {
        var statuses = LongStream.range(0, 500L)
                .mapToObj(seed -> new WorldIdentityGenerator().generate(seed))
                .flatMap(identity -> identity.commercialProperties().stream())
                .map(CommercialProperty::status)
                .collect(Collectors.toSet());
        var conditions = LongStream.range(0, 500L)
                .mapToObj(seed -> new WorldIdentityGenerator().generate(seed))
                .flatMap(identity -> identity.commercialProperties().stream())
                .map(CommercialProperty::condition)
                .collect(Collectors.toSet());

        assertEquals(PropertyStatus.values().length, statuses.size());
        assertEquals(PropertyCondition.values().length, conditions.size());
    }

    @Test
    void generatedPropertiesHaveHistoricalSummariesUtilitiesAndOwnership() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(123L);

        for (CommercialProperty property : identity.commercialProperties()) {
            assertFalse(property.history().historicalSummary().isBlank());
            assertTrue(property.constructionYear() <= property.history().ownershipHistory().getFirst().startYear());
            assertTrue(property.history().ownershipHistory().getLast().isCurrent());
            assertTrue(property.lotSize().squareMeters() >= property.buildingSize().squareMeters());
            assertEquals(property.propertyType() == CommercialPropertyType.EMPTY_COMMERCIAL_LOT,
                    property.buildingSize().squareMeters() == 0);
        }
    }
}
