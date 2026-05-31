package com.rtsbuilding.rtsbuilding.blueprint;

public enum BlueprintFormat {
    VANILLA_NBT("nbt"),
    SPONGE_SCHEM("schem");

    private final String extension;

    BlueprintFormat(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return this.extension;
    }

    public static BlueprintFormat fromFileName(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".schem") || lower.endsWith(".schematic")) {
            return SPONGE_SCHEM;
        }
        return VANILLA_NBT;
    }
}
