package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;
import com.rtsbuilding.rtsbuilding.common.persist.BoundsProvider;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.event.UiEventReply;
import com.rtsbuilding.rtsbuilding.uicore.event.UiKeyEvent;
import com.rtsbuilding.rtsbuilding.uicore.event.UiPointerEvent;
import com.rtsbuilding.rtsbuilding.uicore.routing.UiEventTarget;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiEasing;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiFloatAnimation;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiWindowVisibilityAnimation;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base class for movable RTS window panels.
 *
 * <p>The class owns window chrome, bounds, drag/resize state, close handling,
 * and default input swallowing for the window rectangle. It explicitly does not
 * own gameplay state, networking, storage overlay behavior, or camera controls.
 * That separation lets us migrate visible panels one at a time while the current
 * container overlay and legacy input gate continue to work unchanged.
 */
public abstract class RtsWindowPanel implements RtsPanel, BoundsProvider, UiEventTarget {
    private static final AtomicLong NEXT_Z_ORDER = new AtomicLong();
    private static final int DEFAULT_TITLE_BAR_H = 20;
    private static final int DEFAULT_MIN_W = 80;
    private static final int DEFAULT_MIN_H = 60;
    private static final int DEFAULT_RESIZE_BORDER = 5;
    private static final int SCREEN_MARGIN = 4;
    private static final int CLOSE_BUTTON_SIZE = 14;
    private static final int CLOSE_SHEET_W = 450;
    private static final int CLOSE_SHEET_H = 900;
    private static final int CLOSE_STATE_H = 450;
    private static final int SNAP_THRESHOLD = 6;
    private static final ResourceLocation CLOSE_BUTTON_TEXTURE = new ResourceLocation(
            "rtsbuilding", "textures/gui/general/close_button.png");

    protected BuilderScreen screen;
    protected ClientRtsController controller;
    protected int windowX;
    protected int windowY;
    protected int windowWidth;
    protected int windowHeight;
    protected boolean open;
    protected boolean mouseHovering;
    protected boolean draggable = true;
    protected boolean resizable = false;
    protected boolean closable = true;

    private int defaultWidth;
    private int defaultHeight;
    private boolean positionInitialized;
    private long lastClickTime = NEXT_Z_ORDER.incrementAndGet();
    private WindowButton closeButton;
    private boolean boundsDirty;
    private boolean userBoundsPreference;
    private boolean pendingInitialReveal;
    private boolean pendingReveal;
    private final UiFloatAnimation hoverBorderAnimation =
            new UiFloatAnimation(SystemUiClock.INSTANCE, 0.0D);
    private boolean hoverBorderTarget;
    /**
     * 仅负责视觉显隐。逻辑 open 关闭后输入立即失效，当前对象可在这段时间保留最后一帧，
     * 让老版即时模式下的窗口关闭不再突兀。
     */
    private final UiWindowVisibilityAnimation visibilityAnimation =
            new UiWindowVisibilityAnimation(SystemUiClock.INSTANCE, false);

    /** 拖拽、缩放与窗口吸附的短生命周期输入状态，和面板渲染/持久化职责分离。 */
    private final RtsWindowPointerSession pointerSession = new RtsWindowPointerSession();

    /**
     * When set, the render() method skips hover detection so the
     * window frame and content do NOT show hover effects. This is
     * used by {@link RtsFloatingWindowLayer} to suppress hover on
     * windows that are covered by a higher overlapping window.
     */
    private boolean skipHoverDetection;

    public enum ResizeCursor {
        DEFAULT,
        RESIZE_EW,
        RESIZE_NS,
        RESIZE_NWSE,
        RESIZE_NESW
    }

    /**
     * Draws the panel-specific contents inside the window body. The base class
     * has already drawn the frame/title bar and applied the content scissor.
     */
    protected abstract void renderContent(LegacyGuiGraphics g, int mouseX, int mouseY, float partialTick);

    /**
     * Handles a click inside the content area. Returning true consumes the
     * click; returning false still keeps the event inside the window boundary.
     */
    protected abstract void handleContentClick(double mouseX, double mouseY, int button);

    /** Returns the localized title shown in the window title bar. */
    protected abstract ITextComponent getTitle();

