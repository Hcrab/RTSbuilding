package com.rtsbuilding.rtsbuilding.client.record;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Objects;

/**
 * 客户端不可变快照的共享值语义。
 *
 * <p>这里只处理可变的 {@link ItemStack} 边界，不负责查注册表、补默认物品或修改数量；
 * 调用方仍决定空值是否有效。集中实现复制与比较可以避免各条目在 Java 8 改写后出现不同语义。
 */
final class ClientRecordSupport {
    private ClientRecordSupport() {
    }

    static ItemStack copyStack(ItemStack stack) {
        return stack == null ? null : stack.copy();
    }

    static boolean stackEquals(ItemStack left, ItemStack right) {
        return left == right || left != null && right != null
                && ItemStack.areItemStacksEqual(left, right);
    }

    static int stackHash(ItemStack stack) {
        if (stack == null) {
            return 0;
        }
        return Objects.hash(Item.getIdFromItem(stack.getItem()), stack.stackSize,
                stack.getItemDamage(), stack.getTagCompound());
    }
}
