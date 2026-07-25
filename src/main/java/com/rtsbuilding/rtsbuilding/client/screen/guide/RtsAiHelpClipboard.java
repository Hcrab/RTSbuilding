package com.rtsbuilding.rtsbuilding.client.screen.guide;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/** 把与游戏内问答相同的版本、模式和教程资料复制给玩家选择的外部 AI。 */
public final class RtsAiHelpClipboard {
    private RtsAiHelpClipboard() {
    }

    public static void copy(ClientRtsController controller) {
        Minecraft minecraft = Minecraft.getInstance();
        String language = minecraft.getLanguageManager().getSelected();
        boolean chinese = language != null && language.toLowerCase(Locale.ROOT).startsWith("zh_");
        minecraft.keyboardHandler.setClipboard(RtsAiPrompt.composeClipboard(
                chinese, RtsAiKnowledgeBase.buildForClipboard(controller, chinese)));
    }
}
