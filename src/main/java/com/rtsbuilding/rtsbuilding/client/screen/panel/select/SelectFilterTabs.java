package com.rtsbuilding.rtsbuilding.client.screen.panel.select;

import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 筛选标签管理器——管理实体/方块筛选模式的切换、渲染和点击交互。
 *
 * <p>仅当 {@link SelectPanel} 的条目同时包含实体和方块时激活，显示"全部/实体/方块"三个标签。</p>
 */
public final class SelectFilterTabs {

    /** 筛选标签栏高度 */
    public static final int TAB_BAR_H = 14;

    // ======================== 按钮背景贴图 (button_background.png) ========================

    /** button_background.png：32×48，水平左暗右亮，垂直上正常中悬浮下选中 */
    private static final ResourceLocation BTN_BG_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/select/button_background.png");
    private static final int BTN_BG_TEX_W = 32;
    private static final int BTN_BG_TEX_FILE_H = 48;
    /** 单个状态高度（0-16=正常态，16-32=悬浮态，32-48=选中态） */
    private static final int BTN_BG_STATE_H = 16;
    /** 九宫格边框宽度 */
    private static final int BTN_BG_BORDER = 4;
    private static final TextureInfo BTN_BG_TEX_INFO = new TextureInfo(
            BTN_BG_TEXTURE, BTN_BG_TEX_W, BTN_BG_TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion BTN_BG_NINE_SLICE = NineSliceRegion.fullTheme(
            BTN_BG_TEX_INFO, BTN_BG_STATE_H, BTN_BG_BORDER);

    // ======================== 筛选模式 ========================

    /** 筛选模式——仅当同时存在实体和方块条目时生效 */
    public enum FilterMode {
        ALL("全部"),
        ENTITIES("实体"),
        BLOCKS("方块");

        final String label;

        FilterMode(String label) {
            this.label = label;
        }
    }

    // ======================== 状态 ========================

    private FilterMode filterMode = FilterMode.ALL;

    // ======================== 状态查询 ========================

    /** 获取当前筛选模式 */
    public FilterMode getMode() {
        return filterMode;
    }

    /** 设置筛选模式 */
    public void setMode(FilterMode mode) {
        this.filterMode = mode;
    }

    /** 条目是否匹配当前筛选模式 */
    public boolean matchesFilter(SelectableEntry entry) {
        return switch (filterMode) {
            case ALL -> true;
            case ENTITIES -> entry instanceof EntityEntry;
            case BLOCKS -> entry instanceof BlockEntry;
        };
    }

    /** 是否同时包含实体和方块（需要显示筛选栏） */
    public boolean hasMixedTypes(int entityCount, int blockCount) {
        return entityCount > 0 && blockCount > 0;
    }

    /** 获取筛选栏占据的高度偏移量（无筛选时返回 0） */
    public int getFilterOffset(int entityCount, int blockCount) {
        return hasMixedTypes(entityCount, blockCount) ? TAB_BAR_H : 0;
    }

    // ======================== 渲染 ========================

    /**
     * 渲染筛选标签栏——显示在内容区顶部，点击切换筛选模式。
     * 仅当同时包含实体和方块条目时调用。
     */
    public void render(GuiGraphics g, int mouseX, int mouseY,
                        int cx, int cy, int cw,
                        int entityCount, int blockCount, int totalCount) {
        if (!hasMixedTypes(entityCount, blockCount)) return;

        var font = Minecraft.getInstance().font;
        FilterMode[] modes = FilterMode.values();
        int[] counts = {totalCount, entityCount, blockCount};

        int tabX = cx + PAD_H;
        int tabY = cy;
        int textColor = ThemeManager.getTextColor();
        int activeColor = ThemeManager.getHoverTextColor();

        for (int i = 0; i < modes.length; i++) {
            String label = modes[i].label + " (" + counts[i] + ")";
            int labelW = font.width(label);
            int tabW = labelW + 6; // 左右各 3px 内边距

            boolean hovered = mouseX >= tabX && mouseX < tabX + tabW
                    && mouseY >= tabY && mouseY < tabY + TAB_BAR_H;
            boolean selected = filterMode == modes[i];

            // 使用 button_background.png 九宫格贴图作为标签背景（三态）
            int vOffset = selected ? BTN_BG_STATE_H * 2
                    : hovered ? BTN_BG_STATE_H : 0;
            SpriteRenderer.drawNineSlice(g,
                    BTN_BG_NINE_SLICE.withTheme().withVOffset(vOffset),
                    tabX, tabY, tabW, TAB_BAR_H);

            int color = selected ? activeColor : textColor;
            int textX = tabX + 3;
            int textY = tabY + (TAB_BAR_H - font.lineHeight) / 2;
            TextRenderer.draw(g, label, textX, textY, color);

            tabX += tabW + 2; // 标签间距 2px
        }
    }

    // ======================== 点击处理 ========================

    /**
     * 处理筛选标签点击。
     *
     * @return 是否消费了点击事件
     */
    public boolean handleClick(double mouseX, double mouseY,
                                int cx, int cy,
                                int entityCount, int blockCount, int totalCount) {
        if (!hasMixedTypes(entityCount, blockCount)) return false;

        var font = Minecraft.getInstance().font;
        FilterMode[] modes = FilterMode.values();
        int[] counts = {totalCount, entityCount, blockCount};

        int tabX = cx + PAD_H;

        for (int i = 0; i < modes.length; i++) {
            String label = modes[i].label + " (" + counts[i] + ")";
            int tabW = font.width(label) + 10;
            boolean hit = mouseX >= tabX && mouseX < tabX + tabW
                    && mouseY >= cy && mouseY < cy + TAB_BAR_H;
            if (hit) {
                filterMode = modes[i];
                return true;
            }
            tabX += tabW + 2;
        }
        return false;
    }

    // ======================== 布局常量（与 SelectPanel 对齐）=======================

    private static final int PAD_H = 6;
}
