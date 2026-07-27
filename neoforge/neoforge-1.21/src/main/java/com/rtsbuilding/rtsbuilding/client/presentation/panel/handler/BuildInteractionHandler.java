package com.rtsbuilding.rtsbuilding.client.presentation.panel.handler;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.mining.MiningModule;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.client.input.layer.CameraInputLayer;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.MouseClickEvent;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.MouseReleaseEvent;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.background.ScreenBackgroundPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.DownSidebarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.LeftSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.ModeSwitcher;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;
import com.rtsbuilding.rtsbuilding.network.NetworkConstants;

import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.CONSUMED;
import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.PASS;
import com.rtsbuilding.rtsbuilding.network.NetworkConstants;

public final class BuildInteractionHandler {

    private final RtsClientKernel kernel;
    private final CameraInputLayer cameraInputLayer;

    
    private boolean miningActive;
    private int miningMouseButton = -1;

    public BuildInteractionHandler(RtsClientKernel kernel, CameraInputLayer cameraInputLayer) {
        this.kernel = kernel;
        this.cameraInputLayer = cameraInputLayer;
    }

    
    public EventResult handleMouseClick(MouseClickEvent event, BuilderScreen screen,
                                         LeftSidebarPanel leftSidebarPanel, TopBarPanel topBarPanel) {
        int button = event.button();

        
        if (!isInBuildOrInteractiveMode(topBarPanel)) return PASS;
        if (screen.isMouseOverRtsPanelApi(event.x(), event.y())) return PASS;
        if (!isWorldArea(event.x(), event.y(), screen)) return PASS;
        if (leftSidebarPanel != null && leftSidebarPanel.isClickButtonSelected()
                && screen.isInteractiveMode()) return PASS;

        
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !isAltDown()) {
            return handleLeftClick(screen) ? CONSUMED : PASS;
        }

        
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !isAltDown() && !isShiftDown()) {
            
            return CONSUMED;
        }

        return PASS;
    }

    
    public EventResult handleMouseRelease(MouseReleaseEvent event, BuilderScreen screen,
                                           TopBarPanel topBarPanel) {
        int button = event.button();

        
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.miningActive) {
            stopMining();
            return CONSUMED;
        }

        
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !isAltDown() && !isShiftDown()
                && !screen.isMouseOverRtsPanelApi(event.x(), event.y())
                && isWorldArea(event.x(), event.y(), screen)
                && isInBuildOrInteractiveMode(topBarPanel)) {
            
            if (!cameraInputLayer.wasDragged(button)) {
                return runPrimaryActionAt(screen);
            }
        }

        
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                && !screen.isMouseOverRtsPanelApi(event.x(), event.y())
                && isWorldArea(event.x(), event.y(), screen)) {
            if (!cameraInputLayer.wasDragged(button)) {
                tryPickHoveredBlockForPlacement(screen);
                return CONSUMED;
            }
        }

        return PASS;
    }

    
    private boolean handleLeftClick(BuilderScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        BuildingModule buildingModule = kernel.module(BuildingModule.class);
        if (buildingModule == null) return false;
        if (buildingModule.getMode() != BuilderMode.BUILD) return false;

        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) return false;

        BlockHitResult hit = ray.raycastBlock(mc);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;

        MiningModule miningModule = kernel.module(MiningModule.class);
        if (miningModule == null) return false;

        String toolItemId = buildingModule.getSelectedItemId();
        ItemStack toolPreview = buildingModule.getSelectedItemPreview();
        int toolSlot = mc.player != null ? mc.player.getInventory().selected : 0;

        miningModule.startMining(hit.getBlockPos(), hit.getDirection().get3DDataValue(),
                toolSlot, toolItemId, toolPreview, false, false);
        this.miningActive = true;
        this.miningMouseButton = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        return true;
    }

    private void stopMining() {
        MiningModule miningModule = kernel.module(MiningModule.class);
        if (miningModule != null) {
            int toolSlot = Minecraft.getInstance().player != null
                    ? Minecraft.getInstance().player.getInventory().selected : 0;
            miningModule.abortMining(toolSlot);
        }
        this.miningActive = false;
        this.miningMouseButton = -1;
    }

    
    private EventResult runPrimaryActionAt(BuilderScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return PASS;

        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) return PASS;

        BlockHitResult hit = ray.raycastBlock(mc);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return PASS;

        BuildingModule buildingModule = kernel.module(BuildingModule.class);
        if (buildingModule == null) return PASS;

        boolean shiftDown = isShiftDown();
        boolean isBuildMode = buildingModule.getMode() == BuilderMode.BUILD;

        
        if (buildingModule.hasSelectedFluid()) {
            if (!isBuildMode) return PASS;
            buildingModule.placeFluid(hit, shiftDown, ray.origin(), ray.direction());
            return CONSUMED;
        }

        
        if (buildingModule.hasSelectedItem()) {
            if (!isBuildMode) return PASS;
            buildingModule.placeSelected(hit, shiftDown, ray.origin(), ray.direction());
            return CONSUMED;
        }

        
        if (isBuildMode) return PASS;

        
        if (buildingModule.isEmptyHandSelected()) {
            RtsClientPacketGateway.sendInteractEntityEmptyHand(
                    NetworkConstants.NO_ENTITY,
                    hit.getLocation(), hit, ray.origin(), ray.direction());
            return CONSUMED;
        }

        
        if (mc.player != null) {
            int slot = mc.player.getInventory().selected;
            ItemStack held = mc.player.getInventory().getItem(slot);
            if (!held.isEmpty()) {
                RtsClientPacketGateway.sendInteractEntityEmptyHand(
                        NetworkConstants.NO_ENTITY,
                        hit.getLocation(), hit, ray.origin(), ray.direction());
                return CONSUMED;
            }
        }

        return PASS;
    }

    
    private boolean tryPickHoveredBlockForPlacement(BuilderScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) return false;

        BlockHitResult hit = ray.raycastBlock(mc);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;

        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) return false;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null) return false;

        ItemStack preview = new ItemStack(item);
        if (preview.isEmpty()) return false;

        
        if (mc.player != null) {
            var inventory = mc.player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack candidate = inventory.getItem(i);
                if (!candidate.isEmpty() && candidate.getItem() == preview.getItem()) {
                    inventory.selected = i;
                    
                    BuildingModule buildingModule = kernel.module(BuildingModule.class);
                    if (buildingModule != null) {
                        buildingModule.clearSelection();
                    }
                    return true;
                }
            }
        }

        
        BuildingModule buildingModule = kernel.module(BuildingModule.class);
        if (buildingModule != null) {
            buildingModule.selectItem(itemId.toString(), preview.getHoverName().getString(), preview);
        }
        return true;
    }

    
    private static boolean isWorldArea(double mouseX, double mouseY, BuilderScreen screen) {
        int leftW = screen.getLeftSidebarWidth();
        if (mouseX < leftW) return false;

        int rightW = screen.getRightSidebarWidth();
        if (rightW > 0 && mouseX >= screen.width - rightW) return false;

        int downH = screen.getDownSidebarHeight();
        if (downH > 0 && mouseY >= screen.height - downH) return false;

        if (mouseY < ScreenBackgroundPanel.BACKGROUND_TOP_Y) return false;

        return true;
    }

    private static boolean isInBuildOrInteractiveMode(TopBarPanel topBarPanel) {
        if (topBarPanel == null) return false;
        ModeSwitcher.Mode mode = topBarPanel.getCurrentMode();
        return mode == ModeSwitcher.Mode.BUILD || mode == ModeSwitcher.Mode.INTERACTIVE;
    }

    private static boolean isAltDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    private static boolean isShiftDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }
}
