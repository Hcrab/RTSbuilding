package com.rtsbuilding.rtsbuilding.server.storage.state;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable state container for item funnel runtime state.
 *
 * <p>Extracted from RtsStorageSession, aggregated by the responsibility of "auto-collection and buffering of dropped items for the funnel".
 * Contains the funnel toggle, target position, tick cooldown, and a temporary buffer.
 *
 * <h3>Design constraints</h3>
 * <ul>
 *   <li><b>Pure data container</b> — contains no business logic, only holds public mutable fields</li>
 *   <li><b>Independently instantiable</b> — allows testing funnel state transitions without a full session</li>
 * </ul>
 */
public class RtsFunnelState {

    // ======================================================================
    // Item funnel runtime state
    // ======================================================================

    /** Whether funnel mode is active */
    public boolean funnelEnabled;

    /** Funnel output target position */
    public BlockPos funnelTarget;

    /** Funnel tick cooldown */
    public int funnelTickCooldown;

    /** Temporary funnel buffer holding pending dropped ItemStacks */
    public final List<ItemStack> funnelBuffer = new ArrayList<>();
}
