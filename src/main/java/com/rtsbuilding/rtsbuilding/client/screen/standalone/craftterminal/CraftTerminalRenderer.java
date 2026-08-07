package com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal;

import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.RtsTextureRenderer;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftTerminalLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.CraftTerminalStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * 26.1 Craft Terminal 的提取阶段渲染适配器。
 *
 * <p>UiKit 定义皮肤切片和格子坐标；本类只把这些只读数据提交到终端自己的 GUI 绘制路径。
 * 它不管理鼠标、储存状态或网络请求，更不会结束或 flush Minecraft 的共享渲染缓冲。</p>
 */
public final class CraftTerminalRenderer {
    private static final Identifier TERMINAL_TEXTURE = Identifier.fromNamespaceAndPath(
            "rtsbuilding", "textures/gui/ui/terminal.png");
    private static final int TEXTURE_SIZE = 512;

    private CraftTerminalRenderer() {
    }

    /** 渲染终端皮肤、滚动块与当前服务端页面快照中的物品格。 */
    public static void render(
            GuiGraphicsExtractor graphics,
            Font font,
            int left,
            int top,
            CraftTerminalLayout.Geometry layout,
            CraftTerminalScrollState scrollState,
            int totalEntries) {
        renderContributorSkin(
                graphics,
                left,
                top,
                layout,
                scrollState.fraction(totalEntries, layout.rows));

        int cellCount = layout.rows * CraftTerminalLayout.COLUMNS;
        for (int cell = 0; cell < cellCount; cell++) {
            StorageEntry entry = scrollState.entryAtVisibleCell(cell);
            if (entry == null || entry.stack().isEmpty()) {
                continue;
            }
            UiRect bounds = layout.storageCell(cell);
            int x = left + (int) bounds.getX() + 1;
            int y = top + (int) bounds.getY() + 1;
            graphics.item(entry.stack(), x, y);
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

    /** 在同一份终端皮肤切片上追加轻量悬停反馈。 */
    public static void renderActionHover(
            GuiGraphicsExtractor graphics,
            int left,
            int top,
            UiRect bounds,
            boolean hovered) {
        if (bounds == null || !hovered) {
            return;
        }
        graphics.fill(
                left + (int) bounds.getX(),
                top + (int) bounds.getY(),
                left + (int) bounds.getX() + (int) bounds.getWidth(),
                top + (int) bounds.getY() + (int) bounds.getHeight(),
                CraftTerminalStyle.BUTTON_HOVER_OVERLAY.toArgb());
    }

    private static void renderContributorSkin(
            GuiGraphicsExtractor graphics,
            int left,
            int top,
            CraftTerminalLayout.Geometry layout,
            double scrollFraction) {
        for (CraftTerminalLayout.TextureSlice slice : layout.skinSlices()) {
            renderSlice(graphics, left, top, slice);
        }
        renderSlice(graphics, left, top, layout.scrollbarHandleSlice(scrollFraction));
    }

    private static void renderSlice(
            GuiGraphicsExtractor graphics,
            int left,
            int top,
            CraftTerminalLayout.TextureSlice slice) {
        UiRect source = slice.source;
        UiRect target = slice.target;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TERMINAL_TEXTURE,
                left + (int) target.getX(),
                top + (int) target.getY(),
                (int) source.getX(),
                (int) source.getY(),
                (int) source.getWidth(),
                (int) source.getHeight(),
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                RtsTextureRenderer.NO_TINT);
    }
}
