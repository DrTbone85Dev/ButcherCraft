package com.butchercraft.integration.checkpoint;

import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.business.runtime.BusinessRuntimeManager;
import com.butchercraft.world.business.runtime.BusinessRuntimeStorage;
import com.butchercraft.world.economy.actor.EconomicActorManager;
import com.butchercraft.world.economy.actor.EconomicActorStorage;
import com.butchercraft.world.economy.order.ContractManager;
import com.butchercraft.world.economy.order.OrderManager;
import com.butchercraft.world.economy.order.persistence.ContractStorage;
import com.butchercraft.world.economy.order.persistence.OrderStorage;
import com.butchercraft.world.goods.BuiltInIndustryCatalog;
import com.butchercraft.world.goods.GoodManager;
import com.butchercraft.world.goods.GoodStorage;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.inventory.InventoryStorage;
import com.butchercraft.world.planning.PlanningDependencies;
import com.butchercraft.world.planning.PlanningExecutionBudget;
import com.butchercraft.world.planning.PlanningSelectionPolicy;
import com.butchercraft.world.planning.PlanningStorage;
import com.butchercraft.world.player.runtime.PlayerIdentityStorage;
import com.butchercraft.world.production.ProductionDependencies;
import com.butchercraft.world.production.ProductionManager;
import com.butchercraft.world.production.persistence.ProductionPersistenceSnapshot;
import com.butchercraft.world.production.persistence.ProductionStorage;
import com.butchercraft.world.simulation.SimulationConfiguration;
import com.butchercraft.world.simulation.time.WorldTimeConfiguration;
import com.butchercraft.world.simulation.time.WorldTimeStateStorage;
import com.butchercraft.world.simulation.scheduler.persistence.SimulationSchedulerStorage;
import com.butchercraft.world.transaction.TransactionManager;
import com.butchercraft.world.transaction.TransactionStorage;
import com.butchercraft.world.workforce.WorkforceManager;
import com.butchercraft.world.workforce.WorkforceStorage;
import com.butchercraft.world.workforce.department.DepartmentManager;
import com.butchercraft.world.workforce.department.DepartmentStorage;
import com.butchercraft.world.workforce.employee.EmployeeManager;
import com.butchercraft.world.workforce.employee.EmployeeStorage;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignmentManager;
import com.butchercraft.world.workforce.materialhandling.persistence.EmployeeMaterialHandlingAssignmentStorage;
import com.butchercraft.world.business.runtime.BusinessRuntimeCalendarStorage;
import com.butchercraft.world.checkpoint.OwnerNativeRestorationContext;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Re-reads the published owner set through each owner's existing native parser. */
public final class NativeOwnerLogicalStateVerifier {
    private NativeOwnerLogicalStateVerifier() {
    }

    public static void verify(MinecraftServer server, OwnerNativeRestorationContext context) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(context, "context");
        Path root = context.ownerRoot();

        GoodStorage goodStorage = new GoodStorage(path(root, "goods.json"), BuiltInIndustryCatalog.all());
        var goods = goodStorage.deserialize(read(root, "goods.json"));
        goods.validate();
        GoodManager goodManager = new GoodManager(goods);

        EconomicActorStorage actorStorage = new EconomicActorStorage(
                path(root, "economic_actors.json"), goods, BuiltInIndustryCatalog.all());
        var actors = actorStorage.deserialize(read(root, "economic_actors.json"));
        actors.validate();
        EconomicActorManager actorManager = new EconomicActorManager(actors);

        InventoryStorage inventoryStorage = new InventoryStorage(path(root, "inventory.json"), goods, actors);
        InventoryManager inventoryManager = inventoryStorage.deserialize(read(root, "inventory.json"));
        inventoryManager.validate();

        TransactionStorage transactionStorage = new TransactionStorage(
                path(root, "transactions.json"), inventoryManager);
        TransactionManager transactionManager = transactionStorage.deserialize(read(root, "transactions.json"));

        ContractStorage contractStorage = new ContractStorage(path(root, "contracts.json"), actors);
        ContractManager contractManager = contractStorage.deserialize(read(root, "contracts.json"));
        OrderStorage orderStorage = new OrderStorage(
                path(root, "orders.json"), actors, inventoryManager.registry(),
                transactionManager, contractManager);
        OrderManager orderManager = orderStorage.deserialize(read(root, "orders.json"));
        contractManager.validateLoadedOrderReferences(orderManager.registry());

