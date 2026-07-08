package com.rtsbuilding.rtsbuilding.client.screen.panel.downbar.overlay;

import com.mojang.math.Axis;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.record.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.overlay.DownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.util.animate.EasingFunctions;
import com.rtsbuilding.rtsbuilding.client.util.animate.FloatAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 下栏左嵌层——容器绑定显示面板。
 *
 * <p>显示所有已绑定的存储容器列表，每行包含优先级编号、方块图标、名称，
 * 以及解绑和模式切换按钮。优先级编号可点击进入编辑模式，支持数字键盘输入。
 * 右侧配有纵向滚动条，支持鼠标滚轮和拖拽。</p>
 */
public final class LeftDownOverlayLayer extends DownOverlayLayer {

    // ======================== 布局常量 ========================

    private static final int ROW_H = 20;
    private static final int ICON_SIZE = 12;
    /** 优先级编号显示宽度 */
    private static final int PRIORITY_W = 14;
    private static final int PRIORITY_PAD_H = 4;
    private static final int PRIORITY_ICON_GAP = 2;
    private static final int ICON_TEXT_GAP = 4;
    /** 按钮高度 */
    private static final int BTN_HEIGHT = 14;
    /** 按钮文字左右内边距 */
    private static final int BTN_PAD_H = 4;
    private static final int BTN_GAP = 2;
    /** 箭头按钮尺寸（正方形的边长） */
    private static final int ARROW_BTN_SIZE = 14;
    /** 箭头贴图绘制尺寸（小于按钮尺寸以保留内边距） */
    private static final int ARROW_DRAW_SIZE = 10;
    private static final int SCROLLBAR_W = 7;
    private static final int RIGHT_MARGIN = 1;
    private static final int LEFT_PAD = 5;
    private static final int TOP_PAD = 2;

    /** 编辑模式下输入框宽度 */
    private static final int EDIT_INPUT_W = 40;
    /** 编辑模式下输入框高度 */
    private static final int EDIT_INPUT_H = 13;
    /** 光标闪烁周期（毫秒） */
    private static final long CURSOR_BLINK_MS = 600;

    // ======================== 颜色 ========================

    private static final int UNBIND_COLOR = 0xFFE06060;
    private static final int UNBIND_HOVER_COLOR = 0xFFFF8080;
    private static final int MODE_BI_COLOR = 0xFF60C060;
    private static final int MODE_EXTRACT_COLOR = 0xFFE0A040;
    private static final int BTN_HOVER_FG = 0xFFFFFFFF;
    /** 位置按钮颜色 */
    private static final int LOCATE_BTN_COLOR = 0xFF8080E0;
    private static final int LOCATE_BTN_HOVER_COLOR = 0xFFA0A0FF;
    // ======================== 输入框贴图 ========================

