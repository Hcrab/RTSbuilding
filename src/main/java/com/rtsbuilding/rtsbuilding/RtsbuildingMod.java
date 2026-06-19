package com.rtsbuilding.rtsbuilding;


import com.mojang.logging.LogUtils;
import com.rtsbuilding.rtsbuilding.common.RtsBlocks;
import com.rtsbuilding.rtsbuilding.common.RtsCreativeTabs;
import com.rtsbuilding.rtsbuilding.common.RtsEntities;
import com.rtsbuilding.rtsbuilding.common.RtsItems;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsAPIImpl;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.feedback.RtsDamageFeedbackManager;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.RtsPipelineRegistration;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginService;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.*;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * RTSbuilding 模组的主入口类。
 *
 * <p>该类由 NeoForge 的 {@link Mod @Mod} 注解标记，在模组加载时自动实例化。
 * 负责以下核心工作：</p>
 * <ul>
 *   <li>注册所有方块、物品、实体和创造栏标签页到 NeoForge 注册表</li>
 *   <li>初始化服务注册表、RTS API 和工作流管线</li>
 *   <li>挂载 NeoForge 全局事件总线，处理玩家登录/登出、维度切换等生命周期事件</li>
 *   <li>注册模组配置（通用配置和客户端 UI 配置）</li>
 * </ul>
 *
 * <p>内部定义的 {@link GameEvents} 静态内部类集中处理所有游戏事件订阅，
 * 保持主类的职责清晰专注。</p>
 */
@Mod(RtsbuildingMod.MODID)
public class RtsbuildingMod {

    /** 模组唯一标识符，用于注册表命名空间、资源路径和事件总线过滤 */
    public static final String MODID = "rtsbuilding";

