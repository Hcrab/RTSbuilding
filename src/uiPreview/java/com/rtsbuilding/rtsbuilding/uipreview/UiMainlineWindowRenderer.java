package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintInt3;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintMaterialUiState;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintUiState;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsId;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsRowKind;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiRow;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiState;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiControl;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiMode;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShapeOption;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiState;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiPhase;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiState;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiState;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiStatus;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiRow;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiState;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiCatalog;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiContext;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiState;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiTopic;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiState;
import com.rtsbuilding.rtsbuilding.uicore.craft.CraftQuantityOption;
import com.rtsbuilding.rtsbuilding.uicore.craft.CraftQuantityState;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.SettingsWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.QuickBuildWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.CullingWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.StorageWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowResumeWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.GuideWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.FunnelBufferLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftQuantityWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.WindowSliderLayout;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiControlChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.canvas.CullingWindowChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.canvas.QuickBuildChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.canvas.WorkflowChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.canvas.WorkflowResumeChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.canvas.StorageWindowChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.canvas.BlueprintWindowChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.canvas.WindowButtonChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.canvas.FunnelBufferChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.canvas.GuideWindowChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.canvas.WindowSliderChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.CraftQuantityStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.CullingWindowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintDialogStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.QuickBuildStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiControlVisualStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.SettingsWindowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.WorkflowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.WorkflowResumeStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.StorageWindowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintWindowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.FunnelBufferStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.GuideWindowStyle;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** 复刻 RtsWindowPanel chrome，并为设置、Quick Build、蓝图提供真实内容密度。 */
final class UiMainlineWindowRenderer {
    private final UiMainlineAssets assets;

    UiMainlineWindowRenderer(UiMainlineAssets assets) {
        this.assets = assets;
    }

    void render(BufferedImageUiCanvas canvas, UiPreviewLayout layout,
                UiLanguageBundle language, UiPreviewScenario scenario) {
        for (UiPreviewLayout.NamedPanel panel : layout.panels()) {
            if ("settings".equals(panel.id())) {
                drawSettings(canvas, panel.bounds(), language, scenario);
            } else if ("quick_build".equals(panel.id())) {
                drawQuickBuild(canvas, panel.bounds(), language,
                        QuickBuildPreviewFixtures.forScenario(scenario, language));
            } else if ("blueprint".equals(panel.id())) {
                drawBlueprint(canvas, panel.bounds(), language,
                        BlueprintPreviewFixtures.forScenario(scenario));
            } else if ("blueprint_materials".equals(panel.id())) {
                drawBlueprintMaterials(canvas, panel.bounds(), language,
                        BlueprintPreviewFixtures.forScenario(scenario));
            } else if ("blueprint_name".equals(panel.id())) {
                drawBlueprintName(canvas, panel.bounds(), language,
                        BlueprintPreviewFixtures.forScenario(scenario));
            } else if ("culling".equals(panel.id())) {
                drawCulling(canvas, panel.bounds(), language,
                        CullingPreviewFixtures.forScenario(scenario));
            } else if ("storage_links".equals(panel.id())) {
                drawStorageLinks(canvas,panel.bounds(),language,
                        StoragePreviewFixtures.forScenario(scenario,assets));
            } else if ("workflow".equals(panel.id())) {
                drawWorkflow(canvas, panel.bounds(), language,
                        WorkflowPreviewFixtures.forScenario(scenario, language));
            } else if ("guide".equals(panel.id())) {
                drawGuide(canvas, panel.bounds(), language,
                        GuidePreviewFixtures.forScenario(scenario));
            } else if ("funnel_buffer".equals(panel.id())) {
                int capacity = FunnelBufferLayout.visibleRows((int) panel.bounds().getHeight());
                drawFunnelBuffer(canvas, layout, panel.bounds(),
                        FunnelPreviewFixtures.forScenario(scenario, assets, capacity));
            } else if ("craft_quantity".equals(panel.id())) {
                CraftQuantityWindowLayout.Layout craftLayout = CraftQuantityWindowLayout.resolve(
                        (int) panel.bounds().getX() + 1, (int) panel.bounds().getY() + 20,
                        (int) panel.bounds().getWidth() - 2, (int) panel.bounds().getHeight() - 21);
                drawCraftQuantity(canvas, panel.bounds(),
                        CraftQuantityPreviewFixtures.forScenario(scenario,
                                CraftQuantityWindowLayout.visibleOptionRows(craftLayout)));
            } else if ("resume_placement".equals(panel.id())) {
                drawResumePlacement(
                        canvas,
                        panel.bounds(),
                        language,
                        WorkflowResumePreviewFixtures.placement(
                                scenario,
                                assets));
            } else if ("resume_blueprint".equals(panel.id())) {
                drawResumeBlueprint(
                        canvas,
                        panel.bounds(),
                        language,
                        WorkflowResumePreviewFixtures.blueprint(
                                scenario,
                                assets));
            }
        }
        if (scenario.variant() == UiPreviewScenario.Variant.SETTINGS_TOOLTIP) {
            UiRect anchor = layout.panels().get(0).bounds();
            UiRect tooltip = new UiRect(anchor.right() + 6, anchor.getY() + 64, 212, 42)
                    .clampWithin(layout.screen());
            recordCompactFrame(canvas, tooltip,
                    RtsMainlineTheme.TOOLTIP_BACKGROUND, RtsMainlineTheme.TOOLTIP_BORDER,
                    RtsMainlineTheme.WINDOW_BORDER_DARK);
            canvas.text(language.text("screen.rtsbuilding.settings.storage_auto_refresh"),
                    tooltip.getX() + 7, tooltip.getY() + 14, Color.WHITE);
            canvas.text(canvas.trimToWidth(
                            language.text("screen.rtsbuilding.settings.storage_auto_refresh.hint"), 198),
                    tooltip.getX() + 7, tooltip.getY() + 29, UiMainlinePreviewStyle.MUTED);
        }
    }

    private void drawResumePlacement(
            BufferedImageUiCanvas canvas,
            UiRect bounds,
            UiLanguageBundle language,
            WorkflowResumePreviewFixtures.Placement data) {
        drawChrome(
                canvas,
                bounds,
                language.text(
                        "screen.rtsbuilding.workflow.resume_placement.title"));
        WorkflowResumeWindowLayout.PlacementGeometry geometry =
                WorkflowResumeWindowLayout.placement(
                        (int) bounds.getX() + 1,
                        (int) bounds.getY() + 20,
                        (int) bounds.getWidth() - 2,
                        (int) bounds.getHeight() - 21,
                        data.hasConflicts());
        WorkflowResumeChromeRenderer.renderPlacement(
                canvas,
                geometry,
                data.enough(),
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY);
        canvas.image(assets.item(data.assetName), geometry.itemIcon);
        canvas.text(
                canvas.trimToWidth(
                        data.itemLabel,
                        geometry.innerWidth - 20),
                geometry.x + 20,
                geometry.y + 13,
                resumeColor(WorkflowResumeStyle.ITEM_TEXT));

        int row = 0;
        drawResumeStat(
                canvas,
                language,
                geometry,
                row++,
                "screen.rtsbuilding.workflow.resume_placement.remaining",
                Integer.toString(data.remaining),
                WorkflowResumeStyle.PRIMARY_TEXT);
        drawResumeStat(
                canvas,
                language,
                geometry,
                row++,
                "screen.rtsbuilding.workflow.resume_placement.already_placed",
                Integer.toString(data.alreadyPlaced),
                WorkflowResumeStyle.SECONDARY_TEXT);
        if (data.hasConflicts()) {
            drawResumeStat(
                    canvas,
                    language,
                    geometry,
                    row++,
                    "screen.rtsbuilding.workflow.resume_placement.conflicts",
                    Integer.toString(data.conflicts),
                    WorkflowResumeStyle.WARNING_TEXT);
        }
        drawResumeStat(
                canvas,
                language,
                geometry,
                row++,
                "screen.rtsbuilding.workflow.resume_placement.available",
                Long.toString(data.available),
                WorkflowResumeStyle.SUCCESS_TEXT);
        drawResumeStat(
                canvas,
                language,
                geometry,
                row++,
                "screen.rtsbuilding.workflow.resume_placement.needed",
                Integer.toString(data.needed),
                WorkflowResumeStyle.PRIMARY_TEXT);
        drawResumeStat(
                canvas,
                language,
                geometry,
                row,
                "screen.rtsbuilding.workflow.resume_placement.missing",
                data.enough()
                        ? language.text(
                                "screen.rtsbuilding.workflow.resume_placement.enough")
                        : Long.toString(data.missing),
                data.enough()
                        ? WorkflowResumeStyle.SUCCESS_TEXT
                        : WorkflowResumeStyle.ERROR_TEXT);

        if (geometry.hasConflicts) {
            drawResumeActionText(
                    canvas,
                    language,
                    geometry.primaryAction,
                    data.enough()
                            ? "screen.rtsbuilding.workflow.resume_placement.skip"
                            : "screen.rtsbuilding.workflow.insufficient_items",
                    WorkflowResumeStyle.action(
                            WorkflowResumeStyle.ActionKind.SKIP,
                            data.enough(),
                            false).text);
            drawResumeActionText(
                    canvas,
                    language,
                    geometry.secondaryAction,
                    data.enough()
                            ? "screen.rtsbuilding.workflow.resume_placement.overwrite"
                            : "screen.rtsbuilding.workflow.insufficient_items",
                    WorkflowResumeStyle.action(
                            WorkflowResumeStyle.ActionKind.OVERWRITE,
                            data.enough(),
                            false).text);
        } else {
            drawResumeActionText(
                    canvas,
                    language,
                    geometry.primaryAction,
                    data.enough()
                            ? "screen.rtsbuilding.workflow.resume_placement.restart"
                            : "screen.rtsbuilding.workflow.insufficient_items",
                    WorkflowResumeStyle.action(
                            WorkflowResumeStyle.ActionKind.RESUME,
                            data.enough(),
                            false).text);
        }
    }

