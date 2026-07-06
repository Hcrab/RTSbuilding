package com.rtsbuilding.rtsbuilding.client.screen.panel.handler;

import com.mojang.logging.LogUtils;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.render.pass.BoxSelector;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import com.rtsbuilding.rtsbuilding.client.screen.event.EventResult;
import com.rtsbuilding.rtsbuilding.client.screen.event.MouseClickEvent;
import com.rtsbuilding.rtsbuilding.client.screen.panel.select.BlockEntry;
import com.rtsbuilding.rtsbuilding.client.screen.panel.select.EntityEntry;
import com.rtsbuilding.rtsbuilding.client.screen.panel.select.SelectableEntry;
import com.rtsbuilding.rtsbuilding.client.screen.panel.select.SelectionHighlight;
import com.rtsbuilding.rtsbuilding.client.screen.panel.leftbar.LeftSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.rtsbuilding.rtsbuilding.client.screen.event.EventResult.CONSUMED;
import static com.rtsbuilding.rtsbuilding.client.screen.event.EventResult.PASS;

/**
 * 交互目标处理器——处理交互模式下与生物/方块的远程交互。
 *
 * <p>注册到 {@link com.rtsbuilding.rtsbuilding.client.screen.event.EventDispatcher}
 * 的 {@link com.rtsbuilding.rtsbuilding.client.screen.event.EventDispatcher#P_ENTITY_INTERACT}
 * 优先级（50）：</p>
 * <ul>
 *   <li><b>点击模式 + 交互模式 + 右键</b> → 检测悬停目标，直接交互</li>
 *   <li><b>框选模式 + 交互模式 + 框选完成(COMPLETE) + 右键</b> →
 *       扫描选区内实体，多实体弹出选择面板，单实体直接交互</li>
 * </ul>
 *
 * <p>框选区域扫描委托给 {@link BoxTargetCollector}，选择面板生命周期
 * 委托给 {@link SelectPanelController}，本类聚焦于调度决策。</p>
 */
