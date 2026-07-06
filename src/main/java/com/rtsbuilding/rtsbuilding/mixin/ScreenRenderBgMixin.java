package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.screen.panel.container.ContainerScreenPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin：当 {@link ContainerScreenPanel} 将容器屏幕作为子覆盖层渲染时，
 * 跳过深色背景绘制，防止子屏幕的深色覆盖遮住 RTS UI 面板。
 *
 * <p>设计说明：</p>
 * <ul>
 *   <li>{@link Screen#renderBackground(GuiGraphics, int, int, float)} —
 *       拦截非容器屏幕（一般 Screen 子类）的深色背景绘制，直接取消整个方法。</li>
 *   <li>{@link Screen#renderTransparentBackground(GuiGraphics)} —
 *       拦截透明蒙板绘制。因为 {@code AbstractContainerScreen} 覆写了
 *       {@code renderBackground}，在其方法体内仅调用
 *       {@code renderTransparentBackground} + {@code renderBg}，
 *       所以只拦截 {@code renderTransparentBackground} 即可跳过蒙板，
 *       保留容器纹理（renderBg）的正常渲染。</li>
 * </ul>
 */
@Mixin(Screen.class)
public abstract class ScreenRenderBgMixin {

    /**
     * 非容器屏幕：直接取消整个 {@code renderBackground}。
     * <p>此方法画的是不透明深色背景（全景图/模糊/菜单背景），
     * 整体取消不影响容器纹理渲染。</p>
     */
    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$skipBackgroundInOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (ContainerScreenPanel.isRenderingOverlay()) {
            ci.cancel();
        }
    }

    /**
     * 所有屏幕：拦截 {@code renderTransparentBackground}（透明蒙板）。
     * <p>{@code AbstractContainerScreen.renderBackground} 内部通过
     * {@code this.renderTransparentBackground()} + {@code this.renderBg()}
     * 分别绘制蒙板和容器纹理。仅取消透明蒙板即可，容器纹理（renderBg）不受影响。</p>
     */
    @Inject(method = "renderTransparentBackground(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$skipTransparentBgInOverlay(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (ContainerScreenPanel.isRenderingOverlay()) {
            ci.cancel();
        }
    }
}
