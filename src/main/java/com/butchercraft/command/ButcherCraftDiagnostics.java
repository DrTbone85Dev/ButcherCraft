package com.butchercraft.command;

import com.butchercraft.ButcherCraft;
import com.butchercraft.config.CommonConfig;
import com.butchercraft.entity.employee.EmployeeEntity;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.evaluation.ProcessingEvaluator;
import com.butchercraft.integration.employee.EmployeeWorkstationOperationService;
import com.butchercraft.machine.bandsaw.BandsawWorkstation;
import com.butchercraft.machine.grinder.GrinderWorkstation;
import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.processing.definition.DefinitionRegistryLoadResult;
import com.butchercraft.processing.definition.DefinitionRegistryView;
import com.butchercraft.processing.definition.DefinitionResolution;
import com.butchercraft.processing.definition.DefinitionValidationReport;
import com.butchercraft.processing.definition.ProcessingDefinitionResolver;
import com.butchercraft.processing.definition.ProcessingGraph;
import com.butchercraft.processing.definition.ProcessingProfileDefinition;
import com.butchercraft.processing.definition.ProductDefinition;
import com.butchercraft.processing.definition.ResolvedProcessingOperationDefinition;
import com.butchercraft.processing.definition.SpeciesDefinition;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.product.integration.ProductDataResult;
import com.butchercraft.product.integration.DevelopmentProductItemMappings;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.registration.ModDataComponents;
import com.butchercraft.registration.ModItems;
import com.butchercraft.registration.ModBlockEntityTypes;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModClientRegistrationStatus;
import com.butchercraft.registration.ModMenuTypes;
import com.butchercraft.workstation.DevelopmentWorkstationFixtures;
import com.butchercraft.workstation.PrototypeProcessingContextValues;
import com.butchercraft.workstation.WorkstationDuration;
import com.butchercraft.workstation.WorkstationOperationResolution;
import com.butchercraft.workstation.WorkstationOperationResolver;
import com.butchercraft.world.BusinessRuntimeCalendarService;
import com.butchercraft.world.EmployeeService;
import com.butchercraft.world.WorkstationReservationService;
import com.butchercraft.world.business.runtime.BusinessRuntimeObservationSnapshot;
import com.butchercraft.world.business.runtime.BusinessScheduleBoundary;
import com.butchercraft.workstation.reservation.WorkstationReservationFailure;
import com.butchercraft.workstation.reservation.WorkstationReservationRecord;
import com.butchercraft.workstation.reservation.WorkstationReservationResult;
import com.butchercraft.world.workforce.department.DepartmentId;
import com.butchercraft.world.workforce.department.DepartmentRecord;
import com.butchercraft.world.workforce.department.DepartmentAnchor;
import com.butchercraft.world.workforce.employee.EmployeeFailure;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.employee.EmployeeOperationResult;
import com.butchercraft.world.workforce.employee.EmployeePresenceObservation;
import com.butchercraft.world.workforce.employee.EmployeePresenceState;
import com.butchercraft.world.workforce.employee.EmployeeRecord;
import com.butchercraft.world.simulation.time.WorldTimeService;
import com.butchercraft.world.simulation.time.WorldTimeStatusSnapshot;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

