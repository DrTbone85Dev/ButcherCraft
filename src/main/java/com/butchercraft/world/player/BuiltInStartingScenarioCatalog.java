package com.butchercraft.world.player;

import java.util.List;
import java.util.Optional;

public final class BuiltInStartingScenarioCatalog {
    public static final int STARTING_SCENARIO_COUNT = 6;

    private BuiltInStartingScenarioCatalog() {
    }

    public static PlayerRegistry createRegistry() {
        return PlayerRegistry.of(scenarios());
    }

    public static List<StartingScenario> scenarios() {
        return List.of(
                scenario(
                        "inherited_family_business",
                        "Inherited Family Business",
                        StartingScenarioType.INHERITED_FAMILY_BUSINESS,
                        List.of(CareerProfile.FAMILY_SUCCESSOR, CareerProfile.APPRENTICE_BUTCHER),
                        InitialReputation.LOCAL_FAMILY_NAME,
                        assets(
                                "future_inherited_business",
                                "future_inherited_property",
                                "future_family_shop_equipment",
                                "future_family_supplier_relationship",
                                "future_estate_financing_placeholder"
                        ),
                        Optional.of(new InheritanceRecord(
                                "future_family_previous_owner",
                                "future_inherited_business",
                                "future_inherited_property",
                                LegacyAcquisitionType.INHERITED,
                                "The player legacy begins with an inherited business record placeholder; no inheritance gameplay is attached."
                        )),
                        List.of(
                                relationship(StartingRelationshipType.FAMILY_TIE, "future_family_previous_owner"),
                                relationship(StartingRelationshipType.SUPPLIER_FAMILIARITY, "future_family_supplier_relationship"),
                                relationship(StartingRelationshipType.COMMUNITY_REPUTATION, "future_inherited_business")
                        ),
                        legacy(1, 1, InitialReputation.LOCAL_FAMILY_NAME, "Family trade records predate the player's active operation."),
                        "A future player may begin as a family successor stepping into an existing commercial identity.",
                        "This scenario preserves inherited history without creating family NPCs, wills, finances, or ownership gameplay."
                ),
                scenario(
                        "vacant_property_purchase",
                        "Vacant Property Purchase",
                        StartingScenarioType.VACANT_PROPERTY_PURCHASE,
                        List.of(CareerProfile.INDEPENDENT_ENTREPRENEUR),
                        InitialReputation.UNKNOWN_NEWCOMER,
                        assets(
                                "future_startup_business_record",
                                "future_vacant_property",
                                "future_basic_shop_equipment",
                                "future_introductory_supplier_relationship",
                                "future_purchase_financing_placeholder"
                        ),
                        Optional.empty(),
                        List.of(
                                relationship(StartingRelationshipType.EXISTING_BUSINESS_CONTACT, "future_vacant_property"),
                                relationship(StartingRelationshipType.SUPPLIER_FAMILIARITY, "future_introductory_supplier_relationship")
                        ),
                        legacy(0, 0, InitialReputation.UNKNOWN_NEWCOMER, "No operating legacy exists before the property purchase."),
                        "A future player may purchase a vacant commercial property and establish a new business identity.",
                        "This scenario prepares property-purchase starts without implementing money, deeds, or renovation gameplay."
                ),
                scenario(
                        "existing_business_manager",
                        "Existing Business Manager",
                        StartingScenarioType.EXISTING_BUSINESS_MANAGER,
                        List.of(CareerProfile.CORPORATE_MANAGER),
                        InitialReputation.CORPORATE_PLACED,
                        assets(
                                "future_managed_business",
                                "future_managed_property",
                                "future_managed_facility_equipment",
                                "future_managed_supplier_relationship",
                                "future_corporate_budget_placeholder"
                        ),
                        Optional.empty(),
                        List.of(
                                relationship(StartingRelationshipType.EXISTING_BUSINESS_CONTACT, "future_managed_business"),
                                relationship(StartingRelationshipType.SUPPLIER_FAMILIARITY, "future_managed_supplier_relationship"),
                                relationship(StartingRelationshipType.COMMUNITY_REPUTATION, "future_corporate_budget_placeholder")
                        ),
                        legacy(0, 0, InitialReputation.CORPORATE_PLACED, "The player's authority is tied to an assigned management role."),
                        "A future player may manage an existing business already embedded in the local trade network.",
                        "This scenario prepares management starts without adding corporate systems, payroll, or active finances."
                ),
                scenario(
                        "startup_operation",
                        "Startup Operation",
                        StartingScenarioType.STARTUP_OPERATION,
                        List.of(CareerProfile.INDEPENDENT_ENTREPRENEUR, CareerProfile.APPRENTICE_BUTCHER),
                        InitialReputation.TRUSTED_APPRENTICE,
                        assets(
                                "future_startup_operation",
                                "future_startup_location",
                                "future_apprentice_tooling",
                                "future_training_supplier_relationship",
                                "future_small_business_financing_placeholder"
                        ),
                        Optional.empty(),
                        List.of(
                                relationship(StartingRelationshipType.COMMUNITY_REPUTATION, "future_apprentice_tooling"),
                                relationship(StartingRelationshipType.SUPPLIER_FAMILIARITY, "future_training_supplier_relationship")
                        ),
                        legacy(0, 0, InitialReputation.TRUSTED_APPRENTICE, "The player begins with skill history rather than ownership history."),
                        "A future player may start small after training in the regional industry.",
                        "This scenario prepares apprentice-to-owner starts without implementing skill progression or equipment ownership."
                ),
                scenario(
                        "county_contract",
                        "County Contract",
                        StartingScenarioType.COUNTY_CONTRACT,
                        List.of(CareerProfile.COUNTY_PROCESSOR),
                        InitialReputation.COUNTY_BACKED,
                        assets(
                                "future_county_contract_business",
                                "future_county_contract_property",
                                "future_county_processing_equipment",
                                "future_county_supplier_relationship",
                                "future_county_contract_placeholder"
                        ),
                        Optional.empty(),
                        List.of(
                                relationship(StartingRelationshipType.COMMUNITY_REPUTATION, "future_county_contract_placeholder"),
                                relationship(StartingRelationshipType.EXISTING_BUSINESS_CONTACT, "future_county_contract_business"),
                                relationship(StartingRelationshipType.SUPPLIER_FAMILIARITY, "future_county_supplier_relationship")
                        ),
                        legacy(0, 0, InitialReputation.COUNTY_BACKED, "The player begins with a public contract identity placeholder."),
                        "A future player may enter the industry through a county-backed processing assignment.",
                        "This scenario prepares public-contract starts without implementing contracts, inspections, or municipal gameplay."
                ),
                scenario(
                        "cooperative_assignment",
                        "Cooperative Assignment",
                        StartingScenarioType.COOPERATIVE_ASSIGNMENT,
                        List.of(CareerProfile.COOPERATIVE_MANAGER),
                        InitialReputation.COOPERATIVE_BACKED,
                        assets(
                                "future_cooperative_business",
                                "future_cooperative_property",
                                "future_cooperative_equipment",
                                "future_cooperative_supplier_relationship",
                                "future_cooperative_capital_placeholder"
                        ),
                        Optional.empty(),
                        List.of(
                                relationship(StartingRelationshipType.EXISTING_BUSINESS_CONTACT, "future_cooperative_business"),
                                relationship(StartingRelationshipType.SUPPLIER_FAMILIARITY, "future_cooperative_supplier_relationship"),
                                relationship(StartingRelationshipType.COMMUNITY_REPUTATION, "future_cooperative_capital_placeholder")
                        ),
                        legacy(0, 0, InitialReputation.COOPERATIVE_BACKED, "The player begins with cooperative backing rather than personal ownership."),
                        "A future player may operate as the appointed manager of a producer cooperative.",
                        "This scenario prepares cooperative starts without implementing member voting, dividends, or economy behavior."
                )
        );
    }

