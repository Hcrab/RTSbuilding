package com.rtsbuilding.rtsbuilding.client.screen.overlay;

import com.rtsbuilding.rtsbuilding.client.screen.panel.BottomPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.uikit.layout.LeftDockedTooltipLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.RtsMainlineLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;
import cpw.mods.fml.client.config.GuiUtils;

import java.util.Collections;

/**
 * 左侧固定 Tooltip 的 Minecraft 绘制适配器。
 *
 * <p>本类负责把 Kit 锚点翻译为原版物品/文本 Tooltip 与无阴影补充说明；不选择悬停对象、
 * 不修改底栏状态、不控制原生鼠标，也不发送任何业务动作。拆出后主 Screen 只编排“显示哪条
 * 提示”，锚点和平台绘制不再散落在三千行生命周期类中。</p>
 */
public final class LeftDockedTooltipRenderer {
    private final BuilderScreen screen;
    private final BottomPanel bottomPanel;

    public LeftDockedTooltipRenderer(BuilderScreen screen, BottomPanel bottomPanel) {
        if (screen == null || bottomPanel == null) {
            throw new IllegalArgumentException("screen and bottom panel must not be null");
        }
        this.screen = screen;
        this.bottomPanel = bottomPanel;
    }

    public void render(LegacyGuiGraphics graphics, ItemStack stack) {
        LeftDockedTooltipLayout.Geometry geometry = geometry();
        graphics.renderTooltip(stack, geometry.anchorX(), geometry.anchorY());
    }

    public void render(LegacyGuiGraphics graphics, IChatComponent text) {
        if (text == null) {
            return;
        }
        LeftDockedTooltipLayout.Geometry geometry = geometry();
        graphics.renderTooltipText(
                text.getFormattedText(), geometry.anchorX(), geometry.anchorY());
    }

    public void renderDetail(LegacyGuiGraphics graphics, String detail, UiColor color) {
        if (detail == null || detail.trim().isEmpty() || color == null) {
            return;
        }
        LeftDockedTooltipLayout.Geometry geometry = geometry();
        graphics.drawString(this.screen.font(), detail,
                geometry.detailX(), geometry.detailY(),
                color.toArgb(), false);
    }

    private LeftDockedTooltipLayout.Geometry geometry() {
        return LeftDockedTooltipLayout.resolve(
                this.bottomPanel.resolveBottomPanelLayout().panelX(),
                this.bottomPanel.getBottomY(),
                RtsMainlineLayout.TOP_H);
    }
}
