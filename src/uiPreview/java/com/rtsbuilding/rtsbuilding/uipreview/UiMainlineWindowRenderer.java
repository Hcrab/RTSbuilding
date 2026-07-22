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
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.SettingsWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.QuickBuildWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.CullingWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.StorageWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.GuideWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.FunnelBufferLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftQuantityWindowLayout;

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
            }
        }
        if (scenario.variant() == UiPreviewScenario.Variant.SETTINGS_TOOLTIP) {
            UiRect anchor = layout.panels().get(0).bounds();
            UiRect tooltip = new UiRect(anchor.right() + 6, anchor.getY() + 64, 212, 42)
                    .clampWithin(layout.screen());
            UiMainlinePreviewStyle.frame(canvas, tooltip,
                    0xF018202A, 0xFF8CA5BE, 0xFF0B0F14);
            canvas.text(language.text("screen.rtsbuilding.settings.storage_auto_refresh"),
                    tooltip.getX() + 7, tooltip.getY() + 14, Color.WHITE);
            canvas.text(canvas.trimToWidth(
                            language.text("screen.rtsbuilding.settings.storage_auto_refresh.hint"), 198),
                    tooltip.getX() + 7, tooltip.getY() + 29, UiMainlinePreviewStyle.MUTED);
        }
    }

    private void drawWorkflow(BufferedImageUiCanvas canvas, UiRect bounds,
                              UiLanguageBundle language, WorkflowUiState state) {
        drawChrome(canvas, bounds, language.text("screen.rtsbuilding.workflow.title"), false);
        int x = (int) bounds.getX() + 1;
        int y = (int) bounds.getY() + 20 + WorkflowWindowLayout.PADDING;
        int rowW = WorkflowWindowLayout.rowWidth();
        for (WorkflowUiRow row : state.rows) {
            boolean suspended = row.suspended;
            int bg = row.protectedWorkflow
                    ? 0xAA315B70 : suspended ? 0xAA2A2820 : 0xAA1A222C;
            int border = row.protectedWorkflow
                    ? 0xFFA8E8FF : suspended ? 0xFF8A7A4A : 0xFF5E738A;
            int labelColor = row.protectedWorkflow
                    ? 0xFFEAFBFF : suspended ? 0xFFE7C46A : 0xFFEAF2FF;
            int barFill = row.protectedWorkflow
                    ? 0xDDA8E8FF : suspended ? 0xAA8A7A3A : 0xFF88BEF4;
            UiMainlinePreviewStyle.frame(canvas,
                    new UiRect(x, y, rowW, WorkflowWindowLayout.ROW_H),
                    bg, border, suspended ? 0xFF0D0D0A : 0xFF0D1117);
            canvas.text(canvas.trimToWidth(row.label, rowW - 8), x + 4, y + 11,
                    UiMainlinePreviewStyle.color(labelColor));
            int barX = x + 4;
            int barY = y + 12;
            int barW = rowW - 8;
            canvas.fill(new UiRect(barX, barY, barW, WorkflowWindowLayout.BAR_H),
                    UiMainlinePreviewStyle.color(suspended ? 0xAA303030 : 0xAA202832));
            int fillW = (int) Math.round(row.progress() * barW);
            if (fillW > 0) {
                canvas.fill(new UiRect(barX, barY, fillW, WorkflowWindowLayout.BAR_H),
                        UiMainlinePreviewStyle.color(barFill));
            }
            canvas.text(canvas.trimToWidth(row.progressText, barW - 4),
                    barX + 2, barY + 7, UiMainlinePreviewStyle.color(0xCCFFFFFF));

            int protectX = WorkflowWindowLayout.protectX(x);
            drawWorkflowButton(canvas, protectX, y, row.protectedWorkflow ? "◆" : "◇",
                    row.protectedWorkflow ? 0xCC4DAFD8 : 0xAA263442,
                    row.protectedWorkflow ? 0xFFA8E8FF : 0xFF5E738A);
            int actionX = WorkflowWindowLayout.actionX(x);
            boolean resume = suspended || row.paused;
            drawWorkflowButton(canvas, actionX, y, resume ? "▶" : "⏸",
                    resume ? 0xCC2C873F : 0xCC705A1A,
                    resume ? 0xFF74E88C : 0xFFE7C46A);
            drawWorkflowButton(canvas, WorkflowWindowLayout.deleteX(x), y, "✖",
                    0xAA4A2A2A, 0xFFC07070);
            y += WorkflowWindowLayout.ROW_H;
        }
    }

    private void drawWorkflowButton(BufferedImageUiCanvas canvas, int x, int y,
                                    String label, int bg, int border) {
        UiMainlinePreviewStyle.frame(canvas,
                new UiRect(x, y, WorkflowWindowLayout.BUTTON_W, WorkflowWindowLayout.ROW_H),
                bg, border, 0xFF0D1117);
        canvas.centeredText(label, x + WorkflowWindowLayout.BUTTON_W / 2.0D,
                y + 13, Color.WHITE);
    }

    private void drawGuide(BufferedImageUiCanvas canvas, UiRect bounds,
                           UiLanguageBundle language, GuideUiState state) {
        drawChrome(canvas, bounds, language.text(GuideUiCatalog.titleKey(state.context)));
        int contentX = (int) bounds.getX() + 1;
        int contentY = (int) bounds.getY() + 20;
        int contentW = (int) bounds.getWidth() - 2;
        int contentH = (int) bounds.getHeight() - 21;
        int tabX = contentX + GuideWindowLayout.CONTENT_PAD;
        int tabY = contentY + GuideWindowLayout.CONTENT_PAD;
        int tabW = GuideWindowLayout.topicTabWidth(state.context == GuideUiContext.BOTTOM);
        GuideUiTopic[] topics = GuideUiCatalog.topics(state.context);
        int visible = GuideWindowLayout.visibleTopicRows(contentH);
        int end = Math.min(topics.length, state.topicScroll + visible);
        for (int index = state.topicScroll; index < end; index++) {
            int y = tabY + (index - state.topicScroll) * 22;
            boolean active = index == state.page;
            UiMainlinePreviewStyle.frame(canvas, new UiRect(tabX, y, tabW, 18),
                    active ? 0xCC355A71 : 0x88303A45,
                    active ? 0xFF8FB4D0 : 0xFF4A5665, 0xFF0D1218);
            if (state.context == GuideUiContext.BOTTOM) {
                canvas.text(canvas.trimToWidth(language.text(topics[index].titleKey), tabW - 8),
                        tabX + 4, y + 13, active ? Color.WHITE : UiMainlinePreviewStyle.MUTED);
            } else {
                drawGuideIcon(canvas, topics[index], tabX + 10, y + 9);
            }
        }
        drawGuideScrollbar(canvas, tabX + tabW + 3, tabY,
                GuideWindowLayout.topicAreaHeight(contentH), state.topicScroll,
                topics.length, visible);
        int textX = contentX + tabW + 18;
        int titleY = contentY + 10;
        int maxTextW = GuideWindowLayout.textMaxWidth(contentW, tabW);
        GuideUiTopic topic = topics[state.page];
        canvas.text(canvas.trimToWidth(language.text(topic.titleKey), maxTextW),
                textX, titleY + 9, UiMainlinePreviewStyle.color(0xFFE7C46A));
        int bodyY = titleY + 16;
        int line = 0;
        for (String key : topic.lineKeys) {
            for (String wrapped : wrap(canvas, language.text(key), maxTextW)) {
                if (line++ < state.textScroll) continue;
                int visibleLine = line - state.textScroll - 1;
                if (visibleLine >= GuideWindowLayout.visibleTextLines(contentH)) return;
                canvas.text(wrapped, textX, bodyY + visibleLine * 12 + 9,
                        UiMainlinePreviewStyle.color(0xFFE6EDF8));
            }
        }
    }

    private void drawGuideScrollbar(BufferedImageUiCanvas canvas, int x, int y, int h,
                                    int scroll, int total, int visible) {
        if (total <= visible || h <= 0) return;
        int knobH = Math.max(10, h * visible / Math.max(visible + 1, total));
        int maxScroll = Math.max(1, total - visible);
        int knobY = y + (h - knobH) * Math.max(0, Math.min(maxScroll, scroll)) / maxScroll;
        canvas.fill(new UiRect(x, y, 3, h), UiMainlinePreviewStyle.color(0x55303A45));
        canvas.fill(new UiRect(x, knobY, 3, knobH), UiMainlinePreviewStyle.color(0xCC8FB4D0));
    }

    private void drawGuideIcon(BufferedImageUiCanvas canvas, GuideUiTopic topic, int cx, int cy) {
        String texture = null;
        switch (topic.icon) {
            case HAND: texture = "mode_interact"; break;
            case LINK: texture = "mode_link"; break;
            case FUNNEL: texture = "mode_funnel"; break;
            case ROTATE: texture = "mode_rotate"; break;
            case BUILD: texture = "quick_build"; break;
            case PICKAXE: texture = "ultimine"; break;
            case GRID: texture = "chunk_view"; break;
            default: break;
        }
        if (texture != null) {
            canvas.image(assets.topBar(texture, "active"), new UiRect(cx - 7, cy - 7, 14, 14));
        } else {
            canvas.fill(new UiRect(cx - 6, cy - 5, 12, 10),
                    UiMainlinePreviewStyle.color(0xFFB9C7D5));
            canvas.fill(new UiRect(cx - 3, cy - 2, 6, 4),
                    UiMainlinePreviewStyle.color(0xFF1B222C));
        }
    }

    private void drawFunnelBuffer(BufferedImageUiCanvas canvas, UiPreviewLayout layout,
                                  UiRect bounds, FunnelUiState state) {
        int toggleX = FunnelBufferLayout.toggleX((int) layout.screen().getWidth());
        int toggleY = FunnelBufferLayout.toggleY((int) layout.topBar().getHeight());
        canvas.fill(new UiRect(toggleX, toggleY, FunnelBufferLayout.TOGGLE_W,
                FunnelBufferLayout.TOGGLE_H), UiMainlinePreviewStyle.color(
                state.panelVisible ? 0xAA2C4E3D : 0xAA2A2D36));
        canvas.centeredText("BUFFER", toggleX + FunnelBufferLayout.TOGGLE_W / 2.0D,
                toggleY + 12, Color.WHITE);
        if (!state.panelVisible) return;
        canvas.fill(bounds, UiMainlinePreviewStyle.color(0xAA17191F));
        canvas.text("Funnel Buffer", bounds.getX() + 6, bounds.getY() + 13, Color.WHITE);
        int listY = (int) bounds.getY() + 16;
        for (int index = 0; index < state.visibleEntries.size(); index++) {
            FunnelUiEntry entry = state.visibleEntries.get(index);
            int rowX = (int) bounds.getX() + 4;
            int rowY = listY + index * FunnelBufferLayout.ROW_H;
            int rowW = FunnelBufferLayout.PANEL_W - 8;
            canvas.fill(new UiRect(rowX, rowY, rowW, FunnelBufferLayout.ROW_H - 2),
                    UiMainlinePreviewStyle.color(0x88303845));
            canvas.fill(new UiRect(rowX + 2, rowY + 2, 18, 18),
                    UiMainlinePreviewStyle.color(0xAA1E222A));
            String item = entry.itemId.substring(entry.itemId.indexOf(':') + 1);
            canvas.image(assets.item(item), new UiRect(rowX + 3, rowY + 3, 16, 16));
            canvas.text(canvas.trimToWidth(entry.label, rowW - 30), rowX + 24, rowY + 12,
                    Color.WHITE);
            canvas.text("x" + compact(entry.count), rowX + 24, rowY + 21,
                    UiMainlinePreviewStyle.color(0xFFFFDFAE));
            if (entry.sourceIndex == state.hoveredSourceIndex) {
                canvas.fill(new UiRect(rowX, rowY, rowW, FunnelBufferLayout.ROW_H - 2),
                        UiMainlinePreviewStyle.color(0x33FFFFFF));
            }
        }
        if (state.totalEntries == 0) {
            canvas.text("empty", bounds.getX() + 6, bounds.getY() + 29,
                    UiMainlinePreviewStyle.MUTED);
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
                l.x + 22, l.y + 10, UiMainlinePreviewStyle.color(0xFFE4ECF6));
        CraftQuantityOption selected = state.selected();
        canvas.text("Each craft: x" + (selected == null ? 1 : selected.resultCount),
                l.x + 22, l.y + 22, UiMainlinePreviewStyle.color(0xFFAFC0D3));
        canvas.text("Recipes", l.x, l.optionsY - 1, UiMainlinePreviewStyle.color(0xFFD8E3EE));
        UiMainlinePreviewStyle.frame(canvas, new UiRect(l.x, l.optionsY, l.optionsW, l.optionsH),
                0xAA202833, 0xFF61758A, 0xFF11161C);
        int visible = CraftQuantityWindowLayout.visibleOptionRows(l);
        for (int row = 0; row < visible; row++) {
            int optionIndex = state.scroll + row;
            if (optionIndex >= state.options.size()) break;
            CraftQuantityOption option = state.options.get(optionIndex);
            int rowY = l.optionsY + 2 + row * CraftQuantityWindowLayout.OPTION_ROW_H;
            int fill = option.craftable ? 0xAA223B2E : 0xAA402626;
            if (optionIndex == state.selectedIndex) fill = option.craftable ? 0xCC2E5B43 : 0xCC684040;
            canvas.fill(new UiRect(l.x + 2, rowY, l.optionsW - 4,
                    CraftQuantityWindowLayout.OPTION_ROW_H - 1), UiMainlinePreviewStyle.color(fill));
            canvas.text(canvas.trimToWidth("x" + option.resultCount + " "
                            + (option.summary.isEmpty() ? "Recipe" : option.summary), l.optionsW - 56),
                    l.x + 6, rowY + 12, Color.WHITE);
            canvas.text(option.craftable ? "MAKE" : "MISS",
                    l.x + l.optionsW - 30, rowY + 12,
                    UiMainlinePreviewStyle.color(option.craftable ? 0xFFC9F0C7 : 0xFFF0C4C4));
        }
        String detail = selected == null ? "No recipe" : selected.craftable
                ? (selected.summary.isEmpty() ? "Recipe" : selected.summary)
                : (selected.missingSummary.isEmpty() ? "Missing ingredients." : selected.missingSummary);
        canvas.text(canvas.trimToWidth(detail, l.w), l.x, l.detailY + 9,
                UiMainlinePreviewStyle.color(selected != null && !selected.craftable
                        ? 0xFFD6AAAA : 0xFFBCD0E2));
        drawCraftButton(canvas, l.minusTenX, l.inputY, CraftQuantityWindowLayout.STEP_W,
                CraftQuantityWindowLayout.STEP_H, "-10", 0xAA2A3340);
        drawCraftButton(canvas, l.minusOneX, l.inputY, CraftQuantityWindowLayout.STEP_W,
                CraftQuantityWindowLayout.STEP_H, "-1", 0xAA2A3340);
        UiMainlinePreviewStyle.frame(canvas, new UiRect(l.inputX, l.inputY,
                        CraftQuantityWindowLayout.INPUT_W, CraftQuantityWindowLayout.INPUT_H),
                0xFF202833, 0xFF61758A, 0xFF11161C);
        canvas.centeredText(Integer.toString(state.quantity),
                l.inputX + CraftQuantityWindowLayout.INPUT_W / 2.0D, l.inputY + 11, Color.WHITE);
        drawCraftButton(canvas, l.plusOneX, l.inputY, CraftQuantityWindowLayout.STEP_W,
                CraftQuantityWindowLayout.STEP_H, "+1", 0xAA2A3340);
        drawCraftButton(canvas, l.plusTenX, l.inputY, CraftQuantityWindowLayout.STEP_W,
                CraftQuantityWindowLayout.STEP_H, "+10", 0xAA2A3340);
        canvas.text("Enter confirm, Esc cancel", l.x, l.helpY + 9, UiMainlinePreviewStyle.MUTED);
        drawCraftButton(canvas, l.cancelX, l.actionY, CraftQuantityWindowLayout.ACTION_W,
                CraftQuantityWindowLayout.ACTION_H, "Cancel", 0xAA473030);
        drawCraftButton(canvas, l.confirmX, l.actionY, CraftQuantityWindowLayout.ACTION_W,
                CraftQuantityWindowLayout.ACTION_H, "Craft", 0xAA345A38);
    }

    private void drawCraftButton(BufferedImageUiCanvas canvas, int x, int y, int w, int h,
                                 String label, int fill) {
        UiMainlinePreviewStyle.frame(canvas, new UiRect(x, y, w, h),
                fill, 0xFF667D95, 0xFF111821);
        canvas.centeredText(label, x + w / 2.0D, y + Math.max(11, h - 4), Color.WHITE);
    }

    private static String compact(long count) {
        if (count >= 1_000_000L) return (count / 1_000_000L) + "M";
        if (count >= 1_000L) return (count / 1_000L) + "K";
        return Long.toString(count);
    }

    private void drawStorageLinks(BufferedImageUiCanvas canvas,UiRect bounds,
                                  UiLanguageBundle language,StorageUiState state){
        drawChrome(canvas,bounds,language.text("screen.rtsbuilding.storage_links.title"));
        int contentX=(int)bounds.getX()+1,contentY=(int)bounds.getY()+20;
        int x=StorageWindowLayout.left(contentX),y=StorageWindowLayout.top(contentY);
        int w=StorageWindowLayout.innerWidth((int)bounds.getWidth()-2);
        canvas.text(language.text("screen.rtsbuilding.storage_links.header"),x,y+9,UiMainlinePreviewStyle.TEXT);
        if(state.status!=StorageUiStatus.READY){
            String label=state.status==StorageUiStatus.LOADING?language.text("screen.rtsbuilding.storage_links.loading")
                    :state.status==StorageUiStatus.FAILED?language.text(state.errorMessage):language.text("screen.rtsbuilding.storage_links.empty");
            canvas.text(canvas.trimToWidth(label,w),x,y+StorageWindowLayout.HEADER_H+21,
                    state.status==StorageUiStatus.FAILED?UiMainlinePreviewStyle.color(0xFFFF9AA8):UiMainlinePreviewStyle.color(0xFFFFD480));
            if(state.status==StorageUiStatus.EMPTY)canvas.text(canvas.trimToWidth(language.text("screen.rtsbuilding.storage_links.empty_detail"),w),x,y+StorageWindowLayout.HEADER_H+33,UiMainlinePreviewStyle.MUTED);
            return;
        }
        int rowW=StorageWindowLayout.rowWidth(w,state.hasScrollbar());
        canvas.text(language.text("screen.rtsbuilding.storage_links.priority"),StorageWindowLayout.priorityX(x,rowW),y+12,UiMainlinePreviewStyle.MUTED);
        canvas.text(language.text("screen.rtsbuilding.storage_links.mode_extract_header"),StorageWindowLayout.extractX(x,rowW),y+12,UiMainlinePreviewStyle.MUTED);
        int firstY=StorageWindowLayout.firstRowY(contentY);
        for(int i=0;i<state.visibleEntries.size();i++){
            StorageUiEntry entry=state.visibleEntries.get(i);int rowY=firstY+i*StorageWindowLayout.ROW_H;
            UiMainlinePreviewStyle.frame(canvas,new UiRect(x,rowY,rowW,StorageWindowLayout.ROW_H-2),0xAA1A222D,0xFF566D83,0xFF0D1117);
            String item=entry.itemId.substring(entry.itemId.indexOf(':')+1);canvas.image(assets.item(item),new UiRect(x+5,rowY+5,16,16));
            int priorityX=StorageWindowLayout.priorityX(x,rowW),controlY=StorageWindowLayout.controlY(rowY);
            canvas.text(canvas.trimToWidth(entry.label,Math.max(30,priorityX-(x+26)-6)),x+26,rowY+11,UiMainlinePreviewStyle.WHITE);
            canvas.text(entry.position,x+26,rowY+22,UiMainlinePreviewStyle.MUTED);
            UiMainlinePreviewStyle.frame(canvas,new UiRect(priorityX,controlY,StorageWindowLayout.PRIORITY_W,StorageWindowLayout.CONTROL_H),0xAA101820,0xFF566D83,0xFF0D1117);
            canvas.text(Integer.toString(entry.priority),priorityX+4,controlY+11,UiMainlinePreviewStyle.WHITE);
            int extractX=StorageWindowLayout.extractX(x,rowW);UiMainlinePreviewStyle.frame(canvas,new UiRect(extractX,controlY,StorageWindowLayout.EXTRACT_W,StorageWindowLayout.CONTROL_H),entry.extractOnly?0xFF4A253F:0xAA1A222D,entry.extractOnly?0xFFFF74C9:0xFF566D83,0xFF0D1117);
            canvas.centeredText(language.text(entry.extractOnly?"screen.rtsbuilding.storage_links.mode_yes":"screen.rtsbuilding.storage_links.mode_no"),extractX+StorageWindowLayout.EXTRACT_W/2.0D,controlY+11,UiMainlinePreviewStyle.WHITE);
            int unlinkX=StorageWindowLayout.unlinkX(x,rowW);UiMainlinePreviewStyle.frame(canvas,new UiRect(unlinkX,controlY,StorageWindowLayout.UNLINK_W,StorageWindowLayout.UNLINK_H),0xAA2A2228,0xFF7B5660,0xFF180B0E);
            canvas.centeredText(language.text("screen.rtsbuilding.storage_links.unlink"),unlinkX+StorageWindowLayout.UNLINK_W/2.0D,controlY+11,UiMainlinePreviewStyle.WHITE);
        }
        if(state.hasScrollbar()){
            int barX=x+rowW+StorageWindowLayout.SCROLLBAR_GAP,barH=state.visibleRowCapacity*StorageWindowLayout.ROW_H;
            canvas.fill(new UiRect(barX,firstY,StorageWindowLayout.SCROLLBAR_W,barH),UiMainlinePreviewStyle.color(0xAA101820));
            int thumbH=Math.max(14,barH*state.visibleRowCapacity/Math.max(1,state.totalRows));
            int thumbY=firstY+(barH-thumbH)*state.scroll/Math.max(1,state.maxScroll());
            canvas.fill(new UiRect(barX+1,thumbY,StorageWindowLayout.SCROLLBAR_W-2,thumbH),UiMainlinePreviewStyle.color(0xFF8EA9C4));
        }
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
                UiMainlinePreviewStyle.color(0xFF8EC8FF));
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
        UiMainlinePreviewStyle.frame(canvas, new UiRect(buttonX,
                        CullingWindowLayout.buttonTop(CullingWindowLayout.deleteButtonRowY(contentY)),
                        CullingWindowLayout.DELETE_BUTTON_WIDTH, CullingWindowLayout.buttonHeight()),
                0xFF742833, 0xFFFFA2AE, 0xFF0B1017);
        canvas.centeredText(language.text("screen.rtsbuilding.culling.delete_button"),
                buttonX + CullingWindowLayout.DELETE_BUTTON_WIDTH / 2.0D,
                CullingWindowLayout.buttonTextY(CullingWindowLayout.deleteButtonRowY(contentY)) + 9,
                UiMainlinePreviewStyle.WHITE);
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
                        Math.max(1, contentH - 4)), UiMainlinePreviewStyle.color(0x88313A46));
                int totalH = settingsLayout.contentHeight + SettingsWindowLayout.CONTENT_TOP_PADDING;
                int thumbH = Math.max(18, (int) Math.round(trackH
                        * (trackH / (double) Math.max(trackH, totalH))));
                int thumbY = contentY + (int) Math.round((trackH - thumbH)
                        * (state.scroll / (double) maxScroll));
                canvas.fill(new UiRect(contentX + contentW - 8, thumbY, 4, thumbH),
                        UiMainlinePreviewStyle.color(0xCC8AA0B8));
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
        UiMainlinePreviewStyle.frame(canvas, new UiRect(x + 8, y, width - 16,
                        SettingsWindowLayout.SECTION_HEADER_H),
                0xCC202A35, 0xFF596D82, 0xFF0B1016);
        canvas.text(expanded ? "v" : ">", x + 16, y + 15, UiMainlinePreviewStyle.WHITE);
        canvas.text(canvas.trimToWidth(label, width - 58), x + 31, y + 15, Color.WHITE);
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
                        x + 16, y + 18, UiMainlinePreviewStyle.TEXT);
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
                x + 16, y + 14, UiMainlinePreviewStyle.TEXT);
        canvas.text(row.valueLabel, x + width - 60, y + 14, UiMainlinePreviewStyle.WHITE);
        int trackX = x + 16;
        int trackY = y + 24;
        int trackW = width - 32;
        canvas.fill(new UiRect(trackX, trackY, trackW, 4), UiMainlinePreviewStyle.color(0xFF07090D));
        canvas.fill(new UiRect(trackX + 1, trackY + 1, Math.max(0, trackW - 2), 2),
                UiMainlinePreviewStyle.color(0xFF313946));
        int knobX = trackX + (int) Math.round(row.valueIndex
                / (double) Math.max(1, row.valueCount - 1) * trackW);
        canvas.fill(new UiRect(knobX - 3, trackY - 5, 7, 13),
                UiMainlinePreviewStyle.color(0xFF5FE36C));
    }

    private void drawCoreStep(BufferedImageUiCanvas canvas, SettingsUiRow row,
                              int x, int y, int width, UiLanguageBundle language) {
        boolean sound = row.id == SettingsId.BLOCK_SOUNDS_PER_TICK;
        int buttonY = y + (sound ? 8 : 6);
        canvas.text(canvas.trimToWidth(language.text(row.id.labelKey), width - 156),
                x + 16, y + (sound ? 12 : 17), UiMainlinePreviewStyle.TEXT);
        if (sound) {
            canvas.text(canvas.trimToWidth(language.text(row.id.hintKey), width - 156),
                    x + 16, y + 27, UiMainlinePreviewStyle.MUTED);
        }
        int minusX = x + width - 124;
        drawCoreStepButton(canvas, minusX, buttonY, "-");
        UiMainlinePreviewStyle.frame(canvas, new UiRect(minusX + 26, buttonY, 56, 22),
                0xCC1A232E, 0xFF566B80, 0xFF0D1218);
        canvas.centeredText(row.valueLabel, minusX + 54, buttonY + 15,
                UiMainlinePreviewStyle.WHITE);
        drawCoreStepButton(canvas, minusX + 86, buttonY, "+");
    }

    private void drawCoreStepButton(BufferedImageUiCanvas canvas, int x, int y, String label) {
        UiMainlinePreviewStyle.frame(canvas, new UiRect(x, y, 22, 22),
                0xCC26303D, 0xFF6A8299, 0xFF0E1116);
        canvas.centeredText(label, x + 11, y + 15, UiMainlinePreviewStyle.WHITE);
    }

    private void drawCoreHintToggle(BufferedImageUiCanvas canvas, SettingsUiRow row,
                                    int x, int y, int width, UiLanguageBundle language) {
        canvas.text(canvas.trimToWidth(language.text(row.id.labelKey), width - 116),
                x + 16, y + 11, UiMainlinePreviewStyle.TEXT);
        int hintX = x + 16 + (row.hintExpandable
                ? SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE + 4 : 0);
        int hintW = Math.max(24, x + width - 92 - hintX - 8);
        if (row.hintExpandable) {
            UiMainlinePreviewStyle.frame(canvas,
                    new UiRect(x + 16, y + 12, SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE,
                            SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE),
                    0xAA26303D, 0xFF6A8299, 0xFF0E1116);
            canvas.centeredText(row.hintExpanded ? "v" : ">",
                    x + 16 + SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE / 2.0D,
                    y + 21, UiMainlinePreviewStyle.WHITE);
        }
        List<String> lines = row.hintExpanded
                ? wrap(canvas, language.text(row.id.hintKey), hintW)
                : java.util.Collections.singletonList(
                canvas.trimToWidth(language.text(row.id.hintKey), hintW));
        for (int i = 0; i < lines.size(); i++) {
            canvas.text(lines.get(i), hintX, y + 22 + i * SettingsWindowLayout.HINT_LINE_H,
                    UiMainlinePreviewStyle.MUTED);
        }
        drawCoreToggleButton(canvas, x + width - 92, y + 4, row.active, language);
    }

    private void drawCoreToggleButton(BufferedImageUiCanvas canvas, int x, int y,
                                      boolean active, UiLanguageBundle language) {
        UiMainlinePreviewStyle.frame(canvas, new UiRect(x, y, 76, 22),
                active ? 0xDD329A42 : 0xDD28313C,
                active ? 0xFF8EF19A : 0xFF68788A, 0xFF10151B);
        canvas.fill(new UiRect(active ? x + 50 : x + 6, y + 4, 18, 14),
                UiMainlinePreviewStyle.color(active ? 0xFF72F07A : 0xFF788696));
        canvas.centeredText(language.text(active ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"),
                x + 38, y + 15, Color.WHITE);
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
                bounds.getHeight() - 30), UiMainlinePreviewStyle.color(0x88313A46));
        canvas.fill(new UiRect(bounds.right() - 8, bounds.getY() + 96, 4, 30),
                UiMainlinePreviewStyle.color(0xCC8AA0B8));
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
        UiMainlinePreviewStyle.frame(canvas, new UiRect(x, y, 80, 22),
                active ? 0xDD329A42 : 0xDD28313C,
                active ? 0xFF8EF19A : 0xFF68788A, 0xFF10151B);
        canvas.fill(new UiRect(active ? x + 54 : x + 6, y + 4, 18, 14),
                UiMainlinePreviewStyle.color(active ? 0xFF72F07A : 0xFF788696));
        canvas.centeredText(language.text(active ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"),
                x + 40, y + 15, Color.WHITE);
    }

    private int section(BufferedImageUiCanvas canvas, int x, int y, int w,
                        String label, boolean expanded) {
        UiMainlinePreviewStyle.frame(canvas, new UiRect(x, y, w, 22),
                0xCC202A35, 0xFF596D82, 0xFF0B1016);
        canvas.text(expanded ? "v" : ">", x + 8, y + 15,
                UiMainlinePreviewStyle.WHITE);
        canvas.text(canvas.trimToWidth(label, w - 34), x + 23, y + 15, Color.WHITE);
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
        UiMainlinePreviewStyle.frame(canvas, new UiRect(buttonX, y + 4, 76, 22),
                active ? 0xDD329A42 : 0xDD28313C,
                active ? 0xFF8EF19A : 0xFF68788A, 0xFF10151B);
        int switchX = active ? buttonX + 50 : buttonX + 6;
        canvas.fill(new UiRect(switchX, y + 8, 18, 14),
                UiMainlinePreviewStyle.color(active ? 0xFF72F07A : 0xFF788696));
        canvas.centeredText(language.text(active ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"),
                buttonX + 38, y + 19, Color.WHITE);
        return y + (hint.isEmpty() ? 28 : 34);
    }

    private void drawQuickBuild(BufferedImageUiCanvas canvas, UiRect bounds,
                                UiLanguageBundle language, QuickBuildUiState state) {
        drawChrome(canvas, bounds, language.text("screen.rtsbuilding.quick_build.title"));
        QuickBuildWindowLayout.Geometry g = QuickBuildWindowLayout.geometry(
                (int) bounds.getX(), (int) bounds.getY(), state.mode == QuickBuildUiMode.DESTROY);
        drawQuickMode(canvas, g.buildModeX, g.modeY, g.modeW,
                language.text("screen.rtsbuilding.quick_build.mode_build"),
                state.mode == QuickBuildUiMode.BUILD, true);
        drawQuickMode(canvas, g.destroyModeX, g.modeY, g.modeW,
                language.text("screen.rtsbuilding.quick_build.mode_destroy"),
                state.mode == QuickBuildUiMode.DESTROY, state.destroyEnabled);
        canvas.text(language.text("screen.rtsbuilding.quick_build.shape"),
                bounds.getX() + 10, g.sectionTitleY + 9,
                UiMainlinePreviewStyle.TEXT);
        for (int i = 0; i < state.shapes.size(); i++) {
            QuickBuildUiShapeOption option = state.shapes.get(i);
            int slotX = g.shapeX(i);
            int slotY = g.shapeY(i);
            UiMainlinePreviewStyle.frame(canvas, new UiRect(slotX, slotY, 32, 32),
                    option.selected ? 0xCC355B4C : option.enabled ? 0xAA1B222B : 0xAA111720,
                    option.selected ? 0xFF7CCB93 : option.enabled ? 0xFF667A91 : 0xFF3A4652,
                    0xFF0D1117);
            canvas.image(assets.quickBuild(option.shape.textureName), new UiRect(slotX, slotY, 32, 32));
            if (!option.enabled) canvas.fill(new UiRect(slotX + 1, slotY + 1, 30, 30),
                    UiMainlinePreviewStyle.color(0x66000000));
        }
        canvas.text(language.text("screen.rtsbuilding.quick_build.fill"),
                g.rightX, g.sectionTitleY + 9, UiMainlinePreviewStyle.TEXT);
        if (state.chainMode()) {
            int labelY = g.bodyY + QuickBuildWindowLayout.SECTION_TOP + 17;
            canvas.text(language.text("screen.rtsbuilding.quick_build.chain_limit_label"),
                    g.rightX, labelY + 9, UiMainlinePreviewStyle.TEXT);
            int sliderW = Math.max(50, QuickBuildWindowLayout.WINDOW_W
                    - QuickBuildWindowLayout.RIGHT_COL_X - 40);
            int sliderY = labelY + 14;
            UiMainlinePreviewStyle.frame(canvas, new UiRect(g.rightX, sliderY, sliderW, 18),
                    0xAA1E2731, 0xFF5E738A, 0xFF111921);
            int fill = (state.chainLimit - state.chainMinimum) * (sliderW - 4)
                    / Math.max(1, state.chainMaximum - state.chainMinimum);
            canvas.fill(new UiRect(g.rightX + 2, sliderY + 7, fill, 4),
                    UiMainlinePreviewStyle.color(0xFF67D47B));
            canvas.text(Integer.toString(state.chainLimit), g.rightX + sliderW + 6,
                    sliderY + 13, Color.WHITE);
        } else {
            int row = 0;
            for (QuickBuildUiControl control : state.controls) {
                int controlY = g.controlY(row++);
                drawQuickControl(canvas, g.rightX, controlY,
                        QuickBuildWindowLayout.CONTROL_W, control.label,
                        control.selected, control.enabled);
            }
        }

        canvas.fill(new UiRect(bounds.getX() + 6, g.dividerY - 1,
                bounds.getWidth() - 12, 1), UiMainlinePreviewStyle.color(0xFF647B92));
        int progressW = QuickBuildWindowLayout.WINDOW_W - 16;
        canvas.fill(new UiRect(bounds.getX() + 8, g.dividerY + 4, progressW, 4),
                UiMainlinePreviewStyle.color(0xFF0B1118));
        if (state.progressCompleted >= 0 && state.progressTotal > 0) {
            canvas.fill(new UiRect(bounds.getX() + 8, g.dividerY + 4,
                    progressW * state.progressCompleted / state.progressTotal, 4),
                    UiMainlinePreviewStyle.color(0xFFFF8EAD));
        }
        int textY = g.dividerY + 21;
        if (state.mode == QuickBuildUiMode.DESTROY && state.progressCompleted >= 0) {
            canvas.text(canvas.trimToWidth(state.progressText + "  "
                    + language.format("screen.rtsbuilding.quick_build.destroy_remaining", state.remainingBlocks),
                    QuickBuildWindowLayout.WINDOW_W - 16), bounds.getX() + 8, textY,
                    UiMainlinePreviewStyle.color(0xFFB8FFB8));
        } else if (state.mode == QuickBuildUiMode.DESTROY) {
            canvas.text(canvas.trimToWidth(language.format(state.hintKey, state.confirmKeyLabel),
                    QuickBuildWindowLayout.WINDOW_W - 16), bounds.getX() + 8, textY,
                    UiMainlinePreviewStyle.color(0xFFFFB8B8));
        } else {
            canvas.text("x " + state.costText, bounds.getX() + 8, textY,
                    UiMainlinePreviewStyle.color(0xFFB8FFB8));
            if (state.missingBlocks > 0) canvas.text(language.format(
                    "screen.rtsbuilding.quick_build.missing_blocks", state.missingBlocks),
                    bounds.getX() + 64, textY, UiMainlinePreviewStyle.color(0xFFFFB8B8));
        }
        canvas.text(canvas.trimToWidth(language.format(
                        "screen.rtsbuilding.quick_build.dimensions", state.dimensions),
                QuickBuildWindowLayout.WINDOW_W - 16), bounds.getX() + 8, textY + 14,
                UiMainlinePreviewStyle.TEXT);
    }

    private void drawQuickMode(BufferedImageUiCanvas canvas, int x, int y, int w,
                               String label, boolean selected, boolean enabled) {
        UiMainlinePreviewStyle.frame(canvas, new UiRect(x, y, w, QuickBuildWindowLayout.MODE_H),
                !enabled ? 0xFF111720 : selected ? 0xFF29583E : 0xFF141C26,
                !enabled ? 0xFF3A4652 : selected ? 0xFF5FE36C : 0xFF647B92,
                0xFF0D1117);
        canvas.centeredText(canvas.trimToWidth(label, w - 4), x + w / 2.0D,
                y + 13, enabled ? Color.WHITE : UiMainlinePreviewStyle.MUTED);
    }

    private void drawQuickControl(BufferedImageUiCanvas canvas, int x, int y, int w,
                                  String label, boolean selected, boolean enabled) {
        UiMainlinePreviewStyle.frame(canvas, new UiRect(x, y, w, QuickBuildWindowLayout.CONTROL_H),
                selected ? 0xCC355B4C : enabled ? 0xAA28313C : 0xAA111720,
                selected ? 0xFF7CCB93 : enabled ? 0xFF63788D : 0xFF3A4652, 0xFF0D1117);
        canvas.centeredText(canvas.trimToWidth(label, w - 22), x + w / 2.0D,
                y + 14, enabled ? Color.WHITE : UiMainlinePreviewStyle.MUTED);
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
        int hintColor = state.mode == BlueprintUiState.Mode.CAPTURE_READY
                || state.mode == BlueprintUiState.Mode.CAPTURE_SAVING
                ? 0xFF8EEA9B : 0xFFFFC06C;
        canvas.text(canvas.trimToWidth(language.text(
                        "screen.rtsbuilding.blueprints.capture_window_hint"), g.width),
                g.x, g.y + 23, UiMainlinePreviewStyle.color(hintColor));
        canvas.text(canvas.trimToWidth(language.text(
                        "screen.rtsbuilding.blueprints.capture_window_scroll_hint"), g.width),
                g.x, g.y + 35, UiMainlinePreviewStyle.MUTED);
        boolean complete = state.mode == BlueprintUiState.Mode.CAPTURE_READY
                || state.mode == BlueprintUiState.Mode.CAPTURE_SAVING;
        if (complete) {
            canvas.text(canvas.trimToWidth(language.format(
                            "screen.rtsbuilding.blueprints.capture_size", size(state.captureSize)), g.width),
                    g.x, g.y + 51, UiMainlinePreviewStyle.color(0xFFB7CDE2));
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
                complete ? 0xFFB7CDE2 : 0xFFFFC06C);
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
                selectorY + 14, Color.WHITE);
        drawWindowButton(canvas, groupX + BlueprintWindowLayout.SMALL_BUTTON_W
                        + BlueprintWindowLayout.CONTROL_GAP + nameW
                        + BlueprintWindowLayout.CONTROL_GAP,
                selectorY, BlueprintWindowLayout.SMALL_BUTTON_W, ">", true, false);
        int sizeW = Math.min(74, Math.max(42, state.blueprintSize.length() * 6 + 6));
        int detailGroupW = sizeW + BlueprintWindowLayout.CONTROL_GAP
                + BlueprintWindowLayout.DETAILS_BUTTON_W;
        int sizeX = selectorX + Math.max(0, (selectorW - detailGroupW) / 2);
        canvas.centeredText(canvas.trimToWidth(state.blueprintSize, sizeW),
                sizeX + sizeW / 2.0D, selectorY + 41, UiMainlinePreviewStyle.MUTED);
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
            canvas.text(axes[i], rowX, rowY + 14, state.isPinned()
                    ? UiMainlinePreviewStyle.MUTED : UiMainlinePreviewStyle.color(0xFF4F5B68));
            drawWindowButton(canvas, rowX + 14, rowY, 18, "-", state.isPinned(), false);
            drawTextField(canvas, rowX + 36, rowY, BlueprintWindowLayout.POSITION_INPUT_W,
                    state.isPinned() ? Integer.toString(values[i]) : "", state.isPinned());
            drawWindowButton(canvas, rowX + 104, rowY, 18, "+", state.isPinned(), false);
        }

        if (state.isPinned()) {
            drawStatus(canvas, g.x, g.statusY, g.width,
                    language.text("screen.rtsbuilding.blueprints.status.ready_to_build")
                            + " · " + language.text("screen.rtsbuilding.blueprints.status.ready_to_build_controls"),
                    0xFF8EEA9B);
        } else {
            drawStatus(canvas, g.x, g.statusY, g.width,
                    language.text("screen.rtsbuilding.blueprints.placement_window_hint"), 0xFFFFE66D);
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
        canvas.fill(new UiRect(x, y, w, h), UiMainlinePreviewStyle.color(0x33111821));
        canvas.fill(new UiRect(x, y, w, 1), UiMainlinePreviewStyle.color(0x55344555));
        canvas.fill(new UiRect(x, y + h - 1, w, 1), UiMainlinePreviewStyle.color(0x550D1117));
    }

    private void drawStatus(BufferedImageUiCanvas canvas, int x, int y, int w,
                            String text, int color) {
        canvas.fill(new UiRect(x, y, w, BlueprintWindowLayout.STATUS_H),
                UiMainlinePreviewStyle.color(0x66111821));
        canvas.fill(new UiRect(x, y, w, 1), UiMainlinePreviewStyle.color(0x44344555));
        canvas.centeredText(canvas.trimToWidth(text, w - 12), x + w / 2.0D,
                y + 21, UiMainlinePreviewStyle.color(color));
    }

    private void drawTextField(BufferedImageUiCanvas canvas, int x, int y, int w,
                               String value, boolean enabled) {
        UiMainlinePreviewStyle.frame(canvas, new UiRect(x, y, w, BlueprintWindowLayout.BUTTON_H),
                enabled ? 0xAA18212B : 0xAA101620, 0xFF596D84, 0xFF0D1117);
        canvas.centeredText(value, x + w / 2.0D, y + 14,
                enabled ? Color.WHITE : UiMainlinePreviewStyle.MUTED);
    }

    private void drawWindowButton(BufferedImageUiCanvas canvas, int x, int y, int w,
                                  String label, boolean enabled, boolean primary) {
        int fill = !enabled ? 0xAA202630 : primary ? 0xCC244E35 : 0xAA28313C;
        int light = !enabled ? 0xFF47515D : primary ? 0xFF7FCEA0 : 0xFF63788D;
        UiMainlinePreviewStyle.frame(canvas, new UiRect(x, y, w, BlueprintWindowLayout.BUTTON_H),
                fill, light, 0xFF0D1117);
        canvas.centeredText(canvas.trimToWidth(label, Math.max(8, w - 10)), x + w / 2.0D,
                y + 14, enabled ? Color.WHITE : UiMainlinePreviewStyle.MUTED);
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
        canvas.text(canvas.trimToWidth(state.materials.blueprintName, w - 20), x + 10, y + 17, Color.WHITE);
        String summary = state.materials.rows.isEmpty()
                ? language.text("screen.rtsbuilding.blueprints.materials_all_ready")
                : language.format("screen.rtsbuilding.blueprints.details_summary",
                        state.materials.percent, state.materials.buildable, state.materials.total,
                        state.materials.missingTypes, state.materials.unsupportedTypes,
                        state.materials.missingBlockTypes);
        canvas.text(canvas.trimToWidth(summary, w - 20), x + 10, y + 30,
                state.materials.allReady() ? UiMainlinePreviewStyle.color(0xFF8EEA9B)
                        : UiMainlinePreviewStyle.color(0xFFFFC06C));
        int listX = x + 10;
        int listY = y + 38;
        int listW = w - 20;
        int listH = Math.max(44, h - 46);
        UiMainlinePreviewStyle.frame(canvas, new UiRect(listX, listY, listW, listH),
                0x99101620, 0xFF415266, 0xFF0B0E13);
        int columns = listW >= 390 ? 2 : 1;
        int cellW = (listW - 8 - (columns - 1) * 6) / columns;
        for (int i = 0; i < state.materials.rows.size(); i++) {
            int row = i / columns;
            int column = i % columns;
            int rowX = listX + 4 + column * (cellW + 6);
            int rowY = listY + 3 + row * 22;
            BlueprintMaterialUiState.Row line = state.materials.rows.get(i);
            UiMainlinePreviewStyle.frame(canvas, new UiRect(rowX + 5, rowY + 3, 16, 16),
                    0xAA36506A, 0xFF58708A, 0xFF0B0E13);
            canvas.centeredText("?", rowX + 13, rowY + 15, UiMainlinePreviewStyle.color(0xFFFFD080));
            int detailW = Math.min(86, Math.max(54, cellW / 3));
            int detailX = rowX + cellW - detailW - 4;
            canvas.text(canvas.trimToWidth(line.label, Math.max(24, detailX - rowX - 28)),
                    rowX + 26, rowY + 11, Color.WHITE);
            canvas.text(canvas.trimToWidth(line.detail, detailW), detailX, rowY + 16,
                    UiMainlinePreviewStyle.color(line.color));
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
                    x + 10, y + 19, UiMainlinePreviewStyle.color(0xFFCDEBFF));
            canvas.text(language.format("screen.rtsbuilding.blueprints.capture_preview_summary",
                            size(state.captureSize), state.captureBlockCount),
                    x + 10, y + 31, UiMainlinePreviewStyle.color(0xFFB8FFB8));
        } else {
            canvas.text(language.format("screen.rtsbuilding.blueprints.name_dialog_current", state.blueprintName),
                    x + 10, y + 19, UiMainlinePreviewStyle.MUTED);
        }
        int inputX = x + 10;
        int inputW = Math.max(80, w - 20);
        int cancelW = 58;
        int confirmW = 70;
        int buttonY = y + h - 24;
        int inputY = Math.max(y + 36, buttonY - 28);
        int cancelX = x + w - cancelW - 10;
        int confirmX = cancelX - confirmW - 6;
        canvas.text(language.text("screen.rtsbuilding.blueprints.name_dialog_label"),
                inputX, inputY - 2, UiMainlinePreviewStyle.color(0xFFB7CDE2));
        UiMainlinePreviewStyle.frame(canvas, new UiRect(inputX, inputY, inputW, 18),
                0xDD05070B, 0xFF8BA4B8, 0xFF0B0E13);
        canvas.text(canvas.trimToWidth(state.nameDraft + "_", inputW - 8),
                inputX + 4, inputY + 13, Color.WHITE);
        drawWindowButton(canvas, confirmX, buttonY, confirmW,
                language.text("screen.rtsbuilding.blueprints.name_dialog_confirm"), true, false);
        drawWindowButton(canvas, cancelX, buttonY, cancelW,
                language.text("screen.rtsbuilding.blueprints.name_dialog_cancel"), true, false);
    }

    private void drawModeButton(BufferedImageUiCanvas canvas, double x, double y,
                                int w, String label, boolean active) {
        UiMainlinePreviewStyle.frame(canvas, new UiRect(x, y, w, 20),
                active ? 0xCC355B4C : 0xAA28313C,
                active ? 0xFF7CCB93 : 0xFF63788D, 0xFF0D1117);
        canvas.centeredText(canvas.trimToWidth(label, w - 8), x + w / 2.0D,
                y + 14, Color.WHITE);
    }

    private void drawChrome(BufferedImageUiCanvas canvas, UiRect bounds, String title) {
        drawChrome(canvas, bounds, title, true);
    }

    private void drawChrome(BufferedImageUiCanvas canvas, UiRect bounds, String title,
                            boolean closable) {
        UiMainlinePreviewStyle.frame(canvas, bounds,
                0xFF161C24, 0xFF6C839A, 0xFF0D1117);
        canvas.fill(new UiRect(bounds.getX() + 1, bounds.getY() + 1,
                bounds.getWidth() - 2, 19), UiMainlinePreviewStyle.color(0xCC233345));
        canvas.text(canvas.trimToWidth(title, (int) bounds.getWidth() - (closable ? 36 : 16)),
                bounds.getX() + 8, bounds.getY() + 14, Color.WHITE);
        if (closable) {
            canvas.imageRegion(assets.closeButton(), new UiRect(0, 0, 450, 450),
                    new UiRect(bounds.right() - 17, bounds.getY() + 3, 14, 14));
        }
    }
}
