package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsPlaceAnimationPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBlockActionSoundPayload;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import net.minecraft.block.Block;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.UUID;

/**
 * RTS 远程方块放置/破坏的声音播放和动画效果。
 *
 * <p>此类负责在远程操作成功后向玩家发送听觉和视觉反馈。
 * 所有方法均为 {@code static}，类本身为不可实例化的工具类。
 *
 * <p><b>核心方法：</b>
 * <ul>
 *   <li>{@link #playRemotePlacedBlockAnimation(ServerPlayer, BlockPos)} —
 *       发送方块破坏动画数据包（{@link S2CRtsPlaceAnimationPayload}）给玩家</li>
 *   <li>{@link #playRemotePlacedBlockSound(ServerPlayer, ServerLevel, BlockPos)} —
 *       播放远程放置方块的放置声音</li>
 *   <li>{@link #playRemoteBlockBreakSound(ServerPlayer, ServerLevel, BlockPos, BlockState)} —
 *       播放远程挖掘方块的破坏声音</li>
 * </ul>
 *
 * <p><b>限流机制：</b>每名玩家每 tick 最多发送
 * {@link Config#remotePlaceSoundsPerTick()} 个声音。超过上限的声音直接丢弃，不跨 tick 排队，
 * 因而不会在批量操作结束后继续播放尾音。
 *
 * <p><b>声音定位：</b>服务端只选择方块音色并限流，客户端把它作为相对监听器的方块声音播放，
 * 避免大范围操作时因为玩家实体与 RTS 相机距离不同而逐渐静音。
 *
 * <p><b>设计原则：</b>此类故意不执行放置、物品提取或批处理作业管理，
 * 这些职责位于放置包的其它类中。
 */
public final class RtsPlacementSound {

    private static final RtsBlockActionSoundLimiter SOUND_LIMITER = new RtsBlockActionSoundLimiter();

    private RtsPlacementSound() {
    }

    /**
     * 向玩家发送给定位置的方块破坏动画数据包。
     */
    public static void playRemotePlacedBlockAnimation(EntityPlayerMP player, BlockPos pos) {
        if (player == null || pos == null) {
            return;
        }
        BlockState state = BlockState.fromWorld(player.getServerForPlayer(), pos);
        RtsClientboundPackets.sendToPlayer(player, new S2CRtsPlaceAnimationPayload(pos.toImmutable(), state));
    }

    /**
     * 播放远程放置方块的位置声音。
     * <p>
     * 同 tick 超过服务端上限的声音会直接丢弃，不产生延迟尾音。
     */
    public static void playRemotePlacedBlockSound(EntityPlayerMP player, WorldServer level,
                                                   BlockPos pos) {
        if (player == null || level == null || pos == null || !com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(level, pos)) {
            return;
        }
        BlockState state = BlockState.fromWorld(level, pos);
        if (state.getBlock() == net.minecraft.init.Blocks.air) {
            return;
        }
        if (!tryAcquireSound(player, level)) {
            return;
        }
        Block.SoundType soundType = state.getBlock().stepSound;
        sendBlockActionSound(
                player,
                soundType.func_150496_b(),
                (soundType.getVolume() + 1.0F) / 2.0F,
                soundType.getPitch() * 0.8F,
                false);
    }

    /**
     * 播放远程挖掘/破坏方块的方块破坏声音。
     */
    public static void playRemoteBlockBreakSound(EntityPlayerMP player, WorldServer level,
                                                  BlockPos pos, BlockState brokenState) {
        if (player == null || level == null || pos == null || brokenState == null || !com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(level, pos)) {
            return;
        }
        if (brokenState.getBlock() == net.minecraft.init.Blocks.air) {
            return;
        }
        if (!tryAcquireSound(player, level)) {
            return;
        }
        Block.SoundType soundType = brokenState.getBlock().stepSound;
        sendBlockActionSound(
                player,
                soundType.getBreakSound(),
                (soundType.getVolume() + 1.0F) / 2.0F,
                soundType.getPitch() * 0.8F,
                true);
    }

    private static boolean tryAcquireSound(EntityPlayerMP player, WorldServer level) {
        return SOUND_LIMITER.tryAcquire(
                player.getUniqueID(),
                level.getTotalWorldTime(),
                Config.remotePlaceSoundsPerTick());
    }

    /** 清除离线玩家的 tick 计数状态。 */
    public static void forgetPlayer(UUID playerId) {
        SOUND_LIMITER.forget(playerId);
    }

    private static void sendBlockActionSound(
            EntityPlayerMP player, String sound, float volume, float pitch, boolean breakAction) {
        if (player == null || sound == null) {
            return;
        }
        RtsClientboundPackets.sendToPlayer(
                player,
                new S2CRtsBlockActionSoundPayload(
                        sound,
                        volume,
                        pitch,
                        breakAction));
    }

}