public final class EntityInteractionHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final SelectionHighlight highlight;
    private final BoxTargetCollector targetCollector;
    private final SelectPanelController panelController;

    public EntityInteractionHandler(SelectionHighlight highlight) {
        this.highlight = highlight;
        this.targetCollector = new BoxTargetCollector();
        this.panelController = new SelectPanelController(highlight);
    }

    // ======================================================================
    //  公开 API（供 BuilderScreen 调用）
    // ======================================================================

    /**
     * 处理鼠标右键点击事件。
     * <p>同时处理点击模式的直接交互和框选模式的批量选择。</p>
     */
    public EventResult handleMouseClick(MouseClickEvent event, BuilderScreen screen,
                                         LeftSidebarPanel leftSidebarPanel) {
        if (event.button() != GLFW_BUTTON_RIGHT) return PASS;
        if (!screen.isInteractiveMode()) return PASS;
        if (isAltDown() || isShiftDown()) return PASS;

        // 已有选择面板打开时，不处理点击（由浮窗系统处理）
        if (panelController.isOpen()) return PASS;

        // ---- 点击模式：直接实体/方块交互 ----
        if (leftSidebarPanel.isClickButtonSelected() && !leftSidebarPanel.isBindModeActive()) {
            return handleDirectInteract(screen);
        }

        // ---- 框选模式：框选完成时尝试交互 ----
        if (!leftSidebarPanel.isClickButtonSelected()) {
            var sel = RtsClientKernel.get().renderPipeline().boxSelector;
            if (sel.getPhase() == BoxSelector.Phase.COMPLETE) {
                return handleBoxSelectInteract(screen, sel, (int) event.x(), (int) event.y());
            }
        }

        return PASS;
    }

    /** 校验当前选择面板条目是否仍有效，由 BuilderScreen 每帧调用。 */
    public void validatePanel(BuilderScreen screen) {
        if (!panelController.isOpen()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { panelController.close(); return; }

        var sel = RtsClientKernel.get().renderPipeline().boxSelector;
        if (sel.getPhase() != BoxSelector.Phase.COMPLETE) {
            panelController.close();
            return;
        }

        var cache = new BoxTargetCollector.BoxSelectorCache(sel.getMinCorner(), sel.getMaxCorner());
        List<Entity> currentEntities = targetCollector.collectEntities(
                mc.level, cache, mc.getCameraEntity());
        panelController.validate(screen, currentEntities, cache);
    }

    /** 选择面板是否打开。 */
    public boolean isSelectPanelOpen() {
        return panelController.isOpen();
    }

    /** 关闭当前选择面板。 */
    public void closeSelectPanel() {
        panelController.close();
    }

    // ======================================================================
    //  直接交互（点击模式）
    // ======================================================================

    /**
     * 点击模式下右键点击屏幕中的实体或方块。
     */
    private EventResult handleDirectInteract(BuilderScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null) return PASS;

        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) return PASS;

        var hit = ray.raycastNearest(mc);

        if (hit.hasEntity() && hit.entityHit() != null) {
            // 目标实体可能打开容器（村民交易、马鞍栏等）→ 提前关闭旧面板
            Entity target = hit.entityHit().getEntity();
            if (screen.hasContainerScreen() && isContainerEntity(target)) {
                screen.closeContainerScreen();
            }

            int entityId = target.getId();
            Vec3 hitLocation = hit.entityHit().getLocation();
            RtsClientPacketGateway.sendInteractEntityEmptyHand(
                    entityId, hitLocation, null, ray.origin(), ray.direction());
            return CONSUMED;
        }

        if (hit.hasBlock() && hit.blockHit() != null) {
            BlockHitResult blockHit = hit.blockHit();
            // 目标方块有 MenuProvider（箱子、熔炉等容器）→ 提前关闭旧面板
            if (screen.hasContainerScreen() && isContainerBlock(mc.level, blockHit.getBlockPos())) {
                screen.closeContainerScreen();
            }

            RtsClientPacketGateway.sendInteractEntityEmptyHand(
                    C2SRtsInteractPayload.NO_ENTITY,
                    blockHit.getLocation(), blockHit, ray.origin(), ray.direction());
            return CONSUMED;
        }

        return PASS;
    }

    // ======================================================================
    //  框选交互
    // ======================================================================

    /**
     * 框选完成后，右键点击扫描选区内可交互实体或方块。
     * <p>根据选区内目标情况分派到直接交互或选择面板。</p>
     */
    private EventResult handleBoxSelectInteract(BuilderScreen screen, BoxSelector sel,
                                                  int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return PASS;

        // 检测鼠标光标是否在框内
        var cache = validateCursorInBox(mc, screen, sel);
        if (cache == null) return PASS;

        // 收集选区内目标
        List<Entity> entities = targetCollector.collectEntities(
                mc.level, cache, mc.getCameraEntity());
        List<BoxTargetCollector.BlockInfo> guiBlocks = targetCollector.collectGuiBlocks(mc.level, cache);
        List<BoxTargetCollector.BlockInfo> nonGuiBlocks = targetCollector.collectNonGuiBlocks(mc.level, cache);

        LOGGER.info("[SelectInteract] box=[{}~{}] entities={} guiBlocks={} nonGuiInteractiveBlocks={}",
                cache.minCorner(), cache.maxCorner(), entities.size(), guiBlocks.size(), nonGuiBlocks.size());

        // 计算射线
        var ray = CursorRaycaster.computeCameraCenterRay(mc);
        if (ray == null) return PASS;
        Vec3 rayOrigin = ray.origin();
        Vec3 rayDir = ray.direction();

        boolean hasGuiTargets = !entities.isEmpty() || !guiBlocks.isEmpty();

        // ---- 纯非 GUI 交互方块：直接批量交互 ----
        if (!hasGuiTargets && !nonGuiBlocks.isEmpty()) {
            for (var info : nonGuiBlocks) {
                RtsClientPacketGateway.sendInteractEntityEmptyHand(
                        C2SRtsInteractPayload.NO_ENTITY,
                        info.hitLocation(), info.blockHit(), rayOrigin, rayDir);
            }
            sel.reset();
            return CONSUMED;
        }

        // ---- 有 GUI 目标 → 弹选择面板 ----
        List<SelectableEntry> entries = buildEntries(entities, guiBlocks, nonGuiBlocks);
        if (!entries.isEmpty()) {
            return panelController.show(entries, rayOrigin, rayDir, sel, screen, mouseX, mouseY);
        }

        // ---- 无任何可交互目标：与光标所在位置方块交互 ----
        var cursorHit = Objects.requireNonNull(CursorRaycaster.computeCursorRay(mc, screen))
                .raycastBlock(mc);
        if (cursorHit != null) {
            RtsClientPacketGateway.sendInteractEntityEmptyHand(
                    C2SRtsInteractPayload.NO_ENTITY,
                    cursorHit.getLocation(), cursorHit, rayOrigin, rayDir);
            sel.reset();
            return CONSUMED;
        }

        return PASS;
    }

    /**
     * 验证鼠标光标是否在框选区内，返回框选边界缓存。
     * @return 框选边界缓存，若光标不在框内则返回 null
     */
    private static BoxTargetCollector.BoxSelectorCache validateCursorInBox(
            Minecraft mc, BuilderScreen screen, BoxSelector sel) {
        var cursorRay = CursorRaycaster.computeCursorRay(mc, screen);
        if (cursorRay == null) {
            LOGGER.info("[SelectInteract] cursorRay is null");
            return null;
        }
        var cursorHit = cursorRay.raycastBlock(mc);
        if (cursorHit == null) {
            LOGGER.info("[SelectInteract] cursorHit is null (raycastBlock missed)");
            return null;
        }

        BlockPos min = sel.getMinCorner();
        BlockPos max = sel.getMaxCorner();
        if (min == null || max == null) return null;

        BlockPos hitPos = cursorHit.getBlockPos();
        if (hitPos.getX() < min.getX() || hitPos.getX() >= max.getX()
                || hitPos.getY() < min.getY() || hitPos.getY() >= max.getY()
                || hitPos.getZ() < min.getZ() || hitPos.getZ() >= max.getZ()) {
            return null;
        }

        return new BoxTargetCollector.BoxSelectorCache(min, max);
    }

    // ======================================================================
    //  条目构建
    // ======================================================================

    /** 将实体和方块列表合并为 SelectableEntry 列表。 */
    private static List<SelectableEntry> buildEntries(
            List<Entity> entities,
            List<BoxTargetCollector.BlockInfo> guiBlocks,
            List<BoxTargetCollector.BlockInfo> nonGuiBlocks) {
        int total = entities.size() + guiBlocks.size() + nonGuiBlocks.size();
        if (total == 0) return List.of();

        List<SelectableEntry> entries = new ArrayList<>(total);
        for (Entity entity : entities) {
            entries.add(new EntityEntry(
                    entity.getId(), entity,
                    entity.getDisplayName().getString(),
                    entity.position()));
        }
        for (var info : guiBlocks) {
            entries.add(new BlockEntry(
                    info.blockPos(), info.blockHit(),
                    info.displayName(), info.hitLocation()));
        }
        for (var info : nonGuiBlocks) {
            entries.add(new BlockEntry(
                    info.blockPos(), info.blockHit(),
                    info.displayName(), info.hitLocation()));
        }
        return entries;
    }

    // ======================================================================
    //  辅助方法
    // ======================================================================

    private static boolean isAltDown() {
        var window = Minecraft.getInstance().getWindow();
        long handle = window.getWindow();
        return org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    private static boolean isShiftDown() {
        var window = Minecraft.getInstance().getWindow();
        long handle = window.getWindow();
        return org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    private static final int GLFW_BUTTON_RIGHT = 1;

    public SelectionHighlight getHighlight() {
        return highlight;
    }

    // ======================================================================
    //  容器目标检测——判断方块/实体是否会触发容器 GUI 打开
    // ======================================================================

    /** 判断方块位置是否有 MenuProvider（标准容器或模组机器）。
     *  兜底检测 use() 覆写——某些模组（如 Mekanism）在 Block.use() 中直接开 GUI。 */
    private static boolean isContainerBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getMenuProvider(level, pos) != null) return true;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MenuProvider) return true;
        return BoxTargetCollector.hasUseOverride(state.getBlock());
    }

    /** 判断实体是否能打开容器/交易/物品栏等 GUI 界面。 */
    private static boolean isContainerEntity(Entity entity) {
        return entity instanceof MenuProvider
                || entity instanceof AbstractVillager
                || entity instanceof AbstractHorse
                || entity instanceof ContainerEntity;
    }
}
