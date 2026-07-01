package com.rtsbuilding.rtsbuilding.client.screen.panel.gear;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.util.SettingsSection;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.ResetButton;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.ThemeSwitchComponent;
import com.rtsbuilding.rtsbuilding.client.util.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 个性化设置折叠分区——在设置面板中管理"个性化设置"分区的渲染和交互。
 */
public class PersonalizationSection extends SettingsSection {

    /** 主题开关组件实例 */
    private final ThemeSwitchComponent themeSwitch = new ThemeSwitchComponent();

    /** 重置按钮——恢复主题为暗色模式（默认） */
    private final ResetButton themeResetBtn = new ResetButton();

    /** 缓存的翻译模板文本 */
    private String cachedThemeTemplate;
    private String cachedLightLabel;
    private String cachedDarkLabel;

    public PersonalizationSection() {
        super("screen.rtsbuilding.settings.category.personalization");
        themeResetBtn.setResetAction(() -> ThemeManager.getInstance().setLightMode(false));
    }

    @Override
    protected int getContentRowCount() {
        return 1;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int lineCount) {
        // 主题标签（左对齐，第一行）
        String label = buildThemeLabel();
        renderLabel(g, label, x, y, 0);

        // 主题开关（手动计算位置，左移以给重置按钮留空间）
        boolean lightMode = ThemeManager.getInstance().isLightMode();
        int textCenterY = textY(y, 0) + Minecraft.getInstance().font.lineHeight / 2;
        int toggleX = x + w - RIGHT_PAD - ResetButton.BTN_SIZE - 4 - ThemeSwitchComponent.SIZE;
        int toggleY = textCenterY - ThemeSwitchComponent.SIZE / 2;
        themeSwitch.render(g, mouseX, mouseY, toggleX, toggleY, lightMode);

        // 重置按钮在右边缘
        int resetX = x + w - RIGHT_PAD - ResetButton.BTN_SIZE;
        int resetY = textCenterY - ResetButton.BTN_SIZE / 2;
        themeResetBtn.render(g, mouseX, mouseY, resetX, resetY);
    }

    private String buildThemeLabel() {
        boolean lightMode = ThemeManager.getInstance().isLightMode();
        if (cachedThemeTemplate == null) {
            cachedThemeTemplate = Component.translatable(
                    "screen.rtsbuilding.settings.category.personalization.theme", "%s").getString();
            cachedLightLabel = Component.translatable("screen.rtsbuilding.settings.theme.light").getString();
            cachedDarkLabel = Component.translatable("screen.rtsbuilding.settings.theme.dark").getString();
        }
        String modeLabel = lightMode ? cachedLightLabel : cachedDarkLabel;
        return cachedThemeTemplate.replace("%s", modeLabel);
    }

    @Override
    protected boolean onContentLineClick(int lineIndex, double mouseX, double mouseY,
                                         int contentX, int contentY, int contentW) {
        if (themeResetBtn.handleClick(mouseX, mouseY)) return true;
        if (themeSwitch.handleClick(mouseX, mouseY)) {
            ThemeManager.getInstance().toggle();
            return true;
        }
        return false;
    }
}
