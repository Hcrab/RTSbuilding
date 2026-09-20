package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferExtractor;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 隔离验证建造取料链的真实物品语义：两个竞争者不能重复拿走同一份材料，
 * 插入不足时必须保留 remainder，之后仍能从同一处理器取回真实堆叠。
 *
 * <p>夹具不启动 Minecraft 注册表、不伪造“永远成功”的服务端任务，直接调用
 * {@link RtsPlacementExtractor} 与 {@link RtsTransferInserter}/{@link RtsTransferExtractor}
 * 的生产方法，覆盖 B06/B07 的物品扣除与归还边界；菜单选择、权限和区块状态仍由
 * GameTest/真人清单验证。</p>
 */
class ConstructionMaterialFlowFixtureTest {
    @Test
    void competingExtractionAndPartialReturnPreserveRealStack() {
        Item item = Items.STONE;
        SingleSlotHandler handler = new SingleSlotHandler(new ItemStack(item, 1));
        List<IItemHandler> handlers = List.of(handler);

        ItemStack first = RtsPlacementExtractor.extractSelectedFromLinked(handlers, item, ItemStack.EMPTY);
        ItemStack second = RtsPlacementExtractor.extractSelectedFromLinked(handlers, item, ItemStack.EMPTY);

        assertEquals(1, first.getCount(), "第一个取料者只能拿到真实的一件");
        assertTrue(second.isEmpty(), "第二个取料者不能重复消费同一槽位");
        assertTrue(handler.getStackInSlot(0).isEmpty(), "提取后处理器必须确实减少");

        ItemStack remainder = RtsTransferInserter.storeToLinkedOnly(handlers, new ItemStack(item, 2));
        assertEquals(1, handler.getStackInSlot(0).getCount(), "处理器只能接收容量允许的一件");
        assertEquals(1, remainder.getCount(), "不足容量的物品必须原样作为 remainder 返回");

        ItemStack returned = RtsTransferExtractor.extractOneFromLinked(handlers, item);
        assertEquals(1, returned.getCount(), "真实归还堆叠仍可被后续取料者拿到");
        assertTrue(handler.getStackInSlot(0).isEmpty(), "归还堆叠再次提取后槽位应为空");
    }

    @Test
    void failedActionRefundKeepsMutatedComponentsAndRejectsDifferentVariant() {
        ItemStack original = richTool();
        SingleSlotHandler handler = new SingleSlotHandler(original);
        List<IItemHandler> handlers = List.of(handler);

        ItemStack differentVariant = richTool();
        CompoundTag differentData = new CompoundTag();
        differentData.putString("energy", "different");
        differentVariant.set(DataComponents.CUSTOM_DATA, CustomData.of(differentData));
        assertTrue(RtsPlacementExtractor.extractSelectedFromLinked(
                handlers, Items.DIAMOND_PICKAXE, differentVariant).isEmpty(),
                "同一物品 ID 但组件不同不能误取另一变体");
        assertTrue(ItemStack.matches(original, handler.getStackInSlot(0)),
                "拒绝不同变体后真实来源不能被修改");

        ItemStack extracted = RtsPlacementExtractor.extractSelectedFromLinked(
                handlers, Items.DIAMOND_PICKAXE, ItemStack.EMPTY);
        extracted.setDamageValue(19);
        CompoundTag changedData = new CompoundTag();
        changedData.putString("energy", "mutated-after-extract");
        extracted.set(DataComponents.CUSTOM_DATA, CustomData.of(changedData));
        ItemStack remainder = RtsTransferInserter.storeToLinkedOnly(handlers, extracted);

        assertTrue(remainder.isEmpty(), "失败动作的真实 remainder 应能完整退回来源处理器");
        ItemStack returned = RtsTransferExtractor.extractOneFromLinked(
                handlers, Items.DIAMOND_PICKAXE);
        assertTrue(ItemStack.matches(extracted, returned),
                "退款必须保留动作期间修改过的耐久与组件");
    }

    private static ItemStack richTool() {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        stack.setDamageValue(7);
        CompoundTag data = new CompoundTag();
        data.putString("energy", "before");
        data.putInt("container_marker", 42);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    /** 单槽、单件容量的可变处理器，让竞争和 remainder 都由生产插入/提取代码决定。 */
    private static final class SingleSlotHandler implements IItemHandler {
        private ItemStack stack;

        private SingleSlotHandler(ItemStack initial) {
            this.stack = initial.copy();
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? stack.copy() : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack incoming, boolean simulate) {
            if (slot != 0 || incoming == null || incoming.isEmpty()) {
                return incoming == null ? ItemStack.EMPTY : incoming.copy();
            }
            if (!stack.isEmpty() && !ItemStack.isSameItemSameComponents(stack, incoming)) {
                return incoming.copy();
            }
            int free = stack.isEmpty() ? 1 : Math.max(0, 1 - stack.getCount());
            int moved = Math.min(free, incoming.getCount());
            if (!simulate && moved > 0) {
                if (stack.isEmpty()) {
                    stack = incoming.copyWithCount(moved);
                } else {
                    stack.grow(moved);
                }
            }
            return moved == incoming.getCount() ? ItemStack.EMPTY : incoming.copyWithCount(incoming.getCount() - moved);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0 || stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int moved = Math.min(amount, stack.getCount());
            ItemStack extracted = stack.copyWithCount(moved);
            if (!simulate) {
                stack.shrink(moved);
            }
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack incoming) {
            return slot == 0;
        }
    }
}
