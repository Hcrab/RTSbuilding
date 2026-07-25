package com.rtsbuilding.rtsbuilding.client.record;

public record CraftRecipeOption(
        String recipeId,
        int resultCount,
        boolean craftable,
        String summary,
        String missingSummary) {
}
