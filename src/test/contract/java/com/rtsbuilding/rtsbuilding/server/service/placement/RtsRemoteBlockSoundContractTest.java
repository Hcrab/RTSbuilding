package com.rtsbuilding.rtsbuilding.server.service.placement;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsRemoteBlockSoundContractTest {
    @Test
    void breakSoundUsesStateCapturedBeforeDestroy() throws IOException {
        String soundSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementSound.java"));
        String method = methodBody(soundSource,
                "public static void playRemoteBlockBreakSound(ServerPlayer player, ServerLevel level,");

        assertTrue(soundSource.contains("BlockPos pos, BlockState brokenState)"),
                "远程破坏声必须接收破坏前的方块状态，不能在方块变成空气后再读。");
        assertTrue(method.contains("brokenState.getSoundType(level, pos, player)"),
                "破坏声应使用被破坏方块自己的 SoundType。");
        assertFalse(method.contains("level.getBlockState(pos)"),
                "破坏声方法内部不能重新读取当前位置，否则批量破坏后常会读到空气。");
    }

    @Test
    void miningAndPlacedRecoveryPassPreBreakStateToSound() throws IOException {
        String miningSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsMiningStateMachine.java"));
        String miningBody = methodBody(miningSource,
                "public static MiningBreakResult destroyMinedBlock");
        assertTrue(miningBody.contains("BlockState beforeState = player.serverLevel().getBlockState(pos);"),
                "普通挖掘/连锁挖掘应先捕获破坏前状态。");
        assertTrue(miningBody.contains("playRemoteBlockBreakSound(player, player.serverLevel(), pos, beforeState)"),
                "普通挖掘/连锁挖掘应把破坏前状态传给相机位置声音。");

        String recoverySource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsPlacedRecoveryService.java"));
        String recoveryBody = methodBody(recoverySource,
                "public static void breakPlaced");
        assertTrue(recoveryBody.contains("BlockState state = level.getBlockState(targetPos);"),
                "已记录放置方块回收应先捕获破坏前状态。");
        assertTrue(recoveryBody.contains("playRemoteBlockBreakSound(player, level, targetPos, state)"),
                "已记录放置方块回收应把破坏前状态传给相机位置声音。");
    }

    @Test
    void batchSoundsAreRelativeNonAttenuatedAndNeverQueuedAcrossTicks() throws IOException {
        String serverSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementSound.java"));
        String clientSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/sound/RtsBlockActionSoundPlayer.java"));
        String configSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/Config.java"));
        String modSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/RtsbuildingMod.java"));
        String packetRegistry = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/network/builder/RtsBuilderPackets.java"));
        String dispatcher = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/network/ClientPayloadDispatcher.java"));

        assertTrue(serverSource.contains("S2CRtsBlockActionSoundPayload"),
                "服务端应发送方块音色，而不是把声音固定在可能滞后的世界坐标。");
        assertTrue(clientSource.contains("SoundSource.BLOCKS"),
                "相对声音仍应服从玩家的方块音量设置。");
        assertTrue(clientSource.contains("SoundInstance.Attenuation.NONE"),
                "RTS 批量声音不能随着玩家实体和相机的距离衰减。");
        assertTrue(serverSource.contains("SOUND_LIMITER.tryAcquire"),
                "服务端应按玩家和当前 tick 限制声音数量。");
        assertTrue(serverSource.contains("RtsClientboundPackets.sendToPlayer"),
                "额度内的声音应当立即发送，不能进入延迟队列。");
        assertFalse(serverSource.contains("PENDING_SOUNDS") || serverSource.contains("tickPlayer("),
                "方块操作完成后不得继续排出声音尾巴。");
        assertFalse(clientSource.contains("QUEUE") || clientSource.contains("drainTick"),
                "客户端收到声音后应立即播放，不得跨 tick 缓存。");
        assertFalse(clientSource.contains("isActive(activeSound)"),
                "当前样本仍在播放时也不应吞掉后续方块声音。");
        assertTrue(configSource.contains("remoteBlockActionSoundsPerTick\", 16, 0, 16"),
                "新的配置键应默认允许每 tick 最多 16 声，避免旧默认值 1 延续到已有运行目录。");
        assertFalse(modSource.contains("RtsPlacementSound.tickPlayer(serverPlayer)"),
                "玩家 tick 不应再驱动任何声音队列。");
        assertTrue(modSource.contains("RtsPlacementSound.forgetPlayer(serverPlayer.getUUID())"),
                "玩家离线时仍应清理限流计数状态。");
        assertTrue(clientSource.contains("0.0D,\n                true"),
                "声音实例必须相对监听器播放，跟随当前 RTS 相机。");
        assertTrue(packetRegistry.contains("S2CRtsBlockActionSoundPayload.STREAM_CODEC"),
                "相对方块声音必须注册为 S2C 数据包。");
        assertTrue(dispatcher.contains("case S2CRtsBlockActionSoundPayload p ->"),
                "专用服务端也必须能安全分发声音包，而不直接加载客户端类。");
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "method not found: " + signatureStart);
        int bodyStart = source.indexOf('{', start);
        assertTrue(bodyStart >= 0, "method body not found: " + signatureStart);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, i + 1);
                }
            }
        }
        throw new AssertionError("method body is not closed: " + signatureStart);
    }
}
