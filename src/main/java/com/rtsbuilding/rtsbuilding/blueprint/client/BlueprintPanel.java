package com.rtsbuilding.rtsbuilding.blueprint.client;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintTransform;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.blueprint.format.BlueprintReaders;
import com.rtsbuilding.rtsbuilding.blueprint.format.BlueprintWriters;
import com.rtsbuilding.rtsbuilding.blueprint.network.C2SBlueprintPlacePayload;
import com.rtsbuilding.rtsbuilding.blueprint.network.S2CBlueprintStatusPayload;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintMaterialInspector.*;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelFiles.*;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelLayout.*;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelUi.*;

public final class BlueprintPanel {
    private static final int ROW_H = 24;
    private static final int BUTTON_H = 14;
    private static final int SEARCH_H = 14;
    private static final int DETAIL_BUTTON_H = 14;
    private static final List<BlueprintEntry> ENTRIES = new ArrayList<>();
    private static boolean loaded = false;
    private static int selectedIndex = -1;
    private static int scroll = 0;
    private static boolean searchFocused = false;
    private static boolean materialDialogOpen = false;
    private static int materialDialogScroll = 0;
    private static NameDialogMode nameDialogMode = NameDialogMode.NONE;
    private static String nameDialogValue = "";
    private static BlueprintEntry nameDialogEntry = null;
    private static boolean nameDialogReplaceOnType = false;
    private static long nameDialogCaptureBlockCount = 0L;
    private static int yRotationSteps = 0;
    private static int xRotationSteps = 0;
    private static int zRotationSteps = 0;
    private static BlockPos pinnedAnchor = null;
    private static Direction pinnedNudgeForward = Direction.SOUTH;
    private static final BlueprintCaptureController CAPTURE = new BlueprintCaptureController();
    private static String search = "";
    private static Component statusText = Component.translatable("screen.rtsbuilding.blueprints.status.ready");
    private static int statusColor = 0xFFB8C7D6;

    private BlueprintPanel() {
    }

    public static void render(GuiGraphics g, Font font, ClientRtsController controller,
            int x, int y, int w, int h, int mouseX, int mouseY) {
        if (!Config.areBlueprintsEnabled()) {
            CAPTURE.clearSilently();
            renderDisabled(g, font, x, y, w, h);
            return;
        }
        tickCaptureSaveJob();
        ensureLoaded();

        int buttonY = y;
        TopBarLayout top = topBarLayout(font, x, w, CAPTURE.isActive());
        drawButton(g, font, top.folderX(), buttonY, top.folderW(), BUTTON_H, text("screen.rtsbuilding.blueprints.open_folder_short"),
                inside(mouseX, mouseY, top.folderX(), buttonY, top.folderW(), BUTTON_H));
        drawButton(g, font, top.importX(), buttonY, top.importW(), BUTTON_H, text("screen.rtsbuilding.blueprints.import_file_short"),
                inside(mouseX, mouseY, top.importX(), buttonY, top.importW(), BUTTON_H));
        drawButton(g, font, top.syncCreateX(), buttonY, top.syncCreateW(), BUTTON_H,
                text("screen.rtsbuilding.blueprints.sync_create_short"),
                inside(mouseX, mouseY, top.syncCreateX(), buttonY, top.syncCreateW(), BUTTON_H));
        drawButton(g, font, top.captureX(), buttonY, top.captureW(), BUTTON_H,
                text(CAPTURE.isActive() ? "screen.rtsbuilding.blueprints.capture_active_short" : "screen.rtsbuilding.blueprints.capture_short"),
                inside(mouseX, mouseY, top.captureX(), buttonY, top.captureW(), BUTTON_H));

        drawFrame(g, top.searchX(), buttonY, top.searchW(), SEARCH_H, searchFocused ? 0xCC09111B : 0xAA111820, 0xFF6B8095, 0xFF0C1118);
        String searchLabel = search.isBlank() && !searchFocused
                ? text("screen.rtsbuilding.blueprints.search")
                : search + (searchFocused && (Util.getMillis() / 500L) % 2L == 0L ? "_" : "");
        g.drawString(font, trim(font, searchLabel, top.searchW() - 8), top.searchX() + 4, buttonY + 3,
                search.isBlank() && !searchFocused ? 0x8898A8B8 : 0xFFEAF2FF, false);

        int listY = y + 19;
        int statusY = y + h - 13;
        int listH = Math.max(24, statusY - listY - 4);
        if (CAPTURE.isActive()) {
            renderCaptureLockedBottom(g, font, x, listY, w, listH);
            return;
        }
        int detailsW = Math.min(210, Math.max(148, w / 4));
        int listW = Math.max(120, w - detailsW - 8);
        renderList(g, font, controller, x, listY, listW, listH, mouseX, mouseY);
        renderDetails(g, font, controller, x + listW + 8, listY, detailsW, listH, mouseX, mouseY);
        g.drawString(font, trim(font, statusText.getString(), w - 8), x + 2, statusY, statusColor, false);
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int x, int y, int w, int h) {
        if (!Config.areBlueprintsEnabled()) {
            searchFocused = false;
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.disabled", "");
            return true;
        }
        ensureLoaded();
        TopBarLayout top = topBarLayout(Minecraft.getInstance().font, x, w, CAPTURE.isActive());
        if (inside(mouseX, mouseY, top.folderX(), y, top.folderW(), BUTTON_H)) {
            openBlueprintFolder();
            return true;
        }
        if (inside(mouseX, mouseY, top.importX(), y, top.importW(), BUTTON_H)) {
            importBlueprintFile();
            return true;
        }
        if (inside(mouseX, mouseY, top.syncCreateX(), y, top.syncCreateW(), BUTTON_H)) {
            syncOtherModBlueprints();
            return true;
        }
        if (inside(mouseX, mouseY, top.captureX(), y, top.captureW(), BUTTON_H)) {
            toggleCaptureMode();
            return true;
        }
        if (CAPTURE.isActive()) {
            searchFocused = false;
            setStatus(S2CBlueprintStatusPayload.INFO,
                    !CAPTURE.isSaving()
                            ? "screen.rtsbuilding.blueprints.status.capture_locked"
                            : "screen.rtsbuilding.blueprints.status.save_busy",
                    "");
            return true;
        }

        searchFocused = inside(mouseX, mouseY, top.searchX(), y, top.searchW(), SEARCH_H);
        if (searchFocused) {
            return true;
        }

        int listY = y + 19;
        int statusY = y + h - 13;
        int listH = Math.max(24, statusY - listY - 4);
        int detailsW = Math.min(210, Math.max(148, w / 4));
        int listW = Math.max(120, w - detailsW - 8);
        if (inside(mouseX, mouseY, x, listY, listW, listH)) {
            List<BlueprintEntry> filtered = filteredEntries();
            int columns = listColumns(listW);
            int visibleRows = Math.max(1, listH / ROW_H);
            scroll = Mth.clamp(scroll, 0, maxListScroll(filtered.size(), columns, visibleRows));
            int row = ((int) mouseY - listY) / ROW_H;
            int cellW = listCellWidth(listW, columns);
            int col = Math.min(columns - 1, Math.max(0, ((int) mouseX - x - 1) / Math.max(1, cellW + LIST_COLUMN_GAP)));
            int index = (scroll + row) * columns + col;
            if (index >= 0 && index < filtered.size()) {
                BlueprintEntry entry = filtered.get(index);
                Font font = Minecraft.getInstance().font;
                int cellX = x + 1 + col * (cellW + LIST_COLUMN_GAP);
                RowActionLayout actions = rowActionLayout(font, cellX, listY + row * ROW_H, cellW);
                if (entry.error().isBlank()
                        && inside(mouseX, mouseY, actions.saveX(), actions.buttonY(), actions.saveW(), DETAIL_BUTTON_H)) {
                    saveEntryAs(entry);
                    return true;
                }
                if (entry.error().isBlank()
                        && inside(mouseX, mouseY, actions.renameX(), actions.buttonY(), actions.renameW(), DETAIL_BUTTON_H)) {
                    openRenameDialog(entry);
                    return true;
                }
                if (inside(mouseX, mouseY, actions.deleteX(), actions.buttonY(), actions.deleteW(), DETAIL_BUTTON_H)) {
                    deleteEntry(entry);
                    return true;
                }
                selectEntry(entry);
            }
            return true;
        }
        if (inside(mouseX, mouseY, x + listW + 8, listY, detailsW, listH)) {
            return handleDetailsClick(mouseX, mouseY, x + listW + 8, listY, detailsW, listH);
        }
        return false;
    }

