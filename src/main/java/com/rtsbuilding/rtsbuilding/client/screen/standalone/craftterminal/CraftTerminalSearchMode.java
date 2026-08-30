package com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal;

/** 合成终端搜索与 JEI 搜索框之间的联动模式。 */
public enum CraftTerminalSearchMode {
    STANDARD("S"),
    SYNC_TO_JEI("J"),
    BIDIRECTIONAL("B");

    private final String shortLabel;

    CraftTerminalSearchMode(String shortLabel) {
        this.shortLabel = shortLabel;
    }

    public String shortLabel() {
        return this.shortLabel;
    }

    public CraftTerminalSearchMode next() {
        CraftTerminalSearchMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static CraftTerminalSearchMode parse(String value) {
        if (value == null || value.isBlank()) {
            return STANDARD;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return STANDARD;
        }
    }
}
