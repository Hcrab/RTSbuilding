package com.rtsbuilding.rtsbuilding.client.screen.panel.gear;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.util.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
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
    private static final int SCROLLBAR_RIGHT_GAP = 7;
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
        this.cameraModule = kernel.module("camera");
        this.operationSection.setCameraModule(this.cameraModule);
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
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        int ch = contentHeight();

        // 更新滚动范围
        int totalH = totalSectionHeight(cw);
        scrollBar.setContent(totalH, ch);

        int scroll = scrollBar.getScroll();

        // 渲染折叠分区（Y 偏移 -scroll 实现滚动效果）
        int scrolledCy = cy - scroll;
        int sectionY = scrolledCy;
        renderingSection.render(g, mouseX, mouseY, cx, sectionY, cw);
        sectionY += renderingSection.totalHeight(cw);
        personalizationSection.render(g, mouseX, mouseY, cx, sectionY, cw);
        sectionY += personalizationSection.totalHeight(cw);
        operationSection.render(g, mouseX, mouseY, cx, sectionY, cw);

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

            int scroll = scrollBar.getScroll();
            int scrolledCy = cy - scroll;

            int sectionCY = scrolledCy;
            if (renderingSection.handleClick(mouseX, mouseY, cx, sectionCY, cw)) return;
            sectionCY += renderingSection.totalHeight(cw);
            if (personalizationSection.handleClick(mouseX, mouseY, cx, sectionCY, cw)) return;
            sectionCY += personalizationSection.totalHeight(cw);
            operationSection.handleClick(mouseX, mouseY, cx, sectionCY, cw);
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
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollBar.endDrag();
        operationSection.endSliderDrag();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 滑条滚轮优先：鼠标在灵敏度滑条区域时调值而非滚动面板
        if (operationSection.handleSliderScroll(mouseX, mouseY, scrollY)) {
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
        this.windowX = Math.max(8, (this.screen.width - this.windowWidth) / 2);
        this.windowY = Mth.clamp((this.screen.height - this.windowHeight) / 2,
                TOP_H + 6,
                Math.max(TOP_H + 6, this.screen.height - this.windowHeight - 8));
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
                        v -> setOpen(v)),
                // 滚动位置
                PersistableProperty.intField(
                        pk + ".scroll",
                        s -> s.panelScrollOffsets.getOrDefault(pk, 0),
                        (s, v) -> { if (v != 0) s.panelScrollOffsets.put(pk, v); else s.panelScrollOffsets.remove(pk); },
                        () -> scrollBar.getScroll(),
                        v -> scrollBar.setScroll(v)),
                // 渲染设置分区展开状态
                PersistableProperty.boolField(
                        pk + ".renderingExpanded",
                        s -> s.sectionExpandedStates.getOrDefault(pk + ".rendering", false),
                        (s, v) -> { if (v) s.sectionExpandedStates.put(pk + ".rendering", true); else s.sectionExpandedStates.remove(pk + ".rendering"); },
                        () -> renderingSection.isExpanded(),
                        v -> renderingSection.setExpanded(v)),
                // 个性化设置分区展开状态
                PersistableProperty.boolField(
                        pk + ".personalizationExpanded",
                        s -> s.sectionExpandedStates.getOrDefault(pk + ".personalization", false),
                        (s, v) -> { if (v) s.sectionExpandedStates.put(pk + ".personalization", true); else s.sectionExpandedStates.remove(pk + ".personalization"); },
                        () -> personalizationSection.isExpanded(),
                        v -> personalizationSection.setExpanded(v)),
                // 操作设置分区展开状态
                PersistableProperty.boolField(
                        pk + ".operationExpanded",
                        s -> s.sectionExpandedStates.getOrDefault(pk + ".operation", false),
                        (s, v) -> { if (v) s.sectionExpandedStates.put(pk + ".operation", true); else s.sectionExpandedStates.remove(pk + ".operation"); },
                        () -> operationSection.isExpanded(),
                        v -> operationSection.setExpanded(v))
        );
    }

    public CameraModule getCameraModule() {
        return cameraModule;
    }

}