    private void drawResumeBlueprint(
            BufferedImageUiCanvas canvas,
            UiRect bounds,
            UiLanguageBundle language,
            WorkflowResumePreviewFixtures.Blueprint data) {
        drawChrome(
                canvas,
                bounds,
                language.text(
                        "screen.rtsbuilding.workflow.blueprint_resume.title"));
        WorkflowResumeWindowLayout.BlueprintGeometry geometry =
                WorkflowResumeWindowLayout.blueprint(
                        (int) bounds.getX() + 1,
                        (int) bounds.getY() + 20,
                        (int) bounds.getWidth() - 2,
                        (int) bounds.getHeight() - 21,
                        data.visibleRows.size());
        WorkflowResumeChromeRenderer.renderBlueprint(
                canvas,
                geometry,
                data.enough(),
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY);
        canvas.text(
                language.format(
                        "screen.rtsbuilding.workflow.blueprint_resume.progress",
                        data.completed,
                        data.total,
                        data.total - data.completed),
                geometry.x,
                geometry.y + 9,
                resumeColor(WorkflowResumeStyle.PROGRESS_TEXT));
        int headerY = geometry.y + 31;
        canvas.text(
                language.text(
                        "screen.rtsbuilding.workflow.blueprint_resume.material"),
                geometry.x,
                headerY,
                resumeColor(WorkflowResumeStyle.LABEL_TEXT));
        canvas.text(
                language.text(
                        "screen.rtsbuilding.workflow.blueprint_resume.required"),
                geometry.requiredColumnX,
                headerY,
                resumeColor(WorkflowResumeStyle.LABEL_TEXT));
        canvas.text(
                language.text(
                        "screen.rtsbuilding.workflow.blueprint_resume.available"),
                geometry.availableColumnX,
                headerY,
                resumeColor(WorkflowResumeStyle.LABEL_TEXT));

        for (WorkflowResumeWindowLayout.BlueprintRowGeometry row
                : geometry.rows) {
            WorkflowResumePreviewFixtures.Material material =
                    data.visibleRows.get(row.visibleIndex);
            canvas.image(
                    assets.item(material.assetName),
                    row.itemIcon);
            canvas.text(
                    canvas.trimToWidth(material.label, 100),
                    row.itemIcon.getX() + 18,
                    row.itemIcon.getY() + 13,
                    resumeColor(WorkflowResumeStyle.ITEM_TEXT));
            canvas.text(
                    Integer.toString(material.required),
                    geometry.requiredColumnX,
                    row.row.getY() + 13,
                    resumeColor(WorkflowResumeStyle.PRIMARY_TEXT));
            canvas.text(
                    material.enough()
                            ? Long.toString(material.available)
                            : language.format(
                                    "screen.rtsbuilding.workflow.blueprint_resume.missing",
                                    material.missing()),
                    geometry.availableColumnX,
                    row.row.getY() + 13,
                    resumeColor(
                            material.enough()
                                    ? WorkflowResumeStyle.SUCCESS_TEXT
                                    : WorkflowResumeStyle.ERROR_TEXT));
        }
        drawResumeActionText(
                canvas,
                language,
                geometry.action,
                data.enough()
                        ? "screen.rtsbuilding.workflow.blueprint_resume.restart"
                        : "screen.rtsbuilding.workflow.blueprint_resume.insufficient_materials",
                WorkflowResumeStyle.action(
                        WorkflowResumeStyle.ActionKind.RESUME,
                        data.enough(),
                        false).text);
    }

    private static void drawResumeStat(
            BufferedImageUiCanvas canvas,
            UiLanguageBundle language,
            WorkflowResumeWindowLayout.PlacementGeometry geometry,
            int row,
            String labelKey,
            String value,
            UiColor valueColor) {
        int y = geometry.statY(row) + 9;
        canvas.text(
                language.text(labelKey),
                geometry.x,
                y,
                resumeColor(WorkflowResumeStyle.LABEL_TEXT));
        canvas.text(
                value,
                geometry.valueX,
                y,
                resumeColor(valueColor));
    }

    private static void drawResumeActionText(
            BufferedImageUiCanvas canvas,
            UiLanguageBundle language,
            UiRect action,
            String key,
            UiColor color) {
        canvas.centeredText(
                language.text(key),
                action.getX() + action.getWidth() / 2.0D,
                action.getY() + 13,
                resumeColor(color));
    }

    private static Color resumeColor(UiColor color) {
        return UiMainlinePreviewStyle.color(color.toArgb());
    }

    private void drawWorkflow(BufferedImageUiCanvas canvas, UiRect bounds,
                              UiLanguageBundle language, WorkflowUiState state) {
        drawChrome(canvas, bounds, language.text("screen.rtsbuilding.workflow.title"), false);
        int x = (int) bounds.getX() + 1;
        int firstRowY = (int) bounds.getY()
                + 20
                + WorkflowWindowLayout.PADDING;
        WorkflowWindowLayout.Geometry geometry =
                WorkflowWindowLayout.geometry(
                        x,
                        firstRowY,
                        state.rows.size());
        for (int index = 0; index < state.rows.size(); index++) {
            WorkflowUiRow row = state.rows.get(index);
            WorkflowWindowLayout.RowGeometry rowGeometry =
                    geometry.rows.get(index);
            WorkflowChromeRenderer.renderRow(
                    canvas,
                    rowGeometry,
                    row);

            WorkflowStyle.RowVisual rowVisual = WorkflowStyle.row(
                    row.suspended,
                    row.protectedWorkflow,
                    false);
            canvas.text(
                    canvas.trimToWidth(
                            row.label,
                            (int) rowGeometry.row.getWidth() - 8),
                    rowGeometry.row.getX() + WorkflowWindowLayout.LABEL_X,
                    rowGeometry.row.getY() + 11,
                    UiMainlinePreviewStyle.color(
                            rowVisual.labelText.toArgb()));
            canvas.text(
                    canvas.trimToWidth(
                            row.progressText,
                            (int) rowGeometry.progress.getWidth() - 4),
                    rowGeometry.progress.getX()
                            + WorkflowWindowLayout.PROGRESS_TEXT_X,
                    rowGeometry.progress.getY() + 7,
                    UiMainlinePreviewStyle.color(
                            rowVisual.progressText.toArgb()));

            drawWorkflowGlyph(
                    canvas,
                    rowGeometry.protect,
                    row.protectedWorkflow ? "◆" : "◇",
                    WorkflowStyle.protect(
                            row.protectedWorkflow,
                            false).text);
            drawWorkflowGlyph(
                    canvas,
                    rowGeometry.action,
                    row.suspended || row.paused ? "▶" : "⏸",
                    WorkflowStyle.action(
                            row.suspended,
                            row.paused,
                            false).text);
            drawWorkflowGlyph(
                    canvas,
                    rowGeometry.delete,
                    "✖",
                    WorkflowStyle.delete(false).text);
        }
    }

    private void drawWorkflowGlyph(
            BufferedImageUiCanvas canvas,
            UiRect bounds,
            String glyph,
            UiColor color) {
        canvas.centeredText(
                glyph,
                bounds.getX() + bounds.getWidth() / 2.0D,
                bounds.getY() + 13,
                UiMainlinePreviewStyle.color(color.toArgb()));
    }

    private void drawGuide(BufferedImageUiCanvas canvas, UiRect bounds,
                           UiLanguageBundle language, GuideUiState state) {
        drawChrome(canvas, bounds, language.text(GuideUiCatalog.titleKey(state.context)));
        int contentX = (int) bounds.getX() + 1;
        int contentY = (int) bounds.getY() + 20;
        int contentW = (int) bounds.getWidth() - 2;
        int contentH = (int) bounds.getHeight() - 21;
        GuideWindowLayout.Geometry geometry = GuideWindowLayout.geometry(
                new UiRect(contentX, contentY, contentW, contentH),
                state.context == GuideUiContext.BOTTOM);
        int tabW = geometry.topicTabWidth;
        GuideUiTopic[] topics = GuideUiCatalog.topics(state.context);
        int visible = geometry.visibleTopicRows;
        int end = Math.min(topics.length, state.topicScroll + visible);
        for (int index = state.topicScroll; index < end; index++) {
            UiRect row = geometry.topicRow(index, state.topicScroll);
            int tabX = (int) row.getX();
            int y = (int) row.getY();
            boolean active = index == state.page;
            GuideWindowChromeRenderer.renderTopic(canvas, row, active);
            if (state.context == GuideUiContext.BOTTOM) {
                canvas.text(canvas.trimToWidth(language.text(topics[index].titleKey),
                                tabW - GuideWindowLayout.TOPIC_LABEL_HORIZONTAL_PAD),
                        tabX + GuideWindowLayout.TOPIC_LABEL_INSET_X,
                        y + GuideWindowLayout.TOPIC_LABEL_BASELINE_Y,
                        UiMainlinePreviewStyle.color(
                                GuideWindowStyle.topicContent(active).toArgb()));
            } else {
                drawGuideIcon(canvas, topics[index],
                        tabX + GuideWindowLayout.TOPIC_ICON_CENTER_X,
                        y + GuideWindowLayout.TOPIC_ICON_CENTER_Y);
            }
        }
        GuideWindowChromeRenderer.renderScrollbar(canvas, geometry.topicScrollbar,
                state.topicScroll,
                topics.length, visible);
        int textX = (int) geometry.title.getX();
        int titleY = (int) geometry.title.getY();
        int maxTextW = (int) geometry.title.getWidth();
        GuideUiTopic topic = topics[state.page];
        canvas.text(canvas.trimToWidth(language.text(topic.titleKey), maxTextW),
                textX, titleY + GuideWindowLayout.TITLE_BASELINE_Y,
                UiMainlinePreviewStyle.color(GuideWindowStyle.TITLE_TEXT.toArgb()));
        int bodyY = (int) geometry.body.getY();
        List<String> lines = new ArrayList<String>();
        for (String key : topic.lineKeys) {
            lines.addAll(wrap(canvas, language.text(key), maxTextW));
        }
        int lineEnd = Math.min(lines.size(), state.textScroll + geometry.visibleTextLines);
        for (int line = state.textScroll; line < lineEnd; line++) {
            int visibleLine = line - state.textScroll;
            canvas.text(lines.get(line), textX,
                    bodyY + visibleLine * GuideWindowLayout.BODY_LINE_H
                            + GuideWindowLayout.BODY_BASELINE_Y,
                    UiMainlinePreviewStyle.color(GuideWindowStyle.BODY_TEXT.toArgb()));
        }
        GuideWindowChromeRenderer.renderScrollbar(canvas, geometry.bodyScrollbar,
                state.textScroll, lines.size(), geometry.visibleTextLines);
    }

