package com.rtsbuilding.rtsbuilding.common.energy;

import com.rtsbuilding.rtsbuilding.api.energy.Action;
import com.rtsbuilding.rtsbuilding.api.energy.AutomationType;
import com.rtsbuilding.rtsbuilding.api.energy.IContentsListener;
import com.rtsbuilding.rtsbuilding.api.energy.IEnergyContainer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Default {@link IEnergyContainer} implementation, modeled after Mekanism's
 * {@code BasicEnergyContainer}.
 * <p>
 * Supports restricting insertions/extractions by {@link AutomationType} and an
 * optional {@link IContentsListener} that is notified whenever the stored energy
 * changes.
 */
public class BasicEnergyContainer implements IEnergyContainer {

    /** Only internal interactions may extract. */
    public static final Predicate<AutomationType> internalOnly = type -> type == AutomationType.INTERNAL;
    /** Only manual (player) interactions may extract. */
    public static final Predicate<AutomationType> manualOnly = type -> type == AutomationType.MANUAL;
    /** Everything except external interactions may insert/extract. */
    public static final Predicate<AutomationType> notExternal = type -> type != AutomationType.EXTERNAL;

    public static BasicEnergyContainer create(long maxEnergy, @Nullable IContentsListener listener) {
        return new BasicEnergyContainer(maxEnergy, type -> true, type -> true, listener);
    }

    public static BasicEnergyContainer input(long maxEnergy, @Nullable IContentsListener listener) {
        return new BasicEnergyContainer(maxEnergy, notExternal, type -> true, listener);
    }

    public static BasicEnergyContainer output(long maxEnergy, @Nullable IContentsListener listener) {
        return new BasicEnergyContainer(maxEnergy, type -> true, internalOnly, listener);
    }

    public static BasicEnergyContainer create(long maxEnergy, Predicate<AutomationType> canExtract,
          Predicate<AutomationType> canInsert, @Nullable IContentsListener listener) {
        Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        Objects.requireNonNull(canInsert, "Insertion validity check cannot be null");
        return new BasicEnergyContainer(maxEnergy, canExtract, canInsert, listener);
    }

    private long stored = 0L;
    protected final Predicate<AutomationType> canExtract;
    protected final Predicate<AutomationType> canInsert;
    private final long maxEnergy;
    @Nullable
    private final IContentsListener listener;

    protected BasicEnergyContainer(long maxEnergy, Predicate<AutomationType> canExtract,
          Predicate<AutomationType> canInsert, @Nullable IContentsListener listener) {
        if (maxEnergy < 0) {
            throw new IllegalArgumentException("Energy capacity cannot be negative");
        }
        this.maxEnergy = maxEnergy;
        this.canExtract = canExtract;
        this.canInsert = canInsert;
        this.listener = listener;
    }

    @Override
    public void onContentsChanged() {
        if (listener != null) {
            listener.onContentsChanged();
        }
    }

    @Override
    public long getEnergy() {
        return stored;
    }

    protected long clampEnergy(long energy) {
        return Math.min(energy, getMaxEnergy());
    }

    @Override
    public void setEnergy(long energy) {
        if (energy < 0) {
            throw new IllegalArgumentException("Energy cannot be negative");
        }
        energy = clampEnergy(energy);
        if (stored != energy) {
            stored = energy;
            onContentsChanged();
        }
    }

    /**
     * Helper to allow easily setting a rate at which energy can be inserted.
     *
     * @return The rate this container can insert at.
     *
     * @implNote By default this returns {@link Long#MAX_VALUE} to not actually limit the container's rate.
     */
    protected long getInsertRate(@Nullable AutomationType automationType) {
        return Long.MAX_VALUE;
    }

    /**
     * Helper to allow easily setting a rate at which energy can be extracted.
     *
     * @return The rate this container can extract at.
     *
     * @implNote By default this returns {@link Long#MAX_VALUE} to not actually limit the container's rate.
     */
    protected long getExtractRate(@Nullable AutomationType automationType) {
        return Long.MAX_VALUE;
    }

    @Override
    public long insert(long amount, Action action, AutomationType automationType) {
        if (amount <= 0L || !canInsert.test(automationType)) {
            return amount;
        }
        long needed = Math.min(getInsertRate(automationType), getNeeded());
        if (needed == 0L) {
            //Fail if we are a full container or our rate is zero
            return amount;
        }
        long toAdd = Math.min(amount, needed);
        if (action.execute()) {
            stored += toAdd;
            onContentsChanged();
        }
        return amount - toAdd;
    }

    @Override
    public long extract(long amount, Action action, AutomationType automationType) {
        if (isEmpty() || amount <= 0L || !canExtract.test(automationType)) {
            return 0L;
        }
        long ret = Math.min(Math.min(getExtractRate(automationType), getEnergy()), amount);
        if (ret > 0L && action.execute()) {
            stored -= ret;
            onContentsChanged();
        }
        return ret;
    }

    @Override
    public boolean isEmpty() {
        return stored == 0L;
    }

    @Override
    public long getMaxEnergy() {
        return maxEnergy;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();
        if (!isEmpty()) {
            nbt.putLong("stored", stored);
        }
        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        if (nbt.contains("stored", net.minecraft.nbt.Tag.TAG_LONG)) {
            setEnergy(nbt.getLong("stored"));
        }
    }
}
