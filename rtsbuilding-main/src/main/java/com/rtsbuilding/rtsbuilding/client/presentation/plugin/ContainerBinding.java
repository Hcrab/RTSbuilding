package com.rtsbuilding.rtsbuilding.client.presentation.plugin;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.OverlayContext;
import com.rtsbuilding.rtsbuilding.client.presentation.plugin.binding.*;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public class ContainerBinding {

    private final OverlayContext context;
    private final ScrollBar scrollBar = new ScrollBar();
    private final List<RowLayout> rowLayouts = new ArrayList<>();
    private final PriorityEditController editController;
    private final EntryAnimationController animController = new EntryAnimationController();
    private final BindingRenderer renderer;
    private final BindingInputHandler inputHandler;

    public ContainerBinding(OverlayContext context) {
        this.context = context;
        this.editController = new PriorityEditController(rowLayouts);
        this.renderer = new BindingRenderer(context, scrollBar, rowLayouts, editController, animController);
        this.inputHandler = new BindingInputHandler(context, scrollBar, rowLayouts, editController);
    }

    public void renderContent(GuiGraphics g) {
        renderer.renderContent(g);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return inputHandler.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return inputHandler.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return inputHandler.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return inputHandler.charTyped(codePoint, modifiers);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return inputHandler.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return inputHandler.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
}
