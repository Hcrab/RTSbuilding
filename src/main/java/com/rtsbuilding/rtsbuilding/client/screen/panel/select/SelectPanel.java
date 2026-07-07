package com.rtsbuilding.rtsbuilding.client.screen.panel.select;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.render.pass.EntitySelectHighlightPass;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.window.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.state.HoverStateManager;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.TOP_H;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 交互目标选择面板——框选多个可交互实体或方块后，让玩家选择与哪个目标交互。
 *
 * <p>每个菜单项横向排布，渲染实体模型或方块物品图标，下方显示名称，
 * 悬停时对应目标在世界中渲染角支架高亮线框。
 * 面板宽度固定，条目过多时通过横向滚动条滚动查看。</p>
 *
 * <p><b>架构说明：</b>本面板为编排器角色，渲染条目收归 {@link SelectEntryRenderer}，
 * 筛选标签收归 {@link SelectFilterTabs}，自身聚焦布局计算、事件路由和条目生命周期。</p>
 */
public final class SelectPanel extends RtsPanel {

    // ======================== 布局常量 ========================

    /** 面板固定宽度 */
    private static final int PANEL_FIXED_W = 320;
    /** 模型/图标渲染尺寸 */
    private static final int ICON_SIZE = 36;
    /** 每个条目的固定宽度 */
    private static final int ITEM_W = 86;
    /** 内容左右 padding */
    private static final int PAD_H = 6;
    /** 内容上下 padding */
    private static final int PAD_V = 4;
    /** 图标到文字的间距 */
    private static final int ICON_TEXT_GAP = 4;
    /** 滚动条区域高度 */
    private static final int SCROLL_BAR_H = 8;
    /** 滚动条与图标区域的间距 */
    private static final int SCROLL_GAP = 4;
    /** 内容区左右裁剪缩进 */
    private static final int CONTENT_INSET = 4;
    /** 标题栏高度 */
    private static final int TITLE_BAR_H = 20;
    /** 标题栏下方到内容区的间距 */
    private static final int TITLE_BAR_BOTTOM_GAP = 2;
    /** 内容区底部留白（面板下边框内） */
    private static final int CONTENT_BOTTOM_PAD = 8;

    // ======================== 高亮状态 ========================

    private final SelectionHighlight highlight;

    // ======================== 条目系统 ========================

    private List<SelectableEntry> entries;
    private final Vec3 rayOrigin;
    private final Vec3 rayDir;
    private final int contentItemH;
    private final int panelHeight;

    /** 横向滚动条 */
    private final ScrollBar horizontalBar = new ScrollBar()
            .withOrientation(ScrollBar.Orientation.HORIZONTAL);

    /** 每个菜单项独立的悬浮状态管理器 */
    private HoverStateManager[] hoverStates;

    /** 筛选标签管理器 */
    private final SelectFilterTabs filterTabs;

    /** 实体与方块条目计数 */
    private final int entityCount;
    private final int blockCount;

    /**
     * @param entries   可选项列表（内部做防御性拷贝）
     * @param highlight 高亮状态共享对象（写入端），由 {@link BuilderScreen} 统一管理
     * @param rayOrigin 射线起点，用于交互时网络发包
     * @param rayDir    射线方向
     */
    public SelectPanel(List<SelectableEntry> entries, SelectionHighlight highlight,
                       Vec3 rayOrigin, Vec3 rayDir) {
        this.entries = List.copyOf(entries);
        this.highlight = Objects.requireNonNull(highlight, "highlight must not be null");
        this.rayOrigin = rayOrigin;
        this.rayDir = rayDir;
        initHoverStates(this.entries.size());

        // 计算实体与方块数量
        int ec = 0, bc = 0;
        for (SelectableEntry e : this.entries) {
            if (e instanceof EntityEntry) ec++;
            else bc++;
        }
        this.entityCount = ec;
        this.blockCount = bc;

        this.filterTabs = new SelectFilterTabs();

        // 内容区单行高度：图标 + 间距 + 文字
        var font = Minecraft.getInstance().font;
        this.contentItemH = ICON_SIZE + ICON_TEXT_GAP + font.lineHeight;

        // 面板高度：筛选栏 + 标题栏 + 内容区（图标行 + 滚动条）
        int contentH = SelectFilterTabs.TAB_BAR_H + PAD_V + contentItemH
                + SCROLL_GAP + SCROLL_BAR_H + PAD_V;
        this.panelHeight = TITLE_BAR_H + TITLE_BAR_BOTTOM_GAP + contentH + CONTENT_BOTTOM_PAD;

        // RtsPanel 行为配置
        this.closable = true;
        this.draggable = true;
        this.resizable = false;

        bounds.setInitialized(true);
    }

