package com.rtsbuilding.rtsbuilding.client.presentation.panel.handler;

import com.mojang.logging.LogUtils;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.MouseClickEvent;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.LeftSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.select.BlockEntry;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.select.EntityEntry;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.select.SelectableEntry;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.select.SelectionHighlight;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.pass.BoxSelector;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import com.rtsbuilding.rtsbuilding.network.NetworkConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.CONSUMED;
import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.PASS;

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

    
    
    

    
    public EventResult handleMouseClick(MouseClickEvent event, BuilderScreen screen,
                                         LeftSidebarPanel leftSidebarPanel) {
        if (event.button() != GLFW_BUTTON_RIGHT) return PASS;
        if (!screen.isInteractiveMode()) return PASS;
        if (isAltDown() || isShiftDown()) return PASS;

        
        if (panelController.isOpen()) return PASS;

        
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

    
    public boolean isSelectPanelOpen() {
        return panelController.isOpen();
    }

    
    public void closeSelectPanel() {
        panelController.close();
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
        if (!entries.isEmpty()) {
            return panelController.show(entries, rayOrigin, rayDir, sel, screen, mouseX, mouseY);
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

    public SelectionHighlight getHighlight() {
        return highlight;
    }
}
