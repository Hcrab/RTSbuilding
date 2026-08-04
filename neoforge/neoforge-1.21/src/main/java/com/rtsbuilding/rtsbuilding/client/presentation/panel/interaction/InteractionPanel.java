package com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import com.rtsbuilding.rtsbuilding.network.NetworkConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreenConstants.TOP_H;

/**
 * 容器标签面板（原"选择交互目标 + 容器面板"合并后的进一步简化版）：
 * 以网页式多标签页的形式，将框选到的所有容器目标直接展示在标签栏上，
 * 每个容器一个标签，点击即打开对应容器，当前打开的容器标签高亮。
 *
 * <p>交互流程：框选目标后弹出本面板（标签栏列出全部框选容器并自动打开第一个）→
 * 点击其他标签关闭旧容器并打开新容器 → 容器关闭后面板自动关闭。</p>
 *
 * <p>职责划分：容器页状态机见 {@link ContainerPageState}，目标能力探测见 {@link TargetProbe}，
 * 图标解析见 {@link ContainerIconResolver}，输入转发见 {@link ContainerInputForwarder}，
 * 标签栏渲染见 {@link PageTabBar}。本类仅负责协调与窗口管理。</p>
 */
public final class InteractionPanel extends RtsPanel {

    // ==================== 布局常量 ====================

    private static final int PANEL_PAD_H = 10;
    private static final int PANEL_PAD_V = 4;
    private static final int WIDGET_SCAN_MARGIN = 50;
    private static final int CONTENT_INSET = 4;
    /** 面板最小宽度。 */
    private static final int MIN_WINDOW_WIDTH = 88;
    private static final int DEFAULT_W = 320;
    private static final int DEFAULT_H = 120;
    private static final Component FIXED_TITLE = Component.literal("容器面板");

    private static final int TAB_BAR_H = PageTabBar.TAB_BAR_H;

    /** 窗口宽 → 容器区宽的水平差值（窗口边框 2 + 内容内缩 2×CONTENT_INSET）。 */
    private static final int WINDOW_TO_CONTAINER_DX = 2 + CONTENT_INSET * 2;
    /** 窗口高 → 容器区高的垂直差值（标题栏下沿 3 + 内容底部 4 + 标签栏高），不含标题栏自身。 */
    private static final int WINDOW_TO_CONTAINER_DY = 3 + 4 + TAB_BAR_H;

    // ==================== 状态 ====================

    private final PageTabBar pageTabBar = new PageTabBar();
    /** 容器页状态机：等待打开 / 已打开 / 超时 / 关闭。 */
    private final ContainerPageState pageState = new ContainerPageState();

    private List<SelectableEntry> entries = List.of();
    private Vec3 rayOrigin = Vec3.ZERO;
    private Vec3 rayDir = Vec3.ZERO;

    @Nullable
    private ContainerInputForwarder inputForwarder;
    /** 当前打开容器（外部打开、无关联条目时）的标签图标。 */
    private ItemStack containerIcon = ItemStack.EMPTY;

    /** 容器内容尺寸缓存：key = 归一化标识，value = {内容宽, 内容高}；面板大小取所有标签缓存的最大值。 */
    private final Map<Object, int[]> containerSizeCache = new HashMap<>();

    /**
     * 内容适配尺寸（"natural"）：仅在内容扫描（打开容器、自动增长）时更新，
     * 作为默认尺寸与"恢复适配尺寸"的依据，不被用户缩放/拖动覆盖。
     */
    @Nullable
    private int[] naturalPanelSize;
    /** 已同步容器基准的转发器实例：实例变化（打开新容器）时强制重建布局。 */
    private ContainerInputForwarder syncedForwarder;
    /** 已同步容器基准时的窗口尺寸。 */
    private int syncedWindowW = -1;
    private int syncedWindowH = -1;
    /** 本次打开请求是否由面板从关闭态发起：容器页提交时按内容适配尺寸创建面板，不沿用旧尺寸。 */
    private boolean panelReopened;

    /** 单点记录模式：标签由玩家单点右键容器逐个累积，与框选状态无关。 */
    private boolean directInteractMode;

    // ==================== 标签列表缓存 ====================

    /** 标签缓存：entries 引用或"外部容器标签可见性"变化时失效，避免渲染热路径每帧重建。 */
    @Nullable
    private List<PageTabBar.Tab> tabsCache;
    private List<SelectableEntry> tabsCacheEntries = List.of();
    private boolean tabsCacheExternalTab;

    /**
     * 渲染期标志：当本面板将容器屏幕作为子覆盖层渲染时置位，供
     * {@code ScreenRenderBgMixin} 跳过深色背景。
     *
     * <p>该标志的正确性不依赖"面板单例"约定：渲染在同一线程串行执行，
     * 且置位/复位在 try-finally 中成对出现，即使将来出现多面板实例，
     * 标志也只影响各自渲染调用栈内的子屏幕背景绘制。</p>
     */
    private static volatile boolean renderingOverlay;

    public InteractionPanel() {
        this.draggable = true;
        this.resizable = true;
        this.closable = true;
        bounds.setInitialized(true);
    }

