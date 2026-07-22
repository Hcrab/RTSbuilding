package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiButton;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiButtonId;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiState;
import com.rtsbuilding.rtsbuilding.uikit.layout.RtsMainlineLayout;

import java.awt.Color;
import java.awt.image.BufferedImage;

/** 复刻 main 的顶部按钮、两行状态、帮助提示、齿轮和状态条。 */
final class UiMainlineChromeRenderer {
    private final UiMainlineAssets assets;

    UiMainlineChromeRenderer(UiMainlineAssets assets) {
        this.assets = assets;
    }

    void render(BufferedImageUiCanvas canvas, UiPreviewLayout layout,
                UiLanguageBundle language, UiPreviewScenario scenario) {
        canvas.fill(layout.topBar(), UiMainlinePreviewStyle.color(0xC8141922));
        TopBarUiState state = TopBarPreviewFixtures.forScenario(scenario);
        RtsMainlineLayout.TopButtons buttons = RtsMainlineLayout.topButtons(
                (int) layout.screen().getWidth(), visible(state, TopBarUiButtonId.QUICK_BUILD),
                visible(state, TopBarUiButtonId.QUEST_DETECT),
                visible(state, TopBarUiButtonId.RANGE_CULLING),
                visible(state, TopBarUiButtonId.DEVELOPER));
        for (TopBarUiButton button : state.buttons) {
            if (!button.visible || button.id == TopBarUiButtonId.GUIDE
                    || button.id == TopBarUiButtonId.DEVELOPER
                    || button.id == TopBarUiButtonId.GEAR) continue;
            String textureState = button.active ? "active" : "inactive";
            BufferedImage texture = assets.topBar(textureName(button.id), textureState);
            double x = buttons.x(button.id.layoutIndex) + (RtsMainlineLayout.TOP_ICON_BUTTON_W
                    - RtsMainlineLayout.TOP_BUTTON_H) / 2.0D;
            canvas.image(texture, new UiRect(x, RtsMainlineLayout.TOP_BUTTON_Y,
                    RtsMainlineLayout.TOP_BUTTON_H, RtsMainlineLayout.TOP_BUTTON_H));
        }

        int guideX = buttons.x(TopBarUiButtonId.GUIDE.layoutIndex);
        UiMainlinePreviewStyle.frame(canvas,
                new UiRect(guideX + 4, RtsMainlineLayout.TOP_BUTTON_Y,
                        RtsMainlineLayout.TOP_BUTTON_H, RtsMainlineLayout.TOP_BUTTON_H),
                0xAA1F2329, 0xFF5B6673, 0xFF0D0E10);
        canvas.centeredText("i", guideX + 16, 19, UiMainlinePreviewStyle.WHITE);

        if (visible(state, TopBarUiButtonId.DEVELOPER)) {
            int developerX = buttons.x(TopBarUiButtonId.DEVELOPER.layoutIndex);
            UiMainlinePreviewStyle.frame(canvas,
                    new UiRect(developerX + 4, RtsMainlineLayout.TOP_BUTTON_Y,
                            RtsMainlineLayout.TOP_BUTTON_H, RtsMainlineLayout.TOP_BUTTON_H),
                    0xAA1F2329, 0xFF5B6673, 0xFF0D0E10);
            canvas.centeredText("D", developerX + 16, 19, UiMainlinePreviewStyle.WHITE);
        }

        int gearX = buttons.x(TopBarUiButtonId.GEAR.layoutIndex) + 4;
        canvas.image(assets.topBar("settings_gear",
                        state.button(TopBarUiButtonId.GEAR).active ? "active" : "inactive"),
                new UiRect(gearX, RtsMainlineLayout.TOP_BUTTON_Y,
                        RtsMainlineLayout.TOP_BUTTON_H, RtsMainlineLayout.TOP_BUTTON_H));

        String mode = language.format("screen.rtsbuilding.status.mode",
                language.text(modeTranslationKey(state.mode)));
        String linked = state.storageLinked
                ? language.format("screen.rtsbuilding.status.storage_linked", state.linkedStorageName)
                : language.text("screen.rtsbuilding.status.storage_not_linked");
        String row2 = linked + "    " + language.text(state.autoStoreMinedDrops
                ? "screen.rtsbuilding.status.auto_store_on"
                : "screen.rtsbuilding.status.auto_store_off")
                + "    " + language.format("screen.rtsbuilding.status.funnel",
                language.text(state.funnelEnabled ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"))
                + (state.shapeStatus.isEmpty() ? "" : "    " + state.shapeStatus)
                + (state.pendingGuiBindSlot < 0 ? "" : "    " + language.format(
                "screen.rtsbuilding.status.gui_bind_armed", state.pendingGuiBindSlot + 1));
        canvas.text(canvas.trimToWidth(mode, (int) layout.screen().getWidth() - 16),
                8, RtsMainlineLayout.TOP_STATUS_ROW_1_Y + 8, Color.WHITE);
        canvas.text(canvas.trimToWidth(row2, (int) layout.screen().getWidth() - 16),
                8, RtsMainlineLayout.TOP_STATUS_ROW_2_Y + 8,
                UiMainlinePreviewStyle.color(state.storageLinked ? 0xFFB8FFB8 : 0xFFFFD8AE));

        if (state.mode == TopBarUiState.Mode.FUNNEL) {
            String tip = language.text("screen.rtsbuilding.mode_tip.funnel");
            RtsMainlineLayout.TopStatus status = RtsMainlineLayout.topStatus(
                    (int) layout.screen().getWidth());
            int tipX = RtsMainlineLayout.contextualHintX(status,
                    canvas.textWidth(row2), canvas.textWidth(tip), 12);
            if (tipX >= 0) {
                canvas.text(tip, tipX, status.row2Y + 8,
                        UiMainlinePreviewStyle.color(0xFFB8FFB8));
            }
        }

        if (!state.button(TopBarUiButtonId.GUIDE).active) {
            String hint = language.text("screen.rtsbuilding.top_hint.guide");
            double hintX = guideX + RtsMainlineLayout.TOP_ICON_BUTTON_W + 8;
            int hintRight = visible(state, TopBarUiButtonId.DEVELOPER)
                    ? buttons.x(TopBarUiButtonId.DEVELOPER.layoutIndex) : gearX;
            canvas.text(canvas.trimToWidth("> " + hint, Math.max(80, hintRight - (int) hintX - 10)),
                    hintX, 21, UiMainlinePreviewStyle.color(0xFFE7C46A));
        }

        renderPlayerStatus(canvas, layout);
        if (scenario.variant() == UiPreviewScenario.Variant.JADE_MODES) {
            UiMainlinePreviewStyle.frame(canvas, layout.jadeReserve(),
                    0xCC171B22, 0xFF6F8298, 0xFF0A0E13);
            canvas.text("Jade", layout.jadeReserve().getX() + 6,
                    layout.jadeReserve().getY() + 13, UiMainlinePreviewStyle.WHITE);
            canvas.text("anchored / follow / hidden", layout.jadeReserve().getX() + 6,
                    layout.jadeReserve().getY() + 26, UiMainlinePreviewStyle.MUTED);
        }
    }

    private static boolean visible(TopBarUiState state, TopBarUiButtonId id) {
        TopBarUiButton button = state.button(id);
        return button != null && button.visible;
    }

    private static String textureName(TopBarUiButtonId id) {
        switch (id) {
            case INTERACT: return "mode_interact";
            case LINK: return "mode_link";
            case FUNNEL: return "mode_funnel";
            case ROTATE: return "mode_rotate";
            case QUICK_BUILD: return "quick_build";
            case QUEST_DETECT: return "quest_detect";
            case CHUNK_VIEW: return "chunk_view";
            case RANGE_CULLING: return "filter_block";
            default: throw new IllegalArgumentException("No texture-backed top button: " + id);
        }
    }

    private static String modeTranslationKey(TopBarUiState.Mode mode) {
        switch (mode) {
            case INTERACT: return "screen.rtsbuilding.mode.interact";
            case LINK_STORAGE: return "screen.rtsbuilding.mode.link_storage";
            case FUNNEL: return "screen.rtsbuilding.mode.funnel";
            case CAMERA: return "screen.rtsbuilding.mode.camera";
            case ROTATE: return "screen.rtsbuilding.mode.rotate";
            default: return "screen.rtsbuilding.mode.idle";
        }
    }

    private static void renderPlayerStatus(BufferedImageUiCanvas canvas, UiPreviewLayout layout) {
        double x = layout.screen().right() - 132;
        double y = layout.topBar().bottom() + 8;
        String[] labels = new String[] {"HP 20/20", "FD 20/20", "AR 0"};
        int[] fills = new int[] {0xFFD74848, 0xFFD99A32, 0xFF202735};
        for (int i = 0; i < labels.length; i++) {
            UiMainlinePreviewStyle.frame(canvas, new UiRect(x, y + i * 13, 126, 12),
                    0xDD202735, 0xFF65758A, 0xFF0B0E13);
            if (i < 2) canvas.fill(new UiRect(x + 2, y + i * 13 + 2, 122, 8),
                    UiMainlinePreviewStyle.color(fills[i]));
            canvas.text(labels[i], x + 4, y + i * 13 + 10, Color.WHITE);
        }
    }
}
