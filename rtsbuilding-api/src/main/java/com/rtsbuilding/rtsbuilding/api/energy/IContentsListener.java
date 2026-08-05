package com.rtsbuilding.rtsbuilding.api.energy;

/**
 * Helper interface to reduce generic duplicate code between the various energy
 * handler types — fired whenever the contents a listener is monitoring change.
 * <p>
 * Modeled after Mekanism's {@code mekanism.api.IContentsListener}.
 */
@FunctionalInterface
public interface IContentsListener {

    /**
     * Called when the contents this listener is monitoring gets changed.
     */
    void onContentsChanged();
}
