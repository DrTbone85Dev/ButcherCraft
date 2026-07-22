package com.butchercraft.world.business;

import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import com.butchercraft.world.property.CommercialProperty;
import com.butchercraft.world.property.CommercialPropertyId;
import com.butchercraft.world.property.CommercialPropertyType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessGenerationTest {
    @Test
    void sameSeedProducesIdenticalBusinesses() {
        WorldIdentity first = new WorldIdentityGenerator().generate(12_345L);
        WorldIdentity second = new WorldIdentityGenerator().generate(12_345L);

        assertEquals(first.businesses(), second.businesses());
    }

    @Test
    void generationDoesNotDependOnSettlementOrPropertyIterationOrder() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(9_876L);
        List<Business> normal = BuiltInBusinessCatalog.generate(
                identity.worldSeed(),
                identity.region(),
                identity.settlements(),
                identity.commercialProperties()
        );
        List<Business> reversed = BuiltInBusinessCatalog.generate(
                identity.worldSeed(),
                identity.region(),
                identity.settlements().reversed(),
                identity.commercialProperties().reversed()
        );

        assertEquals(normal, reversed);
    }

    @Test
    void generatorCreatesOneBusinessForEveryNonEmptyCommercialProperty() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(42L);
        long occupiedCapableProperties = identity.commercialProperties().stream()
                .filter(property -> property.propertyType() != CommercialPropertyType.EMPTY_COMMERCIAL_LOT)
                .count();

        assertEquals(occupiedCapableProperties, identity.businesses().size());
        for (Business business : identity.businesses()) {
            assertEquals(1, business.associatedCommercialPropertyIds().size());
            assertEquals(1, identity.businessesForProperty(business.primaryPropertyId()).size());
        }
    }

    @Test
    void generatedBusinessesHaveHistoryOwnershipManufacturersAndPropertyReferences() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(123L);
        List<CommercialPropertyId> propertyIds = identity.commercialProperties().stream()
                .map(CommercialProperty::id)
                .toList();

        for (Business business : identity.businesses()) {
            assertFalse(business.historicalSummary().isBlank());
            assertFalse(business.preferredManufacturerIds().isEmpty());
            assertTrue(propertyIds.contains(business.primaryPropertyId()));
            assertTrue(business.associatedCommercialPropertyIds().contains(business.primaryPropertyId()));
            assertEquals(identity.region().id(), business.primaryRegionId());
            assertTrue(business.occupancyHistory().stream()
                    .allMatch(occupancy -> business.associatedCommercialPropertyIds().contains(occupancy.propertyId())));
            assertTrue(business.status().hasActiveOccupancy()
                    == business.occupancyHistory().stream().anyMatch(BusinessOccupancy::isCurrent));
        }
    }

    @Test
    void broadSeedSampleCanProduceEveryBusinessTypeStatusAndReputation() {
        var identities = LongStream.range(0, 500L)
                .mapToObj(seed -> new WorldIdentityGenerator().generate(seed))
                .toList();
        var types = identities.stream()
                .flatMap(identity -> identity.businesses().stream())
                .map(Business::businessType)
                .collect(Collectors.toSet());
        var statuses = identities.stream()
                .flatMap(identity -> identity.businesses().stream())
                .map(Business::status)
                .collect(Collectors.toSet());
        var reputations = identities.stream()
                .flatMap(identity -> identity.businesses().stream())
                .map(Business::reputation)
                .collect(Collectors.toSet());

        assertEquals(BusinessType.values().length, types.size());
        assertEquals(BusinessStatus.values().length, statuses.size());
        assertEquals(BusinessReputation.values().length, reputations.size());
    }

    @Test
    void existingBusinessIdentitiesDoNotShiftWhenUnrelatedPropertiesAreAdded() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(321L);
        List<Business> original = BuiltInBusinessCatalog.generate(
                identity.worldSeed(),
                identity.region(),
                identity.settlements(),
                identity.commercialProperties()
        );
        CommercialProperty extraProperty = propertyWithId(
                identity.commercialProperties().getFirst(),
                new CommercialPropertyId(identity.settlements().getFirst().id() + "_future_test_property")
        );
        List<CommercialProperty> expandedProperties = new ArrayList<>(identity.commercialProperties());
        expandedProperties.add(extraProperty);

        List<Business> expanded = BuiltInBusinessCatalog.generate(
                identity.worldSeed(),
                identity.region(),
                identity.settlements(),
                expandedProperties
        );

        assertTrue(expanded.containsAll(original));
    }

    private static CommercialProperty propertyWithId(CommercialProperty source, CommercialPropertyId id) {
        return new CommercialProperty(
                id,
                source.displayName() + " Future Test",
                source.settlementId(),
                source.propertyType(),
                source.constructionYear(),
                source.condition(),
                source.status(),
                source.lotSize(),
                source.buildingSize(),
                source.utilityProfile(),
                source.expansionCapacity(),
                source.history()
        );
    }
}
