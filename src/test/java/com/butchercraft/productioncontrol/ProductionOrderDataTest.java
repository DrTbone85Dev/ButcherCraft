package com.butchercraft.productioncontrol;

import com.butchercraft.world.production.ProductionRunId;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionOrderDataTest {
    @Test
    void defaultOrderUsesOnlyTheBeefPattiesTemplateWithoutRunAuthority() {
        ProductionOrderData data = ProductionOrderData.beefPattiesOrder();

        assertEquals(ProductionOrderData.BEEF_PATTIES_TEMPLATE_ID, data.templateId());
        assertTrue(data.isBeefPattiesTemplate());
        assertTrue(data.runId().isEmpty());
    }

    @Test
    void runReferenceIsStoredAsProductionRunIdentityOnly() {
        ProductionRunId runId = ProductionRunId.of("butchercraft:manual_beef_patties/pabc/t1/n1/run");

        ProductionOrderData data = ProductionOrderData.beefPattiesOrder().withRun(runId);

        assertEquals(Optional.of(runId.value()), data.runId());
        assertEquals(ProductionOrderData.BEEF_PATTIES_TEMPLATE_ID, data.templateId());
    }

    @Test
    void unsupportedTemplateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProductionOrderData("butchercraft:production_template/other", Optional.empty()));
    }

    @Test
    void invalidRunIdentityIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProductionOrderData(
                        ProductionOrderData.BEEF_PATTIES_TEMPLATE_ID,
                        Optional.of("not a production run id")
                ));
    }

    @Test
    void codecRoundTripsPersistentOrderData() {
        ProductionOrderData data = ProductionOrderData.beefPattiesOrder()
                .withRun(ProductionRunId.of("butchercraft:manual_beef_patties/pabc/t4/n2/run"));

        JsonObject json = ProductionOrderData.CODEC.encodeStart(JsonOps.INSTANCE, data)
                .resultOrPartial(message -> {
                    throw new AssertionError(message);
                })
                .orElseThrow()
                .getAsJsonObject();
        ProductionOrderData decoded = ProductionOrderData.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(message -> {
                    throw new AssertionError(message);
                })
                .orElseThrow();

        assertEquals(data, decoded);
        assertEquals(ProductionOrderData.BEEF_PATTIES_TEMPLATE_ID, json.get("template_id").getAsString());
        assertEquals(data.runId().orElseThrow(), json.get("run_id").getAsString());
    }
}
