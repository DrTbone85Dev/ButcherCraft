package com.butchercraft.engine;

import com.butchercraft.engine.modifier.ModifierCategory;
import com.butchercraft.engine.modifier.ProcessingModifier;
import com.butchercraft.engine.operation.ProcessingOperation;
import com.butchercraft.engine.operation.YieldRatio;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quality.ProductQuality;
import com.butchercraft.engine.quantity.ProductQuantity;

import java.util.List;

final class EngineTestFixtures {
    static final EngineId RAW_BEEF = EngineId.of("raw_beef");
    static final EngineId PREPARED_BEEF = EngineId.of("prepared_beef");
    static final EngineId BASIC_OPERATION = EngineId.of("basic_prepare");

    private EngineTestFixtures() {
    }

    static Product rawProduct() {
        return new Product(
                RAW_BEEF,
                ProductCategory.BEEF,
                ProcessingState.RAW,
                ProductQuantity.grams(1_000),
                ProductQuality.ofScore(650)
        );
    }

    static ProcessingOperation basicOperation(List<ProcessingModifier> modifiers) {
        return new ProcessingOperation(
                BASIC_OPERATION,
                RAW_BEEF,
                ProcessingState.RAW,
                PREPARED_BEEF,
                ProcessingState.PREPARED,
                20,
                new YieldRatio(1, 2),
                25,
                modifiers
        );
    }

    static ProcessingModifier qualityModifier(String id, int effect, int priority) {
        return new ProcessingModifier(EngineId.of(id), "Quality fixture " + id, ModifierCategory.QUALITY, effect, priority);
    }

    static ProcessingModifier warningModifier(String id, int priority) {
        return new ProcessingModifier(EngineId.of(id), "Warning fixture " + id, ModifierCategory.WARNING, 0, priority);
    }
}
