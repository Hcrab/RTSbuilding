package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.selection.RtsSelectionNudge;
import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintDialogState;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiAction;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiState;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintUiAction;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintLibraryLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintLibraryStyle;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.Collections;

import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanelFiles.sanitizeFileBase;
import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanelFiles.stripBlueprintExtension;

/**
 * 蓝图功能的稳定公共门面。
 *
 * <p>本类只负责把屏幕、Core 动作和世界输入分派给仓储、放置、捕获及弹窗 owner；
 * 不再直接拥有仓储列表、旋转/虚影状态或文件任务结果编排。</p>
 */
public final class BlueprintPanel {
    private static final BlueprintCaptureController CAPTURE = new BlueprintCaptureController();
    private static final BlueprintDialogState<BlueprintEntry> DIALOGS = new BlueprintDialogState<>();
    private static final BlueprintLibrarySession LIBRARY = new BlueprintLibrarySession(
            BlueprintPanel::setStatus, BlueprintPanel::onLibrarySelectionChanged);
    private static final BlueprintPlacementSession PLACEMENT = new BlueprintPlacementSession(
            LIBRARY::selectedEntry, BlueprintPanel::setStatus);
    private static ITextComponent statusText = new TextComponentTranslation(
            "screen.rtsbuilding.blueprints.status.ready");
    private static int statusColor = BlueprintLibraryStyle.STATUS_DEFAULT_TEXT.toArgb();

    private BlueprintPanel() {
    }

    public static void render(LegacyGuiGraphics graphics, FontRenderer font, ClientRtsController controller,
            int x, int y, int width, int height, int mouseX, int mouseY) {
        if (!Config.areBlueprintsEnabled()) {
            CAPTURE.clearSilently();
            BlueprintLibraryPanelRenderer.renderDisabled(
                    graphics, font, x, y, width, height);
            return;
        }
        tickCaptureSaveJob();
        LIBRARY.ensureLoaded();
        BlueprintLibraryLayout.Geometry geometry =
                BlueprintLibraryLayout.geometry(x, y, width, height);
        BlueprintLibraryUiState library = BlueprintLibraryUiAdapter.snapshotForViewport(
                controller, geometry.listW, geometry.listH);
        BlueprintLibraryPanelRenderer.render(
                graphics, font, library, x, y, width, height, mouseX, mouseY);
    }

