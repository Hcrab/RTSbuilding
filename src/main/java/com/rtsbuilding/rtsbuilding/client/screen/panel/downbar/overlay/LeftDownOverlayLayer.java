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

import java.util.*;

/**
 * 下栏左嵌层——容器绑定显示面板。
 *
 * <p>显示所有已绑定的存储容器列表，每行包含：优先级编号（可点击编辑）、方块图标、
 * 容器名称，以及「解绑」「双向/仅提取」和「开启位置/关闭显示」三个操作按钮。
 * 右侧配有纵向滚动条，支持滚轮和拖拽；优先级编辑支持数字键盘输入与闪烁光标。</p>
 *
 * <p>内部职责由子控制器分离管理：</p>
 * <ul>
 *   <li>{@link PriorityEditController} —— 优先级编辑生命周期</li>
 *   <li>{@link EntryAnimationController} —— 条地位移动画与背景悬浮动画</li>
 * </ul>
 */
public final class LeftDownOverlayLayer extends DownOverlayLayer {

    // ======================== 布局常量 ========================

    private static final int ROW_H = 20;
    private static final int ICON_SIZE = 12;
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
    private static final int RIGHT_MARGIN = 4;
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
            "rtsbuilding:textures/gui/base/base_ui/base_ui_4.png");
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
            "rtsbuilding:textures/gui/base/base_ui/base_ui_2.png");
    private static final int BTN_TEX_W = 32;
    private static final int BTN_TEX_H = 48;
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
    private static final int ARROW_TEX_FILE_W = 1024;
    private static final int ARROW_TEX_H = 512;
    private static final TextureInfo ARROW_TEX_INFO = new TextureInfo(
            ARROW_TEXTURE, ARROW_TEX_FILE_W, ARROW_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);
    /** 单帧宽度 = 半区宽度（通过 halfWidth() 获取，避免硬编码 512） */
    private static final SpriteRegion ARROW_SPRITE = new SpriteRegion(
            ARROW_TEX_INFO, 0, 0, ARROW_TEX_INFO.halfWidth(), ARROW_TEX_H).withTheme();

    // ======================== 行背景贴图 ========================

    /** 行背景贴图（base_ui_7.png，32×48，水平双主题，垂直三分：0-16=偶数行、16-32=奇数行、32-48=悬浮） */
    private static final ResourceLocation BG_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_7.png");
    private static final int BG_TEX_W = 32;
    private static final int BG_TEX_H = 48;
    private static final int BG_STATE_H = 16;
    private static final int BG_BORDER = 4;
    private static final TextureInfo BG_TEX_INFO = new TextureInfo(
            BG_TEXTURE, BG_TEX_W, BG_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion BG_NINE_SLICE = NineSliceRegion.fullTheme(
            BG_TEX_INFO, BG_STATE_H, BG_BORDER);

    // ======================== 组件 ========================

    private final ScrollBar scrollBar = new ScrollBar();
    private final List<RowLayout> rowLayouts = new ArrayList<>();

    /** 优先级编辑控制器——负责编辑状态、键盘输入、动画宽度管理 */
    private final PriorityEditController editController = new PriorityEditController();
    /** 动画控制器——负责条目位移动画、背景条悬浮动画 */
    private final EntryAnimationController animController = new EntryAnimationController();

    // ======================== 内部数据结构 ========================

    /**
     * 右侧三按钮布局——所有行共用同一组按钮宽度和边界。
     * <p>每帧在渲染前由构造器计算一次，供所有行的按钮位置计算和点击检测使用。</p>
     */
    private record ButtonBar(int unbindW, int toggleW, int locateW, int btnAreaRight) {
        /** 切换按钮 X（始终右对齐） */
        int toggleX()  { return btnAreaRight - toggleW; }
        /** 解绑按钮 X（紧贴切换按钮左侧） */
        int unbindX()  { return toggleX() - BTN_GAP - unbindW; }
        /** 位置按钮 X（紧贴解绑按钮左侧） */
        int locateX()  { return unbindX() - BTN_GAP - locateW; }

        /**
         * 构建按钮条布局。
         *
         * @param mc              Minecraft 实例（用于测量文字宽度）
         * @param scrollBarVisible 滚动条是否可见（影响右侧可用空间）
         * @param parentX          父级 overlay 的 X
         * @param parentW          父级 overlay 的宽度
         */
        ButtonBar(Minecraft mc, boolean scrollBarVisible, int parentX, int parentW) {
            this(
                    mc.font.width("解绑") + BTN_PAD_H * 2,
                    Math.max(mc.font.width("双向"), mc.font.width("仅提取")) + BTN_PAD_H * 2,
                    Math.max(mc.font.width("开启位置"), mc.font.width("关闭显示")) + BTN_PAD_H * 2,
                    parentX + LEFT_PAD + (parentW - LEFT_PAD - SCROLLBAR_W - RIGHT_MARGIN)
                            - (scrollBarVisible ? 2 : 0) - 1
            );
        }
    }

    /**
     * 单行布局快照——存储每一行各元素的屏幕坐标，用于渲染和点击检测。
     * <p>每帧由 {@link #renderContent(GuiGraphics)} 重建，与滚动位置同步。</p>
     */
    private static final class RowLayout {
        /** 屏幕 Y 坐标 */
        int y;
        /** 箭头按钮 X */
        int arrowBtnX;
        /** 优先级点击区域起始 X */
        int priorityX;
        /** 优先级框实际宽度（含动画宽度） */
        int priorityW;
        /** 解绑按钮 X */
        int unbindX;
        /** 模式切换按钮 X */
        int toggleX;
        /** 解绑按钮宽度 */
        int unbindW;
        /** 模式切换按钮宽度 */
        int toggleW;
        /** 位置按钮 X */
        int locateBtnX;
        /** 位置按钮宽度 */
        int locateBtnW;
        /** 在原始列表中的索引 */
        int originalIndex;
    }

    // ======================== 核心渲染入口 ========================

    @Override
    protected void renderContent(GuiGraphics g) {
        // ---- 获取数据源 ----
        StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
        if (sm == null) return;

        var entries = sm.getLinkedStorageEntries();
        var names = sm.getLinkedDisplayNames();
        var iconIds = sm.getLinkedIconItemIds();
        var priorities = sm.getLinkedPriorities();
        int count = Math.min(entries.size(), Math.min(names.size(),
                Math.min(iconIds.size(), priorities.size())));
        if (count == 0) {
            renderEmptyHint(g);
            return;
        }

        // ---- 更新所有动画（每帧一次） ----
        editController.tick(count);
        animController.tick(count);

        // ---- 布局参数 ----
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int mouseX = getLastMouseX(), mouseY = getLastMouseY();
        Minecraft mc = Minecraft.getInstance();
        int visibleH = h - TOP_PAD * 2;

        scrollBar.setContent(count * ROW_H, visibleH);
        int scroll = scrollBar.getScroll();

        // ---- 第一遍：渲染背景条（与滚动联动，交叉淡入淡出悬浮）----
        renderBackgroundRows(g, x, y, w, count, scroll, visibleH, mouseX, mouseY);

        // ---- 按优先级升序排序（优先级越小越靠前）----
        List<Integer> sortedIndices = buildSortedIndices(count, priorities);

        // ---- 预计算按钮条布局（所有行共用）----
        ButtonBar btnBar = new ButtonBar(mc, scrollBar.isVisible(), x, w);
        int fontColor = ThemeManager.getTextColor();

        // ---- 第二遍：逐行渲染条目内容 ----
        rowLayouts.clear();
        int clipY = y + TOP_PAD;
        for (int vi = 0; vi < count; vi++) {
            int origIdx = sortedIndices.get(vi);
            RowLayout rl = new RowLayout();
            rl.originalIndex = origIdx;
            rowLayouts.add(rl);

            renderSingleRow(g, x, y, scroll, vi, origIdx, rl, entries, names, iconIds, priorities,
                    btnBar, fontColor, mc, mouseX, mouseY, clipY, visibleH);
        }

        // ---- 滚动条（首尾各缩 6px）----
        renderScrollbar(g, x, y, h);
    }

    // ======================== 背景条渲染 ========================

    /**
     * 渲染所有背景行，含偶数行/奇数行交替贴图和悬浮交叉淡入淡出。
     *
     * @param g           GuiGraphics
     * @param x           overlay 左上角 X
     * @param y           overlay 左上角 Y
     * @param w           overlay 宽度
     * @param count       条目总数（超出此数量的行不渲染悬浮效果）
     * @param scroll      当前滚动偏移量
     * @param visibleH    可见区域高度
     * @param mouseX      鼠标 X
     * @param mouseY      鼠标 Y
     */
    private void renderBackgroundRows(GuiGraphics g, int x, int y, int w, int count,
                                       int scroll, int visibleH, int mouseX, int mouseY) {
        int firstRow = scroll / ROW_H;
        int totalRows = visibleH / ROW_H + 2;
        for (int i = firstRow; i < firstRow + totalRows; i++) {
            int bgTop = y + TOP_PAD + i * ROW_H - scroll;
            boolean hovered = !isDividerDragging() && i < count
                    && mouseX >= x && mouseX < x + w
                    && mouseY >= bgTop && mouseY < bgTop + ROW_H;

            float barHoverT = animController.tickBarHover(i, hovered);
            int baseVOffset = (i % 2 == 0) ? 0 : BG_STATE_H;
            NineSliceRegion normalSlice = BG_NINE_SLICE.withTheme().withVOffset(baseVOffset);
            NineSliceRegion hoveredSlice = BG_NINE_SLICE.withTheme().withVOffset(BG_STATE_H * 2);
            CrossFadeRenderer.render(barHoverT,
                    () -> SpriteRenderer.drawNineSlice(g, normalSlice, x, bgTop, w, ROW_H),
                    () -> SpriteRenderer.drawNineSlice(g, hoveredSlice, x, bgTop, w, ROW_H));
        }
    }

    // ======================== 单行条目渲染 ========================

    /**
     * 渲染单个完整条目行，按从左到右顺序绘制：箭头按钮 → 优先级 → 方块图标 → 名称 → 三操作按钮。
     * <p>非可见行跳过渲染但保留布局数据，编辑行强制可见。</p>
     */
    private void renderSingleRow(GuiGraphics g, int x, int y, int scroll,
                                  int vi, int origIdx, RowLayout rl,
                                  List<LinkedStorageEntry> entries, List<String> names,
                                  List<String> iconIds, List<Integer> priorities,
                                  ButtonBar btnBar, int fontColor, Minecraft mc,
                                  int mouseX, int mouseY, int clipY, int clipH) {
        int lineH = mc.font.lineHeight;

        // ---- Y 坐标计算（含排序位移动画）----
        int baseRowY = TOP_PAD + vi * ROW_H;
        float animY = animController.updateEntryAnimY(origIdx, baseRowY);
        int contentY = y + Math.round(animY) - scroll;
        rl.y = contentY;

        // 可见性判断（编辑行强制可见）
        boolean rowVisible = contentY + ROW_H >= clipY && contentY < clipY + clipH;
        boolean isEditingRow = editController.isEditingRow(vi);
        boolean actuallyRender = rowVisible || isEditingRow;

        // 当前行数据
        LinkedStorageEntry entry = entries.get(origIdx);
        String name = names.get(origIdx);
        String iconItemId = iconIds.get(origIdx);
        int priority = priorities.get(origIdx);
        boolean dimmed = !entry.worldAvailable();

        int rowCenterY = contentY + ROW_H / 2;
        int cursorX = x + LEFT_PAD;

        // ---- 0. 优先级调整箭头按钮 ----
        int arrowBtnY = rowCenterY - ARROW_BTN_SIZE / 2;
        rl.arrowBtnX = cursorX;
        if (actuallyRender) {
            renderArrowButton(g, cursorX, arrowBtnY, vi == 0);
        }
        cursorX += ARROW_BTN_SIZE + PRIORITY_ICON_GAP;
        rl.priorityX = cursorX;

        // ---- 1. 优先级（输入框风格，含动画宽度展开/回缩）----
        int priorityBoxW = mc.font.width(String.valueOf(priority)) + PRIORITY_PAD_H * 2;
        rl.priorityW = priorityBoxW;
        float animW = editController.computePriorityBoxWidth(priorityBoxW, isEditingRow, vi);
        if (actuallyRender || isEditingRow) {
            renderPriorityBox(g, cursorX, rowCenterY, String.valueOf(priority),
                    isEditingRow, dimmed, (int) animW);
        }
        cursorX += (int) animW + PRIORITY_ICON_GAP;

        // ---- 2. 方块图标 ----
        if (actuallyRender && !iconItemId.isEmpty()) {
            ItemStack stack = resolveItemStack(iconItemId);
            if (!stack.isEmpty()) {
                renderItemIcon(g, stack, cursorX + ICON_SIZE / 2, rowCenterY);
            }
        }
        cursorX += ICON_SIZE;

        // ---- 3. 容器名称（从光标到按钮区左边界之间，超出截断）----
        if (actuallyRender) {
            int maxNameW = Math.max(0, btnBar.locateX() - cursorX - ICON_TEXT_GAP - BTN_GAP);
            String displayName = TextRenderer.trimToWidth(mc.font, name, maxNameW);
            int nameX = cursorX + ICON_TEXT_GAP;
            int nameColor = dimmed ? (fontColor & 0xFFFFFF) | 0x60000000 : fontColor;
            TextRenderer.draw(g, displayName, nameX, rowCenterY - lineH / 2, nameColor);
        }

        // ---- 4-6. 右侧三操作按钮：位置 / 解绑 / 模式切换 ----
        if (actuallyRender) {
            renderActionButtons(g, entry, rl, btnBar, rowCenterY, mouseX, mouseY);
        }
    }

    /** 绘制箭头按钮——九宫格背景 + 指向图标，首行箭头朝下，其余朝上 */
    private void renderArrowButton(GuiGraphics g, int btnX, int btnY, boolean isFirst) {
        SpriteRenderer.drawNineSlice(g, BTN_NINE_SLICE.withTheme(),
                btnX, btnY, ARROW_BTN_SIZE, ARROW_BTN_SIZE);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(btnX + ARROW_BTN_SIZE / 2, btnY + ARROW_BTN_SIZE / 2, 0);
        if (isFirst) {
            pose.mulPose(Axis.ZP.rotationDegrees(180f));
        }
        SpriteRenderer.drawSprite(g, ARROW_SPRITE,
                -ARROW_DRAW_SIZE / 2, -ARROW_DRAW_SIZE / 2,
                ARROW_DRAW_SIZE, ARROW_DRAW_SIZE);
        pose.popPose();
    }

    /**
     * 绘制右侧三操作按钮（位置 / 解绑 / 模式切换）。
     * <p>三个按钮均使用 {@link #drawTextButton}，以纯色文字 + 九宫格背景渲染。</p>
     */
    private void renderActionButtons(GuiGraphics g, LinkedStorageEntry entry, RowLayout rl,
                                      ButtonBar btnBar, int rowCenterY, int mouseX, int mouseY) {
        int btnY = rowCenterY - BTN_HEIGHT / 2;

        // 位置按钮
        String locateText;
        if (entry.worldAvailable()) {
            StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
            boolean showLocate = sm != null && sm.isLocationDisplayActive(entry.pos());
            locateText = showLocate ? "关闭显示" : "开启位置";
        } else {
            locateText = "开启位置";
        }
        int locateBtnW = mc().font.width(locateText) + BTN_PAD_H * 2;
        int locateBtnX = btnBar.locateX();
        rl.locateBtnX = locateBtnX;
        rl.locateBtnW = locateBtnW;
        boolean hoverLocate = !isDividerDragging()
                && inRect(mouseX, mouseY, locateBtnX, btnY, locateBtnW, BTN_HEIGHT);
        drawTextButton(g, locateBtnX, btnY, locateText,
                hoverLocate ? LOCATE_BTN_HOVER_COLOR : LOCATE_BTN_COLOR);

        // 解绑按钮
        String unbindText = "解绑";
        int unbindBtnW = mc().font.width(unbindText) + BTN_PAD_H * 2;
        int unbindX = btnBar.unbindX();
        rl.unbindX = unbindX;
        rl.unbindW = unbindBtnW;
        boolean hoverUnbind = !isDividerDragging()
                && inRect(mouseX, mouseY, unbindX, btnY, unbindBtnW, BTN_HEIGHT);
        drawTextButton(g, unbindX, btnY, unbindText,
                hoverUnbind ? UNBIND_HOVER_COLOR : UNBIND_COLOR);

        // 模式切换按钮
        String toggleText = entry.isExtractOnly() ? "仅提取" : "双向";
        int toggleBtnW = mc().font.width(toggleText) + BTN_PAD_H * 2;
        int toggleX = btnBar.toggleX();
        rl.toggleX = toggleX;
        rl.toggleW = toggleBtnW;
        boolean hoverToggle = !isDividerDragging()
                && inRect(mouseX, mouseY, toggleX, btnY, toggleBtnW, BTN_HEIGHT);
        int toggleColor = entry.isExtractOnly() ? MODE_EXTRACT_COLOR : MODE_BI_COLOR;
        drawTextButton(g, toggleX, btnY, toggleText,
                hoverToggle ? BTN_HOVER_FG : toggleColor);
    }

    /**
     * 绘制文字按钮——九宫格贴图自动适配文字宽度，居中显示文字。
     * <p>悬浮态使用带高亮偏移的贴图状态。</p>
     */
    private void drawTextButton(GuiGraphics g, int btnX, int btnY, String text, int textColor) {
        int btnW = mc().font.width(text) + BTN_PAD_H * 2;
        // 根据文字颜色隐式推断悬浮态（调用方已在入参中传入对应的悬浮颜色）
        NineSliceRegion slice = textColor == UNBIND_HOVER_COLOR
                || textColor == LOCATE_BTN_HOVER_COLOR
                || textColor == BTN_HOVER_FG
                ? BTN_NINE_SLICE.withTheme().withVOffset(BTN_STATE_H)
                : BTN_NINE_SLICE.withTheme();
        SpriteRenderer.drawNineSlice(g, slice, btnX, btnY, btnW, BTN_HEIGHT);
        int lineH = mc().font.lineHeight;
        TextRenderer.drawCentered(g, mc().font, text,
                btnX + btnW / 2, btnY + (BTN_HEIGHT - lineH) / 2, textColor);
    }

    // ======================== 优先级框编辑渲染 ========================

    /**
     * 渲染优先级输入框——使用 base_ui_4.png 九宫格贴图作为背景。
     * <p>非编辑态居中显示数字，编辑态显示缓冲区文本 + 闪烁光标，
     * 常态/聚焦态贴图之间以交叉渐变过渡。</p>
     */
    private void renderPriorityBox(GuiGraphics g, int boxX, int centerY,
                                    String priorityStr, boolean editing, boolean dimmed, int boxW) {
        int boxY = centerY - EDIT_INPUT_H / 2;

        // 交叉渐变：常态贴图 ↔ 聚焦贴图
        float crossFadeT = editController.getAnimValue();
        NineSliceRegion normalSpec = INPUT_BOX_NINE_SLICE.withTheme();
        NineSliceRegion focusSpec = INPUT_BOX_NINE_SLICE.withTheme().withVOffset(INPUT_BOX_STATE_H);
        CrossFadeRenderer.render(crossFadeT,
                () -> SpriteRenderer.drawNineSlice(g, normalSpec, boxX, boxY, boxW, EDIT_INPUT_H),
                () -> SpriteRenderer.drawNineSlice(g, focusSpec, boxX, boxY, boxW, EDIT_INPUT_H));

        Minecraft mc = mc();
        int fontColor = ThemeManager.getTextColor();
        int textColor = editing ? fontColor : (dimmed ? (fontColor & 0xFFFFFF) | 0x60000000 : fontColor);
        int textX = boxX + 3;
        int textY = boxY + (EDIT_INPUT_H - mc.font.lineHeight) / 2;

        if (editing) {
            // 编辑态：显示缓冲区 + 闪烁光标
            String text = editController.getBufferText();
            if (!text.isEmpty()) {
                String visible = TextRenderer.trimToWidth(mc.font, text, boxW - 8);
                TextRenderer.draw(g, visible, textX, textY, textColor);
            }
            long elapsed = System.currentTimeMillis() - editController.getStartTime();
            if ((elapsed / CURSOR_BLINK_MS) % 2 == 0) {
                int cursorVisualX = mc.font.width(text.isEmpty() ? "0"
                        : text.substring(0, Math.min(editController.getBufferLength(), text.length())));
                int clampedX = Math.min(cursorVisualX, boxW - 8);
                g.fill(textX + clampedX, textY,
                        textX + clampedX + 1, textY + mc.font.lineHeight, 0xFFFFFFFF);
            }
        } else if (priorityStr != null && !priorityStr.isEmpty()) {
            // 非编辑态：居中显示优先级数字
            int textWidth = mc.font.width(priorityStr);
            int centeredTextX = boxX + (boxW - textWidth) / 2;
            TextRenderer.draw(g, priorityStr, centeredTextX, textY, textColor);
        }
    }

    // ======================== 辅助渲染方法 ========================

    /** 空状态提示文本 */
    private void renderEmptyHint(GuiGraphics g) {
        String hint = "No linked";
        int textColor = ThemeManager.getTextColor() & 0xFFFFFF | 0x60000000;
        int lineH = mc().font.lineHeight;
        TextRenderer.drawCentered(g, mc().font, hint,
                getX() + getWidth() / 2, getY() + (getHeight() - lineH) / 2, textColor);
    }

    /** 在指定中心点渲染 12×12 的物品图标 */
    private void renderItemIcon(GuiGraphics g, ItemStack stack, int centerX, int centerY) {
        if (stack.isEmpty()) return;
        var pose = g.pose();
        pose.pushPose();
        float scale = (float) ICON_SIZE / 16.0f;
        pose.translate(centerX, centerY, 0);
        pose.scale(scale, scale, 1.0f);
        g.renderItem(stack, -8, -8);
        pose.popPose();
    }

    /** 滚动条渲染（首尾各缩 6px 留出视觉边距） */
    private void renderScrollbar(GuiGraphics g, int x, int y, int h) {
        int visibleH = h - TOP_PAD * 2;
        int barX = x + getWidth() - SCROLLBAR_W - RIGHT_MARGIN;
        scrollBar.render(g, barX, y + TOP_PAD + 6, visibleH - 12);
    }

    // ======================== 布局计算辅助 ========================

    /**
     * 构建按优先级升序排列的原始索引列表。
     *
     * @param count      条目总数
     * @param priorities 优先级列表
     * @return 排序后的原始索引列表（优先级小的靠前）
     */
    private static List<Integer> buildSortedIndices(int count, List<Integer> priorities) {
        List<Integer> sorted = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            sorted.add(i);
        }
        sorted.sort(Comparator.comparingInt(priorities::get));
        return sorted;
    }

    // ======================== 物品解析 ========================

    /** 根据物品 ID 字符串解析为 ItemStack */
    private static ItemStack resolveItemStack(String itemId) {
        if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    // ======================== 矩形区域检测 ========================

    /** 检测 (px, py) 是否在指定矩形区域内 */
    private static boolean inRect(int px, int py, int rx, int ry, int rw, int rh) {
        return px >= rx && px < rx + rw && py >= ry && py < ry + rh;
    }

    /** 获取 Minecraft 实例（简化写法） */
    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    // ======================== 鼠标事件 ========================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int mx = (int) mouseX;
        int my = (int) mouseY;

        // 编辑态下，若点击在输入框外部则提交编辑
        if (editController.isEditing()
                && !editController.isClickOnEditBox(mx, my, getX(), getY(), scrollBar.getScroll())) {
            editController.tryCommit();
        }

        StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
        if (sm == null) return false;

        // 滚动条点击
        int barX = getX() + getWidth() - SCROLLBAR_W - RIGHT_MARGIN;
        if (scrollBar.handleClick(mouseX, mouseY, barX,
                getY() + TOP_PAD + 6, getHeight() - TOP_PAD * 2 - 12)) {
            return true;
        }

        // 检测条目中各元素的点击
        return handleRowClick(mx, my, sm);
    }

    /**
     * 遍历所有可见行，检测点击命中的元素并执行对应操作。
     * <p>检测优先级：箭头按钮 > 优先级文本 > 位置按钮 > 解绑按钮 > 模式切换按钮。</p>
     */
    private boolean handleRowClick(int mx, int my, StorageModule sm) {
        var entries = sm.getLinkedStorageEntries();
        var priorities = sm.getLinkedPriorities();
        int count = Math.min(entries.size(), Math.min(rowLayouts.size(), priorities.size()));

        for (int i = 0; i < count; i++) {
            RowLayout rl = rowLayouts.get(i);
            if (rl == null) continue;
            if (my < rl.y || my >= rl.y + ROW_H - 1) continue;

            int origIdx = rl.originalIndex;
            LinkedStorageEntry entry = entries.get(origIdx);

            // 箭头按钮——交换优先级
            if (inRect(mx, my, rl.arrowBtnX, rl.y, ARROW_BTN_SIZE, ROW_H)) {
                handleArrowSwap(i, count, entries, priorities, rowLayouts);
                return true;
            }

            // 优先级文本——进入编辑模式
            if (inRect(mx, my, rl.priorityX, rl.y, rl.priorityW, ROW_H)) {
                if (!editController.isEditing() || editController.getEditingIndex() != i) {
                    editController.beginEdit(i, priorities.get(origIdx));
                }
                return true;
            }

            // 位置按钮——切换位置标记显示
            if (inRect(mx, my, rl.locateBtnX, rl.y, rl.locateBtnW, ROW_H)) {
                sm.toggleLocationDisplay(entry.pos());
                return true;
            }

            // 解绑按钮——发送解绑包
            if (inRect(mx, my, rl.unbindX, rl.y, rl.unbindW, ROW_H)) {
                RtsClientPacketGateway.sendUnlinkStorage(entry.pos());
                return true;
            }

            // 模式切换按钮——切换双向/仅提取
            if (inRect(mx, my, rl.toggleX, rl.y, rl.toggleW, ROW_H)) {
                boolean nextExtractOnly = !entry.isExtractOnly();
                RtsClientPacketGateway.sendUpdateLinkedStorage(
                        entry.pos(), nextExtractOnly, priorities.get(origIdx));
                return true;
            }
        }
        return false;
    }

    /**
     * 处理箭头按钮点击——与相邻条目交换优先级排序位置。
     * <p>若优先级相等则微调 ±1；若不等则直接交换。</p>
     */
    private void handleArrowSwap(int sortedIdx, int count, List<LinkedStorageEntry> entries,
                                  List<Integer> priorities, List<RowLayout> layouts) {
        int targetIdx = (sortedIdx == 0) ? sortedIdx + 1 : sortedIdx - 1;
        if (targetIdx < 0 || targetIdx >= count) return;

        RowLayout currentRl = layouts.get(sortedIdx);
        RowLayout targetRl = layouts.get(targetIdx);

        int currentPriority = priorities.get(currentRl.originalIndex);
        int targetPriority = priorities.get(targetRl.originalIndex);
        LinkedStorageEntry currentEntry = entries.get(currentRl.originalIndex);
        LinkedStorageEntry targetEntry = entries.get(targetRl.originalIndex);

        if (currentPriority == targetPriority) {
            // 优先级相等时，通过 ±1 打破平局实现位置互换
            int newPriority = (sortedIdx == 0)
                    ? Math.min(100, targetPriority + 1)
                    : Math.max(0, targetPriority - 1);
            RtsClientPacketGateway.sendUpdateLinkedStorage(
                    currentEntry.pos(), currentEntry.isExtractOnly(), newPriority);
        } else {
            RtsClientPacketGateway.sendUpdateLinkedStorage(
                    currentEntry.pos(), currentEntry.isExtractOnly(), targetPriority);
            RtsClientPacketGateway.sendUpdateLinkedStorage(
                    targetEntry.pos(), targetEntry.isExtractOnly(), currentPriority);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (editController.isEditing()) {
            editController.tryCommit();
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
        return editController.handleKeyPressed(keyCode);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return editController.handleCharTyped(codePoint);
    }

    // ============================================================
    //  优先级编辑控制器
    //  职责：编辑状态、缓冲区、键盘输入、动画宽度、光标闪烁
    //  非静态内嵌类——需访问外部类的 rowLayouts 字段
    // ============================================================

    /**
     * 优先级编辑控制器——管理优先级数值的内联编辑生命周期。
     *
     * <p>处理编辑状态的进入/提交/取消、数字键盘输入与退格、闪烁光标时间戳、</p>
     * <ul>
     *   <li>优先级框宽度动画（常态 ↔ EDIT_INPUT_W 之间的 FloatAnimation 过渡）</li>
     *   <li>编辑框点击区域检测（用于判断是否应失焦提交）</li>
     *   <li>条目越界时自动取消编辑</li>
     * </ul>
     */
    private final class PriorityEditController {

        /** 正在编辑的视觉行号，-1 表示未编辑 */
        private int editingIndex = -1;
        /** 编辑缓冲区 */
        private final StringBuilder editBuffer = new StringBuilder();
        /** 进入编辑时的时间戳（用于光标闪烁） */
        private long editStartTime;
        /** 当前是否正在编辑 */
        private boolean isEditing;

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

        // ======================== 生命周期 ========================

        /**
         * 开始编辑指定条目的优先级。
         *
         * @param rowIndex 视觉行号
         * @param priority 当前优先级值
         */
        void beginEdit(int rowIndex, int priority) {
            editingIndex = rowIndex;
            isEditing = true;
            editBuffer.setLength(0);
            editBuffer.append(priority);
            editStartTime = System.currentTimeMillis();
            // 记录此行常态宽度供回退动画使用
            lastAnimRow = rowIndex;
            lastAnimBaseW = mc().font.width(String.valueOf(priority)) + PRIORITY_PAD_H * 2;
            priorityBoxAnim.start(1f); // 动画展开至编辑宽度
        }

        /**
         * 尝试提交编辑——解析缓冲区数值、发送网络包、取消编辑。
         * <p>若缓冲区为空或解析失败，仅取消编辑不发送任何包。</p>
         */
        void tryCommit() {
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
                            RtsClientPacketGateway.sendUpdateLinkedStorage(
                                    entry.pos(), entry.isExtractOnly(), newPriority);
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
            doCancel();
        }

        /** 取消编辑——清空编辑状态并启动回退动画 */
        void doCancel() {
            if (editingIndex >= 0 && editingIndex < rowLayouts.size()) {
                lastAnimRow = editingIndex;
                lastAnimBaseW = rowLayouts.get(editingIndex).priorityW;
            }
            isEditing = false;
            editingIndex = -1;
            editBuffer.setLength(0);
            priorityBoxAnim.start(0f); // 动画回缩至常态
        }

        /**
         * 每帧更新动画并清理过期状态。
         *
         * @param count 当前条目总数（用于越界检测）
         */
        void tick(int count) {
            priorityBoxAnim.tick();
            if (!priorityBoxAnim.isRunning() && !isEditing) {
                lastAnimRow = -1;
            }
            if (isEditing && editingIndex >= count) {
                doCancel();
            }
        }

        // ======================== 编辑状态查询 ========================

        boolean isEditing() { return isEditing; }

        /** 判断指定行是否为当前编辑行 */
        boolean isEditingRow(int rowIndex) { return isEditing && rowIndex == editingIndex; }

        int getEditingIndex() { return editingIndex; }

        String getBufferText() { return editBuffer.toString(); }

        int getBufferLength() { return editBuffer.length(); }

        long getStartTime() { return editStartTime; }

        float getAnimValue() { return priorityBoxAnim.getValue(); }

        // ======================== 动画宽度计算 ========================

        /**
         * 计算优先级框的显示宽度——编辑行从常态化宽度动画展开至 {@link #EDIT_INPUT_W}，
         * 刚结束编辑的行反向动画回缩。
         *
         * @param normalW      常态化宽度（由文字宽度决定）
         * @param isEditingRow 当前行是否处于编辑态
         * @param rowIndex     行号
         * @return 当前帧应使用的宽度
         */
        float computePriorityBoxWidth(int normalW, boolean isEditingRow, int rowIndex) {
            boolean applyAnim = isEditingRow || (rowIndex == lastAnimRow && lastAnimRow >= 0);
            if (!applyAnim) return normalW;
            float baseW = isEditingRow ? normalW : lastAnimBaseW;
            return baseW + (EDIT_INPUT_W - baseW) * priorityBoxAnim.getValue();
        }

        // ======================== 点击区域检测 ========================

        /**
         * 判断点击位置是否在编辑框区域内。
         *
         * @param mx       鼠标 X
         * @param my       鼠标 Y
         * @param parentX  overlay 左上角 X
         * @param parentY  overlay 左上角 Y
         * @param scroll   当前滚动偏移量
         * @return 点击在编辑框内返回 true
         */
        boolean isClickOnEditBox(int mx, int my, int parentX, int parentY, int scroll) {
            int editBoxX = parentX + LEFT_PAD + ARROW_BTN_SIZE + PRIORITY_ICON_GAP;
            int editBoxY = parentY + TOP_PAD + editingIndex * ROW_H - scroll + ROW_H / 2;
            int boxTop = editBoxY - EDIT_INPUT_H / 2;
            return inRect(mx, my, editBoxX, boxTop, EDIT_INPUT_W, EDIT_INPUT_H);
        }

        // ======================== 键盘输入 ========================

        /**
         * 处理按键按下事件（仅在编辑态下生效）。
         *
         * @param keyCode 按键代码
         * @return true 表示事件已消费
         */
        boolean handleKeyPressed(int keyCode) {
            if (!isEditing) return false;

            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                tryCommit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                doCancel();
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

        /**
         * 处理字符输入事件（仅在编辑态下生效）。
         *
         * @param codePoint Unicode 字符
         * @return true 表示事件已消费
         */
        boolean handleCharTyped(char codePoint) {
            if (!isEditing) return false;
            if (codePoint >= '0' && codePoint <= '9') {
                editBuffer.append(codePoint);
                return true;
            }
            return false;
        }
    }

    // ============================================================
    //  动画控制器
    //  职责：条目 Y 坐标位移动画、背景条悬浮交叉淡入淡出
    //  静态内嵌类——不依赖外部类实例，纯数据+方法
    // ============================================================

    /**
     * 动画控制器——管理条目位移动画与背景悬浮动画。
     *
     * <p>包含两种动画：</p>
     * <ol>
     *   <li><b>条目 Y 坐标动画</b>（{@link #entryContentY}）——优先级变动导致排序变化时，
     *       条目平滑过渡到新位置，使用指数平滑算法，对排序位移生效但滚动即时。</li>
     *   <li><b>背景条悬浮动画</b>（{@link #barHoverProgress}）——鼠标悬停行背景时，
     *       交叉淡入淡出到悬浮态，使用指数平滑算法。</li>
     * </ol>
     */
    private static final class EntryAnimationController {

        /** 每条目的内容 Y 坐标动画（key=原始索引，value=当前帧的视觉 Y） */
        private final Map<Integer, Float> entryContentY = new HashMap<>();

        /** 每根背景条的悬浮动画进度（key=视觉行号，value=[0,1]） */
        private final Map<Integer, Float> barHoverProgress = new HashMap<>();
        /** 每根背景条当前是否处于悬浮态 */
        private final Map<Integer, Boolean> barHoverState = new HashMap<>();

        /** 指数平滑系数（条目位移动画） */
        private static final float ENTRY_SMOOTH_FACTOR = 0.15f;
        /** 指数平滑系数（背景悬浮动画） */
        private static final float BAR_HOVER_SMOOTH_FACTOR = 0.28f;
        /** 动画终止阈值 */
        private static final float EPSILON = 0.001f;

        /**
         * 每帧更新：清理已移除条目的动画状态。
         *
         * @param count 当前条目总数（仅保留索引 < count 的状态）
         */
        void tick(int count) {
            if (entryContentY.size() > count) {
                entryContentY.keySet().removeIf(key -> key >= count);
            }
        }

        /**
         * 更新指定条目的内容 Y 坐标动画。
         * <p>指数平滑：新值 = 旧值 + (目标 - 旧值) * 系数，距目标不足阈值时直接跳到目标。</p>
         *
         * @param origIdx     原始索引
         * @param targetBaseY 目标基础 Y（相对于 overlay 局部坐标）
         * @return 当前帧的动画 Y（局部坐标，需叠加 overlay 偏移和滚动）
         */
        float updateEntryAnimY(int origIdx, int targetBaseY) {
            Float animY = entryContentY.get(origIdx);
            if (animY == null) {
                animY = (float) targetBaseY;
            } else {
                animY += (targetBaseY - animY) * ENTRY_SMOOTH_FACTOR;
                if (Math.abs(animY - targetBaseY) < 0.5f) {
                    animY = (float) targetBaseY;
                }
            }
            entryContentY.put(origIdx, animY);
            return animY;
        }

        /**
         * 获取背景条交叉渐变动画的当前进度，并自动向目标值过渡。
         * <p>首次调用的行直接跳到目标值，后续使用指数平滑过渡。</p>
         *
         * @param barIndex    背景条视觉行号
         * @param shouldHover 当前帧是否应悬浮
         * @return 动画进度 [0, 1]，0=常态，1=悬浮态
         */
        float tickBarHover(int barIndex, boolean shouldHover) {
            Float progress = barHoverProgress.get(barIndex);
            if (progress == null) {
                float val = shouldHover ? 1f : 0f;
                barHoverProgress.put(barIndex, val);
                barHoverState.put(barIndex, shouldHover);
                return val;
            }
            Boolean prevState = barHoverState.get(barIndex);
            if (prevState == null || prevState != shouldHover) {
                barHoverState.put(barIndex, shouldHover);
            }
            float target = shouldHover ? 1f : 0f;
            progress += (target - progress) * BAR_HOVER_SMOOTH_FACTOR;
            if (Math.abs(progress - target) < EPSILON) {
                progress = target;
            }
            barHoverProgress.put(barIndex, progress);
            return progress;
        }
    }
}
