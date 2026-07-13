package com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.popup;

import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.popup.BasePopup;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Logo 图标点击后弹出的下拉菜单列表。
 *
 * <p>集成了齿轮菜单状态管理和菜单项创建，是一个自包含的下拉菜单组件。</p>
 */
public final class LogoMenuPopup extends BasePopup {

    public boolean isGearMenuOpen() {
        return gearMenuOpen;
    }

    /** 单个菜单项：显示文本 + 点击回调 */
    public record MenuItem(Component label, Runnable action) {}

    private final List<MenuItem> items;

    // ======================== 齿轮菜单状态 ========================

    /** Gear 菜单打开状态 */
    private boolean gearMenuOpen;
    /** Gear 菜单开关回调（由外部注入，避免直接持有 GearMenuPanel 引用） */
    private Runnable onGearMenuToggle;

    // ======================== 设置图标贴图 ========================

    /** 设置图标贴图（1024×512，横向双主题，左半=暗色，右半=亮色） */
    private static final ResourceLocation SETTING_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/top/setting.png");
    private static final int SETTING_TEX_W = 1024;
    private static final int SETTING_TEX_H = 512;
    private static final int SETTING_HALF_W = 512;
    private static final int SETTING_ICON_SIZE = 17;

    // ======================== 快捷键文字颜色（比主文字更加暗/弱） ========================

    /** 亮色主题下快捷键文字颜色（中灰色，比主文字 0xFF333333 更弱） */
    private static final int LIGHT_SHORTCUT_COLOR = 0xFF777777;
    /** 暗色主题下快捷键文字颜色（暗灰色，比主文字 0xFFCCCCCC 更弱） */
    private static final int DARK_SHORTCUT_COLOR = 0xFF888888;

    /** 主文字与快捷键文字之间的间距 */
    private static final int LABEL_TO_SHORTCUT_GAP = 12;

    public LogoMenuPopup() {
        this.items = new ArrayList<>();
        // 创建设置菜单项
        this.items.add(new MenuItem(
                Component.translatable("screen.rtsbuilding.settings.title"),
                () -> {
                    if (onGearMenuToggle != null) {
                        onGearMenuToggle.run();
                    }
                }));
        // 自动计算每个菜单项的内容宽度（图标 + 间距 + 文字 + 间距 + 快捷键文字）
        var font = Minecraft.getInstance().font;
        int[] contentWidths = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            int labelWidth = font.width(items.get(i).label());
            int keyWidth = font.width(RtsKeyMappings.OPEN_GEAR_MENU_KEY.getTranslatedKeyMessage());
            contentWidths[i] = SETTING_ICON_SIZE + 4 + labelWidth + LABEL_TO_SHORTCUT_GAP + keyWidth;
        }
        setItemContentWidths(contentWidths);

        initAnims(items.size());
    }

    // ======================== 齿轮菜单状态 ========================

    /** 设置 Gear 菜单打开状态 */
    public void setGearMenuOpen(boolean open) {
        this.gearMenuOpen = open;
    }

    /** 注册 Gear 菜单开关回调 */
    public void setOnGearMenuToggle(Runnable toggle) {
        this.onGearMenuToggle = toggle;
    }

    // ======================== BasePopup 实现 ========================

    @Override
    protected int getItemCount() {
        return items.size();
    }

    @Override
    protected void renderItem(GuiGraphics g, int index, int itemY, float hoverT) {
        // 设置图标（精灵图画法，双主题横向偏移）
        int iconX = x + getPadH();
        int iconY = itemY + (getItemHeight() - SETTING_ICON_SIZE) / 2;
        TextureInfo settingTex = new TextureInfo(
                SETTING_TEXTURE, SETTING_TEX_W, SETTING_TEX_H,
                TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
                TextureInfo.FilterMode.PIXEL);
        SpriteRegion iconRegion = new SpriteRegion(settingTex, 0, 0, SETTING_HALF_W, SETTING_TEX_H).withTheme();
        SpriteRenderer.drawSprite(g, iconRegion,
                iconX, iconY, SETTING_ICON_SIZE, SETTING_ICON_SIZE);

        // 文字（跟在图标后面）
        int textColor = hoverT > 0.5f ? ThemeManager.getHoverTextColor() : ThemeManager.getTextColor();
        String label = items.get(index).label().getString();
        int textX = iconX + SETTING_ICON_SIZE + 4;
        int textY = iconY + (SETTING_ICON_SIZE - Minecraft.getInstance().font.lineHeight) / 2 + 1;
        TextRenderer.draw(g, label, textX, textY, textColor);

        // 快捷键文字（跟在主文字后面，颜色更暗/弱，不受悬浮影响）
        int shortcutColor = ThemeManager.getInstance().isLightMode() ? LIGHT_SHORTCUT_COLOR : DARK_SHORTCUT_COLOR;
        String shortcutLabel = RtsKeyMappings.OPEN_GEAR_MENU_KEY.getTranslatedKeyMessage().getString();
        int shortcutX = textX + Minecraft.getInstance().font.width(label) + LABEL_TO_SHORTCUT_GAP;
        TextRenderer.draw(g, shortcutLabel, shortcutX, textY, shortcutColor);
    }

    @Override
    protected boolean onItemClick(int index) {
        // 先关闭再执行回调，避免递归问题
        close();
        items.get(index).action().run();
        return true;
    }
}
