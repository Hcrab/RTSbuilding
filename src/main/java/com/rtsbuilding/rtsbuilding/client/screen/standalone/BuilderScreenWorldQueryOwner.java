package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingWorldInput;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2IconResolver;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.common.registry.ForgeRegistries;


import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * BuilderScreen 的WorldQueryOwner职责所有者。
 *
 * <p>它只处理这一类完整职责，不拥有 Screen 生命周期，也不复制面板状态。</p>
 */
final class BuilderScreenWorldQueryOwner {
    private final BuilderScreen screen;

    BuilderScreenWorldQueryOwner(BuilderScreen screen) {
        this.screen = screen;
    }

    boolean tryUseMainHandItemInAir() {
            if (!screen.canUseMainHandItemInAir()) {
                return false;
            }
            InteractionTypes.InteractionTarget target = screen.cursorPicker.pickItemAirInteractionTarget();
            if (target == null || target.blockHit() == null) {
                return false;
            }
            screen.shapeController.clearShapeBuildSession();
            screen.controller.useItemInAirWithToolSlot(
                    target.blockHit(),
                    screen.getSelectedToolSlot(),
                    target.rayOrigin(),
                    target.rayDir());
            return true;
        }

    boolean handleRangeCullingSelectionClick(double mouseX, double mouseY, int button) {
            if (button != 0 || !screen.cullingManager.isManagementMode() || !screen.isWorldArea(mouseX, mouseY)) {
                return false;
            }
            return screen.handleRangeCullingWorldAction(mouseX, mouseY);
        }

    boolean handleRangeCullingWorldAction(double mouseX, double mouseY) {
            if (!screen.cullingManager.isManagementMode() || !screen.isWorldArea(mouseX, mouseY)) {
                return false;
            }
            return RtsCullingWorldInput.handleWorldAction(screen.cullingManager, screen.cursorPicker);
        }

    void blurSearchFocus() {
            boolean blurred = false;
            if (screen.searchBox != null && screen.searchBox.isFocused()) {
                screen.searchBox.setFocused(false);
                blurred = true;
            }
            if (screen.craftSearchBox != null && screen.craftSearchBox.isFocused()) {
                screen.craftSearchBox.setFocused(false);
                blurred = true;
            }
            if (blurred) {
            }
        }

    void focusStorageSearchBox() {
            if (screen.craftSearchBox != null && screen.craftSearchBox.isFocused()) {
                screen.craftSearchBox.setFocused(false);
            }
            if (screen.searchBox != null) {
                screen.searchBox.setFocused(true);
            }
        }

    void focusCraftSearchBox() {
            if (screen.searchBox != null && screen.searchBox.isFocused()) {
                screen.searchBox.setFocused(false);
            }
            if (screen.craftSearchBox != null) {
                screen.craftSearchBox.setFocused(true);
            }
        }

    boolean isWorldArea(double mouseX, double mouseY) {
            return mouseY > TOP_H && !screen.bottomPanel.isInsideBottomPanel(mouseX, mouseY);
        }

    int getBottomY() {
            return screen.bottomPanel.getBottomY();
        }

    int getFloatingPanelAvailableHeight(int panelY) {
            return Math.max(0, screen.getBottomY() - panelY - 6);
        }

    boolean isInsideBottomPanel(double mouseX, double mouseY) {
            return screen.bottomPanel.isInsideBottomPanel(mouseX, mouseY);
        }

    boolean isSearchFocused() {
            return (screen.searchBox != null && screen.searchBox.isFocused())
                    || (screen.craftSearchBox != null && screen.craftSearchBox.isFocused())
                    || screen.aiChatPanel.isInputFocused();
        }

    int getSelectedToolSlot() {
            if (screen.getMinecraft() == null || screen.getMinecraft().player == null) {
                return 0;
            }
            return MathHelper.clamp(screen.getMinecraft().player.inventory.currentItem, 0, 8);
        }

    ItemStack getSelectedToolStack() {
            if (screen.getMinecraft() == null || screen.getMinecraft().player == null) {
                return ItemStack.EMPTY;
            }
            return screen.getMinecraft().player.inventory.getStackInSlot(screen.getSelectedToolSlot());
        }

    String resolveGuiBindingItemId(RayTraceResult hit) {
            if (hit == null || screen.getMinecraft() == null || screen.getMinecraft().world == null) {
                return "";
            }
            BlockPos pos = hit.getBlockPos();
            if (!screen.getMinecraft().world.isBlockLoaded(pos)) {
                return "";
            }
            IBlockState state = screen.getMinecraft().world.getBlockState(pos);
            ItemStack preview = state.getBlock().getPickBlock(
                    hit, screen.getMinecraft().world, pos, screen.getMinecraft().player);
            if (preview.isEmpty()) {
                preview = new ItemStack(Item.getItemFromBlock(state.getBlock()));
            }
            if (preview.isEmpty() || preview.getItem() == Items.AIR) {
                return RtsAe2IconResolver.resolveGuiBindingIconItemId(screen.getMinecraft().world, pos, hit.sideHit, "");
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(preview.getItem());
            return id == null ? "" : id.toString();
        }

    boolean canUseToolSlotShapeSource() {
            if (screen.controller.hasSelectedItem() || screen.controller.hasSelectedFluid() || screen.controller.isEmptyHandSelected()) {
                return false;
            }
            ItemStack stack = screen.getSelectedToolStack();
            return !stack.isEmpty() && stack.getItem() instanceof ItemBlock;
        }

    boolean tryAssignQuickSlotFromToolSelection(int pinIndex) {
            if (screen.getMinecraft() == null || screen.getMinecraft().player == null) {
                return false;
            }
            if (screen.controller.isEmptyHandSelected()) {
                return false;
            }
            int slot = screen.bottomPanel.hoveredToolSlot >= 0 ? screen.bottomPanel.hoveredToolSlot : screen.getSelectedToolSlot();
            slot = MathHelper.clamp(slot, 0, 8);
            ItemStack stack = screen.getMinecraft().player.inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                return false;
            }
            screen.controller.assignQuickSlotFromToolItem(pinIndex, stack);
            return true;
        }

    void setSelectedToolSlot(int slot) {
            if (screen.getMinecraft() == null || screen.getMinecraft().player == null) {
                return;
            }
            screen.getMinecraft().player.inventory.currentItem = MathHelper.clamp(slot, 0, 8);
        }

    boolean hasMainHandItem() {
            return screen.getMinecraft() != null
                    && screen.getMinecraft().player != null
                    && !screen.getMinecraft().player.getHeldItemMainhand().isEmpty();
        }

    boolean isAltDownForInput() {
            return screen.isAltDown();
        }

}
