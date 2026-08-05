package com.rtsbuilding.rtsbuilding;

import com.mojang.logging.LogUtils;
import com.rtsbuilding.rtsbuilding.common.RtsBlocks;
import com.rtsbuilding.rtsbuilding.common.RtsCreativeTabs;
import com.rtsbuilding.rtsbuilding.common.RtsEntities;
import com.rtsbuilding.rtsbuilding.common.RtsItems;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsAPIImpl;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.data.SaveScheduler;
import com.rtsbuilding.rtsbuilding.server.feedback.RtsDamageFeedbackManager;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.RtsPipelineRegistration;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;
import com.rtsbuilding.rtsbuilding.server.service.RtsProgressRefresher;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.service.ServerTickOrchestrator;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import javax.imageio.spi.ServiceRegistry;

/**
 * Main entry class for the RTSbuilding mod.
 *
 * <p>This class is annotated with NeoForge's {@link Mod @Mod} annotation and is automatically instantiated
 * when the mod is loaded. It is responsible for the following core tasks:</p>
 * <ul>
 *   <li>Registering all blocks, items, entities, and creative tabs to the NeoForge registry</li>
 *   <li>Initializing the service registry, RTS API, and workflow pipelines</li>
 *   <li>Mounting the NeoForge global event bus to handle lifecycle events such as player login/logout and dimension changes</li>
 *   <li>Registering mod configuration (common config and client UI config)</li>
 * </ul>
 *
 * <p>The static inner class {@link GameEvents} defined within centralizes all game event subscriptions,
 * keeping the main class focused and clear in its responsibilities.</p>
 */
@Mod(RtsbuildingMod.MODID)
public class RtsbuildingMod {

    /** Unique mod identifier, used for registry namespace, resource paths, and event bus filtering */
    public static final String MODID = "rtsbuilding";

