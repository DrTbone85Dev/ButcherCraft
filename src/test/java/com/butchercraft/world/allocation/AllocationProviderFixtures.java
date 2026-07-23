package com.butchercraft.world.allocation;

import java.util.List;
import java.util.function.Function;

final class AllocationProviderFixtures {
    static final long TICK = 300L;
    static final CapacityTypeId MACHINE_TIME =
            CapacityTypeId.of("test:machine_time");
    static final CapacityTypeId WORKFORCE_SLOT =
            CapacityTypeId.of("test:workforce_slot");

    private AllocationProviderFixtures() {
    }

    static AllocationProviderDescriptor descriptor(
            String suffix,
            String owner,
            ResourceCategory category,
            CapacityTypeId capacityTypeId,
            CapacityUnitId capacityUnitId
    ) {
        return new AllocationProviderDescriptor(
                AllocationProviderId.of("test:provider_" + suffix),
                List.of(owner),
                List.of(category),
                List.of(capacityTypeId),
                List.of(capacityUnitId),
                AllocationMetadata.empty(),
                AllocationSchema.CURRENT_VERSION
        );
    }

    static TestProvider provider(
            AllocationProviderDescriptor descriptor,
            Function<AllocationObservationContext, AllocationObservationResult> observation
    ) {
        return new TestProvider(descriptor, observation);
    }

    static TestProvider provider(
            String suffix,
            ResourceCategory category,
            CapacityTypeId capacityTypeId,
            CapacityUnitId capacityUnitId
    ) {
        String owner = "test:owner_" + suffix;
        AllocationProviderDescriptor descriptor = descriptor(
                suffix,
                owner,
                category,
                capacityTypeId,
                capacityUnitId
        );
        return provider(descriptor, context -> {
            ObservedResourceSnapshot resource = resource(
                    descriptor,
                    suffix,
                    category,
                    owner,
                    context.simulationTick(),
                    ResourceExclusivityMode.SHARED
            );
            ObservedCapacitySnapshot capacity = capacity(
                    suffix,
                    resource.resourceId(),
                    capacityTypeId,
                    capacityUnitId,
                    owner,
                    context.simulationTick(),
                    "8"
            );
            return AllocationObservationResult.success(
                    descriptor.providerId(),
                    context.simulationTick(),
                    List.of(resource),
                    List.of(capacity)
            );
        });
    }

    static ObservedResourceSnapshot resource(
            AllocationProviderDescriptor descriptor,
            String suffix,
            ResourceCategory category,
            String owner,
            long tick,
            ResourceExclusivityMode exclusivityMode
    ) {
        return new ObservedResourceSnapshot(
                ResourceId.of("test:resource_" + suffix),
                category,
                descriptor.providerId(),
                ExternalReference.of(
                        "butchercraft:resource_observation",
                        "test:resource_observation_" + suffix,
                        owner
                ),
                ResourceAvailability.AVAILABLE,
                exclusivityMode,
                tick,
                AllocationMetadata.empty(),
                AllocationSchema.CURRENT_VERSION
        );
    }

    static ObservedCapacitySnapshot capacity(
            String suffix,
            ResourceId resourceId,
            CapacityTypeId capacityTypeId,
            CapacityUnitId capacityUnitId,
            String owner,
            long tick,
            String amount
    ) {
        return new ObservedCapacitySnapshot(
                CapacityId.of("test:capacity_" + suffix),
                resourceId,
                capacityTypeId,
                AllocationQuantity.of(amount, capacityUnitId),
                capacityUnitId,
                tick,
                ExternalReference.of(
                        "butchercraft:capacity_observation",
                        "test:capacity_observation_" + suffix,
                        owner
                ),
                AllocationMetadata.empty(),
                AllocationSchema.CURRENT_VERSION
        );
    }

    static AllocationObservationRequest request() {
        return AllocationObservationRequest.all(
                AllocationObservationContext.allAtTick(TICK)
        );
    }

    static final class TestProvider implements AllocationResourceProvider {
        private final AllocationProviderDescriptor descriptor;
        private final Function<AllocationObservationContext, AllocationObservationResult>
                observation;
        private int invocationCount;

        TestProvider(
                AllocationProviderDescriptor descriptor,
                Function<AllocationObservationContext, AllocationObservationResult> observation
        ) {
            this.descriptor = descriptor;
            this.observation = observation;
        }

        @Override
        public AllocationProviderDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public AllocationObservationResult observe(AllocationObservationContext context) {
            invocationCount++;
            return observation.apply(context);
        }

        int invocationCount() {
            return invocationCount;
        }
    }
}
