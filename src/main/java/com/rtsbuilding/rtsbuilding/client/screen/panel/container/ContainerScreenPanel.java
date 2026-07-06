package com.rtsbuilding.rtsbuilding.client.screen.panel.container;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.mixin.ScreenRenderBgMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.TOP_H;

/**
 * 容器屏幕面板——将原版容器 GUI（箱子、熔炉、村民交易等）包裹为 RTS 浮窗面板。
 *
 * <p>该面板继承了 {@link RtsPanel} 的所有窗口行为（拖拽、缩放、关闭、Z 顺序），
 * 在其内容区域内渲染容器屏幕的内容，并将输入事件转发到容器屏幕。
 * 容器屏幕的坐标系经过偏移映射，使交互正确的在面板内响应。</p>
 *
 * <p>当容器菜单在服务端关闭时（containerId == 0），面板自动关闭。</p>
 *
 * <p>输入转发收归 {@link ContainerInputForwarder}，本类聚焦生命周期与布局计算。</p>
 */
public final class ContainerScreenPanel extends RtsPanel {

    /** 输入转发器——封装容器屏幕的输入事件转发与空安全检查 */
    private final ContainerInputForwarder inputForwarder;

    /** 容器屏幕的标题，同时也是面板的窗口标题 */
    private final Component title;

    /** 缓存的实际面板尺寸 [panelW, panelH]，null 表示使用 fallback */
    @Nullable
    private int[] computedPanelSize;

    /** 渲染容器覆盖层时设为 true，供 {@link ScreenRenderBgMixin} 跳过深色背景绘制 */
    private static volatile boolean renderingOverlay;

    /** 面板内容区到 GUI 内容的水平内边距 */
    private static final int PANEL_PAD_H = 10;
    /** 面板内容区到 GUI 内容的垂直内边距 */
    private static final int PANEL_PAD_V = 4;
    /**
     * 控件扫描容差——距背景边缘此像素内的控件算作 GUI 内容的一部分，
     * 用于扩展面板尺寸使其不被裁剪。超过此距离的控件视为屏幕元素而非 GUI 内容。
     */
    private static final int WIDGET_SCAN_MARGIN = 50;

    public ContainerScreenPanel(AbstractContainerScreen<?> containerScreen) {
        this.inputForwarder = new ContainerInputForwarder(containerScreen);
        this.title = containerScreen.getTitle();
        this.draggable = true;
        this.resizable = true;
        this.closable = true;
    }

    /** 查询当前是否正在渲染容器覆盖层（供 Mixin ScreenRenderBgMixin 使用）。 */
    public static boolean isRenderingOverlay() {
        return renderingOverlay;
    }

    /** 获取被包裹的容器屏幕（可能为 null）。 */
    @Nullable
    public AbstractContainerScreen<?> getContainerScreen() {
        return inputForwarder.getScreen();
    }

    // ======================================================================
    //  生命周期
    // ======================================================================

    @Override
    public void init(BuilderScreen screen) {
        super.init(screen);
        if (!inputForwarder.hasScreen()) return;

        var cs = inputForwarder.getScreen();

        // 第一阶段：用 fallback 尺寸初始化容器屏幕（使 leftPos/topPos 和控件可用）
        int panelW = Math.max(getMinWindowWidth(), getDefaultWidth());
        int panelH = Math.max(getMinWindowHeight(), getDefaultHeight());
        int cw = Math.max(1, panelW - 2);
        int ch = Math.max(1, panelH - getTitleBarHeight() - 8);
        inputForwarder.init(cw, ch);

        // 第二阶段：从控件实际位置测量内容区尺寸
        int[] contentBounds = null;
        if (cs != null) {
            contentBounds = scanContentBounds(cs);
        }
        int cw2 = 0;
        if (contentBounds != null) {
            cw2 = contentBounds[0] + PANEL_PAD_H;
        }
        int ch2 = 0;
        if (contentBounds != null) {
            ch2 = contentBounds[1] + PANEL_PAD_V;
        }

        // 转为面板总尺寸
        int actualW = Math.max(getMinWindowWidth(), cw2 + 2);
        int actualH = Math.max(getMinWindowHeight(), ch2 + getTitleBarHeight() + 8);

        this.computedPanelSize = new int[]{actualW, actualH};
        this.bounds.setDefaults(actualW, actualH);

        int actualCw = Math.max(1, actualW - 2);
        int actualCh = Math.max(1, actualH - getTitleBarHeight() - 8);
        inputForwarder.init(actualCw, actualCh);
    }

