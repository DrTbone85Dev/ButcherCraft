package com.butchercraft.processing.definition;

import com.butchercraft.registration.ModDataPackRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public record DefinitionRegistryView(
        Map<ResourceLocation, SpeciesDefinition> species,
        Map<ResourceLocation, ProcessingProfileDefinition> processingProfiles,
        Map<ResourceLocation, ProductDefinition> products,
        Map<ResourceLocation, ProcessingOperationDefinition> operations
) {
    public DefinitionRegistryView {
        species = immutableSortedCopy(species);
        processingProfiles = immutableSortedCopy(processingProfiles);
        products = immutableSortedCopy(products);
        operations = immutableSortedCopy(operations);
    }

    public static DefinitionRegistryView empty() {
        return new DefinitionRegistryView(Map.of(), Map.of(), Map.of(), Map.of());
    }

    public static DefinitionRegistryLoadResult fromRegistryAccess(RegistryAccess registryAccess) {
        Objects.requireNonNull(registryAccess, "registryAccess");
        RegistrySnapshot<SpeciesDefinition> species = snapshot(registryAccess, ModDataPackRegistries.SPECIES);
        RegistrySnapshot<ProcessingProfileDefinition> profiles = snapshot(registryAccess, ModDataPackRegistries.PROCESSING_PROFILE);
        RegistrySnapshot<ProductDefinition> products = snapshot(registryAccess, ModDataPackRegistries.PRODUCT);
        RegistrySnapshot<ProcessingOperationDefinition> operations = snapshot(registryAccess, ModDataPackRegistries.PROCESSING_OPERATION);

        DefinitionValidationReport report = DefinitionValidationReport.combine(List.of(
                missingRegistryReport(species.available(), "species_registry_missing", "Species datapack registry is not available"),
                missingRegistryReport(profiles.available(), "processing_profile_registry_missing", "Processing-profile datapack registry is not available"),
                missingRegistryReport(products.available(), "product_registry_missing", "Product datapack registry is not available"),
                missingRegistryReport(operations.available(), "processing_operation_registry_missing", "Processing-operation datapack registry is not available")
        ));

        return new DefinitionRegistryLoadResult(
                species.available(),
                profiles.available(),
                products.available(),
                operations.available(),
                new DefinitionRegistryView(species.entries(), profiles.entries(), products.entries(), operations.entries()),
                report
        );
    }

    private static DefinitionValidationReport missingRegistryReport(boolean available, String code, String explanation) {
        if (available) {
            return DefinitionValidationReport.EMPTY;
        }
        return DefinitionValidationReport.of(DefinitionValidationIssue.error(code, explanation));
    }

    private static <T> RegistrySnapshot<T> snapshot(
            RegistryAccess registryAccess,
            ResourceKey<Registry<T>> registryKey
    ) {
        Optional<HolderLookup.RegistryLookup<T>> lookup = registryAccess.lookup(registryKey);
        if (lookup.isEmpty()) {
            return new RegistrySnapshot<>(false, Map.of());
        }
        Map<ResourceLocation, T> entries = lookup.orElseThrow().listElements()
                .sorted(Comparator.comparing(reference -> reference.key().location().toString()))
                .collect(Collectors.toMap(
                        reference -> reference.key().location(),
                        reference -> reference.value(),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
        return new RegistrySnapshot<>(true, entries);
    }

    private static <T> Map<ResourceLocation, T> immutableSortedCopy(Map<ResourceLocation, T> input) {
        Map<ResourceLocation, T> sorted = Objects.requireNonNull(input, "input").entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
        return Collections.unmodifiableMap(sorted);
    }

    private record RegistrySnapshot<T>(boolean available, Map<ResourceLocation, T> entries) {
    }
}
