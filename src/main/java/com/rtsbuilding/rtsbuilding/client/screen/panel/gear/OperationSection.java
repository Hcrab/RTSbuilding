package com.rtsbuilding.rtsbuilding.client.screen.panel.gear;

import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.util.SettingsSection;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.ScaleSliderComponent;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/**
 * 操作设置折叠分区——在设置面板中管理"操作设置"分区的渲染和交互。
 *
 * <p>目前包含：</p>
 * <ul>
 *   <li>相机输入灵敏度滑条（使用 {@link ScaleSliderComponent}，范围 0.1 ~ 2.0）</li>
 * </ul>
 *
 * <p>布局说明：内容区域共 2 行空行（34px 高），标签与滑条在同一行水平布局，
 * 标签左对齐，滑条紧贴标签右侧。
 * 滑条位置在渲染时缓存到 {@link #sliderTrackX} / {@link #sliderTrackY} / {@link #sliderTrackW}，
 * 点击/拖拽/滚轮检测直接复用缓存值，确保渲染与交互坐标绝对一致。</p>
 */
public class OperationSection extends SettingsSection {

    // ======================== 灵敏度常量 ========================

    private static final double SENS_MIN = 0.1;
    private static final double SENS_MAX = 2.0;

    // ======================== 组件 ========================

    private final ScaleSliderComponent slider = new ScaleSliderComponent();

    /** 相机模块引用，由 {@link #setCameraModule} 注入 */
    @Nullable
    private CameraModule cameraModule;

    /** 最近渲染时滑条轨道的屏幕位置缓存（供点击/拖拽复用） */
    private int sliderTrackX, sliderTrackY, sliderTrackW;

    // ======================== 构造 ========================

    public OperationSection() {
        super("screen.rtsbuilding.settings.category.controls");
    }

    // ======================== 依赖注入 ========================

    /** 设置相机模块引用，必须在首次渲染前调用。 */
    public void setCameraModule(@Nullable CameraModule module) {
        this.cameraModule = module;
    }

    // ======================== 内容行 ========================

    /**
     * 返回内容行数组。
     * <p>此处返回 2 个空字符串，仅用于通过父类布局机制控制内容区域的高度。
     * 实际渲染内容（灵敏度标签+滑条）使用坐标定位，不依赖行文本。</p>
     *
     * @return 2 个空字符串，表示 2 行空白，总高度 = 2 × 14 + 6 = 34px
     */
    @Override
    protected String[] getContentLines() {
        return new String[] { "", "" };
    }

    // ======================== 渲染 ========================

