package com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import com.rtsbuilding.rtsbuilding.network.NetworkConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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
import java.util.concurrent.ConcurrentHashMap;

import static com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreenConstants.TOP_H;

/**
 * 容器标签面板（原"选择交互目标 + 容器面板"合并后的进一步简化版）：
 * 以网页式多标签页的形式，将框选到的所有容器目标直接展示在标签栏上，
 * 每个容器一个标签，点击即打开对应容器，当前打开的容器标签高亮。
 *
 * <p>交互流程：框选目标后弹出本面板（标签栏列出全部框选容器并自动打开第一个）→
 * 点击其他标签关闭旧容器并打开新容器 → 容器关闭后面板自动关闭。</p>
 */
public final class InteractionPanel extends RtsPanel {

    // ==================== 容器页常量 ====================

    private static final int PANEL_PAD_H = 10;
    private static final int PANEL_PAD_V = 4;
    private static final int WIDGET_SCAN_MARGIN = 50;
    private static final int CONTENT_INSET = 4;
    private static final int DEFAULT_W = 320;
    private static final int DEFAULT_H = 120;
    private static final Component FIXED_TITLE = Component.literal("容器面板");

    private static final int TAB_BAR_H = PageTabBar.TAB_BAR_H;

    // ==================== 状态 ====================

    private final PageTabBar pageTabBar = new PageTabBar();

    private List<SelectableEntry> entries = List.of();
    private Vec3 rayOrigin = Vec3.ZERO;
    private Vec3 rayDir = Vec3.ZERO;

    private boolean containerPageOpen;
    @Nullable
    private ContainerInputForwarder inputForwarder;
    private ItemStack containerIcon = ItemStack.EMPTY;
    @Nullable
    private int[] computedPanelSize;

    /** 当前打开的容器对应的条目 GUI 归一化键（外部打开时为 null）。 */
    @Nullable
    private Object activeContainerId;
    /** 等待服务端打开的条目 GUI 归一化键（点击标签后置位，打开成功或超时后清除）。 */
    @Nullable
    private Object pendingOpenId;
    private int pendingOpenTicks;

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

        int first = firstGuiEntryIndex();
        if (first < 0) return false;

        boolean wasOpen = isOpen();
        setOpen(true);
        if (!wasOpen) {
            positionNearMouse(mouseX, mouseY);
        }
        requestOpenContainer(first);
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
        boolean wasOpen = isOpen();
        this.inputForwarder = new ContainerInputForwarder(containerScreen);
        this.containerIcon = ContainerIconResolver.resolve(containerScreen);
        this.computedPanelSize = null;
        this.containerPageOpen = true;
        this.activeContainerId = pendingOpenId;
        this.pendingOpenId = null;
        this.pendingOpenTicks = 0;
        setOpen(true);
        if (screen == null) return;

        int[] contentBounds = scanContentBounds(containerScreen);
        int naturalW = Math.max(getMinWindowWidth(), contentBounds[0] + PANEL_PAD_H + 2);
        int naturalH = Math.max(getMinWindowHeight(),
                contentBounds[1] + PANEL_PAD_V + TAB_BAR_H + getTitleBarHeight() + 8);

        this.computedPanelSize = new int[]{naturalW, naturalH};
        this.bounds.setDefaults(naturalW, naturalH);

        if (!isResizing()) {
            setWindowWidth(Math.min(Math.max(getWindowWidth(), naturalW), getMaxWindowWidth()));
            setWindowHeight(Math.min(Math.max(getWindowHeight(), naturalH), getMaxWindowHeight()));
        }
        if (!wasOpen) {
            computeDefaultPosition();
        }
        clampWindowToScreen();