    public static boolean isMaterialDialogOpen() {
        return materialDialogOpen;
    }

    public static boolean isNameDialogOpen() {
        return nameDialogMode != NameDialogMode.NONE;
    }

    static boolean isNameDialogCaptureMode() {
        return nameDialogMode == NameDialogMode.CAPTURE_SAVE;
    }

    static String nameDialogValue() {
        return nameDialogValue;
    }

    static BlueprintEntry nameDialogEntry() {
        return nameDialogEntry;
    }

    static BlockPos nameDialogCapturePointA() {
        return CAPTURE.pointA();
    }

    static BlockPos nameDialogCapturePointB() {
        return CAPTURE.previewPointB();
    }

    static long nameDialogCaptureBlockCount() {
        return nameDialogCaptureBlockCount;
    }

    static void confirmActiveNameDialog() {
        confirmNameDialog();
    }

    static void cancelActiveNameDialog() {
        cancelNameDialog();
    }

    static BlueprintEntry materialDialogEntry() {
        return selectedEntry();
    }

    static int materialDialogScroll() {
        return materialDialogScroll;
    }

    static void setMaterialDialogScroll(int scroll) {
        materialDialogScroll = Math.max(0, scroll);
    }

    static void closeMaterialDialog() {
        materialDialogOpen = false;
        materialDialogScroll = 0;
    }

    public static void renderNameDialog(GuiGraphics g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        if (!isNameDialogOpen()) {
            return;
        }
        boolean capture = nameDialogMode == NameDialogMode.CAPTURE_SAVE;
        BlueprintNameDialog.render(g, font, screenW, screenH, mouseX, mouseY, capture, nameDialogValue, nameDialogEntry,
                CAPTURE.pointA(), CAPTURE.previewPointB(), nameDialogCaptureBlockCount);
    }

