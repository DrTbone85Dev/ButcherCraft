package com.butchercraft.productioncontrol;

import com.butchercraft.registration.ModItems;
import com.butchercraft.registration.ModMenuTypes;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.world.production.ProductionChainStepStatus;
import com.butchercraft.world.production.ProductionFailureCode;
import com.butchercraft.world.production.ProductionRunStatus;
import com.butchercraft.world.production.ProductionWorkstationChainStatus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class ProductionOrderMenu extends AbstractContainerMenu {
    private static final int HAS_RUN = 0;
    private static final int STALE_REFERENCE = 1;
    private static final int RUN_STATUS = 2;
    private static final int CHAIN_STATUS = 3;
    private static final int GRINDER_STEP_STATUS = 4;
    private static final int PATTY_FORMER_STEP_STATUS = 5;
    private static final int GRINDER_ASSIGNED = 6;
    private static final int PATTY_FORMER_ASSIGNED = 7;
    private static final int GRINDER_MISSING = 8;
    private static final int PATTY_FORMER_MISSING = 9;
    private static final int GRINDER_PROGRESS = 10;
    private static final int PATTY_FORMER_PROGRESS = 11;
    private static final int GRINDER_WORKSTATION_STATE = 12;
    private static final int PATTY_FORMER_WORKSTATION_STATE = 13;
    private static final int CAN_CANCEL = 14;
    private static final int FAILURE_CODE = 15;
    private static final int NEXT_ACTION = 16;
    private static final int DATA_COUNT = 17;

    private final ContainerData data;
    private final ServerPlayer serverPlayer;
    private final InteractionHand hand;

    public ProductionOrderMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf ignoredExtraData) {
        this(containerId, playerInventory, new SimpleContainerData(DATA_COUNT), null, null);
    }

    public ProductionOrderMenu(int containerId, Inventory playerInventory, ServerPlayer serverPlayer, InteractionHand hand) {
        this(containerId, playerInventory, new ServerData(serverPlayer, hand), serverPlayer, hand);
    }

    private ProductionOrderMenu(
            int containerId,
            Inventory playerInventory,
            ContainerData data,
            ServerPlayer serverPlayer,
            InteractionHand hand
    ) {
        super(ModMenuTypes.PRODUCTION_ORDER.get(), containerId);
        Objects.requireNonNull(playerInventory, "playerInventory");
        this.data = Objects.requireNonNull(data, "data");
        this.serverPlayer = serverPlayer;
        this.hand = hand;
        addDataSlots(data);
    }

    public boolean hasRun() {
        return flag(HAS_RUN);
    }

    public boolean staleReference() {
        return flag(STALE_REFERENCE);
    }

    public ProductionRunStatus runStatus() {
        return enumValue(ProductionRunStatus.values(), data.get(RUN_STATUS), ProductionRunStatus.PLANNED);
    }

    public ProductionWorkstationChainStatus chainStatus() {
        return enumValue(
                ProductionWorkstationChainStatus.values(),
                data.get(CHAIN_STATUS),
                ProductionWorkstationChainStatus.UNKNOWN_OUTCOME
        );
    }

    public ProductionChainStepStatus grinderStepStatus() {
        return enumValue(
                ProductionChainStepStatus.values(),
                data.get(GRINDER_STEP_STATUS),
                ProductionChainStepStatus.UNKNOWN_OUTCOME
        );
    }

    public ProductionChainStepStatus pattyFormerStepStatus() {
        return enumValue(
                ProductionChainStepStatus.values(),
                data.get(PATTY_FORMER_STEP_STATUS),
                ProductionChainStepStatus.UNKNOWN_OUTCOME
        );
    }

    public boolean grinderAssigned() {
        return flag(GRINDER_ASSIGNED);
    }

    public boolean pattyFormerAssigned() {
        return flag(PATTY_FORMER_ASSIGNED);
    }

    public boolean grinderMissing() {
        return flag(GRINDER_MISSING);
    }

    public boolean pattyFormerMissing() {
        return flag(PATTY_FORMER_MISSING);
    }

    public int grinderProgressPercent() {
        return data.get(GRINDER_PROGRESS);
    }

    public int pattyFormerProgressPercent() {
        return data.get(PATTY_FORMER_PROGRESS);
    }

    public WorkstationState grinderWorkstationState() {
        return enumValue(WorkstationState.values(), data.get(GRINDER_WORKSTATION_STATE), WorkstationState.ERROR);
    }

    public WorkstationState pattyFormerWorkstationState() {
        return enumValue(WorkstationState.values(), data.get(PATTY_FORMER_WORKSTATION_STATE), WorkstationState.ERROR);
    }

    public boolean canCancel() {
        return flag(CAN_CANCEL);
    }

    public ProductionFailureCode failureCode() {
        int value = data.get(FAILURE_CODE);
        if (value < 0 || value >= ProductionFailureCode.values().length) {
            return null;
        }
        return ProductionFailureCode.values()[value];
    }

    public ProductionOrderNextAction nextAction() {
        return enumValue(ProductionOrderNextAction.values(), data.get(NEXT_ACTION), ProductionOrderNextAction.UNKNOWN_OUTCOME);
    }

    public Component nextActionComponent() {
        return Component.translatable(nextAction().translationKey());
    }

    public Component chainStatusComponent() {
        if (staleReference()) {
            return Component.translatable("screen.butchercraft.production_order.status.stale");
        }
        ProductionFailureCode failure = failureCode();
        if (failure != null) {
            return Component.translatable("screen.butchercraft.production_order.status.failure");
        }
        return Component.translatable("screen.butchercraft.production_order.status." + chainStatus().name().toLowerCase(java.util.Locale.ROOT));
    }

    public Component grinderStatusComponent() {
        return stepStatusComponent("grinder", grinderAssigned(), grinderMissing(), grinderStepStatus(), grinderWorkstationState());
    }

    public Component pattyFormerStatusComponent() {
        return stepStatusComponent(
                "patty_former",
                pattyFormerAssigned(),
                pattyFormerMissing(),
                pattyFormerStepStatus(),
                pattyFormerWorkstationState()
        );
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == ProductionOrderControl.cancelButtonId() && player instanceof ServerPlayer && hand != null) {
            return ProductionOrderControl.cancel((ServerPlayer) player, player.getItemInHand(hand));
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide) {
            return true;
        }
        if (hand == null) {
            return false;
        }
        return player.getItemInHand(hand).is(ModItems.PRODUCTION_ORDER.get());
    }

    private boolean flag(int index) {
        return data.get(index) != 0;
    }

    private static Component stepStatusComponent(
            String step,
            boolean assigned,
            boolean missing,
            ProductionChainStepStatus stepStatus,
            WorkstationState workstationState
    ) {
        if (!assigned) {
            return Component.translatable("screen.butchercraft.production_order.step." + step + ".unassigned");
        }
        if (missing) {
            return Component.translatable("screen.butchercraft.production_order.step." + step + ".missing");
        }
        if (stepStatus == ProductionChainStepStatus.COMPLETE || workstationState == WorkstationState.COMPLETE) {
            return Component.translatable("screen.butchercraft.production_order.step." + step + ".complete");
        }
        if (stepStatus == ProductionChainStepStatus.RUNNING || workstationState == WorkstationState.PROCESSING) {
            return Component.translatable("screen.butchercraft.production_order.step." + step + ".running");
        }
        return Component.translatable("screen.butchercraft.production_order.step." + step + ".ready");
    }

    private static <T extends Enum<T>> T enumValue(T[] values, int ordinal, T fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }

    private static final class ServerData implements ContainerData {
        private final ServerPlayer player;
        private final InteractionHand hand;
        private long lastGameTime = Long.MIN_VALUE;
        private ProductionOrderStatusSnapshot snapshot = ProductionOrderStatusSnapshot.empty();

        private ServerData(ServerPlayer player, InteractionHand hand) {
            this.player = Objects.requireNonNull(player, "player");
            this.hand = Objects.requireNonNull(hand, "hand");
        }

        @Override
        public int get(int index) {
            ProductionOrderStatusSnapshot current = snapshot();
            return switch (index) {
                case HAS_RUN -> current.hasRun() ? 1 : 0;
                case STALE_REFERENCE -> current.staleReference() ? 1 : 0;
                case RUN_STATUS -> current.runStatus().ordinal();
                case CHAIN_STATUS -> current.chainStatus().ordinal();
                case GRINDER_STEP_STATUS -> current.grinderStepStatus().ordinal();
                case PATTY_FORMER_STEP_STATUS -> current.pattyFormerStepStatus().ordinal();
                case GRINDER_ASSIGNED -> current.grinderAssigned() ? 1 : 0;
                case PATTY_FORMER_ASSIGNED -> current.pattyFormerAssigned() ? 1 : 0;
                case GRINDER_MISSING -> current.grinderMissing() ? 1 : 0;
                case PATTY_FORMER_MISSING -> current.pattyFormerMissing() ? 1 : 0;
                case GRINDER_PROGRESS -> current.grinderProgressPercent();
                case PATTY_FORMER_PROGRESS -> current.pattyFormerProgressPercent();
                case GRINDER_WORKSTATION_STATE -> current.grinderWorkstationState().ordinal();
                case PATTY_FORMER_WORKSTATION_STATE -> current.pattyFormerWorkstationState().ordinal();
                case CAN_CANCEL -> current.canCancel() ? 1 : 0;
                case FAILURE_CODE -> current.failureCode().map(Enum::ordinal).orElse(-1);
                case NEXT_ACTION -> current.nextAction().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }

        private ProductionOrderStatusSnapshot snapshot() {
            long gameTime = player.serverLevel().getGameTime();
            if (gameTime != lastGameTime) {
                lastGameTime = gameTime;
                snapshot = ProductionOrderControl.refreshStatus(
                        player,
                        ProductionOrderControl.dataOrDefault(player.getItemInHand(hand)),
                        true
                );
            }
            return snapshot;
        }
    }
}
