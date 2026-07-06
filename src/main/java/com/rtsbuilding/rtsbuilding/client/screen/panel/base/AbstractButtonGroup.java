package com.rtsbuilding.rtsbuilding.client.screen.panel.base;

import com.mojang.math.Axis;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.util.state.HoverStateManager;
import com.rtsbuilding.rtsbuilding.client.util.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 通用按钮组基类——统一管理一组按钮的精灵图渲染、悬浮动画、选中态与点击分发。
 *
 * <p>统一精灵图规格（1024×1536，横向双主题，纵向3状态），所有按钮组均继承此类。</p>
 *
 * <p>支持两种渲染模式：</p>
 * <ul>
 *   <li><b>双层渲染（有背景贴图）：</b>先按位置顺序绘制底部背景（down/middle/up），再绘制上层图案。
 *       适用于需要视觉分组的左侧栏按钮组。</li>
 *   <li><b>单层渲染（无背景贴图）：</b>直接绘制按钮贴图本身，保持原有行为。适用于顶栏等单图标按钮组。</li>
 * </ul>
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
 * <p>背景贴图位置规则（纵向组 VERTICAL，自上而下排列）：</p>
 * <ul>
 *   <li>2个按钮：首位→up，末位→down</li>
 *   <li>3个及以上：首位→up，中间→middle，末位→down</li>
 * </ul>
 *
 * <p>横向组（HORIZONTAL）自动对背景贴图旋转。由于 ButtonGroup.fromRight
 * 从右向左布局，index 0 为右侧按钮、index n-1 为左侧按钮。
 * 右侧按钮用 up 作 +90° 旋转使顶部圆角变右圆角，
 * 左侧按钮用 down 作 +90° 旋转使底部圆角变左圆角。</p>
 *
 * <p>子类需覆盖 {@link #onButtonClick(int)} 自定义点击行为，默认单选逻辑。</p>
 */
public abstract class AbstractButtonGroup {

    // ======================== 精灵图渲染常量 ========================

    /** 精灵图总宽 */
    public static final int TEX_W = 1024;
    /** 精灵图总高（3状态贴图） */
    public static final int TEX_H = 1536;
    /** 扁平图案贴图总高（仅亮暗分区，无纵向状态） */
    public static final int ICON_TEX_H = 512;
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

    // ---- 图案贴图（每按钮各自独立的图标/上层图案） ----

    /** 该组各按钮对应的图案贴图 */
    protected final ResourceLocation[] patternTextures;

    /** 各按钮图案的 TextureInfo */
    private final TextureInfo[] patternTexInfoCache;

    /**
     * 各按钮图案精灵区域：
     * <ul>
     *   <li>{@code hasBg=true}（扁平图案，1024×512）：大小为 n，每按钮 1 个区域，{@code patternRegions[i]}</li>
     *   <li>{@code hasBg=false}（3状态图案，1024×1536）：大小为 n*3，{@code patternRegions[i*3+state]}</li>
     * </ul>
     */
    private final SpriteRegion[] patternRegions;

    // ---- 背景贴图（基于位置共享的 down/middle/up，可选） ----

    /** 是否有独立背景贴图层 */
    protected final boolean hasBg;

    /** 三种位置背景贴图：down（首位）、middle（中间）、up（末位） */
    private final ResourceLocation downBg, middleBg, upBg;

    /** 三种背景的 TextureInfo：[0]=down, [1]=middle, [2]=up */
    private final TextureInfo[] bgTexInfo;

    /** 三种背景的精灵区域：[type*3+state]，type=0/1/2 对应 down/middle/up */
    private final SpriteRegion[] bgStateRegions;

    /** 各按钮对应的背景类型索引：buttonIndex → 0=down / 1=middle / 2=up */
    protected final int[] bgTypeForButton;

    // ---- 公共 ----

    /** 各按钮选中状态（true=绘制启用态） */
    protected final boolean[] selected;

    /** 各按钮悬浮状态管理器 */
    protected final HoverStateManager[] hoverStates;

    /** 当前帧各按钮悬浮进度缓存（仅 hasBg=true 时用于避免重复计算） */
    private final float[] hoverProgressCache;

    /** 按钮绘制尺寸 */
    protected final int buttonSize;
    /** 组内间隙 */
    protected final int innerGap;
    /** 布局方向 */
    protected final Direction direction;

    // ======================== 构造（有背景贴图） ========================

    /**
     * 完整构造——支持双层渲染（背景 + 图案）。
     *
     * <p>当 {@code hasBg=true} 时启用双层渲染：先按位置顺序绘制底部背景
     * （首位→downBg / 中间→middleBg / 末位→upBg），再绘制上层图案。
     * 当 {@code hasBg=false} 时仅绘制图案（旧行为）。</p>
     *
     * @param direction  布局方向
     * @param buttonSize 按钮绘制尺寸
     * @param innerGap   组内按钮间隙
     * @param hasBg      是否启用独立背景贴图层
     * @param downBg     首位按钮背景贴图（hasBg=false 时传 null）
     * @param middleBg   中间按钮背景贴图（hasBg=false 时传 null）
     * @param upBg       末位按钮背景贴图（hasBg=false 时传 null）
     * @param patterns   各按钮图案贴图（索引顺序对应组内排列）
     */
    protected AbstractButtonGroup(Direction direction, int buttonSize, int innerGap, boolean hasBg,
                                  ResourceLocation downBg, ResourceLocation middleBg, ResourceLocation upBg,
                                  ResourceLocation... patterns) {
        this.direction = direction;
        this.buttonSize = buttonSize;
        this.innerGap = innerGap;
        this.hasBg = hasBg;
        this.downBg = downBg;
        this.middleBg = middleBg;
        this.upBg = upBg;
        this.patternTextures = patterns;
        int n = patterns.length;

        // ---- 选中态 + 悬浮态（公共初始化） ----
        this.selected = new boolean[n];
        this.hoverStates = new HoverStateManager[n];
        this.hoverProgressCache = new float[n];
        for (int i = 0; i < n; i++) {
            this.hoverStates[i] = new HoverStateManager();
        }

        // ---- 初始化图案贴图缓存（必选） ----
        this.patternTexInfoCache = new TextureInfo[n];
        if (hasBg) {
            // 扁平图案（1024×512）：仅亮暗分区，背景层负责状态视觉效果
            this.patternRegions = new SpriteRegion[n];
            for (int i = 0; i < n; i++) {
                if (patterns[i] == null) continue; // 无图案（如用时由额外渲染自行绘制）
                this.patternTexInfoCache[i] = new TextureInfo(
                        patterns[i], TEX_W, ICON_TEX_H,
                        TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
                        TextureInfo.FilterMode.PIXEL);
                this.patternRegions[i] = new SpriteRegion(patternTexInfoCache[i], 0, 0, HALF_W, ICON_TEX_H);
            }
        } else {
            // 3状态图案（1024×1536）：包含正常/悬浮/选中
            this.patternRegions = new SpriteRegion[n * 3];
            for (int i = 0; i < n; i++) {
                this.patternTexInfoCache[i] = new TextureInfo(
                        patterns[i], TEX_W, TEX_H,
                        TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
                        TextureInfo.FilterMode.PIXEL);
                this.patternRegions[i * 3]     = new SpriteRegion(patternTexInfoCache[i], 0, 0,          HALF_W, STATE_H);
                this.patternRegions[i * 3 + 1] = new SpriteRegion(patternTexInfoCache[i], 0, STATE_H,    HALF_W, STATE_H);
                this.patternRegions[i * 3 + 2] = new SpriteRegion(patternTexInfoCache[i], 0, STATE_H * 2, HALF_W, STATE_H);
            }
        }

        // ---- 初始化背景贴图缓存（可选） ----
        if (hasBg) {
            ResourceLocation[] bgTexArr = { downBg, middleBg, upBg };
            this.bgTexInfo = new TextureInfo[3];
            this.bgStateRegions = new SpriteRegion[9]; // 3种类型 × 3状态
            for (int t = 0; t < 3; t++) {
                this.bgTexInfo[t] = new TextureInfo(
                        bgTexArr[t], TEX_W, TEX_H,
                        TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
                        TextureInfo.FilterMode.PIXEL);
                this.bgStateRegions[t * 3]     = new SpriteRegion(bgTexInfo[t], 0, 0,           HALF_W, STATE_H);
                this.bgStateRegions[t * 3 + 1] = new SpriteRegion(bgTexInfo[t], 0, STATE_H,     HALF_W, STATE_H);
                this.bgStateRegions[t * 3 + 2] = new SpriteRegion(bgTexInfo[t], 0, STATE_H * 2, HALF_W, STATE_H);
            }

            // ---- 计算每个按钮对应哪种背景 ----
            this.bgTypeForButton = new int[n];
            for (int i = 0; i < n; i++) {
                if (n == 1) {
                    bgTypeForButton[i] = 1; // 单按钮用 middle
                } else if (direction == Direction.HORIZONTAL) {
                    // 横向：由于 ButtonGroup.fromRight 从右向左布局，index 0 为右侧按钮
                    // 右侧按钮 → up（旋转后右圆角），左侧按钮 → down（旋转后左圆角）
                    bgTypeForButton[i] = i == 0 ? 2 : (i == n - 1 ? 0 : 1);
                } else {
                    // 纵向首位（上）→ up，末位（下）→ down
                    bgTypeForButton[i] = i == 0 ? 2 : (i == n - 1 ? 0 : 1);
                }
            }
        } else {
            this.bgTexInfo = null;
            this.bgStateRegions = null;
            this.bgTypeForButton = null;
        }
    }

    /** 左边栏快捷构造（VERTICAL，24px，0间隙，无背景） */
    protected AbstractButtonGroup(ResourceLocation... textures) {
        this(Direction.VERTICAL, DEFAULT_BTN_SIZE, DEFAULT_INNER_GAP, false, null, null, null, textures);
    }

    // ======================== 查询 ========================

    /** 该组按钮数量 */
    public final int buttonCount() {
        return patternTextures.length;
    }

    /** 该组在纵向占据的总高度 */
    public final int totalHeight() {
        int n = patternTextures.length;
        return n * buttonSize + (n - 1) * innerGap;
    }

    // ======================== 渲染（顶栏按钮组） ========================

    public void render(GuiGraphics g, int mouseX, int mouseY, TopBarLayoutHelper.ButtonGroup group) {
        int n = patternTextures.length;
        // 第一遍：按顺序绘制所有按钮背景
        if (hasBg) {
            for (int i = 0; i < n; i++) {
                renderSingleBg(g, mouseX, mouseY, i, group.rect(i).x(), group.rect(i).y());
            }
        }
        // 第二遍：绘制所有按钮图案
        for (int i = 0; i < n; i++) {
            renderSinglePattern(g, mouseX, mouseY, i, group.rect(i).x(), group.rect(i).y());
        }
        renderExtra(g, mouseX, mouseY, group);
    }

    // ======================== 渲染（左边栏按钮组） ========================

    public void render(GuiGraphics g, int mouseX, int mouseY, int originX, int originY) {
        int n = patternTextures.length;
        // 第一遍：按顺序绘制所有按钮背景
        if (hasBg) {
            for (int i = 0; i < n; i++) {
                int bx = direction == Direction.HORIZONTAL
                        ? originX + i * (buttonSize + innerGap) : originX;
                int by = direction == Direction.VERTICAL
                        ? originY + i * (buttonSize + innerGap) : originY;
                renderSingleBg(g, mouseX, mouseY, i, bx, by);
            }
        }
        // 第二遍：绘制所有按钮图案
        for (int i = 0; i < n; i++) {
            int bx = direction == Direction.HORIZONTAL
                    ? originX + i * (buttonSize + innerGap) : originX;
            int by = direction == Direction.VERTICAL
                    ? originY + i * (buttonSize + innerGap) : originY;
            renderSinglePattern(g, mouseX, mouseY, i, bx, by);
        }
    }

    // ======================== 单体背景渲染 ========================

    /**
     * 绘制单个按钮的背景层——根据位置选择 down/middle/up 并应用状态动画。
     *
     * <p>横向组（HORIZONTAL）时对背景贴图进行旋转：
     * <ul>
     *   <li>down 类型（首位/左）→ +90°（底部圆角旋转为左侧圆角）</li>
     *   <li>up 类型（末位/右）→ +90°（顶部圆角旋转为右侧圆角）</li>
     *   <li>middle 类型（中间）→ 不旋转</li>
     * </ul></p>
     */
    protected void renderSingleBg(GuiGraphics g, int mouseX, int mouseY, int index, int bx, int by) {
        boolean hovering = mouseX >= bx && mouseX < bx + buttonSize
                && mouseY >= by && mouseY < by + buttonSize;
        float hoverT = this.hoverStates[index].update(hovering);
        hoverProgressCache[index] = hoverT; // 缓存供 pattern 层复用

        int bt = bgTypeForButton[index]; // 0=down, 1=middle, 2=up
        int bsi = bt * 3;

        boolean needRotate = direction == Direction.HORIZONTAL && bt != 1;
        if (needRotate) {
            float angle = 90f; // down→+90°(底部→左侧圆角), up→+90°(顶部→右侧圆角)
            float cx = bx + buttonSize / 2.0f;
            float cy = by + buttonSize / 2.0f;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
            g.pose().translate(-cx, -cy, 0);
        }

        SpriteRenderer.drawStateSprite(g,
                bgStateRegions[bsi],     // normal
                bgStateRegions[bsi + 1], // hovered
                bgStateRegions[bsi + 2], // selected
                selected[index],
                hoverT,
                bx, by, buttonSize, buttonSize);

        if (needRotate) {
            g.pose().popPose();
        }
    }

    // ======================== 单体图案渲染 ========================

    /** 绘制单个按钮的图案层（上层图标） */
    protected void renderSinglePattern(GuiGraphics g, int mouseX, int mouseY, int index, int bx, int by) {
        boolean hovering = mouseX >= bx && mouseX < bx + buttonSize
                && mouseY >= by && mouseY < by + buttonSize;

        if (hasBg) {
            // 扁平图案：不重复更新悬浮状态（已在 renderSingleBg 中更新），直接绘制静图案
            if (patternTextures[index] == null) return; // 无图案，跳过
            SpriteRenderer.drawSprite(g, patternRegions[index].withTheme(),
                    bx, by, buttonSize, buttonSize);
        } else {
            // 3状态图案：更新悬浮动画后绘制三段式状态
            float hoverT = this.hoverStates[index].update(hovering);
            int si = index * 3;
            SpriteRenderer.drawStateSprite(g,
                    patternRegions[si],     // normal
                    patternRegions[si + 1], // hovered
                    patternRegions[si + 2], // selected
                    selected[index],
                    hoverT,
                    bx, by, buttonSize, buttonSize);
        }
    }

    // ======================== 额外渲染（子类覆盖） ========================

    protected void renderExtra(GuiGraphics g, int mouseX, int mouseY, TopBarLayoutHelper.ButtonGroup group) {}

    // ======================== 点击（顶栏按钮组） ========================

    public boolean mouseClicked(int mx, int my, TopBarLayoutHelper.ButtonGroup group) {
        for (int i = 0; i < patternTextures.length; i++) {
            var r = group.rect(i);
            if (mx >= r.x() && mx < r.x() + buttonSize && my >= r.y() && my < r.y() + buttonSize) {
                onButtonClick(i);
                return true;
            }
        }
        return false;
    }

    // ======================== 点击（左边栏按钮组） ========================

    public int mouseClicked(double mx, double my, int originX, int originY) {
        for (int i = 0; i < patternTextures.length; i++) {
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

    protected void onButtonClick(int index) {
        java.util.Arrays.fill(selected, false);
        selected[index] = true;
    }

    // ======================== 选中态管理 ========================

    public final void clearSelection() {
        java.util.Arrays.fill(selected, false);
    }

    public final boolean isSelected(int index) {
        return index >= 0 && index < selected.length && selected[index];
    }

    // ======================== 动画刷新（已由 HoverStateManager 内部管理） ========================

    public void tick() {
    }
}
