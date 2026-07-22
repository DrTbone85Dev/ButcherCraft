package com.butchercraft.world.manufacturer;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record Manufacturer(
        String id,
        String displayName,
        int foundingYear,
        Headquarters headquarters,
        Set<ManufacturerCategory> categories,
        ManufacturerTier marketTier,
        EngineeringPhilosophy engineeringPhilosophy,
        String companyHistory,
        String slogan,
        ManufacturerBranding branding,
        List<String> primarySpecialties,
        String reputation,
        String websitePlaceholder,
        String catalogDescriptionPlaceholder
) {
    private static final int MIN_FOUNDING_YEAR = 1850;
    private static final int MAX_FOUNDING_YEAR = 2026;

    public Manufacturer {
        id = requireNonBlank(id, "id");
        displayName = requireNonBlank(displayName, "displayName");
        if (foundingYear < MIN_FOUNDING_YEAR || foundingYear > MAX_FOUNDING_YEAR) {
            throw new IllegalArgumentException("Manufacturer founding year is outside the supported range: " + foundingYear);
        }
        headquarters = Objects.requireNonNull(headquarters, "headquarters");
        categories = copyCategories(categories, id);
        marketTier = Objects.requireNonNull(marketTier, "marketTier");
        engineeringPhilosophy = Objects.requireNonNull(engineeringPhilosophy, "engineeringPhilosophy");
        companyHistory = requireHistory(companyHistory, id);
        slogan = requireNonBlank(slogan, "slogan");
        branding = Objects.requireNonNull(branding, "branding");
        primarySpecialties = copySpecialties(primarySpecialties, id);
        reputation = requireNonBlank(reputation, "reputation");
        websitePlaceholder = requireNonBlank(websitePlaceholder, "websitePlaceholder");
        catalogDescriptionPlaceholder = requireNonBlank(catalogDescriptionPlaceholder, "catalogDescriptionPlaceholder");
    }

    public boolean servesCategory(ManufacturerCategory category) {
        return categories.contains(category);
    }

    private static Set<ManufacturerCategory> copyCategories(Set<ManufacturerCategory> categories, String id) {
        Objects.requireNonNull(categories, "categories");
        if (categories.isEmpty()) {
            throw new IllegalArgumentException("Manufacturer " + id + " must define at least one category");
        }
        EnumSet<ManufacturerCategory> copied = EnumSet.noneOf(ManufacturerCategory.class);
        for (ManufacturerCategory category : categories) {
            copied.add(Objects.requireNonNull(category, "category"));
        }
        return Set.copyOf(copied);
    }

    private static List<String> copySpecialties(List<String> specialties, String id) {
        Objects.requireNonNull(specialties, "primarySpecialties");
        if (specialties.isEmpty()) {
            throw new IllegalArgumentException("Manufacturer " + id + " must define at least one primary specialty");
        }
        List<String> copied = List.copyOf(specialties);
        for (String specialty : copied) {
            requireNonBlank(specialty, "primarySpecialty");
        }
        return copied;
    }

    private static String requireHistory(String history, String id) {
        String value = requireNonBlank(history, "companyHistory");
        int sentences = sentenceCount(value);
        if (sentences < 2 || sentences > 4) {
            throw new IllegalArgumentException("Manufacturer " + id + " history must contain 2 to 4 sentences");
        }
        return value;
    }

    private static int sentenceCount(String value) {
        int count = 0;
        for (String part : value.split("[.!?]")) {
            if (!part.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Manufacturer " + fieldName + " must not be blank");
        }
        return value;
    }
}
