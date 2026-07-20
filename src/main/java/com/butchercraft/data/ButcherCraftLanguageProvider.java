package com.butchercraft.data;

import com.butchercraft.ButcherCraft;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

final class ButcherCraftLanguageProvider extends LanguageProvider {
    ButcherCraftLanguageProvider(PackOutput output) {
        super(output, ButcherCraft.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup.butchercraft", "ButcherCraft");
        add(ModItems.DEVELOPMENT_TEST_ITEM.get(), "Development Test Item");
        add(ModItems.BEEF_TRIM_TEST.get(), "Beef Trim Test Product");
        add(ModItems.GROUND_BEEF_TEST.get(), "Ground Beef Test Product");
        add(ModItems.PORK_TRIM_TEST.get(), "Pork Trim Test Product");
        add(ModItems.GROUND_PORK_TEST.get(), "Ground Pork Test Product");
        add(ModItems.BISON_TRIM_TEST.get(), "Bison Trim Test Product");
        add(ModItems.GROUND_BISON_TEST.get(), "Ground Bison Test Product");
        add(ModItems.BEEF_FOREQUARTER_TEST.get(), "Beef Forequarter Test Product");
        add(ModItems.BEEF_CHUCK_TEST.get(), "Beef Chuck Test Product");
        add(ModItems.BEEF_RIB_TEST.get(), "Beef Rib Test Product");
        add(ModItems.BEEF_PACKER_BRISKET_TEST.get(), "Packer Brisket Test Product");
        add(ModItems.BEEF_PLATE_TEST.get(), "Beef Plate Test Product");
        add(ModItems.BEEF_SHANK_TEST.get(), "Beef Shank Test Product");
        add(ModItems.BEEF_FAT_TEST.get(), "Beef Fat Test Product");
        add(ModItems.BEEF_BONE_TEST.get(), "Beef Bone Test Product");
        add(ModBlocks.GRINDER.get(), "Grinder");
        add(ModBlocks.BANDSAW.get(), "Industrial Bandsaw");
        add(ModBlocks.BANDSAW_UPPER.get(), "Industrial Bandsaw");
        add(ModBlocks.DEVELOPMENT_PROCESSING_WORKSTATION.get(), "Development Processing Workstation");
        add("container.butchercraft.grinder", "Grinder");
        add("container.butchercraft.bandsaw", "Industrial Bandsaw");
        add("container.butchercraft.development_processing_workstation", "Development Processing Workstation");
        add("commands.butchercraft.info.title", "ButcherCraft");
        add("commands.butchercraft.info.version", "Version %s - Early Development / Foundation Update");
        add("commands.butchercraft.info.status", "Processing, inventory, employee, and business simulation systems are under active development.");
        add("commands.butchercraft.diagnostic", "ButcherCraft Diagnostic");
        add("definition.butchercraft.species.beef", "Beef");
        add("definition.butchercraft.species.pork", "Pork");
        add("definition.butchercraft.species.bison", "Bison");
        add("definition.butchercraft.processing_profile.red_meat", "Red Meat");
        add("definition.butchercraft.product.beef_trim", "Beef Trim");
        add("definition.butchercraft.product.ground_beef", "Ground Beef");
        add("definition.butchercraft.product.pork_trim", "Pork Trim");
        add("definition.butchercraft.product.ground_pork", "Ground Pork");
        add("definition.butchercraft.product.bison_trim", "Bison Trim");
        add("definition.butchercraft.product.ground_bison", "Ground Bison");
        add("definition.butchercraft.product.beef_forequarter", "Beef Forequarter");
        add("definition.butchercraft.product.beef_chuck", "Beef Chuck");
        add("definition.butchercraft.product.beef_rib", "Beef Rib");
        add("definition.butchercraft.product.beef_packer_brisket", "Packer Brisket");
        add("definition.butchercraft.product.beef_plate", "Beef Plate");
        add("definition.butchercraft.product.beef_shank", "Beef Shank");
        add("definition.butchercraft.product.beef_fat", "Beef Fat");
        add("definition.butchercraft.product.beef_bone", "Beef Bone");
        add("definition.butchercraft.processing_operation.grind_beef", "Grind Beef");
        add("definition.butchercraft.processing_operation.grind_pork", "Grind Pork");
        add("definition.butchercraft.processing_operation.grind_bison", "Grind Bison");
        add("definition.butchercraft.processing_operation.break_beef_forequarter", "Break Beef Forequarter");
        add("tooltip.butchercraft.product_data.product", "Product: %s");
        add("tooltip.butchercraft.product_data.source", "Source: %s");
        add("tooltip.butchercraft.product_data.state", "State: %s");
        add("tooltip.butchercraft.product_data.quantity", "Quantity: %s %s");
        add("tooltip.butchercraft.product_data.quality", "Quality: %s");
        add("tooltip.butchercraft.product_data.quality_score", "Quality score: %s");
        add("tooltip.butchercraft.product_data.missing", "Missing ButcherCraft product data");
        add("tooltip.butchercraft.product_data.invalid", "Invalid ButcherCraft product data");
        add("workstation.butchercraft.failure.no_input", "No input product");
        add("workstation.butchercraft.failure.input_not_product", "Input is not a ButcherCraft product");
        add("workstation.butchercraft.failure.missing_product_data", "Input is missing product data");
        add("workstation.butchercraft.failure.unknown_product_definition", "Product definition is not loaded");
        add("workstation.butchercraft.failure.product_data_mismatch", "Product data does not match definitions");
        add("workstation.butchercraft.failure.no_compatible_operation", "No compatible operation");
        add("workstation.butchercraft.failure.multiple_compatible_operations", "Select an operation");
        add("workstation.butchercraft.failure.operation_profile_mismatch", "Operation profile mismatch");
        add("workstation.butchercraft.failure.operation_capability_mismatch", "Workstation cannot perform this operation");
        add("workstation.butchercraft.failure.input_quantity_too_low", "Input quantity is too low");
        add("workstation.butchercraft.failure.input_quantity_too_high", "Input quantity is too high");
        add("workstation.butchercraft.failure.output_occupied", "Output slot is occupied");
        add("workstation.butchercraft.failure.output_incompatible", "Output slot is incompatible");
        add("workstation.butchercraft.failure.transaction_already_active", "Transaction is already active");
        add("workstation.butchercraft.failure.invalid_workstation_state", "Workstation state is invalid");
        add("workstation.butchercraft.failure.registry_not_available", "Processing definitions are unavailable");
        add("workstation.butchercraft.failure.processing_validation_rejected", "Processing validation rejected the input");
        add("workstation.butchercraft.failure.result_creation_failed", "Could not create processing result");
        add("workstation.butchercraft.state.idle", "Idle");
        add("workstation.butchercraft.state.ready", "Ready");
        add("workstation.butchercraft.state.processing", "Processing");
        add("workstation.butchercraft.state.blocked", "Blocked");
        add("workstation.butchercraft.state.complete", "Complete");
        add("workstation.butchercraft.state.error", "Error");
    }
}
