package com.rtsbuilding.rtsbuilding.client.presentation.panel.color;

import java.util.Collections;
import java.util.List;


public class ColorGroup {

    private final String groupDisplayName;
    private final List<ColorSlot> slots;

    
    public ColorGroup(String groupDisplayName, List<ColorSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException("ColorGroup 至少需要一个 ColorSlot");
        }
        this.groupDisplayName = groupDisplayName;
        this.slots = List.copyOf(slots);
    }

    
    public String groupDisplayName() { return groupDisplayName; }

    
    public List<ColorSlot> slots() { return slots; }

    
    public int size() { return slots.size(); }

    
    public ColorSlot slot(int index) { return slots.get(index); }

    
    public static ColorGroup single(String groupDisplayName, String slotDisplayName, ColorSource source) {
        return new ColorGroup(groupDisplayName, Collections.singletonList(new ColorSlot(slotDisplayName, source)));
    }
}
