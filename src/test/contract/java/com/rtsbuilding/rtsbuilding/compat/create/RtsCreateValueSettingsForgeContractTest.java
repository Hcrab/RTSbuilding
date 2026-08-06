package com.rtsbuilding.rtsbuilding.compat.create;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定 Forge 1.20.1 / Create 0.5.x 的远程数值设置产品语义与兼容边界。
 *
 * <p>这些源码契约不伪造 Create 类型，因而也能直接证明未安装 Create 时没有硬类加载依赖。</p>
 */
class RtsCreateValueSettingsForgeContractTest {
    @Test
    void serverUsesRtsSessionRangeWithoutRestoringCreateNearDistance() throws Exception {
        String server = source("compat/create/RtsCreateValueSettingsServerCompat.java");

        assertTrue(server.contains("RtsCameraManager.isActive(player)"));
        assertTrue(server.contains("RtsCameraManager.isWithinActionRange(player, payload.pos())"));
        assertTrue(server.contains("level.hasChunkAt(payload.pos())"));
        assertTrue(server.contains("level.mayInteract(player, payload.pos())"));
        assertTrue(server.contains("level.dimension().location().equals(payload.dimension())"));
        assertFalse(server.contains("distanceToSqr"));
        assertFalse(server.contains("closerThan"));
        assertFalse(server.contains("canInteractWithBlock"));
        assertFalse(server.contains("COOLDOWN"));
    }

    @Test
    void runtimeTargetsCreateZeroPointFiveByBehaviourNotBlockId() throws Exception {
        String runtime = source("compat/create/RtsCreateValueSettingsRuntime.java");

        assertFalse(runtime.contains("import com.simibubi.create"));
        assertFalse(runtime.contains("BuiltInRegistries"));
        assertTrue(runtime.contains("ValueSettingsBehaviour\""));
        assertTrue(runtime.contains("getAllBehaviours"));
        assertTrue(runtime.contains("onShortInteract\", Player.class, InteractionHand.class, Direction.class"));
        assertTrue(runtime.contains("createBoard\", Player.class, BlockHitResult.class"));
        assertTrue(runtime.contains("setValueSettings\", Player.class, valueSettingsClass, boolean.class"));
    }

    @Test
    void holdAndNativeScreenAreBoundToTheExactRtsTarget() throws Exception {
        String client = source("client/compat/create/RtsCreateValueSettingsCompat.java");
        String mixin = source("mixin/CreateValueSettingsScreenMixin.java");

        assertTrue(client.contains("SCREEN_OPEN_HOLD_TICKS = 5"));
        assertTrue(client.contains("expected.getBlockPos().equals(actual.getBlockPos())"));
        assertTrue(client.contains("expected.getDirection() == actual.getDirection()"));
        assertTrue(client.contains("candidate.blockEntity() != pending.blockEntity()"));
        assertTrue(client.contains("candidate.behaviour() != pending.behaviour()"));
        assertTrue(client.indexOf("if (!sameTarget(pending.hit(), currentHit))")
                < client.indexOf("if (!isMouseButtonDown(minecraft, pending.mouseButton()))"));
        assertTrue(client.contains("session.screen() != screen"));
        assertTrue(mixin.contains("@Pseudo"));
        assertTrue(mixin.contains("ValueSettingsScreen\""));
        assertTrue(mixin.contains("require = 0"));
        assertTrue(mixin.contains("submitNativeScreenSave"));
    }

    @Test
    void dedicatedPacketIsRegisteredOnTheForgeChannel() throws Exception {
        String registrar = source("network/RtsForgePayloadRegistrar.java");
        String packets = source("network/create/RtsCreateValueSettingsPackets.java");

        assertTrue(registrar.contains("RtsCreateValueSettingsPackets.register(registrar)"));
        assertTrue(packets.contains("C2SRtsCreateValueSettingsPayload.TYPE"));
        assertTrue(packets.contains("RtsCreateValueSettingsNetworkHandler::handle"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/" + relative),
                StandardCharsets.UTF_8);
    }
}
