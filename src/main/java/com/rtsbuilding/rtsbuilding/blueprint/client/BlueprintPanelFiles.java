package com.rtsbuilding.rtsbuilding.blueprint.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import net.minecraftforge.fml.loading.FMLPaths;

/**
 * File-system helper layer for the client blueprint panel.
 *
 * <p>Blueprint UI code needs names, extensions, and instance-local folders, but
 * the panel itself should not know loader-specific path APIs. This class is the
 * small NeoForge edge: the Forge branch keeps the same helper shape with its own
 * {@code FMLPaths} import.</p>
 */
final class BlueprintPanelFiles {
    private BlueprintPanelFiles() {
    }

    /**
     * Returns the RTSBuilding-owned blueprint folder inside the current instance.
     */
    static Path blueprintFolder() {
        return FMLPaths.GAMEDIR.get().resolve("rtsbuilding-blueprints");
    }

    /**
     * Returns common mod-owned blueprint folders for one-way sync/copy into
     * RTSBuilding's own blueprint folder.
     */
    static List<Path> otherModBlueprintFolders() {
        Path gameDir = FMLPaths.GAMEDIR.get();
        return List.of(
                gameDir.resolve("schematics"),
                gameDir.resolve("buildinggadgets"),
                gameDir.resolve("buildinggadgets").resolve("templates"),
                gameDir.resolve("buildinggadgets").resolve("blueprints"),
                gameDir.resolve("buildinggadgets2"),
                gameDir.resolve("buildinggadgets2").resolve("templates"),
                gameDir.resolve("buildinggadgets2").resolve("blueprints"),
                gameDir.resolve("building_gadgets"),
                gameDir.resolve("building_gadgets").resolve("templates"),
                gameDir.resolve("building_gadgets").resolve("blueprints"),
                gameDir.resolve("templates").resolve("buildinggadgets"),
                gameDir.resolve("templates").resolve("buildinggadgets2"),
                gameDir.resolve("blueprints").resolve("buildinggadgets"),
                gameDir.resolve("blueprints").resolve("buildinggadgets2"));
    }

    /**
     * Returns the local rotation-default metadata file stored beside blueprints.
     */
    static Path defaultsPath() {
        return blueprintFolder().resolve(".rtsbuilding-rotation-defaults.properties");
    }

    /**
     * Adds an extension to a selected path when the user omitted one.
     */
    static Path ensureExtension(Path path, String extension) {
        if (path == null || extension == null || extension.isBlank()) {
            return path;
        }
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith("." + extension.toLowerCase(Locale.ROOT))) {
            return path;
        }
        Path parent = path.getParent();
        Path renamed = Path.of(name + "." + extension);
        return parent == null ? renamed : parent.resolve(renamed);
    }

    /**
     * Removes any supported blueprint extension while preserving the base label.
     */
    static String stripBlueprintExtension(String fileName) {
        String clean = fileName == null || fileName.isBlank() ? "blueprint" : fileName;
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".schematic")) {
            return clean.substring(0, clean.length() - ".schematic".length());
        }
        if (lower.endsWith(".schem")) {
            return clean.substring(0, clean.length() - ".schem".length());
        }
        if (lower.endsWith(".litematic")) {
            return clean.substring(0, clean.length() - ".litematic".length());
        }
        if (lower.endsWith(".json")) {
            return clean.substring(0, clean.length() - ".json".length());
        }
        if (lower.endsWith(".nbt")) {
            return clean.substring(0, clean.length() - ".nbt".length());
        }
        return clean;
    }

    /**
     * Reads the supported blueprint extension from a file name, or uses a fallback.
     */
    static String blueprintExtension(String fileName, String fallback) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".schematic")) {
            return "schematic";
        }
        if (lower.endsWith(".schem")) {
            return "schem";
        }
        if (lower.endsWith(".litematic")) {
            return "litematic";
        }
        if (lower.endsWith(".json")) {
            return "json";
        }
        if (lower.endsWith(".nbt")) {
            return "nbt";
        }
        return fallback == null || fallback.isBlank() ? "nbt" : fallback;
    }

    /**
     * Creates a unique vanilla-structure file name inside the blueprint folder.
     */
    static String uniqueNbtFileName(String base) {
        String clean = sanitizeFileBase(base);
        String candidate = clean + ".nbt";
        int suffix = 2;
        while (Files.exists(blueprintFolder().resolve(candidate))) {
            candidate = clean + "_" + suffix + ".nbt";
            suffix++;
        }
        return candidate;
    }

    /**
     * Creates a unique destination path while allowing an existing file to keep
     * its own name during rename/save-as operations.
     */
    static Path uniqueBlueprintPath(String base, String extension, Path currentPath) {
        String clean = sanitizeFileBase(base);
        String safeExtension = extension == null || extension.isBlank() ? "nbt" : extension;
        Path folder = blueprintFolder();
        Path current = currentPath == null ? null : currentPath.toAbsolutePath().normalize();
        Path candidate = folder.resolve(clean + "." + safeExtension);
        int suffix = 2;
        while (Files.exists(candidate)
                && (current == null || !candidate.toAbsolutePath().normalize().equals(current))) {
            candidate = folder.resolve(clean + "_" + suffix + "." + safeExtension);
            suffix++;
        }
        return candidate;
    }

    /**
     * Removes only the vanilla NBT extension used by newly captured blueprints.
     */
    static String stripNbtExtension(String fileName) {
        String name = fileName == null ? "blueprint" : fileName;
        return name.toLowerCase(Locale.ROOT).endsWith(".nbt")
                ? name.substring(0, name.length() - 4)
                : name;
    }

    /**
     * Converts a user-facing blueprint name into a safe file-name base.
     *
     * <p>Chinese characters are intentionally kept because the project has a
     * large Chinese-speaking audience. Characters that are unsafe or awkward on
     * Windows/macOS/Linux file systems are replaced with underscores.</p>
     */
    static String sanitizeFileBase(String raw) {
        String clean = raw == null ? "blueprint" : raw.trim();
        if (clean.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
            clean = clean.substring(0, clean.length() - 4);
        }
        clean = clean.replaceAll("[\\\\/:*?\"<>|]+", "_").replaceAll("\\s+", "_");
        clean = clean.replaceAll("[^A-Za-z0-9._\\-\\u4e00-\\u9fff]+", "_");
        clean = clean.replaceAll("_+", "_");
        if (clean.isBlank() || clean.equals(".") || clean.equals("..")) {
            clean = "blueprint";
        }
        return clean.length() > 80 ? clean.substring(0, 80) : clean;
    }

    /**
     * Returns whether the file has a blueprint format that RTSBuilding can read.
     */
    static boolean isBlueprintFile(Path path) {
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return lower.endsWith(".nbt") || lower.endsWith(".schem") || lower.endsWith(".schematic")
                || lower.endsWith(".litematic") || lower.endsWith(".json");
    }

    /**
     * Filters files discovered from other mods. JSON is accepted only when it
     * looks like a Building Gadgets template, so config files are not copied
     * into the player's RTS blueprint list by accident.
     */
    static boolean isSyncBlueprintFile(Path path) {
        if (!isBlueprintFile(path)) {
            return false;
        }
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".json")) {
            return true;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            return text.contains("\"statePosArrayList\"")
                    || (text.contains("\"header\"") && text.contains("\"body\""));
        } catch (Exception ex) {
            return false;
        }
    }
}