    /** Default size used the first time the window opens or when reset. */
    protected abstract int getDefaultWidth();

    /** Default size used the first time the window opens or when reset. */
    protected abstract int getDefaultHeight();

    /** Computes the default position after {@link #windowWidth} is known. */
    protected abstract void computeDefaultPosition();

    // ======================== 可持久化属性 ========================

    /**
     * 返回此面板声明的可持久化属性列表。
     * <p>Manager 的 {@code persistUiState()} 和 {@code applyStoredUiState()} 会遍历此列表，
     * 自动完成运行时状态 ↔ UiState 的双向同步。
     * <p>默认返回空列表。子类可重写以声明需要持久化的属性。
     */
    public List<PersistableProperty> persistableProperties() {
        return Collections.emptyList();
    }

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
        this.defaultWidth = Math.max(getMinWindowWidth(), getDefaultWidth());
        this.defaultHeight = Math.max(getMinWindowHeight(), getDefaultHeight());
        this.closeButton = createCloseButton();
    }

    @Override
    public void render(LegacyGuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!tryStartPendingReveal()) {
            this.mouseHovering = false;
            return;
        }
        if (!shouldRenderWindow()) {
            this.mouseHovering = false;
            return;
        }
        initializePosition();
        clampWindowToScreen();
        this.mouseHovering = this.open && !this.skipHoverDetection && isInsideWindow(mouseX, mouseY);
        updateHoverBorderAnimation(this.mouseHovering);

        double previousAlpha = g.pushAlpha(visibilityAnimation.subtreeTintOpacity());
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, (float) visibilityAnimation.offsetY(), 0.0F);

        // When the window is covered, globally suppress hover effects on all child buttons
        // Must be set before renderWindowFrame because the close button renders there
        if (this.skipHoverDetection) {
            WindowButton.setGlobalSkipHover(true);
        }
        try {
            renderWindowFrame(g, mouseX, mouseY);
            // Flush the window frame first (no scissor) so the border is not clipped
            // by the content scissor that follows.
            // Must be flushed separately from content because the window border lies
            // outside the content clipping region.
            if (shouldClipContent()) {
                enableContentScissor(g);
            }
            renderContent(g, mouseX, mouseY, partialTick);
            // Flush content while scissor is still active, so item icons (renderItem) and
            // text batched vertices are clipped to the content region at rasterisation time,
            // preventing visual bleed-through to adjacent panels.
        } finally {
            if (this.skipHoverDetection) {
                WindowButton.setGlobalSkipHover(false);
            }
            if (shouldClipContent()) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            GlStateManager.popMatrix();
            g.restoreAlpha(previousAlpha);
            visibilityAnimation.finishDismissalIfNeeded(Config.isUiAnimationsEnabled());
        }
    }

    public void render(LegacyGuiGraphics g, int mouseX, int mouseY) {
        render(g, mouseX, mouseY, 0.0F);
    }

    public boolean isOpen() {
        return this.open;
    }

    public boolean isVisibleWindow() {
        return this.open && !this.pendingInitialReveal && !this.pendingReveal && canShowWindow();
    }

    /**
     * 渲染层专用可见性：逻辑关闭的窗口仍可完成短暂淡出，但绝不重新参与命中、焦点或 Escape。
     */
    public boolean shouldRenderWindow() {
        return visibilityAnimation.shouldRender(this.open) && canShowWindow();
    }

    public void setOpen(boolean open) {
        boolean wasOpen = this.open;
        boolean hadPendingReveal = this.pendingInitialReveal || this.pendingReveal;
        if (open && !wasOpen) {
            // 普通手动打开仍然立即揭示；若生命周期尚未准备好，则只排队本次普通揭示。
            this.pendingInitialReveal = false;
            if (isLayoutReady()) {
                initializePosition();
                visibilityAnimation.reveal(Config.isUiAnimationsEnabled());
            } else {
                this.pendingReveal = true;
            }
        }
        this.open = open;
        if (!open) {
            // 首次安全渲染前关闭时，取消待揭示状态，避免下一帧幽灵式重新打开。
            this.pendingInitialReveal = false;
            this.pendingReveal = false;
        }
        if (!open && wasOpen) {
            this.pointerSession.cancel();
            if (!hadPendingReveal) {
                visibilityAnimation.dismiss(Config.isUiAnimationsEnabled());
            }
            onClose();
        }
    }

    /**
     * 记录默认逻辑打开，不读取任何依赖完整屏幕布局的尺寸或位置。
     * 首次视觉揭示由 render 生命周期在布局就绪后消费，保证渐显动画仍然存在。
     */
    protected final void requestInitialOpen() {
        this.open = true;
        this.pendingInitialReveal = true;
        this.pendingReveal = false;
    }

    /** 返回窗口默认定位所需的屏幕和依赖面板是否已经完成绑定。 */
    protected boolean isLayoutReady() {
        return this.screen != null && this.screen.isRtsWindowLayoutReady();
    }

    /**
     * 在首次安全渲染或延迟的普通打开时完成一次定位并启动渐显。
     * 待处理标记只消费一次，因此不会在每帧重复初始化或重算位置。
     */
    private boolean tryStartPendingReveal() {
        if (!this.pendingInitialReveal && !this.pendingReveal) {
            return true;
        }
        if (!this.open) {
            this.pendingInitialReveal = false;
            this.pendingReveal = false;
            return true;
        }
        if (!isLayoutReady()) {
            return false;
        }
        initializePosition();
        this.pendingInitialReveal = false;
        this.pendingReveal = false;
        visibilityAnimation.reveal(Config.isUiAnimationsEnabled());
        return true;
    }

    public void toggleOpen() {
        setOpen(!this.open);
    }

    public int getWindowX() {
        return this.windowX;
    }

    public int getWindowY() {
        return this.windowY;
    }

    public int getWindowWidth() {
        return this.windowWidth;
    }

    public int getWindowHeight() {
        return this.windowHeight;
    }

    public long getLastClickTime() {
        return lastClickTime;
    }

    public void markBroughtToFront() {
        // 单调序号比 nanoTime 更适合做 z-order：不会因时钟粒度产生并列窗口。
        this.lastClickTime = NEXT_Z_ORDER.incrementAndGet();
    }

    public boolean hasInitializedBounds() {
        return this.positionInitialized;
    }

    public boolean hasUserBoundsPreference() {
        return this.userBoundsPreference;
    }

    public void setPosition(int x, int y) {
        ensureSizeInitialized();
        this.windowX = x;
        this.windowY = y;
        this.positionInitialized = true;
        clampWindowToScreen();
        markUserBoundsDirty();
    }

    /**
     * Sets the window position and size in one call, then clamps to screen bounds once.
     * This avoids intermediate clamp side effects from calling {@link #setSize} and
     * {@link #setPosition} separately.
     */
    public void setBounds(int x, int y, int width, int height) {
        this.windowX = x;
        this.windowY = y;
        this.windowWidth = Math.max(getMinWindowWidth(), width);
        this.windowHeight = Math.max(getMinWindowHeight(), height);
        clampWindowSize();
        this.positionInitialized = true;
        clampWindowToScreen();
        markUserBoundsDirty();
    }

    /**
     * Sets bounds for anchored/transient windows without marking them as a user
     * preference. Use this for dropdown-style panels whose position follows a
     * button, not for movable user-arranged windows.
     */
    public void setTransientBounds(int x, int y, int width, int height) {
        this.windowX = x;
        this.windowY = y;
        this.windowWidth = Math.max(getMinWindowWidth(), width);
        this.windowHeight = Math.max(getMinWindowHeight(), height);
        clampWindowSize();
        this.positionInitialized = true;
        clampWindowToScreen();
        this.userBoundsPreference = false;
    }

    public void setSize(int width, int height) {
        ensureSizeInitialized();
        this.windowWidth = width;
        this.windowHeight = height;
        clampWindowSize();
        clampWindowToScreen();
        markUserBoundsDirty();
    }

    public void resetToDefaultBounds() {
        this.windowWidth = this.defaultWidth;
        this.windowHeight = this.defaultHeight;
        clampWindowSize();
        computeDefaultPosition();
        clampWindowToScreen();
        this.positionInitialized = true;
        markUserBoundsDirty();
    }

    public boolean consumeBoundsDirty() {
        boolean dirty = this.boundsDirty;
        this.boundsDirty = false;
        return dirty;
    }

    public boolean isInsideWindow(double mouseX, double mouseY) {
        return mouseX >= this.windowX && mouseX < this.windowX + this.windowWidth
                && mouseY >= this.windowY && mouseY < this.windowY + this.windowHeight;
    }

    /**
     * Suppresses hover detection so the window frame and buttons
     * do not show hover effects during the next render call.
     * Used by {@link RtsFloatingWindowLayer} for covered windows.
     */
    void setSkipHoverDetection(boolean skip) {
        this.skipHoverDetection = skip;
    }

    public boolean isInsideWindowOrResizeBorder(double mouseX, double mouseY) {
        int border = getResizeBorderWidth();
        return mouseX >= this.windowX - border && mouseX < this.windowX + this.windowWidth + border
                && mouseY >= this.windowY - border && mouseY < this.windowY + this.windowHeight + border;
    }

    public boolean isInsideResizableBorder(double mouseX, double mouseY) {
        return currentResizeCursor(mouseX, mouseY) != ResizeCursor.DEFAULT;
    }

    public ResizeCursor currentResizeCursor(double mouseX, double mouseY) {
        if (!this.open || !canShowWindow() || !this.resizable) {
            return ResizeCursor.DEFAULT;
        }
        initializePosition();
        return this.pointerSession.currentResizeCursor(this, (int) mouseX, (int) mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleClick(mouseX, mouseY, button);
    }

    public boolean handleClick(double mouseX, double mouseY, int button) {
        if (!this.open || !canShowWindow()) {
            return false;
        }
        initializePosition();
        if (button == 0) {
            if (this.closable && this.closeButton != null && this.closeButton.mouseClicked(mouseX, mouseY, button)) {
                setOpen(false);
                return true;
            }
            if (isInsideTitleBar(mouseX, mouseY)
                    && handleTitleBarAction(mouseX, mouseY, button)) {
                return true;
            }
            if (this.pointerSession.beginResize(this, mouseX, mouseY)) {
                return true;
            }
            if (this.pointerSession.beginDrag(this, mouseX, mouseY)) {
                return true;
            }
            if (isInsideWindow(mouseX, mouseY)) {
                handleContentClick(mouseX, mouseY, button);
                return true;
            }
        }
        return isInsideWindow(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.pointerSession.drag(this, mouseX, mouseY, button);
    }

    public boolean handleMouseDragged(double mouseX, double mouseY, int button) {
        return mouseDragged(mouseX, mouseY, button, 0.0D, 0.0D);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.pointerSession.release(this, mouseX, mouseY, button);
    }

    public void handleMouseReleased(double mouseX, double mouseY, int button) {
        mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isVisibleWindow() || !isInsideWindow(mouseX, mouseY)) {
            return false;
        }
        handleContentScroll(mouseX, mouseY, scrollX, scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.open) {
            return false;
        }
        if (this.closable && keyCode == Keyboard.KEY_ESCAPE) {
            setOpen(false);
            return true;
        }
        return handleWindowKeyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return this.open && handleWindowCharTyped(codePoint, modifiers);
    }

    @Override
    public UiEventReply handlePointer(UiPointerEvent event) {
        boolean handled;
        switch (event.getType()) {
            case PRESS: handled = mouseClicked(event.getX(), event.getY(), event.getButton()); break;
            case DRAG: handled = mouseDragged(event.getX(), event.getY(), event.getButton(),
                    event.getDeltaX(), event.getDeltaY()); break;
            case RELEASE: handled = mouseReleased(event.getX(), event.getY(), event.getButton()); break;
            case SCROLL: handled = mouseScrolled(event.getX(), event.getY(),
                    event.getDeltaX(), event.getDeltaY()); break;
            case MOVE: default: handled = false; break;
        }
        if (!handled) {
            return UiEventReply.PASS;
        }
        if (event.getType() == UiPointerEvent.Type.PRESS) {
            markBroughtToFront();
            return UiEventReply.CAPTURE_POINTER;
        }
        return UiEventReply.BLOCK_WORLD;
    }

    @Override
    public UiEventReply handleKey(UiKeyEvent event) {
        boolean handled;
        switch (event.getType()) {
            case PRESS: handled = keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers()); break;
            case CHAR_TYPED: handled = charTyped(event.getCharacter(), event.getModifiers()); break;
            case RELEASE: default: handled = false; break;
        }
        return handled ? UiEventReply.BLOCK_WORLD : UiEventReply.PASS;
    }

    @Override
    public boolean handleEscape() {
        if (!isVisibleWindow() || !this.closable) {
            return false;
        }
        setOpen(false);
        return true;
    }

    @Override
    public void close() {
        setOpen(false);
    }

    protected int getTitleBarHeight() {
        return DEFAULT_TITLE_BAR_H;
    }

    protected int getMinWindowWidth() {
        return DEFAULT_MIN_W;
    }

    protected int getMinWindowHeight() {
        return DEFAULT_MIN_H;
    }

    protected int getResizeBorderWidth() {
        return DEFAULT_RESIZE_BORDER;
    }

    protected int getMaxWindowWidth() {
        return this.screen == null
                ? this.windowWidth
                : Math.max(getMinWindowWidth(), this.screen.width - SCREEN_MARGIN * 2);
    }

    protected int getMaxWindowHeight() {
        return this.screen == null
                ? this.windowHeight
                : Math.max(getMinWindowHeight(), this.screen.height - SCREEN_MARGIN * 2);
    }

    protected int getBackgroundColor() {
        return RtsMainlineTheme.WINDOW_BACKGROUND.toArgb();
    }

    protected int getBorderLightColor() {
        return RtsMainlineTheme.WINDOW_BORDER_LIGHT.toArgb();
    }

    protected int getBorderDarkColor() {
        return RtsMainlineTheme.WINDOW_BORDER_DARK.toArgb();
    }

    protected int getHoverBorderLightColor() {
        return RtsMainlineTheme.WINDOW_BORDER_HOVER_LIGHT.toArgb();
    }

    protected int getHoverBorderDarkColor() {
        return RtsMainlineTheme.WINDOW_BORDER_HOVER_DARK.toArgb();
    }

    protected int getTitleBarColor() {
        return RtsMainlineTheme.WINDOW_TITLE.toArgb();
    }

    protected int getTitleTextColor() {
        return RtsMainlineTheme.WINDOW_TITLE_TEXT.toArgb();
    }

    protected boolean canShowWindow() {
        return true;
    }

    /** 子类仅在确实需要阻断全部背景 UI/世界输入时覆写。 */
    protected boolean isModalWindow() {
        return false;
    }

    protected boolean shouldClipContent() {
        return true;
    }

    protected int contentX() {
        return this.windowX + 1;
    }

    protected int contentY() {
        return this.windowY + getTitleBarHeight();
    }

    protected int contentWidth() {
        return Math.max(0, this.windowWidth - 2);
    }

    protected int contentHeight() {
        return Math.max(0, this.windowHeight - getTitleBarHeight() - 1);
    }

    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        return true;
    }

    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        return false;
    }

    private WindowButton createCloseButton() {
        return new WindowButton(0, 0, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE,
                new TextComponentString(""), CLOSE_BUTTON_TEXTURE,
                0, 0,
                CLOSE_SHEET_W, CLOSE_STATE_H,
                CLOSE_STATE_H, CLOSE_STATE_H,
                CLOSE_SHEET_W, CLOSE_SHEET_H,
                button -> setOpen(false));
    }

    protected void onClose() {
    }

    /**
     * 当面板边界发生变化时调用的回调。
     * <p>基类实现会自动触发持久化，确保所有面板的拖拽/缩放结果被保存。
     * 子类如需额外处理应调用 {@code super.onBoundsChanged()}。
     */
    protected void onBoundsChanged() {
        if (this.screen != null) {
            this.screen.persistUiState();
        }
    }

    void markUserBoundsDirty() {
        this.userBoundsPreference = true;
        this.boundsDirty = true;
        onBoundsChanged();
    }

    protected void positionBelow(RtsWindowPanel aboveWindow, int gap) {
        this.windowX = aboveWindow.windowX;
        this.windowY = aboveWindow.windowY + aboveWindow.windowHeight + gap;
        clampWindowToScreen();
    }

    private void renderWindowFrame(LegacyGuiGraphics g, int mouseX, int mouseY) {
        double hoverProgress = Config.isUiAnimationsEnabled()
                ? this.hoverBorderAnimation.value()
                : (this.mouseHovering ? 1.0D : 0.0D);
        int light = UiColor.interpolate(new UiColor(getBorderLightColor()),
                new UiColor(getHoverBorderLightColor()), hoverProgress).toArgb();
        int dark = UiColor.interpolate(new UiColor(getBorderDarkColor()),
                new UiColor(getHoverBorderDarkColor()), hoverProgress).toArgb();
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        UiChromeRenderer.frame(new MinecraftUiCanvas(g, font, this.screen),
                new UiRect(this.windowX, this.windowY, this.windowWidth, this.windowHeight), 1.0D,
                new UiColor(getBackgroundColor()), new UiColor(light), new UiColor(dark));
        int titleH = getTitleBarHeight();
        if (titleH > 0) {
            g.fill(this.windowX + 1, this.windowY + 1, this.windowX + this.windowWidth - 1,
                    this.windowY + titleH, getTitleBarColor());
            String title = font.trimStringToWidth(getTitle().getUnformattedText(),
                    Math.max(8, this.windowWidth - 36));
            g.drawString(font, title, this.windowX + 8,
                    this.windowY + Math.max(1, (titleH - font.FONT_HEIGHT) / 2),
                    getTitleTextColor(), false);
        }
        if (this.closable && this.closeButton != null) {
            this.closeButton.setX(closeButtonX());
            this.closeButton.setY(closeButtonY());
            this.closeButton.render(g, mouseX, mouseY, 0.0F);
        }
        renderTitleBarActions(g, mouseX, mouseY);
    }

    /**
     * 子窗口可在标题栏增加无业务副作用的本地操作，例如主题选择入口；默认不占用拖拽。
     * 该钩子不负责窗口焦点、关闭或输入捕获，仍由父类的统一路由维护。
     */
    protected boolean handleTitleBarAction(double mouseX, double mouseY, int button) {
        return false;
    }

    /** 与 {@link #handleTitleBarAction(double, double, int)} 使用同一命中区域绘制标题栏附加操作。 */
    protected void renderTitleBarActions(LegacyGuiGraphics graphics, int mouseX, int mouseY) {
    }

    /** 只在悬浮目标变化时重定向动画，避免每帧重启动导致边框永远追不上终值。 */
    private void updateHoverBorderAnimation(boolean hovering) {
        if (this.hoverBorderTarget == hovering) {
            return;
        }
        this.hoverBorderTarget = hovering;
        this.hoverBorderAnimation.animateTo(hovering ? 1.0D : 0.0D,
                90L, UiEasing.EASE_OUT_CUBIC);
    }

    private void enableContentScissor(LegacyGuiGraphics g) {
        int x1 = contentX();
        int y1 = contentY();
        int x2 = x1 + contentWidth();
        int y2 = y1 + contentHeight();
        this.screen.enableRtsScissor(g, x1, y1, x2, y2);
    }

    boolean isInsideTitleBar(double mouseX, double mouseY) {
        return mouseX >= this.windowX && mouseX < this.windowX + this.windowWidth
                && mouseY >= this.windowY && mouseY < this.windowY + getTitleBarHeight();
    }

    private void initializePosition() {
        if (!this.positionInitialized && !isLayoutReady()) {
            return;
        }
        if (!this.positionInitialized) {
            initializeDefaultBounds();
        }
    }

    private void initializeDefaultBounds() {
        this.windowWidth = this.defaultWidth;
        this.windowHeight = this.defaultHeight;
        clampWindowSize();
        computeDefaultPosition();
        clampWindowToScreen();
        this.positionInitialized = true;
        this.userBoundsPreference = false;
    }

    private void ensureSizeInitialized() {
        if (this.windowWidth <= 0 || this.windowHeight <= 0) {
            this.windowWidth = this.defaultWidth;
            this.windowHeight = this.defaultHeight;
            clampWindowSize();
        }
    }

    void clampWindowSize() {
        this.windowWidth = MathHelper.clamp(this.windowWidth, getMinWindowWidth(), getMaxWindowWidth());
        this.windowHeight = MathHelper.clamp(this.windowHeight, getMinWindowHeight(), getMaxWindowHeight());
    }

    void clampWindowToScreen() {
        if (this.screen == null) {
            return;
        }
        int maxX = Math.max(SCREEN_MARGIN, this.screen.width - this.windowWidth - SCREEN_MARGIN);
        int maxY = Math.max(SCREEN_MARGIN, this.screen.height - getTitleBarHeight() - SCREEN_MARGIN);
        this.windowX = MathHelper.clamp(this.windowX, SCREEN_MARGIN, maxX);
        this.windowY = MathHelper.clamp(this.windowY, SCREEN_MARGIN, maxY);
    }

    /**
     * Snaps this panel to any nearby open panel's opposite edges if within threshold,
     * with actual overlapping range (not just infinite extension lines).
     *
     * <p>This is a transient drag-time alignment — no permanent relationship is created.
     * Each drag operation is independent; panels do not follow each other after
     * the drag ends. This matches real-world window snapping behavior.
     *
     * <p>Rules:
     * <ul>
     *   <li>Horizontal snap (left↔right, right↔left) requires vertical overlap</li>
     *   <li>Vertical snap (top↔bottom, bottom↔top) requires horizontal overlap</li>
     *   <li>This panel's LEFT edge snaps to another panel's RIGHT edge</li>
     *   <li>This panel's RIGHT edge snaps to another panel's LEFT edge</li>
     *   <li>This panel's TOP edge snaps to another panel's BOTTOM edge</li>
     *   <li>This panel's BOTTOM edge snaps to another panel's TOP edge</li>
     * </ul>
     */
    boolean snapToNearbyPanel() {
        if (this.screen == null) return false;
        RtsFloatingWindowLayer layer = this.screen.getFloatingWindowLayer();
        List<RtsWindowPanel> panels = layer.frontToBackWindows();

        int preSnapX = this.windowX;
        int preSnapY = this.windowY;

        for (RtsWindowPanel other : panels) {
            if (other == this || !other.isOpen()) continue;

            // Hysteresis: once snapped, use a wider threshold to break free.
            // This prevents small slow mouse movements from constantly re-snapping.
            int threshold = SNAP_THRESHOLD;

            boolean verticalOverlap = overlapY(this, other) > 0;
            boolean horizontalOverlap = overlapX(this, other) > 0;

            int oL = other.windowX;
            int oR = other.windowX + other.windowWidth;
            int oT = other.windowY;
            int oB = other.windowY + other.windowHeight;

            // Horizontal: snap opposite edges
            if (verticalOverlap) {
                int mL = this.windowX;
                int mR = this.windowX + this.windowWidth;
                if (Math.abs(mL - oR) < threshold) {
                    this.windowX = oR + 1;
                } else if (Math.abs(mR - oL) < threshold) {
                    this.windowX = oL - this.windowWidth - 1;
                }
            }

            // Vertical: snap opposite edges
            if (horizontalOverlap) {
                int mT = this.windowY;
                int mB = this.windowY + this.windowHeight;
                if (Math.abs(mT - oB) < threshold) {
                    this.windowY = oB + 1;
                } else if (Math.abs(mB - oT) < threshold) {
                    this.windowY = oT - this.windowHeight - 1;
                }
            }
        }
        return this.windowX != preSnapX || this.windowY != preSnapY;
    }

    /** Returns the overlapping pixel count in the Y axis between two panels, or 0 if none. */
    private static int overlapY(RtsWindowPanel a, RtsWindowPanel b) {
        int aTop = a.windowY;
        int aBot = a.windowY + a.windowHeight;
        int bTop = b.windowY;
        int bBot = b.windowY + b.windowHeight;
        return Math.max(0, Math.min(aBot, bBot) - Math.max(aTop, bTop));
    }

    /** Returns the overlapping pixel count in the X axis between two panels, or 0 if none. */
    private static int overlapX(RtsWindowPanel a, RtsWindowPanel b) {
        int aL = a.windowX;
        int aR = a.windowX + a.windowWidth;
        int bL = b.windowX;
        int bR = b.windowX + b.windowWidth;
        return Math.max(0, Math.min(aR, bR) - Math.max(aL, bL));
    }

    private int closeButtonX() {
        return this.windowX + this.windowWidth - CLOSE_BUTTON_SIZE - 3;
    }

    private int closeButtonY() {
        return this.windowY + 3;
    }
}
