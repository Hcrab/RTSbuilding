package com.rtsbuilding.rtsbuilding.client.presentation.plugin.grid;

public enum SortType {
    NAME("Name"),
    COUNT("Count"),
    MOD("Mod");

    private final String displayName;

    SortType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