    /**
     * 渲染分区内容——灵敏度标签和滑条。
     * <p>布局方式：</p>
     * <ol>
     *   <li>灵敏度标签左对齐，垂直居中于第一行</li>
     *   <li>灵敏度滑条紧贴标签右侧，同一行水平排列</li>
     * </ol>
     *
     * @param g      渲染上下文
     * @param mouseX 鼠标 X 坐标（传递给滑条用于悬浮检测）
     * @param mouseY 鼠标 Y 坐标
     * @param x      内容区域左上角 X（不含左 padding）
     * @param y      内容区域左上角 Y（含标题栏偏移）
     * @param w      内容区域宽度
     * @param lines  内容行数组（此处仅取长度计算内容区域高度）
     */
    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, String[] lines) {
        // 根据行数计算内容区域总高度：行数 × 行高 + 底部间距
        int contentH = lines.length * getLineHeight() + 6;
        double sens = getSensitivity();

        // ======================== 灵敏度标签（左对齐）=======================
        String labelText = buildSensitivityLabel(sens);
        renderSensitivityLabel(g, labelText, x, y, contentH);

        // ======================== 灵敏度滑条（紧贴标签右侧）=======================
        renderSlider(g, mouseX, mouseY, labelText, x, y, w, contentH);
    }

    /**
     * 拼接灵敏度标签文本，格式如 "输入灵敏度：x1.00"。
     *
     * @param sens 当前灵敏度值
     * @return 完整的标签字符串
     */
    private String buildSensitivityLabel(double sens) {
        String label = Component.translatable("screen.rtsbuilding.settings.sensitivity").getString();
        String value = String.format(java.util.Locale.ROOT, "：x%.2f", sens);
        return label + value;
    }

    /**
     * 渲染灵敏度标签。
     * <p>标签文本左对齐，垂直居中于第一行，在左边缘向右偏移 6px 处绘制。</p>
     *
     * @param g          渲染上下文
     * @param labelText  完整的标签文本
     * @param x          内容区域左上角 X
     * @param y          内容区域左上角 Y
     * @param contentH   内容区域总高度（用于垂直居中定位）
     */
    private void renderSensitivityLabel(GuiGraphics g, String labelText, int x, int y, int contentH) {
        // 标签位于第一行顶部（偏移 4px），文字在行内下沉 2px 对齐基线
        int firstLineY = y + 4;
        RtsClientUiUtil.drawUiText(g, labelText, x + 6, firstLineY + 2, getTextColor());
    }

    /**
     * 渲染灵敏度滑条并缓存其位置。
     * <p>滑条紧贴标签文本右侧，在标签文字结束位置向右偏移 8px 处开始，
     * 垂直方向上居中于第一行，宽度占满剩余可用空间（保底 20px）。
     * 渲染完成后将滑条轨道坐标缓存到 {@link #sliderTrackX} / {@link #sliderTrackY} / {@link #sliderTrackW}，
     * 供点击/拖拽/滚轮直接使用。</p>
     *
     * @param g          渲染上下文
     * @param mouseX     鼠标 X 坐标（传递给滑条用于悬浮检测）
     * @param mouseY     鼠标 Y 坐标
     * @param labelText  完整标签文本（用于测量文字宽度以确定滑条起始位置）
     * @param x          内容区域左上角 X
     * @param y          内容区域左上角 Y
     * @param w          内容区域宽度
     * @param contentH   内容区域总高度（用于垂直居中定位）
     */
    private void renderSlider(GuiGraphics g, int mouseX, int mouseY, String labelText, int x, int y, int w, int contentH) {
        int textWidth = Minecraft.getInstance().font.width(labelText);
        int gap = 8;

        // 滑条起始 X = 左边缘 + 标签宽度 + 间距
        sliderTrackX = x + 6 + textWidth + gap;
        // 滑条 Y = 第一行垂直居中 - 轨道半高（TRACK_SRC_H = 4）
        int lineCenterY = y + 4 + getLineHeight() / 2;
        sliderTrackY = lineCenterY - 2;
        // 滑条宽度 = 总可用宽度 - 标签占用宽度 - 间距，保底 20px
        sliderTrackW = Mth.clamp(w - 12 - textWidth - gap, 20, w - 12);

        slider.render(g, mouseX, mouseY, sliderTrackX, sliderTrackY, sliderTrackW,
                SENS_MIN, SENS_MAX, getSensitivity());
    }

    // ======================== 点击 ========================

    @Override
    protected boolean onContentLineClick(int lineIndex, double mouseX, double mouseY,
                                         int contentX, int contentY, int contentW) {
        Double newVal = slider.handleClick(mouseX, mouseY,
                sliderTrackX, sliderTrackY, sliderTrackW, SENS_MIN, SENS_MAX);
        if (newVal != null) {
            setSensitivity(newVal);
            return true;
        }
        return false;
    }

    // ======================== 拖拽（由 GearMenuPanel 委托调用） ========================

    /** 滑条是否正在被拖拽 */
    public boolean isSliderDragging() {
        return slider.isDragging();
    }

    /** 处理滑条拖拽 */
    public void handleSliderDrag(double mouseX) {
        if (slider.isDragging() && sliderTrackW > 0) {
            double val = slider.handleDrag(mouseX, sliderTrackX, sliderTrackW,
                    SENS_MIN, SENS_MAX);
            setSensitivity(val);
        }
    }

    /** 结束滑条拖拽 */
    public void endSliderDrag() {
        slider.endDrag();
    }

    // ======================== 滚轮 ========================

    /**
     * 处理鼠标滚轮在灵敏度滑条区域的滚动。
     *
     * @return true 如果滚轮事件被滑条消费
     */
    public boolean handleSliderScroll(double mouseX, double mouseY, double scrollY) {
        Double newVal = slider.handleScroll(mouseX, mouseY, scrollY,
                sliderTrackX, sliderTrackY, sliderTrackW, SENS_MIN, SENS_MAX);
        if (newVal != null) {
            setSensitivity(newVal);
            return true;
        }
        return false;
    }

    // ======================== 灵敏度读写 ========================

    private double getSensitivity() {
        if (cameraModule != null) {
            return cameraModule.getInputSensitivity();
        }
        return 1.0;
    }

    private void setSensitivity(double val) {
        if (cameraModule != null) {
            cameraModule.setInputSensitivity((float) val);
        }
    }
}