    // ==================== 页面公开 API ====================

    /**
     * 框选后打开面板：记录目标列表并自动打开第一个有 GUI 的容器。
     * 若没有任何可交互目标则返回 {@code false} 且不打开面板。
     */
    public boolean showTargets(List<SelectableEntry> newEntries,
                               Vec3 rayOrigin, Vec3 rayDir, int mouseX, int mouseY) {
        this.entries = List.copyOf(newEntries);
        this.rayOrigin = rayOrigin;
        this.rayDir = rayDir;
        this.directInteractMode = false;

        int first = firstGuiEntryIndex();
        if (first < 0) return false;

        // 与单点模式共用同一打开流程：面板未开时自动定位，已开时按尺寸策略适配
        if (openContainerEntry(entries.get(first), mouseX, mouseY)) {
            interactWithEntry(first);
        }
        if (screen != null) screen.getFloatingWindowLayer().markSortDirty();
        return true;
    }

    /**
     * 更新目标列表（供框选校验使用）：刷新标签栏；当前打开的容器若已失效，
     * 由服务端关闭容器后的 {@link #tick()} 兜底关闭面板。
     */
    public void updateTargets(List<SelectableEntry> newEntries) {
        this.entries = List.copyOf(newEntries);
    }

    /**
     * 打开（或刷新）容器页。若面板尚未打开则自动打开。
     */
    public void openContainerPage(AbstractContainerScreen<?> containerScreen) {
        if (containerScreen == null) return;
        // 释放上一个转发器（外部打开路径不经过 openContainerEntry 的切换清理，防止旧 screen 泄漏）
        if (inputForwarder != null) inputForwarder.clear();
        boolean wasOpen = isOpen();
        // 面板从关闭态首次打开（含外部打开）：直接以标签中最大的容器尺寸创建面板，
        // 避免沿用上次会话残留尺寸导致框选/单点两条路径大小不一致
        boolean firstOpen = !wasOpen || panelReopened;
        this.inputForwarder = new ContainerInputForwarder(containerScreen);

        // 先提交打开结果（active = 等待中的归一化键；tick 探测已转正时沿用其 activeId；
        // 两者皆无则为外部打开的容器，active = null），再按活动条目解析图标
        Object openedId = pageState.getPendingId();
        if (openedId == null && pageState.isPageOpen() && pageState.getActiveId() != null) {
            openedId = pageState.getActiveId();
        }
        pageState.opened(openedId);
        this.containerIcon = ContainerIconResolver.resolve(containerScreen, activeEntry());
        setOpen(true);
        if (screen == null) return;

        int[] contentBounds = scanContentBounds(containerScreen);
        // 记录当前容器内容尺寸，并重算"所有标签最大"的自然尺寸（面板以最大容器为准）
        cacheContainerSize(openedId, contentBounds[0], contentBounds[1]);
        updateNaturalSize(contentBounds[0], contentBounds[1]);
        int naturalW = naturalPanelSize[0];
        int naturalH = naturalPanelSize[1];
        this.bounds.setDefaults(naturalW, naturalH);

        if (!isResizing()) {
            if (firstOpen) {
                // 面板从关闭态首次打开：直接以标签中最大的容器为准（范围/单点两条路径行为一致）
                setWindowWidth(Math.min(naturalW, getMaxWindowWidth()));
                setWindowHeight(Math.min(naturalH, getMaxWindowHeight()));
            } else {
                // 容器页间切换：新容器更大则递进，否则保持当前尺寸
                setWindowWidth(Math.min(Math.max(getWindowWidth(), naturalW), getMaxWindowWidth()));
                setWindowHeight(Math.min(Math.max(getWindowHeight(), naturalH), getMaxWindowHeight()));
            }
        }
        if (!wasOpen) {
            computeDefaultPosition();
        }
        clampWindowToScreen();

        syncContainerScreen();
        markBroughtToFront();
        if (screen != null) screen.getFloatingWindowLayer().markSortDirty();
    }

    /**
     * 完全关闭面板（向服务端发送容器关闭包并清理全部状态）。
     */
    public void closePanel() {
        if (!isOpen()) return;
        closeContainerIfOpen();
        resetState();
        setOpen(false);
    }

    /**
     * 仅关闭容器页（向服务端发送关闭包），随后关闭整个面板。
     */
    public void closeContainerPage() {
        if (!pageState.isPageOpen()) return;
        closeContainerOnServer();
        resetState();
        setOpen(false);
    }

    public boolean isContainerPageOpen() {
        return isOpen() && pageState.isPageOpen();
    }

