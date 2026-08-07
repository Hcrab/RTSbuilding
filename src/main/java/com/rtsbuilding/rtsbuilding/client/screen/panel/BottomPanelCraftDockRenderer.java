package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiToolSlot;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationRegistry;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelCraftDockLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelCraftDockStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 26.1 快捷制作环的生产绘制适配器。
 *
 * <p>它只渲染 Core 中的绑定状态与控制器给出的真实预览。打开终端、挂接 GUI 与清除
 * 绑定仍从 BottomPanel 的 Core 动作进入，不会在绘制中修改会话或远程访问权限。</p>
 */
public final class BottomPanelCraftDockRenderer {
    private static final UiControlAnimationRegistry<String> ANIMATIONS =
            new UiControlAnimationRegistry<>(SystemUiClock.INSTANCE, 10);

    private BottomPanelCraftDockRenderer() {
    }

    /** 绘制中心工艺入口与环形绑定槽，并返回悬停绑定槽索引。 */
    public static int render(
            GuiGraphicsExtractor graphics,
            Font font,
            List<BottomBarUiToolSlot> bindings,
            ClientRtsController controller,
            BottomPanelCraftDockLayout layout,
            int mouseX,
            int mouseY) {
        double craftHover = hover(
                "craft", layout.craftButton.contains(mouseX, mouseY), false);
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(graphics, font);
        UiCompactFrameRenderer.frame(
                canvas,
                new UiRect(layout.craftButton.x, layout.craftButton.y,
                        layout.craftButton.width, layout.craftButton.height),
                BottomPanelCraftDockStyle.craftBackground(craftHover),
                BottomPanelCraftDockStyle.CRAFT_BORDER_LIGHT,
                BottomPanelCraftDockStyle.CRAFT_BORDER_DARK);
        drawCenteredNoShadow(graphics, font, "C",
                layout.craftButton.x, layout.craftButton.y,
                layout.craftButton.width, layout.craftButton.height,
                argb(BottomPanelCraftDockStyle.TEXT));

        int hoveredSlot = layout.slotIndexAt(mouseX, mouseY);
        for (int slot = 0; slot < layout.bindingCount; slot++) {
            BottomBarUiToolSlot binding = bindingAt(bindings, slot);
            boolean pending = binding != null && binding.pending;
            boolean bound = binding != null && binding.bound;
            double slotHover = hover(
                    "binding." + slot, hoveredSlot == slot, pending);
            int slotX = layout.slotX(slot);
            int slotY = layout.slotY(slot);
            UiCompactFrameRenderer.frame(
                    canvas,
                    new UiRect(slotX, slotY,
                            BottomPanelCraftDockLayout.BINDING_SLOT_SIZE,
                            BottomPanelCraftDockLayout.BINDING_SLOT_SIZE),
                    BottomPanelCraftDockStyle.slotBackground(
                            pending, bound, slotHover),
                    BottomPanelCraftDockStyle.SLOT_BORDER_LIGHT,
                    BottomPanelCraftDockStyle.SLOT_BORDER_DARK);

            ItemStack preview = controller.getGuiBindingPreview(slot);
            if (bound && !pending && !preview.isEmpty()) {
                graphics.item(preview, slotX + 1, slotY + 1);
                continue;
            }
            String label = !bound || pending ? "+" : Integer.toString(slot + 1);
            drawCenteredNoShadow(graphics, font, label,
                    slotX, slotY,
                    BottomPanelCraftDockLayout.BINDING_SLOT_SIZE,
                    BottomPanelCraftDockLayout.BINDING_SLOT_SIZE,
                    argb(BottomPanelCraftDockStyle.TEXT));
        }
        return hoveredSlot;
    }

    private static double hover(String id, boolean hovered, boolean selected) {
        UiControlState state = new UiControlState(
                true, selected, false, false, "")
                .withInteraction(hovered, false, false);
        return ANIMATIONS.update(
                id, state, Config.isUiAnimationsEnabled()).hover();
    }

    private static BottomBarUiToolSlot bindingAt(
            List<BottomBarUiToolSlot> bindings, int sourceIndex) {
        for (BottomBarUiToolSlot binding : bindings) {
            if (binding.kind == BottomBarUiToolSlot.Kind.GUI_BINDING
                    && binding.sourceIndex == sourceIndex) {
                return binding;
            }
        }
        return null;
    }

    private static void drawCenteredNoShadow(
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            int x,
            int y,
            int width,
            int height,
            int color) {
        int textX = x + (width - font.width(text)) / 2;
        int textY = y + Math.max(0, (height - font.lineHeight) / 2);
        graphics.text(font, text, textX, textY, color, false);
    }

    private static int argb(UiColor color) {
        return color.toArgb();
    }
}