    private static StartingScenario scenario(
            String id,
            String displayName,
            StartingScenarioType scenarioType,
            List<CareerProfile> careerProfiles,
            InitialReputation initialReputation,
            StartingAssets assets,
            Optional<InheritanceRecord> inheritanceRecord,
            List<StartingRelationship> relationships,
            LegacyProgress legacyProgress,
            String scenarioSummary,
            String backgroundSummary
    ) {
        return new StartingScenario(
                new StartingScenarioId(id),
                displayName,
                scenarioType,
                careerProfiles,
                "future_starting_settlement",
                initialReputation,
                assets,
                inheritanceRecord,
                relationships,
                legacyProgress,
                scenarioSummary,
                backgroundSummary
        );
    }

    private static StartingAssets assets(
            String businessReference,
            String propertyReference,
            String equipmentPlaceholder,
            String supplierRelationshipPlaceholder,
            String financialPlaceholder
    ) {
        return new StartingAssets(
                businessReference,
                propertyReference,
                List.of(equipmentPlaceholder),
                List.of(supplierRelationshipPlaceholder),
                List.of(financialPlaceholder)
        );
    }

    private static StartingRelationship relationship(StartingRelationshipType type, String reference) {
        return new StartingRelationship(
                type,
                reference,
                "Historical starting relationship placeholder for future player legacy systems."
        );
    }

    private static LegacyProgress legacy(
            int generationsOperated,
            int businessesOwned,
            InitialReputation reputation,
            String legacySummary
    ) {
        return new LegacyProgress(
                generationsOperated,
                businessesOwned,
                List.of("future_player_legacy_milestone"),
                reputation,
                legacySummary
        );
    }
}
