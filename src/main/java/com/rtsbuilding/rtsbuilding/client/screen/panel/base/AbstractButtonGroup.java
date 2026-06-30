package com.rtsbuilding.rtsbuilding.client.screen.panel.base;

import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 通用按钮组基类——统一管理一组按钮的精灵图渲染、悬浮动画、选中态与点击分发。
 *
 * <p>统一精灵图规格（1024×1536，横向双主题，纵向3状态），所有按钮组均继承此类。</p>
 *
 * <p>支持两种布局模式：</p>
 * <ul>
 *   <li><b>顶栏按钮组</b>：通过 {@link TopBarLayoutHelper.ButtonGroup} 提供布局位置，
 *      调用 {@link #render(GuiGraphics, int, int, TopBarLayoutHelper.ButtonGroup)} 和
 *      {@link #mouseClicked(int, int, TopBarLayoutHelper.ButtonGroup)}</li>
 *   <li><b>左边栏按钮组</b>：通过 originX/originY 锚定，
 *      调用 {@link #render(GuiGraphics, int, int, int, int)} 和
 *      {@link #mouseClicked(double, double, int, int)}</li>
 * </ul>
 *
 * <p>子类需覆盖 {@link #onButtonClick(int)} 自定义点击行为，默认单选逻辑。</p>
 */
public abstract class AbstractButtonGroup {

    // ======================== 精灵图渲染常量 ========================

    /** 精灵图总宽 */
    public static final int TEX_W = 1024;
    /** 精灵图总高 */
    public static final int TEX_H = 1536;
    /** 单主题半区宽度 */
    public static final int HALF_W = 512;
    /** 单个状态帧高度 */
    public static final int STATE_H = 512;

    // ======================== 默认值 ========================

    /** 默认按钮尺寸（左边栏） */
    public static final int DEFAULT_BTN_SIZE = 24;
    /** 默认组内间隙 */
    public static final int DEFAULT_INNER_GAP = 0;

    /** 布局方向 */
    public enum Direction { HORIZONTAL, VERTICAL }

    // ======================== 实例字段 ========================

    /** 该组各按钮对应的贴图 */
    protected final ResourceLocation[] textures;

    /** 各按钮选中状态（true=绘制启用态 srcY=1024） */
    protected final boolean[] selected;

    /** 各按钮悬浮动画状态 */
    private final boolean[] lastHovered;
    private final SmoothAnimator[] hoverAnims;

    /** 按钮绘制尺寸 */
    protected final int buttonSize;
    /** 组内间隙 */
    private final int innerGap;
    /** 布局方向 */
    private final Direction direction;

    // ======================== 构造 ========================

    /**
     * @param direction  布局方向
     * @param buttonSize 按钮绘制尺寸
     * @param innerGap   组内按钮间隙
     * @param textures   各按钮贴图（索引顺序对应组内从左到右/从上到下的排列）
     */
    protected AbstractButtonGroup(Direction direction, int buttonSize, int innerGap, ResourceLocation... textures) {
        this.direction = direction;
        this.buttonSize = buttonSize;
        this.innerGap = innerGap;
        this.textures = textures;
        int n = textures.length;
        this.selected = new boolean[n];
        this.lastHovered = new boolean[n];
        this.hoverAnims = new SmoothAnimator[n];
        for (int i = 0; i < n; i++) {
            this.hoverAnims[i] = AnimationFactory.createHoverAnim();
        }
    }

    /** 左边栏快捷构造（VERTICAL，24px，0间隙） */
    protected AbstractButtonGroup(ResourceLocation... textures) {
        this(Direction.VERTICAL, DEFAULT_BTN_SIZE, DEFAULT_INNER_GAP, textures);
    }

    // ======================== 查询 ========================

    /** 该组按钮数量 */
    public final int buttonCount() {
        return textures.length;
    }

    /** 该组在纵向占据的总高度（用于垂直布局的跨组间距计算） */
    public final int totalHeight() {
        int n = textures.length;
        return n * buttonSize + (n - 1) * innerGap;
    }

    // ======================== 渲染（顶栏按钮组） ========================

    /** 通过 {@link TopBarLayoutHelper.ButtonGroup} 渲染所有按钮 */
    public void render(GuiGraphics g, int mouseX, int mouseY, TopBarLayoutHelper.ButtonGroup group) {
        for (int i = 0; i < textures.length; i++) {
            renderSingle(g, mouseX, mouseY, i, group.rect(i).x(), group.rect(i).y());
        }
        renderExtra(g, mouseX, mouseY, group);
    }

    // ======================== 渲染（左边栏按钮组） ========================

    /** 通过原点坐标渲染所有按钮（按 direction 方向排列） */
    public void render(GuiGraphics g, int mouseX, int mouseY, int originX, int originY) {
        for (int i = 0; i < textures.length; i++) {
            int bx = direction == Direction.HORIZONTAL
                    ? originX + i * (buttonSize + innerGap) : originX;
            int by = direction == Direction.VERTICAL
                    ? originY + i * (buttonSize + innerGap) : originY;
            renderSingle(g, mouseX, mouseY, i, bx, by);
        }
    }

    // ======================== 单体按钮渲染 ========================

    /** 绘制单个按钮（含悬浮动画 + 选中态），使用统一精灵图格式 */
    private void renderSingle(GuiGraphics g, int mouseX, int mouseY, int index, int bx, int by) {
        // 更新悬浮状态
        boolean hovering = mouseX >= bx && mouseX < bx + buttonSize
                && mouseY >= by && mouseY < by + buttonSize;
        if (hovering != lastHovered[index]) {
            lastHovered[index] = hovering;
            hoverAnims[index].start(hovering ? 1.0f : 0.0f);
        }
        hoverAnims[index].tick();

        // 双主题 x 偏移：亮色→右半区，暗色→左半区
        int themeU = RtsClientUiUtil.isLightMode() ? HALF_W : 0;

        if (selected[index]) {
            // 选中态（srcY=1024）：直接绘制，不做悬浮处理
            RtsClientUiUtil.drawScaledImage(g, textures[index],
                    bx, by, buttonSize, buttonSize,
                    themeU, STATE_H * 2, HALF_W, STATE_H,
                    TEX_W, TEX_H);
        } else {
            // 非选中态：正常态（srcY=0）↔ 悬浮态（srcY=512）交叉淡入
            float t = hoverAnims[index].getValue();
            if (t > 0.001f && t < 0.999f) {
                Runnable normal = () -> RtsClientUiUtil.drawScaledImage(g, textures[index],
                        bx, by, buttonSize, buttonSize,
                        themeU, 0, HALF_W, STATE_H,
                        TEX_W, TEX_H);
                Runnable hovered = () -> RtsClientUiUtil.drawScaledImage(g, textures[index],
                        bx, by, buttonSize, buttonSize,
                        themeU, STATE_H, HALF_W, STATE_H,
                        TEX_W, TEX_H);
                RtsClientUiUtil.renderCrossFade(t, normal, hovered);
            } else if (t >= 0.999f) {
                RtsClientUiUtil.drawScaledImage(g, textures[index],
                        bx, by, buttonSize, buttonSize,
                        themeU, STATE_H, HALF_W, STATE_H,
                        TEX_W, TEX_H);
            } else {
                RtsClientUiUtil.drawScaledImage(g, textures[index],
                        bx, by, buttonSize, buttonSize,
                        themeU, 0, HALF_W, STATE_H,
                        TEX_W, TEX_H);
            }
        }
    }

    // ======================== 额外渲染（子类覆盖） ========================

    /**
     * 在组内所有按钮渲染完成后调用，用于绘制折叠箭头、浮窗等附加元素。
     * 仅顶栏按钮组使用此钩子。
     */
    protected void renderExtra(GuiGraphics g, int mouseX, int mouseY, TopBarLayoutHelper.ButtonGroup group) {}

    // ======================== 点击（顶栏按钮组） ========================

    /** 处理鼠标点击（顶栏），返回是否消费事件 */
    public boolean mouseClicked(int mx, int my, TopBarLayoutHelper.ButtonGroup group) {
        for (int i = 0; i < textures.length; i++) {
            var r = group.rect(i);
            if (mx >= r.x() && mx < r.x() + buttonSize && my >= r.y() && my < r.y() + buttonSize) {
                onButtonClick(i);
                return true;
            }
        }
        return false;
    }

    // ======================== 点击（左边栏按钮组） ========================

    /** 处理鼠标点击（左边栏），返回被点击的按钮索引，未命中返回 -1 */
    public int mouseClicked(double mx, double my, int originX, int originY) {
        for (int i = 0; i < textures.length; i++) {
            int bx = direction == Direction.HORIZONTAL
                    ? originX + i * (buttonSize + innerGap) : originX;
            int by = direction == Direction.VERTICAL
                    ? originY + i * (buttonSize + innerGap) : originY;
            if (mx >= bx && mx < bx + buttonSize && my >= by && my < by + buttonSize) {
                onButtonClick(i);
                return i;
            }
        }
        return -1;
    }

    // ======================== 子类可覆盖的点击行为 ========================

    /**
     * 按钮点击回调。默认行为：单选（清除所有选中，点亮当前按钮）。
     * <p>子类可覆盖此方法实现互斥切换、外部状态驱动等复杂逻辑。</p>
     */
    protected void onButtonClick(int index) {
        java.util.Arrays.fill(selected, false);
        selected[index] = true;
    }

    // ======================== 选中态管理 ========================

    /** 清除该组所有按钮的选中状态 */
    public final void clearSelection() {
        java.util.Arrays.fill(selected, false);
    }

    /**
     * 检测指定索引的按钮是否处于选中状态。
     *
     * @param index 按钮索引
     * @return true 如果索引在有效范围内且该按钮被选中
     */
    public final boolean isSelected(int index) {
        return index >= 0 && index < selected.length && selected[index];
    }

    // ======================== 动画刷新 ========================

    /** 刷新所有悬浮动画器 */
    public void tick() {
        for (SmoothAnimator a : hoverAnims) a.tick();
    }
}
