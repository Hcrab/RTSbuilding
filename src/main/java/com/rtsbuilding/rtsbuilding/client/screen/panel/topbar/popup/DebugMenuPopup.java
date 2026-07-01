package com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.popup;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.BasePopup;
import com.rtsbuilding.rtsbuilding.client.util.*;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 调试选项弹出菜单——点击 button_right 后显示。
 *
 * <p>集成了调试状态管理（区块边框、碰撞箱等辅助显示功能）、
 * 渲染同步、RTS 模式生命周期钩子和持久化属性，
 * 是一个自包含的调试功能组件。</p>
 */
public final class DebugMenuPopup extends BasePopup {

    // ======================== 调试状态字段 ========================

    /** 辅助显示模式总开关（默认关闭） */
    private boolean debugOverlayEnabled = false;
    /** 区块边框显示（默认开启） */
    private boolean chunkBorderVisible = true;
    /** 碰撞箱显示（默认开启） */
    private boolean collisionBoxVisible = true;

    /** 是否已调用 switchRenderChunkborder() 开启区块边框渲染 */
    private boolean chunkBorderRenderingActive;
    /** 是否已通过 setRenderHitBoxes(true) 开启碰撞箱渲染 */
    private boolean collisionBoxRenderingActive;

    // ======================== 菜单项数据 ========================

    /** 单个选项项：标签 + 切换回调 */
    public record DebugToggleItem(Component label, ToggleAction action) {}

    @FunctionalInterface
    public interface ToggleAction {
        void onToggle(boolean newState);
    }

    private final DebugToggleItem[] items;
    private final boolean[] states;

    // ======================== 外观常量 ========================

    /** 模式按钮贴图（1024×1536，横向双主题，纵向 3 状态：正常/悬浮/按下） */
    private static final ResourceLocation MODE_BUTTON_TEXTURE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/base/mode_button.png");
    private static final int MODE_BTN_TEX_W = 1024;
    private static final int MODE_BTN_TEX_H = 1536;
    /** 单主题半区宽度 */
    private static final int MODE_BTN_HALF_W = 512;
    /** 单个状态高度 */
    private static final int MODE_BTN_STATE_H = 512;
    /** 按钮绘制尺寸 */
    private static final int MODE_BTN_SIZE = 12;

    /** 按钮与文字的间距 */
    private static final int BTN_TEXT_GAP = 4;

    // ======================== 构造 ========================

    public DebugMenuPopup() {
        List<DebugToggleItem> itemsList = new ArrayList<>();

        // 1) 区块显示（默认勾选）
        itemsList.add(new DebugToggleItem(
                Component.translatable("screen.rtsbuilding.debug.chunk_border"),
                state -> {
                    boolean was = this.chunkBorderVisible;
                    this.chunkBorderVisible = state;
                    // 仅当 debug 总开关开启且状态发生变化时同步实际的区块边框渲染
                    if (this.debugOverlayEnabled && was != state) {
                        syncChunkBorder(state);
                    }
                }));

        // 2) 碰撞箱显示
        itemsList.add(new DebugToggleItem(
                Component.translatable("screen.rtsbuilding.debug.collision_box"),
                state -> {
                    boolean was = this.collisionBoxVisible;
                    this.collisionBoxVisible = state;
                    // 仅当 debug 总开关开启且状态发生变化时同步实际渲染
                    if (this.debugOverlayEnabled && was != state) {
                        syncCollisionBox(state);
                    }
                }));

        this.items = itemsList.toArray(new DebugToggleItem[0]);
        this.states = new boolean[]{true, true};

        // 自动计算每个菜单项的内容宽度（复选框 + 间距 + 文字）
        var font = Minecraft.getInstance().font;
        int[] contentWidths = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            contentWidths[i] = MODE_BTN_SIZE + BTN_TEXT_GAP + font.width(items[i].label());
        }
        setItemContentWidths(contentWidths);

