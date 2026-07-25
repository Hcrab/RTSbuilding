package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Unified RTS panel interface.
 * <p>
 * All RTS UI panels implement this interface, which is orchestrated
 * by {@link BuilderScreen} through the init / tick / render / event dispatch lifecycle.
 */
public interface RtsPanel {

    /** Initialises the panel, called each time the screen is initted */
    default void init(BuilderScreen screen, ClientRtsController controller) {}

    /** Ticks the panel state each tick */
    default void tick() {}

    /** Renders the panel content */
    default void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    /** Renders tooltips (after hover detection) */
    default void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {}

    // --- Input events ---

    default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }

    default boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return false; }

    default boolean mouseMoved(double mouseX, double mouseY) { return false; }

    default boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) { return false; }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    default boolean keyReleased(int keyCode, int scanCode, int modifiers) { return false; }

    default boolean charTyped(char codePoint, int modifiers) { return false; }

    /** Called when the panel / screen is closed */
    default void close() {}
}
