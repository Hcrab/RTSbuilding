package com.rtsbuilding.rtsbuilding.api.energy;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A multi-container energy handler, capable of exposing different sets of
 * containers depending on the {@link Direction} being interacted from.
 * <p>
 * Modeled after Mekanism's {@code ISidedStrictEnergyHandler} combined with
 * {@code IMekanismStrictEnergyHandler}: implementations only need to supply the
 * container list for a side, and all aggregate operations are provided as
 * defaults that distribute across those containers.
 */
public interface IEnergyHandler {

    /**
     * Used to check if an instance of {@link IEnergyHandler} actually has the
     * ability to handle energy.
     *
     * @return True if we are actually capable of handling energy.
     *
     * @implNote If this returns false the capability should not be exposed AND methods should turn reasonable defaults for not doing anything.
     */
    default boolean canHandleEnergy() {
        return true;
    }

    /**
     * Returns the list of {@link IEnergyContainer}s that this handler exposes
     * on the given side.
     *
     * @param side The side we are interacting with the handler from (null for internal).
     *
     * @return The containers exposed on the given side. If there are no
     *         containers for the side or {@link #canHandleEnergy()} is false then an empty list.
     */
    List<IEnergyContainer> getEnergyContainers(@Nullable Direction side);

    /**
     * Returns the {@link IEnergyContainer} that has the given index from the
     * list of containers on the given side.
     *
     * @param index The index of the container to retrieve.
     * @param side  The side we are interacting with the handler from (null for internal).
     *
     * @return The container, or {@code null} if the index is out of bounds.
     */
    @Nullable
    default IEnergyContainer getEnergyContainer(int index, @Nullable Direction side) {
        List<IEnergyContainer> containers = getEnergyContainers(side);
        return index >= 0 && index < containers.size() ? containers.get(index) : null;
    }

    /**
     * @return The number of energy storage units ("containers") available on the given side.
     */
    default int getContainerCount(@Nullable Direction side) {
        return getEnergyContainers(side).size();
    }

    /**
     * @return The total energy stored across all containers on the given side, in FE.
     */
    default long getEnergy(@Nullable Direction side) {
        long total = 0;
        for (IEnergyContainer container : getEnergyContainers(side)) {
            total += container.getEnergy();
        }
        return total;
    }

    /**
     * @return The total maximum energy across all containers on the given side, in FE.
     */
    default long getMaxEnergy(@Nullable Direction side) {
        long total = 0;
        for (IEnergyContainer container : getEnergyContainers(side)) {
            total += container.getMaxEnergy();
        }
        return total;
    }

    /**
     * @return The total amount of energy needed to fill all containers on the given side, in FE.
     */
    default long getNeeded(@Nullable Direction side) {
        long total = 0;
        for (IEnergyContainer container : getEnergyContainers(side)) {
            total += container.getNeeded();
        }
        return total;
    }

    /**
     * Inserts energy into this handler, distributing across all containers on
     * the given side.
     *
     * @param amount Energy to insert. This must not be modified by the handler.
     * @param side   The side we are interacting with the handler from (null for internal).
     * @param action The action to perform, either {@link Action#EXECUTE} or {@link Action#SIMULATE}
     *
     * @return The remaining energy that was not inserted (if the entire amount is accepted, then return 0).
     */
    default long insertEnergy(long amount, @Nullable Direction side, Action action) {
        if (amount <= 0) {
            return amount;
        }
        List<IEnergyContainer> containers = getEnergyContainers(side);
        // Prefer filling containers that already hold energy first (avoids scattering
        // a small amount across many empty containers), then fall back to any container.
        AutomationType automationType = AutomationType.handler(side);
        long remaining = amount;
        for (IEnergyContainer container : containers) {
            if (container.isEmpty()) {
                continue;
            }
            remaining = container.insert(remaining, action, automationType);
            if (remaining == 0) {
                return 0;
            }
        }
        for (IEnergyContainer container : containers) {
            remaining = container.insert(remaining, action, automationType);
            if (remaining == 0) {
                return 0;
            }
        }
        return remaining;
    }

    /**
     * Extracts energy from this handler, distributing across all containers on
     * the given side.
     *
     * @param amount Amount of energy to extract (may be greater than the current stored amount or the container's capacity). This must not be modified by the handler.
     * @param side   The side we are interacting with the handler from (null for internal).
     * @param action The action to perform, either {@link Action#EXECUTE} or {@link Action#SIMULATE}
     *
     * @return Energy extracted from the handler, must be 0 if no energy can be extracted.
     */
    default long extractEnergy(long amount, @Nullable Direction side, Action action) {
        if (amount <= 0) {
            return 0;
        }
        AutomationType automationType = AutomationType.handler(side);
        long extracted = 0;
        for (IEnergyContainer container : getEnergyContainers(side)) {
            if (container.isEmpty()) {
                continue;
            }
            long toExtract = Math.min(amount - extracted, Long.MAX_VALUE);
            extracted += container.extract(toExtract, action, automationType);
            if (extracted >= amount) {
                break;
            }
        }
        return extracted;
    }
}
