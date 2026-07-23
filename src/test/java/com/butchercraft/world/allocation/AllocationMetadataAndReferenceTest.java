package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationMetadataAndReferenceTest {
    @Test
    void metadataIsTypedCanonicallyOrderedAndImmutable() {
        Map<String, AllocationMetadataValue> mutable = new HashMap<>();
        mutable.put("example:zeta", AllocationMetadataValue.text("ready"));
        mutable.put("example:alpha", AllocationMetadataValue.decimal("2.500"));
        mutable.put("example:enabled", AllocationMetadataValue.bool(true));
        mutable.put("example:sequence", AllocationMetadataValue.integer(7L));
        AllocationMetadata metadata = AllocationMetadata.of(mutable);
        mutable.clear();

        assertEquals(
                List.of("example:alpha", "example:enabled", "example:sequence", "example:zeta"),
                List.copyOf(metadata.values().keySet())
        );
        assertEquals("2.5", metadata.values().get("example:alpha").canonicalValue());
        assertEquals(4, metadata.values().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> metadata.values().put("example:new", AllocationMetadataValue.text("value"))
        );
    }

    @Test
    void metadataRejectsMalformedTypedValuesAndUnboundedMaps() {
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.NULL_VALUE,
                () -> AllocationMetadata.of(null)
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.INVALID_METADATA,
                () -> new AllocationMetadataValue(
                        AllocationMetadataValueType.BOOLEAN,
                        "TRUE"
                )
        );
        Map<String, AllocationMetadataValue> tooLarge = new HashMap<>();
        for (int index = 0; index <= AllocationSchema.MAXIMUM_METADATA_ENTRIES; index++) {
            tooLarge.put("example:key_" + index, AllocationMetadataValue.integer(index));
        }
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.INVALID_METADATA,
                () -> AllocationMetadata.of(tooLarge)
        );
    }

    @Test
    void everyMetadataValueTypeHasAStableRoundTripContract() {
        List<AllocationMetadataValue> values = List.of(
                AllocationMetadataValue.text("capacity observed"),
                AllocationMetadataValue.identifier("example:value"),
                AllocationMetadataValue.integer(Long.MAX_VALUE),
                AllocationMetadataValue.decimal("12.340"),
                AllocationMetadataValue.bool(false)
        );

        for (AllocationMetadataValue value : values) {
            assertEquals(
                    value,
                    new AllocationMetadataValue(value.type(), value.canonicalValue())
            );
        }
    }

    @Test
    void externalReferencesCarryAuthorityRoleAndStableOrdering() {
        ExternalReference reference = ExternalReference.of(
                "butchercraft:shift_capacity",
                "example:day_shift_butchers",
                "example:workforce"
        ).withRole("example:butcher");

        assertEquals(Optional.of("example:butcher"), reference.roleId());
        assertEquals(
                "butchercraft:shift_capacity|example:day_shift_butchers|example:workforce|example:butcher",
                reference.canonicalKey()
        );
        assertEquals(
                reference,
                new ExternalReference(
                        reference.referenceTypeId(),
                        reference.stableExternalId(),
                        reference.authoritativeSubsystemId(),
                        reference.roleId()
                )
        );
        assertTrue(reference.compareTo(AllocationTestFixtures.work("later")) != 0);
    }

    @Test
    void categoriesAndUnitsAreOpenTypedIdentifiersWithCanonicalBuiltIns() {
        assertTrue(ResourceCategories.schemaOne().contains(ResourceCategories.WORKFORCE));
        assertEquals(
                ResourceCategory.of("expansion:refrigeration"),
                ResourceCategory.of("expansion:refrigeration")
        );
        assertEquals(
                CapacityUnitId.of("expansion:pallet_slot"),
                CapacityUnitId.of("expansion:pallet_slot")
        );
    }
}
