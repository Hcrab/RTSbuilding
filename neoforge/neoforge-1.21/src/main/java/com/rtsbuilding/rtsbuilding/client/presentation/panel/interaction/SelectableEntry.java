package com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction;

public sealed interface SelectableEntry permits EntityEntry, BlockEntry {

    
    String displayName();

    
    Object identifier();
}
