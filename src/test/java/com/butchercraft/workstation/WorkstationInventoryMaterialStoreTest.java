package com.butchercraft.workstation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.machine.bandsaw.BandsawWorkstation;
import com.butchercraft.registration.ModItems;
import com.butchercraft.transformation.BuiltInTransformationRegistry;
import com.butchercraft.transformation.MaterialAmount;
import com.butchercraft.transformation.TransformationMaterialStore;
import com.butchercraft.transformation.TransformationOutput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationInventoryMaterialStoreTest {
    private static final EngineId BEEF_FOREQUARTER = EngineId.of("butchercraft:beef_forequarter");
    private static final EngineId BEEF_CHUCK = EngineId.of("butchercraft:beef_chuck");

    @Test
    void inputStoreExposesProductStackMaterialQuantity() {
        WorkstationInventory inventory = bandsawInventory();
        inventory.setInputInternal(ModItems.BEEF_FOREQUARTER_TEST.get().getDefaultInstance());

        TransformationMaterialStore store = WorkstationInventoryMaterialStore.inputStore(inventory);

        assertEquals(ProductQuantity.grams(100_000), store.quantity(BEEF_FOREQUARTER).orElseThrow());
        assertEquals(List.of(BEEF_FOREQUARTER), store.materials().stream()
                .map(MaterialAmount::materialId)
                .toList());
    }

    @Test
    void emptyBandsawOutputStoreAcceptsBuiltInForequarterOutputs() {
        WorkstationInventory inventory = bandsawInventory();

        TransformationMaterialStore store = WorkstationInventoryMaterialStore.outputStore(inventory);

        assertTrue(store.canInsertAll(bandsawOutputs()));
    }

    @Test
    void occupiedBandsawOutputStoreRejectsBuiltInForequarterOutputs() {
        WorkstationInventory inventory = bandsawInventory();
        inventory.setOutputInternal(ModItems.BEEF_CHUCK_TEST.get().getDefaultInstance());

        TransformationMaterialStore store = WorkstationInventoryMaterialStore.outputStore(inventory);

        assertEquals(ProductQuantity.grams(30_000), store.quantity(BEEF_CHUCK).orElseThrow());
        assertFalse(store.canInsertAll(bandsawOutputs()));
    }

    private static WorkstationInventory bandsawInventory() {
        return new WorkstationInventory(BandsawWorkstation.capability(), () -> {});
    }

    private static List<MaterialAmount> bandsawOutputs() {
        return BuiltInTransformationRegistry.breakBeefForequarter().outputs().stream()
                .map(TransformationOutput::producedAmount)
                .toList();
    }
}
