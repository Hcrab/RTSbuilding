package com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.group_button;

import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.AbstractButtonGroup;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.util.ThemeManager;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.state.TooltipController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 相机模式按钮组——管理"自由模式"和"环绕玩家模式"两个按钮。
 *
 * <p>两个按钮互斥（只能启用一个），且至少启用一个。
 * 选中态由 {@link CameraModule#isPlayerOrbitMode()} 驱动。</p>
 *
 * <p>纹理索引：0=free_mode（组内右侧）、1=surround_mode（组内左侧）。</p>
 */
public final class CameraModeGroup extends AbstractButtonGroup {

    private static final ResourceLocation FREE_MODE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/top/button/free_mode.png");
    private static final ResourceLocation SURROUND_MODE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/top/button/surround_mode.png");

    // ======================== 位置背景贴图 ========================

    /** up_button.png —— 首位（左侧）按钮背景 */
    private static final ResourceLocation DOWN_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/down_button.png");
    /** middle_button.png —— 中间按钮背景 */
    private static final ResourceLocation MIDDLE_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/middle_button.png");
    /** up_button.png —— 末位（右侧）按钮背景 */
    private static final ResourceLocation UP_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/up_button.png");

    private final CameraModule cameraModule;

    // ----- 浮窗提示 -----
    private final TooltipController freeModeTooltip = TooltipController.builder().build();
    private final TooltipController surroundModeTooltip = TooltipController.builder().build();

    public CameraModeGroup(CameraModule cameraModule) {
        // HORIZONTAL 方向，顶栏按钮大小 14px，0间隙，有背景层
        super(Direction.HORIZONTAL, TopBarLayoutHelper.BTN_SIZE, DEFAULT_INNER_GAP, true,
                DOWN_BG, MIDDLE_BG, UP_BG,
                FREE_MODE, SURROUND_MODE);
        this.cameraModule = cameraModule;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, TopBarLayoutHelper.ButtonGroup group) {
        // 从相机模块同步选中态
        selected[0] = !cameraModule.isPlayerOrbitMode();  // free_mode
        selected[1] = cameraModule.isPlayerOrbitMode();   // surround_mode
        super.render(g, mouseX, mouseY, group);
    }

    @Override
    protected void renderExtra(GuiGraphics g, int mouseX, int mouseY, TopBarLayoutHelper.ButtonGroup group) {
        // 自由模式按钮（index 0）
        {
            var rect = group.rect(0);
            boolean hovered = rect.contains(mouseX, mouseY);
                        freeModeTooltip.update(hovered, false);
        }
        // 环绕模式按钮（index 1）
        {
            var rect = group.rect(1);
            boolean hovered = rect.contains(mouseX, mouseY);
                        surroundModeTooltip.update(hovered, false);
        }
    }

    /**
     * 在覆盖层渲染阶段绘制浮窗，确保浮窗在所有 UI 之上。
     */
    public void renderTooltipOverlay(GuiGraphics g, TopBarLayoutHelper.ButtonGroup group,
                                     int screenW, int screenH) {
        String keyText = RtsKeyMappings.TOGGLE_CAMERA_MODE_KEY.getTranslatedKeyMessage().getString();
        int textColor = ThemeManager.getTextColor();
        int shortcutColor = ColorAnimation.scale(textColor, 0.6f);

        // 自由模式浮窗
        if (freeModeTooltip.shouldRender()) {
            var rect = group.rect(0);
            String text = Component.translatable("tooltip.rtsbuilding.camera.free_mode").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.camera.free_mode.desc").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
            freeModeTooltip.render(g, rect.x(), rect.y(), rect.width(), rect.height(),
                    text, textColor, shortcutColor, screenW, screenH);
        }

        // 环绕模式浮窗
        if (surroundModeTooltip.shouldRender()) {
            var rect = group.rect(1);
            String text = Component.translatable("tooltip.rtsbuilding.camera.surround_mode").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.camera.surround_mode.desc").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
            surroundModeTooltip.render(g, rect.x(), rect.y(), rect.width(), rect.height(),
                    text, textColor, shortcutColor, screenW, screenH);
        }
    }

    @Override
    protected void onButtonClick(int index) {
        if (index == 0) {
            // 点击 free_mode：仅在环绕模式启用时切换
            if (cameraModule.isPlayerOrbitMode()) {
                cameraModule.disablePlayerOrbitMode();
            }
        } else {
            // 点击 surround_mode：仅在自由模式启用时切换
            if (!cameraModule.isPlayerOrbitMode()) {
                cameraModule.enablePlayerOrbitMode();
            }
        }
        // 不调用 super——selected[] 由 render() 从 cameraModule 同步
    }
}

