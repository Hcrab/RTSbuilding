package com.rtsbuilding.rtsbuilding.client.module.remote;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.compat.RtsClientRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;

/**
 * 远程菜单模块——管理远程容器菜单的生命周期。
 *
 * <p>移植自 {@code client_old/controller/ClientRtsController} 中的远程菜单管理逻辑。
 * 负责：</p>
 * <ul>
 *   <li>检测远程容器菜单是否打开（{@code containerMenu.containerId != 0}）</li>
 *   <li>安装 {@link RtsClientRemoteMenuCompat} 兼容层（包装/校验</li>
 *   <li>放宽远程菜单的容器有效性校验（使远程容器在 RTS 模式下仍然生效）</li>
 *   <li>替代原版 {@link CraftingScreen} 为 {@link RtsCraftTerminalScreen}</li>
 *   <li>管理 {@link BuilderScreen} 在容器菜单打开/关闭时的生命周期</li>
 *   <li>通过事件 {@link StateEvent.RemoteMenuOpened}/{@link StateEvent.RemoteMenuClosed}
 *       通知其他模块远程菜单状态变化</li>
 * </ul>
 */
public final class RemoteMenuModule implements FeatureModule {

    // ======================================================================
    //  常数
    // ======================================================================

    /** 远程菜单打开的等待 tick 数（服务端对容器打开响应有延迟） */
    private static final int REMOTE_MENU_OPEN_GRACE_TICKS = 80;
    /** 容器菜单已打开但无对应 Screen 时的恢复超时 tick 数 */
    private static final int SCREENLESS_REMOTE_MENU_RECOVERY_TICKS = 10;

    // ======================================================================
    //  运行时状态
    // ======================================================================

    /** 远程菜单打开等待 tick 计时器 */
    private int pendingRemoteMenuOpenTicks;
    /** 容器菜单已打开但无对应 Screen 的持续 tick 数 */
    private int screenlessRemoteMenuTicks;
    /** 当前已安装的宽松校验菜单实例（用于检测是否需要重新安装） */
    private AbstractContainerMenu relaxedRemoteMenu;
    /** 当前是否有远程菜单打开 */
    private boolean hasRemoteMenuOpen;
    /** 上一 tick 的远程菜单打开状态（用于检测边沿触发） */
    private boolean wasRemoteMenuOpen;

    /** 等待合成终端打开的标记 */
    private boolean pendingCraftTerminalOpen;
    /** 等待合成终端打开的 tick 超时 */
    private int pendingCraftTerminalOpenTicks;

    // ======================================================================
    //  Module interface
    // ======================================================================

    @Override
    public String moduleId() {
        return "remote_menu";
    }

    @Override
    public void onSessionEvent(StateEvent event) {
        if (event instanceof StateEvent.RtsToggled e) {
            if (!e.enabled()) {
                // RTS 关闭时清理远程菜单状态
                clearRemoteMenuValidationState();
                this.relaxedRemoteMenu = null;
                this.pendingCraftTerminalOpen = false;
                this.pendingCraftTerminalOpenTicks = 0;
                this.pendingRemoteMenuOpenTicks = 0;
                this.screenlessRemoteMenuTicks = 0;
                this.hasRemoteMenuOpen = false;
                this.wasRemoteMenuOpen = false;
            }
        } else if (event instanceof StateEvent.PlayerDied) {
            clearRemoteMenuValidationState();
            this.relaxedRemoteMenu = null;
            this.pendingCraftTerminalOpen = false;
            this.pendingCraftTerminalOpenTicks = 0;
            this.pendingRemoteMenuOpenTicks = 0;
            this.screenlessRemoteMenuTicks = 0;
            this.hasRemoteMenuOpen = false;
            this.wasRemoteMenuOpen = false;
        }
        // 注意：远程菜单提示由网络处理器直接调用 beginRemoteMenuOpenGrace()
        // 不经过事件系统，避免事件分发延迟
    }

    @Override
    public void tick(long epochMs, int tickIndex) {
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) return;

