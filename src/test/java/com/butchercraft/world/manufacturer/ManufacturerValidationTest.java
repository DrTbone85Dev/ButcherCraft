package com.butchercraft.world.manufacturer;

import com.butchercraft.world.identity.BuiltInRegionCatalog;
import com.butchercraft.world.identity.RegionCatalog;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ManufacturerValidationTest {
    @Test
    void manufacturerRejectsMalformedCoreFields() {
        assertThrows(IllegalArgumentException.class, () -> validManufacturer(" "));
        assertThrows(IllegalArgumentException.class, () -> manufacturerWithYear(1849));
        assertThrows(IllegalArgumentException.class, () -> manufacturerWithYear(2027));
        assertThrows(IllegalArgumentException.class, () -> manufacturerWithCategories(Set.of()));
        assertThrows(NullPointerException.class, () -> manufacturerWithPhilosophy(null));
        assertThrows(IllegalArgumentException.class, () -> manufacturerWithHistory("Only one sentence."));
        assertThrows(IllegalArgumentException.class, () -> manufacturerWithSpecialties(List.of()));
        assertThrows(NullPointerException.class, () -> manufacturerWithBranding(null));
    }

    @Test
    void brandingAndHeadquartersRejectMalformedData() {
        assertThrows(IllegalArgumentException.class, () -> new Headquarters(" ", "Town"));
        assertThrows(IllegalArgumentException.class, () -> new Headquarters(BuiltInRegionCatalog.TIMBER_RIDGE, " "));
        assertThrows(IllegalArgumentException.class, () -> new ManufacturerBranding("green", "#FFFFFF", "#000000", "mark"));
        assertThrows(IllegalArgumentException.class, () -> new ManufacturerBranding("#FFFFFF", "#000000", "#111111", " "));
    }

    @Test
    void registryRejectsDuplicateIdsNamesAndSlogans() {
        Manufacturer first = validManufacturer("first");
        Manufacturer duplicateId = new Manufacturer(
                first.id(),
                "Second Name",
                first.foundingYear(),
                first.headquarters(),
                first.categories(),
                first.marketTier(),
                first.engineeringPhilosophy(),
                "Second history begins here. It stays valid for duplicate testing.",
                "Second slogan.",
                first.branding(),
                first.primarySpecialties(),
                first.reputation(),
                first.websitePlaceholder(),
                first.catalogDescriptionPlaceholder()
        );
        Manufacturer duplicateName = new Manufacturer(
                "second",
                first.displayName(),
                first.foundingYear(),
                first.headquarters(),
                first.categories(),
                first.marketTier(),
                first.engineeringPhilosophy(),
                "Second history begins here. It stays valid for duplicate testing.",
                "Second slogan.",
                first.branding(),
                first.primarySpecialties(),
                first.reputation(),
                first.websitePlaceholder(),
                first.catalogDescriptionPlaceholder()
        );
        Manufacturer duplicateSlogan = new Manufacturer(
                "third",
                "Third Name",
                first.foundingYear(),
                first.headquarters(),
                first.categories(),
                first.marketTier(),
                first.engineeringPhilosophy(),
                "Third history begins here. It stays valid for duplicate testing.",
                first.slogan(),
                first.branding(),
                first.primarySpecialties(),
                first.reputation(),
                first.websitePlaceholder(),
                first.catalogDescriptionPlaceholder()
        );

        assertThrows(IllegalArgumentException.class, () -> ManufacturerRegistry.of(List.of(first, duplicateId), RegionCatalog.builtIn()));
        assertThrows(IllegalArgumentException.class, () -> ManufacturerRegistry.of(List.of(first, duplicateName), RegionCatalog.builtIn()));
        assertThrows(IllegalArgumentException.class, () -> ManufacturerRegistry.of(List.of(first, duplicateSlogan), RegionCatalog.builtIn()));
    }

    @Test
    void registryRejectsInvalidHeadquartersRegion() {
        Manufacturer manufacturer = new Manufacturer(
                "invalid_region",
                "Invalid Region",
                1950,
                new Headquarters("unknown_region", "Nowhere"),
                EnumSet.of(ManufacturerCategory.PROCESSING_EQUIPMENT),
                ManufacturerTier.LOCAL,
                EngineeringPhilosophy.BUILT_TO_LAST,
                "This company exists only for validation testing. It should never be accepted by the registry.",
                "Invalid on purpose.",
                new ManufacturerBranding("#123456", "#654321", "#ABCDEF", "Test mark"),
                List.of("test specialty"),
                "Invalid regional reference.",
                "https://manufacturers.invalid/invalid-region",
                "Invalid catalog placeholder."
        );

        assertThrows(IllegalArgumentException.class, () -> ManufacturerRegistry.of(List.of(manufacturer), RegionCatalog.builtIn()));
    }

    private static Manufacturer validManufacturer(String id) {
        return new Manufacturer(
                id,
                "Validation Manufacturer " + id,
                1950,
                new Headquarters(BuiltInRegionCatalog.PRAIRIE_COMMONWEALTH, "Validation Town"),
                EnumSet.of(ManufacturerCategory.PROCESSING_EQUIPMENT),
                ManufacturerTier.LOCAL,
                EngineeringPhilosophy.BUILT_TO_LAST,
                "This company exists only for validation testing. It has enough history to satisfy the model.",
                "Validation slogan " + id,
                new ManufacturerBranding("#123456", "#654321", "#ABCDEF", "Test mark"),
                List.of("test specialty"),
                "Validation reputation.",
                "https://manufacturers.invalid/validation-" + id,
                "Validation catalog placeholder."
        );
    }

    private static Manufacturer manufacturerWithYear(int year) {
        Manufacturer valid = validManufacturer("year");
        return new Manufacturer(
                valid.id(),
                valid.displayName(),
                year,
                valid.headquarters(),
                valid.categories(),
                valid.marketTier(),
                valid.engineeringPhilosophy(),
                valid.companyHistory(),
                valid.slogan(),
                valid.branding(),
                valid.primarySpecialties(),
                valid.reputation(),
                valid.websitePlaceholder(),
                valid.catalogDescriptionPlaceholder()
        );
    }

    private static Manufacturer manufacturerWithCategories(Set<ManufacturerCategory> categories) {
        Manufacturer valid = validManufacturer("categories");
        return new Manufacturer(
                valid.id(),
                valid.displayName(),
                valid.foundingYear(),
                valid.headquarters(),
                categories,
                valid.marketTier(),
                valid.engineeringPhilosophy(),
                valid.companyHistory(),
                valid.slogan(),
                valid.branding(),
                valid.primarySpecialties(),
                valid.reputation(),
                valid.websitePlaceholder(),
                valid.catalogDescriptionPlaceholder()
        );
    }

    private static Manufacturer manufacturerWithPhilosophy(EngineeringPhilosophy philosophy) {
        Manufacturer valid = validManufacturer("philosophy");
        return new Manufacturer(
                valid.id(),
                valid.displayName(),
                valid.foundingYear(),
                valid.headquarters(),
                valid.categories(),
                valid.marketTier(),
                philosophy,
                valid.companyHistory(),
                valid.slogan(),
                valid.branding(),
                valid.primarySpecialties(),
                valid.reputation(),
                valid.websitePlaceholder(),
                valid.catalogDescriptionPlaceholder()
        );
    }

    private static Manufacturer manufacturerWithHistory(String history) {
        Manufacturer valid = validManufacturer("history");
        return new Manufacturer(
                valid.id(),
                valid.displayName(),
                valid.foundingYear(),
                valid.headquarters(),
                valid.categories(),
                valid.marketTier(),
                valid.engineeringPhilosophy(),
                history,
                valid.slogan(),
                valid.branding(),
                valid.primarySpecialties(),
                valid.reputation(),
                valid.websitePlaceholder(),
                valid.catalogDescriptionPlaceholder()
        );
    }

    private static Manufacturer manufacturerWithSpecialties(List<String> specialties) {
        Manufacturer valid = validManufacturer("specialties");
        return new Manufacturer(
                valid.id(),
                valid.displayName(),
                valid.foundingYear(),
                valid.headquarters(),
                valid.categories(),
                valid.marketTier(),
                valid.engineeringPhilosophy(),
                valid.companyHistory(),
                valid.slogan(),
                valid.branding(),
                specialties,
                valid.reputation(),
                valid.websitePlaceholder(),
                valid.catalogDescriptionPlaceholder()
        );
    }

    private static Manufacturer manufacturerWithBranding(ManufacturerBranding branding) {
        Manufacturer valid = validManufacturer("branding");
        return new Manufacturer(
                valid.id(),
                valid.displayName(),
                valid.foundingYear(),
                valid.headquarters(),
                valid.categories(),
                valid.marketTier(),
                valid.engineeringPhilosophy(),
                valid.companyHistory(),
                valid.slogan(),
                branding,
                valid.primarySpecialties(),
                valid.reputation(),
                valid.websitePlaceholder(),
                valid.catalogDescriptionPlaceholder()
        );
    }
}
