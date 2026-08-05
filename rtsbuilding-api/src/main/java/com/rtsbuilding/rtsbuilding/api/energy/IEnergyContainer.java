package com.rtsbuilding.rtsbuilding.api.energy;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Range;

/**
 * A single energy storage buffer, measured in Forge Energy (FE).
 * <p>
 * Modeled after Mekanism's {@code mekanism.api.energy.IEnergyContainer}: the
 * distinguishing feature over NeoForge's plain {@code IEnergyStorage} is the
 * {@link Action}/{@link AutomationType} semantics — every mutation can be
 * simulated and gated by who is performing it.
 */
public interface IEnergyContainer {

    /**
     * @return The energy stored in this container, in FE.
     */
    @Range(from = 0, to = Long.MAX_VALUE)
    long getEnergy();

    /**
     * Overrides the amount of energy in this container.
     *
     * @param energy Energy to set this container's contents to. Must be greater than or equal to 0.
     *
     * @implNote If the internal amount does get updated make sure to call {@link #onContentsChanged()}.
     */
    void setEnergy(@Range(from = 0, to = Long.MAX_VALUE) long energy);

    /**
     * Inserts energy into this container and returns the remainder.
     *
     * @param amount         Energy to insert. Must be positive.
     * @param action         The action to perform, either {@link Action#EXECUTE} or {@link Action#SIMULATE}
     * @param automationType The method that this container is being interacted from.
     *
     * @return The remaining energy that was not inserted (if the entire amount is accepted, then return 0).
     */
    @Range(from = 0, to = Long.MAX_VALUE)
    default long insert(@Range(from = 0, to = Long.MAX_VALUE) long amount, Action action, AutomationType automationType) {
        if (amount <= 0) {
            //"Fail quick" if the given amount is empty
            return amount;
        }
        long needed = getNeeded();
        if (needed == 0) {
            //Fail if we are a full container
            return amount;
        }
        long toAdd = Math.min(amount, needed);
        if (action.execute()) {
            setEnergy(getEnergy() + toAdd);
        }
        return amount - toAdd;
    }

    /**
     * Extracts energy from this container.
     * <p>
     * The returned value must be 0 if nothing is extracted, otherwise it must be
     * less than or equal to {@code amount}.
     *
     * @param amount         Amount of energy to extract (may be greater than the current stored amount or the container's capacity). Must be positive or 0.
     * @param action         The action to perform, either {@link Action#EXECUTE} or {@link Action#SIMULATE}
     * @param automationType The method that this container is being interacted from.
     *
     * @return Energy extracted from the container, must be 0 if no energy can be extracted.
     */
    @Range(from = 0, to = Long.MAX_VALUE)
    default long extract(@Range(from = 0, to = Long.MAX_VALUE) long amount, Action action, AutomationType automationType) {
        if (isEmpty() || amount <= 0) {
            return 0;
        }
        long ret = Math.min(getEnergy(), amount);
        if (ret > 0 && action.execute()) {
            setEnergy(getEnergy() - ret);
        }
        return ret;
    }

    /**
     * @return The maximum amount of energy allowed in this container, in FE.
     */
    long getMaxEnergy();

    /**
     * Convenience method for checking if this container is empty.
     *
     * @return True if the container is empty, false otherwise.
     */
    default boolean isEmpty() {
        return getEnergy() == 0L;
    }

    /**
     * Convenience method for emptying this container.
     */
    default void setEmpty() {
        setEnergy(0L);
    }

    /**
     * @return The amount of energy needed by this container to reach a filled state.
     */
    @Range(from = 0, to = Long.MAX_VALUE)
    default long getNeeded() {
        return getMaxEnergy() - getEnergy();
    }

    /**
     * Called when the contents of this container changes, forwarding to the
     * attached listener if any.
     */
    default void onContentsChanged() {
    }

    /**
     * Serializes the container contents to NBT.
     */
    default CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();
        if (!isEmpty()) {
            nbt.putLong("stored", getEnergy());
        }
        return nbt;
    }

    /**
     * Deserializes the container contents from NBT.
     */
    default void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        if (nbt.contains("stored", net.minecraft.nbt.Tag.TAG_LONG)) {
            setEnergy(nbt.getLong("stored"));
        }
    }
}