    private void drawGuideIcon(BufferedImageUiCanvas canvas, GuideUiTopic topic, int cx, int cy) {
        String texture = null;
        boolean guideTexture = false;
        switch (topic.icon) {
            case HAND: texture = "mode_interact"; break;
            case LINK: texture = "mode_link"; break;
            case FUNNEL: texture = "mode_funnel"; break;
            case ROTATE: texture = "mode_rotate"; break;
            case BUILD: texture = "quick_build"; break;
            case PICKAXE: texture = "ultimine"; break;
            case GRID: texture = "chunk_view"; break;
            case GEAR: texture = "settings_gear"; break;
            default:
                texture = topic.icon.name().toLowerCase(java.util.Locale.ROOT);
                guideTexture = true;
                break;
        }
        canvas.image(guideTexture ? assets.guide(texture) : assets.topBar(texture, "active"),
                new UiRect(cx - 9, cy - 9, 18, 18));
    }

    private void drawFunnelBuffer(BufferedImageUiCanvas canvas, UiPreviewLayout layout,
                                  UiRect bounds, FunnelUiState state) {
        FunnelBufferLayout.Geometry geometry = FunnelBufferLayout.geometry(
                (int) layout.screen().getWidth(),
                (int) layout.topBar().getHeight(),
                (int) bounds.getHeight());
        FunnelBufferChromeRenderer.renderToggle(canvas, geometry.toggle, state.panelVisible);
        int toggleX = (int) geometry.toggle.getX();
        int toggleY = (int) geometry.toggle.getY();
        canvas.centeredText("BUFFER", toggleX + FunnelBufferLayout.TOGGLE_W / 2.0D,
                toggleY + 12, UiMainlinePreviewStyle.color(FunnelBufferStyle.PRIMARY_TEXT));
        if (!state.panelVisible || !geometry.panelRenderable) return;
        FunnelBufferChromeRenderer.renderPanel(canvas, geometry.panel);
        canvas.text("Funnel Buffer", geometry.panel.getX() + 6, geometry.panel.getY() + 13,
                FunnelBufferStyle.TITLE_TEXT);
        for (int index = 0; index < state.visibleEntries.size(); index++) {
            FunnelUiEntry entry = state.visibleEntries.get(index);
            UiRect row = geometry.row(index);
            UiRect slot = geometry.slot(index);
            boolean hovered = entry.sourceIndex == state.hoveredSourceIndex;
            FunnelBufferChromeRenderer.renderRow(canvas, row, slot, hovered);
            int rowX = (int) row.getX();
            int rowY = (int) row.getY();
            int rowW = (int) row.getWidth();
            String item = entry.itemId.substring(entry.itemId.indexOf(':') + 1);
            canvas.image(assets.item(item),
                    new UiRect(slot.getX() + 1, slot.getY() + 1, 16, 16));
            canvas.text(canvas.trimToWidth(entry.label, rowW - 30), rowX + 24, rowY + 12,
                    FunnelBufferStyle.PRIMARY_TEXT);
            canvas.text("x" + compact(entry.count), rowX + 24, rowY + 21,
                    FunnelBufferStyle.COUNT_TEXT);
        }
        if (state.totalEntries == 0) {
            canvas.text("empty", geometry.panel.getX() + 6, geometry.panel.getY() + 29,
                    FunnelBufferStyle.EMPTY_TEXT);
        }
    }

    private void drawCraftQuantity(BufferedImageUiCanvas canvas, UiRect bounds,
                                   CraftQuantityState state) {
        drawChrome(canvas, bounds, "Craft Recipe");
        CraftQuantityWindowLayout.Layout l = CraftQuantityWindowLayout.resolve(
                (int) bounds.getX() + 1, (int) bounds.getY() + 20,
                (int) bounds.getWidth() - 2, (int) bounds.getHeight() - 21);
        String item = state.itemId.substring(state.itemId.indexOf(':') + 1);
        canvas.image(assets.item(item), new UiRect(l.x, l.y, 16, 16));
        canvas.text(canvas.trimToWidth(state.itemLabel, Math.max(24, l.w - 28)),
                l.x + 22, l.y + 10, CraftQuantityStyle.ITEM_LABEL);
        CraftQuantityOption selected = state.selected();
        canvas.text("Each craft: x" + (selected == null ? 1 : selected.resultCount),
                l.x + 22, l.y + 22, CraftQuantityStyle.MUTED_TEXT);
        canvas.text("Recipes", l.x, l.optionsY - 1, CraftQuantityStyle.SECTION_LABEL);
        recordChrome(canvas, new UiRect(l.x, l.optionsY, l.optionsW, l.optionsH),
                CraftQuantityStyle.OPTIONS_BACKGROUND, CraftQuantityStyle.OPTIONS_BORDER_LIGHT,
                CraftQuantityStyle.OPTIONS_BORDER_DARK);
        int visible = CraftQuantityWindowLayout.visibleOptionRows(l);
        for (int row = 0; row < visible; row++) {
            int optionIndex = state.scroll + row;
            if (optionIndex >= state.options.size()) break;
            CraftQuantityOption option = state.options.get(optionIndex);
            int rowY = l.optionsY + 2 + row * CraftQuantityWindowLayout.OPTION_ROW_H;
            UiColor fill = CraftQuantityStyle.rowBackground(option.craftable,
                    optionIndex == state.selectedIndex);
            canvas.fill(new UiRect(l.x + 2, rowY, l.optionsW - 4,
                    CraftQuantityWindowLayout.OPTION_ROW_H - 1), fill);
            canvas.text(canvas.trimToWidth("x" + option.resultCount + " "
                            + (option.summary.isEmpty() ? "Recipe" : option.summary), l.optionsW - 56),
                    l.x + 6, rowY + 12, CraftQuantityStyle.ROW_TEXT);
            canvas.text(option.craftable ? "MAKE" : "MISS",
                    l.x + l.optionsW - 30, rowY + 12,
                    CraftQuantityStyle.badge(option.craftable));
        }
        String detail = selected == null ? "No recipe" : selected.craftable
                ? (selected.summary.isEmpty() ? "Recipe" : selected.summary)
                : (selected.missingSummary.isEmpty() ? "Missing ingredients." : selected.missingSummary);
        canvas.text(canvas.trimToWidth(detail, l.w), l.x, l.detailY + 9,
                CraftQuantityStyle.detail(selected != null && !selected.craftable));
        drawCraftButton(canvas, l.minusTenX, l.inputY, CraftQuantityWindowLayout.STEP_W,
                CraftQuantityWindowLayout.STEP_H, "-10", UiControlRole.HOLD_REPEAT);
        drawCraftButton(canvas, l.minusOneX, l.inputY, CraftQuantityWindowLayout.STEP_W,
                CraftQuantityWindowLayout.STEP_H, "-1", UiControlRole.HOLD_REPEAT);
        recordChrome(canvas, new UiRect(l.inputX, l.inputY,
                        CraftQuantityWindowLayout.INPUT_W, CraftQuantityWindowLayout.INPUT_H),
                RtsMainlineTheme.INPUT_BACKGROUND, RtsMainlineTheme.INPUT_BORDER_LIGHT,
                RtsMainlineTheme.INPUT_BORDER_DARK);
        canvas.centeredText(Integer.toString(state.quantity),
                l.inputX + CraftQuantityWindowLayout.INPUT_W / 2.0D, l.inputY + 11,
                UiMainlinePreviewStyle.color(RtsMainlineTheme.BUTTON_TEXT));
        drawCraftButton(canvas, l.plusOneX, l.inputY, CraftQuantityWindowLayout.STEP_W,
                CraftQuantityWindowLayout.STEP_H, "+1", UiControlRole.HOLD_REPEAT);
        drawCraftButton(canvas, l.plusTenX, l.inputY, CraftQuantityWindowLayout.STEP_W,
                CraftQuantityWindowLayout.STEP_H, "+10", UiControlRole.HOLD_REPEAT);
        canvas.text("Enter confirm, Esc cancel", l.x, l.helpY + 9, CraftQuantityStyle.MUTED_TEXT);
        drawCraftButton(canvas, l.cancelX, l.actionY, CraftQuantityWindowLayout.ACTION_W,
                CraftQuantityWindowLayout.ACTION_H, "Cancel", UiControlRole.DESTRUCTIVE_CONFIRM);
        drawCraftButton(canvas, l.confirmX, l.actionY, CraftQuantityWindowLayout.ACTION_W,
                CraftQuantityWindowLayout.ACTION_H, "Craft", UiControlRole.PRIMARY_ACTION);
    }

    private void drawCraftButton(BufferedImageUiCanvas canvas, int x, int y, int w, int h,
                                 String label, UiControlRole role) {
        UiControlChromeRenderer.compactFrame(canvas, new UiRect(x, y, w, h),
                role, UiControlState.enabled());
        canvas.centeredText(label, x + w / 2.0D, y + Math.max(11, h - 4),
                UiMainlinePreviewStyle.color(RtsMainlineTheme.BUTTON_TEXT));
    }

    private static void recordChrome(BufferedImageUiCanvas canvas, UiRect bounds,
                                     UiColor background, UiColor light, UiColor dark) {
        int quads = UiChromeRenderer.frame(canvas, bounds, 1.0D, background, light, dark);
        canvas.recordNineSliceQuads(quads);
    }

    private static void recordCompactFrame(BufferedImageUiCanvas canvas, UiRect bounds,
                                           UiColor background, UiColor light, UiColor dark) {
        UiCompactFrameRenderer.frame(canvas, bounds, background, light, dark);
    }

    private static String compact(long count) {
        if (count >= 1_000_000L) return (count / 1_000_000L) + "M";
        if (count >= 1_000L) return (count / 1_000L) + "K";
        return Long.toString(count);
    }

