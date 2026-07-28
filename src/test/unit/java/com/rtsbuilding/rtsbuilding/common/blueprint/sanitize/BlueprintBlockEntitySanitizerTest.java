package com.rtsbuilding.rtsbuilding.common.blueprint.sanitize;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintBlockEntitySanitizerTest {
    @Test
    void survivalPlacementDropsInventoryCapabilityAndFluidContents() {
        NBTTagCompound source = new NBTTagCompound();
        source.setString("id", "minecraft:chest");
        source.setString("CustomName", "{\"text\":\"Builder Cache\"}");
        source.setTag("Items", itemList("minecraft:diamond", 64));

        NBTTagCompound forgeCaps = new NBTTagCompound();
        forgeCaps.setTag("item_handler", itemList("minecraft:netherite_ingot", 8));
        source.setTag("ForgeCaps", forgeCaps);

        NBTTagCompound tank = new NBTTagCompound();
        tank.setString("FluidName", "minecraft:lava");
        tank.setInteger("Amount", 1000);
        source.setTag("Tank", tank);

        NBTTagCompound sanitized = BlueprintBlockEntitySanitizer.sanitizeForSurvivalPlacement(source);

        assertEquals("minecraft:chest", sanitized.getString("id"));
        assertEquals("{\"text\":\"Builder Cache\"}", sanitized.getString("CustomName"));
        assertFalse(sanitized.hasKey("Items"), "Survival blueprints must not copy container items.");
        assertFalse(sanitized.hasKey("ForgeCaps"), "Capability payloads can contain free resources.");
        assertFalse(sanitized.hasKey("Tank"), "Fluid contents must not be copied from blueprint NBT.");
        assertTrue(source.hasKey("Items"), "The sanitizer must not mutate original blueprint NBT.");
    }

    @Test
    void nestedItemStackCompoundsAreRemovedWithoutDroppingNeutralData() {
        NBTTagCompound source = new NBTTagCompound();
        source.setString("id", "minecraft:decorated_pot");

        NBTTagCompound nested = new NBTTagCompound();
        nested.setString("owner_note", "keep me");
        nested.setTag("preview_stack", itemStack("minecraft:emerald", 3));
        source.setTag("display", nested);

        NBTTagCompound sanitized = BlueprintBlockEntitySanitizer.sanitizeForSurvivalPlacement(source);
        NBTTagCompound display = sanitized.getCompoundTag("display");

        assertEquals("keep me", display.getString("owner_note"));
        assertFalse(display.hasKey("preview_stack"), "Nested item stacks must not survive sanitizing.");
    }

    @Test
    void survivalPlacementDropsDangerousExecutableAndGeneratedContent() {
        NBTTagCompound source = new NBTTagCompound();
        source.setString("id", "minecraft:command_block");
        source.setString("Command", "give @a minecraft:diamond 64");
        source.setTag("SpawnData", new NBTTagCompound());
        source.setInteger("Primary", 5);
        source.setString("LootTable", "minecraft:chests/end_city_treasure");
        source.setString("front_text", "{\"messages\":[\"malicious\"]}");
        source.setString("Text1", "{\"text\":\"legacy sign\"}");

        NBTTagCompound sanitized = BlueprintBlockEntitySanitizer.sanitizeForSurvivalPlacement(source);

        assertEquals("minecraft:command_block", sanitized.getString("id"));
        assertFalse(sanitized.hasKey("Command"), "Command blocks must not import executable commands.");
        assertFalse(sanitized.hasKey("SpawnData"), "Spawner payloads must not import entity spawn data.");
        assertFalse(sanitized.hasKey("Primary"), "Beacon effects must not be imported for free.");
        assertFalse(sanitized.hasKey("LootTable"), "Loot tables must not be imported from blueprint NBT.");
        assertFalse(sanitized.hasKey("front_text"), "Modern sign text is user-authored content.");
        assertFalse(sanitized.hasKey("Text1"), "Legacy sign text is user-authored content.");
        assertTrue(source.hasKey("Command"), "The original blueprint tag is kept intact.");
    }

    private static NBTTagList itemList(String itemId, int count) {
        NBTTagList items = new NBTTagList();
        items.appendTag(itemStack(itemId, count));
        return items;
    }

    private static NBTTagCompound itemStack(String itemId, int count) {
        NBTTagCompound stack = new NBTTagCompound();
        stack.setString("id", itemId);
        stack.setInteger("count", count);
        return stack;
    }
}
