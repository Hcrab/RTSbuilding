package com.rtsbuilding.rtsbuilding.client.screen.panel.gear;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.render.pass.BoundaryPass;
import com.rtsbuilding.rtsbuilding.client.render.pass.BoxSelectionPass;
import com.rtsbuilding.rtsbuilding.client.render.pass.InteractionTargetPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.util.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.animate.FloatAnimation;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * 设置面板——RTS Builder 的设置窗口。
 *
 * <p>窗口边框、关闭按钮、拖拽/缩放行为和 Z 顺序由 {@link RtsPanel} 基类管理。</p>
 */
public final class GearMenuPanel extends RtsPanel {
    private static final int LEGACY_DEFAULT_WINDOW_W = 200;
    private static final int LEGACY_DEFAULT_WINDOW_H = 284;
    private static final int DEFAULT_WINDOW_W = 253;
    private static final int MIN_WINDOW_W = 187;
    /** 滚动条与内容区右边缘的间距 */
    private static final int SCROLLBAR_RIGHT_GAP = 11;
    /** 内容区宽度缩减量，为滑条滑块留足空间 */
    private static final int CONTENT_WIDTH_REDUCTION = 6;
    /** 第一个分区头部距内容区顶部的偏移，totalSectionHeight 需包含此值 */
    private static final int CONTENT_TOP_PAD = 8;
    private CameraModule cameraModule = null;
    private final RenderingSection renderingSection = new RenderingSection();
    private final PersonalizationSection personalizationSection = new PersonalizationSection();
    private final OperationSection operationSection = new OperationSection();

    /** 垂直滚动条 */
    private final ScrollBar scrollBar = new ScrollBar();

    @Override
    public void init(BuilderScreen screen) {
        super.init(screen);
        this.resizable = true;
        RtsClientKernel kernel = RtsClientKernel.get();
        this.cameraModule = kernel.module(CameraModule.class);
        this.operationSection.setCameraModule(this.cameraModule);
        this.renderingSection.setColorPickerPanel(
                ((BuilderScreen) screen).getColorPickerPanel());
        this.renderingSection.setColorPickerButtonParent(this);
    }

    public void open() {
        setOpen(true);
        markBroughtToFront();
    }

    // ======================== 滚动计算 ========================

    /**
     * 所有折叠分区展开时的内容总高度（不含面板标题栏和边框）。
     */
    private int totalSectionHeight(int cw) {
        return CONTENT_TOP_PAD
                + renderingSection.totalHeight(cw)
                + personalizationSection.totalHeight(cw)
                + operationSection.totalHeight(cw);
    }

    // ======================== 渲染 ========================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // 在所有折叠条和 scissor 结束后渲染颜色浮窗，避免被其他分区遮挡
        renderingSection.renderColorTooltips(g, mouseX, mouseY);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        int ch = contentHeight();

        // 更新滚动范围
        int totalH = totalSectionHeight(cw);
        scrollBar.setContent(totalH, ch);

        int scroll = scrollBar.getScroll();

        // 内容宽度统一缩减，为滑条滑块留足空间
        int sectionRenderW = cw - CONTENT_WIDTH_REDUCTION;

        // 渲染折叠分区（Y 偏移 -scroll 实现滚动效果）
        int scrolledCy = cy - scroll;
        int sectionY = scrolledCy;
        renderingSection.render(g, mouseX, mouseY, cx, sectionY, sectionRenderW);
        sectionY += renderingSection.totalHeight(cw);
        personalizationSection.render(g, mouseX, mouseY, cx, sectionY, sectionRenderW);
        sectionY += personalizationSection.totalHeight(cw);
        operationSection.render(g, mouseX, mouseY, cx, sectionY, sectionRenderW);