    /**
     * carried 退回兜底：若菜单 carried 中仍有物品（点击式拿起后未放回），
     * 先把 carried 退回远程存储，避免物品滞留或流入背包。幂等：仅 carried 非空时发送。
     *
     * <p>静态方法：逻辑仅依赖玩家容器菜单，不依赖面板实例。公开给 ScreenCoordinator：
     * 退出 RTS 模式时无条件调用（面板可能从未创建——仅点击网格条目拿起物品的路径
     * 没有容器页也没有面板实例，setOpen(false) 不会触发 onClose 链）。</p>
     */
    public static void returnCarriedToLinked() {
        Minecraft mc = Minecraft.getInstance();
        var menu = mc.player != null ? mc.player.containerMenu : null;
        if (menu == null || menu.getCarried().isEmpty()) return;
        ItemStack carried = menu.getCarried();
        String itemId = BuiltInRegistries.ITEM.getKey(carried.getItem()).toString();
        // amount=64：服务端取 min(amount, carried.getCount())，即全部退回；剩余由 S2C 同步
        RtsClientPacketGateway.sendReturnCarried(itemId, 64);
        menu.setCarried(ItemStack.EMPTY); // 乐观清空，服务端权威状态经 S2C 同步
    }

    public List<SelectableEntry> getEntries() {
        return entries;
    }

    public boolean isDirectInteractMode() {
        return directInteractMode;
    }

    /**
     * 单点模式右键记录：将目标条目加入标签列表（按归一化键去重）并打开面板，
     * 随后准备打开该容器（关闭旧容器、置位等待键）。
     *
     * <p>返回 {@code true} 表示调用方应继续发送交互包以打开容器；
     * {@code false} 表示目标已记录且正在打开/已打开，无需重复交互。</p>
     */
    public boolean recordDirectInteract(SelectableEntry entry, Vec3 rayOrigin, Vec3 rayDir,
                                        int mouseX, int mouseY) {
        Object key = entry.identifier();

        boolean exists = false;
        for (SelectableEntry e : entries) {
            if (Objects.equals(e.identifier(), key)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            List<SelectableEntry> newEntries = new ArrayList<>(entries);
            newEntries.add(entry);
            this.entries = newEntries;
        }
        this.rayOrigin = rayOrigin;
        this.rayDir = rayDir;
        this.directInteractMode = true;

        // 与框选模式共用同一打开流程（面板打开/定位/去重/切换判定全部统一）
        return openContainerEntry(entry, mouseX, mouseY);
    }

    /**
     * 统一"打开/切换到指定容器条目"流程：面板未开时自动打开并定位到鼠标附近，
     * 与当前打开的容器相同时仅恢复适配尺寸，正在打开同一容器时跳过重复交互；
     * 否则关闭旧容器、置位等待键。框选、单点、点标签三条入口共用，保证行为一致。
     *
     * @return 是否需要发送交互包以打开容器（{@code false} 表示已打开/正在打开，无需重复交互）。
     */
    private boolean openContainerEntry(SelectableEntry entry, int mouseX, int mouseY) {
        Object key = entry.identifier();
        // 在 openRequested 之前捕获"从关闭态发起"：容器页提交时据此直接适配内容尺寸
        this.panelReopened = !pageState.isPageOpen();

        boolean wasOpen = isOpen();
        setOpen(true);
        if (!wasOpen) {
            positionNearMouse(mouseX, mouseY);
        }

        // 已打开同一容器：仅同步尺寸
        if (pageState.sameAsActive(key)) {
            applyContainerPageSize();
            if (screen != null) screen.getFloatingWindowLayer().markSortDirty();
            return false;
        }
        // 正在打开同一容器：等待服务端结果，无需重复交互
        if (pageState.sameAsPending(key)) {
            return false;
        }

        // 切换容器：先关闭旧容器，再等待服务端打开新容器
        if (pageState.isPageOpen()) {
            closeContainerOnServer();
            if (inputForwarder != null) inputForwarder.clear();
        }
        pageState.openRequested(key);
        if (screen != null) screen.getFloatingWindowLayer().markSortDirty();
        return true;
    }

    /**
     * 容器页作为子覆盖层渲染时置位，供 {@code ScreenRenderBgMixin} 跳过深色背景。
     */
    public static boolean isRenderingOverlay() {
        return renderingOverlay;
    }

    // ==================== 状态清理 ====================

    /**
     * 统一重置面板状态（输入转发器、状态机、目标列表、图标与尺寸缓存）。
     * 各关闭入口（关闭按钮、Esc、服务端关闭、超时）共用，避免清理序列漂移。
     */
    private void resetState() {
        // 关闭（超时/服务端关闭/异常路径）：先把 carried 退回远程存储
        returnCarriedToLinked();
        if (inputForwarder != null) inputForwarder.clear();
        pageState.reset();
        containerIcon = ItemStack.EMPTY;
        naturalPanelSize = null;
        containerSizeCache.clear();
        entries = List.of();
        directInteractMode = false;
        panelReopened = false;
    }

    /** 容器页打开时先向服务端发送关闭包。 */
    private void closeContainerIfOpen() {
        if (pageState.isPageOpen()) {
            closeContainerOnServer();
        }
    }

    // ==================== 内部工具 ====================

    private int firstGuiEntryIndex() {
        for (int i = 0; i < entries.size(); i++) {
            if (TargetProbe.hasGuiInteraction(entries.get(i))) return i;
        }
        return -1;
    }

    /** 当前打开的容器对应的条目；外部打开的容器（无关联键）返回 null。 */
    @Nullable
    private SelectableEntry activeEntry() {
        Object id = pageState.getActiveId();
        if (id == null) return null;
        for (SelectableEntry e : entries) {
            if (Objects.equals(e.identifier(), id)) return e;
        }
        return null;
    }

    private void positionNearMouse(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int px = Math.max(0, Math.min(mouseX + 8, screenW - getWindowWidth()));
        int py = Math.max(0, Math.min(mouseY - getWindowHeight() / 2, screenH - getWindowHeight()));
        setBounds(px, py, getWindowWidth(), getWindowHeight());
    }

    private List<PageTabBar.Tab> buildTabs() {
        boolean externalTab = pageState.isPageOpen() && pageState.getActiveId() == null
                && inputForwarder != null && inputForwarder.hasScreen();
        // 缓存命中：entries 引用未变且外部标签可见性未变（entries 每次修改都会替换引用）
        if (tabsCache != null && tabsCacheEntries == entries && tabsCacheExternalTab == externalTab) {
            return tabsCache;
        }

        List<PageTabBar.Tab> tabs = new ArrayList<>();
        Map<String, Integer> nameCounts = new HashMap<>();
        Set<Object> seenKeys = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            SelectableEntry entry = entries.get(i);
            if (!TargetProbe.hasGuiInteraction(entry)) continue;
            // 多方块共用同一个 GUI 的条目（如大箱子）只生成一个标签
            if (!seenKeys.add(entry.identifier())) continue;
            String base = entry.displayName();
            int n = nameCounts.merge(base, 1, Integer::sum);
            String label = n == 1 ? base : base + " (" + n + ")";
            tabs.add(new PageTabBar.Tab(entry.identifier(),
                    ContainerIconResolver.resolveForEntry(entry), Component.literal(label), i));
        }
        if (externalTab) {
            tabs.add(new PageTabBar.Tab(null, containerIcon, FIXED_TITLE, -1));
        }

        this.tabsCache = tabs;
        this.tabsCacheEntries = entries;
        this.tabsCacheExternalTab = externalTab;
        return tabs;
    }

