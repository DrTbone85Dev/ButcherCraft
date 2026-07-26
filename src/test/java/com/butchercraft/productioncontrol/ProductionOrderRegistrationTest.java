package com.butchercraft.productioncontrol;

import com.butchercraft.registration.ModClientRegistrationStatus;
import com.butchercraft.registration.ModDataComponents;
import com.butchercraft.registration.ModItems;
import com.butchercraft.registration.ModMenuTypes;
import com.butchercraft.test.TestProjectPaths;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionOrderRegistrationTest {
    private static final List<String> REQUIRED_TRANSLATION_KEYS = List.of(
            "item.butchercraft.production_order",
            "container.butchercraft.production_order",
            "tooltip.butchercraft.production_order.chain",
            "tooltip.butchercraft.production_order.new",
            "tooltip.butchercraft.production_order.linked",
            "tooltip.butchercraft.production_order.assign",
            "message.butchercraft.production_order.created",
            "message.butchercraft.production_order.unsupported_template",
            "message.butchercraft.production_order.unsupported_workstation",
            "message.butchercraft.production_order.stale_reference",
            "message.butchercraft.production_order.create_rejected",
            "message.butchercraft.production_order.grinder_assigned",
            "message.butchercraft.production_order.patty_former_assigned",
            "message.butchercraft.production_order.assignment_rejected",
            "message.butchercraft.production_order.cancel_no_run",
            "message.butchercraft.production_order.cancelled",
            "message.butchercraft.production_order.cancel_rejected",
            "screen.butchercraft.production_order",
            "screen.butchercraft.production_order.chain",
            "screen.butchercraft.production_order.cancel",
            "screen.butchercraft.production_order.step.grinder",
            "screen.butchercraft.production_order.step.transfer",
            "screen.butchercraft.production_order.step.patty_former",
            "screen.butchercraft.production_order.step.grinder.unassigned",
            "screen.butchercraft.production_order.step.grinder.ready",
            "screen.butchercraft.production_order.step.grinder.running",
            "screen.butchercraft.production_order.step.grinder.complete",
            "screen.butchercraft.production_order.step.grinder.missing",
            "screen.butchercraft.production_order.step.patty_former.unassigned",
            "screen.butchercraft.production_order.step.patty_former.ready",
            "screen.butchercraft.production_order.step.patty_former.running",
            "screen.butchercraft.production_order.step.patty_former.complete",
            "screen.butchercraft.production_order.step.patty_former.missing",
            "screen.butchercraft.production_order.next.create_run",
            "screen.butchercraft.production_order.next.assign_grinder",
            "screen.butchercraft.production_order.next.load_beef_trim",
            "screen.butchercraft.production_order.next.wait_for_grinder",
            "screen.butchercraft.production_order.next.clear_grinder_output",
            "screen.butchercraft.production_order.next.move_ground_beef",
            "screen.butchercraft.production_order.next.assign_patty_former",
            "screen.butchercraft.production_order.next.load_ground_beef",
            "screen.butchercraft.production_order.next.wait_for_patty_former",
            "screen.butchercraft.production_order.next.clear_patty_former_output",
            "screen.butchercraft.production_order.next.collect_beef_patties",
            "screen.butchercraft.production_order.next.complete",
            "screen.butchercraft.production_order.next.cancelled",
            "screen.butchercraft.production_order.next.failed",
            "screen.butchercraft.production_order.next.unknown_outcome",
            "screen.butchercraft.production_order.next.stale_reference",
            "screen.butchercraft.production_order.status.awaiting_grinder_assignment",
            "screen.butchercraft.production_order.status.grinder_assigned",
            "screen.butchercraft.production_order.status.grinder_running",
            "screen.butchercraft.production_order.status.grinder_complete",
            "screen.butchercraft.production_order.status.awaiting_manual_transfer",
            "screen.butchercraft.production_order.status.awaiting_patty_former_assignment",
            "screen.butchercraft.production_order.status.patty_former_assigned",
            "screen.butchercraft.production_order.status.patty_former_running",
            "screen.butchercraft.production_order.status.patty_former_complete",
            "screen.butchercraft.production_order.status.complete",
            "screen.butchercraft.production_order.status.failed",
            "screen.butchercraft.production_order.status.unknown_outcome",
            "screen.butchercraft.production_order.status.cancelled_before_first_effect",
            "screen.butchercraft.production_order.status.stale",
            "screen.butchercraft.production_order.status.failure"
    );

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

    @Test
    void productionOrderTranslationsAreAvailableToRuntimeResources() throws IOException {
        assertProductionOrderTranslationsPresent("src/main/resources/assets/butchercraft/lang/en_us.json");
    }

    @Test
    void productionOrderTranslationsAreGeneratedConsistently() throws IOException {
        assertProductionOrderTranslationsPresent("src/generated/resources/assets/butchercraft/lang/en_us.json");
    }

    private static void assertProductionOrderTranslationsPresent(String relativePath) throws IOException {
        JsonObject language = JsonParser.parseString(Files.readString(
                TestProjectPaths.projectPath(relativePath)
        )).getAsJsonObject();

        assertEquals("Production Order", language.get("item.butchercraft.production_order").getAsString());
        assertEquals("Production Order", language.get("container.butchercraft.production_order").getAsString());
        assertEquals("Production Order", language.get("screen.butchercraft.production_order").getAsString());
        for (String key : REQUIRED_TRANSLATION_KEYS) {
            assertTrue(language.has(key), "Missing Production Order translation key in " + relativePath + ": " + key);
        }
    }
}
