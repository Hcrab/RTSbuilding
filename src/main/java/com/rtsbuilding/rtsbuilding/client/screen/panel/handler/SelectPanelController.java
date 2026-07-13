package com.rtsbuilding.rtsbuilding.client.screen.panel.handler;

import com.rtsbuilding.rtsbuilding.client.render.pass.BoxSelector;
import com.rtsbuilding.rtsbuilding.client.screen.event.model.EventResult;
import com.rtsbuilding.rtsbuilding.client.screen.panel.select.*;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 选择面板控制器——管理 {@link SelectPanel} 的创建、验证和生命周期。
 *
 * <p>将面板的创建逻辑、条目验证和清理操作从 {@link EntityInteractionHandler} 中解耦，
 * 使得交互调度逻辑更专注于业务决策，面板的 UI 生命周期由本控制器统一管理。</p>
 */
public final class SelectPanelController {

    private final SelectionHighlight highlight;

    /** 当前激活的选择面板（null 表示未弹出） */
    @Nullable
    private SelectPanel selectPanel;

    public SelectPanelController(SelectionHighlight highlight) {
        this.highlight = highlight;
    }

    // ======================== 创建与显示 ========================

    /**
     * 创建并显示选择面板。
     *
     * @param entries   可选项列表
     * @param rayOrigin 射线起点（交互发包用）
     * @param rayDir    射线方向
     * @param sel       框选器（面板关闭时自动重置）
     * @param screen    所属 BuilderScreen
     * @param mouseX    鼠标 X（面板定位）
     * @param mouseY    鼠标 Y
     * @return {@link EventResult#CONSUMED}
     */
    public EventResult show(List<SelectableEntry> entries, Vec3 rayOrigin, Vec3 rayDir,
                             BoxSelector sel, BuilderScreen screen, int mouseX, int mouseY) {
        close(); // 关闭旧面板
        selectPanel = new SelectPanel(entries, highlight, rayOrigin, rayDir);
        selectPanel.init(screen);
        screen.getFloatingWindowLayer().frontToBackWindows().add(selectPanel);

        int popupW = selectPanel.getDefaultWidth();
        int popupH = selectPanel.getDefaultHeight();
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int px = Math.max(0, Math.min(mouseX + 8, screenW - popupW));
        int py = Math.max(0, Math.min(mouseY - popupH / 2, screenH - popupH));
        selectPanel.setBounds(px, py, popupW, popupH);
        selectPanel.setOpen(true);
        return EventResult.CONSUMED;
    }

    // ======================== 生命周期校验 ========================

    /**
     * 校验当前面板条目是否仍有效。
     * <p>由 {@link BuilderScreen} 每帧调用。当框选内实体死亡或离开时，
     * 调用 {@link SelectPanel#updateEntries} 增量更新，无需重建面板。</p>
     *
     * @param screen           当前 BuilderScreen
     * @param currentEntities  框选区内当前有效的实体列表
     * @param sel              框选器（用于判断框选状态）
     */
    public void validate(BuilderScreen screen, List<Entity> currentEntities, BoxTargetCollector.BoxSelectorCache sel) {
        if (selectPanel == null || !selectPanel.isOpen()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { close(); return; }

        // 框选已取消或状态变了 → 关闭面板
        if (sel == null || sel.minCorner() == null || sel.maxCorner() == null) {
            close();
            return;
        }

        // 过滤：只保留仍有效的条目
        List<SelectableEntry> oldEntries = selectPanel.getEntries();
        List<SelectableEntry> newEntries = new ArrayList<>();
        for (SelectableEntry entry : oldEntries) {
            switch (entry) {
                case EntityEntry ee -> {
                    if (ee.entity() != null && ee.entity().isAlive()
                            && currentEntities.contains(ee.entity())) {
                        newEntries.add(entry);
                    }
                }
                case BlockEntry be -> newEntries.add(entry); // 方块始终有效
            }
        }

        // 数量没变 → 无需更新
        if (newEntries.size() == oldEntries.size()) return;

        // 全部无效 → 关闭面板
        if (newEntries.isEmpty()) {
            close();
            return;
        }

        // 部分无效 → 增量更新
        selectPanel.updateEntries(newEntries);
    }

    // ======================== 查询与关闭 ========================

    /** 选择面板是否打开。 */
    public boolean isOpen() {
        return selectPanel != null && selectPanel.isOpen();
    }

    /** 关闭当前选择面板并清理。 */
    public void close() {
        if (selectPanel != null && selectPanel.isOpen()) {
            selectPanel.setOpen(false);
            removeFromFloatingLayer();
            selectPanel = null;
        }
    }

    // ======================== 内部 ========================

    private void removeFromFloatingLayer() {
        if (selectPanel != null && selectPanel.getScreen() != null) {
            selectPanel.getScreen().getFloatingWindowLayer()
                    .frontToBackWindows().remove(selectPanel);
        }
    }
}