    private void handleTabClick(PageTabBar.Tab tab) {
        requestOpenContainer(tab.entryIndex());
    }

    /**
     * 关闭标签：按归一化键从目标列表中移除对应条目（大箱子等左右两半一并移除）。
     * 若关闭的是当前打开的容器，先关闭容器并自动打开剩余的第一个容器；
     * 若标签正在等待打开则取消等待；所有标签关闭后整个面板自动关闭。
     */
    private void handleTabClose(PageTabBar.Tab tab) {
        int entryIndex = tab.entryIndex();
        if (entryIndex < 0 || entryIndex >= entries.size()) return;
        Object key = entries.get(entryIndex).identifier();
        // 标签关闭后不再参与"最大容器"尺寸计算
        containerSizeCache.remove(key);

        List<SelectableEntry> remaining = new ArrayList<>(entries.size());
        for (SelectableEntry e : entries) {
            if (!Objects.equals(e.identifier(), key)) remaining.add(e);
        }
        this.entries = remaining;

        boolean wasActive = pageState.sameAsActive(key);
        boolean wasPending = pageState.sameAsPending(key);
        if (!wasActive && !wasPending) return;

        // 取消等待中的打开请求
        if (wasPending) {
            pageState.cancelPending();
        }
        // 关闭当前打开的容器
        if (wasActive) {
            closeContainerOnServer();
            if (inputForwarder != null) inputForwarder.clear();
            pageState.closed();
        }

        if (remaining.isEmpty()) {
            resetState();
            setOpen(false);
            return;
        }

        // 关闭的是当前容器时，自动打开剩余的第一个可交互容器
        if (wasActive) {
            int next = firstGuiEntryIndex();
            if (next < 0) {
                resetState();
                setOpen(false);
                return;
            }
            requestOpenContainer(next);
        }
    }

    /**
     * 请求打开（或切换到）指定下标的容器条目（点标签入口）：
     * 与当前打开的容器相同时仅切换视图；否则先关闭旧容器，再发送交互包等待服务端打开新容器。
     */
    private void requestOpenContainer(int entryIndex) {
        if (entryIndex < 0 || entryIndex >= entries.size()) return;
        SelectableEntry target = entries.get(entryIndex);
        // 点标签时面板必然开着：传 0,0 不会触发重新定位，与框选/单点共用同一打开流程
        if (openContainerEntry(target, 0, 0)) {
            interactWithEntry(entryIndex);
        }
    }

    private void applyContainerPageSize() {
        if (naturalPanelSize == null) return;
        setSize(Math.max(getWindowWidth(), naturalPanelSize[0]),
                Math.max(getWindowHeight(), naturalPanelSize[1]));
        syncContainerScreen();
    }

    @Nullable
    private PageTabBar.Tab findActiveTab(List<PageTabBar.Tab> tabs) {
        Object activeId = pageState.getActiveId();
        for (PageTabBar.Tab t : tabs) {
            int idx = t.entryIndex();
            if (idx >= 0 && idx < entries.size()
                    && Objects.equals(entries.get(idx).identifier(), activeId)) {
                return t;
            }
        }
        return null;
    }