    private void drawStorageLinks(
            BufferedImageUiCanvas canvas,
            UiRect bounds,
            UiLanguageBundle language,
            StorageUiState state) {
        drawChrome(
                canvas,
                bounds,
                language.text(
                        "screen.rtsbuilding.storage_links.title"));
        int contentX = (int) bounds.getX() + 1;
        int contentY = (int) bounds.getY() + 20;
        StorageWindowLayout.Geometry geometry =
                StorageWindowLayout.geometry(
                        contentX,
                        contentY,
                        (int) bounds.getWidth() - 2,
                        (int) bounds.getHeight() - 21,
                        state.visibleEntries.size(),
                        state.totalRows,
                        state.scroll);
        canvas.text(
                language.text(
                        "screen.rtsbuilding.storage_links.header"),
                geometry.x,
                geometry.y + 9,
                UiMainlinePreviewStyle.color(
                        StorageWindowStyle.HEADER_TEXT.toArgb()));
        if (state.status != StorageUiStatus.READY) {
            String label = state.status == StorageUiStatus.LOADING
                    ? language.text(
                            "screen.rtsbuilding.storage_links.loading")
                    : state.status == StorageUiStatus.FAILED
                    ? language.text(state.errorMessage)
                    : language.text(
                            "screen.rtsbuilding.storage_links.empty");
            canvas.text(
                    canvas.trimToWidth(label, geometry.innerWidth),
                    geometry.x,
                    geometry.y + StorageWindowLayout.STATUS_Y + 9,
                    UiMainlinePreviewStyle.color(
                            StorageWindowStyle.statusText(
                                    state.status).toArgb()));
            if (state.status == StorageUiStatus.EMPTY) {
                canvas.text(
                        canvas.trimToWidth(
                                language.text(
                                        "screen.rtsbuilding.storage_links.empty_detail"),
                                geometry.innerWidth),
                        geometry.x,
                        geometry.y
                                + StorageWindowLayout.STATUS_Y
                                + StorageWindowLayout.STATUS_DETAIL_GAP
                                + 9,
                        UiMainlinePreviewStyle.color(
                                StorageWindowStyle.STATUS_DETAIL_TEXT
                                        .toArgb()));
            }
            return;
        }
        canvas.text(
                language.text(
                        "screen.rtsbuilding.storage_links.priority"),
                geometry.priorityColumnX,
                geometry.y + 12,
                UiMainlinePreviewStyle.color(
                        StorageWindowStyle.COLUMN_TEXT.toArgb()));
        canvas.text(
                language.text(
                        "screen.rtsbuilding.storage_links.mode_extract_header"),
                geometry.extractColumnX,
                geometry.y + 12,
                UiMainlinePreviewStyle.color(
                        StorageWindowStyle.COLUMN_TEXT.toArgb()));
        for (int index = 0;
                index < state.visibleEntries.size();
                index++) {
            StorageUiEntry entry = state.visibleEntries.get(index);
            StorageWindowLayout.RowGeometry rowGeometry =
                    geometry.rows.get(index);
            StorageWindowChromeRenderer.renderRow(
                    canvas,
                    rowGeometry,
                    entry,
                    false);
            int separator = entry.itemId.indexOf(':');
            String item = entry.itemId.substring(separator + 1);
            canvas.image(
                    assets.item(item),
                    rowGeometry.icon);
            int labelWidth = Math.max(
                    30,
                    (int) rowGeometry.priority.getX()
                            - ((int) rowGeometry.row.getX()
                            + StorageWindowLayout.ROW_TEXT_X)
                            - StorageWindowLayout.COLUMN_GAP);
            canvas.text(
                    canvas.trimToWidth(entry.label, labelWidth),
                    rowGeometry.row.getX()
                            + StorageWindowLayout.ROW_TEXT_X,
                    rowGeometry.row.getY() + 11,
                    UiMainlinePreviewStyle.color(
                            StorageWindowStyle.ROW_LABEL_TEXT.toArgb()));
            canvas.text(
                    entry.position,
                    rowGeometry.row.getX()
                            + StorageWindowLayout.ROW_TEXT_X,
                    rowGeometry.row.getY() + 22,
                    UiMainlinePreviewStyle.color(
                            StorageWindowStyle.ROW_POSITION_TEXT.toArgb()));
            canvas.text(
                    Integer.toString(entry.priority),
                    rowGeometry.priority.getX()
                            + StorageWindowLayout.CONTROL_TEXT_X,
                    rowGeometry.priority.getY() + 11,
                    UiMainlinePreviewStyle.color(
                            StorageWindowStyle.PRIORITY_TEXT.toArgb()));
            StorageWindowStyle.FrameVisual extractVisual =
                    StorageWindowStyle.extract(
                            entry.extractOnly,
                            false);
            canvas.centeredText(
                    language.text(
                            entry.extractOnly
                                    ? "screen.rtsbuilding.storage_links.mode_yes"
                                    : "screen.rtsbuilding.storage_links.mode_no"),
                    rowGeometry.extract.getX()
                            + rowGeometry.extract.getWidth() / 2.0D,
                    rowGeometry.extract.getY() + 11,
                    UiMainlinePreviewStyle.color(
                            extractVisual.text.toArgb()));
            canvas.centeredText(
                    language.text(
                            "screen.rtsbuilding.storage_links.unlink"),
                    rowGeometry.unlink.getX()
                            + rowGeometry.unlink.getWidth() / 2.0D,
                    rowGeometry.unlink.getY() + 11,
                    UiMainlinePreviewStyle.color(
                            StorageWindowStyle.UNLINK_TEXT.toArgb()));
        }
        StorageWindowChromeRenderer.renderScrollbar(
                canvas,
                geometry);
    }

    private void drawCulling(BufferedImageUiCanvas canvas, UiRect bounds,
                             UiLanguageBundle language, CullingUiState state) {
        drawChrome(canvas, bounds, language.text("screen.rtsbuilding.culling.title"));
        int contentX = (int) bounds.getX() + 1;
        int contentY = (int) bounds.getY() + 20;
        int contentW = (int) bounds.getWidth() - 2;
        int x = CullingWindowLayout.contentLeft(contentX);
        int w = CullingWindowLayout.contentInnerWidth(contentW);
        canvas.text(canvas.trimToWidth(language.format(
                        "screen.rtsbuilding.culling.count", state.boxCount), w),
                x, CullingWindowLayout.countRowY(contentY) + 9, UiMainlinePreviewStyle.TEXT);
        String phase = state.phase == CullingUiPhase.NEED_SECOND
                ? language.text("screen.rtsbuilding.culling.phase.second")
                : state.phase == CullingUiPhase.NEED_HEIGHT
                ? language.format("screen.rtsbuilding.culling.phase.height", state.previewHeight)
                : language.text("screen.rtsbuilding.culling.phase.idle");
        canvas.text(canvas.trimToWidth(phase, w), x,
                CullingWindowLayout.phaseRowY(contentY) + 9,
                CullingWindowStyle.PHASE_TEXT);
        if (!state.hasSelection()) {
            canvas.text(canvas.trimToWidth(language.text(
                            "screen.rtsbuilding.culling.no_selection"), w), x,
                    CullingWindowLayout.selectedRowY(contentY) + 9,
                    UiMainlinePreviewStyle.MUTED);
            return;
        }
        int buttonX = CullingWindowLayout.deleteButtonX(x, w);
        canvas.text(canvas.trimToWidth(language.format(
                        "screen.rtsbuilding.culling.selected", state.selectedId),
                        CullingWindowLayout.selectedTextWidth(w)), x,
                CullingWindowLayout.selectedRowY(contentY) + 9, UiMainlinePreviewStyle.TEXT);
        CullingWindowChromeRenderer.renderDeleteButton(
                canvas,
                new UiRect(buttonX,
                        CullingWindowLayout.buttonTop(CullingWindowLayout.deleteButtonRowY(contentY)),
                        CullingWindowLayout.DELETE_BUTTON_WIDTH,
                        CullingWindowLayout.buttonHeight()),
                false);
        canvas.centeredText(language.text("screen.rtsbuilding.culling.delete_button"),
                buttonX + CullingWindowLayout.DELETE_BUTTON_WIDTH / 2.0D,
                CullingWindowLayout.buttonTextY(CullingWindowLayout.deleteButtonRowY(contentY)) + 9,
                UiMainlinePreviewStyle.color(CullingWindowStyle.PRIMARY_TEXT));
        canvas.text(canvas.trimToWidth(language.format("screen.rtsbuilding.culling.dimensions",
                        state.width, state.height, state.depth), w), x,
                CullingWindowLayout.dimensionRowY(contentY) + 9, UiMainlinePreviewStyle.TEXT);
        canvas.text(canvas.trimToWidth(language.text("screen.rtsbuilding.culling.delete_hint"), w),
                x, CullingWindowLayout.hintRowY(contentY) + 9, UiMainlinePreviewStyle.MUTED);
    }

    private void drawSettings(BufferedImageUiCanvas canvas, UiRect bounds,
                              UiLanguageBundle language, UiPreviewScenario scenario) {
        drawChrome(canvas, bounds, language.text("screen.rtsbuilding.settings.title"));
        int contentX = (int) bounds.getX() + 1;
        int contentY = (int) bounds.getY() + 20;
        int contentW = (int) bounds.getWidth() - 2;
        int contentH = (int) bounds.getHeight() - 21;
        SettingsUiState rawState = SettingsPreviewFixtures.forScenario(
                scenario, language, canvas, contentX, contentW);
        SettingsWindowLayout.Layout rawLayout = settingsLayout(
                rawState, canvas, language, contentX, contentY, contentW);
        int maxScroll = SettingsWindowLayout.maxScroll(rawLayout, contentH);
        SettingsUiState state = rawState.withScroll(Math.min(rawState.scroll, maxScroll));
        SettingsWindowLayout.Layout settingsLayout = settingsLayout(
                state, canvas, language, contentX, contentY, contentW);
        UiRect clip = new UiRect(bounds.getX() + 2, bounds.getY() + 21,
                bounds.getWidth() - 4, bounds.getHeight() - 23);
        canvas.pushClip(clip);
        try {
            for (SettingsWindowLayout.Node node : settingsLayout.nodes) {
                int y = node.y - state.scroll;
                if (node.isSection()) {
                    drawCoreSettingsSection(canvas, node.x, y, node.width,
                            language.text(node.section.id.titleKey), node.section.expanded);
                } else {
                    drawCoreSettingsRow(canvas, node.row, node.x, y, node.width,
                            language);
                }
            }
            if (maxScroll > 0) {
                int trackH = Math.max(1, contentH);
                canvas.fill(new UiRect(contentX + contentW - 7, contentY + 2, 2,
                        Math.max(1, contentH - 4)), SettingsWindowStyle.SCROLL_TRACK);
                int totalH = settingsLayout.contentHeight + SettingsWindowLayout.CONTENT_TOP_PADDING;
                int thumbH = Math.max(18, (int) Math.round(trackH
                        * (trackH / (double) Math.max(trackH, totalH))));
                int thumbY = contentY + (int) Math.round((trackH - thumbH)
                        * (state.scroll / (double) maxScroll));
                canvas.fill(new UiRect(contentX + contentW - 8, thumbY, 4, thumbH),
                        SettingsWindowStyle.SCROLL_THUMB);
            }
        } finally {
            canvas.popClip();
        }
    }