    public static boolean mouseClickedNameDialog(double mouseX, double mouseY, int button, int screenW, int screenH) {
        if (!isNameDialogOpen()) {
            return false;
        }
        if (button != org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        BlueprintNameDialog.ClickResult click = BlueprintNameDialog.click(mouseX, mouseY, screenW, screenH,
                nameDialogMode == NameDialogMode.CAPTURE_SAVE);
        if (click == BlueprintNameDialog.ClickResult.CONFIRM) {
            confirmNameDialog();
            return true;
        }
        if (click == BlueprintNameDialog.ClickResult.CANCEL) {
            cancelNameDialog();
            return true;
        }
        return true;
    }

    public static boolean keyPressedNameDialog(int keyCode) {
        if (!isNameDialogOpen()) {
            return false;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            cancelNameDialog();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            confirmNameDialog();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            if (nameDialogReplaceOnType) {
                nameDialogValue = "";
                nameDialogReplaceOnType = false;
            } else if (!nameDialogValue.isEmpty()) {
                nameDialogValue = nameDialogValue.substring(0, nameDialogValue.length() - 1);
            }
            return true;
        }
        return true;
    }

    public static boolean charTypedNameDialog(char codePoint) {
        if (!isNameDialogOpen()) {
            return false;
        }
        if (!Character.isISOControl(codePoint) && nameDialogValue.length() < 80) {
            if (nameDialogReplaceOnType) {
                nameDialogValue = "";
                nameDialogReplaceOnType = false;
            }
            nameDialogValue += codePoint;
        }
        return true;
    }

    public static void renderMaterialDialog(GuiGraphics g, Font font, ClientRtsController controller,
            int screenW, int screenH, int mouseX, int mouseY) {
        if (!materialDialogOpen) {
            return;
        }
        BlueprintEntry entry = selectedEntry();
        if (entry == null) {
            materialDialogOpen = false;
            return;
        }
        materialDialogScroll = BlueprintMaterialDialog.render(g, font, entry, controller, screenW, screenH,
                mouseX, mouseY, materialDialogScroll);
    }

    public static boolean mouseClickedMaterialDialog(double mouseX, double mouseY, int button, int screenW, int screenH) {
        if (!materialDialogOpen) {
            return false;
        }
        if (button != org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        if (BlueprintMaterialDialog.shouldClose(mouseX, mouseY, screenW, screenH)) {
            materialDialogOpen = false;
        }
        return true;
    }

    public static boolean mouseScrolledMaterialDialog(double scrollY, ClientRtsController controller,
            int screenW, int screenH) {
        if (!materialDialogOpen) {
            return false;
        }
        BlueprintEntry entry = selectedEntry();
        if (entry == null) {
            materialDialogOpen = false;
            return true;
        }
        materialDialogScroll = BlueprintMaterialDialog.scrolled(materialDialogScroll, scrollY, entry, controller, screenH);
        return true;
    }

    public static boolean keyPressedMaterialDialog(int keyCode) {
        if (!materialDialogOpen) {
            return false;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            materialDialogOpen = false;
        }
        return true;
    }

    private static void openCaptureNameDialog() {
        nameDialogMode = NameDialogMode.CAPTURE_SAVE;
        nameDialogValue = sanitizeFileBase("captured_" + System.currentTimeMillis());
        nameDialogEntry = null;
        nameDialogReplaceOnType = false;
        nameDialogCaptureBlockCount = CAPTURE.countCapturableBlocks(Minecraft.getInstance().level);
        materialDialogOpen = false;
        searchFocused = false;
    }

    private static void openRenameDialog(BlueprintEntry entry) {
        if (entry == null || !entry.error().isBlank()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.no_selection", "");
            return;
        }
        nameDialogMode = NameDialogMode.RENAME_ENTRY;
        nameDialogValue = sanitizeFileBase(stripBlueprintExtension(entry.fileName()));
        nameDialogEntry = entry;
        // Renaming should behave like a selected text field: the first typed
        // character replaces the old file name instead of appending to it.
        nameDialogReplaceOnType = true;
        nameDialogCaptureBlockCount = 0L;
        materialDialogOpen = false;
        searchFocused = false;
    }

    private static void cancelNameDialog() {
        NameDialogMode previous = nameDialogMode;
        nameDialogMode = NameDialogMode.NONE;
        nameDialogValue = "";
        nameDialogEntry = null;
        nameDialogReplaceOnType = false;
        nameDialogCaptureBlockCount = 0L;
        setStatus(S2CBlueprintStatusPayload.INFO,
                previous == NameDialogMode.RENAME_ENTRY
                        ? "screen.rtsbuilding.blueprints.status.rename_cancelled"
                        : "screen.rtsbuilding.blueprints.status.save_cancelled",
                "");
    }

    private static void confirmNameDialog() {
        if (!isNameDialogOpen()) {
            return;
        }
        String cleanName = sanitizeFileBase(stripBlueprintExtension(nameDialogValue));
        if (cleanName.isBlank()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.name_required", "");
            return;
        }
        NameDialogMode mode = nameDialogMode;
        BlueprintEntry entry = nameDialogEntry;
        nameDialogMode = NameDialogMode.NONE;
        nameDialogValue = "";
        nameDialogEntry = null;
        nameDialogReplaceOnType = false;
        nameDialogCaptureBlockCount = 0L;
        if (mode == NameDialogMode.CAPTURE_SAVE) {
            startCaptureSave(cleanName);
        } else if (mode == NameDialogMode.RENAME_ENTRY) {
            renameEntry(entry, cleanName);
        }
    }

    public static void renderPlacementHud(GuiGraphics g, Font font, ClientRtsController controller,
            int screenW, int screenH, int mouseX, int mouseY, int topSafeY, int bottomSafeY) {
        if (!Config.areBlueprintsEnabled() || CAPTURE.isActive()) {
            return;
        }
        BlueprintEntry entry = selectedEntry();
        if (entry == null || !entry.error().isBlank()) {
            return;
        }

        int topY = topSafeY;
        int barW = Math.max(286, Math.min(560, screenW - 32));
        int barH = 24;
        int barX = (screenW - barW) / 2;
        int barY = Math.max(topY, bottomSafeY - barH - 8);
        drawFrame(g, barX, barY, barW, barH, 0xDD101820, 0xFF5B7894, 0xFF0B0F14);

        int xPos = barX + 6;
        int rotateW = 42;
        int resetW = 40;
        int nudgeW = 34;
        int gap = 4;
        drawButton(g, font, xPos, barY + 5, rotateW, DETAIL_BUTTON_H, text("screen.rtsbuilding.blueprints.y_rotate_short"),
                inside(mouseX, mouseY, xPos, barY + 5, rotateW, DETAIL_BUTTON_H));
        xPos += rotateW + gap;
        drawButton(g, font, xPos, barY + 5, resetW, DETAIL_BUTTON_H, text("screen.rtsbuilding.blueprints.reset_rotation_short"),
                inside(mouseX, mouseY, xPos, barY + 5, resetW, DETAIL_BUTTON_H));
        xPos += resetW + gap + 4;
        String[] labels = {
                text("screen.rtsbuilding.blueprints.nudge_forward_short"),
                text("screen.rtsbuilding.blueprints.nudge_back_short"),
                text("screen.rtsbuilding.blueprints.nudge_left_short"),
                text("screen.rtsbuilding.blueprints.nudge_right_short"),
                text("screen.rtsbuilding.blueprints.nudge_y_minus_short"),
                text("screen.rtsbuilding.blueprints.nudge_y_plus_short")
        };
        for (String label : labels) {
            drawButton(g, font, xPos, barY + 5, nudgeW, DETAIL_BUTTON_H, label,
                    inside(mouseX, mouseY, xPos, barY + 5, nudgeW, DETAIL_BUTTON_H));
            xPos += nudgeW + gap;
        }

        int buildW = 54;
        int cancelW = 46;
        int detailsW = 48;
        int detailsX = barX + barW - detailsW - buildW - cancelW - gap * 2 - 6;
        int buildX = detailsX + detailsW + gap;
        int cancelX = buildX + buildW + gap;
        drawButton(g, font, detailsX, barY + 5, detailsW, DETAIL_BUTTON_H,
                text("screen.rtsbuilding.blueprints.details"),
                inside(mouseX, mouseY, detailsX, barY + 5, detailsW, DETAIL_BUTTON_H));
        drawButton(g, font, buildX, barY + 5, buildW, DETAIL_BUTTON_H,
                text("screen.rtsbuilding.blueprints.build_preview"),
                inside(mouseX, mouseY, buildX, barY + 5, buildW, DETAIL_BUTTON_H), pinnedAnchor != null);
        drawButton(g, font, cancelX, barY + 5, cancelW, DETAIL_BUTTON_H,
                text("screen.rtsbuilding.blueprints.capture_cancel"),
                inside(mouseX, mouseY, cancelX, barY + 5, cancelW, DETAIL_BUTTON_H));
    }

    public static boolean mouseClickedPlacementHud(double mouseX, double mouseY, int screenW, int screenH,
            int topSafeY, int bottomSafeY, ClientRtsController controller) {
        if (!Config.areBlueprintsEnabled() || CAPTURE.isActive() || !hasSelectedBlueprint()) {
            return false;
        }

        int topY = topSafeY;
        int barW = Math.max(286, Math.min(560, screenW - 32));
        int barH = 24;
        int barX = (screenW - barW) / 2;
        int barY = Math.max(topY, bottomSafeY - barH - 8);
        int xPos = barX + 6;
        int rotateW = 42;
        int resetW = 40;
        int nudgeW = 34;
        int gap = 4;
        if (inside(mouseX, mouseY, xPos, barY + 5, rotateW, DETAIL_BUTTON_H)) {
            yRotationSteps = BlueprintTransform.normalizeSteps(yRotationSteps + 1);
            rememberCurrentRotationAsDefault();
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.rotated", "");
            return true;
        }
        xPos += rotateW + gap;
        if (inside(mouseX, mouseY, xPos, barY + 5, resetW, DETAIL_BUTTON_H)) {
            yRotationSteps = 0;
            xRotationSteps = 0;
            zRotationSteps = 0;
            rememberCurrentRotationAsDefault();
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.rotated", "");
            return true;
        }
        xPos += resetW + gap + 4;
        int[][] deltas = {
                {0, 1, 0},
                {0, -1, 0},
                {-1, 0, 0},
                {1, 0, 0},
                {0, 0, -1},
                {0, 0, 1}
        };
        for (int[] delta : deltas) {
            if (inside(mouseX, mouseY, xPos, barY + 5, nudgeW, DETAIL_BUTTON_H)) {
                nudgePinnedAnchorRelative(delta[0], delta[1], delta[2], controller);
                return true;
            }
            xPos += nudgeW + gap;
        }

        int buildW = 54;
        int cancelW = 46;
        int detailsW = 48;
        int detailsX = barX + barW - detailsW - buildW - cancelW - gap * 2 - 6;
        int buildX = detailsX + detailsW + gap;
        int cancelX = buildX + buildW + gap;
        if (inside(mouseX, mouseY, detailsX, barY + 5, detailsW, DETAIL_BUTTON_H)) {
            openMaterialDialog();
            return true;
        }
        if (inside(mouseX, mouseY, buildX, barY + 5, buildW, DETAIL_BUTTON_H)) {
            return buildPinnedPreview();
        }
        if (inside(mouseX, mouseY, cancelX, barY + 5, cancelW, DETAIL_BUTTON_H)) {
            clearSelectedBlueprint();
            return true;
        }
        return inside(mouseX, mouseY, barX, barY, barW, barH);
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double scrollY, int x, int y, int w, int h) {
        if (!Config.areBlueprintsEnabled()) {
            return false;
        }
        int listY = y + 19;
        int statusY = y + h - 13;
        int listH = Math.max(24, statusY - listY - 4);
        int detailsW = Math.min(210, Math.max(148, w / 4));
        int listW = Math.max(120, w - detailsW - 8);
        if (!inside(mouseX, mouseY, x, listY, listW, listH)) {
            return false;
        }
        List<BlueprintEntry> filtered = filteredEntries();
        int columns = listColumns(listW);
        int visibleRows = Math.max(1, listH / ROW_H);
        int maxScroll = maxListScroll(filtered.size(), columns, visibleRows);
        scroll = Mth.clamp(scroll + (scrollY > 0.0D ? -1 : 1), 0, maxScroll);
        return true;
    }

    public static boolean keyPressed(int keyCode, int scanCode, ClientRtsController controller) {
        if (!Config.areBlueprintsEnabled()) {
            searchFocused = false;
            return false;
        }
        boolean cancelKey = ClientKeyMappings.BLUEPRINT_CANCEL.matches(keyCode, scanCode);
        if (CAPTURE.isActive()) {
            searchFocused = false;
            if (CAPTURE.isSaving()) {
                setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
                return true;
            }
            int step = org.lwjgl.glfw.GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(),
                    org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                    || org.lwjgl.glfw.GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(),
                            org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                    ? 4 : 1;
            if (cancelKey || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                cancelCaptureMode();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                saveCapturedArea();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
                CAPTURE.moveSelection(0, step, 0, BlueprintPanel::setStatus);
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN) {
                CAPTURE.moveSelection(0, -step, 0, BlueprintPanel::setStatus);
                return true;
            }
            return true;
        }
        if (!searchFocused && hasSelectedBlueprint() && isBlueprintRotateKey(keyCode, scanCode)) {
            return rotateSelectedBlueprintY(isShiftDown() ? -1 : 1);
        }
        if (hasPinnedPreview()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_4
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
                return nudgePinnedAnchorRelative(-1, 0, 0, controller);
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_6
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
                return nudgePinnedAnchorRelative(1, 0, 0, controller);
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_8
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
                return nudgePinnedAnchorRelative(0, 1, 0, controller);
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_2
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
                return nudgePinnedAnchorRelative(0, -1, 0, controller);
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
                return nudgePinnedAnchor(0, 1, 0, controller);
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN) {
                return nudgePinnedAnchor(0, -1, 0, controller);
            }
        }
        if (!searchFocused && cancelKey) {
            if (hasSelectedBlueprint() || hasPinnedPreview()) {
                clearSelectedBlueprint();
                return true;
            }
            return false;
        }
        if (!searchFocused) {
            return false;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            if (!search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                scroll = 0;
            }
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            searchFocused = false;
            return true;
        }
        return false;
    }

    public static boolean isPlacementSessionActive() {
        return Config.areBlueprintsEnabled() && (CAPTURE.isActive() || hasSelectedBlueprint());
    }

    public static boolean isBlueprintRotateKey(int keyCode, int scanCode) {
        return ClientKeyMappings.ROTATE_SHAPE.matches(keyCode, scanCode)
                || ClientKeyMappings.MODE_ROTATE.matches(keyCode, scanCode);
    }

    public static boolean charTyped(char codePoint) {
        if (!Config.areBlueprintsEnabled()) {
            return false;
        }
        if (!searchFocused || Character.isISOControl(codePoint)) {
            return false;
        }
        if (search.length() < 96) {
            search += codePoint;
            scroll = 0;
        }
        return true;
    }

    public static boolean hasSelectedBlueprint() {
        if (!Config.areBlueprintsEnabled()) {
            return false;
        }
        BlueprintEntry entry = selectedEntry();
        return entry != null && entry.error().isBlank();
    }

    static String selectedBlueprintName() {
        BlueprintEntry entry = selectedEntry();
        return entry == null ? "" : entry.name();
    }

    static String selectedBlueprintSizeText() {
        BlueprintEntry entry = selectedEntry();
        return entry == null ? "" : entry.sizeText();
    }

    static void selectRelativeBlueprint(int delta) {
        ensureLoaded();
        if (ENTRIES.isEmpty() || delta == 0) {
            return;
        }
        int start = selectedIndex >= 0 && selectedIndex < ENTRIES.size() ? selectedIndex : 0;
        for (int step = 1; step <= ENTRIES.size(); step++) {
            int index = Math.floorMod(start + delta * step, ENTRIES.size());
            BlueprintEntry entry = ENTRIES.get(index);
            if (entry.error().isBlank()) {
                selectEntry(entry);
                return;
            }
        }
    }

    public static int getYRotationSteps() {
        return yRotationSteps;
    }

    public static int getXRotationSteps() {
        return xRotationSteps;
    }

    public static int getZRotationSteps() {
        return zRotationSteps;
    }

    public static BlockPos getPinnedAnchor() {
        return pinnedAnchor;
    }

    static Component statusText() {
        return statusText;
    }

    static int statusColor() {
        return statusColor;
    }

    public static boolean isCaptureModeActive() {
        return Config.areBlueprintsEnabled() && CAPTURE.isActive();
    }

    static boolean isCaptureSaving() {
        return Config.areBlueprintsEnabled() && CAPTURE.isSaving();
    }

    public static boolean isCaptureSelectionComplete() {
        return Config.areBlueprintsEnabled() && CAPTURE.isSelectionComplete();
    }

    public static boolean hasPinnedPreview() {
        return Config.areBlueprintsEnabled() && pinnedAnchor != null && hasSelectedBlueprint();
    }

    public static BlockPos getCapturePointA() {
        return CAPTURE.pointA();
    }

    public static BlockPos getCapturePointB() {
        return CAPTURE.pointB();
    }

    static String capturePointAText() {
        return shortPos(CAPTURE.pointA());
    }

    static String capturePointBText() {
        return shortPos(CAPTURE.previewPointB());
    }

    static String captureSizeText() {
        return BlueprintCaptureGeometry.captureSizeText(CAPTURE.pointA(), CAPTURE.previewPointB());
    }

    static int captureSizeX() {
        return CAPTURE.sizeX();
    }

    static int captureSizeY() {
        return CAPTURE.sizeY();
    }

    static int captureSizeZ() {
        return CAPTURE.sizeZ();
    }

    static long countCaptureBlocks() {
        return CAPTURE.countCapturableBlocks(Minecraft.getInstance().level);
    }

    static String captureSaveProgressLine() {
        return CAPTURE.saveProgressLine();
    }

    public static void updateCaptureHoverPoint(BlockPos pos) {
        CAPTURE.updateHoverPoint(pos);
    }

    public static BlockPos getCapturePreviewPointB() {
        return CAPTURE.previewPointB();
    }

    public static List<BlockPos> getCaptureIncludedBlocksForRender(int limit) {
        return Config.areBlueprintsEnabled()
                ? CAPTURE.includedBlocksForRender(Minecraft.getInstance().level, limit)
                : List.of();
    }

    public static boolean shouldRenderCaptureBlockHighlights(int limit) {
        return Config.areBlueprintsEnabled() && CAPTURE.shouldRenderBlockHighlights(limit);
    }

    public static List<BlockPos> getCaptureExcludedBlocksForRender(int limit) {
        return Config.areBlueprintsEnabled() ? CAPTURE.excludedBlocksForRender(limit) : List.of();
    }

    public static boolean acceptCapturePoint(BlockPos pos) {
        return Config.areBlueprintsEnabled() && CAPTURE.acceptPoint(pos, BlueprintPanel::setStatus);
    }

    public static boolean toggleCaptureBlockExclusion(BlockPos pos) {
        return Config.areBlueprintsEnabled() && CAPTURE.toggleBlockExclusion(pos, BlueprintPanel::setStatus);
    }

    public static boolean cancelCaptureFromClick() {
        if (!Config.areBlueprintsEnabled() || !CAPTURE.isActive()) {
            return false;
        }
        if (CAPTURE.isSaving()) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return true;
        }
        cancelCaptureMode();
        return true;
    }

