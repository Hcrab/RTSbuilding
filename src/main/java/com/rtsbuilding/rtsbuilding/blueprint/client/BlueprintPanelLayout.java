package com.rtsbuilding.rtsbuilding.blueprint.client;


import net.minecraft.client.gui.Font;

import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelUi.buttonWidth;

/**
 * Computes the stable geometry for the blueprint bottom panel controls.
 */
public final class BlueprintPanelLayout {
    static final int LIST_COLUMN_GAP = 4;

    private BlueprintPanelLayout() {
    }

    static int listColumns(int width) {
        return width >= 320 ? 2 : 1;
    }

    static int listCellWidth(int width, int columns) {
        return Math.max(80, (width - 2 - (columns - 1) * LIST_COLUMN_GAP) / columns);
    }

    static int maxListScroll(int entryCount, int columns, int visibleRows) {
        int rows = Math.max(0, (entryCount + Math.max(1, columns) - 1) / Math.max(1, columns));
        return Math.max(0, rows - visibleRows);
    }

    static RowActionLayout rowActionLayout(Font font, int cellX, int rowY, int cellWidth) {
        int gap = 3;
        int saveWidth = buttonWidth(font, "screen.rtsbuilding.blueprints.save_as_short", 38, 46);
        int renameWidth = buttonWidth(font, "screen.rtsbuilding.blueprints.rename", 38, 48);
        int deleteWidth = buttonWidth(font, "screen.rtsbuilding.blueprints.delete", 34, 42);
        int totalWidth = saveWidth + renameWidth + deleteWidth + gap * 2;
        int x = cellX + Math.max(4, cellWidth - totalWidth - 4);
        int buttonY = rowY + 5;
        return new RowActionLayout(
                x,
                saveWidth,
                x + saveWidth + gap,
                renameWidth,
                x + saveWidth + gap + renameWidth + gap,
                deleteWidth,
                buttonY);
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
        int gap = 4;
        int folderWidth = buttonWidth(font, "screen.rtsbuilding.blueprints.open_folder_short", 64, 96);
        int importWidth = buttonWidth(font, "screen.rtsbuilding.blueprints.import_file_short", 44, 72);
        int syncCreateWidth = buttonWidth(font, "screen.rtsbuilding.blueprints.sync_create_short", 58, 94);
        int captureWidth = buttonWidth(font,
                captureActive
                        ? "screen.rtsbuilding.blueprints.capture_active_short"
                        : "screen.rtsbuilding.blueprints.capture_short",
                74,
                112);
        int actionWidth = folderWidth + importWidth + syncCreateWidth + captureWidth + gap * 3;
        int searchX = x + actionWidth + 8;
        int searchWidth = Math.max(60, x + width - searchX);
        if (searchWidth < 80) {
            folderWidth = 56;
            importWidth = 44;
            syncCreateWidth = 58;
            captureWidth = 70;
            actionWidth = folderWidth + importWidth + syncCreateWidth + captureWidth + gap * 3;
            searchX = x + actionWidth + 6;
            searchWidth = Math.max(50, x + width - searchX);
        }
        int folderX = x;
        int importX = folderX + folderWidth + gap;
        int syncCreateX = importX + importWidth + gap;
        int captureX = syncCreateX + syncCreateWidth + gap;
        return new TopBarLayout(
                folderX,
                folderWidth,
                importX,
                importWidth,
                syncCreateX,
                syncCreateWidth,
                captureX,
                captureWidth,
                searchX,
                searchWidth);
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
