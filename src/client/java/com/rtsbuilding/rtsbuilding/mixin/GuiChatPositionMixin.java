package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.event.ClientGuiEventHandler;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将聊天底边抬到 RTS 底部面板上方，保持与 NeoForge 图层事件相同的玩家体验。 */
@Mixin(Gui.class)
public abstract class GuiChatPositionMixin {
    @Shadow @Final private ChatComponent chat;

    @Inject(method = "renderChat", at = @At("HEAD"))
    private void rtsbuilding$beforeRenderChat(
            GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        int vanillaBottomY = graphics.guiHeight() - 40;
        int targetBottomY = ClientGuiEventHandler.chatBottomY(vanillaBottomY);
        graphics.pose().pushPose();
        graphics.pose().translate(
                0.0F,
                (float) ((targetBottomY - vanillaBottomY) / chat.getScale()),
                0.0F);
    }

    @Inject(method = "renderChat", at = @At("RETURN"))
    private void rtsbuilding$afterRenderChat(
            GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        graphics.pose().popPose();
    }
}
