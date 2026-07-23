package com.butchercraft.world.production;

import com.butchercraft.world.business.runtime.BusinessRuntimeManager;
import com.butchercraft.world.economy.actor.EconomicActorManager;
import com.butchercraft.world.economy.order.ContractManager;
import com.butchercraft.world.economy.order.OrderManager;
import com.butchercraft.world.goods.GoodManager;
import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.transaction.TransactionManager;
import com.butchercraft.world.workforce.WorkforceManager;

import java.util.Objects;

public record ProductionDependencies(
        GoodManager goodManager,
        EconomicActorManager actorManager,
        BusinessRuntimeManager businessRuntimeManager,
        WorkforceManager workforceManager,
        InventoryManager inventoryManager,
        TransactionManager transactionManager,
        OrderManager orderManager,
        ContractManager contractManager
) {
    public ProductionDependencies {
        goodManager = Objects.requireNonNull(goodManager, "goodManager");
        actorManager = Objects.requireNonNull(actorManager, "actorManager");
        businessRuntimeManager = Objects.requireNonNull(businessRuntimeManager, "businessRuntimeManager");
        workforceManager = Objects.requireNonNull(workforceManager, "workforceManager");
        inventoryManager = Objects.requireNonNull(inventoryManager, "inventoryManager");
        transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
        orderManager = Objects.requireNonNull(orderManager, "orderManager");
        contractManager = Objects.requireNonNull(contractManager, "contractManager");
    }
}
