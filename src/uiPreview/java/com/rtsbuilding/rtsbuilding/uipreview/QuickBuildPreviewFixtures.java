package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiControl;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiMode;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShape;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShapeOption;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiState;

import java.util.ArrayList;
import java.util.List;

/** Quick Build 正式模式/形状目录的 preview-only Core 快照。 */
final class QuickBuildPreviewFixtures {
    private QuickBuildPreviewFixtures() {}

    static boolean isQuickBuildScenario(UiPreviewScenario.Variant variant) {
        return variant == UiPreviewScenario.Variant.QUICK_BUILD_STATES
                || variant == UiPreviewScenario.Variant.QUICK_BUILD_BUILD
                || variant == UiPreviewScenario.Variant.QUICK_BUILD_DESTROY_CHAIN
                || variant == UiPreviewScenario.Variant.QUICK_BUILD_LOCKED
                || variant == UiPreviewScenario.Variant.QUICK_BUILD_PROGRESS;
    }

    static QuickBuildUiState forScenario(UiPreviewScenario scenario, UiLanguageBundle language) {
        UiPreviewScenario.Variant variant = scenario.variant();
        boolean build = variant == UiPreviewScenario.Variant.QUICK_BUILD_BUILD;
        boolean locked = variant == UiPreviewScenario.Variant.QUICK_BUILD_LOCKED;
        boolean chain = variant == UiPreviewScenario.Variant.QUICK_BUILD_DESTROY_CHAIN;
        boolean progress = variant == UiPreviewScenario.Variant.QUICK_BUILD_PROGRESS;
        QuickBuildUiMode mode = build || locked ? QuickBuildUiMode.BUILD : QuickBuildUiMode.DESTROY;
        QuickBuildUiShape active = chain ? QuickBuildUiShape.CHAIN
                : build || locked ? QuickBuildUiShape.LINE : QuickBuildUiShape.BOX;
        List<QuickBuildUiShapeOption> shapes = new ArrayList<QuickBuildUiShapeOption>();
        if (mode == QuickBuildUiMode.BUILD) {
            for (QuickBuildUiShape shape : QuickBuildUiShape.values()) {
                if (shape == QuickBuildUiShape.CHAIN) continue;
                shapes.add(new QuickBuildUiShapeOption(shape, shape == active, true, ""));
            }
        } else {
            for (QuickBuildUiShape shape : QuickBuildUiShape.values()) {
                shapes.add(new QuickBuildUiShapeOption(shape, shape == active, true, ""));
            }
        }
        List<QuickBuildUiControl> controls = new ArrayList<QuickBuildUiControl>();
        if (!chain) {
            controls.add(control(QuickBuildUiControl.Id.FILL,
                    language.text("screen.rtsbuilding.fill.fill"), true));
            controls.add(control(QuickBuildUiControl.Id.HOLLOW,
                    language.text("screen.rtsbuilding.fill.hollow"), false));
            if (active == QuickBuildUiShape.BOX) {
                controls.add(control(QuickBuildUiControl.Id.SKELETON,
                        language.text("screen.rtsbuilding.fill.skeleton"), false));
                controls.add(control(QuickBuildUiControl.Id.ADVANCED,
                        language.text("screen.rtsbuilding.quick_build.advanced_box"), true));
            }
            if (active == QuickBuildUiShape.LINE) {
                controls.add(control(QuickBuildUiControl.Id.CONNECT,
                        language.text("screen.rtsbuilding.quick_build.connect"), true));
            }
        }
        return new QuickBuildUiState(true, mode, !locked,
                locked ? "plugin_required" : "",
                build || locked ? active : QuickBuildUiShape.BLOCK,
                mode == QuickBuildUiMode.DESTROY ? active : QuickBuildUiShape.CHAIN,
                shapes, controls, 96, 1, 512,
                progress ? 72 : -1, progress ? 160 : 0, progress ? 88 : 0,
                progress ? "72 / 160" : "", build ? "384" : "0",
                "rtsbuilding:area_destroy_plugin", build ? 128 : 0,
                build ? "screen.rtsbuilding.quick_build.build_hint"
                        : chain ? "screen.rtsbuilding.quick_build.chain_hint"
                        : "screen.rtsbuilding.quick_build.destroy_advanced_box_hint",
                build ? "B" : "V", build ? "24*4*4" : "18*7*12");
    }

    private static QuickBuildUiControl control(QuickBuildUiControl.Id id,
                                                String label, boolean selected) {
        return new QuickBuildUiControl(id, label, selected, true);
    }
}
