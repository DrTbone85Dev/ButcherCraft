package com.butchercraft.world.business;

import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import com.butchercraft.world.property.CommercialPropertyId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertThrows;

class BusinessValidationTest {
    private final WorldIdentity identity = new WorldIdentityGenerator().generate(111L);
    private final Business valid = identity.businesses().getFirst();

    @Test
    void businessRejectsInvalidCoreFields() {
        assertThrows(IllegalArgumentException.class, () -> new BusinessId("Bad Id"));
        assertThrows(IllegalArgumentException.class, () -> businessWithFoundingYear(1849));
        assertThrows(IllegalArgumentException.class, () -> businessWithFoundingYear(2027));
        assertThrows(IllegalArgumentException.class, () -> businessWithPrimarySettlement(" "));
        assertThrows(NullPointerException.class, () -> businessWithType(null));
        assertThrows(NullPointerException.class, () -> businessWithStatus(null));
        assertThrows(NullPointerException.class, () -> businessWithReputation(null));
    }

    @Test
    void businessRejectsBrokenHistoryAndOccupancy() {
        assertThrows(IllegalArgumentException.class, () -> new BusinessHistory("Only one sentence.", List.of(valid.occupancyHistory().getFirst())));
        assertThrows(IllegalArgumentException.class, () -> new BusinessHistory("Two sentences. Still invalid.", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new BusinessOccupancy(
                valid.primaryPropertyId(),
                1800,
                OptionalInt.empty(),
                BusinessOccupancyReason.FOUNDED,
                "Invalid early start."
        ));
        assertThrows(IllegalArgumentException.class, () -> new BusinessHistory("Two sentences. Overlap exists.", List.of(
                new BusinessOccupancy(valid.primaryPropertyId(), 1950, OptionalInt.of(1960), BusinessOccupancyReason.FOUNDED, "First record."),
                new BusinessOccupancy(valid.primaryPropertyId(), 1960, OptionalInt.of(1970), BusinessOccupancyReason.CLOSURE_RECORD, "Overlap record.")
        )));
    }

    @Test
    void registryRejectsDuplicateIdsNamesAndInvalidReferences() {
        Business duplicateName = businessWithIdAndName(new BusinessId(valid.id().value() + "_other"), valid.displayName());
        Business invalidProperty = businessWithProperty(new CommercialPropertyId("missing_property"));
        Business invalidManufacturer = businessWithManufacturers(List.of("missing_manufacturer"));

        assertThrows(IllegalArgumentException.class, () -> BusinessRegistry.of(List.of(valid, valid), identity.region(), identity.settlements(), identity.commercialProperties()));
        assertThrows(IllegalArgumentException.class, () -> BusinessRegistry.of(List.of(valid, duplicateName), identity.region(), identity.settlements(), identity.commercialProperties()));
        assertThrows(IllegalArgumentException.class, () -> BusinessRegistry.of(List.of(invalidProperty), identity.region(), identity.settlements(), identity.commercialProperties()));
        assertThrows(IllegalArgumentException.class, () -> BusinessRegistry.of(List.of(invalidManufacturer), identity.region(), identity.settlements(), identity.commercialProperties()));
    }

    @Test
    void worldIdentityRejectsInvalidBusinessSnapshot() {
        Business invalidProperty = businessWithProperty(new CommercialPropertyId("missing_property"));

        assertThrows(IllegalArgumentException.class, () -> new WorldIdentity(
                WorldIdentity.CURRENT_SCHEMA_VERSION,
                identity.id(),
                identity.worldSeed(),
                identity.region(),
                identity.counties(),
                identity.commercialProperties(),
                List.of(invalidProperty)
        ));
    }

    private Business businessWithFoundingYear(int foundingYear) {
        return copy(
                valid.id(),
                valid.displayName(),
                valid.businessType(),
                foundingYear,
                valid.status(),
                valid.reputation(),
                valid.history(),
                valid.associatedCommercialPropertyIds(),
                valid.primaryPropertyId(),
                valid.primarySettlementId(),
                valid.primaryRegionId(),
                valid.additionalLocationPropertyIds(),
                valid.corporateHeadquartersPropertyId(),
                valid.ownershipModel(),
                valid.preferredManufacturerIds()
        );
    }

    private Business businessWithPrimarySettlement(String settlementId) {
        return copy(
                valid.id(),
                valid.displayName(),
                valid.businessType(),
                valid.foundingYear(),
                valid.status(),
                valid.reputation(),
                valid.history(),
                valid.associatedCommercialPropertyIds(),
                valid.primaryPropertyId(),
                settlementId,
                valid.primaryRegionId(),
                valid.additionalLocationPropertyIds(),
                valid.corporateHeadquartersPropertyId(),
                valid.ownershipModel(),
                valid.preferredManufacturerIds()
        );
    }

    private Business businessWithType(BusinessType type) {
        return copy(
                valid.id(),
                valid.displayName(),
                type,
                valid.foundingYear(),
                valid.status(),
                valid.reputation(),
                valid.history(),
                valid.associatedCommercialPropertyIds(),
                valid.primaryPropertyId(),
                valid.primarySettlementId(),
                valid.primaryRegionId(),
                valid.additionalLocationPropertyIds(),
                valid.corporateHeadquartersPropertyId(),
                valid.ownershipModel(),
                valid.preferredManufacturerIds()
        );
    }

    private Business businessWithStatus(BusinessStatus status) {
        return copy(
                valid.id(),
                valid.displayName(),
                valid.businessType(),
                valid.foundingYear(),
                status,
                valid.reputation(),
                valid.history(),
                valid.associatedCommercialPropertyIds(),
                valid.primaryPropertyId(),
                valid.primarySettlementId(),
                valid.primaryRegionId(),
                valid.additionalLocationPropertyIds(),
                valid.corporateHeadquartersPropertyId(),
                valid.ownershipModel(),
                valid.preferredManufacturerIds()
        );
    }

    private Business businessWithReputation(BusinessReputation reputation) {
        return copy(
                valid.id(),
                valid.displayName(),
                valid.businessType(),
                valid.foundingYear(),
                valid.status(),
                reputation,
                valid.history(),
                valid.associatedCommercialPropertyIds(),
                valid.primaryPropertyId(),
                valid.primarySettlementId(),
                valid.primaryRegionId(),
                valid.additionalLocationPropertyIds(),
                valid.corporateHeadquartersPropertyId(),
                valid.ownershipModel(),
                valid.preferredManufacturerIds()
        );
    }

    private Business businessWithIdAndName(BusinessId id, String displayName) {
        return copy(
                id,
                displayName,
                valid.businessType(),
                valid.foundingYear(),
                valid.status(),
                valid.reputation(),
                valid.history(),
                valid.associatedCommercialPropertyIds(),
                valid.primaryPropertyId(),
                valid.primarySettlementId(),
                valid.primaryRegionId(),
                valid.additionalLocationPropertyIds(),
                valid.corporateHeadquartersPropertyId(),
                valid.ownershipModel(),
                valid.preferredManufacturerIds()
        );
    }

    private Business businessWithProperty(CommercialPropertyId propertyId) {
        BusinessOccupancy occupancy = valid.occupancyHistory().getFirst();
        BusinessHistory history = new BusinessHistory(valid.historicalSummary(), List.of(new BusinessOccupancy(
                propertyId,
                occupancy.startYear(),
                occupancy.endYear(),
                occupancy.reason(),
                occupancy.notes()
        )));
        return copy(
                valid.id(),
                valid.displayName(),
                valid.businessType(),
                valid.foundingYear(),
                valid.status(),
                valid.reputation(),
                history,
                List.of(propertyId),
                propertyId,
                valid.primarySettlementId(),
                valid.primaryRegionId(),
                List.of(),
                Optional.empty(),
                valid.ownershipModel(),
                valid.preferredManufacturerIds()
        );
    }

    private Business businessWithManufacturers(List<String> manufacturerIds) {
        return copy(
                valid.id(),
                valid.displayName(),
                valid.businessType(),
                valid.foundingYear(),
                valid.status(),
                valid.reputation(),
                valid.history(),
                valid.associatedCommercialPropertyIds(),
                valid.primaryPropertyId(),
                valid.primarySettlementId(),
                valid.primaryRegionId(),
                valid.additionalLocationPropertyIds(),
                valid.corporateHeadquartersPropertyId(),
                valid.ownershipModel(),
                manufacturerIds
        );
    }

    private static Business copy(
            BusinessId id,
            String displayName,
            BusinessType type,
            int foundingYear,
            BusinessStatus status,
            BusinessReputation reputation,
            BusinessHistory history,
            List<CommercialPropertyId> associatedProperties,
            CommercialPropertyId primaryPropertyId,
            String primarySettlementId,
            String primaryRegionId,
            List<CommercialPropertyId> additionalLocations,
            Optional<CommercialPropertyId> corporateHeadquarters,
            BusinessOwnershipModel ownershipModel,
            List<String> preferredManufacturers
    ) {
        return new Business(
                id,
                displayName,
                type,
                foundingYear,
                status,
                reputation,
                history,
                associatedProperties,
                primaryPropertyId,
                primarySettlementId,
                primaryRegionId,
                additionalLocations,
                corporateHeadquarters,
                ownershipModel,
                preferredManufacturers,
                List.of()
        );
    }
}
