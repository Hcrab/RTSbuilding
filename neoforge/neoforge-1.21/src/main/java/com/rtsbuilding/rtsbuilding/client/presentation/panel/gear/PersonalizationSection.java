package com.rtsbuilding.rtsbuilding.client.presentation.panel.gear;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.SettingsSection;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.component.ResetButton;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.component.ThemeSwitchComponent;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class PersonalizationSection extends SettingsSection {

    
    private final ThemeSwitchComponent themeSwitch = new ThemeSwitchComponent();

    
    private final ResetButton themeResetBtn = new ResetButton();

    
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
        
        String label = buildThemeLabel();
        renderLabel(g, label, x, y, 0);

        
        boolean lightMode = ThemeManager.getInstance().isLightMode();
        int textCenterY = textY(y, 0) + Minecraft.getInstance().font.lineHeight / 2;
        int toggleX = x + w - RIGHT_PAD - ResetButton.BTN_SIZE - 4 - ThemeSwitchComponent.SIZE;
        int toggleY = textCenterY - ThemeSwitchComponent.SIZE / 2;
        themeSwitch.render(g, mouseX, mouseY, toggleX, toggleY, lightMode);

        
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
