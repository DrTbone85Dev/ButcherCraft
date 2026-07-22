package com.butchercraft.world.identity;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionCatalogTest {
    @Test
    void builtInCatalogContainsAllFiveHandcraftedRegions() {
        RegionCatalog catalog = RegionCatalog.builtIn();

        assertEquals(5, catalog.size());
        assertTrue(catalog.contains(BuiltInRegionCatalog.PRAIRIE_COMMONWEALTH));
        assertTrue(catalog.contains(BuiltInRegionCatalog.IRON_VALLEY));
        assertTrue(catalog.contains(BuiltInRegionCatalog.GREAT_RIVER_BASIN));
        assertTrue(catalog.contains(BuiltInRegionCatalog.HIGH_PLAINS_TERRITORY));
        assertTrue(catalog.contains(BuiltInRegionCatalog.TIMBER_RIDGE));
    }

    @Test
    void regionIdentifiersAreUnique() {
        RegionCatalog catalog = RegionCatalog.builtIn();

        Set<String> ids = catalog.regions().stream()
                .map(RegionDefinition::id)
                .collect(Collectors.toSet());

        assertEquals(catalog.size(), ids.size());
    }

    @Test
    void builtInRegionsContainStableIdentityFieldsAndValidNamingProfiles() {
        RegionCatalog catalog = RegionCatalog.builtIn();

        for (RegionDefinition region : catalog.regions()) {
            assertPresent(region.id());
            assertPresent(region.displayName());
            assertPresent(region.description());
            assertPresent(region.agriculturalTendencies());
            assertPresent(region.economicProfile());
            assertPresent(region.culturalProfile());
            assertPresent(region.namingProfileId());
            for (NamingRole role : NamingRole.values()) {
                assertTrue(!catalog.namingProfile(region).namesFor(role).isEmpty());
            }
        }
    }

    @Test
    void catalogRejectsMissingAndDuplicateRegionDefinitions() {
        NamingProfile profile = validProfile("profile");
        RegionDefinition region = validRegion("region", profile.id());

        assertThrows(IllegalArgumentException.class, () -> RegionCatalog.of(List.of(), List.of(profile)));
        assertThrows(IllegalArgumentException.class, () -> RegionCatalog.of(List.of(region, region), List.of(profile)));
    }

    @Test
    void catalogRejectsUnsupportedNamingProfileReferences() {
        RegionDefinition region = validRegion("region", "missing_profile");

        assertThrows(IllegalArgumentException.class, () -> RegionCatalog.of(List.of(region), List.of(validProfile("profile"))));
    }

    @Test
    void catalogRejectsMalformedNamingProfiles() {
        NamingProfile duplicate = validProfile("profile");
        assertThrows(IllegalArgumentException.class, () -> RegionCatalog.of(
                List.of(validRegion("region", "profile")),
                List.of(duplicate, duplicate)
        ));

        Map<NamingRole, List<String>> missingRole = new EnumMap<>(NamingRole.class);
        missingRole.put(NamingRole.COUNTY_PRIMARY, List.of("Only County"));
        NamingProfile incomplete = new NamingProfile("incomplete", "Incomplete", missingRole);
        assertThrows(IllegalArgumentException.class, () -> RegionCatalog.of(
                List.of(validRegion("region", "incomplete")),
                List.of(incomplete)
        ));

        Map<NamingRole, List<String>> blankName = validNames();
        blankName.put(NamingRole.COUNTY_PRIMARY, List.of(" "));
        assertThrows(IllegalArgumentException.class, () -> new NamingProfile("blank", "Blank", blankName));
    }

    private static RegionDefinition validRegion(String id, String profileId) {
        return new RegionDefinition(id, "Region", "Description", "Agriculture", "Economy", "Culture", profileId);
    }

    private static NamingProfile validProfile(String id) {
        return new NamingProfile(id, "Profile", validNames());
    }

    private static Map<NamingRole, List<String>> validNames() {
        EnumMap<NamingRole, List<String>> names = new EnumMap<>(NamingRole.class);
        for (NamingRole role : NamingRole.values()) {
            names.put(role, List.of(role.serializedName() + " Name"));
        }
        return names;
    }

    private static void assertPresent(String value) {
        assertTrue(value != null && !value.isBlank());
    }
}
