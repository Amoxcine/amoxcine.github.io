package fr.ascendant.renaissance.mekanism;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.chemical.attribute.ChemicalAttributeValidator;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** Endpoint-local view. Never expose the delegate or a stack owned by it. */
final class EndpointChemicalTank implements IChemicalTank {
    private final IChemicalTank delegate;
    private final BooleanSupplier accessible;

    EndpointChemicalTank(IChemicalTank delegate, BooleanSupplier accessible) {
        this.delegate = Objects.requireNonNull(delegate);
        this.accessible = Objects.requireNonNull(accessible);
    }

    private boolean open() { return accessible.getAsBoolean(); }

    @Override public ChemicalStack getStack() {
        return open() ? delegate.getStack().copy() : ChemicalStack.EMPTY;
    }

    @Override public void setStack(ChemicalStack stack) {
        if (open()) delegate.setStack(stack.copy());
    }

    @Override public void setStackUnchecked(ChemicalStack stack) {
        if (open()) delegate.setStackUnchecked(stack.copy());
    }

    @Override public ChemicalStack insert(ChemicalStack stack, Action action, AutomationType automation) {
        return open() ? delegate.insert(stack.copy(), action, automation).copy() : stack;
    }

    @Override public ChemicalStack extract(long amount, Action action, AutomationType automation) {
        return open() ? delegate.extract(amount, action, automation).copy() : ChemicalStack.EMPTY;
    }

    @Override public long getCapacity() { return open() ? delegate.getCapacity() : 0; }
    @Override public long getStored() { return open() ? delegate.getStored() : 0; }
    @Override public long getNeeded() { return open() ? delegate.getNeeded() : 0; }
    @Override public boolean isEmpty() { return !open() || delegate.isEmpty(); }

    @Override public boolean isValid(ChemicalStack stack) {
        return open() && delegate.isValid(stack.copy());
    }

    // Delegate mutations explicitly: default arithmetic would lose native rate/validation semantics.
    @Override public long setStackSize(long amount, Action action) {
        return open() ? delegate.setStackSize(amount, action) : 0;
    }

    @Override public long growStack(long amount, Action action) {
        return open() ? delegate.growStack(amount, action) : 0;
    }

    @Override public long shrinkStack(long amount, Action action) {
        return open() ? delegate.shrinkStack(amount, action) : 0;
    }

    @Override public void setEmpty() { if (open()) delegate.setEmpty(); }
    @Override public void onContentsChanged() { if (open()) delegate.onContentsChanged(); }

    @Override public ChemicalAttributeValidator getAttributeValidator() {
        return open() ? delegate.getAttributeValidator() : ChemicalAttributeValidator.DEFAULT;
    }

    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return open() ? delegate.serializeNBT(provider).copy() : new CompoundTag();
    }

    @Override public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        if (open()) delegate.deserializeNBT(provider, tag.copy());
    }
}
