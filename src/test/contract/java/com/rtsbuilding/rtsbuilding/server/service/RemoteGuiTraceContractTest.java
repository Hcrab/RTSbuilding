package com.rtsbuilding.rtsbuilding.server.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁住一次远程 GUI 请求从收包到关闭的低频、同 trace 诊断链。 */
class RemoteGuiTraceContractTest {
    @Test
    void serverStagesAndTerminalPacketRemainWired() throws Exception {
        String handler = read("network/builder/handler/RtsPlacementActionHandlers1122.java");
        String interaction = read("server/service/impl/RtsInteractionServiceImpl.java");
        String lease = read("server/service/RtsRemoteMenuChunkLease.java");
        String menu = read("server/service/RtsRemoteMenuService.java");
        String compat = read("compat/remote/RtsRemoteMenuCompat.java");
        String packets = read("network/builder/RtsPlacementActionPackets1122.java");

        assertTrue(handler.contains("event=C2S_RECEIVED"));
        assertTrue(handler.contains("event=RESULT"));
        assertTrue(interaction.contains("event=INTERACTION_BEGIN"));
        assertTrue(interaction.contains("event=INTERACTION_RETURN"));
        assertTrue(lease.contains("event=CHUNK_PREPARE"));
        assertTrue(menu.contains("event=MENU_MARKED"));
        assertTrue(menu.contains("event=MENU_CLEARED"));
        assertTrue(compat.contains("event=STILL_VALID_BEFORE_MARK"));
        assertTrue(compat.contains("event=STILL_VALID_FORCED"));
        assertTrue(compat.contains("event=STILL_VALID_MISMATCH"));
        assertTrue(packets.contains("S2CRtsRemoteMenuResultPayload.class"));
        assertTrue(packets.contains("registerMessage(166"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/" + relative),
                StandardCharsets.UTF_8);
    }
}
