package com.rtsbuilding.rtsbuilding.platform.storage;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** 统一 1.7.10 的 null/stackSize 空栈语义，避免业务层散落版本判断。 */
public final class StackCompat {
    private StackCompat() {
    }

    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.stackSize <= 0 || stack.getItem() == null;
    }

    /** 把 1.7.10 的 null 空栈转换成现代代码期望的零数量语义。 */
    public static int count(ItemStack stack) {
        return isEmpty(stack) ? 0 : stack.stackSize;
    }

    /**
     * 安全复制一个可能为空的旧版物品堆。
     *
     * <p>1.7.10 使用 {@code null} 表示空堆；从后续版本搬来的业务代码不能直接对
     * “现代版原本为 ItemStack.EMPTY”的变量调用 {@code copy()}。</p>
     */
    public static ItemStack copyOrNull(ItemStack stack) {
        return isEmpty(stack) ? null : stack.copy();
    }

    public static ItemStack copyWithSize(ItemStack stack, int size) {
        if (isEmpty(stack) || size <= 0) return null;
        ItemStack copy = stack.copy();
        copy.stackSize = size;
        return copy;
    }

    /** 保留新版本 ItemStack 的原地增量语义，业务层无需直接感知 stackSize 字段。 */
    public static void grow(ItemStack stack, int amount) {
        if (stack != null && amount != 0) stack.stackSize += amount;
    }

    /** 保留新版本 ItemStack 的原地减量语义；减到零后仍由 {@link #isEmpty(ItemStack)} 统一判空。 */
    public static void shrink(ItemStack stack, int amount) {
        if (stack != null && amount != 0) stack.stackSize -= amount;
    }

    /** 与后续版本 areItemsEqual 一致：比较物品与 metadata，不比较数量和 NBT。 */
    public static boolean areItemsEqual(ItemStack left, ItemStack right) {
        return left == null ? right == null : right != null && left.isItemEqual(right);
    }

    public static ItemStack read(NBTTagCompound tag) {
        return tag == null ? null : ItemStack.loadItemStackFromNBT(tag);
    }
}
