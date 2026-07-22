package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.layout.RtsMainlineLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.QuickBuildWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.CullingWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.StorageWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.GuideWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.FunnelBufferLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftQuantityWindowLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 将输出像素映射到 main 的虚拟 RTS 坐标，并复用生产固定栏布局描述。 */
public final class UiPreviewLayout {
    private final double renderScale;
    private final UiRect screen;
    private final UiRect topBar;
    private final UiRect bottomBar;
    private final UiRect jadeReserve;
    private final RtsMainlineLayout.BottomPanel bottom;
    private final List<NamedPanel> panels;

    private UiPreviewLayout(double renderScale, UiRect screen, UiRect topBar,
                            UiRect bottomBar, UiRect jadeReserve,
                            RtsMainlineLayout.BottomPanel bottom,
                            List<NamedPanel> panels) {
        this.renderScale = renderScale;
        this.screen = screen;
        this.topBar = topBar;
        this.bottomBar = bottomBar;
        this.jadeReserve = jadeReserve;
        this.bottom = bottom;
        this.panels = Collections.unmodifiableList(panels);
    }

    public static UiPreviewLayout calculate(UiPreviewScenario scenario) {
        // 参考图为 2048x1152，基准输出为 1920x1080：2x 主线 UI 等比换算后是 1.875。
        double renderScale = 1.875D * scenario.rtsScale();
        int logicalWidth = Math.max(320, (int) Math.round(scenario.width() / renderScale));
        int logicalHeight = Math.max(240, (int) Math.round(scenario.height() / renderScale));
        UiRect screen = new UiRect(0, 0, logicalWidth, logicalHeight);
        RtsMainlineLayout.BottomPanel bottom = RtsMainlineLayout.bottomPanelAtHeight(
                logicalWidth, logicalHeight, Math.min(88, logicalHeight / 3));
        UiRect top = new UiRect(0, 0, logicalWidth, RtsMainlineLayout.TOP_H);
        UiRect bottomBounds = new UiRect(bottom.panelX, bottom.panelY, bottom.panelW, bottom.panelH);
        UiRect jade = new UiRect(Math.max(8, logicalWidth - 170), RtsMainlineLayout.TOP_H + 8,
                158, 34).clampWithin(screen);

        List<NamedPanel> panels = new ArrayList<NamedPanel>();
        double availableBottom = bottom.panelY - 6;
        // 基线使用参考截图中已持久化的用户窗口边界；chrome/最小尺寸仍来自生产窗口。
        double settingsW = Math.min(280, logicalWidth - 20);
        double settingsH = Math.min(220, Math.max(160, availableBottom - 96));
        UiRect settings = new UiRect(58, 103, settingsW, settingsH).clampWithin(screen);
        if (!CullingPreviewFixtures.supports(scenario.variant())
                && !StoragePreviewFixtures.supports(scenario.variant())
                && !WorkflowPreviewFixtures.supports(scenario.variant())
                && !GuidePreviewFixtures.supports(scenario.variant())
                && !FunnelPreviewFixtures.supports(scenario.variant())
                && !CraftQuantityPreviewFixtures.supports(scenario.variant())) {
            panels.add(new NamedPanel("settings", settings));
        }
        if (scenario.variant() == UiPreviewScenario.Variant.OVERLAPPING_WINDOWS
                || scenario.variant() == UiPreviewScenario.Variant.QUICK_BUILD_STATES
                || scenario.variant() == UiPreviewScenario.Variant.DENSE_WORKFLOW_NARROW
                || scenario.variant() == UiPreviewScenario.Variant.QUICK_BUILD_BUILD
                || scenario.variant() == UiPreviewScenario.Variant.QUICK_BUILD_DESTROY_CHAIN
                || scenario.variant() == UiPreviewScenario.Variant.QUICK_BUILD_LOCKED
                || scenario.variant() == UiPreviewScenario.Variant.QUICK_BUILD_PROGRESS) {
            boolean destroy = scenario.variant() == UiPreviewScenario.Variant.QUICK_BUILD_STATES
                    || scenario.variant() == UiPreviewScenario.Variant.QUICK_BUILD_DESTROY_CHAIN
                    || scenario.variant() == UiPreviewScenario.Variant.QUICK_BUILD_PROGRESS
                    || scenario.variant() == UiPreviewScenario.Variant.DENSE_WORKFLOW_NARROW;
            UiRect quick = new UiRect(settings.getX() + settings.getWidth() - 92,
                    settings.getY() + 56, QuickBuildWindowLayout.WINDOW_W,
                    QuickBuildWindowLayout.windowHeight(destroy)).clampWithin(screen);
            panels.add(new NamedPanel("quick_build", quick));
        }
        if (scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_STATES
                || scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_WAITING
                || scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_READY
                || scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_SAVING
                || scenario.variant() == UiPreviewScenario.Variant.OVERLAPPING_WINDOWS) {
            boolean capture = scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_WAITING
                    || scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_READY
                    || scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_SAVING;
            int width = capture ? BlueprintWindowLayout.CAPTURE_W : BlueprintWindowLayout.PLACEMENT_W;
            int height = capture ? BlueprintWindowLayout.CAPTURE_H : BlueprintWindowLayout.PLACEMENT_H;
            UiRect blueprint = new UiRect(logicalWidth - width - 8,
                    RtsMainlineLayout.TOP_H + 8, width, height).clampWithin(screen);
            panels.add(new NamedPanel("blueprint", blueprint));
        }
        if (scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_MATERIALS
                || scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_MATERIALS_READY
                || scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_MATERIALS_EMPTY) {
            int w = BlueprintWindowLayout.MATERIAL_W;
            int h = BlueprintWindowLayout.MATERIAL_H;
            panels.add(new NamedPanel("blueprint_materials", new UiRect(
                    (logicalWidth - w) / 2, Math.max(24, (logicalHeight - h) / 2), w, h)
                    .clampWithin(screen)));
        }
        if (scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_NAME_CAPTURE
                || scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_NAME_RENAME) {
            int w = BlueprintWindowLayout.NAME_W;
            int h = BlueprintWindowLayout.NAME_H;
            panels.add(new NamedPanel("blueprint_name", new UiRect(
                    (logicalWidth - w) / 2, Math.max(24, (logicalHeight - h) / 2), w, h)
                    .clampWithin(screen)));
        }
        if (CullingPreviewFixtures.supports(scenario.variant())) {
            panels.add(new NamedPanel("culling", new UiRect(
                    CullingWindowLayout.defaultWindowX(),
                    CullingWindowLayout.defaultWindowY(RtsMainlineLayout.TOP_H),
                    CullingWindowLayout.DEFAULT_WIDTH, CullingWindowLayout.DEFAULT_HEIGHT)
                    .clampWithin(screen)));
        }
        if (StoragePreviewFixtures.supports(scenario.variant())) {
            panels.add(new NamedPanel("storage_links", new UiRect(8,
                    RtsMainlineLayout.TOP_H + 6, StorageWindowLayout.WINDOW_W,
                    StorageWindowLayout.WINDOW_H).clampWithin(screen)));
        }
        if (WorkflowPreviewFixtures.supports(scenario.variant())) {
            int rows = WorkflowPreviewFixtures.forScenario(scenario, null).rows.size();
            panels.add(new NamedPanel("workflow", new UiRect(
                    logicalWidth - WorkflowWindowLayout.WINDOW_W - 8,
                    RtsMainlineLayout.TOP_H + 14,
                    WorkflowWindowLayout.WINDOW_W,
                    WorkflowWindowLayout.totalHeight(20, rows)).clampWithin(screen)));
        }
        if (GuidePreviewFixtures.supports(scenario.variant())) {
            panels.add(new NamedPanel("guide", new UiRect(8, RtsMainlineLayout.TOP_H + 6,
                    GuideWindowLayout.DEFAULT_W, GuideWindowLayout.DEFAULT_H).clampWithin(screen)));
        }
        if (FunnelPreviewFixtures.supports(scenario.variant())) {
            int y = FunnelBufferLayout.panelY(RtsMainlineLayout.TOP_H);
            int h = Math.max(20, bottom.panelY - y - 6);
            panels.add(new NamedPanel("funnel_buffer", new UiRect(
                    FunnelBufferLayout.panelX(logicalWidth), y,
                    FunnelBufferLayout.PANEL_W, h).clampWithin(screen)));
        }
        if (CraftQuantityPreviewFixtures.supports(scenario.variant())) {
            panels.add(new NamedPanel("craft_quantity", new UiRect(
                    (logicalWidth - CraftQuantityWindowLayout.DEFAULT_W) / 2,
                    Math.max(24, (logicalHeight - CraftQuantityWindowLayout.DEFAULT_H) / 2),
                    CraftQuantityWindowLayout.DEFAULT_W,
                    CraftQuantityWindowLayout.DEFAULT_H).clampWithin(screen)));
        }
        return new UiPreviewLayout(renderScale, screen, top, bottomBounds, jade, bottom, panels);
    }

    public double renderScale() { return renderScale; }
    public UiRect screen() { return screen; }
    public UiRect topBar() { return topBar; }
    public UiRect bottomBar() { return bottomBar; }
    public UiRect jadeReserve() { return jadeReserve; }
    public RtsMainlineLayout.BottomPanel bottom() { return bottom; }
    public List<NamedPanel> panels() { return panels; }

    public static final class NamedPanel {
        private final String id;
        private final UiRect bounds;

        private NamedPanel(String id, UiRect bounds) {
            this.id = id;
            this.bounds = bounds;
        }

        public String id() { return id; }
        public UiRect bounds() { return bounds; }
    }
}