    private SettingsWindowLayout.Layout settingsLayout(SettingsUiState state,
                                                        BufferedImageUiCanvas canvas,
                                                        UiLanguageBundle language,
                                                        int x, int y, int width) {
        return SettingsWindowLayout.layout(state, x, y, width, row -> {
            if (!row.hintExpanded) return 1;
            int hintX = x + 16 + SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE + 4;
            int hintW = Math.max(24, x + width - 92 - hintX - 8);
            return wrap(canvas, language.text(row.id.hintKey), hintW).size();
        });
    }

    private void drawCoreSettingsSection(BufferedImageUiCanvas canvas, int x, int y, int width,
                                         String label, boolean expanded) {
        recordCompactFrame(canvas, new UiRect(x + 8, y, width - 16,
                        SettingsWindowLayout.SECTION_HEADER_H),
                SettingsWindowStyle.SECTION_BACKGROUND, SettingsWindowStyle.SECTION_BORDER,
                SettingsWindowStyle.SECTION_DARK_BORDER);
        canvas.text(expanded ? "v" : ">", x + 16, y + 15, SettingsWindowStyle.VALUE);
        canvas.text(canvas.trimToWidth(label, width - 58), x + 31, y + 15,
                SettingsWindowStyle.VALUE);
    }

    private void drawCoreSettingsRow(BufferedImageUiCanvas canvas, SettingsUiRow row,
                                     int x, int y, int width, UiLanguageBundle language) {
        switch (row.id.kind) {
            case SENSITIVITY:
                drawCoreSensitivity(canvas, row, x, y, width, language);
                break;
            case STEP_VALUE:
                drawCoreStep(canvas, row, x, y, width, language);
                break;
            case SIMPLE_TOGGLE:
                canvas.text(canvas.trimToWidth(language.text(row.id.labelKey), width - 126),
                        x + 16, y + 18, row.enabled
                                ? SettingsWindowStyle.LABEL : SettingsWindowStyle.DISABLED_TEXT);
                drawCoreToggleButton(canvas, x + width - 92, y + 4, row.active, language);
                break;
            case HINT_TOGGLE:
                drawCoreHintToggle(canvas, row, x, y, width, language);
                break;
            default:
                break;
        }
    }

    private void drawCoreSensitivity(BufferedImageUiCanvas canvas, SettingsUiRow row,
                                     int x, int y, int width, UiLanguageBundle language) {
        canvas.text(canvas.trimToWidth(language.text(row.id.labelKey), width - 90),
                x + 16, y + 14, row.enabled
                        ? SettingsWindowStyle.LABEL : SettingsWindowStyle.DISABLED_TEXT);
        canvas.text(row.valueLabel, x + width - 60, y + 14,
                row.enabled ? SettingsWindowStyle.VALUE : SettingsWindowStyle.DISABLED_TEXT);
        int trackX = x + 16;
        int trackY = y + 24;
        int trackW = width - 32;
        canvas.fill(new UiRect(trackX, trackY, trackW, 4), SettingsWindowStyle.TRACK_BACKGROUND);
        canvas.fill(new UiRect(trackX + 1, trackY + 1, Math.max(0, trackW - 2), 2),
                SettingsWindowStyle.TRACK_FILL);
        int knobX = trackX + (int) Math.round(row.valueIndex
                / (double) Math.max(1, row.valueCount - 1) * trackW);
        canvas.fill(new UiRect(knobX - 3, trackY - 5, 7, 13),
                row.enabled ? SettingsWindowStyle.KNOB : SettingsWindowStyle.KNOB_DISABLED);
    }

    private void drawCoreStep(BufferedImageUiCanvas canvas, SettingsUiRow row,
                              int x, int y, int width, UiLanguageBundle language) {
        boolean sound = row.id == SettingsId.BLOCK_SOUNDS_PER_TICK;
        int buttonY = y + (sound ? 8 : 6);
        canvas.text(canvas.trimToWidth(language.text(row.id.labelKey), width - 156),
                x + 16, y + (sound ? 12 : 17), row.enabled
                        ? SettingsWindowStyle.LABEL : SettingsWindowStyle.DISABLED_TEXT);
        if (sound) {
            canvas.text(canvas.trimToWidth(language.text(row.id.hintKey), width - 156),
                    x + 16, y + 27, SettingsWindowStyle.HINT);
        }
        int minusX = x + width - 124;
        drawCoreStepButton(canvas, minusX, buttonY, "-");
        recordCompactFrame(canvas, new UiRect(minusX + 26, buttonY, 56, 22),
                SettingsWindowStyle.VALUE_BACKGROUND, SettingsWindowStyle.VALUE_BORDER,
                SettingsWindowStyle.VALUE_DARK_BORDER);
        canvas.centeredText(row.valueLabel, minusX + 54, buttonY + 15,
                UiMainlinePreviewStyle.color(SettingsWindowStyle.VALUE));
        drawCoreStepButton(canvas, minusX + 86, buttonY, "+");
    }

    private void drawCoreStepButton(BufferedImageUiCanvas canvas, int x, int y, String label) {
        recordCompactFrame(canvas, new UiRect(x, y, 22, 22),
                SettingsWindowStyle.STEP_BACKGROUND, SettingsWindowStyle.STEP_BORDER,
                SettingsWindowStyle.STEP_DARK_BORDER);
        canvas.centeredText(label, x + 11, y + 15,
                UiMainlinePreviewStyle.color(SettingsWindowStyle.VALUE));
    }

    private void drawCoreHintToggle(BufferedImageUiCanvas canvas, SettingsUiRow row,
                                    int x, int y, int width, UiLanguageBundle language) {
        canvas.text(canvas.trimToWidth(language.text(row.id.labelKey), width - 116),
                x + 16, y + 11, row.enabled
                        ? SettingsWindowStyle.LABEL : SettingsWindowStyle.DISABLED_TEXT);
        int hintX = x + 16 + (row.hintExpandable
                ? SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE + 4 : 0);
        int hintW = Math.max(24, x + width - 92 - hintX - 8);
        if (row.hintExpandable) {
            recordCompactFrame(canvas,
                    new UiRect(x + 16, y + 12, SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE,
                            SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE),
                    SettingsWindowStyle.STEP_BACKGROUND, SettingsWindowStyle.STEP_BORDER,
                    SettingsWindowStyle.STEP_DARK_BORDER);
            canvas.centeredText(row.hintExpanded ? "v" : ">",
                    x + 16 + SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE / 2.0D,
                    y + 21, UiMainlinePreviewStyle.color(SettingsWindowStyle.VALUE));
        }
        List<String> lines = row.hintExpanded
                ? wrap(canvas, language.text(row.id.hintKey), hintW)
                : java.util.Collections.singletonList(
                canvas.trimToWidth(language.text(row.id.hintKey), hintW));
        for (int i = 0; i < lines.size(); i++) {
            canvas.text(lines.get(i), hintX, y + 22 + i * SettingsWindowLayout.HINT_LINE_H,
                    row.enabled ? SettingsWindowStyle.HINT : SettingsWindowStyle.DISABLED_REASON);
        }
        drawCoreToggleButton(canvas, x + width - 92, y + 4, row.active, language);
    }

