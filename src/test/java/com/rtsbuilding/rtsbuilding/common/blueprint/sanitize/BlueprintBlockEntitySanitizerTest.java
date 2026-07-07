package com.rtsbuilding.rtsbuilding.common.blueprint.sanitize;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintBlockEntitySanitizerTest {
    @Test
    void survivalPlacementDropsInventoryCapabilityAndFluidContents() {
        CompoundTag source = new CompoundTag();
        source.putString("id", "minecraft:chest");
        source.putString("CustomName", "{\"text\":\"Builder Cache\"}");
        source.put("Items", itemList("minecraft:diamond", 64));

        CompoundTag forgeCaps = new CompoundTag();
        forgeCaps.put("item_handler", itemList("minecraft:netherite_ingot", 8));
        source.put("ForgeCaps", forgeCaps);

        CompoundTag tank = new CompoundTag();
        tank.putString("FluidName", "minecraft:lava");
        tank.putInt("Amount", 1000);
        source.put("Tank", tank);

        CompoundTag sanitized = BlueprintBlockEntitySanitizer.sanitizeForSurvivalPlacement(source);

        assertEquals("minecraft:chest", sanitized.getString("id"));
        assertEquals("{\"text\":\"Builder Cache\"}", sanitized.getString("CustomName"));
        assertFalse(sanitized.contains("Items"), "生存蓝图放置不能复制容器里的物品。");
        assertFalse(sanitized.contains("ForgeCaps"), "能力库存可能持有物品、流体或能量，也不能复制。");
        assertFalse(sanitized.contains("Tank"), "流体内容不能随蓝图免费复制。");

        assertTrue(source.contains("Items"), "净化器必须复制后处理，不能修改蓝图原始 NBT。");
    }

    @Test
    void nestedItemStackCompoundsAreRemovedWithoutDroppingTextData() {
        CompoundTag source = new CompoundTag();
        source.putString("id", "minecraft:sign");
        source.putString("front_text", "玩家写好的说明");

        CompoundTag nested = new CompoundTag();
        nested.putString("owner_note", "keep me");
        nested.put("preview_stack", itemStack("minecraft:emerald", 3));
        source.put("display", nested);

        CompoundTag sanitized = BlueprintBlockEntitySanitizer.sanitizeForSurvivalPlacement(source);
        CompoundTag display = sanitized.getCompound("display");

        assertEquals("玩家写好的说明", sanitized.getString("front_text"));
        assertEquals("keep me", display.getString("owner_note"));
        assertFalse(display.contains("preview_stack"), "嵌套物品栈也不能通过蓝图复制。");
    }

    private static ListTag itemList(String itemId, int count) {
        ListTag items = new ListTag();
        items.add(itemStack(itemId, count));
        return items;
    }

    private static CompoundTag itemStack(String itemId, int count) {
        CompoundTag stack = new CompoundTag();
        stack.putString("id", itemId);
        stack.putInt("count", count);
        return stack;
    }
}
