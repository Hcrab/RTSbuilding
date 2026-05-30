package com.rtsbuilding.rtsbuilding.compat.jei;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.rtsbuilding.rtsbuilding.client.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.RtsCraftTerminalScreen;

import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

final class RtsOverlayJeiGlobalGuiHandler implements IGlobalGuiHandler {
    private static final int OVERLAY_MARGIN = 6;
    private static final int CRAFT_PANEL_W = 106;
    private static final int PANEL_GAP = 6;
    private static final int STORAGE_PANEL_W = 212;
    private static final int OVERLAY_W = CRAFT_PANEL_W + PANEL_GAP + STORAGE_PANEL_W;
    private static final int SLOT_PITCH = 20;
    private static final int SLOT_SIZE = 18;
    private static final int COLS = 9;
    private static final int ROWS = 4;
    private static final int QUICKBAR_Y_OFF = 17;
    private static final int GRID_Y_OFF = QUICKBAR_Y_OFF + SLOT_SIZE + 6;
    private static final int RETURN_LABEL_Y_OFF = GRID_Y_OFF + ROWS * SLOT_PITCH + 2;
    private static final int RETURN_Y_OFF = RETURN_LABEL_Y_OFF + 9;
    private static final int OVERLAY_H = RETURN_Y_OFF + SLOT_SIZE + 6;

    private final IIngredientManager ingredientManager;

    RtsOverlayJeiGlobalGuiHandler(IIngredientManager ingredientManager) {
        this.ingredientManager = ingredientManager;
    }

    @Override
    public Collection<Rect2i> getGuiExtraAreas() {
        OverlayContext context = resolveContext();
        if (context == null) {
            return List.of();
        }
        return List.of(new Rect2i(context.panelX(), context.panelY(), OVERLAY_W, OVERLAY_H));
    }

    @Override
    public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(double mouseX, double mouseY) {
        OverlayContext context = resolveContext();
        if (context == null) {
            return Optional.empty();
        }

        int gridX = context.storagePanelX() + 6;
        int gridY = context.panelY() + GRID_Y_OFF;
        int index = resolveOverlaySlotIndex(mouseX, mouseY, gridX, gridY);
        if (index < 0) {
            return Optional.empty();
        }

        List<ClientRtsController.StorageEntry> entries = ClientRtsController.get().getStorageEntries();
        if (index >= entries.size()) {
            return Optional.empty();
        }
        ClientRtsController.StorageEntry entry = entries.get(index);
        ItemStack stack = entry.stack();
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }

        int slotX = gridX + (index % COLS) * SLOT_PITCH;
        int slotY = gridY + (index / COLS) * SLOT_PITCH;
        Rect2i area = new Rect2i(slotX, slotY, SLOT_SIZE, SLOT_SIZE);
        return RtsJeiIngredientCompat.createClickableIngredient(this.ingredientManager, stack, area, true);
    }

    private static OverlayContext resolveContext() {
        ClientRtsController controller = ClientRtsController.get();
        if (!controller.canUseStorageOverlay()) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (screen == null
                || screen instanceof BuilderScreen
                || screen instanceof RtsCraftTerminalScreen
                || !(screen instanceof AbstractContainerScreen<?>)) {
            return null;
        }

        int sw = minecraft.getWindow().getGuiScaledWidth();
        int sh = minecraft.getWindow().getGuiScaledHeight();
        int panelX = Math.max(OVERLAY_MARGIN, (sw - OVERLAY_W) / 2);
        int panelY = resolvePanelY(screen, sh);
        return new OverlayContext(panelX, panelY, panelX + CRAFT_PANEL_W + PANEL_GAP);
    }

    private static int resolvePanelY(Screen screen, int screenHeight) {
        if (screen instanceof CraftingScreen || screen instanceof RtsCraftTerminalScreen) {
            return OVERLAY_MARGIN;
        }
        return screenHeight - OVERLAY_H - OVERLAY_MARGIN;
    }

    private static int resolveOverlaySlotIndex(double mouseX, double mouseY, int gridX, int gridY) {
        if (!inside(mouseX, mouseY, gridX, gridY, COLS * SLOT_PITCH, ROWS * SLOT_PITCH)) {
            return -1;
        }
        int col = (int) ((mouseX - gridX) / SLOT_PITCH);
        int row = (int) ((mouseY - gridY) / SLOT_PITCH);
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) {
            return -1;
        }
        int sx = gridX + col * SLOT_PITCH;
        int sy = gridY + row * SLOT_PITCH;
        if (!inside(mouseX, mouseY, sx, sy, SLOT_SIZE, SLOT_SIZE)) {
            return -1;
        }
        return row * COLS + col;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private record OverlayContext(int panelX, int panelY, int storagePanelX) {
    }
}