    @Override
    public void tick() {
        super.tick();
        if (!inputForwarder.hasScreen() || !isOpen()) return;

        inputForwarder.tick();

        // 动态检测容器内容变化（如合成书展开），必要时自动扩展面板
        autoGrowIfNeeded();

        // 容器菜单已关闭时自动移除覆盖层
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu.containerId == 0) {
            setOpen(false);
        }
    }

    /**
     * 当容器内容超出当前面板大小时自动扩展面板尺寸。
     * <p>典型场景：容器内部有按钮（如合成台合成书），点击后展开新控件，
     * 原始的面板尺寸不再够用。此方法仅在内容溢出时单向增长，不会自动缩小，
     * 避免频繁调整尺寸造成布局抖动。</p>
     */
    private void autoGrowIfNeeded() {
        if (isResizing()) return; // 用户正在手动缩放时不做自动扩展
        var cs = inputForwarder.getScreen();
        if (cs == null) return;

        int[] contentBounds = scanContentBounds(cs);
        int neededContentW = contentBounds[0] + PANEL_PAD_H;
        int neededContentH = contentBounds[1] + PANEL_PAD_V;

        int neededPanelW = Math.max(getMinWindowWidth(), neededContentW + 2);
        int neededPanelH = Math.max(getMinWindowHeight(), neededContentH + getTitleBarHeight() + 8);

        // 仅在内容超出当前大小时扩展，不缩小
        if (neededPanelW <= getWindowWidth() && neededPanelH <= getWindowHeight()) return;

        int newW = Math.min(Math.max(getWindowWidth(), neededPanelW), getMaxWindowWidth());
        int newH = Math.min(Math.max(getWindowHeight(), neededPanelH), getMaxWindowHeight());

        if (newW > getWindowWidth() || newH > getWindowHeight()) {
            // 直接设置新尺寸
            setWindowWidth(newW);
            setWindowHeight(newH);
            // 不钳制位置——自动扩展时不移位，避免用户操作区域偏移
            int cw = Math.max(1, newW - 2);
            int ch = Math.max(1, newH - getTitleBarHeight() - 8);
            inputForwarder.init(cw, ch);
            // 同步 computedPanelSize，使默认尺寸记住扩展后的值
            this.computedPanelSize = new int[]{newW, newH};
            // 触发持久化，但标记为 transient 防止覆写用户偏好的手动尺寸
            bounds.markDirtyTransient();
            onBoundsChanged();
        }
    }

    @Override
    protected void onClose() {
        super.onClose();
        inputForwarder.clear(); // 调用 removed() + 置空
        closeContainerOnServer();
    }

    /** 向服务端发送容器关闭包，绕过 NeoForge 的 Player.closeContainer() 补丁。 */
    private void closeContainerOnServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu.containerId == 0) return;

        // 不调 mc.player.closeContainer()——NeoForge 给 Player.closeContainer() 打了补丁，
        // 在集成服务器（单机）上 MemoryConnection 会同步处理包，
        // 补丁逻辑可能触发 mc.setScreen(null) 导致 BuilderScreen 被关闭。
        // 改为手动发送关闭包 + 重置 containerMenu，绕过 NeoForge 的补丁。
        int containerId = mc.player.containerMenu.containerId;
        if (mc.player instanceof LocalPlayer localPlayer) {
            localPlayer.connection.send(new ServerboundContainerClosePacket(containerId));
        }
        mc.player.containerMenu = mc.player.inventoryMenu;
    }

    @Override
    protected void onBoundsChanged() {
        super.onBoundsChanged();
        // 尺寸变化时更新容器屏幕初始化尺寸，使内容区域正确适配新面板大小
        int cw = Math.max(1, getWindowWidth() - 2);
        int ch = Math.max(1, getWindowHeight() - getTitleBarHeight() - 8);
        inputForwarder.init(cw, ch);
        // 同步 computedPanelSize，防止 refreshBoundsIfNeeded 回退面板尺寸
        this.computedPanelSize = new int[]{getWindowWidth(), getWindowHeight()};
    }

    // ======================================================================
    //  渲染
    // ======================================================================

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var cs = inputForwarder.getScreen();
        if (cs == null) return;

        int cx = contentX();
        int cy = contentY();

        g.pose().pushPose();
        try {
            g.pose().translate(cx, cy, 0);
            renderingOverlay = true;
            try {
                // 传入相对面板内容区的鼠标坐标，使容器屏幕的命中检测与渲染位置对齐
                cs.render(g, mouseX - cx, mouseY - cy, partialTick);
            } finally {
                renderingOverlay = false;
            }
        } finally {
            g.pose().popPose();
        }

        // ★ 渲染后清空深度缓冲，防止上层面板渲染时被当前面板的物品轮廓裁剪
        RenderSystem.clear(256, false); // GL_DEPTH_BUFFER_BIT
    }

    // ======================================================================
    //  输入事件转发
    //
    //  注意：非左键事件（右键/中键）转发到容器屏幕后不吞没，
    //  使摄像机拖拽等操作能穿透面板继续响应。
    // ======================================================================

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        inputForwarder.mouseClicked(mouseX - contentX(), mouseY - contentY(), button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 非左键（右键/中键）：转发到容器屏幕处理（如分拆物品堆），
        // 但在面板窗口内时吞没事件，阻止传递到 CameraInputLayer 触发摄像机操控
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && isInsideWindow(mouseX, mouseY)) {
            inputForwarder.mouseClicked(mouseX - contentX(), mouseY - contentY(), button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 如果当前正在拖拽标题栏或缩放边缘，由基类处理
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;

        // 转发到容器屏幕的拖拽（如滚轮滑条、物品堆分拆）
        inputForwarder.mouseDragged(mouseX - contentX(), mouseY - contentY(), button, dragX, dragY);

        // 在面板窗口内时吞没所有按钮事件，阻止传递到 CameraInputLayer 触发摄像机操控
        if (isInsideWindow(mouseX, mouseY)) return true;
        return button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // 非左键：转发到容器屏幕处理，但在面板窗口内时吞没事件
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            inputForwarder.mouseReleased(mouseX - contentX(), mouseY - contentY(), button);
            return isInsideWindow(mouseX, mouseY);
        }
        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        inputForwarder.mouseReleased(mouseX - contentX(), mouseY - contentY(), button);
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!inputForwarder.hasScreen() || !isInsideWindow(mouseX, mouseY)) return false;
        // 转发到容器屏幕处理（如滑条滚动），
        // 无论容器是否消费都返回 true，防止滚轮穿透到 CameraInputLayer 触发摄像机变焦
        inputForwarder.mouseScrolled(mouseX - contentX(), mouseY - contentY(), scrollX, scrollY);
        return true;
    }

    @Override
    public boolean mouseMoved(double mouseX, double mouseY) {
        if (!inputForwarder.hasScreen() || !isInsideWindow(mouseX, mouseY)) return false;
        inputForwarder.mouseMoved(mouseX - contentX(), mouseY - contentY());
        return false;
    }

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        // 不转发 ESC 给容器屏幕——让浮窗层接管 ESC 关闭面板逻辑。
        // 否则容器屏幕吃掉 ESC 返回 true，面板不会关闭，
        // 导致菜单已关但面板还开着、容器屏幕多渲染一帧的闪烁问题。
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return false;
        return inputForwarder.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        return inputForwarder.charTyped(codePoint, modifiers);
    }

    // ======================================================================
    //  面板尺寸
    // ======================================================================

    @Override
    protected Component getTitle() {
        return title;
    }

    @Override
    protected int getDefaultWidth() {
        if (computedPanelSize != null) return computedPanelSize[0];
        var cs = inputForwarder.getScreen();
        if (cs != null) {
            return Math.max(88, cs.getXSize() + 8);
        }
        return 184;
    }

    @Override
    protected int getDefaultHeight() {
        if (computedPanelSize != null) return computedPanelSize[1];
        var cs = inputForwarder.getScreen();
        if (cs != null) {
            return getTitleBarHeight() + cs.getYSize() + PANEL_PAD_V + 8;
        }
        return 200;
    }

    @Override
    public int getMinWindowWidth() {
        return 88;
    }

    @Override
    public int getMinWindowHeight() {
        return getTitleBarHeight() + 50;
    }

    @Override
    protected int getMaxWindowHeight() {
        // 容器面板不限制最大高度——某些 GUI（如村民交易、模组大界面）
        // 的 ySize 可能超过屏幕高度，限制高度会导致内容被裁剪。
        return Integer.MAX_VALUE;
    }

    @Override
    public void clampWindowToScreen() {
        if (this.screen == null) return;
        int maxX = Math.max(0, this.screen.width - bounds.getWidth());
        bounds.setX(Mth.clamp(bounds.getX(), 0, maxX));

        if (bounds.getHeight() > this.screen.height) {
            // 超大面板：允许 Y 为负值，用户可以拖拽看到底部内容
            int minY = this.screen.height - bounds.getHeight(); // 面板底部对齐屏幕底部
            int maxY = 0;                                        // 面板顶部对齐屏幕顶部
            bounds.setY(Mth.clamp(bounds.getY(), minY, maxY));
        } else {
            int maxY = Math.max(0, this.screen.height - getTitleBarHeight());
            bounds.setY(Mth.clamp(bounds.getY(), 0, maxY));
        }
    }

    /**
     * 从控件的实际位置重新计算面板尺寸（用于 tick 中的动态刷新）。
     * 逻辑与 {@link #scanContentBounds} 一致。
     */
    private int[] computePanelSizeFromContent(AbstractContainerScreen<?> cs) {
        int[] contentBounds = scanContentBounds(cs);
        int cw = contentBounds[0] + PANEL_PAD_H;
        int ch = contentBounds[1] + PANEL_PAD_V;
        return new int[]{cw + 2, ch + getTitleBarHeight() + 8};
    }

    /**
     * 扫描容器屏幕实际可视内容的完整边界（背景 + 附近控件）。
     * <p>以 GUI 背景贴图为基准，只考虑距背景 {@link #WIDGET_SCAN_MARGIN}px 范围内的控件，
     * 确保面板不被裁剪的同时避免远处屏幕元素（如角落按钮）干扰。</p>
     *
     * @return [内容区宽度, 内容区高度]
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

                // 控件与背景有交集或紧邻（±margin 内）→ 属于 GUI 内容
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

    @Override
    protected boolean shouldClipContent() {
        // 容器面板不启用裁剪——面板尺寸可能超过屏幕边界（高GUI），
        // 裁剪会导致内容溢出边界时被截断。
        return false;
    }

    @Override
    protected void computeDefaultPosition() {
        if (screen == null) return;
        setWindowX(Math.max(8, (screen.width - getWindowWidth()) / 2));
        if (getWindowHeight() > screen.height) {
            // 超大面板靠顶部对齐，确保标题栏可见
            setWindowY(TOP_H + 6);
        } else {
            setWindowY(Mth.clamp((screen.height - getWindowHeight()) / 2,
                    TOP_H + 6,
                    Math.max(TOP_H + 6, screen.height - getWindowHeight() - 8)));
        }
    }
}
