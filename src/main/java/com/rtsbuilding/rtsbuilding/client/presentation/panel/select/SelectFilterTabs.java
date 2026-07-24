package com.rtsbuilding.rtsbuilding.client.presentation.panel.select;

import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.state.HoverStateManager;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;


public final class SelectFilterTabs {

    
    public static final int TAB_BAR_H = 14;

    

    
    private static final ResourceLocation FILTER_BG_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_2.png");
    private static final int FILTER_BG_TEX_W = 32;
    private static final int FILTER_BG_TEX_FILE_H = 48;
    
    private static final int FILTER_BG_STATE_H = 16;
    
    private static final int FILTER_BG_SELECTED_V_OFFSET = 32;
    
    private static final int FILTER_BG_BORDER = 2;

    private static final TextureInfo FILTER_BG_TEX_INFO = new TextureInfo(
            FILTER_BG_TEXTURE, FILTER_BG_TEX_W, FILTER_BG_TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion FILTER_BG_NINE_SLICE = NineSliceRegion.fullTheme(
            FILTER_BG_TEX_INFO, FILTER_BG_STATE_H, FILTER_BG_BORDER);

    

    
    public enum FilterMode {
        ALL("全部"),
        ENTITIES("实体"),
        BLOCKS("方块");

        final String label;

        FilterMode(String label) {
            this.label = label;
        }
    }

    

    private FilterMode filterMode = FilterMode.ALL;

    
    private final HoverStateManager[] hoverStates = initHoverStates();

    private static HoverStateManager[] initHoverStates() {
        HoverStateManager[] arr = new HoverStateManager[FilterMode.values().length];
        for (int i = 0; i < arr.length; i++) arr[i] = new HoverStateManager();
        return arr;
    }

    

    
    public FilterMode getMode() {
        return filterMode;
    }

    
    public void setMode(FilterMode mode) {
        this.filterMode = mode;
    }

    
    public boolean matchesFilter(SelectableEntry entry) {
        return switch (filterMode) {
            case ALL -> true;
            case ENTITIES -> entry instanceof EntityEntry;
            case BLOCKS -> entry instanceof BlockEntry;
        };
    }

    
    public boolean hasMixedTypes(int entityCount, int blockCount) {
        return entityCount > 0 && blockCount > 0;
    }

    
    public int getFilterOffset(int entityCount, int blockCount) {
        return hasMixedTypes(entityCount, blockCount) ? TAB_BAR_H : 0;
    }

    

    
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
            int tabW = labelW + PAD_H * 2;

            boolean hovered = mouseX >= tabX && mouseX < tabX + tabW
                    && mouseY >= tabY && mouseY < tabY + TAB_BAR_H;
            boolean selected = filterMode == modes[i];

            
            float hoverT = hoverStates[i].update(hovered);

            
            final int fTabX = tabX;
            final int fTabW = tabW;
            if (selected) {
                SpriteRenderer.drawNineSlice(g,
                        FILTER_BG_NINE_SLICE.withTheme().withVOffset(FILTER_BG_SELECTED_V_OFFSET),
                        fTabX, tabY, fTabW, TAB_BAR_H);
            } else {
                CrossFadeRenderer.render(g, hoverT,
                        () -> SpriteRenderer.drawNineSlice(g,
                                FILTER_BG_NINE_SLICE.withTheme().withVOffset(0),
                                fTabX, tabY, fTabW, TAB_BAR_H),
                        () -> SpriteRenderer.drawNineSlice(g,
                                FILTER_BG_NINE_SLICE.withTheme().withVOffset(FILTER_BG_STATE_H),
                                fTabX, tabY, fTabW, TAB_BAR_H));
            }

            
            int color = selected || hovered ? activeColor : textColor;
            int textX = fTabX + (fTabW - labelW) / 2;
            int textY = tabY + (TAB_BAR_H - font.lineHeight) / 2;
            TextRenderer.draw(g, label, textX, textY, color);

            tabX += tabW + 2;
        }
    }

    

    
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
            int tabW = font.width(label) + PAD_H * 2;
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

    

    private static final int PAD_H = 6;
}
