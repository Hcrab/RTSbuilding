package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.selection.RtsSelectionNudge;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintUiAction;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiAction;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiState;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintLibraryLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintLibraryStyle;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.common.blueprint.transform.BlueprintTransform;
import com.rtsbuilding.rtsbuilding.network.blueprint.C2SBlueprintPlacePayload;
import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.RtsGuiContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintMaterialInspector.*;
import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanelFiles.*;
import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanelUi.*;

public final class BlueprintPanel {
    private static final BlueprintLibraryRepository LIBRARY = new BlueprintLibraryRepository();
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
    private static final BlueprintCaptureController CAPTURE = new BlueprintCaptureController();
    private static String search = "";
    private static Component statusText = Component.translatable("screen.rtsbuilding.blueprints.status.ready");
    private static int statusColor =
            BlueprintLibraryStyle.STATUS_DEFAULT_TEXT.toArgb();

    private BlueprintPanel() {
    }

    public static void render(RtsGuiContext g, Font font, ClientRtsController controller,
            int x, int y, int w, int h, int mouseX, int mouseY) {
        if (!Config.areBlueprintsEnabled()) {
            CAPTURE.clearSilently();
            BlueprintLibraryPanelRenderer.renderDisabled(
                    g,
                    font,
                    x,
                    y,
                    w,
                    h);
            return;
        }
        tickCaptureSaveJob();
        ensureLoaded();
        BlueprintLibraryLayout.Geometry geometry = BlueprintLibraryLayout.geometry(x, y, w, h);
        BlueprintLibraryUiState library = BlueprintLibraryUiAdapter.snapshotForViewport(
                controller, geometry.listW, geometry.listH);
        BlueprintLibraryPanelRenderer.render(
                g,
                font,
                library,
                x,
                y,
                w,
                h,
                mouseX,
                mouseY);
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int x, int y, int w, int h,
                                       ClientRtsController controller) {
        if (!Config.areBlueprintsEnabled()) {
            searchFocused = false;
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.disabled", "");
            return true;
        }
        BlueprintLibraryUiState library = BlueprintLibraryUiAdapter.snapshot(controller);
        return BlueprintLibraryPanelInput.mouseClicked(
                mouseX,
                mouseY,
                Minecraft.getInstance().font,
                x,
                y,
                w,
                h,
                library,
                controller);
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

    static boolean nameDialogReplaceOnType() {
        return nameDialogReplaceOnType;
    }

    /** 由 Core 动作提交命名框完整草稿，统一执行生产长度和非法字符约束。 */
    static void setNameDialogValueFromUi(String value) {
        if (!isNameDialogOpen()) {
            return;
        }
        String safe = value == null ? "" : value;
        nameDialogValue = safe.substring(0, Math.min(80, safe.length()));
        nameDialogReplaceOnType = false;
    }

    static BlueprintEntry nameDialogEntry() {
        return nameDialogEntry;
    }

    static BlockPos nameDialogCapturePointA() {
        return CAPTURE.displayPointA();
    }

    static BlockPos nameDialogCapturePointB() {
        return CAPTURE.displayPointB();
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

    public static boolean mouseScrolled(double mouseX, double mouseY, double scrollY,
                                        int x, int y, int w, int h, ClientRtsController controller) {
        if (!Config.areBlueprintsEnabled()) {
            return false;
        }
        BlueprintLibraryUiState library = BlueprintLibraryUiAdapter.snapshot(controller);
        return BlueprintLibraryPanelInput.mouseScrolled(
                mouseX,
                mouseY,
                scrollY,
                x,
                y,
                w,
                h,
                library,
                controller);
    }

    public static boolean keyPressed(int keyCode, int scanCode, ClientRtsController controller) {
        if (!Config.areBlueprintsEnabled()) {
            searchFocused = false;
            return false;
        }
        BlueprintLibraryUiState library = BlueprintLibraryUiAdapter.snapshot(controller);
        boolean cancelKey = ClientKeyMappings.BLUEPRINT_CANCEL.matches(keyCode, scanCode);
        if (CAPTURE.isActive()) {
            searchFocused = false;
            if (CAPTURE.isSaving()) {
                setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
                return true;
            }
            if (cancelKey || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                if (CAPTURE.releaseActiveHandle()) {
                    return true;
                }
                cancelCaptureMode();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                saveCapturedArea();
                return true;
            }
            RtsSelectionNudge.Delta captureDelta = RtsSelectionNudge.fromKey(keyCode, scanCode);
            if (captureDelta != null && CAPTURE.isSelectionComplete()) {
                CAPTURE.moveSelection(captureDelta.dx(), captureDelta.dy(), captureDelta.dz(), BlueprintPanel::setStatus);
                return true;
            }
            return true;
        }
        if (!library.searchFocused && hasSelectedBlueprint() && isBlueprintRotateKey(keyCode, scanCode)) {
            return rotateSelectedBlueprintY(isShiftDown() ? -1 : 1);
        }
        if (hasPinnedPreview()) {
            RtsSelectionNudge.Delta delta = RtsSelectionNudge.fromKey(keyCode, scanCode);
            if (delta != null) {
                return nudgePinnedAnchor(delta.dx(), delta.dy(), delta.dz(), controller);
            }
        }
        if (!library.searchFocused && cancelKey) {
            if (hasSelectedBlueprint() || hasPinnedPreview()) {
                clearSelectedBlueprint();
                return true;
            }
            return false;
        }
        if (!library.searchFocused) {
            return false;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            if (!library.query.isEmpty()) {
                BlueprintLibraryUiAdapter.dispatch(BlueprintLibraryUiAction.text(
                        BlueprintLibraryUiAction.Type.SET_QUERY,
                        library.query.substring(0, library.query.length() - 1)), controller);
            }
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            BlueprintLibraryUiAdapter.dispatch(BlueprintLibraryUiAction.simple(
                    BlueprintLibraryUiAction.Type.BLUR_SEARCH), controller);
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

    public static boolean charTyped(char codePoint, ClientRtsController controller) {
        if (!Config.areBlueprintsEnabled()) {
            return false;
        }
        BlueprintLibraryUiState library = BlueprintLibraryUiAdapter.snapshot(controller);
        if (!library.searchFocused || Character.isISOControl(codePoint)) {
            return false;
        }
        if (library.query.length() < 96) {
            BlueprintLibraryUiAdapter.dispatch(BlueprintLibraryUiAction.text(
                    BlueprintLibraryUiAction.Type.SET_QUERY,
                    library.query + codePoint), controller);
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
        if (LIBRARY.isEmpty() || delta == 0) {
            return;
        }
        int start = selectedIndex >= 0 && selectedIndex < LIBRARY.size() ? selectedIndex : 0;
        for (int step = 1; step <= LIBRARY.size(); step++) {
            int index = Math.floorMod(start + delta * step, LIBRARY.size());
            BlueprintEntry entry = LIBRARY.get(index);
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
        return shortPos(CAPTURE.displayPointA());
    }

    static int selectedBlueprintIndex() {
        return selectedIndex;
    }

    static int blueprintEntryCount() {
        ensureLoaded();
        return LIBRARY.size();
    }

    static List<BlueprintEntry> libraryEntries() {
        ensureLoaded();
        return LIBRARY.copyEntries();
    }

    static BlueprintEntry librarySelectedEntry() {
        return selectedEntry();
    }

    static String libraryQuery() {
        return search;
    }

    static boolean librarySearchFocused() {
        return searchFocused;
    }

    static int libraryScrollRows() {
        return scroll;
    }

    static void applyLibraryViewState(String query, boolean focused, int scrollRows) {
        search = query == null ? "" : query.substring(0, Math.min(96, query.length()));
        searchFocused = focused;
        scroll = Math.max(0, scrollRows);
    }

    static void openBlueprintFolderFromUi() {
        applyFileOperation(BlueprintLibraryFileOperations.openFolder());
    }

    static void importBlueprintFileFromUi() {
        applyFileOperation(BlueprintLibraryFileOperations.importFile());
    }

    static void syncCreateBlueprintsFromUi() {
        applyFileOperation(BlueprintLibraryFileOperations.syncOtherMods());
    }

    static void toggleCaptureModeFromUi() {
        toggleCaptureMode();
    }

    static boolean selectLibraryEntry(String fileName) {
        BlueprintEntry entry = entryByFileName(fileName);
        if (entry == null) return false;
        selectEntry(entry);
        return true;
    }

    static boolean saveLibraryEntryAs(String fileName) {
        BlueprintEntry entry = entryByFileName(fileName);
        if (entry == null || !entry.error().isBlank()) return false;
        applyFileOperation(BlueprintLibraryFileOperations.saveAs(entry));
        return true;
    }

    static boolean renameLibraryEntry(String fileName) {
        BlueprintEntry entry = entryByFileName(fileName);
        if (entry == null || !entry.error().isBlank()) return false;
        openRenameDialog(entry);
        return true;
    }

    static boolean deleteLibraryEntry(String fileName) {
        BlueprintEntry entry = entryByFileName(fileName);
        if (entry == null) return false;
        applyFileOperation(BlueprintLibraryFileOperations.delete(entry));
        return true;
    }

    /**
     * 文件操作只返回结果；UI 列表重载、选择恢复和状态文字仍由工作流 owner 顺序执行。
     */
    private static void applyFileOperation(
            BlueprintLibraryFileOperations.Result result) {
        if (result == null) {
            return;
        }
        if (result.reload()) {
            reload();
        }
        if (!result.selectedFileName().isBlank()) {
            if (result.selectionMode()
                    == BlueprintLibraryFileOperations.SelectionMode.FULL) {
                BlueprintEntry entry = entryByFileName(result.selectedFileName());
                if (entry != null) {
                    selectEntry(entry);
                }
            } else if (result.selectionMode()
                    == BlueprintLibraryFileOperations.SelectionMode.INDEX_ONLY) {
                selectByFileName(result.selectedFileName());
            }
        }
        if (result.status() != null && !result.messageKey().isBlank()) {
            setStatus(result.status(), result.messageKey(), result.detail());
        }
    }

    private static BlueprintEntry entryByFileName(String fileName) {
        ensureLoaded();
        return LIBRARY.findByFileName(fileName);
    }

    static String capturePointBText() {
        return shortPos(CAPTURE.displayPointB());
    }

    static String captureSizeText() {
        return CAPTURE.sizeText();
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

    public static void updateCaptureHover(Vec3 origin, Vec3 direction, BlockPos pos) {
        CAPTURE.updateHoverPoint(pos);
        CAPTURE.updateHandleHover(origin, direction);
    }

    public static BlockPos getCapturePreviewPointB() {
        return CAPTURE.previewPointB();
    }

    public static com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox getCapturePreviewBoxForRender() {
        return CAPTURE.previewBox();
    }

    public static AABB getCapturePreviewAabbForRender() {
        return CAPTURE.previewAabbForRender();
    }

    public static Direction getCaptureHoveredHandleDirection() {
        return CAPTURE.hoveredHandleDirection();
    }

    public static Direction getCaptureActiveHandleDirection() {
        return CAPTURE.activeHandleDirection();
    }

    public static boolean releaseCaptureActiveHandleIfDragged() {
        return Config.areBlueprintsEnabled() && CAPTURE.releaseActiveHandleIfDragged();
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
        if (pos == null) {
            return false;
        }
        return BlueprintUiStateAdapter.dispatch(BlueprintUiAction.vector(
                BlueprintUiAction.Type.ACCEPT_CAPTURE_POINT, pos.getX(), pos.getY(), pos.getZ()), null);
    }

    /** 仅供生产适配器执行 Core 已批准的捕获点命令，避免公开入口绕过状态动作。 */
    static boolean acceptCapturePointDirect(BlockPos pos) {
        return Config.areBlueprintsEnabled() && CAPTURE.acceptPoint(pos, BlueprintPanel::setStatus);
    }

    public static boolean handleCaptureWorldAction(BlockHitResult hit, Vec3 origin, Vec3 direction) {
        return Config.areBlueprintsEnabled() && CAPTURE.handleWorldAction(hit, origin, direction, BlueprintPanel::setStatus);
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

    public static boolean mouseScrolledCaptureHeight(double scrollY, boolean fast) {
        if (!Config.areBlueprintsEnabled() || !CAPTURE.isActive()) {
            return false;
        }
        if (CAPTURE.isSaving()) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return true;
        }
        return CAPTURE.handleScroll(scrollY, fast, BlueprintPanel::setStatus);
    }

    public static boolean mouseDraggedCaptureHandle(double dragX, double dragY, double axisX, double axisY) {
        if (!Config.areBlueprintsEnabled() || !CAPTURE.isActive()) {
            return false;
        }
        if (CAPTURE.isSaving()) {
            setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return true;
        }
        return CAPTURE.handleDrag(dragX, dragY, axisX, axisY, BlueprintPanel::setStatus);
    }

    static void setCaptureSize(int x, int y, int z) {
        if (!Config.areBlueprintsEnabled()) {
            return;
        }
        CAPTURE.setSelectionSize(x, y, z, BlueprintPanel::setStatus);
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

    public static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostPreview createGhostPreview(
            BlockPos anchor, int yRotationSteps, ClientRtsController controller) {
        BlueprintEntry entry = selectedEntry();
        if (!Config.areBlueprintsEnabled() || anchor == null || entry == null || !entry.error().isBlank()) {
            return com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostPreview.EMPTY;
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
        return new com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostPreview(
                List.copyOf(out), hasEnoughMaterials(entry, controller), entry.blockCount() > out.size());
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
            // 一次明确点击只生成一个提交身份；网络层若重发此 payload，会自然复用同一 UUID。
            C2SBlueprintPlacePayload payload = new C2SBlueprintPlacePayload(
                    UUID.randomUUID(),
                    entry.fileName(),
                    data,
                    anchor,
                    (byte) BlueprintTransform.normalizeSteps(yRotationSteps),
                    (byte) BlueprintTransform.normalizeSteps(xRotationSteps),
                    (byte) BlueprintTransform.normalizeSteps(zRotationSteps));
            PacketDistributor.sendToServer(payload);
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

    static boolean rotateSelectedBlueprintX(int step) {
        if (!hasSelectedBlueprint()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.no_selection", "");
            return true;
        }
        xRotationSteps = BlueprintTransform.normalizeSteps(xRotationSteps + step);
        rememberCurrentRotationAsDefault();
        setStatus(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.rotated", "");
        return true;
    }

    static boolean rotateSelectedBlueprintZ(int step) {
        if (!hasSelectedBlueprint()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.no_selection", "");
            return true;
        }
        zRotationSteps = BlueprintTransform.normalizeSteps(zRotationSteps + step);
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
        Direction forward = currentHorizontalFacingDirection();
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
            case S2CBlueprintStatusPayload.SUCCESS ->
                    BlueprintLibraryStyle.STATUS_SUCCESS_TEXT.toArgb();
            case S2CBlueprintStatusPayload.ERROR ->
                    BlueprintLibraryStyle.STATUS_ERROR_TEXT.toArgb();
            default ->
                    BlueprintLibraryStyle.STATUS_DEFAULT_TEXT.toArgb();
        };
    }

    private static void tickCaptureSaveJob() {
        BlueprintCaptureSaveCoordinator.Completion completion =
                BlueprintCaptureSaveCoordinator.poll(CAPTURE, LIBRARY);
        if (completion == null) {
            return;
        }
        if (!completion.selectedFileName().isBlank()) {
            selectByFileName(completion.selectedFileName());
        }
        setStatus(
                completion.status(),
                completion.messageKey(),
                completion.detail());
    }

    static void openMaterialDialog() {
        if (selectedEntry() == null) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.no_selection", "");
            return;
        }
        materialDialogOpen = true;
        materialDialogScroll = 0;
    }

    private static void ensureLoaded() {
        LIBRARY.ensureLoaded(BlueprintPanel::setStatus);
        BlueprintRotationDefaults.ensureLoaded();
    }

    public static void reload() {
        BlueprintRotationDefaults.ensureLoaded();
        selectedIndex = -1;
        scroll = 0;
        materialDialogOpen = false;
        materialDialogScroll = 0;
        pinnedAnchor = null;
        LIBRARY.reload(BlueprintPanel::setStatus);
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

    private static void renameEntry(BlueprintEntry entry, String requestedName) {
        if (entry == null || !LIBRARY.contains(entry)) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.no_selection", "");
            return;
        }
        applyFileOperation(BlueprintLibraryFileOperations.rename(entry, requestedName));
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
            if (!CAPTURE.confirmSingleBlockSelection(BlueprintPanel::setStatus)) {
                setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
                return;
            }
        }
        openCaptureNameDialog();
    }

    static void saveCapturedAreaAs(String requestedName) {
        if (!isCaptureSelectionComplete()) {
            if (!CAPTURE.confirmSingleBlockSelection(BlueprintPanel::setStatus)) {
                setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
                return;
            }
        }
        String cleanName = sanitizeFileBase(stripBlueprintExtension(requestedName == null ? "" : requestedName));
        if (cleanName.isBlank()) {
            setStatus(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.name_required", "");
            return;
        }
        startCaptureSave(cleanName);
    }

    private static void startCaptureSave(String requestedName) {
        BlueprintCaptureSaveCoordinator.start(
                CAPTURE,
                Minecraft.getInstance().level,
                requestedName,
                BlueprintPanel::setStatus);
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
        int index = LIBRARY.indexOfFileName(fileName);
        if (index >= 0) {
            selectedIndex = index;
            applyDefaultRotation(LIBRARY.get(index));
        }
    }

    private static BlueprintEntry selectedEntry() {
        return selectedIndex >= 0 && selectedIndex < LIBRARY.size() ? LIBRARY.get(selectedIndex) : null;
    }

    private static String shortPos(BlockPos pos) {
        return pos == null ? "-" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static void selectEntry(BlueprintEntry entry) {
        selectedIndex = LIBRARY.indexOf(entry);
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
