package com.butchercraft.workstation;

import com.butchercraft.engine.product.Product;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

public record WorkstationExecutionStartRequest(
        WorkstationTickContext tickContext,
        WorkstationCapability capability,
        ResolvedWorkstationOperation operation,
        List<ItemStack> frozenInputs,
        List<Product> expectedOutputs
) {
    public WorkstationExecutionStartRequest {
        tickContext = Objects.requireNonNull(tickContext, "tickContext");
        capability = Objects.requireNonNull(capability, "capability");
        operation = Objects.requireNonNull(operation, "operation");
        frozenInputs = Objects.requireNonNull(frozenInputs, "frozenInputs").stream()
                .map(stack -> Objects.requireNonNull(stack, "frozenInput").copy())
                .toList();
        if (frozenInputs.isEmpty()) {
            throw new IllegalArgumentException("Workstation Execution requires at least one frozen input");
        }
        expectedOutputs = List.copyOf(Objects.requireNonNull(expectedOutputs, "expectedOutputs"));
        if (expectedOutputs.isEmpty()) {
            throw new IllegalArgumentException("Workstation Execution requires expected output identity");
        }
    }
}