    private void drawCoreToggleButton(BufferedImageUiCanvas canvas, int x, int y,
                                      boolean active, UiLanguageBundle language) {
        recordCompactFrame(canvas, new UiRect(x, y, 76, 22),
                active ? SettingsWindowStyle.TOGGLE_ON : SettingsWindowStyle.TOGGLE_OFF,
                active ? SettingsWindowStyle.TOGGLE_ON_BORDER : SettingsWindowStyle.TOGGLE_OFF_BORDER,
                SettingsWindowStyle.TOGGLE_DARK_BORDER);
        canvas.fill(new UiRect(active ? x + 50 : x + 6, y + 4, 18, 14),
                active ? SettingsWindowStyle.TOGGLE_ON_KNOB : SettingsWindowStyle.TOGGLE_OFF_KNOB);
        canvas.centeredText(language.text(active ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"),
                x + 38, y + 15, UiMainlinePreviewStyle.color(SettingsWindowStyle.VALUE));
    }

    private static List<String> wrap(BufferedImageUiCanvas canvas, String text, int maxWidth) {
        List<String> lines = new ArrayList<String>();
        String safe = text == null ? "" : text;
        if (safe.isEmpty()) {
            lines.add("");
            return lines;
        }
        int start = 0;
        while (start < safe.length()) {
            int end = start + 1;
            int lastFit = end;
            while (end <= safe.length() && canvas.textWidth(safe.substring(start, end)) <= maxWidth) {
                lastFit = end;
                end++;
            }
            if (lastFit <= start) lastFit = Math.min(safe.length(), start + 1);
            if (lastFit < safe.length()) {
                int space = safe.lastIndexOf(' ', lastFit - 1);
                if (space > start) lastFit = space;
            }
            lines.add(safe.substring(start, lastFit).trim());
            start = lastFit;
            while (start < safe.length() && safe.charAt(start) == ' ') start++;
        }
        return lines;
    }

    /**
     * 复现参考截图里持久化的设置窗状态：显示分类已展开，滚动位置落在工作流、Jade 与
     * 辅助功能交界处。文本继续直接读取正式语言包，截图本身不会进入预览绘制链路。
     */
    private void drawReferenceScrolledSettings(BufferedImageUiCanvas canvas, UiRect bounds,
                                                int x, int w, UiLanguageBundle language) {
        int y = (int) bounds.getY() + 21;
        drawReferenceToggleButton(canvas, x + w - 84, y, false, language);
        canvas.text(canvas.trimToWidth(
                        language.text("screen.rtsbuilding.settings.show_storage_ready_popup.hint"), w - 102),
                x + 8, y + 12, UiMainlinePreviewStyle.MUTED);

        y += 22;
        y = referenceHintToggle(canvas, x, y, w,
                language.text("screen.rtsbuilding.settings.show_workflow_panel"),
                language.text("screen.rtsbuilding.settings.show_workflow_panel.hint"), true, language);
        y = referenceHintToggle(canvas, x, y, w,
                language.text("screen.rtsbuilding.settings.jade_panel_track_mouse"),
                language.text("screen.rtsbuilding.settings.jade_panel_track_mouse.hint"), true, language);
        y = referenceHintToggle(canvas, x, y, w,
                language.text("screen.rtsbuilding.settings.jade_panel_hidden"),
                language.text("screen.rtsbuilding.settings.jade_panel_hidden.hint"), false, language);
        y += 2;
        y = section(canvas, x, y, w,
                language.text("screen.rtsbuilding.settings.category.helpers"), true);
        y = toggle(canvas, x + 4, y, w - 8,
                language.text("screen.rtsbuilding.settings.auto_store"), "", true, language);
        toggle(canvas, x + 4, y, w - 8,
                language.text("screen.rtsbuilding.settings.storage_refresh_quiet"),
                language.text("screen.rtsbuilding.settings.storage_refresh_quiet.hint"), false, language);

        canvas.fill(new UiRect(bounds.right() - 7, bounds.getY() + 24, 2,
                bounds.getHeight() - 30), SettingsWindowStyle.SCROLL_TRACK);
        canvas.fill(new UiRect(bounds.right() - 8, bounds.getY() + 96, 4, 30),
                SettingsWindowStyle.SCROLL_THUMB);
    }

    private int referenceHintToggle(BufferedImageUiCanvas canvas, int x, int y, int w,
                                    String label, String hint, boolean active,
                                    UiLanguageBundle language) {
        canvas.text(canvas.trimToWidth(label, w - 108), x + 8, y + 10,
                UiMainlinePreviewStyle.TEXT);
        canvas.text(canvas.trimToWidth(hint, w - 108), x + 8, y + 23,
                UiMainlinePreviewStyle.MUTED);
        drawReferenceToggleButton(canvas, x + w - 84, y + 2, active, language);
        return y + 34;
    }

    private void drawReferenceToggleButton(BufferedImageUiCanvas canvas, int x, int y,
                                           boolean active, UiLanguageBundle language) {
        drawSettingsToggleChrome(canvas, new UiRect(x, y, 80, 22), active);
        canvas.centeredText(language.text(active ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"),
                x + 40, y + 15, UiMainlinePreviewStyle.color(SettingsWindowStyle.VALUE));
    }

    private int section(BufferedImageUiCanvas canvas, int x, int y, int w,
                        String label, boolean expanded) {
        recordCompactFrame(canvas, new UiRect(x, y, w, 22),
                SettingsWindowStyle.SECTION_BACKGROUND, SettingsWindowStyle.SECTION_BORDER,
                SettingsWindowStyle.SECTION_DARK_BORDER);
        canvas.text(expanded ? "v" : ">", x + 8, y + 15,
                SettingsWindowStyle.VALUE);
        canvas.text(canvas.trimToWidth(label, w - 34), x + 23, y + 15,
                SettingsWindowStyle.VALUE);
        return y + 22;
    }

    private int toggle(BufferedImageUiCanvas canvas, int x, int y, int w,
                       String label, String hint, boolean active,
                       UiLanguageBundle language) {
        canvas.text(canvas.trimToWidth(label, w - 108), x + 4, y + 12,
                UiMainlinePreviewStyle.TEXT);
        if (!hint.isEmpty()) {
            canvas.text(canvas.trimToWidth(hint, w - 108), x + 4, y + 25,
                    UiMainlinePreviewStyle.MUTED);
        }
        int buttonX = x + w - 80;
        drawSettingsToggleChrome(canvas, new UiRect(buttonX, y + 4, 76, 22), active);
        canvas.centeredText(language.text(active ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"),
                buttonX + 38, y + 19, UiMainlinePreviewStyle.color(SettingsWindowStyle.VALUE));
        return y + (hint.isEmpty() ? 28 : 34);
    }

    /**
     * 参考态和 Core 驱动态设置页都走同一份开关色板与紧凑框体，避免离屏截图悄悄复制生产颜色。
     */
    private void drawSettingsToggleChrome(BufferedImageUiCanvas canvas, UiRect bounds,
                                          boolean active) {
        recordCompactFrame(canvas, bounds,
                active ? SettingsWindowStyle.TOGGLE_ON : SettingsWindowStyle.TOGGLE_OFF,
                active ? SettingsWindowStyle.TOGGLE_ON_BORDER : SettingsWindowStyle.TOGGLE_OFF_BORDER,
                SettingsWindowStyle.TOGGLE_DARK_BORDER);
        canvas.fill(new UiRect(active ? bounds.right() - 26 : bounds.getX() + 6,
                        bounds.getY() + 4, 18, 14),
                active ? SettingsWindowStyle.TOGGLE_ON_KNOB : SettingsWindowStyle.TOGGLE_OFF_KNOB);
    }

    private void drawQuickBuild(BufferedImageUiCanvas canvas, UiRect bounds,
                                UiLanguageBundle language, QuickBuildUiState state) {
        drawChrome(canvas, bounds, language.text("screen.rtsbuilding.quick_build.title"));
        QuickBuildWindowLayout.Geometry g = QuickBuildWindowLayout.geometry(
                (int) bounds.getX(), (int) bounds.getY(), state.mode == QuickBuildUiMode.DESTROY);
        drawQuickMode(canvas, g.buildMode,
                language.text("screen.rtsbuilding.quick_build.mode_build"),
                state.mode == QuickBuildUiMode.BUILD, true);
        drawQuickMode(canvas, g.destroyMode,
                language.text("screen.rtsbuilding.quick_build.mode_destroy"),
                state.mode == QuickBuildUiMode.DESTROY, state.destroyEnabled);
        canvas.text(language.text("screen.rtsbuilding.quick_build.shape"),
                bounds.getX() + 10, g.sectionTitleY + 9,
                UiMainlinePreviewStyle.color(QuickBuildStyle.SECTION_TEXT));
        for (int i = 0; i < state.shapes.size(); i++) {
            QuickBuildUiShapeOption option = state.shapes.get(i);
            int slotX = g.shapeX(i);
            int slotY = g.shapeY(i);
            canvas.imageRegion(
                    assets.quickBuild(option.shape.textureName),
                    new UiRect(0, option.selected ? 450 : 0, 450, 450),
                    new UiRect(slotX, slotY, 32, 32));
        }
        canvas.text(language.text("screen.rtsbuilding.quick_build.fill"),
                g.rightX, g.sectionTitleY + 9,
                UiMainlinePreviewStyle.color(QuickBuildStyle.SECTION_TEXT));
        if (state.chainMode()) {
            int labelY = g.chainLabelY;
            canvas.text(language.text("screen.rtsbuilding.quick_build.chain_limit_label"),
                    g.rightX, labelY + 9,
                    UiMainlinePreviewStyle.color(QuickBuildStyle.SECTION_TEXT));
            int sliderW = QuickBuildWindowLayout.chainSliderWidth(
                    QuickBuildWindowLayout.WINDOW_W);
            int sliderY = g.chainSliderY;
            WindowSliderChromeRenderer.render(canvas, WindowSliderLayout.geometry(
                    new UiRect(g.rightX, sliderY, sliderW, 18),
                    state.chainMinimum, state.chainMaximum, state.chainLimit));
            canvas.text(Integer.toString(state.chainLimit), g.chainValueX(sliderW),
                    sliderY + 13, UiMainlinePreviewStyle.color(QuickBuildStyle.VALUE_TEXT));
        } else {
            int row = 0;
            for (QuickBuildUiControl control : state.controls) {
                int controlY = g.controlY(row++);
                drawQuickControl(canvas, g.rightX, controlY,
                        QuickBuildWindowLayout.CONTROL_W, control.label,
                        control.selected, control.enabled);
            }
        }

        QuickBuildChromeRenderer.renderStatus(
                canvas, g, state.progressCompleted, state.progressTotal);
        int textY = g.statusTextY + 9;
        if (state.mode == QuickBuildUiMode.DESTROY && state.progressCompleted >= 0) {
            canvas.text(canvas.trimToWidth(state.progressText + "  "
                    + language.format("screen.rtsbuilding.quick_build.destroy_remaining", state.remainingBlocks),
                    g.contentW), g.contentX, textY,
                    UiMainlinePreviewStyle.color(QuickBuildStyle.SUCCESS_TEXT));
        } else if (state.mode == QuickBuildUiMode.DESTROY) {
            canvas.text(canvas.trimToWidth(language.format(state.hintKey, state.confirmKeyLabel),
                    g.contentW), g.contentX, textY,
                    UiMainlinePreviewStyle.color(QuickBuildStyle.ERROR_TEXT));
        } else {
            String costText = "x " + state.costText;
            canvas.text(costText, g.contentX, textY,
                    UiMainlinePreviewStyle.color(QuickBuildStyle.SUCCESS_TEXT));
            if (state.missingBlocks > 0) canvas.text(language.format(
                    "screen.rtsbuilding.quick_build.missing_blocks", state.missingBlocks),
                    g.missingTextX(g.contentX + canvas.textWidth(costText)), textY,
                    UiMainlinePreviewStyle.color(QuickBuildStyle.ERROR_TEXT));
        }
        canvas.text(canvas.trimToWidth(language.format(
                        "screen.rtsbuilding.quick_build.dimensions", state.dimensions),
                g.contentW), g.contentX, textY + 14,
                UiMainlinePreviewStyle.color(QuickBuildStyle.DIMENSION_TEXT));
    }

    private void drawQuickMode(BufferedImageUiCanvas canvas, UiRect area,
                               String label, boolean selected, boolean enabled) {
        QuickBuildStyle.ModeVisual visual =
                QuickBuildStyle.mode(enabled, selected, false);
        QuickBuildChromeRenderer.renderMode(
                canvas, area, visual, selected ? 1.0D : 0.0D);
        canvas.centeredText(
                canvas.trimToWidth(label, (int) area.getWidth() - 4),
                area.getX() + area.getWidth() / 2.0D,
                area.getY() + 13,
                UiMainlinePreviewStyle.color(visual.text));
    }

    private void drawQuickControl(BufferedImageUiCanvas canvas, int x, int y, int w,
                                  String label, boolean selected, boolean enabled) {
        UiControlVisualStyle visual = controlVisual(
                UiControlRole.TOGGLE, selected, enabled);
        WindowButtonChromeRenderer.renderSolid(
                canvas,
                new UiRect(x, y, w, QuickBuildWindowLayout.CONTROL_H),
                visual);
        canvas.imageRegion(
                assets.image("textures/gui/general/mode_button.png"),
                new UiRect(0, selected ? 1024 : 0, 512, 512),
                new UiRect(x + 2, y + 2, 16, 16));
        canvas.centeredText(canvas.trimToWidth(label, w - 22), x + w / 2.0D,
                y + 14, UiMainlinePreviewStyle.color(visual.getText()));
    }

    private void drawBlueprint(BufferedImageUiCanvas canvas, UiRect bounds,
                               UiLanguageBundle language, BlueprintUiState state) {
        drawChrome(canvas, bounds, language.text(state.isCapture()
                ? "screen.rtsbuilding.blueprints.window_title_capture"
                : "screen.rtsbuilding.blueprints.window_title_placement"));
        int contentX = (int) bounds.getX() + 1;
        int contentY = (int) bounds.getY() + 20;
        int contentW = (int) bounds.getWidth() - 2;
        int contentH = (int) bounds.getHeight() - 21;
        BlueprintWindowLayout.Geometry geometry = BlueprintWindowLayout.geometry(
                state.isCapture(), contentX, contentY, contentW, contentH);
        if (state.isCapture()) {
            drawBlueprintCapture(canvas, language, state, geometry);
        } else {
            drawBlueprintPlacement(canvas, language, state, geometry);
        }
    }

    private void drawBlueprintCapture(BufferedImageUiCanvas canvas, UiLanguageBundle language,
                                      BlueprintUiState state, BlueprintWindowLayout.Geometry g) {
        canvas.text(language.text("screen.rtsbuilding.blueprints.capture_tool_title"),
                g.x, g.y + 9, UiMainlinePreviewStyle.TEXT);
        boolean complete = state.mode == BlueprintUiState.Mode.CAPTURE_READY
                || state.mode == BlueprintUiState.Mode.CAPTURE_SAVING;
        int hintColor = BlueprintWindowStyle.captureState(complete).toArgb();
        canvas.text(canvas.trimToWidth(language.text(
                        "screen.rtsbuilding.blueprints.capture_window_hint"), g.width),
                g.x, g.y + 23, UiMainlinePreviewStyle.color(hintColor));
        canvas.text(canvas.trimToWidth(language.text(
                        "screen.rtsbuilding.blueprints.capture_window_scroll_hint"), g.width),
                g.x, g.y + 35, UiMainlinePreviewStyle.MUTED);
        if (complete) {
            canvas.text(canvas.trimToWidth(language.format(
                            "screen.rtsbuilding.blueprints.capture_size", size(state.captureSize)), g.width),
                    g.x, g.y + 51, UiMainlinePreviewStyle.color(
                            BlueprintWindowStyle.INFO_TEXT));
        }
        String status;
        if (state.mode == BlueprintUiState.Mode.CAPTURE_SAVING) {
            status = state.status;
        } else if (complete) {
            status = language.format("screen.rtsbuilding.blueprints.capture_blocks", state.captureBlockCount);
        } else if (state.mode == BlueprintUiState.Mode.CAPTURE_WAITING_SECOND) {
            status = language.text("screen.rtsbuilding.blueprints.capture_waiting_b");
        } else {
            status = language.text("screen.rtsbuilding.blueprints.capture_waiting_a");
        }
        drawStatus(canvas, g.x, g.statusY, g.width, status,
                complete
                        ? BlueprintWindowStyle.INFO_TEXT.toArgb()
                        : BlueprintWindowStyle.WARNING_TEXT.toArgb());
        int buttonW = (g.width - BlueprintWindowLayout.GAP) / 2;
        drawWindowButton(canvas, g.x, g.footerY, buttonW,
                language.text("screen.rtsbuilding.blueprints.save_area"),
                state.mode == BlueprintUiState.Mode.CAPTURE_READY, true);
        drawWindowButton(canvas, g.x + buttonW + BlueprintWindowLayout.GAP, g.footerY, buttonW,
                language.text("screen.rtsbuilding.blueprints.capture_cancel"),
                state.mode != BlueprintUiState.Mode.CAPTURE_SAVING, false);
    }

    private void drawBlueprintPlacement(BufferedImageUiCanvas canvas, UiLanguageBundle language,
                                        BlueprintUiState state, BlueprintWindowLayout.Geometry g) {
        drawSectionFrame(canvas, g.x, g.y, g.width, BlueprintWindowLayout.SELECTOR_H);
        int selectorX = g.x + BlueprintWindowLayout.SECTION_PAD;
        int selectorY = g.y + 8;
        int selectorW = g.width - BlueprintWindowLayout.SECTION_PAD * 2;
        int nameW = Math.min(150, Math.max(56, selectorW - 36 - 16));
        int groupW = BlueprintWindowLayout.SMALL_BUTTON_W * 2
                + BlueprintWindowLayout.CONTROL_GAP * 2 + nameW;
        int groupX = selectorX + Math.max(0, (selectorW - groupW) / 2);
        drawWindowButton(canvas, groupX, selectorY, BlueprintWindowLayout.SMALL_BUTTON_W,
                "<", true, false);
        canvas.centeredText(canvas.trimToWidth(state.blueprintName, nameW),
                groupX + BlueprintWindowLayout.SMALL_BUTTON_W
                        + BlueprintWindowLayout.CONTROL_GAP + nameW / 2.0D,
                selectorY + 14, UiMainlinePreviewStyle.color(
                        BlueprintWindowStyle.PRIMARY_TEXT));
        drawWindowButton(canvas, groupX + BlueprintWindowLayout.SMALL_BUTTON_W
                        + BlueprintWindowLayout.CONTROL_GAP + nameW
                        + BlueprintWindowLayout.CONTROL_GAP,
                selectorY, BlueprintWindowLayout.SMALL_BUTTON_W, ">", true, false);
        int sizeW = Math.min(74, Math.max(42, state.blueprintSize.length() * 6 + 6));
        int detailGroupW = sizeW + BlueprintWindowLayout.CONTROL_GAP
                + BlueprintWindowLayout.DETAILS_BUTTON_W;
        int sizeX = selectorX + Math.max(0, (selectorW - detailGroupW) / 2);
        canvas.centeredText(canvas.trimToWidth(state.blueprintSize, sizeW),
                sizeX + sizeW / 2.0D, selectorY + 41,
                UiMainlinePreviewStyle.color(BlueprintWindowStyle.MUTED_TEXT));
        drawWindowButton(canvas, sizeX + sizeW + BlueprintWindowLayout.CONTROL_GAP,
                selectorY + 27, BlueprintWindowLayout.DETAILS_BUTTON_W,
                language.text("screen.rtsbuilding.blueprints.details"), true, false);

        int positionY = g.y + BlueprintWindowLayout.SELECTOR_H + BlueprintWindowLayout.GAP;
        drawSectionFrame(canvas, g.x, positionY, g.width, BlueprintWindowLayout.POSITION_H);
        canvas.text(language.text("screen.rtsbuilding.blueprints.window_position"),
                g.x + BlueprintWindowLayout.SECTION_PAD, positionY + 15,
                UiMainlinePreviewStyle.TEXT);
        BlueprintInt3 anchor = state.anchor == null ? new BlueprintInt3(0, 0, 0) : state.anchor;
        int[] values = new int[] {anchor.x, anchor.y, anchor.z};
        String[] axes = new String[] {"X", "Y", "Z"};
        int rowWidth = 10 + 4 + 18 + 4 + BlueprintWindowLayout.POSITION_INPUT_W + 4 + 18;
        int rowX = g.x + BlueprintWindowLayout.SECTION_PAD
                + Math.max(0, (g.width - BlueprintWindowLayout.SECTION_PAD * 2 - rowWidth) / 2);
        for (int i = 0; i < 3; i++) {
            int rowY = positionY + 22 + i * 26;
            canvas.text(axes[i], rowX, rowY + 14,
                    UiMainlinePreviewStyle.color(
                            BlueprintWindowStyle.axisLabel(state.isPinned())));
            drawWindowButton(canvas, rowX + 14, rowY, 18, "-", state.isPinned(), false);
            drawTextField(canvas, rowX + 36, rowY, BlueprintWindowLayout.POSITION_INPUT_W,
                    state.isPinned() ? Integer.toString(values[i]) : "", state.isPinned());
            drawWindowButton(canvas, rowX + 104, rowY, 18, "+", state.isPinned(), false);
        }

        if (state.isPinned()) {
            drawStatus(canvas, g.x, g.statusY, g.width,
                    language.text("screen.rtsbuilding.blueprints.status.ready_to_build")
                            + " · " + language.text("screen.rtsbuilding.blueprints.status.ready_to_build_controls"),
                    BlueprintWindowStyle.READY_TEXT.toArgb());
        } else {
            drawStatus(canvas, g.x, g.statusY, g.width,
                    language.text("screen.rtsbuilding.blueprints.placement_window_hint"),
                    BlueprintWindowStyle.PLACEMENT_WARNING_TEXT.toArgb());
        }
        int actionW = Math.min(180, Math.max(120, g.width));
        int actionX = g.x + Math.max(0, (g.width - actionW) / 2);
        drawWindowButton(canvas, actionX, g.actionY, actionW,
                language.text("screen.rtsbuilding.blueprints.build_preview"), state.isPinned(), true);
        drawWindowButton(canvas, actionX, g.actionY + BlueprintWindowLayout.BUTTON_H
                        + BlueprintWindowLayout.CONTROL_GAP, actionW,
                language.text("screen.rtsbuilding.blueprints.capture_cancel"), true, false);
    }

    private void drawSectionFrame(BufferedImageUiCanvas canvas, int x, int y, int w, int h) {
        BlueprintWindowChromeRenderer.renderSection(
                canvas, new UiRect(x, y, w, h));
    }

    private void drawStatus(BufferedImageUiCanvas canvas, int x, int y, int w,
                            String text, int color) {
        BlueprintWindowChromeRenderer.renderStatus(
                canvas, new UiRect(x, y, w, BlueprintWindowLayout.STATUS_H));
        canvas.centeredText(canvas.trimToWidth(text, w - 12), x + w / 2.0D,
                y + 21, UiMainlinePreviewStyle.color(color));
    }

    private void drawTextField(BufferedImageUiCanvas canvas, int x, int y, int w,
                               String value, boolean enabled) {
        BlueprintWindowChromeRenderer.renderField(
                canvas,
                new UiRect(x, y, w, BlueprintWindowLayout.BUTTON_H),
                enabled);
        canvas.centeredText(value, x + w / 2.0D, y + 14,
                UiMainlinePreviewStyle.color(enabled
                        ? BlueprintWindowStyle.PRIMARY_TEXT
                        : BlueprintWindowStyle.MUTED_TEXT));
    }

    private void drawWindowButton(BufferedImageUiCanvas canvas, int x, int y, int w,
                                  String label, boolean enabled, boolean primary) {
        UiControlVisualStyle visual = controlVisual(
                primary
                        ? UiControlRole.PRIMARY_ACTION
                        : UiControlRole.COMMAND,
                false,
                enabled);
        if (primary && enabled) {
            BlueprintWindowChromeRenderer.renderPrimaryAction(
                    canvas, new UiRect(x, y, w, BlueprintWindowLayout.BUTTON_H));
        } else {
            WindowButtonChromeRenderer.renderSolid(
                    canvas,
                    new UiRect(x, y, w, BlueprintWindowLayout.BUTTON_H),
                    visual);
        }
        canvas.centeredText(canvas.trimToWidth(label, Math.max(8, w - 10)), x + w / 2.0D,
                y + 14, UiMainlinePreviewStyle.color(primary && enabled
                        ? BlueprintWindowStyle.PRIMARY_TEXT
                        : visual.getText()));
    }

    private static UiControlVisualStyle controlVisual(
            UiControlRole role,
            boolean selected,
            boolean enabled) {
        return UiControlVisualStyle.resolve(
                role,
                new UiControlState(
                        enabled,
                        selected,
                        false,
                        false,
                        enabled ? "" : "disabled"));
    }

    private static String size(BlueprintInt3 value) {
        return value.x + "x" + value.y + "x" + value.z;
    }

    private void drawBlueprintMaterials(BufferedImageUiCanvas canvas, UiRect bounds,
                                        UiLanguageBundle language, BlueprintUiState state) {
        drawChrome(canvas, bounds, language.text("screen.rtsbuilding.blueprints.details_title"));
        int x = (int) bounds.getX() + 1;
        int y = (int) bounds.getY() + 20;
        int w = (int) bounds.getWidth() - 2;
        int h = (int) bounds.getHeight() - 21;
        BlueprintWindowLayout.MaterialDialogGeometry geometry =
                BlueprintWindowLayout.materialDialog(x, y, w, h);
        canvas.text(canvas.trimToWidth(state.materials.blueprintName, w - 20),
                x + 10, y + 17, BlueprintDialogStyle.PRIMARY_TEXT);
        String summary = state.materials.rows.isEmpty()
                ? language.text("screen.rtsbuilding.blueprints.materials_all_ready")
                : language.format("screen.rtsbuilding.blueprints.details_summary",
                        state.materials.percent, state.materials.buildable, state.materials.total,
                        state.materials.missingTypes, state.materials.unsupportedTypes,
                        state.materials.missingBlockTypes);
        canvas.text(canvas.trimToWidth(summary, w - 20), x + 10, y + 30,
                state.materials.allReady() ? BlueprintDialogStyle.READY
                        : BlueprintDialogStyle.WARNING);
        int listX = geometry.listX;
        int listY = geometry.listY;
        int listW = geometry.listW;
        int listH = geometry.listH;
        recordChrome(canvas, new UiRect(listX, listY, listW, listH),
                BlueprintDialogStyle.LIST_BACKGROUND, BlueprintDialogStyle.LIST_BORDER,
                BlueprintDialogStyle.DARK_BORDER);
        int columns = geometry.columns();
        int cellW = (listW - 8 - (columns - 1) * BlueprintWindowLayout.MATERIAL_COLUMN_GAP) / columns;
        for (int i = 0; i < state.materials.rows.size(); i++) {
            int row = i / columns;
            int column = i % columns;
            int rowX = listX + 4 + column * (cellW + BlueprintWindowLayout.MATERIAL_COLUMN_GAP);
            int rowY = listY + 3 + row * BlueprintWindowLayout.MATERIAL_ROW_H;
            BlueprintMaterialUiState.Row line = state.materials.rows.get(i);
            // 生产缺失物品占位符是单块 14x14 色块，不应在离屏侧额外制造一组九宫格。
            canvas.fill(new UiRect(rowX + 6, rowY + 4, 14, 14),
                    BlueprintDialogStyle.MISSING_ICON_BACKGROUND);
            canvas.centeredText("?", rowX + 13, rowY + 15,
                    UiMainlinePreviewStyle.color(BlueprintDialogStyle.MISSING_ICON_TEXT));
            int detailW = Math.min(86, Math.max(54, cellW / 3));
            int detailX = rowX + cellW - detailW - 4;
            canvas.text(canvas.trimToWidth(line.label, Math.max(24, detailX - rowX - 28)),
                    rowX + 26, rowY + 11, BlueprintDialogStyle.PRIMARY_TEXT);
            canvas.text(canvas.trimToWidth(line.detail, detailW), detailX, rowY + 16,
                    BlueprintDialogStyle.materialTone(line.tone));
        }
    }

    private void drawBlueprintName(BufferedImageUiCanvas canvas, UiRect bounds,
                                   UiLanguageBundle language, BlueprintUiState state) {
        drawChrome(canvas, bounds, language.text(state.captureNameMode
                ? "screen.rtsbuilding.blueprints.name_dialog_capture_title"
                : "screen.rtsbuilding.blueprints.name_dialog_rename_title"));
        int x = (int) bounds.getX() + 1;
        int y = (int) bounds.getY() + 20;
        int w = (int) bounds.getWidth() - 2;
        int h = (int) bounds.getHeight() - 21;
        if (state.captureNameMode) {
            canvas.text(language.text("screen.rtsbuilding.blueprints.capture_preview_title"),
                    x + 10, y + 19, BlueprintDialogStyle.CAPTURE_TEXT);
            canvas.text(language.format("screen.rtsbuilding.blueprints.capture_preview_summary",
                            size(state.captureSize), state.captureBlockCount),
                    x + 10, y + 31, BlueprintDialogStyle.READY);
        } else {
            canvas.text(language.format("screen.rtsbuilding.blueprints.name_dialog_current", state.blueprintName),
                    x + 10, y + 19, BlueprintDialogStyle.CURRENT_NAME_TEXT);
        }
        BlueprintWindowLayout.NameDialogGeometry geometry =
                BlueprintWindowLayout.nameDialog(x, y, w, h);
        canvas.text(language.text("screen.rtsbuilding.blueprints.name_dialog_label"),
                geometry.inputX, geometry.inputY - 2, BlueprintDialogStyle.LABEL_TEXT);
        recordChrome(canvas, new UiRect(geometry.inputX, geometry.inputY, geometry.inputW, 18),
                BlueprintDialogStyle.INPUT_BACKGROUND, BlueprintDialogStyle.INPUT_BORDER,
                BlueprintDialogStyle.DARK_BORDER);
        canvas.text(canvas.trimToWidth(state.nameDraft + "_", geometry.inputW - 8),
                geometry.inputX + 4, geometry.inputY + 13, BlueprintDialogStyle.PRIMARY_TEXT);
        drawBlueprintDialogButton(canvas, geometry.confirmX, geometry.buttonY,
                BlueprintWindowLayout.NAME_CONFIRM_W,
                language.text("screen.rtsbuilding.blueprints.name_dialog_confirm"));
        drawBlueprintDialogButton(canvas, geometry.cancelX, geometry.buttonY,
                BlueprintWindowLayout.NAME_CANCEL_W,
                language.text("screen.rtsbuilding.blueprints.name_dialog_cancel"));
    }

    private static void drawBlueprintDialogButton(BufferedImageUiCanvas canvas,
                                                  int x, int y, int width, String label) {
        recordChrome(canvas, new UiRect(x, y, width, BlueprintWindowLayout.NAME_BUTTON_H),
                BlueprintDialogStyle.BUTTON_BACKGROUND, BlueprintDialogStyle.BUTTON_BORDER,
                BlueprintDialogStyle.BUTTON_DARK_BORDER);
        canvas.centeredText(canvas.trimToWidth(label, width - 6), x + width / 2.0D,
                y + 12, UiMainlinePreviewStyle.color(BlueprintDialogStyle.PRIMARY_TEXT));
    }

    private void drawChrome(BufferedImageUiCanvas canvas, UiRect bounds, String title) {
        drawChrome(canvas, bounds, title, true);
    }

    private void drawChrome(BufferedImageUiCanvas canvas, UiRect bounds, String title,
                            boolean closable) {
        recordChrome(canvas, bounds, RtsMainlineTheme.WINDOW_BACKGROUND,
                RtsMainlineTheme.WINDOW_BORDER_LIGHT, RtsMainlineTheme.WINDOW_BORDER_DARK);
        canvas.fill(new UiRect(bounds.getX() + 1, bounds.getY() + 1,
                bounds.getWidth() - 2, 19), RtsMainlineTheme.WINDOW_TITLE);
        canvas.text(canvas.trimToWidth(title, (int) bounds.getWidth() - (closable ? 36 : 16)),
                bounds.getX() + 8, bounds.getY() + 14,
                UiMainlinePreviewStyle.color(RtsMainlineTheme.WINDOW_TITLE_TEXT));
        if (closable) {
            canvas.imageRegion(assets.closeButton(), new UiRect(0, 0, 450, 450),
                    new UiRect(bounds.right() - 17, bounds.getY() + 3, 14, 14));
        }
    }
}
