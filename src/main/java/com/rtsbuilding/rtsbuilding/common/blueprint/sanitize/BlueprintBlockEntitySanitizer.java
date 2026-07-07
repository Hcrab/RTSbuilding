package com.rtsbuilding.rtsbuilding.common.blueprint.sanitize;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Locale;

/**
 * 蓝图方块实体 NBT 的生存模式净化器。
 *
 * <p>这个类只负责移除可能代表“容器内容”的资源数据，例如物品栈、
 * 流体栈、能力库存和能量缓存；它不负责决定蓝图是否能放置，也不改变
 * 方块状态或材料消耗。这样蓝图在生存模式下仍会放置容器本体，但不会把
 * 创造档或导入文件里的库存内容复制出来。</p>
 */
public final class BlueprintBlockEntitySanitizer {
    private BlueprintBlockEntitySanitizer() {
    }

    /**
     * 为生存模式蓝图放置复制并净化方块实体标签。
     *
     * @param original 蓝图中保存的原始方块实体 NBT
     * @return 可安全用于生存放置的新 NBT，原始对象不会被修改
     */
    public static CompoundTag sanitizeForSurvivalPlacement(CompoundTag original) {
        if (original == null || original.isEmpty()) {
            return new CompoundTag();
        }
        CompoundTag sanitized = sanitizeCompound(original, true);
        return sanitized == null ? new CompoundTag() : sanitized;
    }

    private static CompoundTag sanitizeCompound(CompoundTag source, boolean topLevel) {
        if (source == null || source.isEmpty()) {
            return new CompoundTag();
        }
        if (!topLevel && looksLikeItemStack(source)) {
            return null;
        }
        if (!topLevel && looksLikeFluidStack(source)) {
            return null;
        }

        CompoundTag out = new CompoundTag();
        for (String key : source.getAllKeys()) {
            Tag value = source.get(key);
            if (value == null || shouldDropContentKey(key)) {
                continue;
            }
            Tag sanitized = sanitizeTag(key, value);
            if (sanitized != null) {
                out.put(key, sanitized);
            }
        }
        return out;
    }

    private static Tag sanitizeTag(String key, Tag value) {
        if (value instanceof CompoundTag compound) {
            return sanitizeCompound(compound, false);
        }
        if (value instanceof ListTag list) {
            return sanitizeList(key, list);
        }
        return value.copy();
    }

    private static ListTag sanitizeList(String key, ListTag source) {
        ListTag out = new ListTag();
        for (Tag child : source) {
            Tag sanitized = sanitizeTag(key, child);
            if (sanitized != null) {
                out.add(sanitized);
            }
        }
        return out;
    }

    private static boolean shouldDropContentKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return normalized.equals("items")
                || normalized.equals("inventory")
                || normalized.equals("inventories")
                || normalized.equals("stacks")
                || normalized.equals("contents")
                || normalized.equals("fluid")
                || normalized.equals("fluids")
                || normalized.equals("fluidstack")
                || normalized.equals("fluidstacks")
                || normalized.equals("tank")
                || normalized.equals("tanks")
                || normalized.equals("forgecaps")
                || normalized.equals("capabilities")
                || normalized.equals("energy")
                || normalized.equals("energystorage")
                || normalized.equals("storedenergy");
    }

    private static boolean looksLikeItemStack(CompoundTag tag) {
        return tag.contains("id", Tag.TAG_STRING)
                && (hasNumeric(tag, "count") || hasNumeric(tag, "Count"));
    }

    private static boolean looksLikeFluidStack(CompoundTag tag) {
        if (tag.contains("FluidName", Tag.TAG_STRING) && hasNumeric(tag, "Amount")) {
            return true;
        }
        return (tag.contains("fluid", Tag.TAG_STRING) || tag.contains("Fluid", Tag.TAG_STRING))
                && (hasNumeric(tag, "amount") || hasNumeric(tag, "Amount"));
    }

    private static boolean hasNumeric(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_BYTE)
                || tag.contains(key, Tag.TAG_SHORT)
                || tag.contains(key, Tag.TAG_INT)
                || tag.contains(key, Tag.TAG_LONG);
    }
}