        // 检测远程容器菜单是否打开（containerId=0 表示无活动容器菜单）
        boolean remoteMenuOpen = mc.player.containerMenu != null
                && mc.player.containerMenu.containerId != 0;

        // ===== 无 Screen 的远程菜单恢复 =====
        // 容器菜单已打开但没有对应的 Screen → 可能是服务器先打开了菜单但客户端渲染延迟
        if (remoteMenuOpen && mc.screen == null && this.pendingRemoteMenuOpenTicks <= 0) {
            this.screenlessRemoteMenuTicks++;
            if (this.screenlessRemoteMenuTicks >= SCREENLESS_REMOTE_MENU_RECOVERY_TICKS) {
                // 超时仍未出现 Screen → 关闭远程菜单以防客户端崩溃
                RtsClientPacketGateway.sendCloseRemoteMenu();
                mc.player.closeContainer();
                clearRemoteMenuValidationState();
                this.relaxedRemoteMenu = null;
                remoteMenuOpen = false;
                this.screenlessRemoteMenuTicks = 0;
            }
        } else {
            this.screenlessRemoteMenuTicks = 0;
        }

        // ===== 合成终端管理 =====
        // 等待中的 CraftingMenu 已就绪 → 打开 RtsCraftTerminalScreen
        if (this.pendingCraftTerminalOpen
                && mc.player.containerMenu instanceof CraftingMenu
                && mc.player.containerMenu.containerId != 0
                && !(mc.screen instanceof RtsCraftTerminalScreen)) {
            Component pendingTitle = mc.screen != null
                    ? mc.screen.getTitle()
                    : Component.literal("RTS Craft Terminal");
            mc.setScreen(new RtsCraftTerminalScreen(
                    mc.player.containerMenu, mc.player.getInventory(), pendingTitle));
            this.pendingCraftTerminalOpen = false;
            this.pendingCraftTerminalOpenTicks = 0;
        }

        // 检测原版 CraftingScreen → 替换为 RtsCraftTerminalScreen
        if (mc.screen instanceof CraftingScreen craftingScreen
                && mc.player != null
                && craftingScreen.getMenu() instanceof CraftingMenu
                && !(mc.screen instanceof RtsCraftTerminalScreen)
                && shouldUseRtsCraftTerminalScreen(craftingScreen)) {
            mc.setScreen(new RtsCraftTerminalScreen(
                    craftingScreen.getMenu(),
                    mc.player.getInventory(),
                    craftingScreen.getTitle()));
            this.pendingCraftTerminalOpen = false;
            this.pendingCraftTerminalOpenTicks = 0;
        } else if (this.pendingCraftTerminalOpen) {
            if (this.pendingCraftTerminalOpenTicks > 0) {
                this.pendingCraftTerminalOpenTicks--;
            } else {
                this.pendingCraftTerminalOpen = false;
            }
        }

        // ===== 远程菜单安装与校验放宽 =====
        if (remoteMenuOpen) {
            this.pendingRemoteMenuOpenTicks = 0;
            try {
                AbstractContainerMenu activeRemoteMenu = RtsClientRemoteMenuCompat.install(
                        mc, mc.player.containerMenu);
                if (this.relaxedRemoteMenu != activeRemoteMenu) {
                    RtsClientRemoteMenuCompat.relaxValidation(activeRemoteMenu);
                    this.relaxedRemoteMenu = activeRemoteMenu;
                }
                // 远程菜单打开时：BuilderScreen 保持活跃，ScreenEvent.Opening 处理器
                // 会拦截容器屏幕并注入为 BuilderScreen 的子覆盖层，无需在此关闭 BuilderScreen。
                // 旧逻辑：mc.setScreen(null) 导致 BuilderScreen.onClose() 禁用相机，
                // 已由 ScreenEvent.Opening + BuilderScreen 容器覆盖层机制替代。
                if (mc.screen instanceof BuilderScreen) {
                    // 什么也不做——BuilderScreen 保持活跃
                }
            } catch (Throwable throwable) {
                handleRemoteMenuOpenFailure(mc, throwable);
                remoteMenuOpen = false;
            }
        } else if (this.pendingRemoteMenuOpenTicks > 0) {
            this.pendingRemoteMenuOpenTicks--;
        } else {
            clearRemoteMenuValidationState();
            this.relaxedRemoteMenu = null;
        }

