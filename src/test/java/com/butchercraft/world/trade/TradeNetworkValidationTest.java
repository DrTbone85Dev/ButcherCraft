package com.butchercraft.world.trade;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TradeNetworkValidationTest {
    private final WorldIdentity identity = new WorldIdentityGenerator().generate(111L);
    private final SupplyNetwork network = identity.supplyNetwork();
    private final SupplyRelationship relationship = network.supplyRelationships().getFirst();

    @Test
    void tradeModelsRejectInvalidCoreFields() {
        assertThrows(IllegalArgumentException.class, () -> new SupplyNetworkId("Bad Id"));
        assertThrows(IllegalArgumentException.class, () -> new SupplyRelationshipId("Bad Id"));
        assertThrows(IllegalArgumentException.class, () -> new TradeRegion(
                network.tradeRegions().getFirst().id(),
                "Invalid",
                identity.region().id(),
                List.of(identity.settlements().getFirst().id()),
                101,
                "Invalid score."
        ));
        assertThrows(IllegalArgumentException.class, () -> new DistributionRoute(
                network.distributionRoutes().getFirst().id(),
                "Invalid",
                identity.settlements().getFirst().id(),
                identity.settlements().getFirst().id(),
                List.of(identity.businesses().getFirst().id()),
                List.of(ProductCategory.FRESH_BEEF),
                50,
                "Invalid route."
        ));
        assertThrows(IllegalArgumentException.class, () -> new SupplyRelationship(
                new SupplyRelationshipId("invalid_self_relationship"),
                relationship.customerBusinessId(),
                relationship.customerBusinessId(),
                relationship.relationshipType(),
                relationship.preferredManufacturerId(),
                relationship.productCategories(),
                relationship.historicalStartYear(),
                relationship.historicalEndYear(),
                relationship.relationshipStrength(),
                relationship.territoryId(),
                "Invalid self supply."
        ));
        assertThrows(IllegalArgumentException.class, () -> new SupplyContract(
                new SupplyContractId("invalid_contract"),
                relationship.id(),
                2000,
                OptionalInt.of(1999),
                RelationshipStrength.ESTABLISHED,
                "Invalid chronology."
        ));
        assertThrows(IllegalArgumentException.class, () -> new PreferredSupplier(
                relationship.customerBusinessId(),
                relationship.customerBusinessId(),
                relationship.id(),
                relationship.productCategories(),
                relationship.relationshipStrength(),
                "Invalid self preference."
        ));
        assertThrows(IllegalArgumentException.class, () -> new PreferredManufacturer(
                relationship.customerBusinessId(),
                relationship.preferredManufacturerId(),
                List.of(),
                "Missing categories."
        ));
        assertThrows(IllegalArgumentException.class, () -> new BusinessSpecializationProfile(
                relationship.customerBusinessId(),
                List.of(),
                "Missing specializations."
        ));
    }

    @Test
    void registryRejectsDuplicateIdsAndInvalidReferences() {
        assertThrows(IllegalArgumentException.class, () -> validate(networkWithRelationships(addRelationship(
                copyRelationship(
                        relationship.id(),
                        relationship.supplierBusinessId(),
                        relationship.customerBusinessId(),
                        relationship.preferredManufacturerId(),
                        relationship.territoryId(),
                        "Duplicate relationship id."
                )
        ))));
        assertThrows(IllegalArgumentException.class, () -> validate(networkWithRelationships(addRelationship(
                copyRelationship(
                        new SupplyRelationshipId("missing_supplier_relationship"),
                        new BusinessId("missing_business"),
                        relationship.customerBusinessId(),
                        relationship.preferredManufacturerId(),
                        relationship.territoryId(),
                        "Missing supplier."
                )
        ))));
        assertThrows(IllegalArgumentException.class, () -> validate(networkWithRelationships(addRelationship(
                copyRelationship(
                        new SupplyRelationshipId("missing_manufacturer_relationship"),
                        relationship.supplierBusinessId(),
                        relationship.customerBusinessId(),
                        "missing_manufacturer",
                        relationship.territoryId(),
                        "Missing manufacturer."
                )
        ))));
        assertThrows(IllegalArgumentException.class, () -> validate(networkWithRelationships(addRelationship(
                copyRelationship(
                        new SupplyRelationshipId("missing_territory_relationship"),
                        relationship.supplierBusinessId(),
                        relationship.customerBusinessId(),
                        relationship.preferredManufacturerId(),
                        new DistributionTerritoryId("missing_territory"),
                        "Missing territory."
                )
        ))));
        assertThrows(IllegalArgumentException.class, () -> validate(networkWithContracts(addContract(new SupplyContract(
                new SupplyContractId("missing_relationship_contract"),
                new SupplyRelationshipId("missing_relationship"),
                relationship.historicalStartYear(),
                relationship.historicalEndYear(),
                relationship.relationshipStrength(),
                "Missing relationship."
        )))));
    }

    @Test
    void registryRejectsOrphanedTradeRecords() {
        DistributionTerritory territory = network.distributionTerritories().getFirst();
        DistributionTerritory orphan = new DistributionTerritory(
                new DistributionTerritoryId("orphan_trade_territory"),
                "Orphan Trade Territory",
                territory.tradeRegionId(),
                territory.coveredSettlementIds(),
                territory.primaryBusinessIds(),
                territory.dominantManufacturerIds(),
                territory.distributionImportance(),
                territory.regionalInfluenceScore(),
                "Orphan territory."
        );

        assertThrows(IllegalArgumentException.class, () -> validate(networkWithTerritories(addTerritory(orphan))));
        assertThrows(IllegalArgumentException.class, () -> validate(networkWithPreferredSuppliers(
                network.preferredSuppliers().subList(1, network.preferredSuppliers().size())
        )));
    }

    @Test
    void worldIdentityRejectsInvalidSupplySnapshot() {
        SupplyNetwork invalidNetwork = networkWithRelationships(addRelationship(copyRelationship(
                new SupplyRelationshipId("world_identity_missing_manufacturer_relationship"),
                relationship.supplierBusinessId(),
                relationship.customerBusinessId(),
                "missing_manufacturer",
                relationship.territoryId(),
                "Missing manufacturer."
        )));

        assertThrows(IllegalArgumentException.class, () -> new WorldIdentity(
                WorldIdentity.CURRENT_SCHEMA_VERSION,
                identity.id(),
                identity.worldSeed(),
                identity.region(),
                identity.counties(),
                identity.commercialProperties(),
                identity.businesses(),
                identity.families(),
                identity.historicalPersons(),
                identity.ownershipEntities(),
                identity.ownershipHistories(),
                invalidNetwork
        ));
    }

    private void validate(SupplyNetwork supplyNetwork) {
        TradeNetworkRegistry.of(
                supplyNetwork,
                identity.region(),
                identity.settlements(),
                identity.businesses(),
                identity.ownershipHistories()
        );
    }

    private List<SupplyRelationship> addRelationship(SupplyRelationship extra) {
        List<SupplyRelationship> relationships = new ArrayList<>(network.supplyRelationships());
        relationships.add(extra);
        return relationships;
    }

    private List<SupplyContract> addContract(SupplyContract extra) {
        List<SupplyContract> contracts = new ArrayList<>(network.supplyContracts());
        contracts.add(extra);
        return contracts;
    }

    private List<DistributionTerritory> addTerritory(DistributionTerritory extra) {
        List<DistributionTerritory> territories = new ArrayList<>(network.distributionTerritories());
        territories.add(extra);
        return territories;
    }

    private SupplyNetwork networkWithRelationships(List<SupplyRelationship> relationships) {
        return new SupplyNetwork(
                network.id(),
                network.displayName(),
                network.regionId(),
                network.tradeRegions(),
                network.distributionTerritories(),
                network.distributionRoutes(),
                relationships,
                network.supplyContracts(),
                network.preferredSuppliers(),
                network.preferredManufacturers(),
                network.businessSpecializations()
        );
    }

    private SupplyNetwork networkWithContracts(List<SupplyContract> contracts) {
        return new SupplyNetwork(
                network.id(),
                network.displayName(),
                network.regionId(),
                network.tradeRegions(),
                network.distributionTerritories(),
                network.distributionRoutes(),
                network.supplyRelationships(),
                contracts,
                network.preferredSuppliers(),
                network.preferredManufacturers(),
                network.businessSpecializations()
        );
    }

    private SupplyNetwork networkWithTerritories(List<DistributionTerritory> territories) {
        return new SupplyNetwork(
                network.id(),
                network.displayName(),
                network.regionId(),
                network.tradeRegions(),
                territories,
                network.distributionRoutes(),
                network.supplyRelationships(),
                network.supplyContracts(),
                network.preferredSuppliers(),
                network.preferredManufacturers(),
                network.businessSpecializations()
        );
    }

    private SupplyNetwork networkWithPreferredSuppliers(List<PreferredSupplier> preferredSuppliers) {
        return new SupplyNetwork(
                network.id(),
                network.displayName(),
                network.regionId(),
                network.tradeRegions(),
                network.distributionTerritories(),
                network.distributionRoutes(),
                network.supplyRelationships(),
                network.supplyContracts(),
                preferredSuppliers,
                network.preferredManufacturers(),
                network.businessSpecializations()
        );
    }

    private SupplyRelationship copyRelationship(
            SupplyRelationshipId id,
            BusinessId supplierId,
            BusinessId customerId,
            String manufacturerId,
            DistributionTerritoryId territoryId,
            String notes
    ) {
        return new SupplyRelationship(
                id,
                supplierId,
                customerId,
                relationship.relationshipType(),
                manufacturerId,
                relationship.productCategories(),
                relationship.historicalStartYear(),
                relationship.historicalEndYear(),
                relationship.relationshipStrength(),
                territoryId,
                notes
        );
    }
}
