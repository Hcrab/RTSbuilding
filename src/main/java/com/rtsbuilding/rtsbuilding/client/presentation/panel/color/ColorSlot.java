package com.rtsbuilding.rtsbuilding.client.presentation.panel.color;


public class ColorSlot {

    private final String displayName;
    private final ColorSource source;

    public ColorSlot(String displayName, ColorSource source) {
        this.displayName = displayName;
        this.source = source;
    }

    
    public String displayName() { return displayName; }

    
    public ColorSource source() { return source; }
}