        // 渲染滚动条（右侧）
        if (scrollBar.isVisible()) {
            int barX = cx + cw - SCROLLBAR_RIGHT_GAP;
            scrollBar.render(g, barX, cy, ch);
        }
    }

    // ======================== 交互 ========================

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int cx = contentX();
            int cy = contentY();
            int cw = contentWidth();
            int ch = contentHeight();

            // 滚动条点击优先处理
            if (scrollBar.isVisible()) {
                int barX = cx + cw - SCROLLBAR_RIGHT_GAP;
                if (scrollBar.handleClick(mouseX, mouseY, barX, cy, ch)) {
                    return;
                }
            }

            // 点击检测宽度与渲染宽度保持一致
            int sectionClickW = cw - CONTENT_WIDTH_REDUCTION;

            int scroll = scrollBar.getScroll();
            int scrolledCy = cy - scroll;

            int sectionCY = scrolledCy;
            if (renderingSection.handleClick(mouseX, mouseY, cx, sectionCY, sectionClickW)) return;
            sectionCY += renderingSection.totalHeight(cw);
            if (personalizationSection.handleClick(mouseX, mouseY, cx, sectionCY, sectionClickW)) return;
            sectionCY += personalizationSection.totalHeight(cw);
            operationSection.handleClick(mouseX, mouseY, cx, sectionCY, sectionClickW);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            // 滚动条拖拽
            if (scrollBar.isDragging()) {
                int cy = contentY();
                int ch = contentHeight();
                scrollBar.handleDrag(mouseY, cy, ch);
                return true;
            }
            // 灵敏度滑条拖拽
            if (operationSection.isSliderDragging()) {
                operationSection.handleSliderDrag(mouseX);
                return true;
            }
            // 透明度滑条拖拽
            if (renderingSection.isSliderDragging()) {
                renderingSection.handleSliderDrag(mouseX);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollBar.endDrag();
        operationSection.endSliderDrag();
        renderingSection.endSliderDrag();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 滑条滚轮优先：鼠标在灵敏度滑条区域时调值而非滚动面板
        if (operationSection.handleSliderScroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        // 透明度滑条滚轮
        if (renderingSection.handleSliderScroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        return scrollBar.handleScroll(scrollY);
    }

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.settings.title");
    }

    @Override
    protected int getDefaultWidth() {
        return DEFAULT_WINDOW_W;
    }

    @Override
    protected int getDefaultHeight() {
        return GEAR_MENU_H;
    }

    @Override
    public int getMinWindowWidth() {
        return MIN_WINDOW_W;
    }

    @Override
    public int getMinWindowHeight() {
        return GEAR_MENU_MIN_H;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean legacyDefaultBounds = width == LEGACY_DEFAULT_WINDOW_W && height == LEGACY_DEFAULT_WINDOW_H;
        int restoredWidth = legacyDefaultBounds ? DEFAULT_WINDOW_W : width;
        int restoredHeight = legacyDefaultBounds ? GEAR_MENU_H : height;
        super.setBounds(x, y, restoredWidth, restoredHeight);
    }

    @Override
    protected int getMaxWindowWidth() {
        if (this.screen == null) {
            return super.getMaxWindowWidth();
        }
        int viewportLimit = Math.max(getMinWindowWidth(), (this.screen.width * 2) / 3);
        return Math.min(super.getMaxWindowWidth(), viewportLimit);
    }

    @Override
    protected int getMaxWindowHeight() {
        if (this.screen == null) {
            return super.getMaxWindowHeight();
        }
        int viewportLimit = Math.max(getMinWindowHeight(), (this.screen.height * 2) / 3);
        return Math.min(super.getMaxWindowHeight(), viewportLimit);
    }

    @Override
    protected void computeDefaultPosition() {
        setWindowX(Math.max(8, (this.screen.width - getWindowWidth()) / 2));
        setWindowY(Mth.clamp((this.screen.height - getWindowHeight()) / 2,
                TOP_H + 6,
                Math.max(TOP_H + 6, this.screen.height - getWindowHeight() - 8)));
    }

    // ======================== 持久化属性 ========================

    @Override
    public List<PersistableProperty> persistableProperties() {
        String pk = "gearMenu";
        return List.of(
                // 窗口边界（位置+大小）
                PersistableProperty.bounds(pk, this),
                // 面板打开/关闭状态
                PersistableProperty.boolField(
                        pk + ".open",
                        s -> s.panelOpenStates.getOrDefault(pk, false),
                        (s, v) -> { if (v) s.panelOpenStates.put(pk, true); else s.panelOpenStates.remove(pk); },
                        this::isOpen,
                        this::setOpen),
                // 滚动位置
                PersistableProperty.intField(
                        pk + ".scroll",
                        s -> s.panelScrollOffsets.getOrDefault(pk, 0),
                        (s, v) -> { if (v != 0) s.panelScrollOffsets.put(pk, v); else s.panelScrollOffsets.remove(pk); },
                        scrollBar::getScroll,
                        scrollBar::setScroll),
                // 渲染设置分区展开状态
                PersistableProperty.boolField(
                        pk + ".renderingExpanded",
                        s -> s.sectionExpandedStates.getOrDefault(pk + ".rendering", false),
                        (s, v) -> { if (v) s.sectionExpandedStates.put(pk + ".rendering", true); else s.sectionExpandedStates.remove(pk + ".rendering"); },
                        renderingSection::isExpanded,
                        renderingSection::setExpanded),
                // 个性化设置分区展开状态
                PersistableProperty.boolField(
                        pk + ".personalizationExpanded",
                        s -> s.sectionExpandedStates.getOrDefault(pk + ".personalization", false),
                        (s, v) -> { if (v) s.sectionExpandedStates.put(pk + ".personalization", true); else s.sectionExpandedStates.remove(pk + ".personalization"); },
                        personalizationSection::isExpanded,
                        personalizationSection::setExpanded),
                // 操作设置分区展开状态
                PersistableProperty.boolField(
                        pk + ".operationExpanded",
                        s -> s.sectionExpandedStates.getOrDefault(pk + ".operation", false),
                        (s, v) -> { if (v) s.sectionExpandedStates.put(pk + ".operation", true); else s.sectionExpandedStates.remove(pk + ".operation"); },
                        operationSection::isExpanded,
                        operationSection::setExpanded),
                // ===== 渲染设置选项 =====
                // 流动动画
                PersistableProperty.boolField(
                        pk + ".flowAnimation",
                        s -> s.settings.flowAnimationEnabled,
                        (s, v) -> s.settings.flowAnimationEnabled = v,
                        () -> BoxSelectionPass.flowAnimationEnabled,
                        v -> BoxSelectionPass.flowAnimationEnabled = v),
                // 平滑追踪动画
                PersistableProperty.boolField(
                        pk + ".smoothAnimation",
                        s -> s.settings.smoothAnimationEnabled,
                        (s, v) -> s.settings.smoothAnimationEnabled = v,
                        () -> CornerBracketRenderer.SmoothTarget.enabled,
                        v -> CornerBracketRenderer.SmoothTarget.enabled = v),
                // UI 平滑动画
                PersistableProperty.boolField(
                        pk + ".uiSmoothAnimation",
                        s -> s.settings.uiSmoothAnimationEnabled,
                        (s, v) -> s.settings.uiSmoothAnimationEnabled = v,
                        FloatAnimation::isEnabled,
                        FloatAnimation::setEnabled),
                // 深度测试
                PersistableProperty.boolField(
                        pk + ".depthTest",
                        s -> s.settings.depthTestEnabled,
                        (s, v) -> s.settings.depthTestEnabled = v,
                        () -> BoxSelectionPass.depthTestEnabled,
                        v -> BoxSelectionPass.depthTestEnabled = v),
                // 线框透明度
                PersistableProperty.doubleField(
                        pk + ".noDepthAlpha",
                        s -> s.settings.noDepthAlpha,
                        (s, v) -> s.settings.noDepthAlpha = v,
                        () -> (double) CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA,
                        v -> CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA = v.floatValue()),
                // 屏障颜色
                PersistableProperty.intField(
                        pk + ".barrierColor",
                        s -> s.settings.barrierColor,
                        (s, v) -> s.settings.barrierColor = v,
                        () -> BoundaryPass.barrierColor,
                        v -> BoundaryPass.barrierColor = v),
                // 方块目标颜色
                PersistableProperty.intField(
                        pk + ".blockTargetColor",
                        s -> s.settings.blockTargetColor,
                        (s, v) -> s.settings.blockTargetColor = v,
                        () -> InteractionTargetPass.blockTargetColor,
                        v -> InteractionTargetPass.blockTargetColor = v),
                // 实体目标颜色
                PersistableProperty.intField(
                        pk + ".entityTargetColor",
                        s -> s.settings.entityTargetColor,
                        (s, v) -> s.settings.entityTargetColor = v,
                        () -> InteractionTargetPass.entityTargetColor,
                        v -> InteractionTargetPass.entityTargetColor = v),
                // 框选线框颜色
                PersistableProperty.intField(
                        pk + ".selectionColor",
                        s -> s.settings.selectionColor,
                        (s, v) -> s.settings.selectionColor = v,
                        () -> BoxSelectionPass.selectionColor,
                        v -> BoxSelectionPass.selectionColor = v),
                // 框选覆盖层颜色
                PersistableProperty.intField(
                        pk + ".previewOverlayColor",
                        s -> s.settings.previewOverlayColor,
                        (s, v) -> s.settings.previewOverlayColor = v,
                        () -> BoxSelectionPass.previewOverlayColor,
                        v -> BoxSelectionPass.previewOverlayColor = v),
                // 框选虚线间隙颜色
                PersistableProperty.intField(
                        pk + ".selectionGapColor",
                        s -> s.settings.selectionGapColor,
                        (s, v) -> s.settings.selectionGapColor = v,
                        () -> BoxSelectionPass.selectionGapColor,
                        v -> BoxSelectionPass.selectionGapColor = v),
                // 框选实体角支架颜色
                PersistableProperty.intField(
                        pk + ".entitySelectionColor",
                        s -> s.settings.entitySelectionColor,
                        (s, v) -> s.settings.entitySelectionColor = v,
                        () -> BoxSelectionPass.entitySelectionColor,
                        v -> BoxSelectionPass.entitySelectionColor = v)
        );
    }

    public CameraModule getCameraModule() {
        return cameraModule;
    }

}
