package com.rtsbuilding.rtsbuilding.client.input;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public final class InputPipeline {

    private final List<InputLayer> layers = new ArrayList<>();

    
    public void registerLayer(InputLayer layer) {
        this.layers.add(layer);
    }

    
    @SuppressWarnings("unchecked")
    public <T extends InputLayer> T findLayer(Class<T> type) {
        for (InputLayer layer : layers) {
            if (type.isInstance(layer)) return (T) layer;
        }
        return null;
    }

    
    public void onTickPre() {
        Minecraft mc = Minecraft.getInstance();
        for (InputLayer layer : layers) {
            if (layer.isActive()) {
                layer.onTickPre(mc);
            }
        }
    }

    
    public void onTickPost() {
        Minecraft mc = Minecraft.getInstance();
        for (InputLayer layer : layers) {
            if (layer.isActive()) {
                layer.onTickPost(mc);
            }
        }
    }

    
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        for (InputLayer layer : layers) {
            if (layer.isActive() && layer.onMouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        for (InputLayer layer : layers) {
            if (layer.isActive() && layer.onMouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    
    public boolean onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (InputLayer layer : layers) {
            if (layer.isActive() && layer.onMouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    
    public boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (InputLayer layer : layers) {
            if (layer.isActive() && layer.onMouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return false;
    }

    
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        for (InputLayer layer : layers) {
            if (layer.isActive() && layer.onKeyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    
    public boolean onCharTyped(char codePoint, int modifiers) {
        for (InputLayer layer : layers) {
            if (layer.isActive() && layer.onCharTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return false;
    }
}