        BusinessRuntimeStorage businessStorage = new BusinessRuntimeStorage(path(root, "business_runtime.json"));
        var businessRegistry = businessStorage.deserialize(read(root, "business_runtime.json"));
        BusinessRuntimeManager businessManager = new BusinessRuntimeManager(
                businessRegistry, SimulationConfiguration.standard());
        new BusinessRuntimeCalendarStorage(path(root, "business_calendar_runtime.json"))
                .deserialize(read(root, "business_calendar_runtime.json"));
        new WorldTimeStateStorage(path(root, "world_time.json"), WorldTimeConfiguration.defaults())
                .deserialize(read(root, "world_time.json"));

        WorkforceStorage workforceStorage = new WorkforceStorage(path(root, "workforce_definitions.json"));
        WorkforceManager workforceManager = new WorkforceManager(
                workforceStorage.deserialize(read(root, "workforce_definitions.json")));
        WorldIdentityRootIdentity world = new WorldIdentityRootIdentity(
                context.worldIdentityRoot().identity(),
                context.worldIdentityRoot().schemaVersion(),
                context.worldIdentityRoot().rootDigest());
        DepartmentManager departments = new DepartmentManager(
                new DepartmentStorage(path(root, "departments.json"))
                        .deserialize(read(root, "departments.json")));
        departments.validateCanonicalDefinitions(world);
        var employeeDirectory = new EmployeeStorage(path(root, "employee_records.json"))
                .deserialize(read(root, "employee_records.json"));
        EmployeeManager employees = new EmployeeManager(
                employeeDirectory, Math.max(1, employeeDirectory.registry().size()));
        employees.validateDepartmentReferences(departments.registry());
        var assignments = new EmployeeMaterialHandlingAssignmentStorage(
                path(root, "employee_material_handling_assignments.json"))
                .deserialize(read(root, "employee_material_handling_assignments.json"));
        new EmployeeMaterialHandlingAssignmentManager(assignments);

        ProductionDependencies productionDependencies = new ProductionDependencies(
                goodManager,
                actorManager,
                businessManager,
                workforceManager,
                inventoryManager,
                transactionManager,
                orderManager,
                contractManager
        );
        ProductionStorage productionStorage = new ProductionStorage(
                path(root, "production_processes.json"),
                path(root, "production_plans.json"),
                path(root, "production_runs.json"),
                productionDependencies
        );
        ProductionPersistenceSnapshot productionSnapshot = productionStorage.deserialize(
                read(root, "production_processes.json"),
                read(root, "production_plans.json"),
                read(root, "production_runs.json")
        );
        ProductionManager productionManager = new ProductionManager(
                productionDependencies,
                productionSnapshot.processRegistry(),
                productionSnapshot.planRegistry(),
                productionSnapshot.runs()
        );
        productionManager.validateForPersistence();

        var scheduler = new SimulationSchedulerStorage(
                path(root, "simulation_scheduler.json"),
                SimulationSchedulerService.INSTANCE.configuredHandlerRegistry(),
                0L
        ).deserialize(read(root, "simulation_scheduler.json"));
        scheduler.validateForPersistence();
        productionManager.validateSchedulerReferences(scheduler);

        PlanningDependencies planningDependencies = new PlanningDependencies(
                goodManager,
                actorManager,
                businessManager,
                workforceManager,
                inventoryManager,
                transactionManager,
                orderManager,
                contractManager,
                productionManager,
                scheduler
        );
        new PlanningStorage(
                root,
                planningDependencies,
                PlanningSelectionPolicy.standard(),
                PlanningExecutionBudget.standard()
        ).load();

        new PlayerIdentityStorage(path(root, "player_identities.json"))
                .deserialize(read(root, "player_identities.json"));
    }

    private static Path path(Path root, String fileName) {
        return root.resolve(fileName).toAbsolutePath().normalize();
    }

    private static String read(Path root, String fileName) {
        Path path = path(root, fileName);
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException("Failed to read restored native owner file: " + fileName, exception);
        }
    }
}