    private boolean isOverPageTabBar(double mouseY) {
        return mouseY >= contentY() && mouseY < contentY() + TAB_BAR_H;
    }

    private double containerLocalX(double mouseX) {
        return mouseX - contentX();
    }

    private double containerLocalY(double mouseY) {
        return mouseY - contentY() - TAB_BAR_H;
    }

    // ==================== 渲染 ====================

    /**
     * 标签条深色底：在内容区裁剪建立之前绘制，向上延伸至标题栏底部（+1 对齐标题栏背景下缘）、
     * 左右避开面板边框（各内缩 1px）铺满（Edge 深色工具栏风格）。
     */
    @Override
    protected void onRenderBeforeContent(GuiGraphics g, int mouseX, int mouseY) {
        int bgY = bounds.getY() + getTitleBarHeight() + 1;
        int bgBottom = contentY() + TAB_BAR_H;
        if (bgBottom > bgY) {
            int bgX = bounds.getX() + 1;
            int bgW = Math.max(0, bounds.getWidth() - 2);
            g.fill(bgX, bgY, bgX + bgW, bgBottom, PageTabBar.TAB_BAR_BG_COLOR);
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        syncContainerScreen();
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();

        List<PageTabBar.Tab> tabs = buildTabs();
        pageTabBar.render(g, cx, cy, cw, TAB_BAR_H, mouseX, mouseY, findActiveTab(tabs), tabs);

        renderContainerPage(g, mouseX, mouseY, partialTick, cx, cy + TAB_BAR_H, cw);
    }

    /**
     * 容器页内容：将容器屏幕作为子覆盖层渲染在标签栏下方；
     * 尚未打开容器时显示提示文案。
     */
    private void renderContainerPage(GuiGraphics g, int mouseX, int mouseY, float partialTick,
                                     int cx, int cy, int cw) {
        if (inputForwarder == null || !inputForwarder.hasScreen()) {
            String msg = pageState.hasPending() ? "正在打开容器…" : "点击上方标签打开容器";
            int tx = cx + Math.max(0, (cw - Minecraft.getInstance().font.width(msg)) / 2);
            int ty = cy + Math.max(0, (containerAreaHeight() - Minecraft.getInstance().font.lineHeight) / 2);
            TextRenderer.draw(g, msg, tx, ty, ThemeManager.getTextColor());
            return;
        }
        var cs = inputForwarder.getScreen();

        g.pose().pushPose();
        try {
            g.pose().translate(cx, cy, 0);
            renderingOverlay = true;
            try {
                RenderSystem.enableDepthTest();
                try {
                    cs.render(g, mouseX - cx, mouseY - cy, partialTick);
                } finally {
                    RenderSystem.disableDepthTest();
                }
            } finally {
                renderingOverlay = false;
            }
        } finally {
            g.pose().popPose();
        }

        RenderSystem.clear(256, false);
    }

    // ==================== 输入 ====================

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();

        if (isOverPageTabBar(mouseY)) {
            if (button == 0) {
                List<PageTabBar.Tab> tabs = buildTabs();
                PageTabBar.TabHit hit = pageTabBar.handleClick(mouseX, mouseY, cx, cy, cw, TAB_BAR_H,
                        findActiveTab(tabs), tabs);
                if (hit != null) {
                    if (hit.onCloseButton()) {
                        handleTabClose(hit.tab());
                    } else {
                        handleTabClick(hit.tab());
                    }
                }
            }
            return;
        }

        if (button == 0 && inputForwarder != null && inputForwarder.hasScreen()) {
            // 点击容器槽位放下了物品（carried 非空→空）时，若放下的正是当前启用选材则取消选材
            //（启用仅在“拿起”期间有效，放入容器即失效；拿起/交换不触发）
            var menu = Minecraft.getInstance().player != null
                    ? Minecraft.getInstance().player.containerMenu : null;
            ItemStack before = menu != null ? menu.getCarried().copy() : ItemStack.EMPTY;
            inputForwarder.mouseClicked(containerLocalX(mouseX), containerLocalY(mouseY), button);
            if (!before.isEmpty() && menu != null && menu.getCarried().isEmpty()
                    && screen != null) {
                screen.cancelGridSelectionIf(before);
            }
        }
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isOverPageTabBar(mouseY)) {
            pageTabBar.handleScroll(scrollY, contentWidth(), buildTabs());
            return true;
        }
        if (inputForwarder != null && inputForwarder.hasScreen()) {
            return inputForwarder.mouseScrolled(containerLocalX(mouseX), containerLocalY(mouseY), scrollX, scrollY);
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.open) return false;

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && isInsideWindow(mouseX, mouseY)) {
            if (!isOverPageTabBar(mouseY)
                    && inputForwarder != null && inputForwarder.hasScreen()) {
                inputForwarder.mouseClicked(containerLocalX(mouseX), containerLocalY(mouseY), button);
            }
            return true;
        }

        // 容器槽位 Shift+点击：原版式快速转移——一键把该槽位物品导入网络存储
        // （替代原版“转移到玩家背包”；命中玩家背包槽位时同样导入网络，与 AE 终端语义一致）
        // 仅当鼠标位于面板窗口内才转发：窗口外的点击（如物品网格）绝不触发容器槽位操作
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && Screen.hasShiftDown()
                && isInsideWindow(mouseX, mouseY)
                && !isOverPageTabBar(mouseY)
                && inputForwarder != null && inputForwarder.hasScreen()) {
            int slotIdx = inputForwarder.findSlotIndexAt(containerLocalX(mouseX), containerLocalY(mouseY));
            if (slotIdx >= 0) {
                RtsClientPacketGateway.sendImportMenuSlot(slotIdx);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.open) return false;

        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;

        // 仅窗口内的拖拽才转发给容器屏幕：窗口外拖拽（如从网格拿起物品后移动）若转发，
        // 容器屏幕可能把“点击到空处”误判为扔出/快速合成操作
        if (isInsideWindow(mouseX, mouseY)
                && !isOverPageTabBar(mouseY)
                && inputForwarder != null && inputForwarder.hasScreen()) {
            inputForwarder.mouseDragged(containerLocalX(mouseX), containerLocalY(mouseY), button, dragX, dragY);
        }

        if (isInsideWindow(mouseX, mouseY)) return true;
        return button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!this.open) return false;

        boolean inside = isInsideWindow(mouseX, mouseY);

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // 仅窗口内释放才转发：窗口外释放（如点击网格物品后松开）绝不转发，
            // 否则容器屏幕会把“未命中槽位的释放”当作“携带中点击空白”→ 服务端直接扔掉 carried
            if (inside
                    && !isOverPageTabBar(mouseY)
                    && inputForwarder != null && inputForwarder.hasScreen()) {
                inputForwarder.mouseReleased(containerLocalX(mouseX), containerLocalY(mouseY), button);
            }
            return inside;
        }

        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        if (inside
                && !isOverPageTabBar(mouseY)
                && inputForwarder != null && inputForwarder.hasScreen()) {
            inputForwarder.mouseReleased(containerLocalX(mouseX), containerLocalY(mouseY), button);
        }
        return handled;
    }

    @Override
    public boolean mouseMoved(double mouseX, double mouseY) {
        if (!this.open) return false;
        if (!isInsideWindow(mouseX, mouseY)) return false;
        if (!isOverPageTabBar(mouseY)
                && inputForwarder != null && inputForwarder.hasScreen()) {
            inputForwarder.mouseMoved(containerLocalX(mouseX), containerLocalY(mouseY));
        }
        return false;
    }

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        // E（原版背包键）与 ESC 同语义：容器页打开时主动关闭容器页。
        // 不转发给容器屏幕，避免原版 onClose → 服务端回包 → tick 兜底的被动关闭路径
        //（容器已关闭但面板仍渲染旧菜单 1-2 帧，造成画面闪烁）
        boolean closeKey = keyCode == GLFW.GLFW_KEY_ESCAPE
                || Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode);
        if (closeKey) {
            if (pageState.isPageOpen()) {
                closeContainerPage();
                return true;
            }
            return false;
        }
        if (inputForwarder != null) {
            return inputForwarder.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        if (inputForwarder != null) {
            return inputForwarder.charTyped(codePoint, modifiers);
        }
        return false;
    }

    // ==================== 生命周期 ====================

    @Override
    public void init(BuilderScreen screen) {
        super.init(screen);
        // 容器页打开时窗口尺寸必然已初始化，直接按实际窗口同步容器基准
        syncContainerScreen();
    }

    @Override
    public void tick() {
        super.tick();
        if (!isOpen()) return;

        Minecraft mc = Minecraft.getInstance();

        // 等待服务端打开新容器：保持容器页视图，直到打开成功或超时
        // 注意排除 RTS 自家菜单（合成终端 containerId 也非 0）：否则等待会被误判为已打开，
        // pending 被提前清除后超时兜底失效，面板将卡在“点击上方标签打开容器”
        if (pageState.hasPending()) {
            var pendingMenu = mc.player != null ? mc.player.containerMenu : null;
            boolean containerOpen = pendingMenu != null
                    && pendingMenu.containerId != 0
                    && !(pendingMenu instanceof RtsCraftTerminalMenu);
            if (pageState.tickPending(containerOpen) == ContainerPageState.TickResult.TIMED_OUT) {
                resetState();
                setOpen(false);
            }
            return;
        }

        if (pageState.isPageOpen() && inputForwarder != null && inputForwarder.hasScreen()) {
            inputForwarder.tick();
            autoGrowIfNeeded();

            // 服务端已关闭容器：兜底关闭面板
            if (mc.player != null && mc.player.containerMenu.containerId == 0) {
                resetState();
                setOpen(false);
            }
        }
    }

    @Override
    protected void onClose() {
        super.onClose();
        closeContainerIfOpen();
        resetState();
    }

    private void closeContainerOnServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu.containerId == 0) return;

        // 关闭容器前：先退回 carried 再发关闭包（同一连接按序到达，服务端先处理退回）
        returnCarriedToLinked();

        int containerId = mc.player.containerMenu.containerId;
        if (mc.player instanceof LocalPlayer localPlayer) {
            localPlayer.connection.send(new ServerboundContainerClosePacket(containerId));
        }
        mc.player.containerMenu = mc.player.inventoryMenu;
    }

    private void autoGrowIfNeeded() {
        if (isResizing()) return;
        if (inputForwarder == null || !inputForwarder.hasScreen()) return;
        var cs = inputForwarder.getScreen();

        int[] contentBounds = scanContentBounds(cs);
        // 更新当前容器内容尺寸缓存，并重算"所有标签最大"的自然尺寸
        cacheContainerSize(pageState.getActiveId(), contentBounds[0], contentBounds[1]);
        updateNaturalSize(contentBounds[0], contentBounds[1]);
        int neededPanelW = naturalPanelSize[0];
        int neededPanelH = naturalPanelSize[1];

        if (neededPanelW <= getWindowWidth() && neededPanelH <= getWindowHeight()) return;

        int newW = Math.min(Math.max(getWindowWidth(), neededPanelW), getMaxWindowWidth());
        int newH = Math.min(Math.max(getWindowHeight(), neededPanelH), getMaxWindowHeight());

        if (newW > getWindowWidth() || newH > getWindowHeight()) {
            setWindowWidth(newW);
            setWindowHeight(newH);
            syncContainerScreen();
        }
    }

    /**
     * 缓存容器内容尺寸：key 为归一化标识（外部打开的容器为 null，跳过缓存）。
     */
    private void cacheContainerSize(@Nullable Object key, int contentW, int contentH) {
        if (key == null) return;
        containerSizeCache.put(key, new int[]{contentW, contentH});
    }

    /**
     * 重算自然尺寸：取"当前容器内容 + 所有标签已缓存内容"的最大值，
     * 使面板始终以标签栏中最大的容器 GUI 为准；新加入更大的容器时自然递进。
     */
    private void updateNaturalSize(int contentW, int contentH) {
        int maxContentW = contentW;
        int maxContentH = contentH;
        for (SelectableEntry e : entries) {
            if (!TargetProbe.hasGuiInteraction(e)) continue;
            int[] cached = containerSizeCache.get(e.identifier());
            if (cached != null) {
                maxContentW = Math.max(maxContentW, cached[0]);
                maxContentH = Math.max(maxContentH, cached[1]);
            }
        }
        setNaturalPanelSize(naturalPanelWidth(maxContentW), naturalPanelHeight(maxContentH));
    }

    /**
     * 记录内容适配尺寸（natural）：仅在内容扫描结果更新时调用，不受用户缩放影响。
     */
    private void setNaturalPanelSize(int w, int h) {
        if (naturalPanelSize == null) naturalPanelSize = new int[2];
        naturalPanelSize[0] = w;
        naturalPanelSize[1] = h;
    }

    /**
     * 统一同步容器屏幕布局基准：仅当转发器实例或窗口尺寸变化时才重建。
     * 所有尺寸入口（打开、自动增长、恢复、拖拽缩放、屏幕重建）最终都汇聚到这里，
     * 保证容器 GUI 始终按当前实际窗口尺寸居中。
     */
    private void syncContainerScreen() {
        if (inputForwarder == null || !inputForwarder.hasScreen()) return;
        if (!pageState.isPageOpen()) return;
        int windowW = getWindowWidth();
        int windowH = getWindowHeight();
        if (inputForwarder == syncedForwarder && windowW == syncedWindowW && windowH == syncedWindowH) {
            return;
        }
        inputForwarder.init(containerAreaWidth(), containerAreaHeight());
        syncedForwarder = inputForwarder;
        syncedWindowW = windowW;
        syncedWindowH = windowH;
    }

    // ==================== 窗口规范 ====================

    @Override
    protected Component getTitle() {
        return FIXED_TITLE;
    }

    @Override
    protected int getDefaultWidth() {
        if (naturalPanelSize != null) return naturalPanelSize[0];
        if (inputForwarder != null && inputForwarder.hasScreen()) {
            return naturalPanelWidth(inputForwarder.getScreen().getXSize());
        }
        return DEFAULT_W;
    }

    @Override
    protected int getDefaultHeight() {
        if (naturalPanelSize != null) return naturalPanelSize[1];
        if (inputForwarder != null && inputForwarder.hasScreen()) {
            return naturalPanelHeight(inputForwarder.getScreen().getYSize());
        }
        return DEFAULT_H;
    }

    @Override
    public int getMinWindowWidth() {
        return MIN_WINDOW_WIDTH;
    }

    @Override
    public int getMinWindowHeight() {
        return TAB_BAR_H + getTitleBarHeight() + 50;
    }

    /** 容器区可见宽度：与渲染平移、鼠标坐标转发共用同一基准（内容区宽度）。 */
    private int containerAreaWidth() {
        return containerAreaWidthFor(getWindowWidth());
    }

    /** 容器区可见高度：与渲染平移、鼠标坐标转发共用同一基准（内容区高度扣除标签栏）。 */
    private int containerAreaHeight() {
        return containerAreaHeightFor(getWindowHeight());
    }

    /** 给定窗口宽时的容器区宽度。 */
    private int containerAreaWidthFor(int windowW) {
        return Math.max(1, windowW - WINDOW_TO_CONTAINER_DX);
    }

    /** 给定窗口高时的容器区高度。 */
    private int containerAreaHeightFor(int windowH) {
        return Math.max(1, windowH - getTitleBarHeight() - WINDOW_TO_CONTAINER_DY);
    }

    /** 按容器内容宽度计算面板自然宽度（容器区 = 内容宽 + 两侧总留白，内容在容器区内居中）。 */
    private int naturalPanelWidth(int contentW) {
        return Math.max(MIN_WINDOW_WIDTH, contentW + PANEL_PAD_H + WINDOW_TO_CONTAINER_DX);
    }

    /** 按容器内容高度计算面板自然高度（容器区 = 内容高 + 底部留白）。 */
    private int naturalPanelHeight(int contentH) {
        return Math.max(getMinWindowHeight(),
                contentH + PANEL_PAD_V + getTitleBarHeight() + WINDOW_TO_CONTAINER_DY);
    }

    @Override
    protected int contentX() {
        return super.contentX() + CONTENT_INSET;
    }

    @Override
    protected int contentWidth() {
        return Math.max(0, super.contentWidth() - CONTENT_INSET * 2);
    }

    @Override
    protected boolean shouldClipContent() {
        return true;
    }

    @Override
    public void clampWindowToScreen() {
        if (this.screen == null) return;
        int maxX = Math.max(0, this.screen.width - bounds.getWidth());
        bounds.setX(Mth.clamp(bounds.getX(), 0, maxX));

        if (bounds.getHeight() > this.screen.height) {
            int minY = this.screen.height - bounds.getHeight();
            int maxY = 0;
            bounds.setY(Mth.clamp(bounds.getY(), minY, maxY));
        } else {
            int maxY = Math.max(0, this.screen.height - getTitleBarHeight());
            bounds.setY(Mth.clamp(bounds.getY(), 0, maxY));
        }
    }

    @Override
    protected void computeDefaultPosition() {
        if (screen == null) return;
        setWindowX(Math.max(8, (screen.width - getWindowWidth()) / 2));
        if (getWindowHeight() > screen.height) {
            setWindowY(TOP_H + 6);
        } else {
            setWindowY(Mth.clamp((screen.height - getWindowHeight()) / 2,
                    TOP_H + 6,
                    Math.max(TOP_H + 6, screen.height - getWindowHeight() - 8)));
        }
    }

    // ==================== 目标扫描与交互 ====================

    /**
     * 扫描容器屏幕内容边界（启发式）：以容器背景区域为中心、外扩 {@value #WIDGET_SCAN_MARGIN}
     * 像素，圈定附近所有控件的最小包围盒。对特殊布局（如 JEI 侧栏控件较多）可能误圈，
     * 但仅影响面板自然尺寸的估算。
     */
    private int[] scanContentBounds(AbstractContainerScreen<?> cs) {
        int bgLeft = cs.getGuiLeft();
        int bgTop = cs.getGuiTop();
        int bgRight = bgLeft + cs.getXSize();
        int bgBottom = bgTop + cs.getYSize();

        int minX = bgLeft;
        int minY = bgTop;
        int maxX = bgRight;
        int maxY = bgBottom;

        int margin = WIDGET_SCAN_MARGIN;
        for (Renderable r : cs.renderables) {
            if (r instanceof AbstractWidget w) {
                int wx = w.getX();
                int wy = w.getY();
                int ww = w.getWidth();
                int wh = w.getHeight();

                boolean nearX = wx + ww > bgLeft - margin && wx < bgRight + margin;
                boolean nearY = wy + wh > bgTop - margin && wy < bgBottom + margin;

                if (nearX && nearY) {
                    if (wx < minX) minX = wx;
                    if (wy < minY) minY = wy;
                    if (wx + ww > maxX) maxX = wx + ww;
                    if (wy + wh > maxY) maxY = wy + wh;
                }
            }
        }

        return new int[]{maxX - minX, maxY - minY};
    }

    /** 向服务端发送空手交互包以打开目标容器（打开结果由 {@link #openContainerPage} 提交）。 */
    private void interactWithEntry(int index) {
        if (index < 0 || index >= entries.size()) return;
        SelectableEntry entry = entries.get(index);
        switch (entry) {
            case EntityEntry ee -> RtsClientPacketGateway.sendInteractEntityEmptyHand(
                    ee.entityId(), ee.hitLocation(), null, rayOrigin, rayDir);
            case BlockEntry be -> RtsClientPacketGateway.sendInteractEntityEmptyHand(
                    NetworkConstants.NO_ENTITY,
                    be.hitLocation(), be.blockHit(), rayOrigin, rayDir);
        }
    }
}
