package com.rtsbuilding.rtsbuilding.common.blueprint.model;

/**
 * Blueprint format enum — identifies the blueprint file formats supported by RTSbuilding.
 * <p>
 * Each format corresponds to a file extension, used for automatic recognition and routing
 * to the appropriate parser during import.
 * Note: enum ordinals are used in network transmission and must remain stable.
 */
public enum BlueprintFormat {
    /** Vanilla Minecraft structure NBT format (.nbt) */
    VANILLA_NBT("nbt"),
    /** Sponge mod ecosystem Schematic format (.schem / .schematic) */
    SPONGE_SCHEM("schem"),
    /** Litematica mod Litematic format (.litematic) */
    LITEMATIC("litematic"),
    /** Building Gadgets mod JSON template format (.json) */
    BUILDING_GADGETS_JSON("json");

    /** The file extension for this format (without dot) */
    private final String extension;

    BlueprintFormat(String extension) {
        this.extension = extension;
    }

    /**
     * Get the file extension for this format.
     *
     * @return extension string, e.g., "nbt", "schem"
     */
    public String extension() {
        return this.extension;
    }

    /**
     * Infer the blueprint format from a file name.
     * <p>
     * Matches by file extension; defaults to vanilla NBT format if no known extension matches.
     *
     * @param fileName file name (may include path)
     * @return the matching blueprint format enum value
     */
    public static BlueprintFormat fromFileName(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".schem") || lower.endsWith(".schematic")) {
            return SPONGE_SCHEM;
        }
        if (lower.endsWith(".litematic")) {
            return LITEMATIC;
        }
        if (lower.endsWith(".json")) {
            return BUILDING_GADGETS_JSON;
        }
        return VANILLA_NBT;
    }
}