    /** 模组专用的 SLF4J 日志记录器，用于统一日志输出格式 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 模组构造函数，由 NeoForge 在加载时调用。
     *
     * <p>执行顺序：</p>
     * <ol>
     *   <li>注册 {@code commonSetup} 到 Mod 事件总线</li>
     *   <li>依次注册实体、方块、物品和创造栏标签页</li>
     *   <li>将当前实例注册到 NeoForge 全局事件总线</li>
     *   <li>加载通用配置（TOML 文件）</li>
     *   <li>客户端环境额外加载 UI 配置</li>
     * </ol>
     *
     * @param modEventBus  模组事件总线，用于 Mod 生命周期事件
     * @param modContainer 模组容器，用于注册配置
     */
    public RtsbuildingMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        RtsEntities.register(modEventBus);
        RtsBlocks.register(modEventBus);
        RtsItems.register(modEventBus);
        RtsCreativeTabs.register(modEventBus);
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.bootstrap.RtsClientBootstrap.registerConfigUi(modContainer);
        }
    }

    /**
     * 通用初始化，在模组加载的 Common 阶段执行。
     *
     * <p>该阶段在客户端和服务端都会运行，负责初始化不依赖具体游戏世界的全局组件：</p>
     * <ol>
     *   <li>初始化中央服务注册表（{@link ServiceRegistry}）</li>
     *   <li>初始化 RTS API，供其他模组通过 {@code RtsAPI.get()} 访问</li>
     *   <li>注册所有工作流管线（pipelines）</li>
     * </ol>
     *
     * @param event FML 通用初始化事件（无需额外操作）
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        // 初始化中央服务注册表，所有后端服务注册于此
        ServiceRegistry.init();

        // 初始化 RTS API，使 addon 模组可通过 RtsAPI.get() 访问模组功能
        RtsAPIImpl.init();

        // 注册所有工作流管线，为蓝图放置、挖掘等操作建立处理链路
        RtsPipelineRegistration.registerAll();

        LOGGER.info("RTSBuilding 通用初始化完成");
    }

    /**
     * 服务器启动事件处理器。
     * 在 Minecraft 服务器开始加载时触发，此时世界数据尚未完全就绪。
     *
     * @param event 服务器启动事件
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("服务器正在启动……");
    }

    /**
     * 游戏事件订阅器 —— 集中处理所有游戏运行时的事件。
     *
     * <p>使用 {@link EventBusSubscriber} 注解自动注册到 NeoForge 事件总线，
     * 按 {@code modid} 过滤，仅处理本模组相关的事件。</p>
     *
     * <p>负责以下游戏生命周期事件：</p>
     * <ul>
     *   <li>玩家登录/登出 —— 初始化/清理玩家状态的关联组件</li>
     *   <li>服务器启动/停止 —— 缓存预热、数据持久化</li>
     *   <li>玩家维度切换 —— 清理旧维度的缓存和状态</li>
     *   <li>玩家 Tick —— 每 Tick 驱动相机、挖掘反馈等逻辑</li>
     *   <li>服务器 Tick —— 驱动后台挖掘任务等系统操作</li>
     * </ul>
     */
    @EventBusSubscriber(modid = RtsbuildingMod.MODID)
    static class GameEvents {
        /**
         * 玩家登录事件处理器。
         *
         * <p>玩家加入世界时执行以下操作：</p>
         * <ol>
         *   <li>清理该玩家的孤儿相机实体（防止旧世界的相机残留）</li>
         *   <li>初始化伤害反馈管理器，准备显示挖掘/损坏提示</li>
         *   <li>通知进程管理器，恢复玩家的研究/升级数据</li>
         *   <li>同步相关玩家持久化数据</li>
         *   <li>从世界存档中恢复该玩家的工作流状态，使其能继续未完成的蓝图放置</li>
         * </ol>
         *
         * @param event 玩家登录事件
         */
        @SubscribeEvent
        static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                // 清理旧世界/重连残留的相机实体
                RtsCameraManager.cleanupOrphanCameras(serverPlayer.getServer());
                // 注册该玩家的伤害反馈会话
                RtsDamageFeedbackManager.remember(serverPlayer);
                // 加载玩家的进程数据（如已解锁的升级）
                RtsProgressionManager.onPlayerLogin(serverPlayer);
                // 同步与玩家相关的持久化插件数据
                RtsPluginService.syncRelatedPlayers(serverPlayer);
                // 从世界存档恢复工作流，使之前的蓝图放置等任务继续执行
                RtsWorkflowEngine.getInstance().loadPlayerFromStore(
                        serverPlayer.getServer(), serverPlayer);
            }
        }

        /**
         * 服务器启动完成事件处理器。
         *
         * <p>在所有世界加载完毕后触发，此时世界数据已完整就绪：</p>
         * <ol>
         *   <li>预热创造栏标签页的缓存，减少玩家首次打开时的延迟</li>
         *   <li>清理所有维度的孤儿相机实体（如服务端重启/崩溃后的残留）</li>
         * </ol>
         *
         * @param event 服务器启动完成事件
         */
        @SubscribeEvent
        static void onServerStarted(ServerStartedEvent event) {
            // 预热创造模式物品栏缓存，提升首次打开速度
            ServerTickOrchestrator.getInstance().warmCreativeTabCaches(event.getServer());
            // 清理所有维度的孤儿相机实体
            RtsCameraManager.cleanupOrphanCameras(event.getServer());
        }

        /**
         * 服务器停止事件处理器。
         *
         * <p>在服务器完全关闭前触发，确保以下数据安全落地：</p>
         * <ol>
         *   <li>将当前所有工作流状态持久化到世界存档中</li>
         *   <li>清空工作流引擎的内存数据，防止切换世界时数据泄漏</li>
         * </ol>
         *
         * @param event 服务器停止事件
         */
        @SubscribeEvent
        static void onServerStopped(ServerStoppedEvent event) {
            // 将所有活跃工作流保存到世界存档（在清空前执行）
            RtsWorkflowEngine.getInstance().saveAll(event.getServer());
            // 清空引擎内存，防止切换世界时旧世界的数据残留
            RtsWorkflowEngine.getInstance().clearAllData();
        }

        /**
         * 玩家登出事件处理器。
         *
         * <p>玩家离开世界或断开连接时清理以下状态：</p>
         * <ol>
         *   <li>停止并清理该玩家的活跃相机会话</li>
         *   <li>移除伤害反馈会话，释放资源</li>
         *   <li>通知会话服务清理该玩家的网络会话</li>
         *   <li>清理玩家进程数据的内存缓存</li>
         *   <li>清除挂起放置的扫描缓存（防止过期数据保留）</li>
         *   <li>清除进度刷新缓存</li>
         *   <li>同步相关玩家数据</li>
         *   <li>清空撤销历史（防止切换世界后坐标指向旧世界的方块）</li>
         * </ol>
         *
         * @param event 玩家登出事件
         */
        @SubscribeEvent
        static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                // 停止相机会话并销毁服务端相机实体
                RtsCameraManager.stopIfActive(serverPlayer);
                // 移除该玩家的伤害反馈会话
                RtsDamageFeedbackManager.forget(serverPlayer);
                // 清理网络会话状态
                ServiceRegistry.getInstance().session().onPlayerLogout(serverPlayer);
                // 清理进程管理器中的玩家数据
                RtsProgressionManager.onPlayerLogout(serverPlayer);
                // 清除挂起放置的扫描缓存，防止过期数据混淆
                RtsPendingPlacementService.clearPlayerScanCache(serverPlayer.getUUID());
                // 清除进度刷新缓存
                RtsProgressRefresher.clearPlayerCache(serverPlayer.getUUID());
                // 同步相关玩家持久化数据
                RtsPluginService.syncRelatedPlayers(serverPlayer);
                // 清空撤销历史 —— 旧世界的 BlockPos 不适用于新世界
                ServerHistoryManager.clear(serverPlayer.getUUID());
            }
        }

        /**
         * 玩家维度切换事件处理器。
         *
         * <p>玩家在主世界/下界/末地之间切换时执行：</p>
         * <ol>
         *   <li>停止相机会话 —— 新维度的坐标数据不同，需要重新定位</li>
         *   <li>取消该玩家的寻路任务 —— 旧维度的路径在新维度无效</li>
         *   <li>取消注册旧维度的存储 Tick 服务，防止操作失效的维度</li>
         * </ol>
         *
         * @param event 玩家维度切换事件
         */
        @SubscribeEvent
        static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                // 停止相机（坐标已失效，需重新定位）
                RtsCameraManager.stopIfActive(serverPlayer);
                // 取消旧维度的寻路任务
                ServiceRegistry.getInstance().pathfinding().cancel(serverPlayer);
                // 取消注册旧维度的存储服务
                RtsStorageTickService.INSTANCE.unregisterPlayer(serverPlayer);
            }
        }

        /**
         * 玩家 Tick 后事件处理器（每 Tick 执行一次，约 50ms）。
         *
         * <p>在玩家更新逻辑完成后执行，驱动以下每 Tick 逻辑：</p>
         * <ol>
         *   <li>驱动 Tick 编排器，处理该玩家的挂起放置、进度更新等周期性任务</li>
         *   <li>更新伤害反馈管理器的显示效果（如屏幕边缘闪烁动画）</li>
         * </ol>
         *
         * @param event 玩家 Tick 后事件
         */
        @SubscribeEvent
        static void onPlayerTickPost(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                // 驱动该玩家的每 Tick 任务（如挂起放置的进度消耗）
                ServerTickOrchestrator.getInstance().onPlayerTickPost(serverPlayer);
                // 更新该玩家的伤害反馈显示效果
                RtsDamageFeedbackManager.tick(serverPlayer);
            }
        }

        /**
         * 服务器 Tick 后事件处理器（每 Tick 执行一次，约 50ms）。
         *
         * <p>驱动全局性的后台任务：</p>
         * <ul>
         *   <li>挖掘 Tick —— 处理所有玩家挂起的连锁/范围挖掘任务</li>
         * </ul>
         * <p>这部分逻辑不依赖特定玩家，因此在服务器级别统一调度更高效。</p>
         *
         * @param event 服务器 Tick 后事件
         */
        @SubscribeEvent
        static void onServerTick(ServerTickEvent.Post event) {
            // 驱动全局挖掘任务的每 Tick 消耗
            ServerTickOrchestrator.getInstance().tickMining(event.getServer());
        }
    }
}
