package com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal;

import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftTerminalLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.CraftTerminalStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Minecraft 侧的合成终端内容适配器。
 *
 * <p>共享 UiKit 负责全部 chrome 和几何；本类只补上 Minecraft 才能绘制的物品与数量。
 * 它不拥有按钮坐标、不处理输入，也不发送网络包。</p>
 */
public final class CraftTerminalRenderer {
    private static final ResourceLocation TERMINAL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "rtsbuilding", "textures/gui/ui/terminal.png");
    private static final int TEXTURE_SIZE = 512;

    private CraftTerminalRenderer() {
    }

    public static void render(
            GuiGraphics graphics,
            Font font,
            int left,
            int top,
            CraftTerminalLayout.Geometry layout,
            CraftTerminalScrollState scrollState,
            int totalEntries,
            boolean searchFocused,
            boolean searchHasText,
            CraftTerminalSearchMode searchMode,
            boolean searchPinned,
            int sortMode,
            boolean ascending,
            int mouseX,
            int mouseY) {
        renderContributorSkin(
                graphics,
                left,
                top,
                layout,
                scrollState.fraction(totalEntries, layout.rows));

        int cellCount = layout.rows * CraftTerminalLayout.COLUMNS;
        for (int cell = 0; cell < cellCount; cell++) {
            StorageEntry entry = scrollState.entryAtVisibleCell(cell);
            if (entry == null) {
                continue;
            }
            UiRect bounds = layout.storageCell(cell);
            int x = left + (int) bounds.getX() + 1;
            int y = top + (int) bounds.getY() + 1;
            graphics.renderItem(entry.stack(), x, y);
            RtsClientUiUtil.drawSlotCountOverlay(
                    graphics,
                    font,
                    x - 1,
                    y - 1,
                    CraftTerminalLayout.SLOT_SIZE,
                    RtsClientUiUtil.compactCount(entry.count()),
                    CraftTerminalStyle.COUNT_TEXT.toArgb());
        }
    }

    /** 按贡献者原图像素 1:1 贴合终端主体，禁止运行时拉伸。 */
    private static void renderContributorSkin(
            GuiGraphics graphics,
            int left,
            int top,
            CraftTerminalLayout.Geometry layout,
            double scrollFraction) {
        for (CraftTerminalLayout.TextureSlice slice : layout.skinSlices()) {
            renderSlice(graphics, left, top, slice);
        }
        renderSlice(graphics, left, top, layout.scrollbarHandleSlice(scrollFraction));
    }

    /** 统一执行原尺寸切片，防止静态骨架和动态滑块走出两套采样规则。 */
    private static void renderSlice(
            GuiGraphics graphics,
            int left,
            int top,
            CraftTerminalLayout.TextureSlice slice) {
        UiRect source = slice.source;
        UiRect target = slice.target;
        graphics.blit(
                TERMINAL_TEXTURE,
                left + (int) target.getX(),
                top + (int) target.getY(),
                (int) source.getX(),
                (int) source.getY(),
                (int) source.getWidth(),
                (int) source.getHeight(),
                TEXTURE_SIZE,
                TEXTURE_SIZE);
    }
}
