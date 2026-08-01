package com.rtsbuilding.rtsbuilding.client.presentation.panel.handler;

import com.mojang.logging.LogUtils;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.MouseClickEvent;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.LeftSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction.BlockEntry;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction.EntityEntry;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction.InteractionPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction.SelectableEntry;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.pass.BoxSelector;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.rtsbuilding.rtsbuilding.network.NetworkConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.CONSUMED;
import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.PASS;

public final class EntityInteractionHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final BoxTargetCollector targetCollector;
    @Nullable
    private InteractionPanel interactionPanel;

    public EntityInteractionHandler() {
        this.targetCollector = new BoxTargetCollector();
    }

    public EventResult handleMouseClick(MouseClickEvent event, BuilderScreen screen,
                                         LeftSidebarPanel leftSidebarPanel) {
        if (event.button() != GLFW_BUTTON_RIGHT) return PASS;
        if (!screen.isInteractiveMode()) return PASS;
        if (isAltDown() || isShiftDown()) return PASS;

        // 容器标签面板打开期间不响应新的框选交互
        if (isInteractionPanelOpen()) return PASS;

        if (leftSidebarPanel.isClickButtonSelected() && !leftSidebarPanel.isBindModeActive()) {
            return handleDirectInteract(screen);
        }

        if (!leftSidebarPanel.isClickButtonSelected()) {
            var sel = RtsClientKernel.get().renderPipeline().boxSelector;
            if (sel.getPhase() == BoxSelector.Phase.COMPLETE) {
                return handleBoxSelectInteract(screen, sel, (int) event.x(), (int) event.y());
            }
        }

        return PASS;
    }

    /**
     * 校验容器标签面板：实体失效/移出框选范围时从标签列表移除，全部失效则关闭面板。
     */
    public void validatePanel(BuilderScreen screen) {
        InteractionPanel panel = currentPanel(screen);
        if (panel == null || !panel.isOpen()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { panel.closePanel(); return; }

        var sel = RtsClientKernel.get().renderPipeline().boxSelector;
        if (sel.getPhase() != BoxSelector.Phase.COMPLETE) {
            panel.closePanel();
            return;
        }

        var cache = new BoxTargetCollector.BoxSelectorCache(sel.getMinCorner(), sel.getMaxCorner());
        List<Entity> currentEntities = targetCollector.collectEntities(
                mc.level, cache, mc.getCameraEntity());

        List<SelectableEntry> oldEntries = panel.getEntries();
        List<SelectableEntry> newEntries = new ArrayList<>();
        for (SelectableEntry entry : oldEntries) {
            switch (entry) {
                case EntityEntry ee -> {
                    if (ee.entity() != null && ee.entity().isAlive()
                            && currentEntities.contains(ee.entity())) {
                        newEntries.add(entry);
                    }
                }
                case BlockEntry be -> newEntries.add(entry);
            }
        }

        if (newEntries.size() == oldEntries.size()) return;

        if (newEntries.isEmpty()) {
            panel.closePanel();
            return;
        }

        panel.updateTargets(newEntries);
    }

    public boolean isInteractionPanelOpen() {
        return interactionPanel != null && interactionPanel.isOpen();
    }

    public void closeInteractionPanel() {
        if (interactionPanel != null) interactionPanel.closePanel();
    }

    private EventResult handleDirectInteract(BuilderScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null) return PASS;

        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) return PASS;

        var hit = ray.raycastNearest(mc);

        if (hit.hasEntity() && hit.entityHit() != null) {
            Entity target = hit.entityHit().getEntity();

            int entityId = target.getId();
            Vec3 hitLocation = hit.entityHit().getLocation();
            RtsClientPacketGateway.sendInteractEntityEmptyHand(
                    entityId, hitLocation, null, ray.origin(), ray.direction());
            return CONSUMED;
        }

        if (hit.hasBlock() && hit.blockHit() != null) {
            BlockHitResult blockHit = hit.blockHit();

            RtsClientPacketGateway.sendInteractEntityEmptyHand(
                    NetworkConstants.NO_ENTITY,
                    blockHit.getLocation(), blockHit, ray.origin(), ray.direction());
            return CONSUMED;
        }

        return PASS;
    }

    private EventResult handleBoxSelectInteract(BuilderScreen screen, BoxSelector sel,
                                                  int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return PASS;

        var cache = validateCursorInBox(mc, screen, sel);
        if (cache == null) return PASS;

        List<Entity> entities = targetCollector.collectEntities(
                mc.level, cache, mc.getCameraEntity());
        List<BoxTargetCollector.BlockInfo> guiBlocks = targetCollector.collectGuiBlocks(mc.level, cache);
        List<BoxTargetCollector.BlockInfo> nonGuiBlocks = targetCollector.collectNonGuiBlocks(mc.level, cache);

        LOGGER.info("[SelectInteract] box=[{}~{}] entities={} guiBlocks={} nonGuiInteractiveBlocks={}",
                cache.minCorner(), cache.maxCorner(), entities.size(), guiBlocks.size(), nonGuiBlocks.size());

        var ray = CursorRaycaster.computeCameraCenterRay(mc);
        if (ray == null) return PASS;
        Vec3 rayOrigin = ray.origin();
        Vec3 rayDir = ray.direction();

        boolean hasGuiTargets = !entities.isEmpty() || !guiBlocks.isEmpty();

        if (!hasGuiTargets && !nonGuiBlocks.isEmpty()) {
            for (var info : nonGuiBlocks) {
                RtsClientPacketGateway.sendInteractEntityEmptyHand(
                        NetworkConstants.NO_ENTITY,
                        info.hitLocation(), info.blockHit(), rayOrigin, rayDir);
            }
            sel.reset();
            return CONSUMED;
        }

        List<SelectableEntry> entries = buildEntries(entities, guiBlocks, nonGuiBlocks);
        if (!entries.isEmpty() && showPanel(screen, entries, rayOrigin, rayDir, mouseX, mouseY)) {
            return CONSUMED;
        }

        var cursorHit = Objects.requireNonNull(CursorRaycaster.computeCursorRay(mc, screen))
                .raycastBlock(mc);
        if (cursorHit != null) {
            RtsClientPacketGateway.sendInteractEntityEmptyHand(
                    NetworkConstants.NO_ENTITY,
                    cursorHit.getLocation(), cursorHit, rayOrigin, rayDir);
            sel.reset();
            return CONSUMED;
        }

        return PASS;
    }

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

    private boolean showPanel(BuilderScreen screen, List<SelectableEntry> entries,
                              Vec3 rayOrigin, Vec3 rayDir, int mouseX, int mouseY) {
        interactionPanel = screen.getOrCreateInteractionPanel();
        return interactionPanel.showTargets(entries, rayOrigin, rayDir, mouseX, mouseY);
    }

    @Nullable
    private InteractionPanel currentPanel(BuilderScreen screen) {
        if (interactionPanel == null) return null;
        if (interactionPanel.getScreen() != screen) return null;
        return interactionPanel;
    }
}
