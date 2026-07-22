package com.butchercraft.world.manufacturer;

import com.butchercraft.world.identity.BuiltInRegionCatalog;
import com.butchercraft.world.identity.RegionCatalog;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManufacturerRegistryTest {
    private final ManufacturerRegistry registry = ManufacturerRegistry.builtIn();

    @Test
    void builtInRegistryLoadsExactlyThirtyManufacturers() {
        assertEquals(BuiltInManufacturerCatalog.MANUFACTURER_COUNT, registry.size());
        assertEquals(30, registry.size());
    }

    @Test
    void builtInManufacturerIdsNamesAndSlogansAreUnique() {
        assertEquals(registry.size(), uniqueCount(Manufacturer::id));
        assertEquals(registry.size(), uniqueCount(Manufacturer::displayName));
        assertEquals(registry.size(), uniqueCount(Manufacturer::slogan));
    }

    @Test
    void allManufacturersReferenceValidHandcraftedRegions() {
        RegionCatalog regions = RegionCatalog.builtIn();

        for (Manufacturer manufacturer : registry.manufacturers()) {
            assertTrue(regions.contains(manufacturer.headquarters().regionId()), manufacturer.id());
        }
    }

    @Test
    void allCategoriesTiersAndEngineeringPhilosophiesAreRepresented() {
        for (ManufacturerCategory category : ManufacturerCategory.values()) {
            assertFalse(registry.findByCategory(category).isEmpty(), category.serializedName());
        }
        for (ManufacturerTier tier : ManufacturerTier.values()) {
            assertFalse(registry.findByTier(tier).isEmpty(), tier.serializedName());
        }
        for (EngineeringPhilosophy philosophy : EngineeringPhilosophy.values()) {
            assertTrue(registry.stream().anyMatch(manufacturer -> manufacturer.engineeringPhilosophy() == philosophy),
                    philosophy.serializedName());
        }
    }

    @Test
    void manufacturersAreDistributedAcrossAllRegions() {
        assertEquals(6, registry.findByRegion(BuiltInRegionCatalog.PRAIRIE_COMMONWEALTH).size());
        assertEquals(6, registry.findByRegion(BuiltInRegionCatalog.IRON_VALLEY).size());
        assertEquals(6, registry.findByRegion(BuiltInRegionCatalog.GREAT_RIVER_BASIN).size());
        assertEquals(6, registry.findByRegion(BuiltInRegionCatalog.HIGH_PLAINS_TERRITORY).size());
        assertEquals(6, registry.findByRegion(BuiltInRegionCatalog.TIMBER_RIDGE).size());
    }

    @Test
    void lookupByIdCategoryTierRegionAndSearchWorks() {
        Manufacturer prairieForge = registry.find("prairie_forge_works").orElseThrow();

        assertTrue(registry.contains("prairie_forge_works"));
        assertEquals("Prairie Forge Works", prairieForge.displayName());
        assertTrue(registry.findByCategory(ManufacturerCategory.PROCESSING_EQUIPMENT).contains(prairieForge));
        assertTrue(registry.findByTier(ManufacturerTier.REGIONAL).contains(prairieForge));
        assertTrue(registry.findByRegion(BuiltInRegionCatalog.PRAIRIE_COMMONWEALTH).contains(prairieForge));
        assertTrue(registry.search("rugged machines").contains(prairieForge));
        assertTrue(registry.search("serviceability").stream()
                .allMatch(manufacturer -> manufacturer.engineeringPhilosophy() == EngineeringPhilosophy.SERVICEABILITY
                        || manufacturer.reputation().toLowerCase().contains("service")
                        || manufacturer.primarySpecialties().stream().anyMatch(specialty -> specialty.toLowerCase().contains("service"))));
    }

    @Test
    void registryOrderingIsDeterministicAndIndependentOfInputOrder() {
        ManufacturerRegistry reversed = ManufacturerRegistry.of(
                BuiltInManufacturerCatalog.manufacturers().reversed(),
                RegionCatalog.builtIn()
        );
        List<String> expectedIds = registry.manufacturers().stream()
                .map(Manufacturer::id)
                .sorted()
                .toList();

        assertEquals(expectedIds, registry.manufacturers().stream().map(Manufacturer::id).toList());
        assertEquals(registry.manufacturers(), reversed.manufacturers());
    }

    @Test
    void manufacturerValueObjectsAreImmutableAndFuturePlaceholdersArePopulated() {
        Manufacturer manufacturer = registry.find("maplewood_cutlery_house").orElseThrow();

        assertFalse(manufacturer.websitePlaceholder().isBlank());
        assertFalse(manufacturer.catalogDescriptionPlaceholder().isBlank());
        assertFalse(manufacturer.branding().visualIdentity().isBlank());
        assertEquals(3, manufacturer.branding().colors().size());
        assertTrue(manufacturer.companyHistory().split("[.!?]").length >= 2);
        assertThrowsUnsupported(() -> manufacturer.categories().add(ManufacturerCategory.UTILITIES));
        assertThrowsUnsupported(() -> manufacturer.primarySpecialties().add("new specialty"));
        assertThrowsUnsupported(() -> manufacturer.branding().colors().add("#000000"));
    }

    @Test
    void catalogDistributionRemainsIntentional() {
        Map<ManufacturerTier, Long> tiers = registry.stream()
                .collect(Collectors.groupingBy(Manufacturer::marketTier, Collectors.counting()));
        Map<String, Long> regions = registry.stream()
                .collect(Collectors.groupingBy(manufacturer -> manufacturer.headquarters().regionId(), Collectors.counting()));

        assertEquals(Map.of(
                ManufacturerTier.LOCAL, 8L,
                ManufacturerTier.REGIONAL, 10L,
                ManufacturerTier.NATIONAL, 8L,
                ManufacturerTier.INDUSTRY_LEADER, 4L
        ), tiers);
        assertEquals(RegionCatalog.builtIn().regions().stream()
                .map(region -> Map.entry(region.id(), 6L))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)), regions);
        assertEquals(registry.stream()
                .map(Manufacturer::id)
                .sorted(Comparator.naturalOrder())
                .toList(), registry.manufacturers().stream().map(Manufacturer::id).toList());
    }

    private long uniqueCount(Function<Manufacturer, String> field) {
        return registry.stream().map(field).collect(Collectors.toSet()).size();
    }

    private static void assertThrowsUnsupported(Runnable action) {
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Expected immutable collection to reject mutation");
    }
}
