package com.rtsbuilding.rtsbuilding.compat.ae2;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Locale;

/** AE2 rv6 终端/part 的 GUI 绑定图标解析。AE2 未安装时可安全加载。 */
public final class RtsAe2IconResolver {
    private RtsAe2IconResolver() {
    }

    public static String resolveGuiBindingIconItemId(World world, BlockPos pos, EnumFacing face, String labelHint) {
        if (world == null || pos == null || !isAe2Loaded() || !world.isBlockLoaded(pos)) {
            return "";
        }
        IBlockState state = world.getBlockState(pos);
        if (state == null || state.getBlock() == Blocks.AIR) {
            return "";
        }

        TileEntity tile = world.getTileEntity(pos);
        Object part = resolveDirectionalPart(tile, face);
        String partItemId = resolvePartItemId(part);
        if (!partItemId.isEmpty()) {
            return partItemId;
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addIconCandidates(candidates, labelHint);
        addIconCandidates(candidates, part == null ? "" : part.getClass().getName());
        addIconCandidates(candidates, tile == null ? "" : tile.getClass().getName());
        Object displayName = invokeNoArgs(part, "getDisplayName");
        addIconCandidates(candidates, displayName == null ? "" : displayName.toString());

        String candidate = resolveRegisteredItemId(candidates);
        if (!candidate.isEmpty()) {
            return candidate;
        }

        Item blockItem = Item.getItemFromBlock(state.getBlock());
        ResourceLocation blockItemId = blockItem == null ? null : ForgeRegistries.ITEMS.getKey(blockItem);
        return isAe2(blockItemId) ? blockItemId.toString() : "";
    }

    /** 1.12 的 IPartHost 同时提供 getPart(EnumFacing)，优先按玩家点中的面识别终端。 */
    private static Object resolveDirectionalPart(TileEntity tile, EnumFacing face) {
        if (tile == null || face == null) {
            return null;
        }
        try {
            Class<?> hostClass = Class.forName("appeng.api.parts.IPartHost");
            if (!hostClass.isInstance(tile)) {
                return null;
            }
            return hostClass.getMethod("getPart", EnumFacing.class).invoke(tile, face);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    /** 从真实 part 栈取注册物品；metadata/NBT 仍保留在 part 栈中，不靠类名猜物品。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String resolvePartItemId(Object part) {
        if (part == null) {
            return "";
        }
        try {
            Class<?> partClass = Class.forName("appeng.api.parts.IPart");
            Class<?> stackKindClass = Class.forName("appeng.api.parts.PartItemStack");
            if (!partClass.isInstance(part)) {
                return "";
            }
            Method getItemStack = partClass.getMethod("getItemStack", stackKindClass);
            Object networkKind = Enum.valueOf((Class<? extends Enum>) stackKindClass.asSubclass(Enum.class), "NETWORK");
            Object value = getItemStack.invoke(part, networkKind);
            if (!(value instanceof ItemStack) || ((ItemStack) value).isEmpty()) {
                return "";
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(((ItemStack) value).getItem());
            return isAe2(id) ? id.toString() : "";
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return "";
        }
    }

    private static Object invokeNoArgs(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
            return null;
        }
    }

    private static String resolveRegisteredItemId(LinkedHashSet<String> candidates) {
        for (String path : candidates) {
            ResourceLocation id = resourceLocation("appliedenergistics2", path);
            if (id != null && ForgeRegistries.ITEMS.containsKey(id)) {
                return id.toString();
            }
        }
        return "";
    }

    private static ResourceLocation resourceLocation(String namespace, String path) {
        try {
            return path == null || path.isEmpty() ? null : new ResourceLocation(namespace, path);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isAe2(ResourceLocation id) {
        return id != null && "appliedenergistics2".equals(id.getNamespace());
    }

    private static boolean isAe2Loaded() {
        try {
            return Loader.isModLoaded("appliedenergistics2");
        } catch (RuntimeException | LinkageError loaderNotReady) {
            return false;
        }
    }

    private static void addIconCandidates(LinkedHashSet<String> out, String text) {
        String normalized = normalizeToItemPath(text);
        if (normalized.isEmpty()) {
            return;
        }
        out.add(normalized);
        String stripped = stripGuiNoise(normalized);
        out.add(stripped);
        if (stripped.contains("crafting") && stripped.contains("terminal")) {
            out.add("crafting_terminal");
        }
        if (stripped.contains("pattern") && stripped.contains("terminal")) {
            out.add("pattern_terminal");
        }
        if (stripped.contains("interface") && stripped.contains("terminal")) {
            out.add("interface_terminal");
        }
        if ("terminal".equals(stripped)) {
            out.add("terminal");
        }
    }

    private static String stripGuiNoise(String value) {
        String stripped = value;
        String previous;
        do {
            previous = stripped;
            stripped = trimSuffix(stripped, "_menu_provider");
            stripped = trimSuffix(stripped, "_menu");
            stripped = trimSuffix(stripped, "_screen");
            stripped = trimSuffix(stripped, "_part");
            stripped = trimSuffix(stripped, "_host");
            stripped = trimSuffix(stripped, "_tile_entity");
            stripped = trimSuffix(stripped, "_tile");
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
        String simple = text.trim();
        int dot = simple.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < simple.length()) {
            simple = simple.substring(dot + 1);
        }
        String normalized = simple.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
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
