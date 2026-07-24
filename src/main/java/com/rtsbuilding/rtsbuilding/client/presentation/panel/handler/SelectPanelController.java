package com.rtsbuilding.rtsbuilding.client.presentation.panel.handler;

import com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.select.*;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.pass.BoxSelector;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


public final class SelectPanelController {

    private final SelectionHighlight highlight;

    
    @Nullable
    private SelectPanel selectPanel;

    public SelectPanelController(SelectionHighlight highlight) {
        this.highlight = highlight;
    }

    

    
    public EventResult show(List<SelectableEntry> entries, Vec3 rayOrigin, Vec3 rayDir,
                             BoxSelector sel, BuilderScreen screen, int mouseX, int mouseY) {
        close(); 
        selectPanel = new SelectPanel(entries, highlight, rayOrigin, rayDir);
        selectPanel.init(screen);
        screen.getFloatingWindowLayer().frontToBackWindows().add(selectPanel);

        int popupW = selectPanel.getDefaultWidth();
        int popupH = selectPanel.getDefaultHeight();
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int px = Math.max(0, Math.min(mouseX + 8, screenW - popupW));
        int py = Math.max(0, Math.min(mouseY - popupH / 2, screenH - popupH));
        selectPanel.setBounds(px, py, popupW, popupH);
        selectPanel.setOpen(true);
        return EventResult.CONSUMED;
    }

    

    
    public void validate(BuilderScreen screen, List<Entity> currentEntities, BoxTargetCollector.BoxSelectorCache sel) {
        if (selectPanel == null || !selectPanel.isOpen()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { close(); return; }

        
        if (sel == null || sel.minCorner() == null || sel.maxCorner() == null) {
            close();
            return;
        }

        
        List<SelectableEntry> oldEntries = selectPanel.getEntries();
        List<SelectableEntry> newEntries = new ArrayList<>();
        for (SelectableEntry entry : oldEntries) {
            switch (entry) {
                case EntityEntry ee -> {
                    if (ee.entity() != null && ee.entity().isAlive()
                            && currentEntities.contains(ee.entity())) {
                        newEntries.add(entry);
                    }
                }
                case BlockEntry be -> newEntries.add(entry); 
            }
        }

        
        if (newEntries.size() == oldEntries.size()) return;

        
        if (newEntries.isEmpty()) {
            close();
            return;
        }

        
        selectPanel.updateEntries(newEntries);
    }

    

    
    public boolean isOpen() {
        return selectPanel != null && selectPanel.isOpen();
    }

    
    public void close() {
        if (selectPanel != null && selectPanel.isOpen()) {
            selectPanel.setOpen(false);
            removeFromFloatingLayer();
            selectPanel = null;
        }
    }

    

    private void removeFromFloatingLayer() {
        if (selectPanel != null && selectPanel.getScreen() != null) {
            selectPanel.getScreen().getFloatingWindowLayer()
                    .frontToBackWindows().remove(selectPanel);
        }
    }
}
