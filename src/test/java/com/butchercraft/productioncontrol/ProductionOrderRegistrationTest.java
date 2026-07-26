package com.butchercraft.productioncontrol;

import com.butchercraft.registration.ModClientRegistrationStatus;
import com.butchercraft.registration.ModDataComponents;
import com.butchercraft.registration.ModItems;
import com.butchercraft.registration.ModMenuTypes;
import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionOrderRegistrationTest {
    @Test
    void productionOrderRegistrationsAreBound() {
        assertTrue(ModItems.PRODUCTION_ORDER.isBound());
        assertTrue(ModDataComponents.PRODUCTION_ORDER.isBound());
        assertTrue(ModMenuTypes.PRODUCTION_ORDER.isBound());
    }

    @Test
    void productionOrderAppearsInCreativeTabSource() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/registration/ModCreativeModeTabs.java"
        ));

        assertTrue(source.contains("output.accept(ModItems.PRODUCTION_ORDER.get().getDefaultInstance())"));
    }

    @Test
    void productionOrderClientScreenHasRegistrationStatusHook() throws IOException {
        String client = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/client/ButcherCraftClient.java"
        ));
        String status = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/registration/ModClientRegistrationStatus.java"
        ));

        assertTrue(client.contains("event.register(ModMenuTypes.PRODUCTION_ORDER.get(), ProductionOrderScreen::new)"));
        assertTrue(client.contains("ModClientRegistrationStatus.markProductionOrderScreenRegistered()"));
        assertTrue(status.contains("productionOrderScreenRegistered"));
        assertTrue(!ModClientRegistrationStatus.productionOrderScreenRegistered());
    }

    @Test
    void generatedProductionOrderModelUsesExistingPlaceholderTexture() throws IOException {
        String model = Files.readString(TestProjectPaths.projectPath(
                "src/generated/resources/assets/butchercraft/models/item/production_order.json"
        ));

        assertTrue(model.contains("\"layer0\": \"butchercraft:item/development_test_item\""));
    }
}