public final class ButcherCraftDiagnostics {
    private static final ResourceLocation DEVELOPMENT_TEST_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "development_test_item");
    private static final ResourceLocation PRODUCT_DATA_COMPONENT_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "product_data");
    private static final ResourceLocation BEEF_TRIM_TEST_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "beef_trim_test");
    private static final ResourceLocation GROUND_BEEF_TEST_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "ground_beef_test");
    private static final ResourceLocation PORK_TRIM_TEST_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "pork_trim_test");
    private static final ResourceLocation GROUND_PORK_TEST_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "ground_pork_test");
    private static final ResourceLocation CHICKEN_TRIM_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "chicken_trim");
    private static final ResourceLocation GROUND_CHICKEN_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "ground_chicken");
    private static final ResourceLocation BISON_TRIM_TEST_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "bison_trim_test");
    private static final ResourceLocation GROUND_BISON_TEST_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "ground_bison_test");
    private static final ResourceLocation LAMB_TRIM_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "lamb_trim");
    private static final ResourceLocation GROUND_LAMB_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "ground_lamb");
    private static final ResourceLocation VENISON_TRIM_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "venison_trim");
    private static final ResourceLocation GROUND_VENISON_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "ground_venison");
    private static final ResourceLocation BEEF_FOREQUARTER_TEST_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "beef_forequarter_test");
    private static final ResourceLocation DEVELOPMENT_WORKSTATION_BLOCK_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "development_processing_workstation");
    private static final ResourceLocation GRINDER_BLOCK_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "grinder");
    private static final ResourceLocation BANDSAW_BLOCK_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "bandsaw");
    private static final String EMPLOYEE_ARGUMENT = "employee";
    private static final String EMPLOYEE_COMMAND_TAIL_ARGUMENT = "employee_command";
    private static final String DEPARTMENT_ARGUMENT = "department";
    private static final String DEPARTMENT_ANCHOR_POSITION_ARGUMENT = "anchor";
    private static final String WORKSTATION_POSITION_ARGUMENT = "position";
    private static final int DEPARTMENT_ANCHOR_PERMISSION_LEVEL = 2;
    private static final int EMPLOYEE_OPERATION_PERMISSION_LEVEL = 2;
    private static final SuggestionProvider<CommandSourceStack> EMPLOYEE_LOOKUP_SUGGESTIONS =
            (context, builder) -> suggestEmployeeReferences(context.getSource(), builder);
    private static final SuggestionProvider<CommandSourceStack> DEPARTMENT_LOOKUP_SUGGESTIONS =
            (context, builder) -> suggestDepartmentReferences(context.getSource(), builder);
    private static final SuggestionProvider<CommandSourceStack> EMPLOYEE_DEPARTMENT_LOOKUP_SUGGESTIONS =
            (context, builder) -> suggestEmployeeReferenceThenDepartment(context.getSource(), builder);
    private static final SuggestionProvider<CommandSourceStack> EMPLOYEE_WORKSTATION_LOOKUP_SUGGESTIONS =
            (context, builder) -> suggestEmployeeReferenceThenPosition(context.getSource(), builder);
    private static final SuggestionProvider<CommandSourceStack> WORKSTATION_POSITION_SUGGESTIONS =
            (context, builder) -> suggestWorkstationPosition(context.getSource(), builder);

    private ButcherCraftDiagnostics() {
    }

    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(ButcherCraft.MOD_ID)
                .then(Commands.literal("info")
                        .executes(context -> runInfo(context.getSource())))
                .then(Commands.literal("time")
                        .then(Commands.literal("status")
                                .executes(context -> runTimeStatus(context.getSource()))))
                .then(Commands.literal("business")
                        .then(Commands.literal("status")
                                .executes(context -> runBusinessStatus(context.getSource()))))
                .then(Commands.literal("employee")
                        .then(Commands.literal("create")
                                .executes(context -> runEmployeeCreate(context.getSource(), ""))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> runEmployeeCreate(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("list")
                                .executes(context -> runEmployeeList(context.getSource())))
                        .then(Commands.literal("status")
                                .then(Commands.argument(EMPLOYEE_ARGUMENT, StringArgumentType.greedyString())
                                        .suggests(EMPLOYEE_LOOKUP_SUGGESTIONS)
                                        .executes(context -> runEmployeeStatus(
                                                context.getSource(),
                                                StringArgumentType.getString(context, EMPLOYEE_ARGUMENT)))))
                        .then(Commands.literal("navigation")
                                .then(Commands.argument(EMPLOYEE_ARGUMENT, StringArgumentType.greedyString())
                                        .suggests(EMPLOYEE_LOOKUP_SUGGESTIONS)
                                        .executes(context -> runEmployeeNavigation(
                                                context.getSource(),
                                                StringArgumentType.getString(context, EMPLOYEE_ARGUMENT)))))
                        .then(Commands.literal("set-shift")
                                .then(Commands.argument(EMPLOYEE_COMMAND_TAIL_ARGUMENT, StringArgumentType.greedyString())
                                        .suggests(EMPLOYEE_LOOKUP_SUGGESTIONS)
                                        .executes(context -> runEmployeeSetShiftTail(
                                                context.getSource(),
                                                StringArgumentType.getString(context, EMPLOYEE_COMMAND_TAIL_ARGUMENT)))))
                        .then(Commands.literal("set-presence")
                                .then(Commands.argument(EMPLOYEE_COMMAND_TAIL_ARGUMENT, StringArgumentType.greedyString())
                                        .suggests(EMPLOYEE_LOOKUP_SUGGESTIONS)
                                        .executes(context -> runEmployeeSetPresenceTail(
                                                context.getSource(),
                                                StringArgumentType.getString(context, EMPLOYEE_COMMAND_TAIL_ARGUMENT)))))
                        .then(Commands.literal("assign-department")
                                .then(Commands.argument(EMPLOYEE_COMMAND_TAIL_ARGUMENT, StringArgumentType.greedyString())
                                        .suggests(EMPLOYEE_DEPARTMENT_LOOKUP_SUGGESTIONS)
                                        .executes(context -> runEmployeeAssignDepartmentTail(
                                                context.getSource(),
                                                StringArgumentType.getString(context, EMPLOYEE_COMMAND_TAIL_ARGUMENT)))))
                        .then(Commands.literal("assign-workstation")
                                .then(Commands.argument(EMPLOYEE_COMMAND_TAIL_ARGUMENT, StringArgumentType.greedyString())
                                        .suggests(EMPLOYEE_WORKSTATION_LOOKUP_SUGGESTIONS)
                                        .executes(context -> runEmployeeAssignWorkstationTail(
                                                 context.getSource(),
                                                 StringArgumentType.getString(context, EMPLOYEE_COMMAND_TAIL_ARGUMENT)))))
                        .then(Commands.literal("operate")
                                .requires(ButcherCraftDiagnostics::canOperateEmployee)
                                .then(Commands.argument(EMPLOYEE_ARGUMENT, StringArgumentType.greedyString())
                                        .suggests(EMPLOYEE_LOOKUP_SUGGESTIONS)
                                        .executes(context -> runEmployeeOperate(
                                                context.getSource(),
                                                StringArgumentType.getString(context, EMPLOYEE_ARGUMENT)))))
                        .then(Commands.literal("release-workstation")
                                .then(Commands.argument(EMPLOYEE_ARGUMENT, StringArgumentType.greedyString())
                                        .suggests(EMPLOYEE_LOOKUP_SUGGESTIONS)
                                        .executes(context -> runEmployeeReleaseWorkstation(
                                                context.getSource(),
                                                StringArgumentType.getString(context, EMPLOYEE_ARGUMENT))))))
                .then(Commands.literal("department")
                        .then(Commands.literal("list")
                                .executes(context -> runDepartmentList(context.getSource())))
                        .then(Commands.literal("status")
                                .then(Commands.argument(DEPARTMENT_ARGUMENT, StringArgumentType.word())
                                        .suggests(DEPARTMENT_LOOKUP_SUGGESTIONS)
                                        .executes(context -> runDepartmentStatus(
                                                context.getSource(),
                                                StringArgumentType.getString(context, DEPARTMENT_ARGUMENT)))))
                        .then(Commands.literal("set-anchor")
                                .requires(ButcherCraftDiagnostics::canMutateDepartmentAnchor)
                                .then(Commands.argument(DEPARTMENT_ARGUMENT, StringArgumentType.word())
                                        .suggests(DEPARTMENT_LOOKUP_SUGGESTIONS)
                                        .executes(context -> runDepartmentSetAnchor(
                                                context.getSource(),
                                                StringArgumentType.getString(context, DEPARTMENT_ARGUMENT),
                                                BlockPos.containing(context.getSource().getPosition())))
                                        .then(Commands.argument(
                                                        DEPARTMENT_ANCHOR_POSITION_ARGUMENT,
                                                        BlockPosArgument.blockPos())
                                                .executes(context -> runDepartmentSetAnchor(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, DEPARTMENT_ARGUMENT),
                                                        BlockPosArgument.getLoadedBlockPos(
                                                                context,
                                                                DEPARTMENT_ANCHOR_POSITION_ARGUMENT)))))))
                .then(Commands.literal("workstation")
                        .then(Commands.literal("reservations")
                                .executes(context -> runWorkstationReservations(context.getSource())))
                        .then(Commands.literal("status")
                                .then(Commands.argument(WORKSTATION_POSITION_ARGUMENT, StringArgumentType.greedyString())
                                        .suggests(WORKSTATION_POSITION_SUGGESTIONS)
                                        .executes(context -> runWorkstationStatus(
                                                context.getSource(),
                                                StringArgumentType.getString(context, WORKSTATION_POSITION_ARGUMENT))))))
                .then(Commands.literal("diagnostic")
                        .executes(context -> runDiagnostic(context.getSource()))
                        .then(DevelopmentCheckpointCommands.branch())));
    }

    private static int runInfo(net.minecraft.commands.CommandSourceStack source) {
        for (InfoMessageLine line : infoLines(modVersion(ButcherCraft.MOD_ID))) {
            source.sendSuccess(line::toComponent, false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int runBusinessStatus(net.minecraft.commands.CommandSourceStack source) {
        BusinessRuntimeObservationSnapshot snapshot = BusinessRuntimeCalendarService.INSTANCE
                .currentSnapshot(source.getServer())
                .orElse(null);
        if (snapshot == null) {
            source.sendSuccess(() -> Component.literal("ButcherCraft business runtime source is unavailable."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("ButcherCraft Business Runtime"), false);
        source.sendSuccess(() -> Component.literal("Enabled: " + snapshot.enabled()), false);
        source.sendSuccess(() -> Component.literal("Business calendar: day "
                + snapshot.calendar().businessDayIndex() + " " + snapshot.businessTimeDisplay()), false);
        source.sendSuccess(() -> Component.literal("Plant: " + (snapshot.plantOpen() ? "Open" : "Closed")), false);
        source.sendSuccess(() -> Component.literal("Current window: "
                + snapshot.currentOperatingWindow().map(BusinessScheduleBoundary::displayText).orElse("none")), false);
        source.sendSuccess(() -> Component.literal("Next opening: "
                + snapshot.nextOpening().map(BusinessScheduleBoundary::displayText).orElse("none")), false);
        source.sendSuccess(() -> Component.literal("Next closing: "
                + snapshot.nextClosing().map(BusinessScheduleBoundary::displayText).orElse("none")), false);
        source.sendSuccess(() -> Component.literal("Active shift: "
                + snapshot.activeShift().map(BusinessScheduleBoundary::displayName).orElse("none")), false);
        source.sendSuccess(() -> Component.literal("Next shift: "
                + snapshot.nextShift().map(BusinessScheduleBoundary::displayName).orElse("none")), false);
        source.sendSuccess(() -> Component.literal("Operating schedule identity: "
                + snapshot.operatingScheduleIdentity().value()), false);
        source.sendSuccess(() -> Component.literal("Shift set identity: "
                + snapshot.shiftSetIdentity().value()), false);
        source.sendSuccess(() -> Component.literal("Configuration identity: "
                + snapshot.configurationIdentity().value()), false);
        source.sendSuccess(() -> Component.literal("Last movement: "
                + snapshot.movementClassification().serializedName()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runEmployeeCreate(CommandSourceStack source, String requestedName) {
        BlockPos anchor = BlockPos.containing(source.getPosition());
        EmployeeOperationResult<EmployeeRecord> result = EmployeeService.INSTANCE.createEmployee(
                source.getLevel(),
                requestedName == null || requestedName.isBlank()
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(requestedName.strip()),
                java.util.Optional.of(anchor),
                true
        );
        if (!result.succeeded()) {
            sendEmployeeFailure(source, result.failure().orElseThrow());
            return 0;
        }
        EmployeeRecord record = result.orThrow();
        source.sendSuccess(() -> Component.literal("Created employee " + employeeLookupLabel(record)), false);
        source.sendSuccess(() -> Component.literal("Canonical employee id: " + record.employeeId().value()), false);
        source.sendSuccess(() -> Component.literal("Assigned shift: "
                + record.assignedShift().map(shift -> shift.shiftId() + " (" + shift.shiftDisplayName() + ")")
                .orElse("unassigned")), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runEmployeeList(CommandSourceStack source) {
        var records = EmployeeService.INSTANCE.managerFor(source.getServer()).registry().records();
        source.sendSuccess(() -> Component.literal("ButcherCraft Employees: " + records.size()), false);
        for (EmployeeRecord record : records) {
            source.sendSuccess(() -> Component.literal(employeeLookupLabel(record)
                    + " | " + record.status().serializedName()
                    + " | " + record.presenceState().serializedName()
                    + " | " + record.assignedShift().map(shift -> shift.shiftId()).orElse("unassigned")
                    + " | department: " + record.assignedDepartmentId()
                    .map(DepartmentId::value).orElse("unassigned")), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int runEmployeeStatus(CommandSourceStack source, String employeeReference) {
        EmployeeId employeeId = employeeId(employeeReference, source);
        if (employeeId == null) {
            return 0;
        }
        EmployeeOperationResult<EmployeePresenceObservation> observation =
                EmployeeService.INSTANCE.observe(source.getServer(), employeeId);
        if (!observation.succeeded()) {
            sendEmployeeFailure(source, observation.failure().orElseThrow());
            return 0;
        }
        EmployeePresenceObservation value = observation.orThrow();
        source.sendSuccess(() -> Component.literal("Employee: " + value.displayName()), false);
        source.sendSuccess(() -> Component.literal("Status: " + value.status().serializedName()), false);
        source.sendSuccess(() -> Component.literal("Presence: " + value.presenceState().serializedName()), false);
        source.sendSuccess(() -> Component.literal("Shift: "
                + value.assignedShift().map(shift -> shift.shiftId() + " (" + shift.shiftDisplayName() + ")")
                .orElse("unassigned")), false);
        source.sendSuccess(() -> Component.literal("Department: "
                + value.assignedDepartmentId().map(DepartmentId::value).orElse("unassigned")), false);
        WorkstationReservationService.INSTANCE.managerFor(source.getServer())
                .findByEmployee(employeeId.value())
                .ifPresent(reservation -> {
                    source.sendSuccess(() -> Component.literal("Workstation reservation: "
                            + reservation.state().serializedName()
                            + " | " + reservation.workstationType()
                            + " | " + reservation.workstationIdentity()), false);
                    source.sendSuccess(() -> Component.literal("Operating position: "
                            + reservation.operatingX() + " "
                            + reservation.operatingY() + " "
                            + reservation.operatingZ()), false);
                    source.sendSuccess(() -> Component.literal("Navigation: "
                            + employeeNavigationForReservation(source, reservation)), false);
                });
        employeeEntity(source, employeeId).ifPresentOrElse(
                employee -> sendEmployeeOperationDiagnostics(source, employee),
                () -> source.sendSuccess(() -> Component.literal("Employee Operation: unavailable"), false)
        );
        source.sendSuccess(() -> Component.literal("Plant: " + (value.plantOpen() ? "open" : "closed")), false);
        source.sendSuccess(() -> Component.literal("Reason: " + value.reason()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runEmployeeNavigation(CommandSourceStack source, String employeeReference) {
        EmployeeId employeeId = employeeId(employeeReference, source);
        if (employeeId == null) {
            return 0;
        }
        EmployeeRecord record = EmployeeService.INSTANCE.managerFor(source.getServer()).find(employeeId).orElse(null);
        if (record == null || record.entityLink().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Employee navigation: entity unavailable"), false);
            return 0;
        }
        if (!record.entityLink().orElseThrow().dimensionIdentity().equals(EmployeeService.dimensionIdentity(source.getLevel()))) {
            source.sendSuccess(() -> Component.literal("Employee navigation: entity is in another dimension"), false);
            return 0;
        }
        Entity entity = source.getLevel().getEntity(record.entityLink().orElseThrow().entityUuid());
        if (!(entity instanceof EmployeeEntity employee)) {
            source.sendSuccess(() -> Component.literal("Employee navigation: entity unavailable"), false);
            return 0;
        }
        EmployeeEntity.NavigationDiagnostics diagnostics = employee.navigationDiagnostics();
        source.sendSuccess(() -> Component.literal("Employee navigation: " + record.displayName()), false);
        source.sendSuccess(() -> Component.literal("State: " + employee.navigationStateValue()), false);
        source.sendSuccess(() -> Component.literal("Destination: "
                + diagnostics.destinationType()
                + " | " + diagnostics.destinationIdentity()), false);
        source.sendSuccess(() -> Component.literal("Current target: "
                + formatBlockPos(diagnostics.currentDestination())), false);
        source.sendSuccess(() -> Component.literal("Candidate: "
                + (diagnostics.candidateCount() == 0
                ? "none"
                : (diagnostics.candidateIndex() + 1) + "/" + diagnostics.candidateCount())), false);
        source.sendSuccess(() -> Component.literal("Path available: " + diagnostics.pathAvailable()), false);
        source.sendSuccess(() -> Component.literal("Distance to selected target: "
                + formatDistance(diagnostics.distanceToTarget())), false);
        source.sendSuccess(() -> Component.literal("Distance to final destination: "
                + formatDistance(diagnostics.distanceToFinalDestination())), false);
        source.sendSuccess(() -> Component.literal("Configured max navigation range: "
                + formatDistance(diagnostics.configuredMaximumNavigationRange())), false);
        source.sendSuccess(() -> Component.literal("Path search range: "
                + formatDistance(diagnostics.pathSearchRange())), false);
        source.sendSuccess(() -> Component.literal("Destination passed range validation: "
                + diagnostics.destinationWithinRange()), false);
        source.sendSuccess(() -> Component.literal("Visited-node multiplier: "
                + String.format(Locale.ROOT, "%.2f", diagnostics.visitedNodeMultiplier())), false);
        source.sendSuccess(() -> Component.literal("Active path node: "
                + (diagnostics.activePathNodeCount() == 0
                ? "none"
                : diagnostics.activePathNodeIndex() + "/" + diagnostics.activePathNodeCount())), false);
        source.sendSuccess(() -> Component.literal("Distance to next node: "
                + formatDistance(diagnostics.distanceToNextNode())), false);
        source.sendSuccess(() -> Component.literal("Ticks since node progress: "
                + diagnostics.ticksSinceNodeProgress()), false);
        source.sendSuccess(() -> Component.literal("Ticks since progress: "
                + diagnostics.ticksSinceMeaningfulProgress()), false);
        source.sendSuccess(() -> Component.literal("Path replacements: "
                + diagnostics.pathReplacementCount()
                + " | " + diagnostics.lastPathReplacementReason()), false);
        source.sendSuccess(() -> Component.literal("Retry count: " + diagnostics.retryCount()), false);
        source.sendSuccess(() -> Component.literal("Recovery phase: " + diagnostics.recoveryPhase()), false);
        source.sendSuccess(() -> Component.literal("Last failure: " + diagnostics.lastFailureReason()), false);
        WorkstationReservationService.INSTANCE.managerFor(source.getServer())
                .findByEmployee(employeeId.value())
                .ifPresentOrElse(
                        reservation -> source.sendSuccess(() -> Component.literal("Reservation: "
                                + reservation.state().serializedName()
                                + " | " + reservation.workstationIdentity()), false),
                        () -> source.sendSuccess(() -> Component.literal("Reservation: none"), false)
                );
        return Command.SINGLE_SUCCESS;
    }

    private static int runEmployeeSetShift(CommandSourceStack source, String employeeReference, String shiftId) {
        EmployeeId employeeId = employeeId(employeeReference, source);
        if (employeeId == null) {
            return 0;
        }
        EmployeeOperationResult<EmployeeRecord> result =
                EmployeeService.INSTANCE.assignShift(source.getServer(), employeeId, shiftId);
        if (!result.succeeded()) {
            sendEmployeeFailure(source, result.failure().orElseThrow());
            return 0;
        }
        EmployeeRecord record = result.orThrow();
        source.sendSuccess(() -> Component.literal("Employee shift set: "
                + record.assignedShift().map(shift -> shift.shiftId() + " (" + shift.shiftDisplayName() + ")")
                .orElse("unassigned")), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runEmployeeSetShiftTail(CommandSourceStack source, String tail) {
        EmployeeCommandTail parsed = parseEmployeeCommandTail(source, tail, "shift id");
        if (parsed == null) {
            return 0;
        }
        return runEmployeeSetShift(source, parsed.employeeReference(), parsed.value());
    }

    private static int runEmployeeSetPresence(CommandSourceStack source, String employeeReference, String stateValue) {
        EmployeeId employeeId = employeeId(employeeReference, source);
        if (employeeId == null) {
            return 0;
        }
        EmployeePresenceState state;
        try {
            state = EmployeePresenceState.fromSerializedName(stateValue);
        } catch (IllegalArgumentException exception) {
            source.sendSuccess(() -> Component.literal("Unknown employee presence state: " + stateValue), false);
            return 0;
        }
        EmployeeOperationResult<EmployeeRecord> result =
                EmployeeService.INSTANCE.setPresence(source.getServer(), employeeId, state);
        if (!result.succeeded()) {
            sendEmployeeFailure(source, result.failure().orElseThrow());
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Employee presence set: "
                + result.orThrow().presenceState().serializedName()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runEmployeeSetPresenceTail(CommandSourceStack source, String tail) {
        EmployeeCommandTail parsed = parseEmployeeCommandTail(source, tail, "presence state");
        if (parsed == null) {
            return 0;
        }
        return runEmployeeSetPresence(source, parsed.employeeReference(), parsed.value());
    }

    private static int runEmployeeAssignDepartment(
            CommandSourceStack source,
            String employeeReference,
            String departmentId
    ) {
        EmployeeId employeeId = employeeId(employeeReference, source);
        if (employeeId == null) {
            return 0;
        }
        EmployeeOperationResult<EmployeeRecord> result =
                EmployeeService.INSTANCE.assignDepartment(source.getServer(), employeeId, departmentId);
        if (!result.succeeded()) {
            sendEmployeeFailure(source, result.failure().orElseThrow());
            return 0;
        }
        EmployeeRecord record = result.orThrow();
        source.sendSuccess(() -> Component.literal("Employee department set: "
                + record.assignedDepartmentId().map(DepartmentId::value).orElse("unassigned")), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runEmployeeAssignDepartmentTail(CommandSourceStack source, String tail) {
        EmployeeCommandTail parsed = parseEmployeeCommandTail(source, tail, "department");
        if (parsed == null) {
            return 0;
        }
        return runEmployeeAssignDepartment(source, parsed.employeeReference(), parsed.value());
    }

    private static int runEmployeeAssignWorkstation(
            CommandSourceStack source,
            String employeeReference,
            BlockPos workstationPos
    ) {
        EmployeeId employeeId = employeeId(employeeReference, source);
        if (employeeId == null) {
            return 0;
        }
        WorkstationReservationResult<WorkstationReservationRecord> result =
                WorkstationReservationService.INSTANCE.assign(source.getLevel(), employeeId, workstationPos);
        if (!result.succeeded()) {
            sendWorkstationFailure(source, result.failure().orElseThrow());
            return 0;
        }
        WorkstationReservationRecord reservation = result.orThrow();
        source.sendSuccess(() -> Component.literal("Workstation reserved: "
                + reservation.workstationType()
                + " | " + reservation.workstationIdentity()), false);
        source.sendSuccess(() -> Component.literal("Employee en route to operating position: "
                + reservation.operatingX() + " "
                + reservation.operatingY() + " "
                + reservation.operatingZ()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runEmployeeAssignWorkstationTail(CommandSourceStack source, String tail) {
        EmployeeCommandTail parsed = parseEmployeeCommandTail(source, tail, "workstation position");
        if (parsed == null) {
            return 0;
        }
        BlockPos position = parseWorkstationPosition(source, parsed.value());
        if (position == null) {
            return 0;
        }
        return runEmployeeAssignWorkstation(source, parsed.employeeReference(), position);
    }

    private static int runEmployeeReleaseWorkstation(CommandSourceStack source, String employeeReference) {
        EmployeeId employeeId = employeeId(employeeReference, source);
        if (employeeId == null) {
            return 0;
        }
        WorkstationReservationResult<WorkstationReservationRecord> result =
                WorkstationReservationService.INSTANCE.release(
                        source.getServer(),
                        employeeId,
                        "administrative release command"
                );
        if (!result.succeeded()) {
            sendWorkstationFailure(source, result.failure().orElseThrow());
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Workstation reservation released: "
                + result.orThrow().workstationIdentity()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runEmployeeOperate(CommandSourceStack source, String employeeReference) {
        EmployeeId employeeId = employeeId(employeeReference, source);
        if (employeeId == null) {
            return 0;
        }
        Optional<EmployeeEntity> entity = employeeEntity(source, employeeId);
        if (entity.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "Employee not present: authoritative employee entity is unavailable in this dimension"), false);
            return 0;
        }
        EmployeeWorkstationOperationService.RequestResult result =
                EmployeeWorkstationOperationService.INSTANCE.request(entity.orElseThrow());
        source.sendSuccess(() -> Component.literal(employeeOperationFeedback(result)), false);
        return result.accepted() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int runDepartmentList(CommandSourceStack source) {
        List<DepartmentRecord> records = EmployeeService.INSTANCE.departmentManagerFor(source.getServer())
                .registry()
                .records();
        source.sendSuccess(() -> Component.literal("ButcherCraft Departments: " + records.size()), false);
        for (DepartmentRecord record : records) {
            source.sendSuccess(() -> Component.literal(record.departmentId().value()
                    + " | " + record.displayName()
                    + " | " + record.anchor().map(anchor -> "anchor "
                    + anchor.dimensionIdentity() + " "
                    + anchor.x() + " " + anchor.y() + " " + anchor.z()
                    + " r" + anchor.radius()).orElse("definition only")), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int runDepartmentStatus(CommandSourceStack source, String departmentValue) {
        DepartmentId departmentId;
        try {
            departmentId = new DepartmentId(departmentValue);
        } catch (IllegalArgumentException exception) {
            source.sendSuccess(() -> Component.literal("Invalid department: " + departmentValue), false);
            return 0;
        }
        DepartmentRecord record = EmployeeService.INSTANCE.departmentManagerFor(source.getServer())
                .find(departmentId)
                .orElse(null);
        if (record == null) {
            source.sendSuccess(() -> Component.literal("Unknown department: " + departmentValue), false);
            return 0;
        }
        long assigned = EmployeeService.INSTANCE.managerFor(source.getServer()).registry().records().stream()
                .filter(employee -> employee.assignedDepartmentId().filter(departmentId::equals).isPresent())
                .count();
        source.sendSuccess(() -> Component.literal("Department: " + record.displayName()
                + " (" + record.departmentId().value() + ")"), false);
        record.anchor().ifPresentOrElse(
                anchor -> {
                    source.sendSuccess(() -> Component.literal("Anchor dimension: "
                            + anchor.dimensionIdentity()), false);
                    source.sendSuccess(() -> Component.literal("Anchor position: "
                            + anchor.x() + " " + anchor.y() + " " + anchor.z()), false);
                    source.sendSuccess(() -> Component.literal("Configured idle radius: "
                            + anchor.radius()), false);
                },
                () -> source.sendSuccess(() -> Component.literal("Anchor: definition only"), false)
        );
        source.sendSuccess(() -> Component.literal("Assigned employees: " + assigned), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runDepartmentSetAnchor(
            CommandSourceStack source,
            String departmentValue,
            BlockPos anchorPos
    ) {
        if (!CommonConfig.ENABLE_DEVELOPMENT_DIAGNOSTIC.get()) {
            source.sendSuccess(() -> Component.literal(
                    "Department anchor command is disabled by development diagnostic config"), false);
            return 0;
        }
        DepartmentId departmentId;
        try {
            departmentId = new DepartmentId(departmentValue);
        } catch (IllegalArgumentException exception) {
            source.sendSuccess(() -> Component.literal("Invalid department: " + departmentValue), false);
            return 0;
        }
        EmployeeService.DepartmentAnchorUpdate update;
        try {
            update = EmployeeService.INSTANCE.assignDepartmentAnchor(source.getLevel(), departmentId, anchorPos);
        } catch (IllegalArgumentException exception) {
            source.sendSuccess(() -> Component.literal(exception.getMessage()), false);
            return 0;
        }
        DepartmentRecord record = update.updatedRecord();
        source.sendSuccess(() -> Component.literal("Department anchor set: "
                + record.displayName() + " (" + record.departmentId().value() + ")"), false);
        source.sendSuccess(() -> Component.literal("Old anchor: "
                + update.previousAnchor().map(ButcherCraftDiagnostics::formatDepartmentAnchor).orElse("none")), false);
        source.sendSuccess(() -> Component.literal("New anchor: "
                + formatDepartmentAnchor(update.newAnchor())), false);
        source.sendSuccess(() -> Component.literal("Dimension: "
                + update.newAnchor().dimensionIdentity()), false);
        source.sendSuccess(() -> Component.literal("Configured idle radius: "
                + update.newAnchor().radius()), false);
        source.sendSuccess(() -> Component.literal("Record revision: "
                + update.previousRecord().recordRevision()
                + " -> " + update.updatedRecord().recordRevision()
                + (update.changed() ? "" : " (unchanged)")), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runWorkstationReservations(CommandSourceStack source) {
        List<WorkstationReservationRecord> reservations =
                WorkstationReservationService.INSTANCE.activeReservations(source.getServer());
        source.sendSuccess(() -> Component.literal("ButcherCraft Workstation Reservations: "
                + reservations.size()), false);
        for (WorkstationReservationRecord reservation : reservations) {
            source.sendSuccess(() -> Component.literal(reservation.workstationType()
                    + " | " + reservation.state().serializedName()
                    + " | employee " + employeeDisplayForReservation(source, reservation)
                    + " | " + reservation.workstationIdentity()
                    + " | operating " + reservation.operatingX()
                    + " " + reservation.operatingY()
                    + " " + reservation.operatingZ()
                    + " | navigation " + employeeNavigationForReservation(source, reservation)), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int runWorkstationStatus(CommandSourceStack source, String positionValue) {
        BlockPos position = parseWorkstationPosition(source, positionValue);
        if (position == null) {
            return 0;
        }
        WorkstationReservationResult<WorkstationReservationService.ResolvedWorkstationStatus> result =
                WorkstationReservationService.INSTANCE.status(source.getLevel(), position);
        if (!result.succeeded()) {
            sendWorkstationFailure(source, result.failure().orElseThrow());
            return 0;
        }
        WorkstationReservationService.ResolvedWorkstationStatus status = result.orThrow();
        source.sendSuccess(() -> Component.literal("Workstation: "
                + status.target().workstationType()
                + " | " + status.target().workstationIdentity()), false);
        source.sendSuccess(() -> Component.literal("Operating position: "
                + status.target().operatingPos().getX()
                + " " + status.target().operatingPos().getY()
                + " " + status.target().operatingPos().getZ()), false);
        source.sendSuccess(() -> Component.literal("Approach candidates: "
                + status.target().approachCandidates().stream()
                .map(ButcherCraftDiagnostics::formatBlockPos)
                .toList()), false);
        if (status.reservation().isPresent()) {
            WorkstationReservationRecord reservation = status.reservation().orElseThrow();
            source.sendSuccess(() -> Component.literal("Reservation: "
                    + reservation.state().serializedName()
                    + " | employee " + employeeDisplayForReservation(source, reservation)
                    + " | navigation " + employeeNavigationForReservation(source, reservation)), false);
        } else {
            source.sendSuccess(() -> Component.literal("Reservation: unreserved"), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static EmployeeId employeeId(String value, CommandSourceStack source) {
        List<EmployeeRecord> records = EmployeeService.INSTANCE.managerFor(source.getServer()).registry().records();
        EmployeeLookupResult lookup = resolveEmployeeReference(value, records);
        if (lookup.resolved()) {
            return lookup.record().orElseThrow().employeeId();
        }
        sendEmployeeLookupFailure(source, value, lookup);
        return null;
    }

    private static CompletableFuture<Suggestions> suggestEmployeeReferences(
            CommandSourceStack source,
            SuggestionsBuilder builder
    ) {
        List<EmployeeRecord> records = EmployeeService.INSTANCE.managerFor(source.getServer()).registry().records();
        String remaining = builder.getRemaining();
        String normalizedRemaining = unquoteEmployeeReference(remaining).toLowerCase(Locale.ROOT);
        String lowerRemaining = remaining.toLowerCase(Locale.ROOT);
        for (String suggestion : employeeLookupSuggestions(records)) {
            String unquotedSuggestion = unquoteEmployeeReference(suggestion).toLowerCase(Locale.ROOT);
            if (suggestion.toLowerCase(Locale.ROOT).startsWith(lowerRemaining)
                    || unquotedSuggestion.startsWith(normalizedRemaining)) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestDepartmentReferences(
            CommandSourceStack source,
            SuggestionsBuilder builder
    ) {
        List<String> suggestions = departmentLookupSuggestions(EmployeeService.INSTANCE.departmentManagerFor(source.getServer())
                .registry()
                .records());
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String suggestion : suggestions) {
            if (suggestion.startsWith(remaining)) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestEmployeeReferenceThenDepartment(
            CommandSourceStack source,
            SuggestionsBuilder builder
    ) {
        int departmentStart = departmentSuggestionStart(builder.getRemaining());
        if (departmentStart < 0) {
            return suggestEmployeeReferences(source, builder);
        }
        return suggestDepartmentReferences(source, builder.createOffset(builder.getStart() + departmentStart));
    }

    private static CompletableFuture<Suggestions> suggestEmployeeReferenceThenPosition(
            CommandSourceStack source,
            SuggestionsBuilder builder
    ) {
        int positionStart = departmentSuggestionStart(builder.getRemaining());
        if (positionStart < 0) {
            return suggestEmployeeReferences(source, builder);
        }
        return suggestWorkstationPosition(source, builder.createOffset(builder.getStart() + positionStart));
    }

    private static CompletableFuture<Suggestions> suggestWorkstationPosition(
            CommandSourceStack source,
            SuggestionsBuilder builder
    ) {
        BlockPos sourcePos = BlockPos.containing(source.getPosition());
        String suggestion = sourcePos.getX() + " " + sourcePos.getY() + " " + sourcePos.getZ();
        if (suggestion.startsWith(builder.getRemaining())) {
            builder.suggest(suggestion);
        }
        return builder.buildFuture();
    }

    private static boolean canMutateDepartmentAnchor(CommandSourceStack source) {
        return source.hasPermission(DEPARTMENT_ANCHOR_PERMISSION_LEVEL);
    }

    private static boolean canOperateEmployee(CommandSourceStack source) {
        return source.hasPermission(EMPLOYEE_OPERATION_PERMISSION_LEVEL);
    }

    static String employeeOperationFeedback(EmployeeWorkstationOperationService.RequestResult result) {
        return switch (Objects.requireNonNull(result, "result").status()) {
            case ACCEPTED -> "Employee operation accepted: " + result.detail();
            case EMPLOYEE_NOT_PRESENT -> "Employee not present: " + result.detail();
            case EMPLOYEE_NOT_AT_WORKSTATION -> "Employee not at workstation: " + result.detail();
            case RESERVATION_MISSING_OR_INVALID -> "Reservation missing or invalid: " + result.detail();
            case UNSUPPORTED_WORKSTATION -> "Unsupported workstation: " + result.detail();
            case MISSING_INPUT -> "Missing input: " + result.detail();
            case INVALID_RECIPE -> "Invalid recipe: " + result.detail();
            case BLOCKED_OUTPUT -> "Blocked output: " + result.detail();
            case ALREADY_REQUESTED -> "Operation already requested: " + result.detail();
            case EXECUTION_REJECTED -> "Execution rejected: " + result.detail();
            case UNKNOWN_OUTCOME -> "Unknown Outcome; recovery required: " + result.detail();
            case RECOVERY_REQUIRED -> "Recovery required: " + result.detail();
        };
    }

    static List<String> employeeLookupSuggestions(List<EmployeeRecord> records) {
        List<String> suggestions = new ArrayList<>();
        List<EmployeeRecord> sortedRecords = sortedEmployeeRecords(records);
        for (EmployeeRecord record : sortedRecords) {
            addUniqueSuggestion(suggestions, employeeNumberReference(record));
            if (hasUniqueDisplayName(record, sortedRecords)) {
                addUniqueSuggestion(suggestions, quotedEmployeeReference(record.displayName()));
            }
            addUniqueSuggestion(suggestions, record.employeeId().value());
        }
        return List.copyOf(suggestions);
    }

    static List<String> departmentLookupSuggestions(List<DepartmentRecord> records) {
        return records.stream()
                .map(record -> record.departmentId().value())
                .toList();
    }

    static EmployeeLookupResult resolveEmployeeReference(String value, List<EmployeeRecord> records) {
        String reference = unquoteEmployeeReference(Objects.requireNonNull(value, "value")).strip();
        if (reference.isEmpty()) {
            return EmployeeLookupResult.failed(EmployeeLookupFailure.EMPTY, List.of());
        }

        for (EmployeeRecord record : sortedEmployeeRecords(records)) {
            if (record.employeeId().value().equals(reference)) {
                return EmployeeLookupResult.resolved(record);
            }
        }

        OptionalLong employeeNumber = parseEmployeeNumber(reference);
        if (employeeNumber.isPresent()) {
            long sequence = employeeNumber.getAsLong() - 1L;
            List<EmployeeRecord> matches = sortedEmployeeRecords(records).stream()
                    .filter(record -> record.sequence() == sequence)
                    .toList();
            if (matches.size() == 1) {
                return EmployeeLookupResult.resolved(matches.getFirst());
            }
            if (matches.size() > 1) {
                return EmployeeLookupResult.failed(EmployeeLookupFailure.AMBIGUOUS, matches);
            }
            return EmployeeLookupResult.failed(EmployeeLookupFailure.NOT_FOUND, List.of());
        }

        List<EmployeeRecord> matches = sortedEmployeeRecords(records).stream()
                .filter(record -> record.displayName().equalsIgnoreCase(reference))
                .toList();
        if (matches.size() == 1) {
            return EmployeeLookupResult.resolved(matches.getFirst());
        }
        if (matches.size() > 1) {
            return EmployeeLookupResult.failed(EmployeeLookupFailure.AMBIGUOUS, matches);
        }
        return EmployeeLookupResult.failed(EmployeeLookupFailure.NOT_FOUND, List.of());
    }

    private static EmployeeCommandTail parseEmployeeCommandTail(
            CommandSourceStack source,
            String value,
            String trailingArgumentLabel
    ) {
        try {
            EmployeeCommandTail parsed = parseEmployeeCommandTail(value);
            if (parsed.value().isBlank()) {
                source.sendSuccess(() -> Component.literal("Employee reference and "
                        + trailingArgumentLabel + " are required."), false);
                return null;
            }
            return parsed;
        } catch (CommandSyntaxException exception) {
            source.sendSuccess(() -> Component.literal("Invalid employee reference: "
                    + exception.getMessage()), false);
            return null;
        }
    }

    private static EmployeeCommandTail parseEmployeeCommandTail(String value) throws CommandSyntaxException {
        String stripped = Objects.requireNonNull(value, "value").stripLeading();
        if (stripped.isEmpty()) {
            return new EmployeeCommandTail("", "");
        }
        StringReader reader = new StringReader(stripped);
        String employeeReference = readEmployeeReference(reader);
        skipWhitespace(reader);
        if (!reader.canRead()) {
            return new EmployeeCommandTail(employeeReference, "");
        }
        return new EmployeeCommandTail(employeeReference, reader.getRemaining().strip());
    }

    static Optional<BlockPos> parseWorkstationPosition(String value) {
        try {
            return Optional.of(parseWorkstationPositionValue(value));
        } catch (CommandSyntaxException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static BlockPos parseWorkstationPosition(CommandSourceStack source, String value) {
        try {
            return parseWorkstationPositionValue(value);
        } catch (CommandSyntaxException | IllegalArgumentException exception) {
            source.sendSuccess(() -> Component.literal("Invalid workstation position: "
                    + value + ". Use absolute coordinates like 12 64 -3."), false);
            return null;
        }
    }

    private static BlockPos parseWorkstationPositionValue(String value) throws CommandSyntaxException {
        StringReader reader = new StringReader(Objects.requireNonNull(value, "value").strip());
        if (!reader.canRead()) {
            throw new IllegalArgumentException("Workstation position is required");
        }
        int x = reader.readInt();
        skipWhitespace(reader);
        int y = reader.readInt();
        skipWhitespace(reader);
        int z = reader.readInt();
        skipWhitespace(reader);
        if (reader.canRead()) {
            throw new IllegalArgumentException("Workstation position has trailing data");
        }
        return new BlockPos(x, y, z);
    }

    private static String readEmployeeReference(StringReader reader) throws CommandSyntaxException {
        if (StringReader.isQuotedStringStart(reader.peek())) {
            return reader.readString();
        }
        int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    private static void skipWhitespace(StringReader reader) {
        while (reader.canRead() && Character.isWhitespace(reader.peek())) {
            reader.skip();
        }
    }

    private static int departmentSuggestionStart(String value) {
        if (value.isEmpty()) {
            return -1;
        }
        int index = 0;
        if (value.charAt(index) == '"') {
            index++;
            boolean escaped = false;
            while (index < value.length()) {
                char current = value.charAt(index);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    index++;
                    break;
                }
                index++;
            }
            if (index > value.length() || (index == value.length() && value.charAt(index - 1) != '"')) {
                return -1;
            }
        } else {
            while (index < value.length() && !Character.isWhitespace(value.charAt(index))) {
                index++;
            }
        }
        if (index >= value.length() || !Character.isWhitespace(value.charAt(index))) {
            return -1;
        }
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static OptionalLong parseEmployeeNumber(String reference) {
        String value = reference.startsWith("#") ? reference.substring(1).strip() : reference;
        if (value.isEmpty()) {
            return OptionalLong.empty();
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return OptionalLong.empty();
            }
        }
        try {
            long number = Long.parseLong(value);
            return number > 0L ? OptionalLong.of(number) : OptionalLong.empty();
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    private static void sendEmployeeLookupFailure(
            CommandSourceStack source,
            String reference,
            EmployeeLookupResult lookup
    ) {
        EmployeeLookupFailure failure = lookup.failure().orElse(EmployeeLookupFailure.NOT_FOUND);
        if (failure == EmployeeLookupFailure.AMBIGUOUS) {
            String matches = lookup.matches().stream()
                    .map(ButcherCraftDiagnostics::employeeLookupLabel)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("none");
            source.sendSuccess(() -> Component.literal("Employee lookup is ambiguous for: " + reference), false);
            source.sendSuccess(() -> Component.literal("Matches: " + matches), false);
            source.sendSuccess(() -> Component.literal("Use the employee number, such as #1."), false);
            return;
        }
        if (failure == EmployeeLookupFailure.EMPTY) {
            source.sendSuccess(() -> Component.literal("Employee reference is required."), false);
            return;
        }
        source.sendSuccess(() -> Component.literal("Employee not found: " + reference
                + ". Use /butchercraft employee list."), false);
    }

    private static String employeeDisplayForReservation(
            CommandSourceStack source,
            WorkstationReservationRecord reservation
    ) {
        try {
            EmployeeId employeeId = new EmployeeId(reservation.employeeIdentity());
            return EmployeeService.INSTANCE.managerFor(source.getServer())
                    .find(employeeId)
                    .map(record -> employeeLookupLabel(record) + " (" + record.employeeId().value() + ")")
                    .orElse(reservation.employeeIdentity());
        } catch (IllegalArgumentException exception) {
            return reservation.employeeIdentity();
        }
    }

    private static String employeeNavigationForReservation(
            CommandSourceStack source,
            WorkstationReservationRecord reservation
    ) {
        try {
            EmployeeId employeeId = new EmployeeId(reservation.employeeIdentity());
            EmployeeRecord record = EmployeeService.INSTANCE.managerFor(source.getServer())
                    .find(employeeId)
                    .orElse(null);
            if (record == null || record.entityLink().isEmpty()) {
                return "unloaded";
            }
            if (!record.entityLink().orElseThrow().dimensionIdentity().equals(
                    EmployeeService.dimensionIdentity(source.getLevel()))) {
                return "unloaded";
            }
            Entity entity = source.getLevel().getEntity(record.entityLink().orElseThrow().entityUuid());
            if (entity instanceof EmployeeEntity employee) {
                EmployeeEntity.NavigationDiagnostics diagnostics = employee.navigationDiagnostics();
                return employee.navigationStateValue()
                        + " | target " + formatBlockPos(diagnostics.currentDestination())
                        + " | candidate " + (diagnostics.candidateCount() == 0
                        ? "none"
                        : (diagnostics.candidateIndex() + 1) + "/" + diagnostics.candidateCount())
                        + " | distance " + formatDistance(diagnostics.distanceToTarget())
                        + "/" + formatDistance(diagnostics.configuredMaximumNavigationRange())
                        + " | path " + diagnostics.pathAvailable()
                        + " | phase " + diagnostics.recoveryPhase()
                        + " | failure " + diagnostics.lastFailureReason();
            }
        } catch (IllegalArgumentException exception) {
            return "unknown";
        }
        return "unloaded";
    }

    private static Optional<EmployeeEntity> employeeEntity(CommandSourceStack source, EmployeeId employeeId) {
        EmployeeRecord record = EmployeeService.INSTANCE.managerFor(source.getServer()).find(employeeId).orElse(null);
        if (record == null || record.entityLink().isEmpty()) {
            return Optional.empty();
        }
        if (!record.entityLink().orElseThrow().dimensionIdentity().equals(
                EmployeeService.dimensionIdentity(source.getLevel()))) {
            return Optional.empty();
        }
        Entity entity = source.getLevel().getEntity(record.entityLink().orElseThrow().entityUuid());
        return entity instanceof EmployeeEntity employee ? Optional.of(employee) : Optional.empty();
    }

    private static void sendEmployeeOperationDiagnostics(CommandSourceStack source, EmployeeEntity employee) {
        EmployeeEntity.EmployeeOperationDiagnostics diagnostics = employee.workstationOperationDiagnostics();
        source.sendSuccess(() -> Component.literal("Employee Operation: " + diagnostics.state()), false);
        source.sendSuccess(() -> Component.literal("Workstation: " + diagnostics.workstation()
                + " | Execution: " + diagnostics.executionId()), false);
        source.sendSuccess(() -> Component.literal("Reservation: " + diagnostics.reservation()
                + " | Recipe: " + diagnostics.recipe()
                + " | Failure: " + diagnostics.failure()), false);
    }

    private static String formatBlockPos(BlockPos pos) {
        if (pos == null) {
            return "none";
        }
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static String formatDepartmentAnchor(DepartmentAnchor anchor) {
        return anchor.dimensionIdentity()
                + " "
                + anchor.x()
                + " "
                + anchor.y()
                + " "
                + anchor.z()
                + " radius "
                + anchor.radius();
    }

    private static String formatDistance(double distance) {
        if (distance < 0.0D) {
            return "none";
        }
        return String.format(Locale.ROOT, "%.2f", distance);
    }

    private static void addUniqueSuggestion(List<String> suggestions, String suggestion) {
        if (!suggestions.contains(suggestion)) {
            suggestions.add(suggestion);
        }
    }

    private static List<EmployeeRecord> sortedEmployeeRecords(List<EmployeeRecord> records) {
        return records.stream()
                .sorted(Comparator.comparingLong(EmployeeRecord::sequence)
                        .thenComparing(record -> record.displayName().toLowerCase(Locale.ROOT))
                        .thenComparing(record -> record.employeeId().value()))
                .toList();
    }

    static String employeeLookupLabel(EmployeeRecord record) {
        return employeeNumberReference(record) + " " + record.displayName();
    }

    private static String employeeNumberReference(EmployeeRecord record) {
        return "#" + Math.addExact(record.sequence(), 1L);
    }

    private static boolean hasUniqueDisplayName(EmployeeRecord record, List<EmployeeRecord> records) {
        String displayName = record.displayName().toLowerCase(Locale.ROOT);
        return records.stream()
                .filter(candidate -> candidate.displayName().toLowerCase(Locale.ROOT).equals(displayName))
                .count() == 1L;
    }

    private static String quotedEmployeeReference(String value) {
        if (value.indexOf(' ') < 0 && value.indexOf('"') < 0 && value.indexOf('\\') < 0) {
            return value;
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String unquoteEmployeeReference(String value) {
        String stripped = value.strip();
        if (stripped.length() >= 2 && stripped.startsWith("\"") && stripped.endsWith("\"")) {
            return stripped.substring(1, stripped.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return stripped;
    }

    private record EmployeeCommandTail(String employeeReference, String value) {
        private EmployeeCommandTail {
            employeeReference = Objects.requireNonNull(employeeReference, "employeeReference").strip();
            value = Objects.requireNonNull(value, "value").strip();
        }
    }

    enum EmployeeLookupFailure {
        EMPTY,
        NOT_FOUND,
        AMBIGUOUS
    }

    record EmployeeLookupResult(
            Optional<EmployeeRecord> record,
            Optional<EmployeeLookupFailure> failure,
            List<EmployeeRecord> matches
    ) {
        EmployeeLookupResult {
            record = Objects.requireNonNull(record, "record");
            failure = Objects.requireNonNull(failure, "failure");
            matches = List.copyOf(Objects.requireNonNull(matches, "matches"));
        }

        static EmployeeLookupResult resolved(EmployeeRecord record) {
            return new EmployeeLookupResult(Optional.of(Objects.requireNonNull(record, "record")),
                    Optional.empty(), List.of(record));
        }

        static EmployeeLookupResult failed(EmployeeLookupFailure failure, List<EmployeeRecord> matches) {
            return new EmployeeLookupResult(Optional.empty(), Optional.of(failure), matches);
        }

        boolean resolved() {
            return record.isPresent();
        }
    }

    private static void sendEmployeeFailure(CommandSourceStack source, EmployeeFailure failure) {
        source.sendSuccess(() -> Component.literal("Employee command failed: "
                + failure.code().reasonCode() + " - " + failure.detail()), false);
    }

    private static void sendWorkstationFailure(CommandSourceStack source, WorkstationReservationFailure failure) {
        source.sendSuccess(() -> Component.literal("Workstation reservation failed: "
                + failure.code().reasonCode() + " - " + failure.detail()), false);
    }

    static List<InfoMessageLine> infoLines(String modVersion) {
        return List.of(
                new InfoMessageLine("commands.butchercraft.info.title", List.of()),
                new InfoMessageLine("commands.butchercraft.info.version", List.of(modVersion)),
                new InfoMessageLine("commands.butchercraft.info.status", List.of())
        );
    }

    private static int runTimeStatus(net.minecraft.commands.CommandSourceStack source) {
        WorldTimeStatusSnapshot snapshot = WorldTimeService.INSTANCE.currentSnapshot(source.getServer())
                .orElse(null);
        if (snapshot == null) {
            source.sendSuccess(() -> Component.literal("ButcherCraft world time source is unavailable."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("ButcherCraft World Time"), false);
        source.sendSuccess(() -> Component.literal("Scaling enabled: " + snapshot.scalingEnabled()), false);
        source.sendSuccess(() -> Component.literal("Configured day length: "
                + snapshot.configuredDayLengthMinutes() + " minutes"), false);
        source.sendSuccess(() -> Component.literal("Scale ratio: " + snapshot.scaleNumerator()
                + "/" + snapshot.scaleDenominator() + " day-time units per server tick"), false);
        source.sendSuccess(() -> Component.literal("gameTime: " + snapshot.gameTime()), false);
        source.sendSuccess(() -> Component.literal("dayTime: " + snapshot.dayTime()), false);
        source.sendSuccess(() -> Component.literal("Business calendar: day "
                + snapshot.businessCalendar().businessDayIndex() + " "
                + snapshot.businessTimeDisplay()), false);
        source.sendSuccess(() -> Component.literal("World day identity: "
                + snapshot.businessCalendar().worldDayIdentity()), false);
        source.sendSuccess(() -> Component.literal("Configuration identity: "
                + snapshot.configurationIdentity().value()), false);
        source.sendSuccess(() -> Component.literal("Source dimension: "
                + snapshot.sourceDimensionIdentity()), false);
        source.sendSuccess(() -> Component.literal("Accumulator remainder: "
                + snapshot.accumulatorRemainderNumerator()), false);
        source.sendSuccess(() -> Component.literal("Last movement: "
                + snapshot.movementClassification().serializedName()), false);
        source.sendSuccess(() -> Component.literal("External conflict detected: "
                + snapshot.externalConflictDetected()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runDiagnostic(net.minecraft.commands.CommandSourceStack source) {
        if (!CommonConfig.ENABLE_DEVELOPMENT_DIAGNOSTIC.get()) {
            source.sendSuccess(() -> Component.literal("ButcherCraft diagnostic is disabled by common config."), false);
            return 0;
        }

        String modVersion = modVersion(ButcherCraft.MOD_ID);
        String neoForgeVersion = modVersion("neoforge");
        boolean developmentItemRegistered = BuiltInRegistries.ITEM.containsKey(DEVELOPMENT_TEST_ITEM_ID);
        boolean productDataComponentRegistered = ModDataComponents.PRODUCT_DATA.isBound()
                && PRODUCT_DATA_COMPONENT_ID.equals(ModDataComponents.PRODUCT_DATA.getId());
        boolean beefTrimTestItemRegistered = BuiltInRegistries.ITEM.containsKey(BEEF_TRIM_TEST_ITEM_ID);
        boolean groundBeefTestItemRegistered = BuiltInRegistries.ITEM.containsKey(GROUND_BEEF_TEST_ITEM_ID);
        boolean porkTrimTestItemRegistered = BuiltInRegistries.ITEM.containsKey(PORK_TRIM_TEST_ITEM_ID);
        boolean groundPorkTestItemRegistered = BuiltInRegistries.ITEM.containsKey(GROUND_PORK_TEST_ITEM_ID);
        boolean chickenTrimItemRegistered = BuiltInRegistries.ITEM.containsKey(CHICKEN_TRIM_ITEM_ID);
        boolean groundChickenItemRegistered = BuiltInRegistries.ITEM.containsKey(GROUND_CHICKEN_ITEM_ID);
        boolean bisonTrimTestItemRegistered = BuiltInRegistries.ITEM.containsKey(BISON_TRIM_TEST_ITEM_ID);
        boolean groundBisonTestItemRegistered = BuiltInRegistries.ITEM.containsKey(GROUND_BISON_TEST_ITEM_ID);
        boolean lambTrimItemRegistered = BuiltInRegistries.ITEM.containsKey(LAMB_TRIM_ITEM_ID);
        boolean groundLambItemRegistered = BuiltInRegistries.ITEM.containsKey(GROUND_LAMB_ITEM_ID);
        boolean venisonTrimItemRegistered = BuiltInRegistries.ITEM.containsKey(VENISON_TRIM_ITEM_ID);
        boolean groundVenisonItemRegistered = BuiltInRegistries.ITEM.containsKey(GROUND_VENISON_ITEM_ID);
        boolean beefForequarterTestItemRegistered = BuiltInRegistries.ITEM.containsKey(BEEF_FOREQUARTER_TEST_ITEM_ID);
        ProductRoundTripDiagnostic productRoundTrip = verifyFreshProductStackRoundTrip();
        DefinitionRegistryLoadResult definitionRegistries = DefinitionRegistryView.fromRegistryAccess(source.registryAccess());
        ProcessingDefinitionResolver resolver = new ProcessingDefinitionResolver(definitionRegistries.view());
        DefinitionResolution<SpeciesDefinition> beefDefinition = resolver.resolveSpecies(BuiltInDefinitionIds.BEEF);
        DefinitionResolution<SpeciesDefinition> porkDefinition = resolver.resolveSpecies(BuiltInDefinitionIds.PORK);
        DefinitionResolution<SpeciesDefinition> chickenDefinition = resolver.resolveSpecies(BuiltInDefinitionIds.CHICKEN);
        DefinitionResolution<SpeciesDefinition> bisonDefinition = resolver.resolveSpecies(BuiltInDefinitionIds.BISON);
        DefinitionResolution<SpeciesDefinition> lambDefinition = resolver.resolveSpecies(BuiltInDefinitionIds.LAMB);
        DefinitionResolution<SpeciesDefinition> venisonDefinition = resolver.resolveSpecies(BuiltInDefinitionIds.VENISON);
        DefinitionResolution<ProcessingProfileDefinition> redMeatProfile = resolver.resolveProcessingProfile(BuiltInDefinitionIds.RED_MEAT);
        DefinitionResolution<ProcessingProfileDefinition> poultryProfile = resolver.resolveProcessingProfile(BuiltInDefinitionIds.POULTRY);
        DefinitionResolution<ProductDefinition> beefTrimDefinition = resolver.resolveProduct(BuiltInDefinitionIds.BEEF_TRIM);
        DefinitionResolution<ProductDefinition> groundBeefDefinition = resolver.resolveProduct(BuiltInDefinitionIds.GROUND_BEEF);
        DefinitionResolution<ProductDefinition> porkTrimDefinition = resolver.resolveProduct(BuiltInDefinitionIds.PORK_TRIM);
        DefinitionResolution<ProductDefinition> groundPorkDefinition = resolver.resolveProduct(BuiltInDefinitionIds.GROUND_PORK);
        DefinitionResolution<ProductDefinition> chickenTrimDefinition = resolver.resolveProduct(BuiltInDefinitionIds.CHICKEN_TRIM);
        DefinitionResolution<ProductDefinition> groundChickenDefinition = resolver.resolveProduct(BuiltInDefinitionIds.GROUND_CHICKEN);
        DefinitionResolution<ProductDefinition> bisonTrimDefinition = resolver.resolveProduct(BuiltInDefinitionIds.BISON_TRIM);
        DefinitionResolution<ProductDefinition> groundBisonDefinition = resolver.resolveProduct(BuiltInDefinitionIds.GROUND_BISON);
        DefinitionResolution<ProductDefinition> lambTrimDefinition = resolver.resolveProduct(BuiltInDefinitionIds.LAMB_TRIM);
        DefinitionResolution<ProductDefinition> groundLambDefinition = resolver.resolveProduct(BuiltInDefinitionIds.GROUND_LAMB);
        DefinitionResolution<ProductDefinition> venisonTrimDefinition = resolver.resolveProduct(BuiltInDefinitionIds.VENISON_TRIM);
        DefinitionResolution<ProductDefinition> groundVenisonDefinition = resolver.resolveProduct(BuiltInDefinitionIds.GROUND_VENISON);
        DefinitionResolution<ProductDefinition> beefForequarterDefinition = resolver.resolveProduct(BuiltInDefinitionIds.BEEF_FOREQUARTER);
        DefinitionResolution<ProductDefinition> beefChuckDefinition = resolver.resolveProduct(BuiltInDefinitionIds.BEEF_CHUCK);
        DefinitionResolution<ProductDefinition> beefBoneDefinition = resolver.resolveProduct(BuiltInDefinitionIds.BEEF_BONE);
        DefinitionResolution<ResolvedProcessingOperationDefinition> grindBeefOperation =
                resolver.resolveOperation(BuiltInDefinitionIds.GRIND_BEEF);
        DefinitionResolution<ResolvedProcessingOperationDefinition> grindPorkOperation =
                resolver.resolveOperation(BuiltInDefinitionIds.GRIND_PORK);
        DefinitionResolution<ResolvedProcessingOperationDefinition> grindChickenOperation =
                resolver.resolveOperation(BuiltInDefinitionIds.GRIND_CHICKEN);
        DefinitionResolution<ResolvedProcessingOperationDefinition> grindBisonOperation =
                resolver.resolveOperation(BuiltInDefinitionIds.GRIND_BISON);
        DefinitionResolution<ResolvedProcessingOperationDefinition> grindLambOperation =
                resolver.resolveOperation(BuiltInDefinitionIds.GRIND_LAMB);
        DefinitionResolution<ResolvedProcessingOperationDefinition> grindVenisonOperation =
                resolver.resolveOperation(BuiltInDefinitionIds.GRIND_VENISON);
        DefinitionResolution<ResolvedProcessingOperationDefinition> breakBeefForequarterOperation =
                resolver.resolveOperation(BuiltInDefinitionIds.BREAK_BEEF_FOREQUARTER);
        ProcessingGraph graph = ProcessingGraph.fromDefinitions(definitionRegistries.view());
        DefinitionValidationReport definitionReport = definitionRegistries.report()
                .plus(resolver.validateAll())
                .plus(graph.validationReport());
        boolean initialGraphValid = definitionRegistries.allRegistriesAvailable() && !definitionReport.hasErrors();
        boolean beefTrimToGroundBeefExists = graph.hasDirectTransformation(BuiltInDefinitionIds.BEEF_TRIM, BuiltInDefinitionIds.GROUND_BEEF);
        boolean porkTrimToGroundPorkExists = graph.hasDirectTransformation(BuiltInDefinitionIds.PORK_TRIM, BuiltInDefinitionIds.GROUND_PORK);
        boolean chickenTrimToGroundChickenExists = graph.hasDirectTransformation(BuiltInDefinitionIds.CHICKEN_TRIM, BuiltInDefinitionIds.GROUND_CHICKEN);
        boolean bisonTrimToGroundBisonExists = graph.hasDirectTransformation(BuiltInDefinitionIds.BISON_TRIM, BuiltInDefinitionIds.GROUND_BISON);
        boolean lambTrimToGroundLambExists = graph.hasDirectTransformation(BuiltInDefinitionIds.LAMB_TRIM, BuiltInDefinitionIds.GROUND_LAMB);
        boolean venisonTrimToGroundVenisonExists = graph.hasDirectTransformation(BuiltInDefinitionIds.VENISON_TRIM, BuiltInDefinitionIds.GROUND_VENISON);
        boolean beefForequarterToChuckExists = graph.hasDirectTransformation(BuiltInDefinitionIds.BEEF_FOREQUARTER, BuiltInDefinitionIds.BEEF_CHUCK);
        boolean beefForequarterToBoneExists = graph.hasDirectTransformation(BuiltInDefinitionIds.BEEF_FOREQUARTER, BuiltInDefinitionIds.BEEF_BONE);
        WorkstationDiagnostic workstationDiagnostic = verifyWorkstation(source.registryAccess());
        GrinderDiagnostic grinderDiagnostic = verifyGrinder(source.registryAccess());
        BandsawDiagnostic bandsawDiagnostic = verifyBandsaw(source.registryAccess());

        source.sendSuccess(() -> Component.literal("Project: " + ButcherCraft.PROJECT_NAME), false);
        source.sendSuccess(() -> Component.literal("Mod ID: " + ButcherCraft.MOD_ID), false);
        source.sendSuccess(() -> Component.literal("Mod version: " + modVersion), false);
        source.sendSuccess(() -> Component.literal("Minecraft version: " + SharedConstants.getCurrentVersion().getName()), false);
        source.sendSuccess(() -> Component.literal("NeoForge version: " + neoForgeVersion), false);
        source.sendSuccess(() -> Component.literal("Common initialization completed: " + ButcherCraft.commonInitializationCompleted()), false);
        source.sendSuccess(() -> Component.literal("Development test item registered: " + developmentItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Product data component registered: " + productDataComponentRegistered), false);
        source.sendSuccess(() -> Component.literal("Beef trim test product registered: " + beefTrimTestItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Ground beef test product registered: " + groundBeefTestItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Pork trim test product registered: " + porkTrimTestItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Ground pork test product registered: " + groundPorkTestItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Chicken trim product registered: " + chickenTrimItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Ground chicken product registered: " + groundChickenItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Buffalo trim product registered with retained bison id: " + bisonTrimTestItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Ground buffalo product registered with retained bison id: " + groundBisonTestItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Lamb trim product registered: " + lambTrimItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Ground lamb product registered: " + groundLambItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Venison trim product registered: " + venisonTrimItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Ground venison product registered: " + groundVenisonItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Beef forequarter test product registered: " + beefForequarterTestItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Fresh product stack round trip: " + productRoundTrip.roundTripSucceeded()), false);
        source.sendSuccess(() -> Component.literal("Product quantity survives round trip: " + productRoundTrip.quantityPreserved()), false);
        source.sendSuccess(() -> Component.literal("Product quality survives round trip: " + productRoundTrip.qualityPreserved()), false);
        source.sendSuccess(() -> Component.literal("Species registry available: " + definitionRegistries.speciesRegistryAvailable()), false);
        source.sendSuccess(() -> Component.literal("Processing-profile registry available: " + definitionRegistries.processingProfileRegistryAvailable()), false);
        source.sendSuccess(() -> Component.literal("Product registry available: " + definitionRegistries.productRegistryAvailable()), false);
        source.sendSuccess(() -> Component.literal("Processing-operation registry available: " + definitionRegistries.processingOperationRegistryAvailable()), false);
        source.sendSuccess(() -> Component.literal("Beef definition resolved: " + beefDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Pork definition resolved: " + porkDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Chicken definition resolved: " + chickenDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Buffalo definition resolved with retained bison id: " + bisonDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Lamb definition resolved: " + lambDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Venison definition resolved: " + venisonDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Red-meat profile resolved: " + redMeatProfile.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Poultry profile resolved: " + poultryProfile.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Beef trim definition resolved: " + beefTrimDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Ground beef definition resolved: " + groundBeefDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Pork trim definition resolved: " + porkTrimDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Ground pork definition resolved: " + groundPorkDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Chicken trim definition resolved: " + chickenTrimDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Ground chicken definition resolved: " + groundChickenDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Buffalo trim definition resolved with retained bison id: " + bisonTrimDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Ground buffalo definition resolved with retained bison id: " + groundBisonDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Lamb trim definition resolved: " + lambTrimDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Ground lamb definition resolved: " + groundLambDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Venison trim definition resolved: " + venisonTrimDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Ground venison definition resolved: " + groundVenisonDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Beef forequarter definition resolved: " + beefForequarterDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Beef chuck definition resolved: " + beefChuckDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Beef bone definition resolved: " + beefBoneDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Grind-beef operation resolved: " + grindBeefOperation.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Grind-pork operation resolved: " + grindPorkOperation.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Grind-chicken operation resolved: " + grindChickenOperation.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Grind-buffalo operation resolved with retained bison id: " + grindBisonOperation.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Grind-lamb operation resolved: " + grindLambOperation.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Grind-venison operation resolved: " + grindVenisonOperation.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Break-beef-forequarter operation resolved: " + breakBeefForequarterOperation.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Initial processing graph validates: " + initialGraphValid), false);
        source.sendSuccess(() -> Component.literal("Beef Trim -> Ground Beef direct transformation exists: " + beefTrimToGroundBeefExists), false);
        source.sendSuccess(() -> Component.literal("Pork Trim -> Ground Pork direct transformation exists: " + porkTrimToGroundPorkExists), false);
        source.sendSuccess(() -> Component.literal("Chicken Trim -> Ground Chicken direct transformation exists: " + chickenTrimToGroundChickenExists), false);
        source.sendSuccess(() -> Component.literal("Buffalo Trim -> Ground Buffalo direct transformation exists with retained bison ids: " + bisonTrimToGroundBisonExists), false);
        source.sendSuccess(() -> Component.literal("Lamb Trim -> Ground Lamb direct transformation exists: " + lambTrimToGroundLambExists), false);
        source.sendSuccess(() -> Component.literal("Venison Trim -> Ground Venison direct transformation exists: " + venisonTrimToGroundVenisonExists), false);
        source.sendSuccess(() -> Component.literal("Beef Forequarter -> Beef Chuck direct transformation exists: " + beefForequarterToChuckExists), false);
        source.sendSuccess(() -> Component.literal("Beef Forequarter -> Beef Bone direct transformation exists: " + beefForequarterToBoneExists), false);
        source.sendSuccess(() -> Component.literal("Development workstation block registered: " + workstationDiagnostic.blockRegistered()), false);
        source.sendSuccess(() -> Component.literal("Development workstation block entity registered: " + workstationDiagnostic.blockEntityRegistered()), false);
        source.sendSuccess(() -> Component.literal("Development workstation menu registered: " + workstationDiagnostic.menuRegistered()), false);
        source.sendSuccess(() -> Component.literal("Development workstation screen binding observed: " + ModClientRegistrationStatus.developmentWorkstationScreenRegistered()), false);
        source.sendSuccess(() -> Component.literal("Development workstation capability available: " + workstationDiagnostic.capabilityAvailable()), false);
        source.sendSuccess(() -> Component.literal("Beef Trim resolves to grind_beef for development station: " + workstationDiagnostic.beefTrimResolvesToGrindBeef()), false);
        source.sendSuccess(() -> Component.literal("grind_beef duration resolves to 60 ticks: " + workstationDiagnostic.grindBeefDurationIs60Ticks()), false);
        source.sendSuccess(() -> Component.literal("Prototype processing context validates: " + workstationDiagnostic.prototypeContextValidates()), false);
        source.sendSuccess(() -> Component.literal("Output mapping resolves to Ground Beef: " + workstationDiagnostic.outputMappingResolves()), false);
        source.sendSuccess(() -> Component.literal("Grinder block registered: " + grinderDiagnostic.blockRegistered()), false);
        source.sendSuccess(() -> Component.literal("Grinder block entity registered: " + grinderDiagnostic.blockEntityRegistered()), false);
        source.sendSuccess(() -> Component.literal("Grinder menu registered: " + grinderDiagnostic.menuRegistered()), false);
        source.sendSuccess(() -> Component.literal("Grinder screen binding observed: " + ModClientRegistrationStatus.grinderScreenRegistered()), false);
        source.sendSuccess(() -> Component.literal("Grinder capability available: " + grinderDiagnostic.capabilityAvailable()), false);
        source.sendSuccess(() -> Component.literal("Built-in grind_beef supports Grinder capability: " + grinderDiagnostic.grindBeefSupportsCapability()), false);
        source.sendSuccess(() -> Component.literal("Built-in grind_pork supports Grinder capability: " + grinderDiagnostic.grindPorkSupportsCapability()), false);
        source.sendSuccess(() -> Component.literal("Built-in grind_chicken supports Grinder capability: " + grinderDiagnostic.grindChickenSupportsCapability()), false);
        source.sendSuccess(() -> Component.literal("Built-in grind_bison supports Grinder capability for Buffalo: " + grinderDiagnostic.grindBisonSupportsCapability()), false);
        source.sendSuccess(() -> Component.literal("Built-in grind_lamb supports Grinder capability: " + grinderDiagnostic.grindLambSupportsCapability()), false);
        source.sendSuccess(() -> Component.literal("Built-in grind_venison supports Grinder capability: " + grinderDiagnostic.grindVenisonSupportsCapability()), false);
        source.sendSuccess(() -> Component.literal("Beef Trim resolves to grind_beef for Grinder: " + grinderDiagnostic.beefTrimResolvesToGrindBeef()), false);
        source.sendSuccess(() -> Component.literal("Pork Trim resolves to grind_pork for Grinder: " + grinderDiagnostic.porkTrimResolvesToGrindPork()), false);
        source.sendSuccess(() -> Component.literal("Chicken Trim resolves to grind_chicken for Grinder: " + grinderDiagnostic.chickenTrimResolvesToGrindChicken()), false);
        source.sendSuccess(() -> Component.literal("Buffalo Trim resolves to grind_bison for Grinder: " + grinderDiagnostic.bisonTrimResolvesToGrindBison()), false);
        source.sendSuccess(() -> Component.literal("Lamb Trim resolves to grind_lamb for Grinder: " + grinderDiagnostic.lambTrimResolvesToGrindLamb()), false);
        source.sendSuccess(() -> Component.literal("Venison Trim resolves to grind_venison for Grinder: " + grinderDiagnostic.venisonTrimResolvesToGrindVenison()), false);
        source.sendSuccess(() -> Component.literal("Grinder grind_beef duration resolves to 60 ticks: " + grinderDiagnostic.grindBeefDurationIs60Ticks()), false);
        source.sendSuccess(() -> Component.literal("Ground Beef output mapping resolves for Grinder: " + grinderDiagnostic.outputMappingResolves()), false);
        source.sendSuccess(() -> Component.literal("Ground Pork output mapping resolves for Grinder: " + grinderDiagnostic.groundPorkOutputMappingResolves()), false);
        source.sendSuccess(() -> Component.literal("Ground Chicken output mapping resolves for Grinder: " + grinderDiagnostic.groundChickenOutputMappingResolves()), false);
        source.sendSuccess(() -> Component.literal("Ground Buffalo output mapping resolves for Grinder: " + grinderDiagnostic.groundBisonOutputMappingResolves()), false);
        source.sendSuccess(() -> Component.literal("Ground Lamb output mapping resolves for Grinder: " + grinderDiagnostic.groundLambOutputMappingResolves()), false);
        source.sendSuccess(() -> Component.literal("Ground Venison output mapping resolves for Grinder: " + grinderDiagnostic.groundVenisonOutputMappingResolves()), false);
        source.sendSuccess(() -> Component.literal("Bandsaw block registered: " + bandsawDiagnostic.blockRegistered()), false);
        source.sendSuccess(() -> Component.literal("Bandsaw upper block registered: " + bandsawDiagnostic.upperBlockRegistered()), false);
        source.sendSuccess(() -> Component.literal("Bandsaw block entity registered: " + bandsawDiagnostic.blockEntityRegistered()), false);
        source.sendSuccess(() -> Component.literal("Bandsaw menu registered: " + bandsawDiagnostic.menuRegistered()), false);
        source.sendSuccess(() -> Component.literal("Bandsaw screen binding observed: " + ModClientRegistrationStatus.bandsawScreenRegistered()), false);
        source.sendSuccess(() -> Component.literal("Bandsaw capability available: " + bandsawDiagnostic.capabilityAvailable()), false);
        source.sendSuccess(() -> Component.literal("Built-in break_beef_forequarter supports Bandsaw capability: " + bandsawDiagnostic.breakForequarterSupportsCapability()), false);
        source.sendSuccess(() -> Component.literal("Beef Forequarter resolves to break_beef_forequarter for Bandsaw: " + bandsawDiagnostic.forequarterResolvesToBreakdown()), false);
        source.sendSuccess(() -> Component.literal("Bandsaw break_beef_forequarter duration resolves to 120 ticks: " + bandsawDiagnostic.breakdownDurationIs120Ticks()), false);
        source.sendSuccess(() -> Component.literal("Bandsaw output mappings resolve: " + bandsawDiagnostic.outputMappingsResolve()), false);
        source.sendSuccess(() -> Component.literal("Development workstation remains available: " + (
                workstationDiagnostic.blockRegistered()
                        && workstationDiagnostic.blockEntityRegistered()
                        && workstationDiagnostic.menuRegistered()
        )), false);
        if (!productRoundTrip.detail().isBlank()) {
            source.sendSuccess(() -> Component.literal("Product round-trip detail: " + productRoundTrip.detail()), false);
        }
        if (!workstationDiagnostic.detail().isBlank()) {
            source.sendSuccess(() -> Component.literal("Workstation diagnostic detail: " + workstationDiagnostic.detail()), false);
        }
        if (!grinderDiagnostic.detail().isBlank()) {
            source.sendSuccess(() -> Component.literal("Grinder diagnostic detail: " + grinderDiagnostic.detail()), false);
        }
        if (!bandsawDiagnostic.detail().isBlank()) {
            source.sendSuccess(() -> Component.literal("Bandsaw diagnostic detail: " + bandsawDiagnostic.detail()), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static ProductRoundTripDiagnostic verifyFreshProductStackRoundTrip() {
        try {
            ProductDataResult<ProductStackData> originalDataResult =
                    ProductStackAdapter.readProductData(ModItems.BEEF_TRIM_TEST.get().getDefaultInstance());
            if (!originalDataResult.succeeded()) {
                return ProductRoundTripDiagnostic.failed(originalDataResult.failureReason().orElseThrow().code());
            }

            ProductStackData originalData = originalDataResult.orThrow();
            ProductDataResult<Product> productResult = ProductStackAdapter.toProduct(originalData);
            if (!productResult.succeeded()) {
                return ProductRoundTripDiagnostic.failed(productResult.failureReason().orElseThrow().code());
            }

            ProductDataResult<ProductStackData> roundTripDataResult = ProductStackAdapter.fromProduct(productResult.orThrow());
            if (!roundTripDataResult.succeeded()) {
                return ProductRoundTripDiagnostic.failed(roundTripDataResult.failureReason().orElseThrow().code());
            }

            ProductStackData roundTripData = roundTripDataResult.orThrow();
            boolean quantityPreserved = originalData.quantityValue() == roundTripData.quantityValue()
                    && originalData.quantityUnitId().equals(roundTripData.quantityUnitId());
            boolean qualityPreserved = originalData.qualityScore() == roundTripData.qualityScore();
            return new ProductRoundTripDiagnostic(originalData.equals(roundTripData), quantityPreserved, qualityPreserved, "");
        } catch (RuntimeException exception) {
            return ProductRoundTripDiagnostic.failed(exception.getClass().getSimpleName());
        }
    }

    private static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unavailable");
    }

    record InfoMessageLine(String translationKey, List<String> arguments) {
        InfoMessageLine {
            translationKey = Objects.requireNonNull(translationKey, "translationKey").strip();
            if (translationKey.isEmpty()) {
                throw new IllegalArgumentException("Info message translation key cannot be blank");
            }
            arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        }

        Component toComponent() {
            if (arguments.isEmpty()) {
                return Component.translatable(translationKey);
            }
            return Component.translatable(translationKey, arguments.toArray());
        }
    }

    private static WorkstationDiagnostic verifyWorkstation(net.minecraft.core.RegistryAccess registryAccess) {
        boolean blockRegistered = BuiltInRegistries.BLOCK.containsKey(DEVELOPMENT_WORKSTATION_BLOCK_ID);
        boolean blockEntityRegistered = ModBlockEntityTypes.DEVELOPMENT_PROCESSING_WORKSTATION.isBound()
                && DEVELOPMENT_WORKSTATION_BLOCK_ID.equals(ModBlockEntityTypes.DEVELOPMENT_PROCESSING_WORKSTATION.getId());
        boolean menuRegistered = ModMenuTypes.DEVELOPMENT_PROCESSING_WORKSTATION.isBound()
                && DEVELOPMENT_WORKSTATION_BLOCK_ID.equals(ModMenuTypes.DEVELOPMENT_PROCESSING_WORKSTATION.getId());
        boolean capabilityAvailable = DevelopmentWorkstationFixtures.capability()
                .supportsWorkstationCapability(BuiltInDefinitionIds.WORKSTATION_CAPABILITY_DEVELOPMENT_PROCESSING);
        boolean beefTrimResolvesToGrindBeef = false;
        boolean durationIs60Ticks = false;
        boolean contextValidates = false;
        boolean outputMappingResolves = DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.GROUND_BEEF);
        String detail = "";

        try {
            WorkstationOperationResolution resolution = new WorkstationOperationResolver().resolve(
                    registryAccess,
                    DevelopmentWorkstationFixtures.capability(),
                    ModItems.BEEF_TRIM_TEST.get().getDefaultInstance()
            );
            if (resolution.succeeded()) {
                var operation = resolution.operation().orElseThrow();
                beefTrimResolvesToGrindBeef = BuiltInDefinitionIds.GRIND_BEEF.equals(operation.operationId());
                durationIs60Ticks = operation.totalTicks() == WorkstationDuration.millisecondsToTicks(3_000);
                contextValidates = ProcessingEvaluator.validate(
                        operation.engineOperation(),
                        PrototypeProcessingContextValues.context(operation.inputProduct(), operation.engineOperation())
                ).accepted();
            } else {
                detail = resolution.failure().orElseThrow().code().reasonCode();
            }
        } catch (RuntimeException exception) {
            detail = exception.getClass().getSimpleName();
        }

        return new WorkstationDiagnostic(
                blockRegistered,
                blockEntityRegistered,
                menuRegistered,
                capabilityAvailable,
                beefTrimResolvesToGrindBeef,
                durationIs60Ticks,
                contextValidates,
                outputMappingResolves,
                detail
        );
    }

    private static GrinderDiagnostic verifyGrinder(net.minecraft.core.RegistryAccess registryAccess) {
        boolean blockRegistered = BuiltInRegistries.BLOCK.containsKey(GRINDER_BLOCK_ID);
        boolean blockEntityRegistered = ModBlockEntityTypes.GRINDER.isBound()
                && GRINDER_BLOCK_ID.equals(ModBlockEntityTypes.GRINDER.getId());
        boolean menuRegistered = ModMenuTypes.GRINDER.isBound()
                && GRINDER_BLOCK_ID.equals(ModMenuTypes.GRINDER.getId());
        boolean capabilityAvailable = GrinderWorkstation.capability().supportsWorkstationCapability(GrinderWorkstation.CAPABILITY_ID);
        boolean grindBeefSupportsCapability = false;
        boolean grindPorkSupportsCapability = false;
        boolean grindChickenSupportsCapability = false;
        boolean grindBisonSupportsCapability = false;
        boolean grindLambSupportsCapability = false;
        boolean grindVenisonSupportsCapability = false;
        boolean beefTrimResolvesToGrindBeef = false;
        boolean porkTrimResolvesToGrindPork = false;
        boolean chickenTrimResolvesToGrindChicken = false;
        boolean bisonTrimResolvesToGrindBison = false;
        boolean lambTrimResolvesToGrindLamb = false;
        boolean venisonTrimResolvesToGrindVenison = false;
        boolean durationIs60Ticks = false;
        boolean outputMappingResolves = DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.GROUND_BEEF);
        boolean groundPorkOutputMappingResolves = DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.GROUND_PORK);
        boolean groundChickenOutputMappingResolves = DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.GROUND_CHICKEN);
        boolean groundBisonOutputMappingResolves = DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.GROUND_BISON);
        boolean groundLambOutputMappingResolves = DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.GROUND_LAMB);
        boolean groundVenisonOutputMappingResolves = DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.GROUND_VENISON);
        String detail = "";

        try {
            DefinitionRegistryLoadResult definitionRegistries = DefinitionRegistryView.fromRegistryAccess(registryAccess);
            ProcessingDefinitionResolver definitionResolver = new ProcessingDefinitionResolver(definitionRegistries.view());
            DefinitionResolution<ResolvedProcessingOperationDefinition> resolvedBeefOperation =
                    definitionResolver.resolveOperation(BuiltInDefinitionIds.GRIND_BEEF);
            DefinitionResolution<ResolvedProcessingOperationDefinition> resolvedPorkOperation =
                    definitionResolver.resolveOperation(BuiltInDefinitionIds.GRIND_PORK);
            DefinitionResolution<ResolvedProcessingOperationDefinition> resolvedChickenOperation =
                    definitionResolver.resolveOperation(BuiltInDefinitionIds.GRIND_CHICKEN);
            DefinitionResolution<ResolvedProcessingOperationDefinition> resolvedBisonOperation =
                    definitionResolver.resolveOperation(BuiltInDefinitionIds.GRIND_BISON);
            DefinitionResolution<ResolvedProcessingOperationDefinition> resolvedLambOperation =
                    definitionResolver.resolveOperation(BuiltInDefinitionIds.GRIND_LAMB);
            DefinitionResolution<ResolvedProcessingOperationDefinition> resolvedVenisonOperation =
                    definitionResolver.resolveOperation(BuiltInDefinitionIds.GRIND_VENISON);
            grindBeefSupportsCapability = resolvedBeefOperation.succeeded()
                    && resolvedBeefOperation.orThrow().operation().workstationCapability()
                    .filter(GrinderWorkstation.CAPABILITY_ID::equals)
                    .isPresent();
            grindPorkSupportsCapability = resolvedPorkOperation.succeeded()
                    && resolvedPorkOperation.orThrow().operation().workstationCapability()
                    .filter(GrinderWorkstation.CAPABILITY_ID::equals)
                    .isPresent();
            grindChickenSupportsCapability = resolvedChickenOperation.succeeded()
                    && resolvedChickenOperation.orThrow().operation().workstationCapability()
                    .filter(GrinderWorkstation.CAPABILITY_ID::equals)
                    .isPresent();
            grindBisonSupportsCapability = resolvedBisonOperation.succeeded()
                    && resolvedBisonOperation.orThrow().operation().workstationCapability()
                    .filter(GrinderWorkstation.CAPABILITY_ID::equals)
                    .isPresent();
            grindLambSupportsCapability = resolvedLambOperation.succeeded()
                    && resolvedLambOperation.orThrow().operation().workstationCapability()
                    .filter(GrinderWorkstation.CAPABILITY_ID::equals)
                    .isPresent();
            grindVenisonSupportsCapability = resolvedVenisonOperation.succeeded()
                    && resolvedVenisonOperation.orThrow().operation().workstationCapability()
                    .filter(GrinderWorkstation.CAPABILITY_ID::equals)
                    .isPresent();

            WorkstationOperationResolution resolution = new WorkstationOperationResolver().resolve(
                    registryAccess,
                    GrinderWorkstation.capability(),
                    ModItems.BEEF_TRIM_TEST.get().getDefaultInstance()
            );
            if (resolution.succeeded()) {
                var operation = resolution.operation().orElseThrow();
                beefTrimResolvesToGrindBeef = BuiltInDefinitionIds.GRIND_BEEF.equals(operation.operationId());
                durationIs60Ticks = operation.totalTicks() == WorkstationDuration.millisecondsToTicks(3_000);
            } else {
                detail = resolution.failure().orElseThrow().code().reasonCode();
            }
            WorkstationOperationResolution porkResolution = new WorkstationOperationResolver().resolve(
                    registryAccess,
                    GrinderWorkstation.capability(),
                    ModItems.PORK_TRIM_TEST.get().getDefaultInstance()
            );
            if (porkResolution.succeeded()) {
                porkTrimResolvesToGrindPork = BuiltInDefinitionIds.GRIND_PORK.equals(porkResolution.operation().orElseThrow().operationId());
            }
            WorkstationOperationResolution chickenResolution = new WorkstationOperationResolver().resolve(
                    registryAccess,
                    GrinderWorkstation.capability(),
                    ModItems.CHICKEN_TRIM.get().getDefaultInstance()
            );
            if (chickenResolution.succeeded()) {
                chickenTrimResolvesToGrindChicken = BuiltInDefinitionIds.GRIND_CHICKEN.equals(chickenResolution.operation().orElseThrow().operationId());
            }
            WorkstationOperationResolution bisonResolution = new WorkstationOperationResolver().resolve(
                    registryAccess,
                    GrinderWorkstation.capability(),
                    ModItems.BISON_TRIM_TEST.get().getDefaultInstance()
            );
            if (bisonResolution.succeeded()) {
                bisonTrimResolvesToGrindBison = BuiltInDefinitionIds.GRIND_BISON.equals(bisonResolution.operation().orElseThrow().operationId());
            }
            WorkstationOperationResolution lambResolution = new WorkstationOperationResolver().resolve(
                    registryAccess,
                    GrinderWorkstation.capability(),
                    ModItems.LAMB_TRIM.get().getDefaultInstance()
            );
            if (lambResolution.succeeded()) {
                lambTrimResolvesToGrindLamb = BuiltInDefinitionIds.GRIND_LAMB.equals(lambResolution.operation().orElseThrow().operationId());
            }
            WorkstationOperationResolution venisonResolution = new WorkstationOperationResolver().resolve(
                    registryAccess,
                    GrinderWorkstation.capability(),
                    ModItems.VENISON_TRIM.get().getDefaultInstance()
            );
            if (venisonResolution.succeeded()) {
                venisonTrimResolvesToGrindVenison = BuiltInDefinitionIds.GRIND_VENISON.equals(venisonResolution.operation().orElseThrow().operationId());
            }
        } catch (RuntimeException exception) {
            detail = exception.getClass().getSimpleName();
        }

        return new GrinderDiagnostic(
                blockRegistered,
                blockEntityRegistered,
                menuRegistered,
                capabilityAvailable,
                grindBeefSupportsCapability,
                grindPorkSupportsCapability,
                grindChickenSupportsCapability,
                grindBisonSupportsCapability,
                grindLambSupportsCapability,
                grindVenisonSupportsCapability,
                beefTrimResolvesToGrindBeef,
                porkTrimResolvesToGrindPork,
                chickenTrimResolvesToGrindChicken,
                bisonTrimResolvesToGrindBison,
                lambTrimResolvesToGrindLamb,
                venisonTrimResolvesToGrindVenison,
                durationIs60Ticks,
                outputMappingResolves,
                groundPorkOutputMappingResolves,
                groundChickenOutputMappingResolves,
                groundBisonOutputMappingResolves,
                groundLambOutputMappingResolves,
                groundVenisonOutputMappingResolves,
                detail
        );
    }

    private static BandsawDiagnostic verifyBandsaw(net.minecraft.core.RegistryAccess registryAccess) {
        boolean blockRegistered = BuiltInRegistries.BLOCK.containsKey(BANDSAW_BLOCK_ID);
        boolean upperBlockRegistered = BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "bandsaw_upper"));
        boolean blockEntityRegistered = ModBlockEntityTypes.BANDSAW.isBound()
                && BANDSAW_BLOCK_ID.equals(ModBlockEntityTypes.BANDSAW.getId());
        boolean menuRegistered = ModMenuTypes.BANDSAW.isBound()
                && BANDSAW_BLOCK_ID.equals(ModMenuTypes.BANDSAW.getId());
        boolean capabilityAvailable = BandsawWorkstation.capability().supportsWorkstationCapability(BandsawWorkstation.CAPABILITY_ID);
        boolean breakForequarterSupportsCapability = false;
        boolean forequarterResolvesToBreakdown = false;
        boolean durationIs120Ticks = false;
        boolean outputMappingsResolve = DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.BEEF_CHUCK)
                && DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.BEEF_RIB)
                && DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.BEEF_PACKER_BRISKET)
                && DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.BEEF_PLATE)
                && DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.BEEF_SHANK)
                && DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.BEEF_TRIM)
                && DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.BEEF_FAT)
                && DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.BEEF_BONE);
        String detail = "";

        try {
            DefinitionRegistryLoadResult definitionRegistries = DefinitionRegistryView.fromRegistryAccess(registryAccess);
            ProcessingDefinitionResolver definitionResolver = new ProcessingDefinitionResolver(definitionRegistries.view());
            DefinitionResolution<ResolvedProcessingOperationDefinition> resolvedOperation =
                    definitionResolver.resolveOperation(BuiltInDefinitionIds.BREAK_BEEF_FOREQUARTER);
            breakForequarterSupportsCapability = resolvedOperation.succeeded()
                    && resolvedOperation.orThrow().operation().workstationCapability()
                    .filter(BandsawWorkstation.CAPABILITY_ID::equals)
                    .isPresent();

            WorkstationOperationResolution resolution = new WorkstationOperationResolver().resolve(
                    registryAccess,
                    BandsawWorkstation.capability(),
                    ModItems.BEEF_FOREQUARTER_TEST.get().getDefaultInstance()
            );
            if (resolution.succeeded()) {
                var operation = resolution.operation().orElseThrow();
                forequarterResolvesToBreakdown = BuiltInDefinitionIds.BREAK_BEEF_FOREQUARTER.equals(operation.operationId());
                durationIs120Ticks = operation.totalTicks() == WorkstationDuration.millisecondsToTicks(6_000);
            } else {
                detail = resolution.failure().orElseThrow().code().reasonCode();
            }
        } catch (RuntimeException exception) {
            detail = exception.getClass().getSimpleName();
        }

        return new BandsawDiagnostic(
                blockRegistered,
                upperBlockRegistered,
                blockEntityRegistered,
                menuRegistered,
                capabilityAvailable,
                breakForequarterSupportsCapability,
                forequarterResolvesToBreakdown,
                durationIs120Ticks,
                outputMappingsResolve,
                detail
        );
    }

    private record ProductRoundTripDiagnostic(
            boolean roundTripSucceeded,
            boolean quantityPreserved,
            boolean qualityPreserved,
            String detail
    ) {
        static ProductRoundTripDiagnostic failed(String detail) {
            return new ProductRoundTripDiagnostic(false, false, false, detail);
        }
    }

    private record WorkstationDiagnostic(
            boolean blockRegistered,
            boolean blockEntityRegistered,
            boolean menuRegistered,
            boolean capabilityAvailable,
            boolean beefTrimResolvesToGrindBeef,
            boolean grindBeefDurationIs60Ticks,
            boolean prototypeContextValidates,
            boolean outputMappingResolves,
            String detail
    ) {
    }

    private record GrinderDiagnostic(
            boolean blockRegistered,
            boolean blockEntityRegistered,
            boolean menuRegistered,
            boolean capabilityAvailable,
            boolean grindBeefSupportsCapability,
            boolean grindPorkSupportsCapability,
            boolean grindChickenSupportsCapability,
            boolean grindBisonSupportsCapability,
            boolean grindLambSupportsCapability,
            boolean grindVenisonSupportsCapability,
            boolean beefTrimResolvesToGrindBeef,
            boolean porkTrimResolvesToGrindPork,
            boolean chickenTrimResolvesToGrindChicken,
            boolean bisonTrimResolvesToGrindBison,
            boolean lambTrimResolvesToGrindLamb,
            boolean venisonTrimResolvesToGrindVenison,
            boolean grindBeefDurationIs60Ticks,
            boolean outputMappingResolves,
            boolean groundPorkOutputMappingResolves,
            boolean groundChickenOutputMappingResolves,
            boolean groundBisonOutputMappingResolves,
            boolean groundLambOutputMappingResolves,
            boolean groundVenisonOutputMappingResolves,
            String detail
    ) {
    }

    private record BandsawDiagnostic(
            boolean blockRegistered,
            boolean upperBlockRegistered,
            boolean blockEntityRegistered,
            boolean menuRegistered,
            boolean capabilityAvailable,
            boolean breakForequarterSupportsCapability,
            boolean forequarterResolvesToBreakdown,
            boolean breakdownDurationIs120Ticks,
            boolean outputMappingsResolve,
            String detail
    ) {
    }
}