        int cw = Math.max(1, getWindowWidth() - 2);
        int ch = Math.max(1, getWindowHeight() - TAB_BAR_H - getTitleBarHeight() - 8);
        inputForwarder.init(cw, ch);
        onBoundsChanged();
        markBroughtToFront();
        if (screen != null) screen.getFloatingWindowLayer().markSortDirty();
    }

    /**
     * 完全关闭面板（向服务端发送容器关闭包并清理全部状态）。
     */
    public void closePanel() {
        if (!isOpen()) return;
        if (containerPageOpen) {
            closeContainerOnServer();
        }
        if (inputForwarder != null) inputForwarder.clear();
        containerPageOpen = false;
        activeContainerId = null;
        pendingOpenId = null;
        pendingOpenTicks = 0;
        entries = List.of();
        setOpen(false);
    }

    /**
     * 仅关闭容器页（向服务端发送关闭包），随后关闭整个面板。
     */
    public void closeContainerPage() {
        if (!containerPageOpen) return;
        closeContainerOnServer();
        if (inputForwarder != null) inputForwarder.clear();
        containerPageOpen = false;
        activeContainerId = null;
        pendingOpenId = null;
        pendingOpenTicks = 0;
        setOpen(false);
    }

    public boolean isContainerPageOpen() {
        return isOpen() && containerPageOpen;
    }

    public List<SelectableEntry> getEntries() {
        return entries;
    }

    /**
     * 容器页作为子覆盖层渲染时置位，供 {@code ScreenRenderBgMixin} 跳过深色背景。
     */
    public static boolean isRenderingOverlay() {
        return renderingOverlay;
    }

    // ==================== 内部工具 ====================

    /**
     * 计算条目的 GUI 归一化键：多方块共用同一个 GUI 的条目（如大箱子的左右两半）
     * 归一化为同一键，用于标签去重与容器匹配；普通条目退化为原标识。
     */
    private static Object guiKey(SelectableEntry entry) {
        return switch (entry) {
            case BlockEntry be -> ContainerGroupResolver.normalize(be.blockPos());
            case EntityEntry ee -> ee.identifier();
        };
    }

    private int firstGuiEntryIndex() {
        for (int i = 0; i < entries.size(); i++) {
            if (hasGuiInteraction(entries.get(i))) return i;
        }
        return -1;
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
        List<PageTabBar.Tab> tabs = new ArrayList<>();
        Map<String, Integer> nameCounts = new HashMap<>();
        Set<Object> seenKeys = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            SelectableEntry entry = entries.get(i);
            if (!hasGuiInteraction(entry)) continue;
            // 多方块共用同一个 GUI 的条目（如大箱子）只生成一个标签
            if (!seenKeys.add(guiKey(entry))) continue;
            String base = entry.displayName();
            int n = nameCounts.merge(base, 1, Integer::sum);
            String label = n == 1 ? base : base + " (" + n + ")";
            tabs.add(new PageTabBar.Tab(containerIconFor(entry), Component.literal(label), i));
        }
        if (containerPageOpen && activeContainerId == null
                && inputForwarder != null && inputForwarder.hasScreen()) {
            tabs.add(new PageTabBar.Tab(containerIcon, FIXED_TITLE, -1));
        }
        return tabs;
    }

    private static ItemStack containerIconFor(SelectableEntry entry) {
        return switch (entry) {
            case BlockEntry be -> be.createStack();
            case EntityEntry ee -> {
                Entity entity = ee.entity();
                yield entity == null ? ItemStack.EMPTY : entity.getPickResult();
            }
        };
    }

    private void handleTabClick(PageTabBar.Tab tab) {
        requestOpenContainer(tab.entryIndex());
    }

    /**
     * 请求打开（或切换到）指定下标的容器条目：
     * 与当前打开的容器相同时仅切换视图；否则先关闭旧容器，再发送交互包等待服务端打开新容器。
     */
    private void requestOpenContainer(int entryIndex) {
        if (entryIndex < 0 || entryIndex >= entries.size()) return;
        SelectableEntry target = entries.get(entryIndex);
        Object targetId = guiKey(target);

        if (containerPageOpen && Objects.equals(activeContainerId, targetId)) {
            applyContainerPageSize();
            return;
        }

        if (containerPageOpen) {
            closeContainerOnServer();
            if (inputForwarder != null) inputForwarder.clear();
        }
        this.pendingOpenId = targetId;
        this.pendingOpenTicks = 0;
        this.activeContainerId = null;
        this.containerPageOpen = true;
        interactWithEntry(entryIndex);
    }

    private void applyContainerPageSize() {
        if (computedPanelSize == null) return;
        setSize(Math.max(getWindowWidth(), computedPanelSize[0]),
                Math.max(getWindowHeight(), computedPanelSize[1]));
    }

    @Nullable
    private PageTabBar.Tab findActiveTab(List<PageTabBar.Tab> tabs) {
        for (PageTabBar.Tab t : tabs) {
            int idx = t.entryIndex();
            if (idx >= 0 && idx < entries.size()
                    && Objects.equals(guiKey(entries.get(idx)), activeContainerId)) {
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
            String msg = pendingOpenId != null ? "正在打开容器…" : "点击上方标签打开容器";
            int tx = cx + Math.max(0, (cw - Minecraft.getInstance().font.width(msg)) / 2);
            int ty = cy + Math.max(8, (TAB_BAR_H + 24) / 2);
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
                PageTabBar.Tab tab = pageTabBar.handleClick(mouseX, mouseY, cx, cy, cw, TAB_BAR_H, buildTabs());
                if (tab != null) handleTabClick(tab);
            }
            return;
        }

        if (button == 0 && inputForwarder != null && inputForwarder.hasScreen()) {
            inputForwarder.mouseClicked(containerLocalX(mouseX), containerLocalY(mouseY), button);
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.open) return false;

        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;

        if (!isOverPageTabBar(mouseY)
                && inputForwarder != null && inputForwarder.hasScreen()) {
            inputForwarder.mouseDragged(containerLocalX(mouseX), containerLocalY(mouseY), button, dragX, dragY);
        }

        if (isInsideWindow(mouseX, mouseY)) return true;
        return button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!this.open) return false;

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (!isOverPageTabBar(mouseY)
                    && inputForwarder != null && inputForwarder.hasScreen()) {
                inputForwarder.mouseReleased(containerLocalX(mouseX), containerLocalY(mouseY), button);
            }
            return isInsideWindow(mouseX, mouseY);
        }

        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        if (!isOverPageTabBar(mouseY)
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
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (containerPageOpen) {
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
        if (containerPageOpen && inputForwarder != null && inputForwarder.hasScreen()) {
            int panelW = Math.max(getMinWindowWidth(), getDefaultWidth());
            int panelH = Math.max(getMinWindowHeight(), getDefaultHeight());
            int cw = Math.max(1, panelW - 2);
            int ch = Math.max(1, panelH - TAB_BAR_H - getTitleBarHeight() - 8);
            inputForwarder.init(cw, ch);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!isOpen()) return;

        Minecraft mc = Minecraft.getInstance();

        // 等待服务端打开新容器：保持容器页视图，直到打开成功或超时（2 秒）
        if (pendingOpenId != null) {
            if (mc.player != null && mc.player.containerMenu.containerId != 0) {
                pendingOpenId = null;
                pendingOpenTicks = 0;
            } else if (++pendingOpenTicks > 40) {
                pendingOpenId = null;
                pendingOpenTicks = 0;
                containerPageOpen = false;
                if (inputForwarder != null) inputForwarder.clear();
                activeContainerId = null;
                setOpen(false);
            }
            return;
        }

        if (containerPageOpen && inputForwarder != null && inputForwarder.hasScreen()) {
            inputForwarder.tick();
            autoGrowIfNeeded();

            if (mc.player != null && mc.player.containerMenu.containerId == 0) {
                inputForwarder.clear();
                containerPageOpen = false;
                activeContainerId = null;
                setOpen(false);
            }
        }
    }

    @Override
    protected void onClose() {
        super.onClose();
        if (containerPageOpen) {
            closeContainerOnServer();
        }
        if (inputForwarder != null) inputForwarder.clear();
        containerPageOpen = false;
        entries = List.of();
        activeContainerId = null;
        pendingOpenId = null;
        pendingOpenTicks = 0;
    }

    private void closeContainerOnServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu.containerId == 0) return;

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
        int neededContentW = contentBounds[0] + PANEL_PAD_H;
        int neededContentH = contentBounds[1] + PANEL_PAD_V;

        int neededPanelW = Math.max(getMinWindowWidth(), neededContentW + 2);
        int neededPanelH = Math.max(getMinWindowHeight(),
                neededContentH + TAB_BAR_H + getTitleBarHeight() + 8);

        if (neededPanelW <= getWindowWidth() && neededPanelH <= getWindowHeight()) return;

        int newW = Math.min(Math.max(getWindowWidth(), neededPanelW), getMaxWindowWidth());
        int newH = Math.min(Math.max(getWindowHeight(), neededPanelH), getMaxWindowHeight());

        if (newW > getWindowWidth() || newH > getWindowHeight()) {
            setWindowWidth(newW);
            setWindowHeight(newH);

            int cw = Math.max(1, newW - 2);
            int ch = Math.max(1, newH - TAB_BAR_H - getTitleBarHeight() - 8);
            inputForwarder.init(cw, ch);

            this.computedPanelSize = new int[]{newW, newH};
            onBoundsChanged();
        }
    }

    @Override
    protected void onBoundsChanged() {
        super.onBoundsChanged();
        if (containerPageOpen && inputForwarder != null) {
            int cw = Math.max(1, getWindowWidth() - 2);
            int ch = Math.max(1, getWindowHeight() - TAB_BAR_H - getTitleBarHeight() - 8);
            inputForwarder.init(cw, ch);
            this.computedPanelSize = new int[]{getWindowWidth(), getWindowHeight()};
        }
    }

    // ==================== 窗口规范 ====================

    @Override
    protected Component getTitle() {
        return FIXED_TITLE;
    }

    @Override
    protected int getDefaultWidth() {
        if (computedPanelSize != null) return computedPanelSize[0];
        if (inputForwarder != null && inputForwarder.hasScreen()) {
            return Math.max(88, inputForwarder.getScreen().getXSize() + 8);
        }
        return DEFAULT_W;
    }

    @Override
    protected int getDefaultHeight() {
        if (computedPanelSize != null) return computedPanelSize[1];
        if (inputForwarder != null && inputForwarder.hasScreen()) {
            var cs = inputForwarder.getScreen();
            return TAB_BAR_H + getTitleBarHeight() + cs.getYSize() + PANEL_PAD_V + 8;
        }
        return DEFAULT_H;
    }

    @Override
    public int getMinWindowWidth() {
        return 88;
    }

    @Override
    public int getMinWindowHeight() {
        return TAB_BAR_H + getTitleBarHeight() + 50;
    }

    @Override
    protected int getMaxWindowHeight() {
        return Integer.MAX_VALUE;
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

        if (entity instanceof AbstractVillager) {
            if (entity instanceof Villager villager) {
                return villager.getVillagerData().getProfession() != VillagerProfession.NONE;
            }
            return true;
        }
        if (entity instanceof AbstractHorse) return true;
        if (entity instanceof ContainerEntity) return true;
        if (entity instanceof MenuProvider) return true;
        return false;
    }

    private static final Map<Class<?>, Boolean> USE_OVERRIDE_CACHE = new ConcurrentHashMap<>();

    private static boolean hasBlockGui(Minecraft mc, BlockPos blockPos) {
        BlockState state = mc.level.getBlockState(blockPos);
        if (state.getMenuProvider(mc.level, blockPos) != null) return true;

        BlockEntity be = mc.level.getBlockEntity(blockPos);
        if (be instanceof MenuProvider) {
            if (be instanceof LecternBlockEntity lectern && lectern.getBook().isEmpty()) return false;
            return true;
        }

        return hasUseOverride(state.getBlock());
    }

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
                        // 继续向上查找
                    }
                }
                current = current.getSuperclass();
            }
            return false;
        });
    }

    private void interactWithEntry(int index) {
        if (index < 0 || index >= entries.size()) return;
        SelectableEntry entry = entries.get(index);
        this.pendingOpenId = entry.identifier();
        this.pendingOpenTicks = 0;
        switch (entry) {
            case EntityEntry ee -> RtsClientPacketGateway.sendInteractEntityEmptyHand(
                    ee.entityId(), ee.hitLocation(), null, rayOrigin, rayDir);
            case BlockEntry be -> RtsClientPacketGateway.sendInteractEntityEmptyHand(
                    NetworkConstants.NO_ENTITY,
                    be.hitLocation(), be.blockHit(), rayOrigin, rayDir);
        }
    }
}
