package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

/** 组合 main 顶部栏、底部终端和浮动窗口的确定性离屏渲染。 */
public final class UiPreviewRenderer {
    private final UiMainlineAssets assets = new UiMainlineAssets();
    private final UiMainlineChromeRenderer chrome = new UiMainlineChromeRenderer(assets);
    private final UiMainlineTerminalRenderer terminal = new UiMainlineTerminalRenderer(assets);
    private final UiMainlineWindowRenderer windows = new UiMainlineWindowRenderer(assets);
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
        if (scenario.debugOverlay()) drawDebugOverlay(canvas, layout);
        return new UiPreviewResult(canvas, layout);
    }

    /**
     * 世界不属于通用 2D Canvas；这里仅画明确可识别的无头棋盘，帮助审阅透明度，
     * 不复制参考截图，也不冒充 Minecraft 世界渲染。
     */
    private static void drawWorldBoundary(BufferedImageUiCanvas canvas, UiPreviewLayout layout) {
        canvas.clear(UiMainlinePreviewStyle.color(0xFF17202A));
        canvas.fill(new UiRect(0, layout.topBar().bottom(), layout.screen().getWidth(),
                Math.max(0, layout.bottomBar().getY() - layout.topBar().bottom())),
                UiMainlinePreviewStyle.color(0xFF223746));
        int cell = 48;
        for (int y = (int) layout.topBar().bottom(); y < layout.bottomBar().getY(); y += cell) {
            for (int x = 0; x < layout.screen().getWidth(); x += cell) {
                if (((x / cell) + (y / cell)) % 2 == 0) {
                    canvas.fill(new UiRect(x, y, cell, cell),
                            UiMainlinePreviewStyle.color(0xFF294354));
                }
            }
        }
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
