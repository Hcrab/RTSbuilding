package com.rtsbuilding.rtsbuilding.server.service;

/**
 * Centralised tuning constants for server-side RTS services.
 *
 * <p>This class consolidates magic numbers that were previously scattered
 * across individual service implementations. Grouping them here makes
 * performance parameters visible at a glance and simplifies global tuning
 * without hunting through multiple files.
 *
 * <p>These are <b>internal engine parameters</b>, not user-facing
 * configuration — they are intentionally kept outside {@code Config.java}
 * (which uses NeoForge's {@code ModConfigSpec}) to avoid polluting the
 * server's config file with tuning knobs that have no practical use for
 * server admins.
 */
public final class RtsServiceConstants {

    private RtsServiceConstants() {
    }

    // ======================================================================
    //  Funnel service
    // ======================================================================

    /** Radius (blocks) within which the funnel picks up item entities. */
    public static final double FUNNEL_RADIUS = 2.0D;

    /** Maximum number of item entities processed per tick. */
    public static final int FUNNEL_MAX_ENTITIES_PER_TICK = 24;

    /** Maximum number of individual items processed per tick. */
    public static final int FUNNEL_MAX_ITEMS_PER_TICK = 48;

    /** Maximum number of buffered ItemStack entries before items are dropped. */
    public static final int FUNNEL_BUFFER_MAX_STACKS = 16;

    /** Tick interval between funnel processing cycles. */
    public static final int FUNNEL_TICK_INTERVAL = 2;

    // ======================================================================
    //  Placed-block recovery service
    // ======================================================================

    /** Maximum number of recovery jobs processed per tick. */
    public static final int PLACED_RECOVERY_MAX_JOBS_PER_TICK = 4;

    /** Maximum number of individual item stacks recovered per tick. */
    public static final int PLACED_RECOVERY_MAX_STACKS_PER_TICK = 8;

    // ======================================================================
    //  Storage tick service (adaptive cache refresh)
    // ======================================================================

    /** Fastest refresh rate: every tick (50 ms at 20 TPS). */
    public static final int MIN_TICK_RATE = 1;

    /** Slowest refresh rate: every 60 ticks (3 s at 20 TPS) when fully idle. */
    public static final int MAX_TICK_RATE = 60;

    /** Starting refresh rate after registration or alert. */
    public static final int DEFAULT_TICK_RATE = 8;

    /**
     * Maximum allowed initial refresh rate based on total slot count.
     * Even huge AE2 systems start at most this rate; the adaptive mechanism
     * quickly speeds up if changes are detected.
     */
    public static final int MAX_INITIAL_RATE = 8;

    /**
     * How many consecutive idle cycles before the adaptive scheduler slows
     * down. At the default rate of 8 ticks, this is 15 × 8 = 120 ticks (6 s)
     * of no activity before the interval starts to increase.
     */
    public static final int IDLE_THRESHOLD = 15;
}
