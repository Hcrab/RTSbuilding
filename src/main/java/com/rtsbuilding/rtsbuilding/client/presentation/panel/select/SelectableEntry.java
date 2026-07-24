package com.rtsbuilding.rtsbuilding.client.presentation.panel.select;

public sealed interface SelectableEntry permits EntityEntry, BlockEntry {

    
    String displayName();

    
    Object identifier();
}
