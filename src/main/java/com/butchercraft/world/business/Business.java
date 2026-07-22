package com.butchercraft.world.business;

import com.butchercraft.world.property.CommercialPropertyId;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record Business(
        BusinessId id,
        String displayName,
        BusinessType businessType,
        int foundingYear,
        BusinessStatus status,
        BusinessReputation reputation,
        BusinessHistory history,
        List<CommercialPropertyId> associatedCommercialPropertyIds,
        CommercialPropertyId primaryPropertyId,
        String primarySettlementId,
        String primaryRegionId,
        List<CommercialPropertyId> additionalLocationPropertyIds,
        Optional<CommercialPropertyId> corporateHeadquartersPropertyId,
        BusinessOwnershipModel ownershipModel,
        List<String> preferredManufacturerIds,
        List<BusinessRelationship> relationships
) {
    private static final int MIN_FOUNDING_YEAR = 1850;
    private static final int MAX_FOUNDING_YEAR = 2026;

    public Business {
        id = Objects.requireNonNull(id, "id");
        displayName = requireNonBlank(displayName, "displayName");
        businessType = Objects.requireNonNull(businessType, "businessType");
        if (foundingYear < MIN_FOUNDING_YEAR || foundingYear > MAX_FOUNDING_YEAR) {
            throw new IllegalArgumentException("Business founding year is outside the supported range: " + foundingYear);
        }
        status = Objects.requireNonNull(status, "status");
        reputation = Objects.requireNonNull(reputation, "reputation");
        history = Objects.requireNonNull(history, "history");
        associatedCommercialPropertyIds = copyAssociatedProperties(associatedCommercialPropertyIds);
        primaryPropertyId = Objects.requireNonNull(primaryPropertyId, "primaryPropertyId");
        primarySettlementId = requireNonBlank(primarySettlementId, "primarySettlementId");
        primaryRegionId = requireNonBlank(primaryRegionId, "primaryRegionId");
        additionalLocationPropertyIds = copyPropertyIds(additionalLocationPropertyIds, "additionalLocationPropertyIds");
        corporateHeadquartersPropertyId = Objects.requireNonNull(corporateHeadquartersPropertyId, "corporateHeadquartersPropertyId");
        ownershipModel = Objects.requireNonNull(ownershipModel, "ownershipModel");
        preferredManufacturerIds = copyManufacturerIds(preferredManufacturerIds);
        relationships = List.copyOf(Objects.requireNonNull(relationships, "relationships"));
        validatePrimaryAndAdditionalLocations(primaryPropertyId, associatedCommercialPropertyIds, additionalLocationPropertyIds, corporateHeadquartersPropertyId);
        validateOccupanciesAreAssociated(id, history, associatedCommercialPropertyIds);
        validateStatusOccupancy(status, history);
        if (ownershipModel.recordedSinceYear() < foundingYear) {
            throw new IllegalArgumentException("Business ownership model starts before business founding year");
        }
        if (history.occupancyHistory().stream().mapToInt(BusinessOccupancy::startYear).min().orElseThrow() < foundingYear) {
            throw new IllegalArgumentException("Business occupancy history starts before business founding year");
        }
    }

    public String historicalSummary() {
        return history.historicalSummary();
    }

    public List<BusinessOccupancy> occupancyHistory() {
        return history.occupancyHistory();
    }

    public boolean occupies(CommercialPropertyId propertyId) {
        return associatedCommercialPropertyIds.contains(propertyId);
    }

    private static List<CommercialPropertyId> copyAssociatedProperties(List<CommercialPropertyId> propertyIds) {
        List<CommercialPropertyId> copied = copyPropertyIds(propertyIds, "associatedCommercialPropertyIds");
        if (copied.isEmpty()) {
            throw new IllegalArgumentException("Business must reference at least one commercial property");
        }
        return copied;
    }

    private static List<CommercialPropertyId> copyPropertyIds(List<CommercialPropertyId> propertyIds, String fieldName) {
        Objects.requireNonNull(propertyIds, fieldName);
        Set<CommercialPropertyId> copied = new LinkedHashSet<>();
        for (CommercialPropertyId propertyId : propertyIds) {
            copied.add(Objects.requireNonNull(propertyId, "propertyId"));
        }
        if (copied.size() != propertyIds.size()) {
            throw new IllegalArgumentException("Business " + fieldName + " must not contain duplicates");
        }
        return List.copyOf(copied);
    }

    private static List<String> copyManufacturerIds(List<String> preferredManufacturerIds) {
        Objects.requireNonNull(preferredManufacturerIds, "preferredManufacturerIds");
        LinkedHashSet<String> copied = new LinkedHashSet<>();
        for (String manufacturerId : preferredManufacturerIds) {
            copied.add(requireNonBlank(manufacturerId, "preferredManufacturerId"));
        }
        if (copied.size() != preferredManufacturerIds.size()) {
            throw new IllegalArgumentException("Business preferred manufacturer ids must not contain duplicates");
        }
        return List.copyOf(copied);
    }

    private static void validatePrimaryAndAdditionalLocations(
            CommercialPropertyId primaryPropertyId,
            List<CommercialPropertyId> associatedProperties,
            List<CommercialPropertyId> additionalLocations,
            Optional<CommercialPropertyId> corporateHeadquartersPropertyId
    ) {
        if (!associatedProperties.contains(primaryPropertyId)) {
            throw new IllegalArgumentException("Business primary property must be associated with the business");
        }
        for (CommercialPropertyId additionalLocation : additionalLocations) {
            if (!associatedProperties.contains(additionalLocation)) {
                throw new IllegalArgumentException("Business additional location must be associated with the business: "
                        + additionalLocation.value());
            }
            if (additionalLocation.equals(primaryPropertyId)) {
                throw new IllegalArgumentException("Business primary property must not also be listed as an additional location");
            }
        }
        corporateHeadquartersPropertyId.ifPresent(headquarters -> {
            if (!associatedProperties.contains(headquarters)) {
                throw new IllegalArgumentException("Business corporate headquarters must be an associated commercial property");
            }
        });
    }

    private static void validateOccupanciesAreAssociated(
            BusinessId businessId,
            BusinessHistory history,
            List<CommercialPropertyId> associatedProperties
    ) {
        for (BusinessOccupancy occupancy : history.occupancyHistory()) {
            if (!associatedProperties.contains(occupancy.propertyId())) {
                throw new IllegalArgumentException("Business " + businessId.value()
                        + " occupancy references unassociated commercial property: " + occupancy.propertyId().value());
            }
        }
    }

    private static void validateStatusOccupancy(BusinessStatus status, BusinessHistory history) {
        long currentOccupancies = history.occupancyHistory().stream()
                .filter(BusinessOccupancy::isCurrent)
                .count();
        if (status.hasActiveOccupancy() && currentOccupancies == 0) {
            throw new IllegalArgumentException("Active business records must have a current occupancy");
        }
        if (!status.hasActiveOccupancy() && currentOccupancies > 0) {
            throw new IllegalArgumentException("Inactive business records must not have current occupancy");
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Business " + fieldName + " must not be blank");
        }
        return value;
    }
}
