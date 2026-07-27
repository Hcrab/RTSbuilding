package com.rtsbuilding.rtsbuilding.server.service;

/**
 * Centralized tuning constants for server-side RTS services.
 *
 * <p>This class consolidates magic numbers previously scattered across various service implementations and tick loops,
 * centralizing all performance-related parameters in one place, simplifying global tuning and maintenance.
 * The class itself is a non-instantiable final utility class.
 *
 * <p><b>Design principles:</b> These are <b>internal engine parameters</b>, not user-facing configuration —
 * they are intentionally kept outside {@code Config.java} (which uses NeoForge's {@code ModConfigSpec}),
 * avoiding polluting the server's configuration file with tuning knobs that have no practical use for server administrators.
 *
 * <p><b>Parameter groups:</b>
 * <ul>
 *   <li><b>Funnel service (FUNNEL_*):</b>
 *     <ul>
 *       <li>{@link #FUNNEL_RADIUS} = {@value #FUNNEL_RADIUS}D — Radius for funnel item entity pickup</li>
 *       <li>{@link #FUNNEL_MAX_ENTITIES_PER_TICK} = {@value #FUNNEL_MAX_ENTITIES_PER_TICK} — Max entities processed per tick</li>
 *       <li>{@link #FUNNEL_MAX_ITEMS_PER_TICK} = {@value #FUNNEL_MAX_ITEMS_PER_TICK} — Max items processed per tick</li>
 *       <li>{@link #FUNNEL_BUFFER_MAX_STACKS} = {@value #FUNNEL_BUFFER_MAX_STACKS} — Buffer item stack upper limit</li>
 *       <li>{@link #FUNNEL_TICK_INTERVAL} = {@value #FUNNEL_TICK_INTERVAL} — Processing cycle interval</li>
 *     </ul>
 *   </li>
 *   <li><b>Placed block recovery service (PLACED_RECOVERY_*):</b>
 *     <ul>
 *       <li>{@link #PLACED_RECOVERY_MAX_JOBS_PER_TICK} = {@value #PLACED_RECOVERY_MAX_JOBS_PER_TICK} — Max recovery jobs per tick</li>
 *       <li>{@link #PLACED_RECOVERY_MAX_STACKS_PER_TICK} = {@value #PLACED_RECOVERY_MAX_STACKS_PER_TICK} — Max recovery stacks per tick</li>
 *     </ul>
 *   </li>
 *   <li><b>Storage cache refresh service (adaptive scheduling):</b>
 *     <ul>
 *       <li>{@link #MIN_TICK_RATE} = {@value #MIN_TICK_RATE} — Fastest refresh rate (every tick)</li>
 *       <li>{@link #MAX_TICK_RATE} = {@value #MAX_TICK_RATE} — Slowest refresh rate (every 60 ticks)</li>
 *       <li>{@link #DEFAULT_TICK_RATE} = {@value #DEFAULT_TICK_RATE} — Starting refresh rate after registration</li>
 *       <li>{@link #MAX_INITIAL_RATE} = {@value #MAX_INITIAL_RATE} — Maximum initial refresh rate cap based on slot count</li>
 *       <li>{@link #IDLE_THRESHOLD} = {@value #IDLE_THRESHOLD} — Consecutive idle cycles needed before deceleration</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public final class RtsServiceConstants {

    private RtsServiceConstants() {
    }

    // ======================================================================
    //  Funnel service
    // ======================================================================

    /** Radius for funnel item entity pickup (blocks). */
    public static final double FUNNEL_RADIUS = 2.0D;

    /** Max item entities processed per tick. */
    public static final int FUNNEL_MAX_ENTITIES_PER_TICK = 24;

    /** Max individual items processed per tick. */
    public static final int FUNNEL_MAX_ITEMS_PER_TICK = 48;

    /** Max buffer item stacks before dropping. */
    public static final int FUNNEL_BUFFER_MAX_STACKS = 16;

    /** Tick interval between funnel processing cycles. */
    public static final int FUNNEL_TICK_INTERVAL = 2;

    // ======================================================================
    //  Placed-block recovery service
    // ======================================================================

    /** Max recovery jobs processed per tick. */
    public static final int PLACED_RECOVERY_MAX_JOBS_PER_TICK = 4;

    /** Max individual item stacks recovered per tick. */
    public static final int PLACED_RECOVERY_MAX_STACKS_PER_TICK = 8;

    // ======================================================================
    //  Storage tick service (adaptive cache refresh)
    // ======================================================================

    /** Fastest refresh rate: every tick (50ms at 20 TPS). */
    public static final int MIN_TICK_RATE = 1;

    /** Slowest refresh rate: every 60 ticks when fully idle (3s at 20 TPS). */
    public static final int MAX_TICK_RATE = 60;

    /** Starting refresh rate after registration or alert. */
    public static final int DEFAULT_TICK_RATE = 8;

    /**
     * Maximum initial refresh rate allowed based on total slot count.
     * Even a massive AE2 system starts at most at this rate; the adaptive mechanism
     * rapidly accelerates when changes are detected.
     */
    public static final int MAX_INITIAL_RATE = 8;

    /**
     * How many consecutive idle cycles the adaptive scheduler needs before decelerating.
     * At the default 8 tick rate, this is 15 × 8 = 120 ticks (6s)
     * of inactivity, after which the interval starts increasing.
     */
    public static final int IDLE_THRESHOLD = 15;
}
