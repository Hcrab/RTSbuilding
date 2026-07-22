package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import net.minecraft.client.gui.Font;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintLibraryLayout;

import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanelUi.text;

/**
 * Computes the stable geometry for the blueprint bottom panel controls.
 */
final class BlueprintPanelLayout {
    static final int LIST_COLUMN_GAP = BlueprintLibraryLayout.LIST_COLUMN_GAP;

    private BlueprintPanelLayout() {
    }

    static int listColumns(int width) {
        return BlueprintLibraryLayout.listColumns(width);
    }

    static int listCellWidth(int width, int columns) {
        return BlueprintLibraryLayout.listCellWidth(width, columns);
    }

    static int maxListScroll(int entryCount, int columns, int visibleRows) {
        return BlueprintLibraryLayout.maxListScroll(entryCount, columns, visibleRows);
    }

    static RowActionLayout rowActionLayout(Font font, int cellX, int rowY, int cellWidth) {
        BlueprintLibraryLayout.RowActions shared = BlueprintLibraryLayout.rowActions(
                cellX, rowY, cellWidth,
                font.width(text("screen.rtsbuilding.blueprints.save_as_short")),
                font.width(text("screen.rtsbuilding.blueprints.rename")),
                font.width(text("screen.rtsbuilding.blueprints.delete")));
        return new RowActionLayout(
                shared.saveX, shared.saveW, shared.renameX, shared.renameW,
                shared.deleteX, shared.deleteW, shared.buttonY);
    }

    static NameDialogLayout nameDialogLayout(int screenWidth, int screenHeight, boolean captureDialog) {
        int width = Math.min(420, Math.max(300, screenWidth - 48));
        int height = captureDialog ? 136 : 118;
        int x = (screenWidth - width) / 2;
        int y = Math.max(24, (screenHeight - height) / 2);
        int inputX = x + 10;
        int inputY = y + (captureDialog ? 76 : 62);
        int inputWidth = width - 20;
        int cancelWidth = 58;
        int confirmWidth = 70;
        int buttonY = y + height - 24;
        int cancelX = x + width - cancelWidth - 10;
        int confirmX = cancelX - confirmWidth - 6;
        return new NameDialogLayout(
                x,
                y,
                width,
                height,
                inputX,
                inputY,
                inputWidth,
                confirmX,
                confirmWidth,
                cancelX,
                cancelWidth,
                buttonY);
    }

    static TopBarLayout topBarLayout(Font font, int x, int width, boolean captureActive) {
        String captureKey = captureActive
                ? "screen.rtsbuilding.blueprints.capture_active_short"
                : "screen.rtsbuilding.blueprints.capture_short";
        BlueprintLibraryLayout.TopBar shared = BlueprintLibraryLayout.topBar(x, width, captureActive,
                font.width(text("screen.rtsbuilding.blueprints.open_folder_short")),
                font.width(text("screen.rtsbuilding.blueprints.import_file_short")),
                font.width(text("screen.rtsbuilding.blueprints.sync_create_short")),
                font.width(text(captureKey)));
        return new TopBarLayout(
                shared.folderX, shared.folderW, shared.importX, shared.importW,
                shared.syncX, shared.syncW, shared.captureX, shared.captureW,
                shared.searchX, shared.searchW);
    }

    record NameDialogLayout(
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

    record RowActionLayout(int saveX, int saveW, int renameX, int renameW, int deleteX, int deleteW, int buttonY) {
    }

    record TopBarLayout(int folderX, int folderW, int importX, int importW, int syncCreateX, int syncCreateW,
            int captureX, int captureW, int searchX, int searchW) {
    }
}
