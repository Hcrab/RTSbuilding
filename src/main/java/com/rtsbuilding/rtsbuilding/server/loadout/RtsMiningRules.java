package com.rtsbuilding.rtsbuilding.server.loadout;

import java.util.OptionalInt;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class RtsMiningRules {
    private RtsMiningRules() {
    }

    public static MiningLoadoutRole requiredRole(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return MiningLoadoutRole.PICK;
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return MiningLoadoutRole.SHOVEL;
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return MiningLoadoutRole.AXE;
        }
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            return MiningLoadoutRole.HOE;
        }
        return null;
    }

    public static int requiredLevel(BlockState state) {
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
            return 3;
        }
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) {
            return 2;
        }
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) {
            return 1;
        }
        return 0;
    }

    public static int toolLevel(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.contains("netherite")) {
            return 4;
        }
        if (path.contains("diamond")) {
            return 3;
        }
        if (path.contains("iron")) {
            return 2;
        }
        if (path.contains("stone") || path.contains("golden")) {
            return 1;
        }
        return 0;
    }

    public static boolean hasRequiredLoadoutTool(ServerPlayer player, BlockState state) {
        MiningLoadoutRole role = requiredRole(state);
        if (role == null) {
            return true;
        }

        OptionalInt slotOpt = MiningLoadoutState.getSlot(player, role);
        if (slotOpt.isEmpty()) {
            return false;
        }

        ItemStack toolStack = player.getInventory().getItem(slotOpt.getAsInt());
        if (toolStack.isEmpty()) {
            return false;
        }

        int required = requiredLevel(state);
        int actual = toolLevel(toolStack);
        return actual >= required && toolStack.isCorrectToolForDrops(state);
    }
}