    /** Mod-specific SLF4J logger for consistent log output formatting */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Mod constructor, called by NeoForge when loading.
     *
     * <p>Execution order:</p>
     * <ol>
     *   <li>Register {@code commonSetup} on the Mod event bus</li>
     *   <li>Register entities, blocks, items, and creative tabs in order</li>
     *   <li>Register this instance on the NeoForge global event bus</li>
     *   <li>Load common configuration (TOML file)</li>
     *   <li>Client environment additionally loads UI configuration</li>
     * </ol>
     *
     * @param modEventBus  The mod event bus, used for mod lifecycle events
     * @param modContainer The mod container, used for registering configuration
     */
    public RtsbuildingMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        RtsEntities.register(modEventBus);
        RtsBlocks.register(modEventBus);
        RtsItems.register(modEventBus);
        RtsCreativeTabs.register(modEventBus);
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "rts_building/rtsbuilding-common.toml");
    }

    /**
     * Common initialization, executed during the Common phase of mod loading.
     *
     * <p>This phase runs on both client and server, responsible for initializing global components
     * that do not depend on a specific game world:</p>
     * <ol>
     *   <li>Initialize the central service registry ({@link ServiceRegistry})</li>
     *   <li>Initialize the RTS API, accessible by other mods via {@code RtsAPI.get()}</li>
     *   <li>Register all workflow pipelines</li>
     * </ol>
     *
     * @param event FML common setup event (no additional action required)
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        // Initialize the central service registry, where all backend services are registered
        RtsServer.init();

        // Initialize the RTS API, enabling addon mods to access mod functionality via RtsAPI.get()
        RtsAPIImpl.init();

        // Register all workflow pipelines, establishing processing chains for blueprint placement, mining, etc.
        RtsPipelineRegistration.registerAll();

        LOGGER.info("RTSBuilding common setup completed");
    }

    /**
     * Server starting event handler.
     * Fired when the Minecraft server begins loading; world data is not yet fully ready at this point.
     *
     * @param event Server starting event
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Server is starting...");
    }

    /**
     * Game event subscriber — centrally handles all game runtime events.
     *
     * <p>Automatically registered on the NeoForge event bus via the {@link EventBusSubscriber} annotation,
     * filtering by {@code modid} to only process events relevant to this mod.</p>
     *
     * <p>Handles the following game lifecycle events:</p>
     * <ul>
     *   <li>Player login/logout — initialize/clean up player state-related components</li>
     *   <li>Server start/stop — cache warm-up, data persistence</li>
     *   <li>Player dimension change — clear cache and state for the old dimension</li>
     *   <li>Player tick — drive camera, mining feedback, and other per-tick logic</li>
     *   <li>Server tick — drive background mining tasks and other system operations</li>
     * </ul>
     */
    @EventBusSubscriber(modid = RtsbuildingMod.MODID)
    static class GameEvents {
        /**
         * Player login event handler.
         *
         * <p>When a player joins the world, the following operations are performed:</p>
         * <ol>
         *   <li>Clean up orphan camera entities for the player (prevent camera remnants from old worlds)</li>
         *   <li>Initialize the damage feedback manager to display mining/damage hints</li>
         *   <li>Sync related player persistent data</li>
         *   <li>Restore the player's workflow state from world save to continue unfinished blueprint placements</li>
         * </ol>
         *
         * @param event Player login event
         */
        @SubscribeEvent
        static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                // Clean up camera entities left over from old worlds / reconnections
                RtsCameraManager.cleanupOrphanCameras(serverPlayer.getServer());
                // Register the damage feedback session for this player
                RtsDamageFeedbackManager.remember(serverPlayer);
                // Restore workflows from world save so previous blueprint placements etc. can continue
                RtsWorkflowEngine.getInstance().loadPlayerFromStore(
                        serverPlayer.getServer(), serverPlayer);
            }
        }

        /**
         * Server started event handler.
         *
         * <p>Fired after all worlds have loaded, at which point world data is fully ready:</p>
         * <ol>
         *   <li>Warm up creative tab caches to reduce latency when players first open them</li>
         *   <li>Clean up orphan camera entities across all dimensions (remnants after server restart/crash)</li>
         * </ol>
         *
         * @param event Server started event
         */
        @SubscribeEvent
        static void onServerStarted(ServerStartedEvent event) {
            // Warm up creative mode inventory caches to improve initial opening speed
            ServerTickOrchestrator.getInstance().warmCreativeTabCaches(event.getServer());
            // Clean up orphan camera entities across all dimensions
            RtsCameraManager.cleanupOrphanCameras(event.getServer());
            // Clean up legacy full-data files (delete after migration is complete)
            SaveScheduler.INSTANCE.cleanupLegacyFiles(event.getServer());
        }

        /**
         * Server stopped event handler.
         *
         * <p>Fired before the server fully shuts down, ensuring the following data is safely persisted:</p>
         * <ol>
         *   <li>Save all active workflows to the world save</li>
         *   <li>Flush all persistent data and clear caches</li>
         *   <li>Clear the workflow engine's in-memory data to prevent data leaks when switching worlds</li>
         * </ol>
         *
         * @param event Server stopped event
         */
        @SubscribeEvent
        static void onServerStopped(ServerStoppedEvent event) {
            // Save workflows first (SaveScheduler's cache is still valid at this point)
            RtsWorkflowEngine.getInstance().saveAll(event.getServer());
            // Then flush all persistent data and clear caches
            SaveScheduler.INSTANCE.onServerStopped();
            // Clear engine memory to prevent old world data from lingering when switching worlds
            RtsWorkflowEngine.getInstance().clearAllData();
        }

        /**
         * Player logout event handler.
         *
         * <p>When a player leaves the world or disconnects, the following state is cleaned up:</p>
         * <ol>
         *   <li>Stop and clean up the player's active camera session</li>
         *   <li>Remove damage feedback session to release resources</li>
         *   <li>Notify the session service to clean up the player's network session</li>
         *   <li>Clear pending placement scan cache (prevent stale data from persisting)</li>
         *   <li>Clear progress refresh cache</li>
         *   <li>Sync related player data</li>
         *   <li>Clear undo history (prevent coordinates from pointing to blocks in the old world after world switch)</li>
         * </ol>
         *
         * @param event Player logout event
         */
        @SubscribeEvent
        static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                // Stop camera session and destroy server-side camera entity
                RtsCameraManager.stopIfActive(serverPlayer);
                // Remove the player's damage feedback session
                RtsDamageFeedbackManager.forget(serverPlayer);
                // Clean up network session state
                RtsServer.get().session().onPlayerLogout(serverPlayer);
                // Clean up funnel (item pickup) tasks
                com.rtsbuilding.rtsbuilding.server.service.RtsFunnelService.INSTANCE.onPlayerDisconnect(serverPlayer);
                // Clear pending placement scan cache to prevent stale data confusion
                RtsPendingPlacementService.clearPlayerScanCache(serverPlayer.getUUID());
                // Clear progress refresh cache
                RtsProgressRefresher.clearPlayerCache(serverPlayer.getUUID());
                // Clear undo history — old world BlockPos are not valid for the new world
                ServerHistoryManager.clear(serverPlayer.getUUID());
                // Release the player's inventory signature cache
                ServerTickOrchestrator.getInstance().forgetPlayer(serverPlayer.getUUID());
                // Persist the player's data
                SaveScheduler.INSTANCE.onPlayerLogout(serverPlayer);
            }
        }

        /**
         * Player dimension change event handler.
         *
         * <p>Executed when the player switches between Overworld/Nether/End:</p>
         * <ol>
         *   <li>Stop camera session — coordinate data in the new dimension is different, requiring repositioning</li>
         *   <li>Cancel the player's pathfinding tasks — paths from the old dimension are invalid in the new one</li>
         *   <li>Unregister the old dimension's storage tick service to prevent operating on an invalid dimension</li>
         * </ol>
         *
         * @param event Player dimension change event
         */
        @SubscribeEvent
        static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                // Stop camera (coordinates are no longer valid, needs repositioning)
                RtsCameraManager.stopIfActive(serverPlayer);
                // Cancel pathfinding tasks from the old dimension
                RtsServer.get().pathfinding().cancel(serverPlayer);
                // Unregister the old dimension's storage tick service
                RtsStorageTickService.INSTANCE.unregisterPlayer(serverPlayer);
            }
        }

        /**
         * Player tick post event handler (executes once per tick, approximately 50ms).
         *
         * <p>Executed after player update logic completes, driving the following per-tick logic:</p>
         * <ol>
         *   <li>Drive the tick orchestrator to handle periodic tasks such as pending placements and progress updates</li>
         *   <li>Update damage feedback manager display effects (e.g., screen edge flash animations)</li>
         * </ol>
         *
         * @param event Player tick post event
         */
        @SubscribeEvent
        static void onPlayerTickPost(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                // Drive per-tick tasks for this player (e.g., pending placement progress consumption)
                ServerTickOrchestrator.getInstance().onPlayerTickPost(serverPlayer);
                // Update the player's damage feedback display effects
                RtsDamageFeedbackManager.tick(serverPlayer);
                // Update RTS mode anchor (follows the player's physical movement)
                RtsCameraManager.updateAnchorForPlayer(serverPlayer);
            }
        }

        /**
         * Server tick post event handler (executes once per tick, approximately 50ms).
         *
         * <p>Drives global background tasks:</p>
         * <ul>
         *   <li>Mining tick — processes all players' pending chain/area mining tasks</li>
         * </ul>
         * <p>This logic does not depend on a specific player, so scheduling it at the server level is more efficient.</p>
         *
         * @param event Server tick post event
         */
        @SubscribeEvent
        static void onServerTick(ServerTickEvent.Post event) {
            // Periodically flush persistent cache
            SaveScheduler.INSTANCE.onTick(event.getServer());
            // Drive per-tick consumption of global mining tasks
            ServerTickOrchestrator.getInstance().tickMining(event.getServer());
            // Drive per-tick consumption of funnel (item pickup) tasks
            com.rtsbuilding.rtsbuilding.server.service.RtsFunnelService.INSTANCE.onServerTick(event.getServer());
            // Flush merged workflow progress notifications (one packet per player per tick)
            RtsWorkflowEngine.getInstance().flushDirty();
        }
    }
}
