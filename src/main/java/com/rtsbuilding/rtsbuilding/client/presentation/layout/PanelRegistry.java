package com.rtsbuilding.rtsbuilding.client.presentation.layout;

import com.rtsbuilding.rtsbuilding.client.presentation.event.dispatcher.EventDispatcher;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.api.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.state.HoverStateManager;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.CONSUMED;
import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.PASS;


public final class PanelRegistry {

    
    private record PanelEntry(RtsPanelApi panel, RenderLayer layer) {}

    private final List<PanelEntry> entries = new ArrayList<>();

    

    
    public void register(RtsPanelApi panel, RenderLayer layer) {
        if (panel == null) {
            throw new IllegalArgumentException("不能注册 null 面板");
        }
        entries.add(new PanelEntry(panel, layer));
    }

    
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    
    @SuppressWarnings("unchecked")
    public <T extends RtsPanelApi> T getPanel(Class<T> type) {
        for (PanelEntry entry : entries) {
            if (type.isInstance(entry.panel())) {
                return (T) entry.panel();
            }
        }
        return null;
    }

    

    
    public void initAll(BuilderScreen screen) {
        for (PanelEntry entry : entries) {
            entry.panel().init(screen);
        }
    }

    

    
    public void renderLayer(GuiGraphics g, int mouseX, int mouseY, float partialTick, RenderLayer layer) {
        for (PanelEntry entry : entries) {
            if (entry.layer() == layer) {
                entry.panel().render(g, mouseX, mouseY, partialTick);
            }
        }
    }

    
    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY, RenderLayer layer) {
        for (PanelEntry entry : entries) {
            if (entry.layer() == layer) {
                entry.panel().renderOverlays(g, mouseX, mouseY);
            }
        }
    }

    
    public void renderContentPanels(GuiGraphics g, int mouseX, int mouseY, float partialTick,
                                     boolean mouseOverFloating) {
        if (mouseOverFloating) {
            HoverStateManager.floatingWindowSuppression().setSuppressed(true);
        }
        try {
            for (PanelEntry entry : entries) {
                if (entry.layer() == RenderLayer.CONTENT_PANELS) {
                    entry.panel().render(g, mouseX, mouseY, partialTick);
                }
            }
        } finally {
            if (mouseOverFloating) {
                HoverStateManager.floatingWindowSuppression().setSuppressed(false);
            }
        }
    }

    

    
    public void registerContentPanelMouseClick(EventDispatcher dispatcher) {
        dispatcher.onMouseClick(event -> {
            for (PanelEntry entry : entries) {
                if (entry.layer() == RenderLayer.CONTENT_PANELS
                        && entry.panel().mouseClicked(event.x(), event.y(), event.button())) {
                    return CONSUMED;
                }
            }
            return PASS;
        }, EventDispatcher.P_UI_PANEL);
    }

    
    public void registerContentPanelMouseRelease(EventDispatcher dispatcher) {
        dispatcher.onMouseRelease(event -> {
            for (PanelEntry entry : entries) {
                if (entry.layer() == RenderLayer.CONTENT_PANELS) {
                    entry.panel().mouseReleased(event.x(), event.y(), event.button());
                }
            }
            return PASS; 
        }, EventDispatcher.P_UI_PANEL);
    }

    
    public void registerContentPanelMouseDrag(EventDispatcher dispatcher) {
        dispatcher.onMouseDrag(event -> {
            
            double clampedDx = Math.abs(event.dx()) > 200 ? 0 : event.dx();
            double clampedDy = Math.abs(event.dy()) > 200 ? 0 : event.dy();
            for (PanelEntry entry : entries) {
                if (entry.layer() == RenderLayer.CONTENT_PANELS) {
                    if (entry.panel().mouseDragged(event.x(), event.y(), event.button(),
                            clampedDx, clampedDy)) {
                        return CONSUMED;
                    }
                }
            }
            return PASS;
        }, EventDispatcher.P_UI_PANEL);
    }

    
    public void registerContentPanelMouseScroll(EventDispatcher dispatcher) {
        dispatcher.onMouseScroll(event -> {
            for (PanelEntry entry : entries) {
                if (entry.layer() == RenderLayer.CONTENT_PANELS) {
                    if (entry.panel().mouseScrolled(event.x(), event.y(), event.scrollX(), event.scrollY())) {
                        return CONSUMED;
                    }
                }
            }
            return PASS;
        }, EventDispatcher.P_UI_PANEL);
    }

    
    public void registerContentPanelKeyPress(EventDispatcher dispatcher) {
        dispatcher.onKeyPress(event -> {
            for (PanelEntry entry : entries) {
                if (entry.layer() == RenderLayer.CONTENT_PANELS) {
                    if (entry.panel().keyPressed(event.keyCode(), event.scanCode(), event.modifiers())) {
                        return CONSUMED;
                    }
                }
            }
            return PASS;
        }, EventDispatcher.P_UI_PANEL);
    }

    
    public void registerContentPanelCharTyped(EventDispatcher dispatcher) {
        dispatcher.onChar(event -> {
            for (PanelEntry entry : entries) {
                if (entry.layer() == RenderLayer.CONTENT_PANELS) {
                    if (entry.panel().charTyped(event.codePoint(), event.modifiers())) {
                        return CONSUMED;
                    }
                }
            }
            return PASS;
        }, EventDispatcher.P_UI_PANEL);
    }
}
