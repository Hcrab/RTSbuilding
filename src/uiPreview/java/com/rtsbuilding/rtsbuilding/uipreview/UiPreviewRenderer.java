package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

/** 组合 main 顶部栏、底部终端和浮动窗口的确定性离屏渲染。 */
public final class UiPreviewRenderer {
    private final UiMainlineAssets assets = new UiMainlineAssets();
    private final UiMainlineChromeRenderer chrome = new UiMainlineChromeRenderer(assets);
    private final UiMainlineTerminalRenderer terminal = new UiMainlineTerminalRenderer(assets);
    private final UiMainlineWindowRenderer windows = new UiMainlineWindowRenderer(assets);
    private final PopupPreviewRenderer popups = new PopupPreviewRenderer();
    private final UiWorldInteractionBoundaryRenderer worldInteractions =
            new UiWorldInteractionBoundaryRenderer();

    public UiPreviewResult render(UiPreviewScenario scenario) {
        UiPreviewLayout layout = UiPreviewLayout.calculate(scenario);
        BufferedImageUiCanvas canvas = new BufferedImageUiCanvas(
                scenario.width(), scenario.height(), layout.renderScale());
        canvas.configureFont(scenario.language());
        canvas.recordLayoutRebuild();
        UiLanguageBundle language = assets.language(scenario.language());

        drawWorldBoundary(canvas, layout);
        worldInteractions.render(canvas, layout, scenario);
        chrome.render(canvas, layout, language, scenario);
        terminal.render(canvas, layout, language, scenario);
        windows.render(canvas, layout, language, scenario);
        popups.render(canvas, layout, scenario);
        if (scenario.debugOverlay()) drawDebugOverlay(canvas, layout);
        return new UiPreviewResult(canvas, layout);
    }

    /** 使用固定 Minecraft 实景帮助审阅面板透明度和真实环境中的色彩对比。 */
    private static void drawWorldBoundary(BufferedImageUiCanvas canvas, UiPreviewLayout layout) {
        canvas.clear(UiMainlinePreviewStyle.color(0xFF000000));
        UiPreviewWorldBackground.selected().render(canvas, layout.screen());
    }

    private static void drawDebugOverlay(BufferedImageUiCanvas canvas, UiPreviewLayout layout) {
        canvas.stroke(layout.topBar(), UiMainlinePreviewStyle.color(0xFF3C8CFF));
        canvas.stroke(layout.bottomBar(), UiMainlinePreviewStyle.color(0xFF3C8CFF));
        int z = 0;
        for (UiPreviewLayout.NamedPanel panel : layout.panels()) {
            canvas.stroke(panel.bounds(), UiMainlinePreviewStyle.color(0xFFFF4646));
            canvas.text("z=" + z++, panel.bounds().getX() + 3,
                    panel.bounds().bottom() - 4, UiMainlinePreviewStyle.color(0xFFFFDC00));
        }
    }
}
