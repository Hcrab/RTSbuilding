package com.rtsbuilding.rtsbuilding.compat.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * Icon resolution for AE2 GUI binding slots.
 *
 * <p>Given a world position and a block entity (typically an AE2 part or
 * terminal), this resolver tries multiple strategies to derive the most
 * specific registered item ID that matches the block's GUI:
 * <ol>
 *   <li>Use the display name or label hint as a candidate path</li>
 *   <li>Check the part class name for the direction-facing sub-component</li>
 *   <li>Fall back to the block entity or menu provider class name</li>
 *   <li>Try alias patterns (crafting_terminal, pattern_terminal, etc.)</li>
 * </ol>
 *
 * <p>All access is reflective so RTSBuilding does not gain a hard runtime
 * dependency on AE2. This class is safe to call even when AE2 is not
 * installed (returns empty string).
 */
public final class RtsAe2IconResolver {

    private RtsAe2IconResolver() {
    }

    public static String resolveGuiBindingIconItemId(Level level, BlockPos pos, Direction face, String labelHint) {
        if (level == null || pos == null || !ModList.get().isLoaded("ae2") || !level.hasChunkAt(pos)) {
            return "";
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return "";
        }

        String namespace = resolveItemNamespace(state);
        if (namespace.isBlank()) {
            return "";
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        MenuProvider provider = state.getMenuProvider(level, pos);
        if (provider == null && blockEntity instanceof MenuProvider menuProvider) {
            provider = menuProvider;
        }

        LinkedHashSet<String> candidatePaths = new LinkedHashSet<>();
        addIconCandidates(candidatePaths, labelHint);
        addIconCandidates(candidatePaths, provider == null || provider.getDisplayName() == null ? "" : provider.getDisplayName().getString());
        addIconCandidates(candidatePaths, provider == null ? "" : provider.getClass().getName());
        addIconCandidates(candidatePaths, blockEntity == null ? "" : blockEntity.getClass().getName());

        Object part = resolveDirectionalPart(blockEntity, face);
        addIconCandidates(candidatePaths, part == null ? "" : part.getClass().getName());
        if (part instanceof MenuProvider partProvider && partProvider.getDisplayName() != null) {
            addIconCandidates(candidatePaths, partProvider.getDisplayName().getString());
        }

        return resolveRegisteredItemId(namespace, candidatePaths);
    }

    private static String resolveItemNamespace(BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) {
            return "";
        }
        return "ae2".equals(blockId.getNamespace()) ? blockId.getNamespace() : "";
    }

    private static Object resolveDirectionalPart(BlockEntity blockEntity, Direction face) {
        if (blockEntity == null || face == null) {
            return null;
        }
        Method method = findMethod(blockEntity.getClass(), "getPart", Direction.class);
        return method == null ? null : invokeReflectively(method, blockEntity, face);
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        if (owner == null) {
            return null;
        }

        try {
            Method method = owner.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeReflectively(Method method, Object target, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String resolveRegisteredItemId(String preferredNamespace, LinkedHashSet<String> candidatePaths) {
        for (String path : candidatePaths) {
            if (path == null || path.isBlank()) {
                continue;
            }

            if (!preferredNamespace.isBlank()) {
                ResourceLocation preferred = ResourceLocation.tryParse(preferredNamespace + ":" + path);
                if (preferred != null && BuiltInRegistries.ITEM.containsKey(preferred)) {
                    return preferred.toString();
                }
            }

            ResourceLocation ae2 = ResourceLocation.tryParse("ae2:" + path);
            if (ae2 != null && BuiltInRegistries.ITEM.containsKey(ae2)) {
                return ae2.toString();
            }

            for (ResourceLocation key : BuiltInRegistries.ITEM.keySet()) {
                if (path.equals(key.getPath())) {
                    return key.toString();
                }
            }
        }
        return "";
    }

    private static void addIconCandidates(LinkedHashSet<String> out, String text) {
        String normalized = normalizeToItemPath(text);
        if (normalized.isBlank()) {
            return;
        }

        addCandidate(out, normalized);

        String stripped = stripGuiNoise(normalized);
        if (!stripped.equals(normalized)) {
            addCandidate(out, stripped);
        }
        addAliasCandidates(out, stripped);
    }

    private static void addAliasCandidates(LinkedHashSet<String> out, String normalized) {
        boolean terminal = normalized.contains("terminal") || normalized.contains("term");
        boolean crafting = normalized.contains("crafting");
        boolean pattern = normalized.contains("pattern");
        boolean encoding = normalized.contains("encoding");
        boolean access = normalized.contains("access");
        boolean provider = normalized.contains("provider");

        if (crafting && terminal) {
            addCandidate(out, "crafting_terminal");
        }
        if (pattern && terminal && (encoding || normalized.equals("pattern_terminal"))) {
            addCandidate(out, "pattern_encoding_terminal");
            addCandidate(out, "pattern_terminal");
        }
        if (pattern && terminal && provider) {
            addCandidate(out, "pattern_provider_terminal");
            addCandidate(out, "pattern_access_terminal");
        }
        if (pattern && terminal && access) {
            addCandidate(out, "pattern_access_terminal");
        }
        if (pattern && provider && !terminal) {
            addCandidate(out, "pattern_provider");
        }
        if (normalized.equals("terminal")) {
            addCandidate(out, "terminal");
        }
    }

    private static void addCandidate(LinkedHashSet<String> out, String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        out.add(path);
    }

    private static String stripGuiNoise(String normalized) {
        String stripped = normalized;
        if (stripped.startsWith("me_")) {
            stripped = stripped.substring(3);
        }

        String previous;
        do {
            previous = stripped;
            stripped = trimSuffix(stripped, "_menu_provider");
            stripped = trimSuffix(stripped, "_menuprovider");
            stripped = trimSuffix(stripped, "_menu_host");
            stripped = trimSuffix(stripped, "_menuhost");
            stripped = trimSuffix(stripped, "_menu");
            stripped = trimSuffix(stripped, "_screen");
            stripped = trimSuffix(stripped, "_part");
            stripped = trimSuffix(stripped, "_host");
            stripped = trimSuffix(stripped, "_block_entity");
            stripped = trimSuffix(stripped, "_blockentity");
            stripped = trimSuffix(stripped, "_block");
        } while (!previous.equals(stripped));

        return stripped;
    }

    private static String trimSuffix(String value, String suffix) {
        return value.endsWith(suffix) ? value.substring(0, value.length() - suffix.length()) : value;
    }

    private static String normalizeToItemPath(String text) {
        if (text == null) {
            return "";
        }

        String simple = text.strip();
        if (simple.isEmpty()) {
            return "";
        }
        int dot = simple.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < simple.length()) {
            simple = simple.substring(dot + 1);
        }

        String normalized = simple
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_");
        if (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
