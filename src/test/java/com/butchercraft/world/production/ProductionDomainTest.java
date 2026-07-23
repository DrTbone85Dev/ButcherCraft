package com.butchercraft.world.production;

import com.butchercraft.world.economy.order.GoodQuantity;
import com.butchercraft.world.goods.GoodYieldRatio;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionDomainTest {
    @Test
    void identitiesAreCanonicalOrderedAndValidated() {
        assertEquals("test:process", ProductionProcessId.of("test:process").value());
        assertTrue(ProductionProcessId.of("a:process").compareTo(ProductionProcessId.of("b:process")) < 0);
        assertEquals("test:plan/run", ProductionRunId.forPlan(ProductionPlanId.of("test:plan")).value());
        assertThrows(IllegalArgumentException.class, () -> ProductionProcessId.of("Bad Id"));
        assertThrows(IllegalArgumentException.class, () -> ProductionLineId.of(""));
    }

    @Test
    void exactQuantityScalingRejectsRoundingAndWholeUnitLoss() {
        assertEquals(
                GoodQuantity.of("1.5"),
                ProductionQuantityCalculator.scaleOutput(
                        GoodQuantity.of(1L), 3L, new GoodYieldRatio(1L, 2L)
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                ProductionQuantityCalculator.scaleOutput(
                        GoodQuantity.of(1L), 1L, new GoodYieldRatio(1L, 3L)
                ));
        assertThrows(IllegalArgumentException.class, () ->
                ProductionQuantityCalculator.toInventoryUnits(GoodQuantity.of("1.5")));
        assertThrows(ArithmeticException.class, () ->
                new ProductionDuration(Long.MAX_VALUE, 1L).requiredWorkUnits(2L));
    }

    @Test
    void processCollectionsAndMetadataAreImmutable() {
        ProductionProcessDefinition process = ProductionTestFixtures.process();
        assertThrows(UnsupportedOperationException.class, () -> process.inputs().clear());
        assertThrows(UnsupportedOperationException.class, () ->
                process.additionalRequiredCapabilities().add(
                        com.butchercraft.world.economy.actor.ActorCapability.BUY));
        ProductionMetadata metadata = new ProductionMetadata(Set.of("test:tag"), Optional.of("Generic fixture"));
        assertEquals(Set.of("test:tag"), metadata.tags());
    }

    @Test
    void buildersAndDefinitionsRejectIncompleteOrDuplicateStructure() {
        assertThrows(NullPointerException.class, () -> ProductionProcessDefinition.builder().build());
        ProductionInputDefinition duplicate = ProductionTestFixtures.process().inputs().getFirst();
        assertThrows(IllegalArgumentException.class, () -> ProductionProcessDefinition.builder()
                .id("test:duplicate")
                .displayName("Duplicate")
                .owningIndustryId(ProductionTestFixtures.INDUSTRY)
                .input(duplicate)
                .input(duplicate)
                .output(ProductionTestFixtures.process().outputs().getFirst())
                .duration(ProductionDuration.ofTicks(1L))
                .batchPolicy(ProductionBatchPolicy.wholeBatches(1L, 1L, 1L))
                .build());
        assertThrows(IllegalArgumentException.class, () ->
                new ProductionBatchPolicy(1L, 2L, 1L, true, true));
        assertThrows(IllegalArgumentException.class, () ->
                ProductionBatchPolicy.wholeBatches(2L, 5L, 2L).validate(3L));
    }
}
