package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.server.tracking.RtsBlockTrackingEvents;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 捕获一次玩家 BlockItem.place 前后半径两格内的实际方块变化。
 * 这同时覆盖门、床和双高植物等一次调用写入多个方块的原版行为，不依赖 NeoForge MultiPlaceEvent。
 */
@Mixin(BlockItem.class)
public abstract class BlockItemPlacementTrackingMixin {
    private static final ThreadLocal<ArrayDeque<PlacementSnapshot>> RTS_SNAPSHOTS =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "place", at = @At("HEAD"))
    private void rtsbuilding$captureBeforePlacement(
            BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos center = context.getClickedPos();
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
            before.put(pos.immutable(), level.getBlockState(pos));
        }
        RTS_SNAPSHOTS.get().push(new PlacementSnapshot(player, level, before));
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void rtsbuilding$publishSuccessfulPlacement(
            BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        ArrayDeque<PlacementSnapshot> snapshots = RTS_SNAPSHOTS.get();
        if (snapshots.isEmpty()) {
            return;
        }
        PlacementSnapshot snapshot = snapshots.pop();
        if (snapshots.isEmpty()) {
            RTS_SNAPSHOTS.remove();
        }
        if (!cir.getReturnValue().consumesAction()) {
            return;
        }
        List<BlockPos> changed = new ArrayList<>();
        snapshot.before().forEach((pos, previous) -> {
            BlockState current = snapshot.level().getBlockState(pos);
            if (!current.equals(previous) && !current.isAir()) {
                changed.add(pos);
            }
        });
        RtsBlockTrackingEvents.onPlaced(snapshot.player(), snapshot.level(), changed);
    }

    private record PlacementSnapshot(
            ServerPlayer player, ServerLevel level, Map<BlockPos, BlockState> before) {
    }
}
