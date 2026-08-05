package com.rtsbuilding.rtsbuilding.common.blueprint.sanitize;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

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
    public static NBTTagCompound sanitizeForSurvivalPlacement(NBTTagCompound original) {
        if (original == null || com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.isEmpty(original)) {
            return new NBTTagCompound();
        }
        NBTTagCompound sanitized = sanitizeCompound(original, true);
        return sanitized == null ? new NBTTagCompound() : sanitized;
    }

    private static NBTTagCompound sanitizeCompound(NBTTagCompound source, boolean topLevel) {
        if (source == null || com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.isEmpty(source)) {
            return new NBTTagCompound();
        }
        if (!topLevel && looksLikeItemStack(source)) {
            return null;
        }
        if (!topLevel && looksLikeFluidStack(source)) {
            return null;
        }

        NBTTagCompound out = new NBTTagCompound();
        for (String key : source.func_150296_c()) {
            NBTBase value = source.getTag(key);
            if (value == null || shouldDropBlueprintKey(key)) {
                continue;
            }
            NBTBase sanitized = sanitizeTag(key, value);
            if (sanitized != null) {
                out.setTag(key, sanitized);
            }
        }
        return out;
    }

    private static NBTBase sanitizeTag(String key, NBTBase value) {
        if (value instanceof NBTTagCompound) {
            return sanitizeCompound((NBTTagCompound) value, false);
        }
        if (value instanceof NBTTagList) {
            return sanitizeList(key, (NBTTagList) value);
        }
        return value.copy();
    }

    private static NBTTagList sanitizeList(String key, NBTTagList source) {
        NBTTagList out = new NBTTagList();
        for (int i = 0; i < source.tagCount(); i++) {
            NBTBase sanitized = sanitizeTag(key,
                    com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.listElement(source, i));
            if (sanitized != null) {
                out.appendTag(sanitized);
            }
        }
        return out;
    }

    private static boolean shouldDropBlueprintKey(String key) {
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
                || normalized.equals("storedenergy")
                || normalized.equals("command")
                || normalized.equals("lastoutput")
                || normalized.equals("successcount")
                || normalized.equals("trackoutput")
                || normalized.equals("auto")
                || normalized.equals("powered")
                || normalized.equals("conditionmet")
                || normalized.equals("updatelastexecution")
                || normalized.equals("spawndata")
                || normalized.equals("spawnpotentials")
                || normalized.equals("minspawndelay")
                || normalized.equals("maxspawndelay")
                || normalized.equals("spawncount")
                || normalized.equals("maxnearbyentities")
                || normalized.equals("requiredplayerrange")
                || normalized.equals("spawnrange")
                || normalized.equals("delay")
                || normalized.equals("primary")
                || normalized.equals("secondary")
                || normalized.equals("levels")
                || normalized.equals("loottable")
                || normalized.equals("loottableseed")
                || normalized.equals("lock")
                || normalized.equals("front_text")
                || normalized.equals("back_text")
                || normalized.equals("text1")
                || normalized.equals("text2")
                || normalized.equals("text3")
                || normalized.equals("text4")
                || normalized.equals("filteredtext1")
                || normalized.equals("filteredtext2")
                || normalized.equals("filteredtext3")
                || normalized.equals("filteredtext4");
    }

    private static boolean looksLikeItemStack(NBTTagCompound tag) {
        return tag.hasKey("id", Constants.NBT.TAG_STRING)
                && (hasNumeric(tag, "count") || hasNumeric(tag, "Count"));
    }

    private static boolean looksLikeFluidStack(NBTTagCompound tag) {
        if (tag.hasKey("FluidName", Constants.NBT.TAG_STRING) && hasNumeric(tag, "Amount")) {
            return true;
        }
        return (tag.hasKey("fluid", Constants.NBT.TAG_STRING) || tag.hasKey("Fluid", Constants.NBT.TAG_STRING))
                && (hasNumeric(tag, "amount") || hasNumeric(tag, "Amount"));
    }

    private static boolean hasNumeric(NBTTagCompound tag, String key) {
        return tag.hasKey(key, Constants.NBT.TAG_ANY_NUMERIC);
    }
}
