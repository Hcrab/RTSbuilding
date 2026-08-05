package com.rtsbuilding.rtsbuilding.platform.crafting;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.7.10 配方输入的不可变候选集合。
 *
 * <p>它不是伪造 Minecraft 新版类，而是把原版 ItemStack、矿辞候选列表和常见模组数组
 * 统一成 RTS 合成服务需要的两项能力：列出预览原型、判断真实物品是否匹配。最终产物
 * 仍必须通过原生 {@code IRecipe.matches}，所以兼容层不会绕过 GTNH 的自定义配方校验。</p>
 */
public final class Ingredient {
    public static final Ingredient EMPTY = new Ingredient(new ItemStack[0]);

    private final ItemStack[] matchingStacks;

    private Ingredient(ItemStack[] matchingStacks) {
        this.matchingStacks = matchingStacks;
    }

    public static Ingredient fromLegacyInput(Object input) {
        List<ItemStack> options = new ArrayList<ItemStack>();
        collect(input, options);
        if (options.isEmpty()) return EMPTY;
        ItemStack[] copied = new ItemStack[options.size()];
        for (int i = 0; i < options.size(); i++) {
            copied[i] = options.get(i).copy();
            copied[i].stackSize = 1;
        }
        return new Ingredient(copied);
    }

    public ItemStack[] getMatchingStacks() {
        ItemStack[] copied = new ItemStack[this.matchingStacks.length];
        for (int i = 0; i < copied.length; i++) copied[i] = this.matchingStacks[i].copy();
        return copied;
    }

    public boolean apply(ItemStack candidate) {
        if (candidate == null || candidate.getItem() == null || candidate.stackSize <= 0) return false;
        for (ItemStack option : this.matchingStacks) {
            if (!OreDictionary.itemMatches(option, candidate, false)) continue;
            if (!option.hasTagCompound() || ItemStack.areItemStackTagsEqual(option, candidate)) return true;
        }
        return false;
    }

    private static void collect(Object input, List<ItemStack> out) {
        if (input == null) return;
        if (input instanceof ItemStack) {
            ItemStack stack = (ItemStack) input;
            if (stack.getItem() != null && stack.stackSize > 0) out.add(stack);
            return;
        }
        if (input instanceof Item) {
            out.add(new ItemStack((Item) input));
            return;
        }
        if (input instanceof Block) {
            out.add(new ItemStack((Block) input, 1, OreDictionary.WILDCARD_VALUE));
            return;
        }
        if (input instanceof String) {
            for (ItemStack stack : OreDictionary.getOres((String) input)) collect(stack, out);
            return;
        }
        if (input instanceof Iterable<?>) {
            for (Object value : (Iterable<?>) input) collect(value, out);
            return;
        }
        Class<?> type = input.getClass();
        if (type.isArray()) {
            int length = Array.getLength(input);
            for (int i = 0; i < length; i++) collect(Array.get(input, i), out);
        }
    }
}
