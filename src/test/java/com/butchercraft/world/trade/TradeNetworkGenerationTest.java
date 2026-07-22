package com.butchercraft.world.trade;

import com.butchercraft.world.business.Business;
import com.butchercraft.world.business.BusinessHistory;
import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import com.butchercraft.world.ownership.BuiltInOwnershipCatalog;
import com.butchercraft.world.ownership.OwnershipIdentitySnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeNetworkGenerationTest {
    @Test
    void sameSeedProducesIdenticalSupplyNetwork() {
        WorldIdentity first = new WorldIdentityGenerator().generate(12_345L);
        WorldIdentity second = new WorldIdentityGenerator().generate(12_345L);

        assertEquals(first.supplyNetwork(), second.supplyNetwork());
    }

    @Test
    void generationDoesNotDependOnInputIterationOrder() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(9_876L);
        SupplyNetwork normal = BuiltInTradeNetworkCatalog.generate(
                identity.worldSeed(),
                identity.region(),
                identity.settlements(),
                identity.businesses(),
                identity.ownershipHistories()
        );
        SupplyNetwork reversed = BuiltInTradeNetworkCatalog.generate(
                identity.worldSeed(),
                identity.region(),
                identity.settlements().reversed(),
                identity.businesses().reversed(),
                identity.ownershipHistories().reversed()
        );

        assertEquals(normal, reversed);
    }

    @Test
    void generatedNetworkCoversEveryBusinessAndSettlement() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(42L);
        SupplyNetwork network = identity.supplyNetwork();

        assertEquals(identity.businesses().size(), network.supplyRelationships().size());
        assertEquals(identity.businesses().size(), network.supplyContracts().size());
        assertEquals(identity.businesses().size(), network.preferredSuppliers().size());
        assertEquals(identity.businesses().size(), network.preferredManufacturers().size());
        assertEquals(identity.businesses().size(), network.businessSpecializations().size());
        assertEquals(identity.settlements().size(), network.distributionTerritories().size());
        assertEquals(identity.settlements().size() - 1, network.distributionRoutes().size());

        for (Business business : identity.businesses()) {
            assertTrue(network.supplyRelationships().stream()
                    .anyMatch(relationship -> relationship.customerBusinessId().equals(business.id())));
            assertFalse(TradeNetworkRegistry.of(
                    network,
                    identity.region(),
                    identity.settlements(),
                    identity.businesses(),
                    identity.ownershipHistories()
            ).findBusinessSpecializations(business.id()).isEmpty());
        }
        for (SupplyRelationship relationship : network.supplyRelationships()) {
            assertFalse(relationship.supplierBusinessId().equals(relationship.customerBusinessId()));
            assertFalse(relationship.productCategories().isEmpty());
            assertTrue(network.supplyContracts().stream()
                    .anyMatch(contract -> contract.relationshipId().equals(relationship.id())));
        }
    }

    @Test
    void broadSeedSampleCanProduceEveryTypedRelationshipCategoryAndSpecialization() {
        var identities = LongStream.range(0, 500L)
                .mapToObj(seed -> new WorldIdentityGenerator().generate(seed))
                .toList();
        var relationshipTypes = identities.stream()
                .flatMap(identity -> identity.supplyNetwork().supplyRelationships().stream())
                .map(SupplyRelationship::relationshipType)
                .collect(Collectors.toSet());
        var productCategories = identities.stream()
                .flatMap(identity -> identity.supplyNetwork().supplyRelationships().stream())
                .flatMap(relationship -> relationship.productCategories().stream())
                .collect(Collectors.toSet());
        var specializations = identities.stream()
                .flatMap(identity -> identity.supplyNetwork().businessSpecializations().stream())
                .flatMap(profile -> profile.specializations().stream())
                .collect(Collectors.toSet());

        assertEquals(SupplyRelationshipType.values().length, relationshipTypes.size());
        assertEquals(ProductCategory.values().length, productCategories.size());
        assertEquals(BusinessSpecialization.values().length, specializations.size());
    }

    @Test
    void existingRelationshipIdsDoNotShiftWhenFutureBusinessesAreAdded() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(321L);
        SupplyNetwork original = identity.supplyNetwork();
        List<Business> expandedBusinesses = new ArrayList<>(identity.businesses());
        expandedBusinesses.add(copyBusinessWithId(identity.businesses().getFirst(), new BusinessId("future_supply_chain_test_business")));
        OwnershipIdentitySnapshot expandedOwnership = BuiltInOwnershipCatalog.generate(
                identity.worldSeed(),
                identity.region(),
                identity.settlements(),
                expandedBusinesses
        );

        SupplyNetwork expanded = BuiltInTradeNetworkCatalog.generate(
                identity.worldSeed(),
                identity.region(),
                identity.settlements(),
                expandedBusinesses,
                expandedOwnership.ownershipHistories()
        );

        var originalIds = original.supplyRelationships().stream()
                .map(SupplyRelationship::id)
                .collect(Collectors.toSet());
        var expandedIds = expanded.supplyRelationships().stream()
                .map(SupplyRelationship::id)
                .collect(Collectors.toSet());
        assertTrue(expandedIds.containsAll(originalIds));
    }

    private static Business copyBusinessWithId(Business source, BusinessId id) {
        return new Business(
                id,
                "Future Supply Chain Test",
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