        initAnims(items.length);
    }

    // ======================== Getter ========================

    public boolean isDebugOverlayEnabled() { return debugOverlayEnabled; }

    // ======================== 外部控制 ========================

    /** 切换辅助显示模式总开关并同步实际渲染。 */
    public void toggleDebugOverlay() {
        debugOverlayEnabled = !debugOverlayEnabled;
        if (debugOverlayEnabled) {
            enableAllDebugFeatures();
        } else {
            disableAllDebugFeatures();
        }
    }

    /**
     * 从外部设置指定索引的选项状态（用于持久化恢复后同步显示状态）。
     *
     * @param index 选项索引
     * @param state 新的勾选状态
     */
    public void setItemState(int index, boolean state) {
        if (index >= 0 && index < states.length) {
            states[index] = state;
        }
    }

    // ======================== 生命周期钩子 ========================

    /**
     * 退出 RTS 模式时调用——关闭所有实际渲染的调试覆盖层，
     * 但不擦除 UI 状态字段（留给下次进入时恢复用）。
     */
    public void onRtsExited() {
        // 无条件关闭所有调试覆盖层，不依赖标记位追踪
        disableAllDebugFeatures();
    }

    /**
     * UI 状态加载完成后调用——如果辅助显示总开关处于开启状态，
     * 则重新启用之前已打开的调试覆盖层。
     */
    public void onPostUiStateLoad() {
        if (debugOverlayEnabled) {
            enableAllDebugFeatures();
        }
    }

    // ======================== 调试功能管理 ========================

    /**
     * 启用所有已勾选的调试功能（当 debugOverlayEnabled 打开时调用）。
     * <p>每个功能只有在对应 checkbox 已勾选且未激活时才实际切换渲染。</p>
     */
    private void enableAllDebugFeatures() {
        if (chunkBorderVisible) {
            syncChunkBorder(true);
        }
        if (collisionBoxVisible) {
            syncCollisionBox(true);
        }
    }

    /**
     * 关闭所有调试功能（无条件执行，syncChunkBorder/syncCollisionBox
     * 内部会读取实际渲染状态，仅在需要时才 toggle，不会产生多余操作）。
     */
    private void disableAllDebugFeatures() {
        syncChunkBorder(false);
        syncCollisionBox(false);
    }

    /**
     * 通过反射读取 Minecraft {@code DebugRenderer.renderChunkborder} 的实际状态，
     * 精确地将区块边框渲染同步到目标状态（只 toggle 一次）。
     *
     * <p>由于 {@link DebugRenderer#switchRenderChunkborder()}
     * 是 toggle 操作且 {@code renderChunkborder} 字段为 private，无法从外部直接读取当前状态。
     * 本方法通过反射获取该字段的真实值，仅在 {@code actual != desired} 时执行一次 toggle，
     * 避免了玩家在 RTS 模式外手动按 F3+G 后状态追踪不同步的问题。</p>
     *
     * @param desired true=开启区块边框，false=关闭
     */
    private void syncChunkBorder(boolean desired) {
        try {
            Field f = DebugRenderer.class
                    .getDeclaredField("renderChunkborder");
            f.setAccessible(true);
            boolean actual = f.getBoolean(Minecraft.getInstance().debugRenderer);
            if (actual != desired) {
                Minecraft.getInstance().debugRenderer.switchRenderChunkborder();
            }
            this.chunkBorderRenderingActive = desired;
        } catch (Exception e) {
            // 反射失败时的回退方案：使用旧有的追踪标记
            if (desired != chunkBorderRenderingActive) {
                Minecraft.getInstance().debugRenderer.switchRenderChunkborder();
                chunkBorderRenderingActive = desired;
            }
        }
    }

    /**
     * 通过 {@code EntityRenderDispatcher} 的官方 API 精确开关实体碰撞箱渲染（F3+B）。
     *
     * <p>{@link EntityRenderDispatcher} 提供了
     * {@code shouldRenderHitBoxes()} 读取当前状态和 {@code setRenderHitBoxes(boolean)}
     * 设置目标状态，不需要反射。</p>
     *
     * @param desired true=开启实体碰撞箱，false=关闭
     */
    private void syncCollisionBox(boolean desired) {
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (dispatcher.shouldRenderHitBoxes() != desired) {
            dispatcher.setRenderHitBoxes(desired);
        }
        this.collisionBoxRenderingActive = desired;
    }

    // ======================== 持久化属性 ========================

    public List<PersistableProperty> persistableProperties() {
        return List.of(
                // 辅助显示模式总开关
                PersistableProperty.boolField(
                        "debug.overlayEnabled",
                        s -> s.debug.debugOverlayEnabled,
                        (s, v) -> s.debug.debugOverlayEnabled = v,
                        () -> this.debugOverlayEnabled,
                        v -> this.debugOverlayEnabled = v),
                // 区块边框显示
                PersistableProperty.boolField(
                        "debug.chunkBorderVisible",
                        s -> s.debug.chunkBorderVisible,
                        (s, v) -> s.debug.chunkBorderVisible = v,
                        () -> this.chunkBorderVisible,
                        v -> {
                            this.chunkBorderVisible = v;
                            setItemState(0, v);
                        }),
                // 碰撞箱显示
                PersistableProperty.boolField(
                        "debug.collisionBoxVisible",
                        s -> s.debug.collisionBoxVisible,
                        (s, v) -> s.debug.collisionBoxVisible = v,
                        () -> this.collisionBoxVisible,
                        v -> {
                            this.collisionBoxVisible = v;
                            setItemState(1, v);
                        })

        );
    }

    // ======================== BasePopup 实现 ========================

    @Override
    protected int getItemCount() {
        return items.length;
    }

    @Override
    protected void renderItem(GuiGraphics g, int index, int itemY, float hoverT) {
        // 文字（靠左对齐）
        int textColor = hoverT > 0.5f ? ThemeManager.getHoverTextColor() : ThemeManager.getTextColor();
        String label = items[index].label().getString();
        int textX = x + getPadH();
        int textY = itemY + (getItemHeight() - Minecraft.getInstance().font.lineHeight) / 2 + 1;
        RtsClientUiUtil.drawUiText(g, label, textX, textY, textColor);

        // 模式按钮精灵图（靠右对齐）
        int btnX = x + getPopupWidth() - getPadH() - MODE_BTN_SIZE;
        int btnY = itemY + (getItemHeight() - MODE_BTN_SIZE) / 2;

        TextureInfo modeBtnInfo = new TextureInfo(
                MODE_BUTTON_TEXTURE, MODE_BTN_TEX_W, MODE_BTN_TEX_H,
                TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
                TextureInfo.FilterMode.PIXEL);
        SpriteRegion normal = new SpriteRegion(modeBtnInfo, 0, 0, MODE_BTN_HALF_W, MODE_BTN_STATE_H);
        SpriteRegion hovered = normal.withVOffset(MODE_BTN_STATE_H);
        SpriteRegion selected = normal.withVOffset(MODE_BTN_STATE_H * 2);
        RtsClientUiUtil.drawStateSprite(g, normal, hovered, selected, states[index], hoverT,
                btnX, btnY, MODE_BTN_SIZE, MODE_BTN_SIZE);
    }

    @Override
    protected boolean onItemClick(int index) {
        // 切换状态
        states[index] = !states[index];
        // 执行回调
        if (items[index].action() != null) {
            items[index].action().onToggle(states[index]);
        }
        return true;
    }
}