    public static boolean mouseClicked(double mouseX, double mouseY,
            int x, int y, int width, int height, ClientRtsController controller) {
        if (!Config.areBlueprintsEnabled()) {
            LIBRARY.setSearchFocused(false);
            setStatus(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.disabled", "");
            return true;
        }
        return BlueprintLibraryPanelInput.mouseClicked(
                mouseX, mouseY, Minecraft.getMinecraft().fontRenderer,
                x, y, width, height,
                BlueprintLibraryUiAdapter.snapshot(controller), controller);
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double scrollY,
            int x, int y, int width, int height, ClientRtsController controller) {
        if (!Config.areBlueprintsEnabled()) return false;
        return BlueprintLibraryPanelInput.mouseScrolled(
                mouseX, mouseY, scrollY, x, y, width, height,
                BlueprintLibraryUiAdapter.snapshot(controller), controller);
    }

    public static boolean keyPressed(int keyCode, int scanCode,
            ClientRtsController controller) {
        if (!Config.areBlueprintsEnabled()) {
            LIBRARY.setSearchFocused(false);
            return false;
        }
        BlueprintLibraryUiState library = BlueprintLibraryUiAdapter.snapshot(controller);
        if (CAPTURE.isActive()) {
            LIBRARY.setSearchFocused(false);
            return BlueprintCaptureInputRouter.keyPressed(
                    CAPTURE, keyCode, scanCode, BlueprintPanel::setStatus,
                    BlueprintPanel::cancelCaptureMode, BlueprintPanel::saveCapturedArea);
        }
        boolean cancelKey = matches(ClientKeyMappings.BLUEPRINT_CANCEL, keyCode);
        if (!library.searchFocused && hasSelectedBlueprint()
                && isBlueprintRotateKey(keyCode, scanCode)) {
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
        if (!library.searchFocused) return false;
        if (keyCode == Keyboard.KEY_BACK) {
            if (!library.query.isEmpty()) {
                BlueprintLibraryUiAdapter.dispatch(BlueprintLibraryUiAction.text(
                        BlueprintLibraryUiAction.Type.SET_QUERY,
                        library.query.substring(0, library.query.length() - 1)), controller);
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
            BlueprintLibraryUiAdapter.dispatch(BlueprintLibraryUiAction.simple(
                    BlueprintLibraryUiAction.Type.BLUR_SEARCH), controller);
            return true;
        }
        return false;
    }

    public static boolean charTyped(char codePoint, ClientRtsController controller) {
        if (!Config.areBlueprintsEnabled()) return false;
        BlueprintLibraryUiState library = BlueprintLibraryUiAdapter.snapshot(controller);
        if (!library.searchFocused || Character.isISOControl(codePoint)) return false;
        if (library.query.length() < 96) {
            BlueprintLibraryUiAdapter.dispatch(BlueprintLibraryUiAction.text(
                    BlueprintLibraryUiAction.Type.SET_QUERY, library.query + codePoint), controller);
        }
        return true;
    }

    public static boolean isPlacementSessionActive() {
        return Config.areBlueprintsEnabled() && (CAPTURE.isActive() || PLACEMENT.hasSelection());
    }

    public static boolean isBlueprintRotateKey(int keyCode, int scanCode) {
        return matches(ClientKeyMappings.ROTATE_SHAPE, keyCode)
                || matches(ClientKeyMappings.MODE_ROTATE, keyCode);
    }

    public static boolean hasSelectedBlueprint() {
        return Config.areBlueprintsEnabled() && PLACEMENT.hasSelection();
    }

    static String selectedBlueprintName() {
        BlueprintEntry entry = LIBRARY.selectedEntry();
        return entry == null ? "" : entry.name();
    }

    static String selectedBlueprintSizeText() {
        BlueprintEntry entry = LIBRARY.selectedEntry();
        return entry == null ? "" : entry.sizeText();
    }

    static void selectRelativeBlueprint(int delta) {
        LIBRARY.selectRelative(delta);
    }

    public static int getYRotationSteps() { return PLACEMENT.yRotationSteps(); }
    public static int getXRotationSteps() { return PLACEMENT.xRotationSteps(); }
    public static int getZRotationSteps() { return PLACEMENT.zRotationSteps(); }
    public static BlockPos getPinnedAnchor() { return PLACEMENT.pinnedAnchor(); }
    public static boolean hasPinnedPreview() {
        return Config.areBlueprintsEnabled() && PLACEMENT.hasPinnedPreview();
    }

    public static boolean pinSelected(BlockPos anchor) { return PLACEMENT.pin(anchor); }
    public static BlockPos anchorForCursorTarget(BlockPos cursorTarget) {
        return PLACEMENT.anchorForCursorTarget(cursorTarget);
    }
    public static BlueprintGhostPreview createGhostPreview(BlockPos anchor,
            int yRotationSteps, ClientRtsController controller) {
        return PLACEMENT.createGhostPreview(anchor, yRotationSteps, controller);
    }
    public static boolean placeSelected(BlockPos anchor, int yRotationSteps,
            int xRotationSteps, int zRotationSteps) {
        return PLACEMENT.place(anchor, yRotationSteps, xRotationSteps, zRotationSteps);
    }
    public static boolean confirmPinnedPreview() { return PLACEMENT.confirmPinnedPreview(); }
    static boolean rotateSelectedBlueprintY(int step) { return PLACEMENT.rotateY(step); }
    static boolean rotateSelectedBlueprintX(int step) { return PLACEMENT.rotateX(step); }
    static boolean rotateSelectedBlueprintZ(int step) { return PLACEMENT.rotateZ(step); }
    static void resetSelectedBlueprintRotation() { PLACEMENT.resetRotation(); }
    static boolean nudgePinnedAnchor(int dx, int dy, int dz,
            ClientRtsController controller) {
        return PLACEMENT.nudge(dx, dy, dz, controller);
    }
    static boolean setPinnedAnchor(BlockPos anchor, ClientRtsController controller) {
        return PLACEMENT.setPinnedAnchor(anchor, controller);
    }
    static boolean nudgePinnedAnchorRelative(int right, int forward, int up,
            ClientRtsController controller) {
        return PLACEMENT.nudgeRelative(right, forward, up, controller);
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
    public static BlockPos getCapturePointA() { return CAPTURE.pointA(); }
    public static BlockPos getCapturePointB() { return CAPTURE.pointB(); }
    static String capturePointAText() { return shortPos(CAPTURE.displayPointA()); }
    static String capturePointBText() { return shortPos(CAPTURE.displayPointB()); }
    static String captureSizeText() { return CAPTURE.sizeText(); }
    static int captureSizeX() { return CAPTURE.sizeX(); }
    static int captureSizeY() { return CAPTURE.sizeY(); }
    static int captureSizeZ() { return CAPTURE.sizeZ(); }
    static long countCaptureBlocks() {
        return CAPTURE.countCapturableBlocks(Minecraft.getMinecraft().world);
    }
    static String captureSaveProgressLine() { return CAPTURE.saveProgressLine(); }
    public static void updateCaptureHoverPoint(BlockPos pos) { CAPTURE.updateHoverPoint(pos); }
    public static void updateCaptureHover(Vec3d origin, Vec3d direction, BlockPos pos) {
        CAPTURE.updateHoverPoint(pos);
        CAPTURE.updateHandleHover(origin, direction);
    }
    public static BlockPos getCapturePreviewPointB() { return CAPTURE.previewPointB(); }
    public static com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox
            getCapturePreviewBoxForRender() { return CAPTURE.previewBox(); }
    public static AxisAlignedBB getCapturePreviewAabbForRender() { return CAPTURE.previewAabbForRender(); }
    public static EnumFacing getCaptureHoveredHandleDirection() {
        return CAPTURE.hoveredHandleDirection();
    }
    public static EnumFacing getCaptureActiveHandleDirection() {
        return CAPTURE.activeHandleDirection();
    }
    public static boolean releaseCaptureActiveHandleIfDragged() {
        return Config.areBlueprintsEnabled() && CAPTURE.releaseActiveHandleIfDragged();
    }
    public static List<BlockPos> getCaptureIncludedBlocksForRender(int limit) {
        return Config.areBlueprintsEnabled()
                ? CAPTURE.includedBlocksForRender(Minecraft.getMinecraft().world, limit)
                : Collections.<BlockPos>emptyList();
    }
    public static boolean shouldRenderCaptureBlockHighlights(int limit) {
        return Config.areBlueprintsEnabled() && CAPTURE.shouldRenderBlockHighlights(limit);
    }
    public static List<BlockPos> getCaptureExcludedBlocksForRender(int limit) {
        return Config.areBlueprintsEnabled()
                ? CAPTURE.excludedBlocksForRender(limit)
                : Collections.<BlockPos>emptyList();
    }
    public static boolean acceptCapturePoint(BlockPos pos) {
        return pos != null && BlueprintUiStateAdapter.dispatch(BlueprintUiAction.vector(
                BlueprintUiAction.Type.ACCEPT_CAPTURE_POINT,
                pos.getX(), pos.getY(), pos.getZ()), null);
    }
    static boolean acceptCapturePointDirect(BlockPos pos) {
        return Config.areBlueprintsEnabled()
                && CAPTURE.acceptPoint(pos, BlueprintPanel::setStatus);
    }
    public static boolean handleCaptureWorldAction(
            RayTraceResult hit, Vec3d origin, Vec3d direction) {
        return Config.areBlueprintsEnabled()
                && CAPTURE.handleWorldAction(hit, origin, direction, BlueprintPanel::setStatus);
    }
    public static boolean toggleCaptureBlockExclusion(BlockPos pos) {
        return Config.areBlueprintsEnabled()
                && CAPTURE.toggleBlockExclusion(pos, BlueprintPanel::setStatus);
    }
    public static boolean cancelCaptureFromClick() {
        if (!Config.areBlueprintsEnabled() || !CAPTURE.isActive()) return false;
        if (CAPTURE.isSaving()) {
            setStatus(S2CBlueprintStatusPayload.INFO,
                    "screen.rtsbuilding.blueprints.status.save_busy", "");
        } else {
            cancelCaptureMode();
        }
        return true;
    }
    static void moveCaptureSelection(int dx, int dy, int dz) {
        if (Config.areBlueprintsEnabled()) {
            CAPTURE.moveSelection(dx, dy, dz, BlueprintPanel::setStatus);
        }
    }
    static void adjustCaptureSize(int dx, int dy, int dz) {
        if (Config.areBlueprintsEnabled()) {
            CAPTURE.resizeSelection(dx, dy, dz, BlueprintPanel::setStatus);
        }
    }
    public static boolean mouseScrolledCaptureHeight(double scrollY, boolean fast) {
        if (!Config.areBlueprintsEnabled() || !CAPTURE.isActive()) return false;
        if (CAPTURE.isSaving()) {
            setStatus(S2CBlueprintStatusPayload.INFO,
                    "screen.rtsbuilding.blueprints.status.save_busy", "");
            return true;
        }
        return CAPTURE.handleScroll(scrollY, fast, BlueprintPanel::setStatus);
    }
    public static boolean mouseDraggedCaptureHandle(
            double dragX, double dragY, double axisX, double axisY) {
        if (!Config.areBlueprintsEnabled() || !CAPTURE.isActive()) return false;
        if (CAPTURE.isSaving()) {
            setStatus(S2CBlueprintStatusPayload.INFO,
                    "screen.rtsbuilding.blueprints.status.save_busy", "");
            return true;
        }
        return CAPTURE.handleDrag(dragX, dragY, axisX, axisY, BlueprintPanel::setStatus);
    }
    static void setCaptureSize(int x, int y, int z) {
        if (Config.areBlueprintsEnabled()) {
            CAPTURE.setSelectionSize(x, y, z, BlueprintPanel::setStatus);
        }
    }

    public static boolean isMaterialDialogOpen() { return DIALOGS.isMaterialOpen(); }
    public static boolean isNameDialogOpen() { return DIALOGS.isNameOpen(); }
    static boolean isNameDialogCaptureMode() { return DIALOGS.isCaptureNameOpen(); }
    static String nameDialogValue() { return DIALOGS.nameValue(); }
    static boolean nameDialogReplaceOnType() { return DIALOGS.replaceOnFirstInput(); }
    static void setNameDialogValueFromUi(String value) {
        if (isNameDialogOpen()) DIALOGS.setNameValue(value);
    }
    static BlueprintEntry nameDialogEntry() { return DIALOGS.nameEntry(); }
    static BlockPos nameDialogCapturePointA() { return CAPTURE.displayPointA(); }
    static BlockPos nameDialogCapturePointB() { return CAPTURE.displayPointB(); }
    static long nameDialogCaptureBlockCount() { return DIALOGS.captureBlockCount(); }
    static void confirmActiveNameDialog() { confirmNameDialog(); }
    static void cancelActiveNameDialog() { cancelNameDialog(); }
    static BlueprintEntry materialDialogEntry() { return LIBRARY.selectedEntry(); }
    static int materialDialogScroll() { return DIALOGS.materialScroll(); }
    static void setMaterialDialogScroll(int scroll) { DIALOGS.setMaterialScroll(scroll); }
    static void closeMaterialDialog() { DIALOGS.closeMaterial(); }
    static void openMaterialDialog() {
        if (LIBRARY.selectedEntry() == null) {
            setStatus(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.no_selection", "");
        } else {
            DIALOGS.openMaterial();
        }
    }

    static int selectedBlueprintIndex() { return LIBRARY.selectedIndex(); }
    static int blueprintEntryCount() { return LIBRARY.size(); }
    static List<BlueprintEntry> libraryEntries() { return LIBRARY.entries(); }
    static BlueprintEntry librarySelectedEntry() { return LIBRARY.selectedEntry(); }
    static String libraryQuery() { return LIBRARY.query(); }
    static boolean librarySearchFocused() { return LIBRARY.searchFocused(); }
    static int libraryScrollRows() { return LIBRARY.scrollRows(); }
    static void applyLibraryViewState(String query, boolean focused, int scrollRows) {
        LIBRARY.applyViewState(query, focused, scrollRows);
    }
    static void openBlueprintFolderFromUi() {
        LIBRARY.applyFileOperation(BlueprintLibraryFileOperations.openFolder());
    }
    static void importBlueprintFileFromUi() {
        LIBRARY.applyFileOperation(BlueprintLibraryFileOperations.importFile());
    }
    static void syncCreateBlueprintsFromUi() {
        LIBRARY.applyFileOperation(BlueprintLibraryFileOperations.syncOtherMods());
    }
    static void toggleCaptureModeFromUi() { toggleCaptureMode(); }
    static boolean selectLibraryEntry(String fileName) { return LIBRARY.selectByFileName(fileName); }
    static boolean saveLibraryEntryAs(String fileName) { return LIBRARY.saveAs(fileName); }
    static boolean renameLibraryEntry(String fileName) {
        BlueprintEntry entry = LIBRARY.entryByFileName(fileName);
        if (entry == null || !entry.error().trim().isEmpty()) return false;
        openRenameDialog(entry);
        return true;
    }
    static boolean deleteLibraryEntry(String fileName) { return LIBRARY.delete(fileName); }

    static ITextComponent statusText() { return statusText; }
    static int statusColor() { return statusColor; }
    public static void setStatus(byte status, String messageKey, String detail) {
        statusText = detail == null || detail.trim().isEmpty()
                ? new TextComponentTranslation(messageKey)
                : new TextComponentTranslation(messageKey, detail);
        if (status == S2CBlueprintStatusPayload.SUCCESS) {
            statusColor = BlueprintLibraryStyle.STATUS_SUCCESS_TEXT.toArgb();
        } else if (status == S2CBlueprintStatusPayload.ERROR) {
            statusColor = BlueprintLibraryStyle.STATUS_ERROR_TEXT.toArgb();
        } else {
            statusColor = BlueprintLibraryStyle.STATUS_DEFAULT_TEXT.toArgb();
        }
    }

    public static void reload() {
        DIALOGS.closeMaterial();
        LIBRARY.reload();
    }

    public static void saveCapturedArea() {
        if (CAPTURE.isSaving()) {
            setStatus(S2CBlueprintStatusPayload.INFO,
                    "screen.rtsbuilding.blueprints.status.save_busy", "");
            return;
        }
        World level = Minecraft.getMinecraft().world;
        if (level == null) {
            setStatus(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.save_failed", "No world");
            return;
        }
        if (!CAPTURE.isSelectionComplete()
                && !CAPTURE.confirmSingleBlockSelection(BlueprintPanel::setStatus)) {
            setStatus(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
            return;
        }
        openCaptureNameDialog();
    }

    static void saveCapturedAreaAs(String requestedName) {
        if (!isCaptureSelectionComplete()
                && !CAPTURE.confirmSingleBlockSelection(BlueprintPanel::setStatus)) {
            setStatus(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
            return;
        }
        String cleanName = sanitizeFileBase(stripBlueprintExtension(
                requestedName == null ? "" : requestedName));
        if (cleanName.trim().isEmpty()) {
            setStatus(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.name_required", "");
            return;
        }
        startCaptureSave(cleanName);
    }

    static void cancelCaptureMode() {
        CAPTURE.cancel(BlueprintPanel::setStatus);
        DIALOGS.clearName();
    }

    static void clearSelectedBlueprint() {
        LIBRARY.clearSelection();
        PLACEMENT.clear();
        DIALOGS.closeMaterial();
        setStatus(S2CBlueprintStatusPayload.INFO,
                "screen.rtsbuilding.blueprints.status.preview_cleared", "");
    }

    private static void toggleCaptureMode() {
        if (CAPTURE.isSaving()) {
            setStatus(S2CBlueprintStatusPayload.INFO,
                    "screen.rtsbuilding.blueprints.status.save_busy", "");
        } else if (CAPTURE.isActive()) {
            cancelCaptureMode();
        } else {
            CAPTURE.start(BlueprintPanel::setStatus);
            PLACEMENT.clearPinnedAnchor();
            DIALOGS.clearAll();
        }
    }

    private static void openCaptureNameDialog() {
        DIALOGS.openCaptureName(
                sanitizeFileBase("captured_" + System.currentTimeMillis()),
                CAPTURE.countCapturableBlocks(Minecraft.getMinecraft().world));
        LIBRARY.setSearchFocused(false);
    }

    private static void openRenameDialog(BlueprintEntry entry) {
        DIALOGS.openRename(
                sanitizeFileBase(stripBlueprintExtension(entry.fileName())), entry);
        LIBRARY.setSearchFocused(false);
    }

    private static void cancelNameDialog() {
        BlueprintDialogState.NameMode previous = DIALOGS.cancelName();
        setStatus(S2CBlueprintStatusPayload.INFO,
                previous == BlueprintDialogState.NameMode.RENAME_ENTRY
                        ? "screen.rtsbuilding.blueprints.status.rename_cancelled"
                        : "screen.rtsbuilding.blueprints.status.save_cancelled", "");
    }

    private static void confirmNameDialog() {
        if (!isNameDialogOpen()) return;
        String cleanName = sanitizeFileBase(stripBlueprintExtension(DIALOGS.nameValue()));
        if (cleanName.trim().isEmpty()) {
            setStatus(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.name_required", "");
            return;
        }
        BlueprintDialogState.Confirmation<BlueprintEntry> confirmation = DIALOGS.consumeName();
        if (confirmation.mode() == BlueprintDialogState.NameMode.CAPTURE_SAVE) {
            startCaptureSave(cleanName);
        } else if (confirmation.mode() == BlueprintDialogState.NameMode.RENAME_ENTRY) {
            LIBRARY.rename(confirmation.entry(), cleanName);
        }
    }

    private static void startCaptureSave(String requestedName) {
        BlueprintCaptureSaveCoordinator.start(
                CAPTURE, Minecraft.getMinecraft().world,
                requestedName, BlueprintPanel::setStatus);
    }

    private static void tickCaptureSaveJob() {
        BlueprintCaptureSaveCoordinator.Completion completion = LIBRARY.pollCaptureSave(CAPTURE);
        if (completion != null) {
            setStatus(completion.status(), completion.messageKey(), completion.detail());
        }
    }

    private static void onLibrarySelectionChanged(BlueprintEntry entry) {
        PLACEMENT.onSelectionChanged(entry);
        DIALOGS.closeMaterial();
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private static boolean matches(KeyBinding binding, int keyCode) {
        return binding != null && binding.getKeyCode() == keyCode;
    }

    private static String shortPos(BlockPos pos) {
        return pos == null ? "-" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