    static void moveCaptureSelection(int dx, int dy, int dz) {
        if (!Config.areBlueprintsEnabled()) {
            return;
        }
        CAPTURE.moveSelection(dx, dy, dz, BlueprintPanel::setStatus);
    }

    static void adjustCaptureSize(int dx, int dy, int dz) {
        if (!Config.areBlueprintsEnabled()) {
            return;
        }
        CAPTURE.resizeSelection(dx, dy, dz, BlueprintPanel::setStatus);
    }

    public static boolean mouseScrolledCaptureHeight(double scrollY) {
        if (!Config.areBlueprintsEnabled() || !CAPTURE.isActive()) {
            return false;
        }
        if (CAPTURE.isSaving()) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return true;
        }
        return false;
    }

    static void setCaptureSize(int x, int y, int z) {
        if (!Config.areBlueprintsEnabled()) {
            return;
        }
        CAPTURE.setSelectionSize(x, y, z, BlueprintPanel::setStatus);
    }

    public static void renderCaptureOverlay(GuiGraphics g, Font font, int screenW, int screenH, int mouseX, int mouseY,
            int topSafeY) {
        if (!Config.areBlueprintsEnabled() || !CAPTURE.isActive()) {
            return;
        }
        tickCaptureSaveJob();
        int infoW = Math.min(420, Math.max(270, screenW - 32));
        int infoH = CAPTURE.pointB() == null && !CAPTURE.isSaving() ? 40 : 46;
        int infoX = (screenW - infoW) / 2;
        int infoY = topSafeY;
        drawFrame(g, infoX, infoY, infoW, infoH, 0xDD101820, 0xFF5B7894, 0xFF0B0F14);
        g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.capture_tool_title"), infoW - 12),
                infoX + 6, infoY + 6, 0xFFEAF2FF, false);
        String state = CAPTURE.pointA() == null
                ? text("screen.rtsbuilding.blueprints.capture_waiting_a")
                : CAPTURE.pointB() == null
                        ? text("screen.rtsbuilding.blueprints.capture_waiting_b")
                        : text("screen.rtsbuilding.blueprints.capture_ready");
        if (CAPTURE.isSaving()) {
            state = CAPTURE.saveStatusLine();
        }
        String sizeLine = CAPTURE.pointA() == null
                ? state
                : text("screen.rtsbuilding.blueprints.capture_live_size",
                        BlueprintCaptureGeometry.captureSizeText(CAPTURE.pointA(), CAPTURE.previewPointB()));
        g.drawString(font, trim(font, sizeLine + "  " + state, infoW - 112), infoX + 6, infoY + 20,
                CAPTURE.pointA() != null && CAPTURE.pointB() != null ? 0xFF8EEA9B : 0xFFFFC06C, false);
        if (CAPTURE.pointB() == null && !CAPTURE.isSaving()) {
            return;
        }

        int saveX = infoX + infoW - 104;
        int cancelX = infoX + infoW - 52;
        int buttonY = infoY + 8;
        drawButton(g, font, saveX, buttonY, 48, DETAIL_BUTTON_H,
                text("screen.rtsbuilding.blueprints.save_area"),
                inside(mouseX, mouseY, saveX, buttonY, 48, DETAIL_BUTTON_H));
        drawButton(g, font, cancelX, buttonY, 46, DETAIL_BUTTON_H,
                text("screen.rtsbuilding.blueprints.capture_cancel"),
                inside(mouseX, mouseY, cancelX, buttonY, 46, DETAIL_BUTTON_H));
        if (CAPTURE.isSaving()) {
            g.drawString(font, trim(font, CAPTURE.saveProgressLine(), infoW - 12), infoX + 6, infoY + 33,
                    0xFFB7CDE2, false);
        }

    }

    public static boolean mouseClickedCaptureOverlay(double mouseX, double mouseY, int screenW, int screenH, int topSafeY) {
        if (!Config.areBlueprintsEnabled() || !CAPTURE.isActive()) {
            return false;
        }
        if (CAPTURE.pointB() == null && !CAPTURE.isSaving()) {
            return false;
        }
        int infoW = Math.min(420, Math.max(270, screenW - 32));
        int infoX = (screenW - infoW) / 2;
        int infoY = topSafeY;
        int saveX = infoX + infoW - 104;
        int cancelX = infoX + infoW - 52;
        int buttonY = infoY + 8;
        if (inside(mouseX, mouseY, saveX, buttonY, 48, DETAIL_BUTTON_H)) {
            saveCapturedArea();
            return true;
        }
        if (inside(mouseX, mouseY, cancelX, buttonY, 46, DETAIL_BUTTON_H)) {
            cancelCaptureMode();
            return true;
        }
        if (inside(mouseX, mouseY, infoX, infoY, infoW, CAPTURE.isSaving() ? 58 : 46)) {
            return true;
        }
        if (CAPTURE.isSaving()) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return true;
        }
        return false;
    }

    public static boolean pinSelected(BlockPos anchor) {
        if (!Config.areBlueprintsEnabled()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.disabled", "");
            return true;
        }
        if (!hasSelectedBlueprint() || anchor == null) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.no_selection", "");
            return false;
        }
        pinnedAnchor = anchor.immutable();
        pinnedNudgeForward = currentHorizontalFacingDirection();
        setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.preview_pinned", shortPos(pinnedAnchor));
        return true;
    }

    /**
     * Converts the cursor target block into the internal blueprint anchor.
     *
     * <p>The placement payload still uses the original anchor convention, where
     * blueprint-relative blocks are offset from an invisible origin. For mouse
     * placement, however, players expect the cursor to hold the building itself,
     * not an empty corner of a loose capture box. This maps the cursor target to
     * the transformed blueprint content's bottom-center cell.</p>
     */
    public static BlockPos anchorForCursorTarget(BlockPos cursorTarget) {
        BlueprintEntry entry = selectedEntry();
        if (cursorTarget == null || entry == null || !entry.error().isBlank()) {
            return cursorTarget;
        }
        int y = BlueprintTransform.normalizeSteps(yRotationSteps);
        int x = BlueprintTransform.normalizeSteps(xRotationSteps);
        int z = BlueprintTransform.normalizeSteps(zRotationSteps);
        PlacementBounds bounds = transformedContentBounds(entry.blueprint(), y, x, z);
        if (bounds == null) {
            return cursorTarget;
        }
        return cursorTarget.offset(-bounds.centerX(), -bounds.minY(), -bounds.centerZ());
    }

    public static BlueprintGhostPreview createGhostPreview(BlockPos anchor, int yRotationSteps, ClientRtsController controller) {
        BlueprintEntry entry = selectedEntry();
        if (!Config.areBlueprintsEnabled() || anchor == null || entry == null || !entry.error().isBlank()) {
            return BlueprintGhostPreview.EMPTY;
        }
        int previewLimit = Math.max(1, Config.maxBlueprintBlocks());
        List<BlueprintGhostBlock> out = new ArrayList<>(Math.min(entry.blockCount(), previewLimit));
        int y = BlueprintTransform.normalizeSteps(yRotationSteps);
        int x = BlueprintTransform.normalizeSteps(xRotationSteps);
        int z = BlueprintTransform.normalizeSteps(zRotationSteps);
        BlockPos centerOffset = BlueprintTransform.centerRotationOffset(entry.blueprint().size(), y, x, z);
        for (RtsBlueprintBlock block : entry.blueprint().blocks()) {
            BlockPos pos = anchor.offset(BlueprintTransform.rotateAroundCenter(block.relativePos(), y, x, z, centerOffset)).immutable();
            BlockState state = block.isMissingBlock()
                    ? Blocks.AIR.defaultBlockState()
                    : BlueprintTransform.rotateState(block.state(), y, x, z);
            out.add(new BlueprintGhostBlock(pos, state, block.isMissingBlock()));
            if (out.size() >= previewLimit) {
                break;
            }
        }
        return new BlueprintGhostPreview(List.copyOf(out), hasEnoughMaterials(entry, controller), entry.blockCount() > out.size());
    }

    private static PlacementBounds transformedContentBounds(RtsBlueprint blueprint, int y, int x, int z) {
        if (blueprint == null || blueprint.blocks().isEmpty()) {
            return null;
        }
        BlockPos centerOffset = BlueprintTransform.centerRotationOffset(blueprint.size(), y, x, z);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean found = false;
        for (RtsBlueprintBlock block : blueprint.blocks()) {
            if (block == null || (!block.isMissingBlock() && (block.state() == null || block.state().isAir()))) {
                continue;
            }
            BlockPos pos = BlueprintTransform.rotateAroundCenter(block.relativePos(), y, x, z, centerOffset);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
            found = true;
        }
        return found ? new PlacementBounds(minX, minY, minZ, maxX, maxY, maxZ) : null;
    }

    private record PlacementBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int centerX() {
            return this.minX + ((this.maxX - this.minX) / 2);
        }

        int centerZ() {
            return this.minZ + ((this.maxZ - this.minZ) / 2);
        }
    }

    public static boolean placeSelected(BlockPos anchor, int yRotationSteps, int xRotationSteps, int zRotationSteps) {
        if (!Config.areBlueprintsEnabled()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.disabled", "");
            return true;
        }
        BlueprintEntry entry = selectedEntry();
        if (entry == null || !entry.error().isBlank()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.no_selection", "");
            return false;
        }
        try {
            byte[] data = Files.readAllBytes(entry.path());
            if (data.length > C2SBlueprintPlacePayload.MAX_FILE_BYTES) {
                setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.too_large", "");
                return true;
            }
            PacketDistributor.sendToServer(new C2SBlueprintPlacePayload(
                    entry.fileName(),
                    data,
                    anchor,
                    (byte) BlueprintTransform.normalizeSteps(yRotationSteps),
                    (byte) BlueprintTransform.normalizeSteps(xRotationSteps),
                    (byte) BlueprintTransform.normalizeSteps(zRotationSteps)));
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.uploading", entry.name());
            pinnedAnchor = null;
            return true;
        } catch (IOException ex) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.read_failed", ex.getMessage());
            return true;
        }
    }

    private static boolean buildPinnedPreview() {
        if (pinnedAnchor == null) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.no_preview", "");
            return true;
        }
        return placeSelected(pinnedAnchor, yRotationSteps, xRotationSteps, zRotationSteps);
    }

    public static boolean confirmPinnedPreview() {
        return buildPinnedPreview();
    }

    private static boolean rememberCurrentRotationAsDefault() {
        BlueprintEntry entry = selectedEntry();
        if (entry == null || !entry.error().isBlank()) {
            return false;
        }
        IOException ex = BlueprintRotationDefaults.remember(entry.fileName(), yRotationSteps, xRotationSteps, zRotationSteps);
        if (ex != null) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.save_failed", ex.getMessage());
            return false;
        }
        return true;
    }

    static boolean rotateSelectedBlueprintY(int step) {
        if (!hasSelectedBlueprint()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.no_selection", "");
            return true;
        }
        yRotationSteps = BlueprintTransform.normalizeSteps(yRotationSteps + step);
        rememberCurrentRotationAsDefault();
        setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.rotated", "");
        return true;
    }

    static void resetSelectedBlueprintRotation() {
        if (!hasSelectedBlueprint()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.no_selection", "");
            return;
        }
        yRotationSteps = 0;
        xRotationSteps = 0;
        zRotationSteps = 0;
        rememberCurrentRotationAsDefault();
        setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.rotated", "");
    }

    static boolean nudgePinnedAnchor(int dx, int dy, int dz, ClientRtsController controller) {
        if (pinnedAnchor == null) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.no_preview", "");
            return true;
        }
        BlockPos next = clampAnchorToClientBuildLimits(pinnedAnchor.offset(dx, dy, dz), controller);
        if (next.equals(pinnedAnchor)) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.nudge_blocked", "");
            return true;
        }
        pinnedAnchor = next.immutable();
        setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.nudged", shortPos(pinnedAnchor));
        return true;
    }

    static boolean setPinnedAnchor(BlockPos anchor, ClientRtsController controller) {
        if (anchor == null) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.no_preview", "");
            return true;
        }
        pinnedAnchor = clampAnchorToClientBuildLimits(anchor, controller).immutable();
        setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.nudged", shortPos(pinnedAnchor));
        return true;
    }

    static boolean nudgePinnedAnchorRelative(int rightSteps, int forwardSteps, int upSteps,
            ClientRtsController controller) {
        Direction forward = pinnedNudgeForward == null ? currentHorizontalFacingDirection() : pinnedNudgeForward;
        Direction right = rightOf(forward);
        int dx = forward.getStepX() * forwardSteps + right.getStepX() * rightSteps;
        int dz = forward.getStepZ() * forwardSteps + right.getStepZ() * rightSteps;
        return nudgePinnedAnchor(dx, upSteps, dz, controller);
    }

    private static Direction currentHorizontalFacingDirection() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gameRenderer != null) {
            return Direction.fromYRot(minecraft.gameRenderer.getMainCamera().getYRot());
        }
        if (minecraft != null && minecraft.getCameraEntity() != null) {
            return Direction.fromYRot(minecraft.getCameraEntity().getYRot());
        }
        if (minecraft != null && minecraft.player != null) {
            return Direction.fromYRot(minecraft.player.getYRot());
        }
        return Direction.SOUTH;
    }

    private static boolean isShiftDown() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return false;
        }
        long window = minecraft.getWindow().getWindow();
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    private static Direction rightOf(Direction forward) {
        return switch (forward) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> Direction.WEST;
        };
    }

    private static BlockPos clampAnchorToClientBuildLimits(BlockPos pos, ClientRtsController controller) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            y = Mth.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        }
        if (controller != null && controller.hasBounds()) {
            double halfExtent = controller.getMaxRadius() + 8.0D;
            int minX = Mth.ceil(controller.getAnchorX() - halfExtent - 0.5D);
            int maxX = Mth.floor(controller.getAnchorX() + halfExtent - 0.5D);
            int minZ = Mth.ceil(controller.getAnchorZ() - halfExtent - 0.5D);
            int maxZ = Mth.floor(controller.getAnchorZ() + halfExtent - 0.5D);
            if (minX <= maxX) {
                x = Mth.clamp(x, minX, maxX);
            }
            if (minZ <= maxZ) {
                z = Mth.clamp(z, minZ, maxZ);
            }
        }
        return new BlockPos(x, y, z);
    }

    public static void setStatus(byte status, String messageKey, String detail) {
        Component base = detail == null || detail.isBlank()
                ? Component.translatable(messageKey)
                : Component.translatable(messageKey, detail);
        statusText = base;
        statusColor = switch (status) {
            case S2CBlueprintStatusPayload.SUCCESS -> 0xFF81E58E;
            case S2CBlueprintStatusPayload.ERROR -> 0xFFFF8A8A;
            default -> 0xFFB8C7D6;
        };
    }

    private static void tickCaptureSaveJob() {
        BlueprintCaptureController.SaveResult result = CAPTURE.pollSaveResult();
        if (result == null) {
            return;
        }
        if (!result.success()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, result.messageKey(), result.detail());
            return;
        }
        addOrReplaceEntry(result.path(), result.blueprint());
        selectByFileName(result.fileName());
        setStatus(S2CBlueprintStatusPayload.SUCCESS, "screen.rtsbuilding.blueprints.status.saved_blueprint", result.fileName());
    }

    private static String failureDetail(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static void handleSaveFailure(Throwable throwable) {
        if (throwable instanceof Error error) {
            throw error;
        }
        setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.save_failed", failureDetail(throwable));
    }

    private static void renderDisabled(GuiGraphics g, Font font, int x, int y, int w, int h) {
        drawFrame(g, x, y, w, h, 0x8811161E, 0xFF415266, 0xFF0B0E13);
        Component title = Component.translatable("screen.rtsbuilding.blueprints.disabled");
        Component detail = Component.translatable("screen.rtsbuilding.blueprints.status.disabled");
        g.drawString(font, trim(font, title.getString(), w - 12), x + 6, y + 8, 0xFFEAF2FF, false);
        g.drawString(font, trim(font, detail.getString(), w - 12), x + 6, y + 22, 0xFF9EACB9, false);
    }

    private static void renderList(GuiGraphics g, Font font, ClientRtsController controller,
            int x, int y, int w, int h, int mouseX, int mouseY) {
        drawFrame(g, x, y, w, h, 0x8811161E, 0xFF415266, 0xFF0B0E13);
        List<BlueprintEntry> filtered = filteredEntries();
        int columns = listColumns(w);
        int visibleRows = Math.max(1, h / ROW_H);
        int cellW = listCellWidth(w, columns);
        scroll = Mth.clamp(scroll, 0, maxListScroll(filtered.size(), columns, visibleRows));
        if (filtered.isEmpty()) {
            Component empty = ENTRIES.isEmpty()
                    ? Component.translatable("screen.rtsbuilding.blueprints.empty")
                    : Component.translatable("screen.rtsbuilding.blueprints.no_results");
            g.drawString(font, trim(font, empty.getString(), w - 12), x + 6, y + 8, 0xFF9EACB9, false);
            return;
        }
        for (int row = 0; row < visibleRows; row++) {
            int rowY = y + row * ROW_H;
            for (int col = 0; col < columns; col++) {
                int index = (scroll + row) * columns + col;
                if (index >= filtered.size()) {
                    break;
                }
                BlueprintEntry entry = filtered.get(index);
                int cellX = x + 1 + col * (cellW + LIST_COLUMN_GAP);
                int cellRight = Math.min(x + w - 1, cellX + cellW);
                int actualW = Math.max(44, cellRight - cellX);
                boolean selected = selectedIndex >= 0 && selectedIndex < ENTRIES.size() && ENTRIES.get(selectedIndex) == entry;
                boolean hover = inside(mouseX, mouseY, cellX, rowY, actualW, ROW_H);
                BuildStats stats = buildStats(entry, controller);
                boolean enough = stats.percent() >= 100;
                int bg = selected ? 0xCC2E654B : hover ? 0xAA2B3542 : enough ? 0x77253832 : 0x7731363E;
                if (!entry.error().isBlank()) {
                    bg = selected ? 0xCC694238 : 0x77503A36;
                }
                RowActionLayout actions = rowActionLayout(font, cellX, rowY, actualW);
                boolean showActions = hover || selected;
                int rightTextX = showActions ? actions.saveX() - 4 : cellX + actualW - 38;
                g.fill(cellX, rowY + 1, cellX + actualW, rowY + ROW_H - 1, bg);
                g.drawString(font, trim(font, entry.name(), Math.max(32, rightTextX - cellX - 8)), cellX + 5, rowY + 4,
                        entry.error().isBlank() ? 0xFFEAF2FF : 0xFFFFB0A0, false);
                if (showActions) {
                    if (entry.error().isBlank()) {
                        drawButton(g, font, actions.saveX(), actions.buttonY(), actions.saveW(), DETAIL_BUTTON_H,
                                text("screen.rtsbuilding.blueprints.save_as_short"),
                                inside(mouseX, mouseY, actions.saveX(), actions.buttonY(), actions.saveW(), DETAIL_BUTTON_H));
                        drawButton(g, font, actions.renameX(), actions.buttonY(), actions.renameW(), DETAIL_BUTTON_H,
                                text("screen.rtsbuilding.blueprints.rename"),
                                inside(mouseX, mouseY, actions.renameX(), actions.buttonY(), actions.renameW(), DETAIL_BUTTON_H));
                    }
                    drawButton(g, font, actions.deleteX(), actions.buttonY(), actions.deleteW(), DETAIL_BUTTON_H,
                            text("screen.rtsbuilding.blueprints.delete"),
                            inside(mouseX, mouseY, actions.deleteX(), actions.buttonY(), actions.deleteW(), DETAIL_BUTTON_H));
                } else {
                    g.drawString(font, stats.percent() + "%", cellX + actualW - 36, rowY + 4,
                            enough ? 0xFF9BE6A5 : 0xFF9CA6B2, false);
                }
                g.drawString(font, trim(font, entry.sizeText(), Math.max(24, actualW - 70)), cellX + 5, rowY + 14,
                        0xFF8FA2B7, false);
                int barX = cellX + 64;
                int barY = rowY + ROW_H - 5;
                int barW = Math.max(12, cellX + actualW - barX - 4);
                g.fill(barX, barY, barX + barW, barY + 2, 0xAA0C1118);
                int fillW = Mth.clamp(stats.percent(), 0, 100) * barW / 100;
                g.fill(barX, barY, barX + fillW, barY + 2, enough ? 0xFF62D77A : 0xFFE4B04D);
            }
        }
    }

    private static void renderCaptureLockedBottom(GuiGraphics g, Font font, int x, int y, int w, int h) {
        drawFrame(g, x, y, w, h, 0x8811161E, 0xFF415266, 0xFF0B0E13);
        int textX = x + 8;
        int textY = y + 8;
        g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.capture_tool_title"), w - 16),
                textX, textY, 0xFFEAF2FF, false);
        textY += 14;
        g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.status.capture_locked"), w - 16),
                textX, textY, 0xFFFFC06C, false);
    }

    private static void renderDetails(GuiGraphics g, Font font, ClientRtsController controller,
            int x, int y, int w, int h, int mouseX, int mouseY) {
        drawFrame(g, x, y, w, h, 0x8811161E, 0xFF415266, 0xFF0B0E13);
        BlueprintEntry entry = selectedEntry();
        if (entry == null) {
            g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.select_hint"), w - 12), x + 6, y + 8,
                    0xFF9EACB9, false);
            return;
        }
        g.drawString(font, trim(font, entry.name(), w - 12), x + 6, y + 6, 0xFFEAF2FF, false);
        g.drawString(font, entry.format().extension().toUpperCase(Locale.ROOT) + "  " + entry.sizeText(), x + 6, y + 18,
                0xFF9EACB9, false);
        BuildStats stats = buildStats(entry, controller);
        boolean enough = stats.percent() >= 100;
        String materialLine = materialSummary(entry, controller, stats);
        g.drawString(font, trim(font, materialLine, w - 12), x + 6, y + 31, enough ? 0xFF8EEA9B : 0xFFFFC06C, false);

        int progressX = x + 6;
        int progressY = y + 44;
        int progressW = Math.max(36, w - 12);
        g.fill(progressX, progressY, progressX + progressW, progressY + 4, 0xAA0C1118);
        g.fill(progressX, progressY, progressX + Mth.clamp(stats.percent(), 0, 100) * progressW / 100, progressY + 4,
                enough ? 0xFF62D77A : 0xFFE4B04D);

        int contentY = y + 56;
        renderPreviewItems(g, entry, x + 6, contentY, y + h - 4);
        if (!entry.error().isBlank()) {
            g.drawString(font, trim(font, entry.error(), w - 12), x + 6, y + h - 16, 0xFFFFA0A0, false);
        }
    }

    private static boolean handleDetailsClick(double mouseX, double mouseY, int x, int y, int w, int h) {
        BlueprintEntry entry = selectedEntry();
        if (entry == null) {
            return true;
        }
        return true;
    }

    static void openMaterialDialog() {
        if (selectedEntry() == null) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.no_selection", "");
            return;
        }
        materialDialogOpen = true;
        materialDialogScroll = 0;
    }

    private static void renderPreviewItems(GuiGraphics g, BlueprintEntry entry, int x, int y, int bottomY) {
        for (int i = 0; i < entry.previewItems().size() && i < 18; i++) {
            int px = x + (i % 6) * 20;
            int py = y + (i / 6) * 20;
            if (py + 18 > bottomY) {
                break;
            }
            g.fill(px, py, px + 18, py + 18, 0xAA1A2029);
            g.renderItem(entry.previewItems().get(i), px + 1, py + 1);
        }
    }

    private static void ensureLoaded() {
        if (!loaded) {
            reload();
        }
        BlueprintRotationDefaults.ensureLoaded();
    }

    public static void reload() {
        loaded = true;
        BlueprintRotationDefaults.ensureLoaded();
        ENTRIES.clear();
        selectedIndex = -1;
        scroll = 0;
        materialDialogOpen = false;
        materialDialogScroll = 0;
        pinnedAnchor = null;
        Path folder = blueprintFolder();
        try {
            Files.createDirectories(folder);
            try (var stream = Files.list(folder)) {
                stream.filter(Files::isRegularFile)
                        .filter(BlueprintPanelFiles::isBlueprintFile)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .limit(512)
                        .forEach(BlueprintPanel::addEntry);
            }
        } catch (IOException ex) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.folder_failed", ex.getMessage());
        }
    }

    private static void applyDefaultRotation(BlueprintEntry entry) {
        if (entry == null) {
            yRotationSteps = 0;
            xRotationSteps = 0;
            zRotationSteps = 0;
            return;
        }
        RotationPreset preset = BlueprintRotationDefaults.rotationFor(entry.fileName());
        yRotationSteps = preset == null ? 0 : BlueprintTransform.normalizeSteps(preset.y());
        xRotationSteps = preset == null ? 0 : BlueprintTransform.normalizeSteps(preset.x());
        zRotationSteps = preset == null ? 0 : BlueprintTransform.normalizeSteps(preset.z());
    }

    private static void addEntry(Path path) {
        String fileName = path.getFileName().toString();
        try {
            byte[] data = Files.readAllBytes(path);
            RtsBlueprint blueprint = BlueprintReaders.parse(data, fileName, Minecraft.getInstance().level.registryAccess());
            ENTRIES.add(BlueprintEntry.from(path, fileName, blueprint, ""));
        } catch (Exception ex) {
            ENTRIES.add(BlueprintEntry.error(path, fileName, ex.getMessage()));
        }
    }

    private static void addOrReplaceEntry(Path path) {
        if (path == null || path.getFileName() == null) {
            return;
        }
        String fileName = path.getFileName().toString();
        ENTRIES.removeIf(entry -> entry.fileName().equals(fileName));
        addEntry(path);
        ENTRIES.sort(Comparator.comparing(BlueprintEntry::fileName, String.CASE_INSENSITIVE_ORDER));
        loaded = true;
    }

    private static void addOrReplaceEntry(Path path, RtsBlueprint blueprint) {
        if (path == null || path.getFileName() == null || blueprint == null) {
            return;
        }
        String fileName = path.getFileName().toString();
        ENTRIES.removeIf(entry -> entry.fileName().equals(fileName));
        ENTRIES.add(BlueprintEntry.from(path, fileName, blueprint, ""));
        ENTRIES.sort(Comparator.comparing(BlueprintEntry::fileName, String.CASE_INSENSITIVE_ORDER));
        loaded = true;
    }

    private static void importBlueprintFile() {
        String selected;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(5);
            filters.put(stack.UTF8("*.nbt"));
            filters.put(stack.UTF8("*.schem"));
            filters.put(stack.UTF8("*.schematic"));
            filters.put(stack.UTF8("*.litematic"));
            filters.put(stack.UTF8("*.json"));
            filters.flip();
            selected = TinyFileDialogs.tinyfd_openFileDialog(
                    text("screen.rtsbuilding.blueprints.import_file"),
                    null,
                    filters,
                    "Blueprint files",
                    false);
        }
        if (selected == null || selected.isBlank()) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.import_cancelled", "");
            return;
        }
        Path source = Path.of(selected);
        if (!Files.isRegularFile(source) || !isBlueprintFile(source)) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.invalid_file", "");
            return;
        }
        try {
            Files.createDirectories(blueprintFolder());
            Path dest = blueprintFolder().resolve(source.getFileName().toString());
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            reload();
            selectByFileName(dest.getFileName().toString());
            setStatus(S2CBlueprintStatusPayload.SUCCESS, "screen.rtsbuilding.blueprints.status.imported", dest.getFileName().toString());
        } catch (Exception ex) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.import_failed", ex.getMessage());
        }
    }

    private static void syncOtherModBlueprints() {
        List<Path> sourceFolders = otherModBlueprintFolders().stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::isDirectory)
                .distinct()
                .toList();
        if (sourceFolders.isEmpty()) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.create_sync_missing", "");
            return;
        }
        int copied = 0;
        int skipped = 0;
        int failed = 0;
        String lastCopied = "";
        try {
            Files.createDirectories(blueprintFolder());
            Map<String, Path> filesByName = new LinkedHashMap<>();
            for (Path sourceFolder : sourceFolders) {
                try (var stream = Files.walk(sourceFolder, 3)) {
                    stream.filter(Files::isRegularFile)
                            .filter(BlueprintPanelFiles::isSyncBlueprintFile)
                            .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                            .limit(512)
                            .forEach(path -> filesByName.putIfAbsent(path.getFileName().toString(), path));
                } catch (IOException ex) {
                    failed++;
                }
            }
            for (Map.Entry<String, Path> entry : filesByName.entrySet()) {
                Path dest = blueprintFolder().resolve(entry.getKey());
                if (Files.exists(dest)) {
                    skipped++;
                    continue;
                }
                try {
                    Files.copy(entry.getValue(), dest);
                    copied++;
                    lastCopied = entry.getKey();
                } catch (IOException ex) {
                    failed++;
                }
            }
            if (copied > 0) {
                reload();
                if (!lastCopied.isBlank()) {
                    selectByFileName(lastCopied);
                }
            }
            if (copied == 0 && skipped == 0 && failed == 0) {
                setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.create_sync_empty", "");
            } else if (failed > 0) {
                setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.create_sync_partial",
                        copied + "/" + skipped + "/" + failed);
            } else {
                setStatus(S2CBlueprintStatusPayload.SUCCESS, "screen.rtsbuilding.blueprints.status.create_sync_done",
                        copied + "/" + skipped);
            }
        } catch (Exception ex) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.create_sync_failed", ex.getMessage());
        }
    }

    private static void saveEntryAs(BlueprintEntry entry) {
        if (entry == null || !entry.error().isBlank()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.no_selection", "");
            return;
        }
        String sourceExtension = blueprintExtension(entry.fileName(), entry.format().extension());
        String defaultFileName = sanitizeFileBase(stripBlueprintExtension(entry.fileName())) + "." + sourceExtension;
        String selected;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*." + sourceExtension));
            filters.flip();
            selected = TinyFileDialogs.tinyfd_saveFileDialog(
                    text("screen.rtsbuilding.blueprints.save_as_title"),
                    blueprintFolder().resolve(defaultFileName).toString(),
                    filters,
                    "Blueprint files");
        }
        if (selected == null || selected.isBlank()) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.export_cancelled", "");
            return;
        }
        Path dest = ensureExtension(Path.of(selected), sourceExtension);
        try {
            Path parent = dest.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path source = entry.path();
            if (source != null && Files.isRegularFile(source)) {
                Path normalizedSource = source.toAbsolutePath().normalize();
                Path normalizedDest = dest.toAbsolutePath().normalize();
                if (!normalizedSource.equals(normalizedDest)) {
                    Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                BlueprintWriters.writeVanillaStructure(entry.blueprint(), dest);
            }
            setStatus(S2CBlueprintStatusPayload.SUCCESS, "screen.rtsbuilding.blueprints.status.exported",
                    dest.getFileName() == null ? dest.toString() : dest.getFileName().toString());
        } catch (Exception ex) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.export_failed", ex.getMessage());
        }
    }

    private static void renameEntry(BlueprintEntry entry, String requestedName) {
        if (entry == null || !ENTRIES.contains(entry)) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.no_selection", "");
            return;
        }
        Path source = entry.path();
        if (source == null || !Files.isRegularFile(source)) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.rename_failed", "Missing source file");
            return;
        }
        String extension = blueprintExtension(entry.fileName(), entry.format().extension());
        try {
            Files.createDirectories(blueprintFolder());
            Path dest = uniqueBlueprintPath(requestedName, extension, source);
            if (source.toAbsolutePath().normalize().equals(dest.toAbsolutePath().normalize())) {
                selectEntry(entry);
                return;
            }
            Files.move(source, dest);
            IOException rotationError = BlueprintRotationDefaults.rename(entry.fileName(), dest.getFileName().toString());
            reload();
            selectByFileName(dest.getFileName().toString());
            if (rotationError == null) {
                setStatus(S2CBlueprintStatusPayload.SUCCESS, "screen.rtsbuilding.blueprints.status.renamed",
                        dest.getFileName().toString());
            } else {
                setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.save_failed",
                        rotationError.getMessage());
            }
        } catch (Exception ex) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.rename_failed", ex.getMessage());
        }
    }

    private static void deleteEntry(BlueprintEntry entry) {
        if (entry == null || !ENTRIES.contains(entry)) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.no_selection", "");
            return;
        }
        boolean confirmed = TinyFileDialogs.tinyfd_messageBox(
                text("screen.rtsbuilding.blueprints.delete_confirm_title"),
                text("screen.rtsbuilding.blueprints.delete_confirm_message", entry.name()),
                "yesno",
                "warning",
                false);
        if (!confirmed) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.delete_cancelled", "");
            return;
        }
        try {
            Path source = entry.path();
            if (source != null) {
                Files.deleteIfExists(source);
            }
            IOException rotationError = BlueprintRotationDefaults.remove(entry.fileName());
            if (selectedEntry() == entry) {
                selectedIndex = -1;
                pinnedAnchor = null;
                materialDialogOpen = false;
            }
            reload();
            if (rotationError == null) {
                setStatus(S2CBlueprintStatusPayload.SUCCESS, "screen.rtsbuilding.blueprints.status.deleted", entry.name());
            } else {
                setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.save_failed",
                        rotationError.getMessage());
            }
        } catch (Exception ex) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.delete_failed", ex.getMessage());
        }
    }

    private static void toggleCaptureMode() {
        if (CAPTURE.isSaving()) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return;
        }
        if (CAPTURE.isActive()) {
            cancelCaptureMode();
        } else {
            CAPTURE.start(BlueprintPanel::setStatus);
            pinnedAnchor = null;
            materialDialogOpen = false;
            nameDialogMode = NameDialogMode.NONE;
            nameDialogValue = "";
            nameDialogEntry = null;
            nameDialogReplaceOnType = false;
            nameDialogCaptureBlockCount = 0L;
        }
    }

    public static void saveCapturedArea() {
        if (CAPTURE.isSaving()) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.save_failed", "No world");
            return;
        }
        if (!CAPTURE.isSelectionComplete()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
            return;
        }
        openCaptureNameDialog();
    }

    static void saveCapturedAreaAs(String requestedName) {
        if (!isCaptureSelectionComplete()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
            return;
        }
        String cleanName = sanitizeFileBase(stripBlueprintExtension(requestedName == null ? "" : requestedName));
        if (cleanName.isBlank()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.name_required", "");
            return;
        }
        startCaptureSave(cleanName);
    }

    private static void startCaptureSave(String requestedName) {
        if (CAPTURE.isSaving()) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.save_failed", "No world");
            return;
        }
        if (!CAPTURE.isSelectionComplete()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
            return;
        }
        String fileName = uniqueNbtFileName(requestedName);
        try {
            Path dest = blueprintFolder().resolve(fileName);
            CAPTURE.startSave(level, fileName, dest, BlueprintPanel::setStatus);
        } catch (Throwable throwable) {
            handleSaveFailure(throwable);
        }
    }

    static void cancelCaptureMode() {
        CAPTURE.cancel(BlueprintPanel::setStatus);
        nameDialogMode = NameDialogMode.NONE;
        nameDialogValue = "";
        nameDialogEntry = null;
        nameDialogReplaceOnType = false;
        nameDialogCaptureBlockCount = 0L;
    }

    private static void selectByFileName(String fileName) {
        for (int i = 0; i < ENTRIES.size(); i++) {
            if (ENTRIES.get(i).fileName().equals(fileName)) {
                selectedIndex = i;
                applyDefaultRotation(ENTRIES.get(i));
                return;
            }
        }
    }

    private static void openBlueprintFolder() {
        Path folder = blueprintFolder();
        try {
            Files.createDirectories(folder);
            Util.getPlatform().openFile(folder.toFile());
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.folder_opened", "");
        } catch (Exception ex) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.folder_failed", ex.getMessage());
        }
    }

    private static List<BlueprintEntry> filteredEntries() {
        if (search == null || search.isBlank()) {
            return List.copyOf(ENTRIES);
        }
        String query = search.toLowerCase(Locale.ROOT).trim();
        return ENTRIES.stream()
                .filter(entry -> entry.name().toLowerCase(Locale.ROOT).contains(query)
                        || entry.fileName().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private static BlueprintEntry selectedEntry() {
        return selectedIndex >= 0 && selectedIndex < ENTRIES.size() ? ENTRIES.get(selectedIndex) : null;
    }

    private static String shortPos(BlockPos pos) {
        return pos == null ? "-" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static void selectEntry(BlueprintEntry entry) {
        selectedIndex = ENTRIES.indexOf(entry);
        pinnedAnchor = null;
        materialDialogOpen = false;
        materialDialogScroll = 0;
        applyDefaultRotation(entry);
        setStatus(
                entry.error().isBlank() ? S2CBlueprintStatusPayload.INFO : S2CBlueprintStatusPayload.ERROR,
                entry.error().isBlank()
                        ? "screen.rtsbuilding.blueprints.status.selected"
                        : "screen.rtsbuilding.blueprints.status.parse_failed",
                entry.error().isBlank() ? entry.name() : entry.error());
    }

    static void clearSelectedBlueprint() {
        selectedIndex = -1;
        pinnedAnchor = null;
        pinnedNudgeForward = Direction.SOUTH;
        yRotationSteps = 0;
        xRotationSteps = 0;
        zRotationSteps = 0;
        materialDialogOpen = false;
        materialDialogScroll = 0;
        setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.preview_cleared", "");
    }

    public record BlueprintGhostBlock(BlockPos pos, BlockState state, boolean missing) {
    }

    public record BlueprintGhostPreview(List<BlueprintGhostBlock> blocks, boolean materialsReady, boolean truncated) {
        public static final BlueprintGhostPreview EMPTY = new BlueprintGhostPreview(List.of(), false, false);
    }

    private enum NameDialogMode {
        NONE,
        CAPTURE_SAVE,
        RENAME_ENTRY
    }

}
