package com.butchercraft.workstation;

import com.butchercraft.engine.context.ProcessingContext;
import com.butchercraft.engine.context.ProcessingFactor;
import com.butchercraft.engine.operation.ProcessingOperation;
import com.butchercraft.engine.product.Product;

import java.util.List;

public final class PrototypeProcessingContextValues {
    public static final ProcessingFactor CLEANLINESS = ProcessingFactor.IDEAL;
    public static final ProcessingFactor EQUIPMENT_CONDITION = ProcessingFactor.IDEAL;
    public static final ProcessingFactor OPERATOR_SKILL = ProcessingFactor.NEUTRAL_SKILL;

    private PrototypeProcessingContextValues() {
    }

    public static ProcessingContext context(Product input, ProcessingOperation operation) {
        return new ProcessingContext(input, operation, CLEANLINESS, OPERATOR_SKILL, EQUIPMENT_CONDITION, List.of());
    }
}
