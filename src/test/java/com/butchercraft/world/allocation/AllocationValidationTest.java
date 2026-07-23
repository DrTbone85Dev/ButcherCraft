package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationValidationTest {
    @Test
    void everyFailureCodeHasAStableTypedRepresentation() {
        List<AllocationValidationFailure> failures = Arrays.stream(
                        AllocationValidationFailureCode.values()
                )
                .map(code -> new AllocationValidationFailure(
                        code,
                        "field." + code.name().toLowerCase(),
                        "Failure " + code.name()
                ))
                .toList();

        assertEquals(AllocationValidationFailureCode.values().length, failures.size());
        assertEquals(
                failures.stream().sorted().toList(),
                new AllocationValidationException(failures.reversed()).failures()
        );
    }

    @Test
    void validationFailuresAreCanonicallySortedDeduplicatedAndReplayStable() {
        List<AllocationValidationFailure> failures = new ArrayList<>();
        failures.add(new AllocationValidationFailure(
                AllocationValidationFailureCode.ZERO_QUANTITY,
                "quantity",
                "Quantity is zero"
        ));
        failures.add(new AllocationValidationFailure(
                AllocationValidationFailureCode.NULL_VALUE,
                "id",
                "Id is required"
        ));
        failures.add(failures.getFirst());

        AllocationValidationException first = new AllocationValidationException(failures);
        AllocationValidationException second = new AllocationValidationException(failures.reversed());

        assertEquals(first.failures(), second.failures());
        assertEquals(2, first.failures().size());
        assertEquals(
                AllocationValidationFailureCode.NULL_VALUE,
                first.failures().getFirst().code()
        );
    }

    @Test
    void operationResultConvertsStructuralExceptionsWithoutHidingFailures() {
        AllocationOperationResult<RequirementDefinition> accepted =
                AllocationOperationResult.validate(
                        () -> AllocationTestFixtures.requirement("accepted")
                );
        AllocationOperationResult<RequirementDefinition> rejected =
                AllocationOperationResult.validate(() -> RequirementDefinition.create(
                        AllocationSetId.of("example:set"),
                        AllocationTestFixtures.work("rejected"),
                        ResourceCategories.PRODUCTION,
                        CapacityTypeId.of("example:type"),
                        Optional.empty(),
                        AllocationQuantity.zero(CapacityUnits.PRODUCTION_SLOT),
                        0L,
                        AllocationMetadata.empty()
                ));

        assertTrue(accepted.accepted());
        assertTrue(accepted.value().isPresent());
        assertFalse(rejected.accepted());
        assertTrue(rejected.value().isEmpty());
        assertEquals(
                AllocationValidationFailureCode.ZERO_QUANTITY,
                rejected.failures().getFirst().code()
        );
    }

    @Test
    void canonicalValueContractsRoundTripWithoutAFrameworkCodec() {
        AllocationQuantity quantity = AllocationQuantity.of("123456789.123456789", CapacityUnits.ENERGY);
        AllocationQuantity decoded = AllocationQuantity.of(
                quantity.canonicalAmount(),
                CapacityUnitId.of(quantity.unitId().value())
        );
        ExternalReference reference = AllocationTestFixtures.work("round_trip");
        ExternalReference decodedReference = new ExternalReference(
                reference.referenceTypeId(),
                reference.stableExternalId(),
                reference.authoritativeSubsystemId(),
                reference.roleId()
        );

        assertEquals(quantity, decoded);
        assertEquals(reference, decodedReference);
        assertEquals(AllocationSchema.CURRENT_VERSION, 1);
    }
}