        // ===== 状态变化边沿检测 → 分发事件 =====
        this.hasRemoteMenuOpen = remoteMenuOpen;
        if (this.hasRemoteMenuOpen != this.wasRemoteMenuOpen) {
            this.wasRemoteMenuOpen = this.hasRemoteMenuOpen;
            if (this.hasRemoteMenuOpen) {
                kernel().dispatch(new StateEvent.RemoteMenuOpened());
            } else {
                kernel().dispatch(new StateEvent.RemoteMenuClosed());
            }
        }
    }

    // ======================================================================
    //  Public API
    // ======================================================================

    /**
     * 开始远程菜单打开等待计时。
     * <p>在玩家与方块交互或合成终端打开时调用，
     * 给服务端留出时间创建并同步菜单到客户端。</p>
     */
    public void beginRemoteMenuOpenGrace() {
        this.pendingRemoteMenuOpenTicks = Math.max(
                this.pendingRemoteMenuOpenTicks, REMOTE_MENU_OPEN_GRACE_TICKS);
        this.screenlessRemoteMenuTicks = 0;
        RtsRemoteMenuCompat.beginClientRemoteMenuOpen();
    }

    /**
     * 打开 RTS 合成终端。
     * <p>向服务端发送打开合成终端请求，并开始等待菜单同步。</p>
     */
    public void openCraftTerminal() {
        this.pendingCraftTerminalOpen = true;
        this.pendingCraftTerminalOpenTicks = 120;
        beginRemoteMenuOpenGrace();
        RtsClientPacketGateway.sendOpenCraftTerminal();
    }

    /** 当前是否有远程菜单打开。 */
    public boolean isRemoteMenuOpen() {
        return this.hasRemoteMenuOpen;
    }

    /** 是否有挂起的远程菜单打开（正在等待服务端响应）。 */
    public boolean isRemoteMenuPending() {
        return this.pendingRemoteMenuOpenTicks > 0;
    }

    // ======================================================================
    //  Internal helpers
    // ======================================================================

    /**
     * 处理远程菜单打开失败。
     * <p>关闭容器菜单并向玩家显示错误信息。</p>
     */
    private void handleRemoteMenuOpenFailure(Minecraft minecraft, Throwable throwable) {
        String menuClass = minecraft.player != null && minecraft.player.containerMenu != null
                ? minecraft.player.containerMenu.getClass().getName()
                : "null";
        String screenClass = minecraft.screen != null
                ? minecraft.screen.getClass().getName()
                : "null";
        RtsbuildingMod.LOGGER.error(
                "RTS remote menu open failed for menu {} on screen {}; closing container to prevent a client crash.",
                menuClass, screenClass, throwable);
        clearRemoteMenuValidationState();
        this.pendingRemoteMenuOpenTicks = 0;
        if (minecraft.player != null) {
            RtsClientPacketGateway.sendCloseRemoteMenu();
            minecraft.player.closeContainer();
            minecraft.player.displayClientMessage(Component.literal("Open failed."), true);
        }
        minecraft.setScreen(null);
    }

    /** 清除远程菜单校验状态。 */
    private void clearRemoteMenuValidationState() {
        this.relaxedRemoteMenu = null;
        RtsRemoteMenuCompat.clearClientRemoteMenu();
    }

    /**
     * 判断是否需要将原版 {@link CraftingScreen} 替换为 {@link RtsCraftTerminalScreen}。
     */
    private boolean shouldUseRtsCraftTerminalScreen(CraftingScreen craftingScreen) {
        if (this.pendingCraftTerminalOpen) {
            return true;
        }
        return craftingScreen.getTitle() != null
                && "RTS Craft Terminal".equals(craftingScreen.getTitle().getString());
    }

    private RtsClientKernel kernel() {
        return RtsClientKernel.get();
    }
}
