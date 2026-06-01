package com.rtsbuilding.rtsbuilding.blueprint.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintParseException;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintTransform;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.blueprint.format.BlueprintReaders;
import com.rtsbuilding.rtsbuilding.blueprint.format.BlueprintWriters;
import com.rtsbuilding.rtsbuilding.blueprint.network.C2SBlueprintPlacePayload;
import com.rtsbuilding.rtsbuilding.blueprint.network.S2CBlueprintStatusPayload;
import com.rtsbuilding.rtsbuilding.client.ClientRtsController;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.FMLPaths;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

public final class BlueprintPanel {
    private static final int ROW_H = 24;
    private static final int BUTTON_H = 14;
    private static final int SEARCH_H = 14;
    private static final int DETAIL_BUTTON_H = 14;
    private static final int LIST_COLUMN_GAP = 4;
    private static final int CAPTURE_SCAN_BUDGET_PER_STEP = 64;
    private static final long CAPTURE_SCAN_TIME_BUDGET_NANOS = 500_000L;
    private static final long CAPTURE_SCAN_MIN_INTERVAL_MS = 15L;
    private static final long CAPTURE_STATUS_INTERVAL_MS = 250L;
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
    private static int yRotationSteps = 0;
    private static int xRotationSteps = 0;
    private static int zRotationSteps = 0;
    private static BlockPos pinnedAnchor = null;
    private static Direction pinnedNudgeForward = Direction.SOUTH;
    private static boolean captureMode = false;
    private static boolean capturePreviewVisible = true;
    private static BlockPos capturePointA = null;
    private static BlockPos capturePointB = null;
    private static BlockPos captureHoverPoint = null;
    private static CaptureSaveJob captureSaveJob = null;
    private static final Set<BlockPos> captureExcludedBlocks = new HashSet<>();
    private static boolean defaultsLoaded = false;
    private static final Map<String, RotationPreset> DEFAULT_ROTATIONS = new HashMap<>();
    private static String search = "";
    private static Component statusText = Component.translatable("screen.rtsbuilding.blueprints.status.ready");
    private static int statusColor = 0xFFB8C7D6;

    private BlueprintPanel() {
    }

