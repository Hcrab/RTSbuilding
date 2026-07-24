package com.rtsbuilding.rtsbuilding.client.presentation.layout;


public final class CategoryTypes {

    
    public record CategoryRow(
            String token,
            String label,
            int depth,
            boolean expandable,
            boolean expanded,
            String modNamespace) {}

    
    public record CategoryClick(
            String categoryToken,
            String modNamespace,
            boolean toggleExpandOnly) {}

    private CategoryTypes() {}
}
