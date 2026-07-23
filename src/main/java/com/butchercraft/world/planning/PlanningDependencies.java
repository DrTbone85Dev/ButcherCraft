package com.butchercraft.world.planning;

import com.butchercraft.world.business.runtime.BusinessRuntimeManager;
import com.butchercraft.world.economy.actor.EconomicActorManager;
import com.butchercraft.world.economy.order.ContractManager;
import com.butchercraft.world.economy.order.OrderManager;
import com.butchercraft.world.goods.GoodManager;
import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.production.ProductionManager;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.transaction.TransactionManager;
import com.butchercraft.world.workforce.WorkforceManager;

import java.util.Objects;

public record PlanningDependencies(
        GoodManager goodManager,
        EconomicActorManager actorManager,
        BusinessRuntimeManager businessRuntimeManager,
        WorkforceManager workforceManager,
        InventoryManager inventoryManager,
        TransactionManager transactionManager,
        OrderManager orderManager,
        ContractManager contractManager,
        ProductionManager productionManager,
        SimulationSchedulerManager schedulerManager
) {
    public PlanningDependencies {
        Objects.requireNonNull(goodManager); Objects.requireNonNull(actorManager);
        Objects.requireNonNull(businessRuntimeManager); Objects.requireNonNull(workforceManager);
        Objects.requireNonNull(inventoryManager); Objects.requireNonNull(transactionManager);
        Objects.requireNonNull(orderManager); Objects.requireNonNull(contractManager);
        Objects.requireNonNull(productionManager); Objects.requireNonNull(schedulerManager);
    }
}
