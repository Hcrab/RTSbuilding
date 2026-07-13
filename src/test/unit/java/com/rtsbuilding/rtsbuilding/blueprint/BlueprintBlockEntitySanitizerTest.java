package com.rtsbuilding.rtsbuilding.blueprint;

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
        assertFalse(sanitized.contains("Items"), "Survival blueprints must not copy container items.");
        assertFalse(sanitized.contains("ForgeCaps"), "Capability payloads can contain free resources.");
        assertFalse(sanitized.contains("Tank"), "Fluid contents must not be copied from blueprint NBT.");
        assertTrue(source.contains("Items"), "The sanitizer must not mutate original blueprint NBT.");
    }

    @Test
    void survivalPlacementDropsDangerousExecutableAndGeneratedContent() {
        CompoundTag source = new CompoundTag();
        source.putString("id", "minecraft:command_block");
        source.putString("Command", "give @a minecraft:diamond 64");
        source.put("SpawnData", new CompoundTag());
        source.putInt("Primary", 5);
        source.putString("LootTable", "minecraft:chests/end_city_treasure");
        source.putString("Text1", "{\"text\":\"legacy sign\"}");

        CompoundTag sanitized = BlueprintBlockEntitySanitizer.sanitizeForSurvivalPlacement(source);

        assertEquals("minecraft:command_block", sanitized.getString("id"));
        assertFalse(sanitized.contains("Command"), "Command blocks must not import executable commands.");
        assertFalse(sanitized.contains("SpawnData"), "Spawner payloads must not import entity spawn data.");
        assertFalse(sanitized.contains("Primary"), "Beacon effects must not be imported for free.");
        assertFalse(sanitized.contains("LootTable"), "Loot tables must not be imported from blueprint NBT.");
        assertFalse(sanitized.contains("Text1"), "Legacy sign text is user-authored content.");
        assertTrue(source.contains("Command"), "The original blueprint tag is kept intact.");
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
