package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsPlaceAnimationPayload;
import com.rtsbuilding.rtsbuilding.server.service.SoundService;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sound and animation effects for RTS remote block placement.
 *
 * <p>This helper owns only auditory and visual feedback emitted after a
 * remote placement succeeds: per-block break/replace sounds, per-block
 * animation packets. It deliberately does not execute placement, extract
 * items, or manage batch jobs.
 */
public final class RtsPlacementSound {

    private static final int MAX_SOUNDS_PER_TICK = 1;
    private static final Map<UUID, Integer> PER_PLAYER_SOUNDS_THIS_TICK = new HashMap<>();
    private static long SOUND_RESET_TICK = -1L;

    private RtsPlacementSound() {
    }

    /**
     * Sends a block-break animation packet to the player for the given
     * position.
     */
    public static void playRemotePlacedBlockAnimation(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return;
        }
        BlockState state = player.serverLevel().getBlockState(pos);
        PacketDistributor.sendToPlayer(player, new S2CRtsPlaceAnimationPayload(pos.immutable(), state));
    }

    /**
     * Plays the block-place sound for a remotely placed block.
     * <p>
     * Sound is throttled to at most 2 plays per game tick per player to avoid
     * cacophony during large batch placements.
     */
    public static void playRemotePlacedBlockSound(ServerPlayer player, ServerLevel level,
                                                   BlockPos pos) {
        if (player == null || level == null || pos == null || !level.hasChunkAt(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        // Per-player per-tick throttle: max 2 sounds/tick
        long currentTick = level.getGameTime();
        if (currentTick != SOUND_RESET_TICK) {
            SOUND_RESET_TICK = currentTick;
            PER_PLAYER_SOUNDS_THIS_TICK.clear();
        }
        int count = PER_PLAYER_SOUNDS_THIS_TICK.getOrDefault(player.getUUID(), 0);
        if (count >= MAX_SOUNDS_PER_TICK) {
            return;
        }
        PER_PLAYER_SOUNDS_THIS_TICK.put(player.getUUID(), count + 1);
        SoundType soundType = state.getSoundType(level, pos, player);
        Vec3 soundPos = cameraOrEyePos(player);
        SoundService.sendDirectSound(
                player,
                soundType.getPlaceSound(),
                SoundSource.BLOCKS,
                soundPos.x,
                soundPos.y,
                soundPos.z,
                (soundType.getVolume() + 1.0F) / 2.0F,
                soundType.getPitch() * 0.8F);
    }

    /**
     * Plays the block-break sound for a remotely mined/destroyed block.
     */
    public static void playRemoteBlockBreakSound(ServerPlayer player, ServerLevel level,
                                                  BlockPos pos) {
        if (player == null || level == null || pos == null || !level.hasChunkAt(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        SoundType soundType = state.getSoundType(level, pos, player);
        Vec3 soundPos = cameraOrEyePos(player);
        SoundService.sendDirectSound(
                player,
                soundType.getBreakSound(),
                SoundSource.BLOCKS,
                soundPos.x,
                soundPos.y,
                soundPos.z,
                (soundType.getVolume() + 1.0F) / 2.0F,
                soundType.getPitch() * 0.8F);
    }

    private static Vec3 cameraOrEyePos(ServerPlayer player) {
        Vec3 pos = RtsCameraManager.getCameraPosition(player);
        return pos != null ? pos : player.getEyePosition();
    }
}
