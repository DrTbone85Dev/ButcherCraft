package com.butchercraft.workstation;

import com.butchercraft.product.integration.ProductStackAdapter;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class WorkstationInventory extends ItemStackHandler {
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int SLOT_COUNT = 2;

    private final Runnable changeListener;
    private BooleanSupplier inputLocked = () -> false;
    private BooleanSupplier outputExtractionAllowed = () -> false;
    private boolean suppressChangeListener;

    public WorkstationInventory(Runnable changeListener) {
        super(SLOT_COUNT);
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    public void setInputLocked(BooleanSupplier inputLocked) {
        this.inputLocked = Objects.requireNonNull(inputLocked, "inputLocked");
    }

    public void setOutputExtractionAllowed(BooleanSupplier outputExtractionAllowed) {
        this.outputExtractionAllowed = Objects.requireNonNull(outputExtractionAllowed, "outputExtractionAllowed");
    }

    public ItemStack input() {
        return getStackInSlot(INPUT_SLOT);
    }

    public ItemStack output() {
        return getStackInSlot(OUTPUT_SLOT);
    }

    public void setInputInternal(ItemStack stack) {
        setStackMuted(INPUT_SLOT, stack);
    }

    public void setOutputInternal(ItemStack stack) {
        setStackMuted(OUTPUT_SLOT, stack);
    }

    public void clearInputInternal() {
        setStackMuted(INPUT_SLOT, ItemStack.EMPTY);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot == OUTPUT_SLOT) {
            return false;
        }
        if (slot != INPUT_SLOT || stack.isEmpty()) {
            return false;
        }
        return ProductStackAdapter.readProductData(stack).succeeded();
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (slot == OUTPUT_SLOT) {
            return stack;
        }
        return super.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot == INPUT_SLOT && inputLocked.getAsBoolean()) {
            return ItemStack.EMPTY;
        }
        if (slot == OUTPUT_SLOT && !outputExtractionAllowed.getAsBoolean()) {
            return ItemStack.EMPTY;
        }
        return super.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (!suppressChangeListener) {
            changeListener.run();
        }
    }

    private void setStackMuted(int slot, ItemStack stack) {
        suppressChangeListener = true;
        try {
            setStackInSlot(slot, stack);
        } finally {
            suppressChangeListener = false;
        }
        changeListener.run();
    }
}
