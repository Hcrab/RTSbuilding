package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiAction;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelCraftLayout;
import net.minecraft.client.gui.GuiTextField;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.CRAFT_PANEL_W;

/**
 * 工艺侧栏文本框、过滤按钮、条目与滚动的生产输入适配器。
 *
 * <p>本类不绘制工艺列表，也不拥有搜索或滚动状态；它只复用 Kit 几何更新
 * BottomPanel 的既有状态，并把动作送入统一 Core 出口。</p>
 */
final class BottomPanelCraftInput {
    private final BottomPanel panel;

    BottomPanelCraftInput(BottomPanel panel) {
        this.panel = panel;
    }

    boolean leftPressed(
            double mouseX,
            double mouseY,
            BottomPanelLayoutTypes.BottomPanelLayout panelLayout) {
        BottomPanelCraftLayout craft = resolve(panelLayout);
        panel.craftScroll = craft.scroll;
        if (!craft.panel.contains(mouseX, mouseY)) {
            return false;
        }
        GuiTextField searchBox = panel.screen.getSearchBox();
        if (searchBox != null && searchBox.isFocused()) {
            searchBox.setFocused(false);
        }
        GuiTextField craftSearchBox = panel.screen.getCraftSearchBox();
        if (craftSearchBox != null && contains(craftSearchBox, mouseX, mouseY)) {
            craftSearchBox.mouseClicked((int) mouseX, (int) mouseY, 0);
            panel.screen.focusCraftSearchBox();
            return true;
        }
        if (craft.apply.contains(mouseX, mouseY)) {
            panel.applyCraftSearchDraft();
            panel.screen.blurSearchFocus();
        } else if (craft.toggle.contains(mouseX, mouseY)) {
            panel.dispatchCore(BottomBarUiAction.simple(
                    BottomBarUiAction.Type.TOGGLE_CRAFT_UNAVAILABLE));
        }
        return true;
    }

    boolean rightPressed(
            double mouseX,
            double mouseY,
            BottomPanelLayoutTypes.BottomPanelLayout panelLayout) {
        BottomPanelCraftLayout craft = resolve(panelLayout);
        panel.craftScroll = craft.scroll;
        int entryIndex = craft.entryIndexAt(mouseX, mouseY);
        if (entryIndex < 0
                || entryIndex >= panel.controller.getCraftableEntries().size()) {
            return craft.panel.contains(mouseX, mouseY);
        }
        CraftableEntry entry =
                panel.controller.getCraftableEntries().get(entryIndex);
        if (entry.craftable()) {
            panel.dispatchCore(BottomBarUiAction.index(
                    BottomBarUiAction.Type.OPEN_CRAFT_QUANTITY, entryIndex));
        }
        return true;
    }

    boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollY,
            BottomPanelLayoutTypes.BottomPanelLayout panelLayout) {
        BottomPanelCraftLayout craft = resolve(panelLayout);
        if (!craft.panel.contains(mouseX, mouseY)) {
            return false;
        }
        panel.craftScroll = craft.scroll;
        int delta = scrollY > 0.0D ? -1 : 1;
        panel.dispatchCore(BottomBarUiAction.delta(
                BottomBarUiAction.Type.SCROLL_CRAFT,
                delta, craft.maxScroll));
        if (delta > 0 && panel.craftScroll >= craft.maxScroll
                && panel.controller.hasMoreCraftables()) {
            panel.controller.requestMoreCraftables();
        }
        return true;
    }

    private BottomPanelCraftLayout resolve(
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        return BottomPanelCraftLayout.resolve(
                layout.craftPanelX(), layout.craftPanelY(),
                CRAFT_PANEL_W, layout.craftPanelH(),
                panel.controller.getCraftableEntries().size(),
                panel.craftScroll);
    }

    private static boolean contains(GuiTextField field, double mouseX, double mouseY) {
        return mouseX >= field.x && mouseX < field.x + field.width
                && mouseY >= field.y && mouseY < field.y + field.height;
    }
}