    // ======================== 悬浮状态初始化 ========================

    private void initHoverStates(int count) {
        hoverStates = new HoverStateManager[count];
        for (int i = 0; i < count; i++) {
            hoverStates[i] = new HoverStateManager();
        }
    }

    // ======================== 增量更新 ========================

    /**
     * 增量更新条目列表——保留匹配旧条目的悬停动画状态，其余新建。
     * <p>当框选内实体死亡或离开时调用，无需重建整个面板实例。</p>
     */
    public void updateEntries(List<SelectableEntry> newEntries) {
        List<SelectableEntry> safeCopy = List.copyOf(newEntries);
        HoverStateManager[] newStates = new HoverStateManager[safeCopy.size()];
        for (int i = 0; i < safeCopy.size(); i++) {
            SelectableEntry newEntry = safeCopy.get(i);
            int oldIdx = findEntryByIdentifier(newEntry.identifier());
            newStates[i] = oldIdx >= 0 ? hoverStates[oldIdx] : new HoverStateManager();
        }
        this.entries = safeCopy;
        this.hoverStates = newStates;
    }

    /** 按标识符查找旧条目索引 */
    private int findEntryByIdentifier(Object id) {
        for (int i = 0; i < entries.size(); i++) {
            if (Objects.equals(entries.get(i).identifier(), id)) {
                return i;
            }
        }
        return -1;
    }

    // ======================== 高亮查询 ========================

    /** 返回当前高亮状态对象，供 {@link EntitySelectHighlightPass} 读取 */
    public SelectionHighlight getHighlight() {
        return highlight;
    }

    // ======================== RtsPanel 抽象方法 ========================

    @Override
    protected Component getTitle() {
        return Component.literal("选择交互目标");
    }

    @Override
    public int getDefaultWidth() {
        return PANEL_FIXED_W;
    }

    @Override
    public int getDefaultHeight() {
        return panelHeight;
    }

    @Override
    public int getMinWindowWidth() {
        return PANEL_FIXED_W;
    }

    @Override
    public int getMinWindowHeight() {
        return panelHeight;
    }

    @Override
    public int getMaxWindowWidth() {
        return PANEL_FIXED_W;
    }

    @Override
    public int getMaxWindowHeight() {
        return panelHeight;
    }

    /** 内容区左边裁剪起点（在父类基础上右缩 CONTENT_INSET px） */
    @Override
    protected int contentX() {
        return super.contentX() + CONTENT_INSET;
    }

