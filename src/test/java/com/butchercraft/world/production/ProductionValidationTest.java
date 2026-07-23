package com.butchercraft.world.production;

import com.butchercraft.world.economy.actor.ActorCapability;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionValidationTest {
    @Test
    void validProcessAndPlanResolveEveryAuthorityWithoutReservingInventory() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionValidator validator = new ProductionValidator(context.dependencies());
        ProductionProcessDefinition process = ProductionTestFixtures.process();
        ProductionProcessRegistry registry = ProductionProcessRegistry.builder().register(process).build();

        assertTrue(validator.validateProcess(process).isEmpty());
        assertTrue(validator.validatePlan(ProductionTestFixtures.plan(), registry).isEmpty());
        assertEquals(20L, context.dependencies().inventoryManager().quantityIn(
                ProductionTestFixtures.INPUT_INVENTORY, ProductionTestFixtures.INPUT));
        assertEquals(0, context.dependencies().transactionManager().size());
    }

    @Test
    void invalidBindingAndMissingCapabilityAreTypedRejections() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionProcessDefinition process = withAdditionalCapability(
                ProductionTestFixtures.process(), ActorCapability.BUY
        );
        ProductionProcessRegistry registry = ProductionProcessRegistry.builder().register(process).build();
        ProductionPlanDefinition invalidPlan = ProductionPlanDefinition.builder()
                .id("test:invalid_plan")
                .processId(process.id())
                .producerActorId(ProductionTestFixtures.ACTOR)
                .batchCount(1L)
                .inventoryBinding(new ProductionInventoryBinding(
                        ProductionTestFixtures.INPUT_LINE,
                        ProductionBindingDirection.INPUT,
                        ProductionTestFixtures.INPUT_INVENTORY,
                        ProductionTestFixtures.OUTPUT,
                        com.butchercraft.world.goods.UnitOfMeasure.EACH
                ))
                .createdSimulationTick(0L)
                .earliestStartTick(0L)
                .build();

        List<ProductionFailure> failures = new ProductionValidator(context.dependencies())
                .validatePlan(invalidPlan, registry);
        assertFalse(failures.isEmpty());
        assertTrue(failures.stream().anyMatch(failure ->
                failure.code() == ProductionFailureCode.ACTOR_CAPABILITY_MISSING));
        assertTrue(failures.stream().anyMatch(failure ->
                failure.code() == ProductionFailureCode.INPUT_GOOD_MISMATCH));
        assertTrue(failures.stream().anyMatch(failure ->
                failure.code() == ProductionFailureCode.OUTPUT_BINDING_MISSING));
    }

    private static ProductionProcessDefinition withAdditionalCapability(
            ProductionProcessDefinition source,
            ActorCapability capability
    ) {
        ProductionProcessDefinition.Builder builder = ProductionProcessDefinition.builder()
                .id(source.id())
                .displayName(source.displayName())
                .owningIndustryId(source.owningIndustryId())
                .requiredActorCapability(source.requiredActorCapability())
                .additionalCapability(capability)
                .duration(source.duration())
                .batchPolicy(source.batchPolicy());
        source.inputs().forEach(builder::input);
        source.outputs().forEach(builder::output);
        source.transformationReferences().forEach(builder::transformationReference);
        return builder.build();
    }
}
