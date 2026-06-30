package com.rtsbuilding.rtsbuilding.client.screen.panel.gear;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.util.SettingsSection;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.ThemeSwitchComponent;
import com.rtsbuilding.rtsbuilding.client.util.ThemeManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 个性化设置折叠分区——在设置面板中管理"个性化设置"分区的渲染和交互。
 */
public class PersonalizationSection extends SettingsSection {

    /** 主题开关组件实例 */
    private final ThemeSwitchComponent themeSwitch = new ThemeSwitchComponent();

    public PersonalizationSection() {
        super("screen.rtsbuilding.settings.category.personalization");
    }

    @Override
    protected int getContentRowCount() {
        return 1;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, String[] lines) {
        // 主题标签（左对齐，第一行）
        String label = buildThemeLabel();
        renderLabel(g, label, x, y, 0);

        // 主题开关（右对齐，第一行）
        boolean lightMode = ThemeManager.getInstance().isLightMode();
        renderToggle(g, mouseX, mouseY, x, y, w, 0, themeSwitch, lightMode);
    }

    private String buildThemeLabel() {
        boolean lightMode = ThemeManager.getInstance().isLightMode();
        String modeKey = lightMode
                ? "screen.rtsbuilding.settings.theme.light"
                : "screen.rtsbuilding.settings.theme.dark";
        return Component.translatable(
                "screen.rtsbuilding.settings.category.personalization.theme",
                Component.translatable(modeKey)).getString();
    }

    @Override
    protected boolean onContentLineClick(int lineIndex, double mouseX, double mouseY,
                                         int contentX, int contentY, int contentW) {
        if (themeSwitch.handleClick(mouseX, mouseY)) {
            ThemeManager.getInstance().toggle();
            return true;
        }
        return false;
    }
}