    /** 内容区宽度（在父类基础上左右各缩减 CONTENT_INSET px） */
    @Override
    protected int contentWidth() {
        return Math.max(0, super.contentWidth() - CONTENT_INSET * 2);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, PANEL_FIXED_W, panelHeight);
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(PANEL_FIXED_W, panelHeight);
    }

    @Override
    protected void computeDefaultPosition() {
        if (screen == null) return;
        setWindowX(Math.max(8, (screen.width - getWindowWidth()) / 2));
        setWindowY(Mth.clamp((screen.height - getWindowHeight()) / 2,
                TOP_H + 6,
                Math.max(TOP_H + 6, screen.height - getWindowHeight() - 8)));
    }

    @Override
    public int getTitleBarHeight() {
        return TITLE_BAR_H;
    }

    @Override
    protected boolean shouldClipContent() {
        return true;
    }

    // ======================== 条目宽度计算 ========================

    private int computeEntryWidth(SelectableEntry entry) {
        int textW = Minecraft.getInstance().font.width(entry.displayName());
        return Math.max(ITEM_W, textW + 4);
    }

    // ======================== 渲染 ========================

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 1. 清除上一帧的高亮状态
        highlight.clear();

        // 确保进入内容渲染时 GUI 默认 GL 状态正确
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();

        // 2. 渲染筛选标签栏（仅混合条目时显示）
        int filterOffset = filterTabs.getFilterOffset(entityCount, blockCount);
        if (filterTabs.hasMixedTypes(entityCount, blockCount)) {
            filterTabs.render(g, mouseX, mouseY, cx, cy, cw, entityCount, blockCount, entries.size());
        }

        int scrollOffset = horizontalBar.getScroll();
        int visibleW = Math.max(1, cw - PAD_H * 2);

        // 3. 计算总宽度（所有筛选后条目的动态宽度之和，含 GUI 校验）
        int totalW = 0;
        for (SelectableEntry e : entries) {
            if (filterTabs.matchesFilter(e) && hasGuiInteraction(e)) {
                totalW += computeEntryWidth(e);
            }
        }
        horizontalBar.setContent(totalW, visibleW);

        // 4. 滚动条位置
        int sbY = cy + filterOffset + PAD_V + contentItemH + SCROLL_GAP + 7;
        int sbLen = cw - PAD_H * 2;

        // 5. 渲染滚动条
        horizontalBar.render(g, cx + PAD_H, sbY, sbLen);

        // 6. 渲染条目（应用筛选 + GUI 校验 + 横向滚动偏移，动态宽度）
        int currentX = cx + PAD_H - scrollOffset;
        for (int i = 0; i < entries.size(); i++) {
            SelectableEntry entry = entries.get(i);
            if (!filterTabs.matchesFilter(entry) || !hasGuiInteraction(entry)) continue;

            int entryW = computeEntryWidth(entry);
            int itemX = currentX;
            int itemY = cy + filterOffset + PAD_V + 2;
            int ih = contentItemH;

            if (itemX + entryW >= cx && itemX <= cx + cw) {
                boolean isHovered = mouseX >= itemX && mouseX < itemX + entryW
                        && mouseY >= itemY - 5 && mouseY < itemY + ih + 5;
                float t = hoverStates[i].update(isHovered);

                // 条目背景（九宫格 + 交叉淡入淡出）
                SelectEntryRenderer.renderEntryBg(g, itemX, itemY - 5, entryW, ih + 10, t);

                // 悬停条目的高亮写入
                if (isHovered) {
                    updateHighlight(entry);
                }

                // 条目内容（图标 + 文字）
                SelectEntryRenderer.renderEntryContent(g, entry, itemX, itemY, entryW,
                        ICON_SIZE, ICON_TEXT_GAP, isHovered);
            }
            currentX += entryW;
        }

        // 条目渲染完毕后彻底恢复 GUI 默认 GL 状态
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    // ======================== GUI 交互能力校验 ========================

    /**
     * 检查条目是否具有交互能力——显示能打开容器/交易/物品栏的目标,
     * 或覆写了 use()/useWithoutItem() 的可右键交互方块。
     * <p>当框选区内同时存在 GUI 目标和非 GUI 交互方块时，后者也会被放入选择面板。
     * 与 {@link com.rtsbuilding.rtsbuilding.client.screen.panel.handler.EntityInteractionHandler}
     * 的 {@code hasRightClickInteraction} 不同，后者包含非 GUI 的右键交互
     *（如挤奶、剪毛、上鞍），本方法在实体侧只保留能打开屏幕的条目,
     * 在方块侧同时保留有 GUI 和普通右键交互的方块。</p>
     */
    private static boolean hasGuiInteraction(SelectableEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        return switch (entry) {
            case EntityEntry ee -> hasEntityGui(ee.entity());
            case BlockEntry be -> hasBlockGui(mc, be.blockPos());
        };
    }

    private static boolean hasEntityGui(@Nullable Entity entity) {
        if (entity == null || !entity.isAlive()) return false;

        // 村民/流浪商人 → 交易 GUI
        if (entity instanceof AbstractVillager) {
            if (entity instanceof Villager villager) {
                return villager.getVillagerData().getProfession() != VillagerProfession.NONE;
            }
            return true; // 流浪商人等始终可交易
        }
        // 马类（马、驴、骡、羊驼）→ 物品栏 GUI
        if (entity instanceof AbstractHorse) return true;
        // 容器实体（运输矿车、运输船）→ 物品 GUI
        if (entity instanceof ContainerEntity) return true;
        // 其他实现了 MenuProvider 的实体
        if (entity instanceof MenuProvider) return true;
        return false;
    }

    /** 缓存：Block 类 → use/useWithoutItem 方法是否被覆写。 */
    private static final Map<Class<?>, Boolean> USE_OVERRIDE_CACHE = new ConcurrentHashMap<>();

    private static boolean hasBlockGui(Minecraft mc, BlockPos blockPos) {
        // 方块状态提供 MenuProvider（箱子、熔炉等标准容器）
        BlockState state = mc.level.getBlockState(blockPos);
        if (state.getMenuProvider(mc.level, blockPos) != null) return true;
        // 方块实体实现 MenuProvider（某些模组机器仅在 BlockEntity 上实现）
        BlockEntity be = mc.level.getBlockEntity(blockPos);
        if (be instanceof MenuProvider) {
            // 排除空讲台——LecternBlockEntity 即使无书也实现 MenuProvider
            if (be instanceof LecternBlockEntity lectern && lectern.getBook().isEmpty()) return false;
            return true;
        }
        // 无 MenuProvider 但覆写了 use()/useWithoutItem() 的方块（拉杆、按钮等）
        return hasUseOverride(state.getBlock());
    }

    /**
     * 通过反射检测 Block 的 use()/useWithoutItem() 方法是否被覆写。
     * <p>结果缓存在 {@link #USE_OVERRIDE_CACHE} 中，每个 Block 子类仅检测一次。</p>
     */
    private static boolean hasUseOverride(net.minecraft.world.level.block.Block block) {
        Class<?> clazz = block.getClass();
        if (clazz == net.minecraft.world.level.block.Block.class) return false;
        return USE_OVERRIDE_CACHE.computeIfAbsent(clazz, c -> {
            Class<?> current = c;
            while (current != net.minecraft.world.level.block.Block.class && current != null) {
                try {
                    current.getDeclaredMethod("use",
                            net.minecraft.world.level.block.state.BlockState.class,
                            net.minecraft.world.level.Level.class,
                            BlockPos.class,
                            net.minecraft.world.entity.player.Player.class,
                            net.minecraft.world.InteractionHand.class,
                            BlockHitResult.class);
                    return true;
                } catch (NoSuchMethodException e) {
                    try {
                        current.getDeclaredMethod("useWithoutItem",
                                net.minecraft.world.level.block.state.BlockState.class,
                                net.minecraft.world.level.Level.class,
                                BlockPos.class,
                                net.minecraft.world.entity.player.Player.class,
                                BlockHitResult.class);
                        return true;
                    } catch (NoSuchMethodException e2) {
                        // 这个类没有声明，继续往上
                    }
                }
                current = current.getSuperclass();
            }
            return false;
        });
    }

    /** 将条目写入高亮状态 */
    private void updateHighlight(SelectableEntry entry) {
        switch (entry) {
            case EntityEntry ee -> highlight.set(ee.entity(), null);
            case BlockEntry be -> highlight.set(null, be.blockHit());
        }
    }

    // ======================== 交互 ========================

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) return;

        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        int scrollOffset = horizontalBar.getScroll();
        int filterOffset = filterTabs.getFilterOffset(entityCount, blockCount);

        // 筛选标签点击
        if (filterTabs.hasMixedTypes(entityCount, blockCount)
                && filterTabs.handleClick(mouseX, mouseY, cx, cy,
                entityCount, blockCount, entries.size())) {
            return;
        }

        // 滚动条点击
        int sbY = cy + filterOffset + PAD_V + contentItemH + SCROLL_GAP + 7;
        int sbLen = cw - PAD_H * 2;
        if (horizontalBar.handleClick(mouseX, mouseY, cx + PAD_H, sbY, sbLen)) {
            return;
        }

        // 条目点击（筛选 + GUI 校验感知，动态宽度）
        int clickX = cx + PAD_H - scrollOffset;
        for (int i = 0; i < entries.size(); i++) {
            SelectableEntry entry = entries.get(i);
            if (!filterTabs.matchesFilter(entry) || !hasGuiInteraction(entry)) continue;

            int entryW = computeEntryWidth(entry);
            int itemX = clickX;
            int itemY = cy + filterOffset + PAD_V + 2;
            int ih = contentItemH;
            boolean inside = (int) mouseX >= itemX && (int) mouseX < itemX + entryW
                    && (int) mouseY >= itemY - 5 && (int) mouseY < itemY + ih + 5;
            if (inside) {
                interactWithEntry(i);
                return;
            }
            clickX += entryW;
        }
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        return horizontalBar.handleScroll(scrollY);
    }

    // ======================== 鼠标拖拽/释放 ========================

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (horizontalBar.isDragging()) {
            int cx = contentX();
            int cw = contentWidth();
            int filterOffset = filterTabs.getFilterOffset(entityCount, blockCount);
            int sbY = contentY() + filterOffset + PAD_V + contentItemH + SCROLL_GAP + 7;
            int sbLen = cw - PAD_H * 2;
            horizontalBar.handleDrag(mouseX, cx + PAD_H, sbLen);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (horizontalBar.isDragging()) {
            horizontalBar.endDrag();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ======================== 交互执行 ========================

    private void interactWithEntry(int index) {
        if (index < 0 || index >= entries.size()) return;
        SelectableEntry entry = entries.get(index);
        switch (entry) {
            case EntityEntry ee -> RtsClientPacketGateway.sendInteractEntityEmptyHand(
                    ee.entityId(), ee.hitLocation(), null, rayOrigin, rayDir);
            case BlockEntry be -> RtsClientPacketGateway.sendInteractEntityEmptyHand(
                    C2SRtsInteractPayload.NO_ENTITY,
                    be.hitLocation(), be.blockHit(), rayOrigin, rayDir);
        }
        setOpen(false);
    }

    // ======================== 关闭时清理 ========================

    @Override
    protected void onClose() {
        highlight.clear();
    }

    // ======================== 访问器 ========================

    public Vec3 getRayOrigin() {
        return rayOrigin;
    }

    public Vec3 getRayDir() {
        return rayDir;
    }

    public List<SelectableEntry> getEntries() {
        return entries;
    }
}
