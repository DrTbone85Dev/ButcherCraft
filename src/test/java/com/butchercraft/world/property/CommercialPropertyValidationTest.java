package com.butchercraft.world.property;

import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CommercialPropertyValidationTest {
    private final WorldIdentity identity = new WorldIdentityGenerator().generate(111L);
    private final CommercialProperty valid = identity.commercialProperties().getFirst();

    @Test
    void propertyRejectsInvalidCoreFields() {
        assertThrows(IllegalArgumentException.class, () -> new CommercialPropertyId("Bad Id"));
        assertThrows(IllegalArgumentException.class, () -> propertyWithConstructionYear(1849));
        assertThrows(IllegalArgumentException.class, () -> propertyWithConstructionYear(2027));
        assertThrows(NullPointerException.class, () -> propertyWithUtilityProfile(null));
        assertThrows(NullPointerException.class, () -> propertyWithStatus(null));
        assertThrows(NullPointerException.class, () -> propertyWithCondition(null));
        assertThrows(NullPointerException.class, () -> propertyWithType(null));
    }

    @Test
    void propertyRejectsInvalidSizeAndHistory() {
        assertThrows(IllegalArgumentException.class, () -> new LotSize(0));
        assertThrows(IllegalArgumentException.class, () -> new BuildingSize(-1));
        assertThrows(IllegalArgumentException.class, () -> new ExpansionCapacity(-1));
        assertThrows(IllegalArgumentException.class, () -> propertyWithSizes(new LotSize(10), new BuildingSize(20)));
        assertThrows(IllegalArgumentException.class, () -> propertyWithHistory(new PropertyHistory("Summary", List.of())));
        assertThrows(IllegalArgumentException.class, () -> propertyWithHistory(new PropertyHistory("Summary", List.of(
                new OwnershipRecord("Owner", 1950, OptionalInt.of(1960), PropertyAcquisitionMethod.PRIVATE_SALE, "Past owner.")
        ))));
    }

    @Test
    void registryRejectsDuplicateIdsDuplicateNamesAndInvalidSettlementReference() {
        CommercialProperty duplicateId = propertyWithId(valid.id(), valid.displayName() + " Other", valid.settlementId());
        CommercialProperty duplicateName = propertyWithId(new CommercialPropertyId(valid.id().value() + "_other"), valid.displayName(), valid.settlementId());
        CommercialProperty invalidSettlement = propertyWithId(new CommercialPropertyId(valid.id().value() + "_invalid"), "Invalid Settlement Property", "missing_settlement");

        assertThrows(IllegalArgumentException.class, () -> CommercialPropertyRegistry.of(List.of(valid, duplicateId), identity.settlements()));
        assertThrows(IllegalArgumentException.class, () -> CommercialPropertyRegistry.of(List.of(valid, duplicateName), identity.settlements()));
        assertThrows(IllegalArgumentException.class, () -> CommercialPropertyRegistry.of(List.of(invalidSettlement), identity.settlements()));
    }

    @Test
    void worldIdentityRejectsMissingPropertyForSettlement() {
        assertThrows(IllegalArgumentException.class, () -> new WorldIdentity(
                WorldIdentity.CURRENT_SCHEMA_VERSION,
                identity.id(),
                identity.worldSeed(),
                identity.region(),
                identity.counties(),
                identity.commercialProperties().subList(0, identity.commercialProperties().size() - 4)
        ));
    }

    private CommercialProperty propertyWithConstructionYear(int constructionYear) {
        return new CommercialProperty(
                valid.id(),
                valid.displayName(),
                valid.settlementId(),
                valid.propertyType(),
                constructionYear,
                valid.condition(),
                valid.status(),
                valid.lotSize(),
                valid.buildingSize(),
                valid.utilityProfile(),
                valid.expansionCapacity(),
                valid.history()
        );
    }

    private CommercialProperty propertyWithUtilityProfile(UtilityProfile utilityProfile) {
        return new CommercialProperty(
                valid.id(),
                valid.displayName(),
                valid.settlementId(),
                valid.propertyType(),
                valid.constructionYear(),
                valid.condition(),
                valid.status(),
                valid.lotSize(),
                valid.buildingSize(),
                utilityProfile,
                valid.expansionCapacity(),
                valid.history()
        );
    }

    private CommercialProperty propertyWithStatus(PropertyStatus status) {
        return new CommercialProperty(
                valid.id(),
                valid.displayName(),
                valid.settlementId(),
                valid.propertyType(),
                valid.constructionYear(),
                valid.condition(),
                status,
                valid.lotSize(),
                valid.buildingSize(),
                valid.utilityProfile(),
                valid.expansionCapacity(),
                valid.history()
        );
    }

    private CommercialProperty propertyWithCondition(PropertyCondition condition) {
        return new CommercialProperty(
                valid.id(),
                valid.displayName(),
                valid.settlementId(),
                valid.propertyType(),
                valid.constructionYear(),
                condition,
                valid.status(),
                valid.lotSize(),
                valid.buildingSize(),
                valid.utilityProfile(),
                valid.expansionCapacity(),
                valid.history()
        );
    }

    private CommercialProperty propertyWithType(CommercialPropertyType type) {
        return new CommercialProperty(
                valid.id(),
                valid.displayName(),
                valid.settlementId(),
                type,
                valid.constructionYear(),
                valid.condition(),
                valid.status(),
                valid.lotSize(),
                valid.buildingSize(),
                valid.utilityProfile(),
                valid.expansionCapacity(),
                valid.history()
        );
    }

    private CommercialProperty propertyWithSizes(LotSize lotSize, BuildingSize buildingSize) {
        return new CommercialProperty(
                valid.id(),
                valid.displayName(),
                valid.settlementId(),
                valid.propertyType(),
                valid.constructionYear(),
                valid.condition(),
                valid.status(),
                lotSize,
                buildingSize,
                valid.utilityProfile(),
                valid.expansionCapacity(),
                valid.history()
        );
    }

    private CommercialProperty propertyWithHistory(PropertyHistory history) {
        return new CommercialProperty(
                valid.id(),
                valid.displayName(),
                valid.settlementId(),
                valid.propertyType(),
                valid.constructionYear(),
                valid.condition(),
                valid.status(),
                valid.lotSize(),
                valid.buildingSize(),
                valid.utilityProfile(),
                valid.expansionCapacity(),
                history
        );
    }

    private CommercialProperty propertyWithId(CommercialPropertyId id, String displayName, String settlementId) {
        return new CommercialProperty(
                id,
                displayName,
                settlementId,
                valid.propertyType(),
                valid.constructionYear(),
                valid.condition(),
                valid.status(),
                valid.lotSize(),
                valid.buildingSize(),
                valid.utilityProfile(),
                valid.expansionCapacity(),
                valid.history()
        );
    }
}
