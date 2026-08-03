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
        String soundSource = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementSound.java");
        String method = methodBody(soundSource,
                "public static void playRemoteBlockBreakSound(EntityPlayerMP player, WorldServer level,");

        assertTrue(soundSource.contains("BlockPos pos, IBlockState brokenState)"),
                "远程破坏声必须接收破坏前的 1.12 方块状态");
        assertTrue(method.contains(
                "brokenState.getBlock().getSoundType(brokenState, level, pos, player)"),
                "破坏声必须使用被破坏方块自己的 SoundType");
        assertFalse(method.contains("level.getBlockState(pos)"),
                "破坏声方法不得在方块变成空气后重新读取当前位置");
    }

    @Test
    void miningAndPlacedRecoveryPassPreBreakStateToSound() throws IOException {
        String miningSource = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsMiningStateMachine.java");
        String miningBody = methodBody(miningSource,
                "public static MiningBreakResult destroyMinedBlock");
        assertTrue(miningBody.contains(
                "IBlockState beforeState = player.getServerWorld().getBlockState(pos);"));
        assertTrue(miningBody.contains(
                "playRemoteBlockBreakSound(player, player.getServerWorld(), pos, beforeState)"));

        String recoverySource = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsPlacedRecoveryService.java");
        String recoveryBody = methodBody(recoverySource, "public static void breakPlaced");
        assertTrue(recoveryBody.contains("IBlockState state = level.getBlockState(targetPos);"));
        assertTrue(recoveryBody.contains("playRemoteBlockBreakSound(player, level, targetPos, state)"));
    }

    @Test
    void batchSoundsAreRelativeNonAttenuatedAndNeverQueuedAcrossTicks() throws IOException {
        String serverSource = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementSound.java");
        String clientSource = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/sound/RtsBlockActionSoundPlayer.java")
                .replace("\r\n", "\n");
        String configSource = read("src/main/java/com/rtsbuilding/rtsbuilding/Config.java");
        String modSource = read("src/main/java/com/rtsbuilding/rtsbuilding/RtsbuildingMod.java");
        String packetRegistry = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/network/builder/RtsBlockActionSoundPackets1122.java");
        String dispatcher = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/network/ClientPayloadDispatcher.java");

        assertTrue(serverSource.contains("S2CRtsBlockActionSoundPayload"));
        assertTrue(clientSource.contains("SoundCategory.BLOCKS"),
                "相对声音仍必须服从玩家的方块音量设置");
        assertTrue(clientSource.contains("ISound.AttenuationType.NONE"),
                "RTS 批量声音不得按玩家实体与相机距离衰减");
        assertTrue(serverSource.contains("SOUND_LIMITER.tryAcquire"));
        assertTrue(serverSource.contains("RtsClientboundPackets.sendToPlayer"));
        assertFalse(serverSource.contains("PENDING_SOUNDS") || serverSource.contains("tickPlayer("));
        assertFalse(clientSource.contains("QUEUE") || clientSource.contains("drainTick"));
        assertFalse(clientSource.contains("isActive(activeSound)"));
        assertTrue(configSource.contains("\"remoteBlockActionSoundsPerTick\", 16, 0, 16"));
        assertFalse(modSource.contains("RtsPlacementSound.tickPlayer("));
        assertTrue(modSource.contains("RtsPlacementSound.forgetPlayer(player.getUniqueID())"));
        assertTrue(clientSource.contains(
                "ISound.AttenuationType.NONE,\n                0.0F,\n                0.0F,\n                0.0F"),
                "声音实例必须固定在监听器原点，跟随当前 RTS 相机");
        assertTrue(packetRegistry.contains("S2CRtsBlockActionSoundPayload.class")
                && packetRegistry.contains("Side.CLIENT"));
        assertTrue(dispatcher.contains("class BlockActionSoundHandler")
                && dispatcher.contains("invokeStatic(BLOCK_ACTION_SOUND_PLAYER, \"play\""));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "method not found: " + signatureStart);
        int bodyStart = source.indexOf('{', start);
        assertTrue(bodyStart >= 0, "method body not found: " + signatureStart);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(bodyStart, i + 1);
        }
        throw new AssertionError("method body is not closed: " + signatureStart);
    }
}
