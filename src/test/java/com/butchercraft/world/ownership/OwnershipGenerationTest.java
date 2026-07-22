package com.butchercraft.world.ownership;

import com.butchercraft.world.business.Business;
import com.butchercraft.world.business.BusinessHistory;
import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnershipGenerationTest {
    @Test
    void sameSeedProducesIdenticalOwnershipSnapshot() {
        WorldIdentity first = new WorldIdentityGenerator().generate(12_345L);
        WorldIdentity second = new WorldIdentityGenerator().generate(12_345L);

        assertEquals(first.families(), second.families());
        assertEquals(first.historicalPersons(), second.historicalPersons());
        assertEquals(first.ownershipEntities(), second.ownershipEntities());
        assertEquals(first.ownershipHistories(), second.ownershipHistories());
    }

    @Test
    void generationDoesNotDependOnSettlementOrBusinessIterationOrder() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(9_876L);
        OwnershipIdentitySnapshot normal = BuiltInOwnershipCatalog.generate(
                identity.worldSeed(),
                identity.region(),
                identity.settlements(),
                identity.businesses()
        );
        OwnershipIdentitySnapshot reversed = BuiltInOwnershipCatalog.generate(
                identity.worldSeed(),
                identity.region(),
                identity.settlements().reversed(),
                identity.businesses().reversed()
        );

        assertEquals(normal, reversed);
    }

    @Test
    void generatorCreatesOwnershipHistoryForEveryBusiness() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(42L);

        assertEquals(identity.businesses().size(), identity.families().size());
        assertEquals(identity.businesses().size(), identity.historicalPersons().size());
        assertEquals(identity.businesses().size(), identity.ownershipEntities().size());
        assertEquals(identity.businesses().size(), identity.ownershipHistories().size());
        for (Business business : identity.businesses()) {
            List<OwnershipHistory> histories = identity.ownershipHistoriesForBusiness(business.id().value());
            assertEquals(1, histories.size());
            assertEquals(OwnershipShare.FULL_OWNERSHIP, histories.getFirst().ownershipRecords().stream()
                    .mapToInt(record -> record.ownershipShare().basisPoints())
                    .sum());
        }
    }

    @Test
    void broadSeedSampleCanProduceEveryOwnershipTypeAndFamilyReputation() {
        var identities = LongStream.range(0, 500L)
                .mapToObj(seed -> new WorldIdentityGenerator().generate(seed))
                .toList();
        var types = identities.stream()
                .flatMap(identity -> identity.ownershipEntities().stream())
                .map(OwnershipEntity::type)
                .collect(Collectors.toSet());
        var reputations = identities.stream()
                .flatMap(identity -> identity.families().stream())
                .map(Family::reputation)
                .collect(Collectors.toSet());

        assertEquals(OwnershipEntityType.values().length, types.size());
        assertEquals(FamilyReputation.values().length, reputations.size());
    }

    @Test
    void existingOwnershipIdentitiesDoNotShiftWhenUnrelatedBusinessesAreAdded() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(321L);
        OwnershipIdentitySnapshot original = BuiltInOwnershipCatalog.generate(
                identity.worldSeed(),
                identity.region(),
                identity.settlements(),
                identity.businesses()
        );
        List<Business> expandedBusinesses = new java.util.ArrayList<>(identity.businesses());
        expandedBusinesses.add(copyBusinessWithId(identity.businesses().getFirst(), new BusinessId("future_test_business")));

        OwnershipIdentitySnapshot expanded = BuiltInOwnershipCatalog.generate(
                identity.worldSeed(),
                identity.region(),
                identity.settlements(),
                expandedBusinesses
        );

        assertTrue(expanded.families().containsAll(original.families()));
        assertTrue(expanded.historicalPersons().containsAll(original.historicalPersons()));
        assertTrue(expanded.ownershipEntities().containsAll(original.ownershipEntities()));
        assertTrue(expanded.ownershipHistories().containsAll(original.ownershipHistories()));
    }

    private static Business copyBusinessWithId(Business source, BusinessId id) {
        return new Business(
                id,
                source.displayName() + " Future Test",
                source.businessType(),
                source.foundingYear(),
                source.status(),
                source.reputation(),
                new BusinessHistory(source.historicalSummary(), source.occupancyHistory()),
                source.associatedCommercialPropertyIds(),
                source.primaryPropertyId(),
                source.primarySettlementId(),
                source.primaryRegionId(),
                source.additionalLocationPropertyIds(),
                Optional.empty(),
                source.ownershipModel(),
                source.preferredManufacturerIds(),
                List.of()
        );
    }
}