    private static final ResourceLocation INPUT_BOX_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/input_box.png");
    private static final int INPUT_BOX_TEX_W = 32;
    private static final int INPUT_BOX_TEX_H = 32;
    private static final int INPUT_BOX_STATE_H = 16;
    private static final int INPUT_BOX_BORDER = 4;
    private static final TextureInfo INPUT_BOX_TEX_INFO = new TextureInfo(
            INPUT_BOX_TEXTURE, INPUT_BOX_TEX_W, INPUT_BOX_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion INPUT_BOX_NINE_SLICE = NineSliceRegion.fullTheme(
            INPUT_BOX_TEX_INFO, INPUT_BOX_STATE_H, INPUT_BOX_BORDER);

    // ======================== 按钮贴图 ========================

    private static final ResourceLocation BTN_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button.png");
    private static final int BTN_TEX_W = 32;
    private static final int BTN_TEX_H = 32;
    private static final int BTN_STATE_H = 16;
    private static final int BTN_BORDER = 4;
    private static final TextureInfo BTN_TEX_INFO = new TextureInfo(
            BTN_TEXTURE, BTN_TEX_W, BTN_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion BTN_NINE_SLICE = NineSliceRegion.fullTheme(
            BTN_TEX_INFO, BTN_STATE_H, BTN_BORDER);

    // ======================== 箭头贴图 ========================

    private static final ResourceLocation ARROW_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/arrow.png");
    /** 源区域宽度（单帧 512px）——等于 halfWidth() */
    private static final int ARROW_TEX_W = 512;
    /** 贴图文件总宽度（双主题翻倍为 1024）——等于 fullWidth() */
    private static final int ARROW_TEX_FILE_W = 1024;
    private static final int ARROW_TEX_H = 512;
    private static final TextureInfo ARROW_TEX_INFO = new TextureInfo(
            ARROW_TEXTURE, ARROW_TEX_FILE_W, ARROW_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);
    private static final SpriteRegion ARROW_SPRITE = new SpriteRegion(
            ARROW_TEX_INFO, 0, 0, ARROW_TEX_W, ARROW_TEX_H).withTheme();

    // ======================== 行背景贴图 ========================

    /** 单数行背景（第 1、3、5…行）*/
    private static final ResourceLocation SINGULAR_BG_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/singular_background.png");
    /** 双数行背景（第 2、4、6…行）*/
    private static final ResourceLocation EVEN_BG_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/even_number_background.png");
    private static final int BG_TEX_W = 32;
    private static final int BG_TEX_H = 32;
    private static final int BG_STATE_H = 16;
    private static final int BG_BORDER = 4;
    private static final TextureInfo SINGULAR_BG_TEX_INFO = new TextureInfo(
            SINGULAR_BG_TEXTURE, BG_TEX_W, BG_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);
    private static final TextureInfo EVEN_BG_TEX_INFO = new TextureInfo(
            EVEN_BG_TEXTURE, BG_TEX_W, BG_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion SINGULAR_BG_NINE_SLICE = NineSliceRegion.fullTheme(
            SINGULAR_BG_TEX_INFO, BG_STATE_H, BG_BORDER);
    private static final NineSliceRegion EVEN_BG_NINE_SLICE = NineSliceRegion.fullTheme(
            EVEN_BG_TEX_INFO, BG_STATE_H, BG_BORDER);

    // ======================== 组件与状态 ========================

    private final ScrollBar scrollBar = new ScrollBar();
    private final List<RowLayout> rowLayouts = new ArrayList<>();

    /** 正在编辑优先级的条目索引，-1 表示未编辑 */
    private int editingIndex = -1;
    /** 编辑缓冲区 */
    private final StringBuilder editBuffer = new StringBuilder();
    /** 进入编辑时的时间戳（用于光标闪烁） */
    private long editStartTime;
    /** 当前是否正在编辑（快捷判断） */
    private boolean isEditing;

    // ======================== 优先级框宽度动画 ========================

    /** 优先级框宽度交叉渐变动画（0=常态，1=编辑态） */
    private final FloatAnimation priorityBoxAnim = FloatAnimation.builder()
            .from(0f).to(0f)
            .duration(100L)
            .easing(EasingFunctions.EASE_OUT_QUAD)
            .startFromCurrent(true)
            .build();
    /** 上一次播放动画的行号（编辑结束后继续用此值回退动画） */
    private int lastAnimRow = -1;
    /** 上一次动画行的常态宽度 */
    private float lastAnimBaseW;

    // ======================== 条目移动动画 ========================

    /**
     * 每条目的内容 Y 坐标动画（key=原始索引，value=当前帧的视觉 Y）。
     * 优先级变动导致条目排序变化时，平滑过渡到新位置。
     */
    private final Map<Integer, Float> entryContentY = new HashMap<>();

    // ======================== 背景条悬浮动画 ========================

    /** 每根背景条的悬浮动画进度（key=视觉行号，value=[0,1]） */
    private final Map<Integer, Float> barHoverProgress = new HashMap<>();
    /** 每根背景条当前是否处于悬浮态 */
    private final Map<Integer, Boolean> barHoverState = new HashMap<>();

    /**
     * 获取背景条交叉渐变动画的当前进度，并自动向目标值过渡。
     *
     * @param barIndex      背景条视觉行号
     * @param shouldHover   当前帧是否应悬浮
     * @return 动画进度 [0, 1]，0=常态，1=悬浮态
     */
    private float tickBarHover(int barIndex, boolean shouldHover) {
        Float progressObj = barHoverProgress.get(barIndex);
        float progress;
        if (progressObj == null) {
            // 首次见到此条，直接跳到目标值
            progress = shouldHover ? 1f : 0f;
            barHoverProgress.put(barIndex, progress);
            barHoverState.put(barIndex, shouldHover);
            return progress;
        }
        progress = progressObj;
        Boolean prevState = barHoverState.get(barIndex);
        if (prevState == null || prevState != shouldHover) {
            barHoverState.put(barIndex, shouldHover);
        }
        float target = shouldHover ? 1f : 0f;
        // 指数平滑过渡
        progress += (target - progress) * 0.28f;
        if (Math.abs(progress - target) < 0.001f) {
            progress = target;
        }
        barHoverProgress.put(barIndex, progress);
        return progress;
    }

    private static final class RowLayout {
        int y;
        /** 当前行箭头按钮 X */
        int arrowBtnX;
        /** 当前行优先级点击区域起始 X */
        int priorityX;
        /** 当前行优先级框实际宽度 */
        int priorityW;
        int unbindX;
        int toggleX;
        /** 解绑按钮实际宽度 */
        int unbindW;
        /** 模式切换按钮实际宽度 */
        int toggleW;
        /** 位置按钮 X */
        int locateBtnX;
        /** 位置按钮宽度 */
        int locateBtnW;
        /** 在原始列表中的索引 */
        int originalIndex;
    }

    // ======================== 渲染 ========================

    @Override
    protected void renderContent(GuiGraphics g) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int mouseX = getLastMouseX();
        int mouseY = getLastMouseY();

        int visibleH = h - TOP_PAD * 2;
        Minecraft mc = Minecraft.getInstance();
        int lineH = mc.font.lineHeight;

        // 预先获取条目数，用于滚动计算（背景条与内容联动）
        StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
        int scroll = 0;
        int entryCount = 0;
        if (sm != null) {
            var entries = sm.getLinkedStorageEntries();
            var names = sm.getLinkedDisplayNames();
            var iconIds = sm.getLinkedIconItemIds();
            var priorities = sm.getLinkedPriorities();
            entryCount = Math.min(entries.size(), Math.min(names.size(),
                    Math.min(iconIds.size(), priorities.size())));
            scrollBar.setContent(entryCount * ROW_H, visibleH);
            scroll = scrollBar.getScroll();
        }

        int bgH = 20;
        // ---- 第一遍：背景条（与滚动联动，交叉淡入淡出悬浮）----
        int firstBgRow = scroll / ROW_H;
        int totalBgRows = visibleH / ROW_H + 2;
        for (int i = firstBgRow; i < firstBgRow + totalBgRows; i++) {
            int bgTop = y + TOP_PAD + i * ROW_H - scroll;
            boolean hoveredRow = !isDividerDragging() && i < entryCount
                    && mouseX >= x && mouseX < x + w
                    && mouseY >= bgTop && mouseY < bgTop + bgH;

            // 交叉淡入淡出悬浮
            float barHoverT = tickBarHover(i, hoveredRow);
            NineSliceRegion normalSlice = (i % 2 == 0)
                    ? SINGULAR_BG_NINE_SLICE.withTheme()
                    : EVEN_BG_NINE_SLICE.withTheme();
            NineSliceRegion hoveredSlice = (i % 2 == 0)
                    ? SINGULAR_BG_NINE_SLICE.withTheme().withVOffset(BG_STATE_H)
                    : EVEN_BG_NINE_SLICE.withTheme().withVOffset(BG_STATE_H);
            CrossFadeRenderer.render(barHoverT,
                    () -> SpriteRenderer.drawNineSlice(g, normalSlice, x, bgTop, w, bgH),
                    () -> SpriteRenderer.drawNineSlice(g, hoveredSlice, x, bgTop, w, bgH));
        }

        if (sm == null) return;
        var entries = sm.getLinkedStorageEntries();
        var names = sm.getLinkedDisplayNames();
        var iconIds = sm.getLinkedIconItemIds();
        var priorities = sm.getLinkedPriorities();
        if (entries.isEmpty()) {
            renderEmptyHint(g, x, y, w, h);
            return;
        }

        int count = Math.min(entries.size(), Math.min(names.size(),
                Math.min(iconIds.size(), priorities.size())));

        // 按优先级升序排序（优先级越小越靠前）
        List<Integer> sortedIndices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            sortedIndices.add(i);
        }
        sortedIndices.sort(Comparator.comparingInt(priorities::get));

        // 内容区域宽度 = 总宽 - 左侧内边距 - 滚动条 - 右侧边距
        int contentW = w - LEFT_PAD - SCROLLBAR_W - RIGHT_MARGIN;
        int scrollBarGap = scrollBar.isVisible() ? 2 : 0;

        // 预计算按钮区域右边界和左边界（左边界使用最宽切换文字，保证名称区域布局稳定）
        int btnAreaRight = x + LEFT_PAD + contentW - scrollBarGap;
        int unbindTextW = mc.font.width("解绑");
        int btnUnbindW = unbindTextW + BTN_PAD_H * 2;
        int toggleBiW = mc.font.width("双向");
        int toggleExtractW = mc.font.width("仅提取");
        int btnToggleW = Math.max(toggleBiW, toggleExtractW) + BTN_PAD_H * 2;
        int btnLocateW = Math.max(mc.font.width("开启位置"), mc.font.width("关闭显示")) + BTN_PAD_H * 2;
        int btnAreaLeft = btnAreaRight - btnUnbindW - BTN_GAP - btnToggleW - BTN_GAP - btnLocateW;

        // 重新设置滚动范围（与预计算一致，保持同步）
        int totalH = count * ROW_H;
        scrollBar.setContent(totalH, visibleH);
        scroll = scrollBar.getScroll();

        int fontColor = ThemeManager.getTextColor();

        rowLayouts.clear();

        int clipY = y + TOP_PAD;
        int clipH = visibleH;

        // 如果正在编辑且条目索引越界（数据刷新后），取消编辑
        if (isEditing && editingIndex >= count) {
            cancelEdit();
        }

        // ---- 优先级框宽度动画（FloatAnimation 固定时长缓出）----
        priorityBoxAnim.tick();
        // 动画结束后清除最后动画行记录
        if (!priorityBoxAnim.isRunning() && !isEditing) {
            lastAnimRow = -1;
        }

        // ---- 条目内容 Y 坐标动画（优先级变动时条目上下移动平滑过渡）----
        // 清理已移除条目的动画状态
        if (entryContentY.size() > count) {
            entryContentY.keySet().removeIf(key -> key >= count);
        }

        // ---- 第二遍：可见行根据背景条位置绘制条目内容 ----
        for (int i = 0; i < count; i++) {
            int sortedIdx = sortedIndices.get(i);
            int rowY = y + TOP_PAD + i * ROW_H - scroll;
            int baseRowY = y + TOP_PAD + i * ROW_H;

            // 条目内容 Y 坐标指数平滑（仅对排序位移产生动画，滚动立即生效）
            Float animY = entryContentY.get(sortedIdx);
            float targetBaseY = baseRowY;
            if (animY == null) {
                animY = targetBaseY;
            } else {
                animY += (targetBaseY - animY) * 0.15f;
                if (Math.abs(animY - targetBaseY) < 0.5f) {
                    animY = targetBaseY;
                }
            }
            entryContentY.put(sortedIdx, animY);
            int contentY = Math.round(animY) - scroll;

            RowLayout rl = new RowLayout();
            rl.y = contentY;
            rl.originalIndex = sortedIdx;
            rowLayouts.add(rl);

            boolean rowVisible = rowY + bgH >= clipY && rowY < clipY + clipH;

            if (!rowVisible && i != editingIndex) {
                continue; // 非编辑行不可见时跳过渲染
            }

            // 强制让编辑行可见——跳过裁剪检查
            boolean actuallyRender = rowVisible || i == editingIndex;

            LinkedStorageEntry entry = entries.get(sortedIdx);
            String name = names.get(sortedIdx);
            String iconItemId = iconIds.get(sortedIdx);
            int priority = priorities.get(sortedIdx);
            boolean dimmed = !entry.worldAvailable();
            boolean isThisEditing = isEditing && i == editingIndex;

            // 与背景条（19px，距嵌层顶部2px开始）的中心对齐
            int rowCenterY = contentY + 10;
            int cursorX = x + LEFT_PAD;

            // ---- 0. 优先级调整按钮（箭头） ----
            {
                int arrowBtnY = rowCenterY - ARROW_BTN_SIZE / 2;
                rl.arrowBtnX = cursorX;
                if (actuallyRender) {
                    SpriteRenderer.drawNineSlice(g, BTN_NINE_SLICE.withTheme(),
                            cursorX, arrowBtnY, ARROW_BTN_SIZE, ARROW_BTN_SIZE);
                    int arrowCenterX = cursorX + ARROW_BTN_SIZE / 2;
                    int arrowCenterY = arrowBtnY + ARROW_BTN_SIZE / 2;
                    var pose = g.pose();
                    pose.pushPose();
                    pose.translate(arrowCenterX, arrowCenterY, 0);
                    if (i == 0) {
                        pose.mulPose(Axis.ZP.rotationDegrees(180f));
                    }
                    SpriteRenderer.drawSprite(g, ARROW_SPRITE,
                            -ARROW_DRAW_SIZE / 2, -ARROW_DRAW_SIZE / 2,
                            ARROW_DRAW_SIZE, ARROW_DRAW_SIZE);
                    pose.popPose();
                }
            }
            cursorX += ARROW_BTN_SIZE + PRIORITY_ICON_GAP;
            rl.priorityX = cursorX;

            // ---- 1. 优先级（始终使用输入框贴图） ----
            int priorityBoxW = mc.font.width(String.valueOf(priority)) + PRIORITY_PAD_H * 2;
            rl.priorityW = priorityBoxW;
            // 编辑行或刚结束编辑的行使用动画宽度平滑过渡，其余行使用静态宽度
            boolean applyAnim = isThisEditing || (i == lastAnimRow && lastAnimRow >= 0);
            float priorityDrawW;
            if (applyAnim) {
                float baseW = isThisEditing ? priorityBoxW : lastAnimBaseW;
                priorityDrawW = baseW + (EDIT_INPUT_W - baseW) * priorityBoxAnim.getValue();
            } else {
                priorityDrawW = priorityBoxW;
            }
            if (actuallyRender || isThisEditing) {
                renderPriorityBox(g, cursorX, rowCenterY, String.valueOf(priority), isThisEditing, dimmed, (int) priorityDrawW);
            }
            cursorX += (int) priorityDrawW + PRIORITY_ICON_GAP;

            // ---- 2. 方块图标 ----
            if (actuallyRender && !iconItemId.isEmpty()) {
                ItemStack stack = resolveItemStack(iconItemId);
                if (!stack.isEmpty()) {
                    renderItemIcon(g, stack, cursorX + ICON_SIZE / 2, rowCenterY);
                }
            }
            cursorX += ICON_SIZE;

            // ---- 3. 名称 ----
            if (actuallyRender) {
                // cursorX 已跟随动画宽度移动，图标和名称自动平滑跟随
                int maxNameW = Math.max(0, btnAreaLeft - cursorX - ICON_TEXT_GAP);
                String displayName = TextRenderer.trimToWidth(mc.font, name, maxNameW);
                int nameX = cursorX + ICON_TEXT_GAP;
                int nameColor = dimmed ? (fontColor & 0xFFFFFF) | 0x60000000 : fontColor;
                TextRenderer.draw(g, displayName, nameX, rowCenterY - lineH / 2, nameColor);
            }

            // ---- 4. 位置按钮（文字 + 九宫格背景自动适配宽度，从右侧对齐）----
            String unbindText = "解绑";
            int unbindBtnW = mc.font.width(unbindText) + BTN_PAD_H * 2;
            String toggleText = entry.isExtractOnly() ? "仅提取" : "双向";
            int toggleBtnW = mc.font.width(toggleText) + BTN_PAD_H * 2;
            boolean showLocate = entry.worldAvailable() && sm.isLocationDisplayActive(entry.pos());
            String locateText = showLocate ? "关闭显示" : "开启位置";
            int locateBtnW = mc.font.width(locateText) + BTN_PAD_H * 2;
            // 从右边界计算按钮位置，确保切换按钮右边缘始终对齐
            int toggleX = btnAreaRight - toggleBtnW;
            int unbindX = toggleX - BTN_GAP - unbindBtnW;
            int locateBtnX = unbindX - BTN_GAP - locateBtnW;
            int btnY = rowCenterY - BTN_HEIGHT / 2;
            rl.unbindX = unbindX;
            rl.unbindW = unbindBtnW;
            rl.toggleX = toggleX;
            rl.toggleW = toggleBtnW;
            rl.locateBtnX = locateBtnX;
            rl.locateBtnW = locateBtnW;
            // 位置按钮绘制
            if (actuallyRender) {
                boolean hoverLocate = !isDividerDragging()
                        && mouseX >= locateBtnX && mouseX < locateBtnX + locateBtnW
                        && mouseY >= btnY && mouseY < btnY + BTN_HEIGHT;
                drawTextButton(g, locateBtnX, btnY, locateText,
                        hoverLocate ? LOCATE_BTN_HOVER_COLOR : LOCATE_BTN_COLOR, hoverLocate);
            }

            // ---- 5. 解绑按钮（文字 + 九宫格背景自动适配宽度，从右侧对齐）----
            if (actuallyRender) {
                boolean hoverUnbind = !isDividerDragging()
                        && mouseX >= unbindX && mouseX < unbindX + unbindBtnW
                        && mouseY >= btnY && mouseY < btnY + BTN_HEIGHT;
                drawTextButton(g, unbindX, btnY, unbindText,
                        hoverUnbind ? UNBIND_HOVER_COLOR : UNBIND_COLOR, hoverUnbind);
            }

            // ---- 6. 模式切换按钮（文字 + 九宫格背景自动适配宽度，从右侧对齐）----
            if (actuallyRender) {
                boolean hoverToggle = !isDividerDragging()
                        && mouseX >= toggleX && mouseX < toggleX + toggleBtnW
                        && mouseY >= btnY && mouseY < btnY + BTN_HEIGHT;
                int toggleColor = entry.isExtractOnly() ? MODE_EXTRACT_COLOR : MODE_BI_COLOR;
                drawTextButton(g, toggleX, btnY, toggleText,
                        hoverToggle ? BTN_HOVER_FG : toggleColor, hoverToggle);
            }
        }

        // 6. 滚动条（首尾各缩6px）
        int barX = x + w - SCROLLBAR_W - RIGHT_MARGIN;
        scrollBar.render(g, barX, y + TOP_PAD + 6, visibleH - 12);
    }

    // ======================== 优先级输入框渲染 ========================

    /**
     * 渲染优先级输入框——始终使用 input_box.png 九宫格贴图作为背景。
     * 非编辑态显示优先级数值，编辑态显示缓冲区文字 + 闪烁光标。
     */
    private void renderPriorityBox(GuiGraphics g, int boxX, int centerY,
                                    String priorityStr, boolean editing, boolean dimmed, int boxW) {
        int boxY = centerY - EDIT_INPUT_H / 2;

        // 交叉渐变：常态贴图 ↔ 聚焦贴图
        NineSliceRegion normalSpec = INPUT_BOX_NINE_SLICE.withTheme();
        NineSliceRegion focusSpec = INPUT_BOX_NINE_SLICE.withTheme().withVOffset(INPUT_BOX_STATE_H);
        CrossFadeRenderer.render(priorityBoxAnim.getValue(),
                () -> SpriteRenderer.drawNineSlice(g, normalSpec, boxX, boxY, boxW, EDIT_INPUT_H),
                () -> SpriteRenderer.drawNineSlice(g, focusSpec, boxX, boxY, boxW, EDIT_INPUT_H));

        Minecraft mc = Minecraft.getInstance();
        int fontColor = ThemeManager.getTextColor();
        int textColor = editing ? fontColor : (dimmed ? (fontColor & 0xFFFFFF) | 0x60000000 : fontColor);
        int textX = boxX + 3;
        int textY = boxY + (EDIT_INPUT_H - mc.font.lineHeight) / 2;

        if (editing) {
            String text = editBuffer.toString();
            if (!text.isEmpty()) {
                String visible = TextRenderer.trimToWidth(mc.font, text, boxW - 8);
                TextRenderer.draw(g, visible, textX, textY, textColor);
            }

            // 闪烁光标
            long elapsed = System.currentTimeMillis() - editStartTime;
            if ((elapsed / CURSOR_BLINK_MS) % 2 == 0) {
                int cursorVisualX = mc.font.width(text.isEmpty() ? "0" :
                        text.substring(0, Math.min(editBuffer.length(), text.length())));
                int clampedCursor = Math.min(cursorVisualX, boxW - 8);
                g.fill(textX + clampedCursor, textY,
                        textX + clampedCursor + 1, textY + mc.font.lineHeight, 0xFFFFFFFF);
            }
        } else {
            if (priorityStr != null && !priorityStr.isEmpty()) {
                int textWidth = mc.font.width(priorityStr);
                int centeredTextX = boxX + (boxW - textWidth) / 2;
                TextRenderer.draw(g, priorityStr, centeredTextX, textY, textColor);
            }
        }
    }

    // ======================== 空状态提示 ========================

    private void renderEmptyHint(GuiGraphics g, int x, int y, int w, int h) {
        String hint = "No linked";
        int textColor = ThemeManager.getTextColor() & 0xFFFFFF | 0x60000000;
        int lineH = Minecraft.getInstance().font.lineHeight;
        TextRenderer.drawCentered(g, Minecraft.getInstance().font, hint,
                x + w / 2, y + (h - lineH) / 2, textColor);
    }

    // ======================== 文字按钮渲染（九宫格背景自动适配宽度）==================

    /**
     * 绘制文字按钮，背景使用九宫格贴图自动适配文字宽度。
     *
     * @param btnX      按钮左上角 X
     * @param btnY      按钮左上角 Y
     * @param text      按钮文字
     * @param textColor 文字颜色
     * @param hovered   是否悬浮
     */
    private void drawTextButton(GuiGraphics g, int btnX, int btnY, String text,
                                int textColor, boolean hovered) {
        int textW = Minecraft.getInstance().font.width(text);
        int btnW = textW + BTN_PAD_H * 2;
        NineSliceRegion slice = hovered
                ? BTN_NINE_SLICE.withTheme().withVOffset(BTN_STATE_H)
                : BTN_NINE_SLICE.withTheme();
        SpriteRenderer.drawNineSlice(g, slice, btnX, btnY, btnW, BTN_HEIGHT);
        int lineH = Minecraft.getInstance().font.lineHeight;
        TextRenderer.drawCentered(g, Minecraft.getInstance().font, text,
                btnX + btnW / 2, btnY + (BTN_HEIGHT - lineH) / 2, textColor);
    }

    // ======================== 物品图标渲染 ========================

    private void renderItemIcon(GuiGraphics g, ItemStack stack, int iconX, int iconY) {
        if (stack.isEmpty()) return;
        var pose = g.pose();
        pose.pushPose();
        float scale = (float) ICON_SIZE / 16.0f;
        pose.translate(iconX, iconY, 0);
        pose.scale(scale, scale, 1.0f);
        g.renderItem(stack, -8, -8);
        pose.popPose();
    }

    // ======================== 物品解析 ========================

    private static ItemStack resolveItemStack(String itemId) {
        if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    // ======================== 编辑状态管理 ========================

    /** 提交编辑——发送优先级更新包 */
    private void commitEdit() {
        if (!isEditing) return;
        String text = editBuffer.toString().trim();
        if (!text.isEmpty()) {
            try {
                int newPriority = Mth.clamp(Integer.parseInt(text), 0, 100);
                StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
                if (sm != null && editingIndex >= 0 && editingIndex < rowLayouts.size()) {
                    var entries = sm.getLinkedStorageEntries();
                    RowLayout rl = rowLayouts.get(editingIndex);
                    if (rl.originalIndex >= 0 && rl.originalIndex < entries.size()) {
                        LinkedStorageEntry entry = entries.get(rl.originalIndex);
                        RtsClientPacketGateway.sendUpdateLinkedStorage(entry.pos(), entry.isExtractOnly(), newPriority);
                        sm.requestPage(sm.getState().getPage());
                    }
                }
            } catch (NumberFormatException ignored) {}
        }
        cancelEdit();
    }

    /** 取消编辑——清空状态 */
    private void cancelEdit() {
        // 保存最后动画行信息供回退动画使用
        if (editingIndex >= 0 && editingIndex < rowLayouts.size()) {
            lastAnimRow = editingIndex;
            lastAnimBaseW = rowLayouts.get(editingIndex).priorityW;
        }
        isEditing = false;
        editingIndex = -1;
        editBuffer.setLength(0);
        // 平滑回退动画（FloatAnimation 从当前值缓出至 0）
        priorityBoxAnim.start(0f);
    }

    /**
     * 开始编辑指定条目的优先级。
     *
     * @param index    条目索引
     * @param priority 当前优先级值
     */
    private void beginEdit(int index, int priority) {
        editingIndex = index;
        isEditing = true;
        editBuffer.setLength(0);
        editBuffer.append(priority);
        editStartTime = System.currentTimeMillis();
        // 记录此行常态宽度供回退动画使用
        lastAnimRow = index;
        lastAnimBaseW = Minecraft.getInstance().font.width(String.valueOf(priority)) + PRIORITY_PAD_H * 2;
        // 平滑展开动画（FloatAnimation 从当前值缓出至 1）
        priorityBoxAnim.start(1f);
    }

    // ======================== 鼠标事件 ========================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // 如果正在编辑且点击不在输入框内 → 提交编辑
        if (isEditing) {
            int editBoxX = getX() + LEFT_PAD + ARROW_BTN_SIZE + PRIORITY_ICON_GAP;
            int editBoxY = getY() + TOP_PAD + editingIndex * ROW_H - scrollBar.getScroll();
            int editCenterY = editBoxY + 10;
            int boxTop = editCenterY - EDIT_INPUT_H / 2;
            if (!(mouseX >= editBoxX && mouseX < editBoxX + EDIT_INPUT_W
                    && mouseY >= boxTop && mouseY < boxTop + EDIT_INPUT_H)) {
                commitEdit();
                // 提交后继续检测其他点击（按钮、滚动条等）
            }
        }

        StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
        if (sm == null) return false;

        int mx = (int) mouseX;
        int my = (int) mouseY;

        // 检测滚动条点击
        int barX = getX() + getWidth() - SCROLLBAR_W - RIGHT_MARGIN;
        if (scrollBar.handleClick(mouseX, mouseY, barX, getY() + TOP_PAD + 6, getHeight() - TOP_PAD * 2 - 12)) {
            return true;
        }

        // 检测条目交互
        var entries = sm.getLinkedStorageEntries();
        var priorities = sm.getLinkedPriorities();
        int count = Math.min(entries.size(), Math.min(rowLayouts.size(), priorities.size()));
        for (int i = 0; i < count; i++) {
            RowLayout rl = rowLayouts.get(i);
            if (rl == null) continue;
            if (my < rl.y || my >= rl.y + 19) continue;
            int origIdx = rl.originalIndex;
            LinkedStorageEntry entry = entries.get(origIdx);

            // 箭头按钮点击 → 交换优先级
            if (mx >= rl.arrowBtnX && mx < rl.arrowBtnX + ARROW_BTN_SIZE) {
                int targetSortedIdx = (i == 0) ? i + 1 : i - 1;
                if (targetSortedIdx >= 0 && targetSortedIdx < count) {
                    RowLayout targetRl = rowLayouts.get(targetSortedIdx);
                    int currentPriority = priorities.get(origIdx);
                    int targetPriority = priorities.get(targetRl.originalIndex);
                    LinkedStorageEntry currentEntry = entries.get(origIdx);
                    LinkedStorageEntry targetEntry = entries.get(targetRl.originalIndex);

                    if (currentPriority == targetPriority) {
                        // 优先级相等时，通过 +1/-1 打破相等实现位置互换
                        if (i == 0) {
                            // 首行向下移动 → 优先级增加
                            int newPriority = Math.min(100, targetPriority + 1);
                            RtsClientPacketGateway.sendUpdateLinkedStorage(
                                    currentEntry.pos(), currentEntry.isExtractOnly(), newPriority);
                        } else {
                            // 非首行向上移动 → 优先级减小
                            int newPriority = Math.max(0, targetPriority - 1);
                            RtsClientPacketGateway.sendUpdateLinkedStorage(
                                    currentEntry.pos(), currentEntry.isExtractOnly(), newPriority);
                        }
                    } else {
                        // 不同优先级：交换两者
                        RtsClientPacketGateway.sendUpdateLinkedStorage(
                                currentEntry.pos(), currentEntry.isExtractOnly(), targetPriority);
                        RtsClientPacketGateway.sendUpdateLinkedStorage(
                                targetEntry.pos(), targetEntry.isExtractOnly(), currentPriority);
                    }
                    sm.requestPage(sm.getState().getPage());
                }
                return true;
            }

            // 优先级文本点击 → 进入编辑模式
            if (mx >= rl.priorityX && mx < rl.priorityX + rl.priorityW) {
                if (!isEditing || editingIndex != i) {
                    beginEdit(i, priorities.get(origIdx));
                }
                return true;
            }

            // 位置按钮——切换位置标记显示
            if (mx >= rl.locateBtnX && mx < rl.locateBtnX + rl.locateBtnW) {
                sm.toggleLocationDisplay(entry.pos());
                return true;
            }

            // 解绑按钮
            if (mx >= rl.unbindX && mx < rl.unbindX + rl.unbindW) {
                RtsClientPacketGateway.sendUnlinkStorage(entry.pos());
                sm.requestPage(sm.getState().getPage());
                return true;
            }

            // 模式切换按钮
            if (mx >= rl.toggleX && mx < rl.toggleX + rl.toggleW) {
                boolean nextExtractOnly = !entry.isExtractOnly();
                int currentPriority = priorities.get(origIdx);
                RtsClientPacketGateway.sendUpdateLinkedStorage(entry.pos(), nextExtractOnly, currentPriority);
                sm.requestPage(sm.getState().getPage());
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 编辑中滚轮提交编辑
        if (isEditing) {
            commitEdit();
        }
        return scrollBar.handleScroll(scrollY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (scrollBar.isDragging()) {
            scrollBar.endDrag();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) return false;
        if (scrollBar.isDragging()) {
            return scrollBar.handleDrag(mouseY, getY() + TOP_PAD, getHeight() - TOP_PAD * 2);
        }
        return false;
    }

    // ======================== 键盘事件 ========================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isEditing) return false;

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitEdit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelEdit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (editBuffer.length() > 0) {
                editBuffer.deleteCharAt(editBuffer.length() - 1);
            }
            return true;
        }
        // Tab 阻隔，防止焦点跳走
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!isEditing) return false;
        // 仅接受数字字符
        if (codePoint >= '0' && codePoint <= '9') {
            editBuffer.append(codePoint);
            return true;
        }
        return false;
    }
}