    public static void render(GuiGraphics g, Font font, ClientRtsController controller,
            int x, int y, int w, int h, int mouseX, int mouseY) {
        if (!Config.areBlueprintsEnabled()) {
            captureSaveJob = null;
            renderDisabled(g, font, x, y, w, h);
            return;
        }
        tickCaptureSaveJob();
        ensureLoaded();

        int buttonY = y;
        TopBarLayout top = topBarLayout(font, x, w);
        drawButton(g, font, top.folderX(), buttonY, top.folderW(), BUTTON_H, text("screen.rtsbuilding.blueprints.open_folder_short"),
                inside(mouseX, mouseY, top.folderX(), buttonY, top.folderW(), BUTTON_H));
        drawButton(g, font, top.importX(), buttonY, top.importW(), BUTTON_H, text("screen.rtsbuilding.blueprints.import_file_short"),
                inside(mouseX, mouseY, top.importX(), buttonY, top.importW(), BUTTON_H));
        drawButton(g, font, top.captureX(), buttonY, top.captureW(), BUTTON_H,
                text(captureMode ? "screen.rtsbuilding.blueprints.capture_active_short" : "screen.rtsbuilding.blueprints.capture_short"),
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
        if (captureMode) {
            renderCaptureLockedBottom(g, font, x, listY, w, listH);
            g.drawString(font, trim(font, statusText.getString(), w - 8), x + 2, statusY, statusColor, false);
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
        TopBarLayout top = topBarLayout(Minecraft.getInstance().font, x, w);
        if (inside(mouseX, mouseY, top.folderX(), y, top.folderW(), BUTTON_H)) {
            openBlueprintFolder();
            return true;
        }
        if (inside(mouseX, mouseY, top.importX(), y, top.importW(), BUTTON_H)) {
            importBlueprintFile();
            return true;
        }
        if (inside(mouseX, mouseY, top.captureX(), y, top.captureW(), BUTTON_H)) {
            toggleCaptureMode();
            return true;
        }
        if (captureMode) {
            searchFocused = false;
            setStatus(S2CBlueprintStatusPayload.INFO,
                    captureSaveJob == null
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

    public static void renderNameDialog(GuiGraphics g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        if (!isNameDialogOpen()) {
            return;
        }
        NameDialogLayout layout = nameDialogLayout(screenW, screenH);
        boolean capture = nameDialogMode == NameDialogMode.CAPTURE_SAVE;
        g.fill(0, 0, screenW, screenH, 0x66000000);
        drawFrame(g, layout.x(), layout.y(), layout.w(), layout.h(), 0xEE121922, 0xFF6E8799, 0xFF0B0E13);
        g.fill(layout.x() + 1, layout.y() + 1, layout.x() + layout.w() - 1, layout.y() + 26, 0xD8293440);
        String title = capture
                ? text("screen.rtsbuilding.blueprints.name_dialog_capture_title")
                : text("screen.rtsbuilding.blueprints.name_dialog_rename_title");
        g.drawString(font, trim(font, title, layout.w() - 24), layout.x() + 10, layout.y() + 9, 0xFFEAF2FF, false);

        int textY = layout.y() + 34;
        if (capture) {
            g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.capture_preview_title"), layout.w() - 20),
                    layout.x() + 10, textY, 0xFFCDEBFF, false);
            textY += 12;
            g.drawString(font, trim(font, capturePreviewSummaryLine(), layout.w() - 20),
                    layout.x() + 10, textY, 0xFFB8FFB8, false);
            textY += 14;
        } else if (nameDialogEntry != null) {
            g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.name_dialog_current", nameDialogEntry.name()),
                    layout.w() - 20), layout.x() + 10, textY, 0xFF9EACB9, false);
            textY += 14;
        }

        g.drawString(font, text("screen.rtsbuilding.blueprints.name_dialog_label"), layout.inputX(), layout.inputY() - 11,
                0xFFB7CDE2, false);
        drawFrame(g, layout.inputX(), layout.inputY(), layout.inputW(), 18, 0xDD05070B, 0xFF8BA4B8, 0xFF0B0E13);
        String value = nameDialogValue + ((Util.getMillis() / 500L) % 2L == 0L ? "_" : "");
        g.drawString(font, trim(font, value, layout.inputW() - 8), layout.inputX() + 4, layout.inputY() + 5,
                0xFFEAF2FF, false);

        drawButton(g, font, layout.confirmX(), layout.buttonY(), layout.confirmW(), DETAIL_BUTTON_H,
                text("screen.rtsbuilding.blueprints.name_dialog_confirm"),
                inside(mouseX, mouseY, layout.confirmX(), layout.buttonY(), layout.confirmW(), DETAIL_BUTTON_H));
        drawButton(g, font, layout.cancelX(), layout.buttonY(), layout.cancelW(), DETAIL_BUTTON_H,
                text("screen.rtsbuilding.blueprints.name_dialog_cancel"),
                inside(mouseX, mouseY, layout.cancelX(), layout.buttonY(), layout.cancelW(), DETAIL_BUTTON_H));
    }

    public static boolean mouseClickedNameDialog(double mouseX, double mouseY, int button, int screenW, int screenH) {
        if (!isNameDialogOpen()) {
            return false;
        }
        if (button != org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        NameDialogLayout layout = nameDialogLayout(screenW, screenH);
        if (inside(mouseX, mouseY, layout.confirmX(), layout.buttonY(), layout.confirmW(), DETAIL_BUTTON_H)) {
            confirmNameDialog();
            return true;
        }
        if (inside(mouseX, mouseY, layout.cancelX(), layout.buttonY(), layout.cancelW(), DETAIL_BUTTON_H)
                || !inside(mouseX, mouseY, layout.x(), layout.y(), layout.w(), layout.h())) {
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
            if (!nameDialogValue.isEmpty()) {
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
        int w = Math.min(560, Math.max(300, screenW - 48));
        int h = Math.min(320, Math.max(188, screenH - 70));
        int x = (screenW - w) / 2;
        int y = Math.max(24, (screenH - h) / 2);
        int closeSize = 18;

        g.fill(0, 0, screenW, screenH, 0x66000000);
        drawFrame(g, x, y, w, h, 0xEE121922, 0xFF6E8799, 0xFF0B0E13);
        g.fill(x + 1, y + 1, x + w - 1, y + 26, 0xD8293440);
        g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.details_title"), w - 70), x + 10, y + 9,
                0xFFEAF2FF, false);
        drawButton(g, font, x + w - closeSize - 6, y + 4, closeSize, closeSize, "x",
                inside(mouseX, mouseY, x + w - closeSize - 6, y + 4, closeSize, closeSize));

        g.drawString(font, trim(font, entry.name(), w - 20), x + 10, y + 35, 0xFFEAF2FF, false);
        BuildStats stats = buildStats(entry, controller);
        List<DetailLine> lines = detailLines(entry, controller);
        boolean creativeBypass = isCreativePlayer() && lines.isEmpty();
        String summary = creativeBypass
                ? text("screen.rtsbuilding.blueprints.materials_creative")
                : lines.isEmpty()
                        ? text("screen.rtsbuilding.blueprints.materials_all_ready")
                        : text("screen.rtsbuilding.blueprints.details_summary",
                                stats.percent(),
                                stats.buildable(),
                                stats.total(),
                                stats.missingTypes(),
                                stats.unsupportedTypes(),
                                stats.missingBlockTypes());
        int summaryColor = creativeBypass || lines.isEmpty() ? 0xFF8EEA9B : 0xFFFFC06C;
        g.drawString(font, trim(font, summary, w - 20), x + 10, y + 48, summaryColor, false);

        int listX = x + 10;
        int listY = y + 64;
        int listW = w - 20;
        int listH = h - 76;
        drawFrame(g, listX, listY, listW, listH, 0x99101620, 0xFF415266, 0xFF0B0E13);
        if (creativeBypass || lines.isEmpty()) {
            String message = creativeBypass
                    ? text("screen.rtsbuilding.blueprints.materials_creative")
                    : text("screen.rtsbuilding.blueprints.materials_all_ready");
            g.drawString(font, trim(font, message, listW - 14), listX + 7, listY + 8, summaryColor, false);
            return;
        }

        int rowH = 22;
        int visible = Math.max(1, listH / rowH);
        int maxScroll = Math.max(0, lines.size() - visible);
        materialDialogScroll = Mth.clamp(materialDialogScroll, 0, maxScroll);
        for (int row = 0; row < visible; row++) {
            int index = materialDialogScroll + row;
            if (index >= lines.size()) {
                break;
            }
            DetailLine line = lines.get(index);
            int rowY = listY + 3 + row * rowH;
            if (inside(mouseX, mouseY, listX, rowY, listW, rowH)) {
                g.fill(listX + 1, rowY, listX + listW - 1, rowY + rowH, 0x66324126);
            }
            if (!line.preview().isEmpty()) {
                g.renderItem(line.preview(), listX + 5, rowY + 2);
            } else {
                g.fill(listX + 7, rowY + 4, listX + 21, rowY + 18, 0xAA36506A);
                g.drawCenteredString(font, "?", listX + 14, rowY + 6, 0xFFFFD080);
            }
            g.drawString(font, trim(font, line.label(), listW - 132), listX + 27, rowY + 2, 0xFFEAF2FF, false);
            g.drawString(font, trim(font, line.detail(), 102), listX + listW - 108, rowY + 7, line.color(), false);
        }
        if (maxScroll > 0) {
            int barX = listX + listW - 5;
            int barY = listY + 3;
            int barH = listH - 6;
            int thumbH = Math.max(12, barH * visible / Math.max(visible, lines.size()));
            int thumbY = barY + (barH - thumbH) * materialDialogScroll / maxScroll;
            g.fill(barX, barY, barX + 2, barY + barH, 0x66566A7C);
            g.fill(barX - 1, thumbY, barX + 3, thumbY + thumbH, 0xFF8EA5B8);
        }
    }

    public static boolean mouseClickedMaterialDialog(double mouseX, double mouseY, int button, int screenW, int screenH) {
        if (!materialDialogOpen) {
            return false;
        }
        if (button != org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        int w = Math.min(560, Math.max(300, screenW - 48));
        int h = Math.min(320, Math.max(188, screenH - 70));
        int x = (screenW - w) / 2;
        int y = Math.max(24, (screenH - h) / 2);
        int closeSize = 18;
        if (!inside(mouseX, mouseY, x, y, w, h)
                || inside(mouseX, mouseY, x + w - closeSize - 6, y + 4, closeSize, closeSize)) {
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
        int h = Math.min(320, Math.max(188, screenH - 70));
        int listH = h - 76;
        int visible = Math.max(1, listH / 22);
        int maxScroll = Math.max(0, detailLines(entry, controller).size() - visible);
        materialDialogScroll = Mth.clamp(materialDialogScroll + (scrollY > 0.0D ? -1 : 1), 0, maxScroll);
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
        materialDialogOpen = false;
        searchFocused = false;
    }

    private static void cancelNameDialog() {
        NameDialogMode previous = nameDialogMode;
        nameDialogMode = NameDialogMode.NONE;
        nameDialogValue = "";
        nameDialogEntry = null;
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
        if (mode == NameDialogMode.CAPTURE_SAVE) {
            startCaptureSave(cleanName);
        } else if (mode == NameDialogMode.RENAME_ENTRY) {
            renameEntry(entry, cleanName);
        }
    }

    public static void renderPlacementHud(GuiGraphics g, Font font, ClientRtsController controller,
            int screenW, int screenH, int mouseX, int mouseY, int topSafeY, int bottomSafeY) {
        if (!Config.areBlueprintsEnabled() || captureMode) {
            return;
        }
        BlueprintEntry entry = selectedEntry();
        if (entry == null || !entry.error().isBlank()) {
            return;
        }

        BuildStats stats = buildStats(entry, controller);
        int topW = Math.min(500, Math.max(290, screenW - 32));
        int topH = 34;
        int topX = (screenW - topW) / 2;
        int topY = topSafeY;
        drawFrame(g, topX, topY, topW, topH, 0xDD101820, 0xFF5B7894, 0xFF0B0F14);
        g.drawString(font, trim(font, entry.name(), topW - 184), topX + 8, topY + 6, 0xFFEAF2FF, false);
        g.drawString(font, trim(font, materialSummary(entry, controller, stats), topW - 184), topX + 8, topY + 18,
                stats.percent() >= 100 ? 0xFF8EEA9B : 0xFFFFC06C, false);
        int buttonY = topY + 8;
        int detailsX = topX + topW - 158;
        int defaultX = detailsX + 52;
        int saveX = defaultX + 52;
        drawButton(g, font, detailsX, buttonY, 48, DETAIL_BUTTON_H,
                text("screen.rtsbuilding.blueprints.details"),
                inside(mouseX, mouseY, detailsX, buttonY, 48, DETAIL_BUTTON_H));
        drawButton(g, font, defaultX, buttonY, 48, DETAIL_BUTTON_H,
                text("screen.rtsbuilding.blueprints.default_rotation_short"),
                inside(mouseX, mouseY, defaultX, buttonY, 48, DETAIL_BUTTON_H));
        drawButton(g, font, saveX, buttonY, 48, DETAIL_BUTTON_H,
                text("screen.rtsbuilding.blueprints.save_rotated_short"),
                inside(mouseX, mouseY, saveX, buttonY, 48, DETAIL_BUTTON_H));

        int barW = Math.max(286, Math.min(560, screenW - 32));
        int barH = 24;
        int barX = (screenW - barW) / 2;
        int barY = Math.max(topY + topH + 6, bottomSafeY - barH - 8);
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
        int buildX = barX + barW - buildW - cancelW - gap - 6;
        int cancelX = buildX + buildW + gap;
        drawButton(g, font, buildX, barY + 5, buildW, DETAIL_BUTTON_H,
                text("screen.rtsbuilding.blueprints.build_preview"),
                inside(mouseX, mouseY, buildX, barY + 5, buildW, DETAIL_BUTTON_H), pinnedAnchor != null);
        drawButton(g, font, cancelX, barY + 5, cancelW, DETAIL_BUTTON_H,
                text("screen.rtsbuilding.blueprints.capture_cancel"),
                inside(mouseX, mouseY, cancelX, barY + 5, cancelW, DETAIL_BUTTON_H));
    }

    public static boolean mouseClickedPlacementHud(double mouseX, double mouseY, int screenW, int screenH,
            int topSafeY, int bottomSafeY, ClientRtsController controller) {
        if (!Config.areBlueprintsEnabled() || captureMode || !hasSelectedBlueprint()) {
            return false;
        }

        int topW = Math.min(500, Math.max(290, screenW - 32));
        int topH = 34;
        int topX = (screenW - topW) / 2;
        int topY = topSafeY;
        int buttonY = topY + 8;
        int detailsX = topX + topW - 158;
        int defaultX = detailsX + 52;
        int saveX = defaultX + 52;
        if (inside(mouseX, mouseY, detailsX, buttonY, 48, DETAIL_BUTTON_H)) {
            materialDialogOpen = true;
            materialDialogScroll = 0;
            return true;
        }
        if (inside(mouseX, mouseY, defaultX, buttonY, 48, DETAIL_BUTTON_H)) {
            saveCurrentRotationAsDefault();
            return true;
        }
        if (inside(mouseX, mouseY, saveX, buttonY, 48, DETAIL_BUTTON_H)) {
            saveRotatedCopy();
            return true;
        }
        if (inside(mouseX, mouseY, topX, topY, topW, topH)) {
            return true;
        }

        int barW = Math.max(286, Math.min(560, screenW - 32));
        int barH = 24;
        int barX = (screenW - barW) / 2;
        int barY = Math.max(topY + topH + 6, bottomSafeY - barH - 8);
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
        int buildX = barX + barW - buildW - cancelW - gap - 6;
        int cancelX = buildX + buildW + gap;
        if (inside(mouseX, mouseY, buildX, barY + 5, buildW, DETAIL_BUTTON_H)) {
            return buildPinnedPreview();
        }
        if (inside(mouseX, mouseY, cancelX, barY + 5, cancelW, DETAIL_BUTTON_H)) {
            pinnedAnchor = null;
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.preview_cleared", "");
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

    public static boolean keyPressed(int keyCode, ClientRtsController controller) {
        if (!Config.areBlueprintsEnabled()) {
            searchFocused = false;
            return false;
        }
        if (captureMode) {
            searchFocused = false;
            if (captureSaveJob != null) {
                setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
                return true;
            }
            int step = org.lwjgl.glfw.GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(),
                    org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                    || org.lwjgl.glfw.GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(),
                            org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                    ? 4 : 1;
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                cancelCaptureMode();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                saveCapturedArea();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
                expandCaptureVertical(step);
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN) {
                expandCaptureVertical(-step);
                return true;
            }
            return true;
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

    public static boolean isCaptureModeActive() {
        return Config.areBlueprintsEnabled() && captureMode;
    }

    public static boolean isCaptureSelectionComplete() {
        return Config.areBlueprintsEnabled() && captureMode && capturePointA != null && capturePointB != null;
    }

    public static boolean hasPinnedPreview() {
        return Config.areBlueprintsEnabled() && pinnedAnchor != null && hasSelectedBlueprint();
    }

    public static BlockPos getCapturePointA() {
        return capturePointA;
    }

    public static BlockPos getCapturePointB() {
        return capturePointB;
    }

    public static void updateCaptureHoverPoint(BlockPos pos) {
        captureHoverPoint = pos == null ? null : pos.immutable();
    }

    public static BlockPos getCapturePreviewPointB() {
        if (capturePointB != null) {
            return capturePointB;
        }
        return captureHoverPoint != null ? captureHoverPoint : capturePointA;
    }

    public static boolean shouldRenderCapturePreviewFill() {
        return Config.areBlueprintsEnabled() && captureMode && capturePreviewVisible && capturePointB != null;
    }

    public static List<BlockPos> getCaptureIncludedBlocksForRender(int limit) {
        if (!shouldRenderCaptureBlockHighlights(limit)) {
            return List.of();
        }
        long volume = captureVolume();
        List<BlockPos> blocks = new ArrayList<>((int) volume);
        int minX = Math.min(capturePointA.getX(), capturePointB.getX());
        int minY = Math.min(capturePointA.getY(), capturePointB.getY()) + 1;
        int minZ = Math.min(capturePointA.getZ(), capturePointB.getZ());
        int maxX = Math.max(capturePointA.getX(), capturePointB.getX());
        int maxY = Math.max(capturePointA.getY(), capturePointB.getY());
        int maxZ = Math.max(capturePointA.getZ(), capturePointB.getZ());
        if (minY > maxY) {
            return List.of();
        }
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!captureExcludedBlocks.contains(pos)) {
                        blocks.add(pos);
                    }
                }
            }
        }
        return blocks;
    }

    public static boolean shouldRenderCaptureBlockHighlights(int limit) {
        if (!shouldRenderCapturePreviewFill() || limit <= 0 || capturePointA == null || capturePointB == null) {
            return false;
        }
        long volume = captureVolume();
        return volume > 0L && volume <= limit;
    }

    public static List<BlockPos> getCaptureExcludedBlocksForRender(int limit) {
        if (!Config.areBlueprintsEnabled() || !captureMode || capturePointA == null || capturePointB == null || limit <= 0) {
            return List.of();
        }
        List<BlockPos> blocks = new ArrayList<>(Math.min(limit, captureExcludedBlocks.size()));
        for (BlockPos pos : captureExcludedBlocks) {
            if (blocks.size() >= limit) {
                break;
            }
            if (isInsideCaptureSelection(pos)) {
                blocks.add(pos);
            }
        }
        return blocks;
    }

    public static boolean acceptCapturePoint(BlockPos pos) {
        if (!Config.areBlueprintsEnabled() || !captureMode || pos == null) {
            return false;
        }
        if (captureSaveJob != null) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return true;
        }
        if (capturePointB != null) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_adjust_hint", captureSizeText());
            return false;
        }
        if (capturePointA == null) {
            capturePointA = pos.immutable();
            capturePointB = null;
            captureHoverPoint = pos.immutable();
            captureExcludedBlocks.clear();
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_a", shortPos(capturePointA));
        } else {
            capturePointB = pos.immutable();
            captureHoverPoint = capturePointB;
            captureExcludedBlocks.clear();
            setStatus(S2CBlueprintStatusPayload.SUCCESS, "screen.rtsbuilding.blueprints.status.capture_b", captureSizeText());
        }
        return true;
    }

    public static boolean toggleCaptureBlockExclusion(BlockPos pos) {
        if (!Config.areBlueprintsEnabled() || !captureMode || capturePointA == null || capturePointB == null || pos == null) {
            return false;
        }
        if (captureSaveJob != null) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return true;
        }
        if (!isInsideCaptureSelection(pos)) {
            return false;
        }
        BlockPos key = pos.immutable();
        if (captureExcludedBlocks.remove(key)) {
            setStatus(S2CBlueprintStatusPayload.SUCCESS, "screen.rtsbuilding.blueprints.status.capture_block_included", shortPos(key));
        } else {
            captureExcludedBlocks.add(key);
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_block_excluded", shortPos(key));
        }
        return true;
    }

    public static boolean cancelCaptureFromClick() {
        if (!Config.areBlueprintsEnabled() || !captureMode) {
            return false;
        }
        if (captureSaveJob != null) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return true;
        }
        cancelCaptureMode();
        return true;
    }

    public static void renderCaptureOverlay(GuiGraphics g, Font font, int screenW, int screenH, int mouseX, int mouseY,
            int topSafeY) {
        if (!Config.areBlueprintsEnabled() || !captureMode) {
            return;
        }
        tickCaptureSaveJob();
        int infoW = Math.min(420, Math.max(270, screenW - 32));
        int infoH = capturePointB == null && captureSaveJob == null ? 40 : 46;
        int infoX = (screenW - infoW) / 2;
        int infoY = topSafeY;
        drawFrame(g, infoX, infoY, infoW, infoH, 0xDD101820, 0xFF5B7894, 0xFF0B0F14);
        g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.capture_tool_title"), infoW - 12),
                infoX + 6, infoY + 6, 0xFFEAF2FF, false);
        String state = capturePointA == null
                ? text("screen.rtsbuilding.blueprints.capture_waiting_a")
                : capturePointB == null
                        ? text("screen.rtsbuilding.blueprints.capture_waiting_b")
                        : text("screen.rtsbuilding.blueprints.capture_ready");
        if (captureSaveJob != null) {
            state = captureSaveJob.statusLine();
        }
        String sizeLine = capturePointA == null
                ? state
                : text("screen.rtsbuilding.blueprints.capture_live_size", capturePreviewSizeText());
        g.drawString(font, trim(font, sizeLine + "  " + state, infoW - 112), infoX + 6, infoY + 20,
                capturePointA != null && capturePointB != null ? 0xFF8EEA9B : 0xFFFFC06C, false);
        if (capturePointB == null && captureSaveJob == null) {
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
        if (captureSaveJob != null) {
            g.drawString(font, trim(font, captureSaveJob.progressLine(), infoW - 12), infoX + 6, infoY + 33,
                    0xFFB7CDE2, false);
        }

        int leftW = 92;
        int leftH = 90;
        int leftX = 10;
        int leftY = topSafeY;
        drawFrame(g, leftX, leftY, leftW, leftH, 0xDD101820, 0xFF5B7894, 0xFF0B0F14);
        g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.capture_controls"), leftW - 12),
                leftX + 6, leftY + 6, 0xFFEAF2FF, false);

        int buttonX = leftX + 8;
        int buttonW = leftW - 16;
        int rowY = leftY + 22;
        drawButton(g, font, buttonX, rowY, buttonW, DETAIL_BUTTON_H,
                text("screen.rtsbuilding.blueprints.capture_move_up"),
                inside(mouseX, mouseY, buttonX, rowY, buttonW, DETAIL_BUTTON_H));
        rowY += 18;
        drawButton(g, font, buttonX, rowY, buttonW, DETAIL_BUTTON_H,
                text("screen.rtsbuilding.blueprints.capture_move_down"),
                inside(mouseX, mouseY, buttonX, rowY, buttonW, DETAIL_BUTTON_H));
        rowY += 18;
        drawButton(g, font, buttonX, rowY, buttonW, DETAIL_BUTTON_H,
                text("screen.rtsbuilding.blueprints.capture_preview"),
                inside(mouseX, mouseY, buttonX, rowY, buttonW, DETAIL_BUTTON_H),
                capturePreviewVisible);
        g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.capture_page_hint"), leftW - 12),
                leftX + 6, leftY + leftH - 15, 0xFFB7CDE2, false);
        renderCapturePreviewCard(g, font, screenW, screenH);
    }

    private static void renderCapturePreviewCard(GuiGraphics g, Font font, int screenW, int screenH) {
        if (capturePointA == null || capturePointB == null) {
            return;
        }
        int w = Math.min(230, Math.max(170, screenW / 5));
        int h = 52;
        int x = Math.max(10, (screenW - w) / 2);
        int y = Math.max(72, screenH - h - 132);
        drawFrame(g, x, y, w, h, 0xDD101820, 0xFF5B7894, 0xFF0B0F14);
        g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.capture_preview_title"), w - 14),
                x + 7, y + 6, 0xFFEAF2FF, false);
        g.drawString(font, trim(font, capturePreviewSummaryLine(), w - 14), x + 7, y + 18, 0xFFB8FFB8, false);
        int miniX = x + 8;
        int miniY = y + 34;
        int miniW = w - 16;
        int miniH = 8;
        g.fill(miniX, miniY, miniX + miniW, miniY + miniH, 0xAA0C1118);
        long volume = Math.max(1L, captureVolume());
        int fillW = (int) Math.max(1L, Math.min(miniW, volume * miniW / Math.max(1L, BlueprintWriters.maxCaptureBlocks())));
        g.fill(miniX, miniY, miniX + fillW, miniY + miniH, 0x8857D9FF);
        drawFrame(g, miniX, miniY, miniW, miniH, 0x00000000, 0xFF5B7894, 0xFF0B0F14);
    }

    public static boolean mouseClickedCaptureOverlay(double mouseX, double mouseY, int screenW, int screenH, int topSafeY) {
        if (!Config.areBlueprintsEnabled() || !captureMode) {
            return false;
        }
        if (capturePointB == null && captureSaveJob == null) {
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
        if (inside(mouseX, mouseY, infoX, infoY, infoW, captureSaveJob == null ? 46 : 58)) {
            return true;
        }
        int leftW = 92;
        int leftX = 10;
        int leftY = topSafeY;
        int buttonX = leftX + 8;
        int buttonW = leftW - 16;
        int rowY = leftY + 22;
        if (captureSaveJob != null) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return inside(mouseX, mouseY, leftX, leftY, leftW, 126);
        }
        if (inside(mouseX, mouseY, buttonX, rowY, buttonW, DETAIL_BUTTON_H)) {
            moveCaptureSelection(1);
            return true;
        }
        rowY += 18;
        if (inside(mouseX, mouseY, buttonX, rowY, buttonW, DETAIL_BUTTON_H)) {
            moveCaptureSelection(-1);
            return true;
        }
        rowY += 18;
        if (inside(mouseX, mouseY, buttonX, rowY, buttonW, DETAIL_BUTTON_H)) {
            capturePreviewVisible = !capturePreviewVisible;
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_preview", "");
            return true;
        }
        return inside(mouseX, mouseY, leftX, leftY, leftW, 90);
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
        for (RtsBlueprintBlock block : entry.blueprint().blocks()) {
            BlockPos pos = anchor.offset(BlueprintTransform.rotate(block.relativePos(), y, x, z)).immutable();
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

    private static void saveCurrentRotationAsDefault() {
        BlueprintEntry entry = selectedEntry();
        if (entry == null || !entry.error().isBlank()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.no_selection", "");
            return;
        }
        rememberCurrentRotationAsDefault();
        setStatus(S2CBlueprintStatusPayload.SUCCESS, "screen.rtsbuilding.blueprints.status.default_rotation_saved", entry.name());
    }

    private static void rememberCurrentRotationAsDefault() {
        BlueprintEntry entry = selectedEntry();
        if (entry == null || !entry.error().isBlank()) {
            return;
        }
        DEFAULT_ROTATIONS.put(entry.fileName(), new RotationPreset(yRotationSteps, xRotationSteps, zRotationSteps));
        saveDefaultRotations();
    }

    private static boolean nudgePinnedAnchor(int dx, int dy, int dz, ClientRtsController controller) {
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

    private static boolean nudgePinnedAnchorRelative(int rightSteps, int forwardSteps, int upSteps,
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

    private static void saveRotatedCopy() {
        BlueprintEntry entry = selectedEntry();
        if (entry == null || !entry.error().isBlank()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.no_selection", "");
            return;
        }
        String defaultName = sanitizeFileBase(entry.name()) + "_rot_y" + (yRotationSteps * 90)
                + "_x" + (xRotationSteps * 90)
                + "_z" + (zRotationSteps * 90);
        String requested = TinyFileDialogs.tinyfd_inputBox(
                text("screen.rtsbuilding.blueprints.save_rotated"),
                text("screen.rtsbuilding.blueprints.save_name_prompt"),
                defaultName);
        if (requested == null || requested.isBlank()) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_cancelled", "");
            return;
        }
        String fileName = uniqueNbtFileName(sanitizeFileBase(requested));
        try {
            RtsBlueprint rotated = BlueprintWriters.rotatedCopy(
                    entry.blueprint(),
                    yRotationSteps,
                    xRotationSteps,
                    zRotationSteps,
                    stripNbtExtension(fileName),
                    fileName);
            Path dest = blueprintFolder().resolve(fileName);
            BlueprintWriters.writeVanillaStructure(rotated, dest);
            reload();
            selectByFileName(fileName);
            setStatus(S2CBlueprintStatusPayload.SUCCESS, "screen.rtsbuilding.blueprints.status.saved_blueprint", fileName);
        } catch (Throwable throwable) {
            handleSaveFailure(throwable);
        }
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
        CaptureSaveJob job = captureSaveJob;
        if (job == null) {
            return;
        }
        CaptureSaveResult result = job.tick();
        if (result == null) {
            return;
        }

        captureSaveJob = null;
        if (!result.success()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, result.messageKey(), result.detail());
            return;
        }

        captureMode = false;
        capturePointA = null;
        capturePointB = null;
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
        textY += 14;
        g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.capture_point_a", shortPos(capturePointA)), w - 16),
                textX, textY, 0xFFCDEBFF, false);
        textY += 12;
        g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.capture_point_b", shortPos(capturePointB)), w - 16),
                textX, textY, 0xFFCDEBFF, false);
        textY += 12;
        g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.capture_size", captureSizeText()), w - 16),
                textX, textY, 0xFFB8FFB8, false);
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

        int controlsY = y + 54;
        int gap = 4;
        int leftX = x + 6;
        int halfW = Math.max(42, (w - 16) / 2);
        int detailsX = leftX + halfW + gap;
        drawButton(g, font, leftX, controlsY, halfW, DETAIL_BUTTON_H,
                text("screen.rtsbuilding.blueprints.details"),
                inside(mouseX, mouseY, leftX, controlsY, halfW, DETAIL_BUTTON_H));
        drawButton(g, font, detailsX, controlsY, Math.max(42, w - (detailsX - x) - 6), DETAIL_BUTTON_H,
                text("screen.rtsbuilding.blueprints.clear_preview"),
                inside(mouseX, mouseY, detailsX, controlsY, w - (detailsX - x) - 6, DETAIL_BUTTON_H));

        String anchor = pinnedAnchor == null
                ? text("screen.rtsbuilding.blueprints.bottom_place_hint")
                : text("screen.rtsbuilding.blueprints.preview_pinned", shortPos(pinnedAnchor));
        g.drawString(font, trim(font, anchor, w - 12), x + 6, controlsY + 19, 0xFF9EACB9, false);
        g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.rotation_state",
                yRotationSteps * 90,
                xRotationSteps * 90,
                zRotationSteps * 90), w - 12), x + 6, controlsY + 31, 0xFF9EACB9, false);

        int contentY = controlsY + 45;
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
        int controlsY = y + 54;
        int gap = 4;
        int leftX = x + 6;
        int halfW = Math.max(42, (w - 16) / 2);
        int detailsX = leftX + halfW + gap;
        if (inside(mouseX, mouseY, leftX, controlsY, halfW, DETAIL_BUTTON_H)) {
            materialDialogOpen = true;
            materialDialogScroll = 0;
            return true;
        }
        if (inside(mouseX, mouseY, detailsX, controlsY, w - (detailsX - x) - 6, DETAIL_BUTTON_H)) {
            pinnedAnchor = null;
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.preview_cleared", "");
            return true;
        }

        return true;
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

    private static void renderMaterialDetails(GuiGraphics g, Font font, BlueprintEntry entry, ClientRtsController controller,
            int x, int y, int w, int h) {
        List<MaterialLine> lines = missingMaterialLines(entry, controller);
        if (isCreativePlayer()) {
            g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.materials_creative"), w), x, y, 0xFF8EEA9B, false);
            return;
        }
        if (lines.isEmpty()) {
            g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.materials_all_ready"), w), x, y, 0xFF8EEA9B, false);
            return;
        }
        int rowH = 18;
        int visible = Math.max(1, h / rowH);
        for (int i = 0; i < lines.size() && i < visible; i++) {
            MaterialLine line = lines.get(i);
            int rowY = y + i * rowH;
            g.renderItem(line.preview(), x, rowY);
            g.drawString(font, trim(font, line.label(), w - 20), x + 20, rowY + 1, 0xFFEAF2FF, false);
            g.drawString(font, line.available() + "/" + line.required(), x + 20, rowY + 10, 0xFFFFC06C, false);
        }
    }

    private static String materialSummary(BlueprintEntry entry, ClientRtsController controller, BuildStats stats) {
        if (isCreativePlayer()) {
            if (stats.missingBlockTypes() > 0) {
                return text("screen.rtsbuilding.blueprints.missing_blocks_progress", stats.percent(), stats.buildable(), stats.total());
            }
            return text("screen.rtsbuilding.blueprints.materials_creative");
        }
        if (stats.percent() >= 100) {
            return text("screen.rtsbuilding.blueprints.materials_ready");
        }
        return text("screen.rtsbuilding.blueprints.materials_progress", stats.percent(), stats.buildable(), stats.total());
    }

    private static List<MaterialLine> missingMaterialLines(BlueprintEntry entry, ClientRtsController controller) {
        List<MaterialLine> out = new ArrayList<>();
        if (entry == null || controller == null) {
            return out;
        }
        for (Map.Entry<ResourceLocation, Integer> material : entry.requiredItems().entrySet()) {
            String itemId = material.getKey().toString();
            long available = controller.getStorageTotalCount(itemId);
            int required = Math.max(0, material.getValue());
            if (available >= required) {
                continue;
            }
            if (!BuiltInRegistries.ITEM.containsKey(material.getKey())) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(material.getKey());
            ItemStack stack = new ItemStack(item);
            out.add(new MaterialLine(stack, stack.getHoverName().getString(), available, required));
        }
        return out;
    }

    private static List<UnsupportedLine> unsupportedBlockLines(BlueprintEntry entry) {
        if (entry == null || entry.unsupportedBlocks().isEmpty()) {
            return List.of();
        }
        List<UnsupportedLine> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entryLine : entry.unsupportedBlocks().entrySet()) {
            out.add(new UnsupportedLine(entryLine.getKey(), entryLine.getValue()));
        }
        return out;
    }

    private static List<MissingBlueprintBlockLine> missingBlueprintBlockLines(BlueprintEntry entry) {
        if (entry == null || entry.missingBlueprintBlocks().isEmpty()) {
            return List.of();
        }
        List<MissingBlueprintBlockLine> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entryLine : entry.missingBlueprintBlocks().entrySet()) {
            String blockId = entryLine.getKey();
            out.add(new MissingBlueprintBlockLine(blockId, entryLine.getValue(), namespaceOf(blockId)));
        }
        return out;
    }

    private static List<DetailLine> detailLines(BlueprintEntry entry, ClientRtsController controller) {
        List<DetailLine> out = new ArrayList<>();
        Map<String, Integer> missingMods = missingModCounts(entry);
        for (Map.Entry<String, Integer> mod : missingMods.entrySet()) {
            out.add(new DetailLine(
                    ItemStack.EMPTY,
                    text("screen.rtsbuilding.blueprints.details_missing_mod", mod.getKey()),
                    text("screen.rtsbuilding.blueprints.details_missing_mod_count", mod.getValue()),
                    0xFFFF9E88));
        }
        for (MissingBlueprintBlockLine line : missingBlueprintBlockLines(entry)) {
            out.add(new DetailLine(
                    ItemStack.EMPTY,
                    line.blockId(),
                    text("screen.rtsbuilding.blueprints.details_missing_block_count", line.count()),
                    0xFFFF9E88));
        }
        if (!isCreativePlayer()) {
            for (UnsupportedLine line : unsupportedBlockLines(entry)) {
                out.add(new DetailLine(
                        ItemStack.EMPTY,
                        line.label(),
                        text("screen.rtsbuilding.blueprints.details_unsupported_count", line.count()),
                        0xFFFF9E88));
            }
            for (MaterialLine line : missingMaterialLines(entry, controller)) {
                long missingCount = Math.max(0L, line.required() - line.available());
                out.add(new DetailLine(
                        line.preview(),
                        line.label(),
                        text("screen.rtsbuilding.blueprints.details_count", missingCount, line.available(), line.required()),
                        0xFFFFC06C));
            }
        }
        return out;
    }

    private static BuildStats buildStats(BlueprintEntry entry, ClientRtsController controller) {
        if (entry == null || !entry.error().isBlank()) {
            return new BuildStats(0, 0, 0, 0, 0, 0);
        }
        int total = Math.max(0, entry.blockCount());
        if (total == 0) {
            return new BuildStats(100, 0, 0, 0, 0, 0);
        }
        int missingBlockTypes = missingBlueprintBlockLines(entry).size();
        int missingBlockCount = missingBlueprintBlockCount(entry);
        if (isCreativePlayer()) {
            int buildable = Math.max(0, total - missingBlockCount);
            int percent = (int) Mth.clamp(buildable * 100L / total, 0L, 100L);
            return new BuildStats(percent, buildable, total, 0, 0, missingBlockTypes);
        }
        long buildable = 0L;
        int missingTypes = 0;
        for (Map.Entry<ResourceLocation, Integer> material : entry.requiredItems().entrySet()) {
            int required = Math.max(0, material.getValue());
            long available = controller == null ? 0L : controller.getStorageTotalCount(material.getKey().toString());
            buildable += Math.min((long) required, Math.max(0L, available));
            if (available < required) {
                missingTypes++;
            }
        }
        int unsupportedTypes = unsupportedBlockLines(entry).size();
        int percent = (int) Mth.clamp(buildable * 100L / total, 0L, 100L);
        return new BuildStats(percent, (int) Math.min(buildable, total), total, missingTypes, unsupportedTypes, missingBlockTypes);
    }

    private static int missingBlueprintBlockCount(BlueprintEntry entry) {
        if (entry == null || entry.missingBlueprintBlocks().isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int value : entry.missingBlueprintBlocks().values()) {
            count += Math.max(0, value);
        }
        return count;
    }

    private static Map<String, Integer> missingModCounts(BlueprintEntry entry) {
        if (entry == null || entry.missingBlueprintBlocks().isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Integer> missing : entry.missingBlueprintBlocks().entrySet()) {
            String namespace = namespaceOf(missing.getKey());
            if (namespace.isBlank() || "minecraft".equals(namespace)) {
                continue;
            }
            out.merge(namespace, Math.max(0, missing.getValue()), Integer::sum);
        }
        return out;
    }

    private static String namespaceOf(String blockId) {
        if (blockId == null) {
            return "";
        }
        int colon = blockId.indexOf(':');
        return colon > 0 ? blockId.substring(0, colon) : "";
    }

    private static void ensureLoaded() {
        if (!loaded) {
            reload();
        }
        ensureDefaultRotationsLoaded();
    }

    public static void reload() {
        loaded = true;
        ensureDefaultRotationsLoaded();
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
                        .filter(BlueprintPanel::isBlueprintFile)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .limit(512)
                        .forEach(BlueprintPanel::addEntry);
            }
        } catch (IOException ex) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.folder_failed", ex.getMessage());
        }
    }

    private static void ensureDefaultRotationsLoaded() {
        if (defaultsLoaded) {
            return;
        }
        defaultsLoaded = true;
        DEFAULT_ROTATIONS.clear();
        Path path = defaultsPath();
        if (!Files.isRegularFile(path)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(path)) {
            properties.load(stream);
            for (String key : properties.stringPropertyNames()) {
                if (!key.endsWith(".y")) {
                    continue;
                }
                String fileName = key.substring(0, key.length() - 2);
                int y = parseInt(properties.getProperty(fileName + ".y"), 0);
                int x = parseInt(properties.getProperty(fileName + ".x"), 0);
                int z = parseInt(properties.getProperty(fileName + ".z"), 0);
                DEFAULT_ROTATIONS.put(fileName, new RotationPreset(y, x, z));
            }
        } catch (IOException ignored) {
            // Rotation presets are a convenience cache; bad local metadata should not break the panel.
        }
    }

    private static void saveDefaultRotations() {
        Properties properties = new Properties();
        for (Map.Entry<String, RotationPreset> entry : DEFAULT_ROTATIONS.entrySet()) {
            RotationPreset rotation = entry.getValue();
            properties.setProperty(entry.getKey() + ".y", Integer.toString(BlueprintTransform.normalizeSteps(rotation.y())));
            properties.setProperty(entry.getKey() + ".x", Integer.toString(BlueprintTransform.normalizeSteps(rotation.x())));
            properties.setProperty(entry.getKey() + ".z", Integer.toString(BlueprintTransform.normalizeSteps(rotation.z())));
        }
        try {
            Files.createDirectories(blueprintFolder());
            try (OutputStream stream = Files.newOutputStream(defaultsPath())) {
                properties.store(stream, "RTSBuilding blueprint rotation defaults");
            }
        } catch (IOException ex) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.save_failed", ex.getMessage());
        }
    }

    private static void applyDefaultRotation(BlueprintEntry entry) {
        if (entry == null) {
            yRotationSteps = 0;
            xRotationSteps = 0;
            zRotationSteps = 0;
            return;
        }
        ensureDefaultRotationsLoaded();
        RotationPreset preset = DEFAULT_ROTATIONS.get(entry.fileName());
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
            PointerBuffer filters = stack.mallocPointer(3);
            filters.put(stack.UTF8("*.nbt"));
            filters.put(stack.UTF8("*.schem"));
            filters.put(stack.UTF8("*.schematic"));
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
            RotationPreset preset = DEFAULT_ROTATIONS.remove(entry.fileName());
            if (preset != null) {
                DEFAULT_ROTATIONS.put(dest.getFileName().toString(), preset);
                saveDefaultRotations();
            }
            reload();
            selectByFileName(dest.getFileName().toString());
            setStatus(S2CBlueprintStatusPayload.SUCCESS, "screen.rtsbuilding.blueprints.status.renamed",
                    dest.getFileName().toString());
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
            DEFAULT_ROTATIONS.remove(entry.fileName());
            saveDefaultRotations();
            if (selectedEntry() == entry) {
                selectedIndex = -1;
                pinnedAnchor = null;
                materialDialogOpen = false;
            }
            reload();
            setStatus(S2CBlueprintStatusPayload.SUCCESS, "screen.rtsbuilding.blueprints.status.deleted", entry.name());
        } catch (Exception ex) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.delete_failed", ex.getMessage());
        }
    }

    private static void toggleCaptureMode() {
        if (captureSaveJob != null) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return;
        }
        captureMode = !captureMode;
        if (captureMode) {
            capturePointA = null;
            capturePointB = null;
            captureHoverPoint = null;
            captureExcludedBlocks.clear();
            pinnedAnchor = null;
            capturePreviewVisible = true;
            materialDialogOpen = false;
            nameDialogMode = NameDialogMode.NONE;
            nameDialogValue = "";
            nameDialogEntry = null;
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_start", "");
        } else {
            cancelCaptureMode();
        }
    }

    public static void saveCapturedArea() {
        if (captureSaveJob != null) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.save_failed", "No world");
            return;
        }
        if (capturePointA == null || capturePointB == null) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
            return;
        }
        openCaptureNameDialog();
    }

    private static void startCaptureSave(String requestedName) {
        if (captureSaveJob != null) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.save_failed", "No world");
            return;
        }
        if (capturePointA == null || capturePointB == null) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
            return;
        }
        String fileName = uniqueNbtFileName(requestedName);
        try {
            Path dest = blueprintFolder().resolve(fileName);
            long volume = captureVolume();
            long maxCaptureVolume = BlueprintWriters.maxCaptureVolume();
            if (volume > maxCaptureVolume) {
                setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.save_failed",
                        "Selection volume " + volume + " > " + maxCaptureVolume);
                return;
            }
            captureSaveJob = new CaptureSaveJob(level, capturePointA, capturePointB, captureExcludedBlocks,
                    stripNbtExtension(fileName), fileName, dest);
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_started", fileName);
        } catch (Throwable throwable) {
            handleSaveFailure(throwable);
        }
    }

    private static void cancelCaptureMode() {
        if (captureSaveJob != null) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return;
        }
        captureMode = false;
        capturePointA = null;
        capturePointB = null;
        captureHoverPoint = null;
        captureExcludedBlocks.clear();
        nameDialogMode = NameDialogMode.NONE;
        nameDialogValue = "";
        nameDialogEntry = null;
        setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_cancelled", "");
    }

    private static void moveCaptureSelection(int deltaY) {
        if (captureSaveJob != null) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return;
        }
        if (capturePointA == null || deltaY == 0) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
            return;
        }
        capturePointA = capturePointA.offset(0, deltaY, 0);
        if (capturePointB != null) {
            capturePointB = capturePointB.offset(0, deltaY, 0);
        }
        if (!captureExcludedBlocks.isEmpty()) {
            Set<BlockPos> moved = new HashSet<>();
            for (BlockPos pos : captureExcludedBlocks) {
                moved.add(pos.offset(0, deltaY, 0));
            }
            captureExcludedBlocks.clear();
            captureExcludedBlocks.addAll(moved);
        }
        setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_moved", captureSizeText());
    }

    private static void expandCaptureVertical(int deltaY) {
        if (captureSaveJob != null) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return;
        }
        if (capturePointA == null || capturePointB == null || deltaY == 0) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
            return;
        }
        BlockPos first = capturePointA;
        BlockPos second = capturePointB;
        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());
        maxY = Math.max(minY, maxY + deltaY);
        capturePointA = new BlockPos(minX, minY, minZ);
        capturePointB = new BlockPos(maxX, maxY, maxZ);
        setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_resized", captureSizeText());
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

    private static boolean isBlueprintFile(Path path) {
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return lower.endsWith(".nbt") || lower.endsWith(".schem") || lower.endsWith(".schematic");
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

    private static Path blueprintFolder() {
        return FMLPaths.GAMEDIR.get().resolve("rtsbuilding-blueprints");
    }

    private static Path defaultsPath() {
        return blueprintFolder().resolve(".rtsbuilding-rotation-defaults.properties");
    }

    private static Path ensureExtension(Path path, String extension) {
        if (path == null || extension == null || extension.isBlank()) {
            return path;
        }
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith("." + extension.toLowerCase(Locale.ROOT))) {
            return path;
        }
        Path parent = path.getParent();
        Path renamed = Path.of(name + "." + extension);
        return parent == null ? renamed : parent.resolve(renamed);
    }

    private static String stripBlueprintExtension(String fileName) {
        String clean = fileName == null || fileName.isBlank() ? "blueprint" : fileName;
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".schematic")) {
            return clean.substring(0, clean.length() - ".schematic".length());
        }
        if (lower.endsWith(".schem")) {
            return clean.substring(0, clean.length() - ".schem".length());
        }
        if (lower.endsWith(".nbt")) {
            return clean.substring(0, clean.length() - ".nbt".length());
        }
        return clean;
    }

    private static String blueprintExtension(String fileName, String fallback) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".schematic")) {
            return "schematic";
        }
        if (lower.endsWith(".schem")) {
            return "schem";
        }
        if (lower.endsWith(".nbt")) {
            return "nbt";
        }
        return fallback == null || fallback.isBlank() ? "nbt" : fallback;
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

    private static String uniqueNbtFileName(String base) {
        String clean = sanitizeFileBase(base);
        String candidate = clean + ".nbt";
        int suffix = 2;
        while (Files.exists(blueprintFolder().resolve(candidate))) {
            candidate = clean + "_" + suffix + ".nbt";
            suffix++;
        }
        return candidate;
    }

    private static Path uniqueBlueprintPath(String base, String extension, Path currentPath) {
        String clean = sanitizeFileBase(base);
        String safeExtension = extension == null || extension.isBlank() ? "nbt" : extension;
        Path folder = blueprintFolder();
        Path current = currentPath == null ? null : currentPath.toAbsolutePath().normalize();
        Path candidate = folder.resolve(clean + "." + safeExtension);
        int suffix = 2;
        while (Files.exists(candidate)
                && (current == null || !candidate.toAbsolutePath().normalize().equals(current))) {
            candidate = folder.resolve(clean + "_" + suffix + "." + safeExtension);
            suffix++;
        }
        return candidate;
    }

    private static String stripNbtExtension(String fileName) {
        String name = fileName == null ? "blueprint" : fileName;
        return name.toLowerCase(Locale.ROOT).endsWith(".nbt")
                ? name.substring(0, name.length() - 4)
                : name;
    }

    private static String sanitizeFileBase(String raw) {
        String clean = raw == null ? "blueprint" : raw.trim();
        if (clean.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
            clean = clean.substring(0, clean.length() - 4);
        }
        clean = clean.replaceAll("[\\\\/:*?\"<>|]+", "_").replaceAll("\\s+", "_");
        clean = clean.replaceAll("[^A-Za-z0-9._\\-\\u4e00-\\u9fff]+", "_");
        clean = clean.replaceAll("_+", "_");
        if (clean.isBlank() || clean.equals(".") || clean.equals("..")) {
            clean = "blueprint";
        }
        return clean.length() > 80 ? clean.substring(0, 80) : clean;
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return BlueprintTransform.normalizeSteps(Integer.parseInt(raw));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String shortPos(BlockPos pos) {
        return pos == null ? "-" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static String captureSizeText() {
        if (capturePointA == null || capturePointB == null) {
            return "";
        }
        return captureSizeText(capturePointB);
    }

    private static String capturePreviewSizeText() {
        if (capturePointA == null) {
            return "";
        }
        BlockPos second = getCapturePreviewPointB();
        if (second == null) {
            return "";
        }
        return captureSizeText(second);
    }

    private static String captureSizeText(BlockPos second) {
        if (capturePointA == null || second == null) {
            return "";
        }
        int x = Math.abs(capturePointA.getX() - second.getX()) + 1;
        int y = Math.abs(capturePointA.getY() - second.getY());
        int z = Math.abs(capturePointA.getZ() - second.getZ()) + 1;
        return x + "x" + y + "x" + z;
    }

    private static String captureVolumeText() {
        return Long.toString(captureVolume());
    }

    private static String capturePreviewSummaryLine() {
        return text("screen.rtsbuilding.blueprints.capture_preview_summary", capturePreviewSizeText(), capturePreviewVolumeText());
    }

    private static String capturePreviewVolumeText() {
        BlockPos second = getCapturePreviewPointB();
        return second == null ? "0" : Long.toString(captureVolume(second));
    }

    private static long captureVolume() {
        if (capturePointA == null || capturePointB == null) {
            return 0L;
        }
        return captureVolume(capturePointB);
    }

    private static long captureVolume(BlockPos second) {
        if (capturePointA == null || second == null) {
            return 0L;
        }
        long x = Math.abs(capturePointA.getX() - second.getX()) + 1L;
        long y = Math.abs(capturePointA.getY() - second.getY());
        long z = Math.abs(capturePointA.getZ() - second.getZ()) + 1L;
        return x * y * z;
    }

    private static boolean isInsideCaptureSelection(BlockPos pos) {
        if (capturePointA == null || capturePointB == null || pos == null) {
            return false;
        }
        int minX = Math.min(capturePointA.getX(), capturePointB.getX());
        int minY = Math.min(capturePointA.getY(), capturePointB.getY()) + 1;
        int minZ = Math.min(capturePointA.getZ(), capturePointB.getZ());
        int maxX = Math.max(capturePointA.getX(), capturePointB.getX());
        int maxY = Math.max(capturePointA.getY(), capturePointB.getY());
        int maxZ = Math.max(capturePointA.getZ(), capturePointB.getZ());
        if (minY > maxY) {
            return false;
        }
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private static boolean hasEnoughMaterials(BlueprintEntry entry, ClientRtsController controller) {
        if (entry == null || !entry.error().isBlank() || controller == null) {
            return false;
        }
        if (!entry.missingBlueprintBlocks().isEmpty()) {
            return false;
        }
        if (isCreativePlayer()) {
            return true;
        }
        if (!entry.unsupportedBlocks().isEmpty()) {
            return false;
        }
        for (Map.Entry<ResourceLocation, Integer> material : entry.requiredItems().entrySet()) {
            if (controller.getStorageTotalCount(material.getKey().toString()) < material.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCreativePlayer() {
        return Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
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

    private static int listColumns(int w) {
        return w >= 320 ? 2 : 1;
    }

    private static int listCellWidth(int w, int columns) {
        return Math.max(80, (w - 2 - (columns - 1) * LIST_COLUMN_GAP) / columns);
    }

    private static int maxListScroll(int entryCount, int columns, int visibleRows) {
        int rows = Math.max(0, (entryCount + Math.max(1, columns) - 1) / Math.max(1, columns));
        return Math.max(0, rows - visibleRows);
    }

    private static RowActionLayout rowActionLayout(Font font, int cellX, int rowY, int cellW) {
        int gap = 3;
        int saveW = buttonWidth(font, "screen.rtsbuilding.blueprints.save_as_short", 38, 46);
        int renameW = buttonWidth(font, "screen.rtsbuilding.blueprints.rename", 38, 48);
        int deleteW = buttonWidth(font, "screen.rtsbuilding.blueprints.delete", 34, 42);
        int totalW = saveW + renameW + deleteW + gap * 2;
        int x = cellX + Math.max(4, cellW - totalW - 4);
        int buttonY = rowY + 5;
        return new RowActionLayout(
                x,
                saveW,
                x + saveW + gap,
                renameW,
                x + saveW + gap + renameW + gap,
                deleteW,
                buttonY);
    }

    private static NameDialogLayout nameDialogLayout(int screenW, int screenH) {
        boolean capture = nameDialogMode == NameDialogMode.CAPTURE_SAVE;
        int w = Math.min(420, Math.max(300, screenW - 48));
        int h = capture ? 136 : 118;
        int x = (screenW - w) / 2;
        int y = Math.max(24, (screenH - h) / 2);
        int inputX = x + 10;
        int inputY = y + (capture ? 76 : 62);
        int inputW = w - 20;
        int cancelW = 58;
        int confirmW = 70;
        int buttonY = y + h - 24;
        int cancelX = x + w - cancelW - 10;
        int confirmX = cancelX - confirmW - 6;
        return new NameDialogLayout(x, y, w, h, inputX, inputY, inputW, confirmX, confirmW, cancelX, cancelW, buttonY);
    }

    private static TopBarLayout topBarLayout(Font font, int x, int w) {
        int gap = 4;
        int folderW = buttonWidth(font, "screen.rtsbuilding.blueprints.open_folder_short", 64, 96);
        int importW = buttonWidth(font, "screen.rtsbuilding.blueprints.import_file_short", 44, 72);
        int captureW = buttonWidth(font,
                captureMode ? "screen.rtsbuilding.blueprints.capture_active_short" : "screen.rtsbuilding.blueprints.capture_short",
                74,
                112);
        int actionW = folderW + importW + captureW + gap * 2;
        int searchX = x + actionW + 8;
        int searchW = Math.max(60, x + w - searchX);
        if (searchW < 80) {
            folderW = 56;
            importW = 44;
            captureW = 70;
            actionW = folderW + importW + captureW + gap * 2;
            searchX = x + actionW + 6;
            searchW = Math.max(50, x + w - searchX);
        }
        int folderX = x;
        int importX = folderX + folderW + gap;
        int captureX = importX + importW + gap;
        return new TopBarLayout(folderX, folderW, importX, importW, captureX, captureW, searchX, searchW);
    }

    private static int rowSaveAsWidth(Font font) {
        return buttonWidth(font, "screen.rtsbuilding.blueprints.save_as_short", 42, 58);
    }

    private static int buttonWidth(Font font, String key, int min, int max) {
        int labelWidth = font == null ? 0 : font.width(text(key));
        return Mth.clamp(labelWidth + 10, min, max);
    }

    private static void drawButton(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean hover) {
        drawButton(g, font, x, y, w, h, label, hover, false);
    }

    private static void drawButton(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean hover, boolean active) {
        int fill = active ? 0xCC2E6A50 : (hover ? 0xCC334052 : 0xAA24303C);
        drawFrame(g, x, y, w, h, fill, 0xFF64788E, 0xFF0D1015);
        g.drawCenteredString(font, trim(font, label, w - 6), x + w / 2, y + 3, 0xFFEAF2FF);
    }

    private static void drawFrame(GuiGraphics g, int x, int y, int w, int h, int fill, int light, int dark) {
        g.fill(x, y, x + w, y + h, fill);
        g.hLine(x, x + w, y, light);
        g.hLine(x, x + w, y + h, dark);
        g.vLine(x, y, y + h, light);
        g.vLine(x + w, y, y + h, dark);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static String text(String key) {
        return Component.translatable(key).getString();
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String trim(Font font, String text, int maxWidth) {
        if (font == null || text == null || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "...";
        int limit = Math.max(0, maxWidth - font.width(ellipsis));
        int cut = text.length();
        while (cut > 0 && font.width(text.substring(0, cut)) > limit) {
            cut--;
        }
        return text.substring(0, cut) + ellipsis;
    }

    private static final class CaptureSaveJob {
        private final Level level;
        private final int minX;
        private final int minY;
        private final int captureMinY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final Vec3i size;
        private final String name;
        private final String fileName;
        private final Path dest;
        private final long volume;
        private final Set<BlockPos> excludedBlocks;
        private final List<RtsBlueprintBlock> blocks = new ArrayList<>();
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private int x;
        private int y;
        private int z;
        private long scanned;
        private long lastStatusMillis;
        private long lastScanMillis;
        private boolean scanComplete;
        private CompletableFuture<CaptureSaveResult> writeFuture;

        CaptureSaveJob(Level level, BlockPos first, BlockPos second, Set<BlockPos> excludedBlocks,
                String name, String fileName, Path dest) {
            this.level = level;
            this.minX = Math.min(first.getX(), second.getX());
            this.minY = Math.min(first.getY(), second.getY());
            this.captureMinY = this.minY + 1;
            this.minZ = Math.min(first.getZ(), second.getZ());
            this.maxX = Math.max(first.getX(), second.getX());
            this.maxY = Math.max(first.getY(), second.getY());
            this.maxZ = Math.max(first.getZ(), second.getZ());
            this.size = new Vec3i(this.maxX - this.minX + 1, Math.max(0, this.maxY - this.minY), this.maxZ - this.minZ + 1);
            this.name = name;
            this.fileName = fileName;
            this.dest = dest;
            this.volume = (long) this.size.getX() * this.size.getY() * this.size.getZ();
            this.excludedBlocks = Set.copyOf(excludedBlocks);
            this.x = this.minX;
            this.y = this.captureMinY;
            this.z = this.minZ;
            this.scanComplete = this.captureMinY > this.maxY;
        }

        CaptureSaveResult tick() {
            if (this.writeFuture != null) {
                if (!this.writeFuture.isDone()) {
                    updateStatus("screen.rtsbuilding.blueprints.status.save_writing", "");
                    return null;
                }
                try {
                    return this.writeFuture.join();
                } catch (RuntimeException ex) {
                    return CaptureSaveResult.failure("screen.rtsbuilding.blueprints.status.save_failed", failureDetail(ex));
                }
            }

            long now = Util.getMillis();
            if (!this.scanComplete && now - this.lastScanMillis < CAPTURE_SCAN_MIN_INTERVAL_MS) {
                updateStatus("screen.rtsbuilding.blueprints.status.save_scanning", progressLine());
                return null;
            }
            this.lastScanMillis = now;

            int processed = 0;
            long stepStarted = System.nanoTime();
            while (!this.scanComplete
                    && processed < CAPTURE_SCAN_BUDGET_PER_STEP
                    && System.nanoTime() - stepStarted < CAPTURE_SCAN_TIME_BUDGET_NANOS) {
                this.cursor.set(this.x, this.y, this.z);
                if (!this.excludedBlocks.contains(this.cursor) && this.level.hasChunkAt(this.cursor)) {
                    BlockState state = this.level.getBlockState(this.cursor);
                    if (!state.isAir() && !state.is(Blocks.STRUCTURE_VOID)) {
                        this.blocks.add(new RtsBlueprintBlock(
                                new BlockPos(this.x - this.minX, this.y - this.captureMinY, this.z - this.minZ),
                                state,
                                new net.minecraft.nbt.CompoundTag()));
                        int maxCaptureBlocks = BlueprintWriters.maxCaptureBlocks();
                        if (this.blocks.size() > maxCaptureBlocks) {
                            return CaptureSaveResult.failure("screen.rtsbuilding.blueprints.status.save_failed",
                                    "Blueprint capture contains more than " + maxCaptureBlocks + " blocks");
                        }
                    }
                }
                advance();
                processed++;
                this.scanned++;
            }

            if (!this.scanComplete) {
                updateStatus("screen.rtsbuilding.blueprints.status.save_scanning", progressLine());
                return null;
            }
            if (this.blocks.isEmpty()) {
                return CaptureSaveResult.failure("screen.rtsbuilding.blueprints.status.capture_empty", "");
            }

            RtsBlueprint blueprint = RtsBlueprint.create(
                    this.name,
                    this.fileName,
                    BlueprintFormat.VANILLA_NBT,
                    this.size,
                    List.copyOf(this.blocks));
            updateStatus("screen.rtsbuilding.blueprints.status.save_writing", "");
            this.writeFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    BlueprintWriters.writeVanillaStructure(blueprint, this.dest);
                    return CaptureSaveResult.success(this.fileName, this.dest, blueprint);
                } catch (Exception ex) {
                    return CaptureSaveResult.failure("screen.rtsbuilding.blueprints.status.save_failed", failureDetail(ex));
                }
            });
            return null;
        }

        String statusLine() {
            return this.writeFuture == null
                    ? text("screen.rtsbuilding.blueprints.status.save_scanning", progressLine())
                    : text("screen.rtsbuilding.blueprints.status.save_writing");
        }

        String progressLine() {
            return this.scanned + "/" + this.volume;
        }

        private void updateStatus(String key, String detail) {
            long now = Util.getMillis();
            if (now - this.lastStatusMillis < CAPTURE_STATUS_INTERVAL_MS) {
                return;
            }
            this.lastStatusMillis = now;
            setStatus(S2CBlueprintStatusPayload.INFO, key, detail);
        }

        private void advance() {
            if (++this.x > this.maxX) {
                this.x = this.minX;
                if (++this.z > this.maxZ) {
                    this.z = this.minZ;
                    if (++this.y > this.maxY) {
                        this.scanComplete = true;
                    }
                }
            }
        }
    }

    private record CaptureSaveResult(boolean success, String fileName, Path path, RtsBlueprint blueprint, String messageKey, String detail) {
        static CaptureSaveResult success(String fileName, Path path, RtsBlueprint blueprint) {
            return new CaptureSaveResult(true, fileName, path, blueprint, "", "");
        }

        static CaptureSaveResult failure(String messageKey, String detail) {
            return new CaptureSaveResult(false, "", null, null, messageKey, detail == null ? "" : detail);
        }
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

    private record NameDialogLayout(
            int x,
            int y,
            int w,
            int h,
            int inputX,
            int inputY,
            int inputW,
            int confirmX,
            int confirmW,
            int cancelX,
            int cancelW,
            int buttonY) {
    }

    private record MaterialLine(ItemStack preview, String label, long available, int required) {
    }

    private record UnsupportedLine(String label, int count) {
    }

    private record MissingBlueprintBlockLine(String blockId, int count, String namespace) {
    }

    private record DetailLine(ItemStack preview, String label, String detail, int color) {
    }

    private record BuildStats(
            int percent,
            int buildable,
            int total,
            int missingTypes,
            int unsupportedTypes,
            int missingBlockTypes) {
    }

    private record RotationPreset(int y, int x, int z) {
    }

    private record RowActionLayout(int saveX, int saveW, int renameX, int renameW, int deleteX, int deleteW, int buttonY) {
    }

    private record TopBarLayout(int folderX, int folderW, int importX, int importW, int captureX, int captureW,
            int searchX, int searchW) {
    }

    private record BlueprintEntry(
            Path path,
            String fileName,
            String name,
            BlueprintFormat format,
            String sizeText,
            int blockCount,
            RtsBlueprint blueprint,
            Map<ResourceLocation, Integer> requiredItems,
            Map<String, Integer> unsupportedBlocks,
            Map<String, Integer> missingBlueprintBlocks,
            List<ItemStack> previewItems,
            String error) {
        static BlueprintEntry from(Path path, String fileName, RtsBlueprint blueprint, String error) {
            Vec3i size = blueprint.size();
            List<ItemStack> preview = new ArrayList<>();
            for (ResourceLocation id : blueprint.requiredItems().keySet()) {
                if (!BuiltInRegistries.ITEM.containsKey(id)) {
                    continue;
                }
                Item item = BuiltInRegistries.ITEM.get(id);
                ItemStack stack = new ItemStack(item);
                if (!stack.isEmpty()) {
                    preview.add(stack);
                }
                if (preview.size() >= 18) {
                    break;
                }
            }
            Map<String, Integer> unsupported = new java.util.LinkedHashMap<>();
            Map<String, Integer> missingBlueprintBlocks = new java.util.LinkedHashMap<>();
            for (RtsBlueprintBlock block : blueprint.blocks()) {
                if (block.isMissingBlock()) {
                    missingBlueprintBlocks.merge(block.missingBlockId(), 1, Integer::sum);
                    continue;
                }
                if (block.state().getBlock().asItem() == Items.AIR) {
                    unsupported.merge(block.state().getBlock().getName().getString(), 1, Integer::sum);
                }
            }
            String sizeText = size.getX() + "x" + size.getY() + "x" + size.getZ();
            return new BlueprintEntry(
                    path,
                    fileName,
                    blueprint.name(),
                    blueprint.format(),
                    sizeText,
                    blueprint.blockCount(),
                    blueprint,
                    blueprint.requiredItems(),
                    Map.copyOf(unsupported),
                    Map.copyOf(missingBlueprintBlocks),
                    List.copyOf(preview),
                    error == null ? "" : error);
        }

        static BlueprintEntry error(Path path, String fileName, String error) {
            String name = fileName;
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                name = name.substring(0, dot);
            }
            return new BlueprintEntry(
                    path,
                    fileName,
                    name,
                    BlueprintFormat.fromFileName(fileName),
                    "-",
                    0,
                    RtsBlueprint.create(name, fileName, BlueprintFormat.fromFileName(fileName), Vec3i.ZERO, List.of()),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    List.of(),
                    error == null ? "Parse failed" : error);
        }
    }
}
