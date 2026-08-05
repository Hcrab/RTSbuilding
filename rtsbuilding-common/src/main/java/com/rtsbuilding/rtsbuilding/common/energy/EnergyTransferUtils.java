package com.rtsbuilding.rtsbuilding.common.energy;

import com.rtsbuilding.rtsbuilding.api.energy.Action;
import com.rtsbuilding.rtsbuilding.api.energy.IEnergyContainer;
import com.rtsbuilding.rtsbuilding.api.energy.IEnergyHandler;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Static helpers for moving energy between {@link IEnergyHandler}s and
 * {@link IEnergyContainer}s.
 * <p>
 * Modeled after Mekanism's {@code LongTransferUtils} / transfer logic: transfers
 * always simulate the source side first so that a partially-full destination
 * never loses energy.
 */
public final class EnergyTransferUtils {

    private EnergyTransferUtils() {
    }

    /**
     * Transfers up to {@code maxAmount} FE from {@code from} to {@code to}.
     * <p>
     * The source is simulated first, so if the destination can only accept part
     * of the requested amount only that part is actually extracted.
     *
     * @param from      The handler to extract from.
     * @param fromSide  The side to extract from (null for internal).
     * @param to        The handler to insert into.
     * @param toSide    The side to insert into (null for internal).
     * @param maxAmount The maximum amount of energy to transfer.
     * @param action    The action to perform, either {@link Action#EXECUTE} or {@link Action#SIMULATE}
     *
     * @return The amount of energy actually transferred.
     */
    public static long transfer(IEnergyHandler from, @Nullable Direction fromSide,
          IEnergyHandler to, @Nullable Direction toSide, long maxAmount, Action action) {
        if (maxAmount <= 0) {
            return 0;
        }
        long available = from.extractEnergy(maxAmount, fromSide, Action.SIMULATE);
        if (available <= 0) {
            return 0;
        }
        long accepted = to.insertEnergy(available, toSide, action);
        if (accepted > 0 && action.execute()) {
            from.extractEnergy(accepted, fromSide, Action.EXECUTE);
        }
        return accepted;
    }

    /**
     * Transfers up to {@code maxAmount} FE between two single containers.
     *
     * @param from      The container to extract from.
     * @param to        The container to insert into.
     * @param maxAmount The maximum amount of energy to transfer.
     * @param action    The action to perform, either {@link Action#EXECUTE} or {@link Action#SIMULATE}
     *
     * @return The amount of energy actually transferred.
     */
    public static long transfer(IEnergyContainer from, IEnergyContainer to, long maxAmount, Action action) {
        if (maxAmount <= 0) {
            return 0;
        }
        long available = from.extract(maxAmount, Action.SIMULATE, com.rtsbuilding.rtsbuilding.api.energy.AutomationType.EXTERNAL);
        if (available <= 0) {
            return 0;
        }
        long accepted = to.insert(available, action, com.rtsbuilding.rtsbuilding.api.energy.AutomationType.EXTERNAL);
        if (accepted > 0 && action.execute()) {
            from.extract(accepted, Action.EXECUTE, com.rtsbuilding.rtsbuilding.api.energy.AutomationType.EXTERNAL);
        }
        return accepted;
    }
}
