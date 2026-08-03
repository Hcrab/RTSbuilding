package com.rtsbuilding.rtsbuilding.server.service.transfer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayCarriedStackSyncContractTest {
    private static final Path HANDLER = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/network/storage/handler/RtsTransferHandlers.java");

    @Test
    void pickupAndReturnAlwaysUseAuthoritativeCarriedStackSchedule() throws IOException {
        String source = Files.readString(HANDLER);
        String pickup = nestedClassBody(source, "public static final class LinkedPickup");
        String returned = nestedClassBody(source, "public static final class ReturnCarried");

        assertTrue(pickup.contains("scheduleCarriedTransfer(context"),
                "Overlay pickup must acknowledge the authoritative server cursor stack.");
        assertTrue(returned.contains("scheduleCarriedTransfer(context"),
                "Overlay return must acknowledge the authoritative server cursor stack.");
    }

    @Test
    void authoritativeCursorSyncRunsForSuccessBusinessRejectionAndFailure() throws IOException {
        String source = Files.readString(HANDLER);
        String schedule = methodBody(source,
                "private static void scheduleCarriedTransfer(MessageContext context, final Action action)");
        String sync = methodBody(source, "private static void syncCarriedStack(EntityPlayerMP player)");

        assertTrue(schedule.contains("try"), "Carried transfer scheduling must guard its acknowledgement.");
        assertTrue(schedule.contains("finally"),
                "Server cursor truth must be sent even when transfer execution rejects or fails.");
        assertTrue(schedule.contains("action.run(player);"),
                "A visible container overlay must execute independently from RTS camera activity.");
        assertTrue(schedule.contains("syncCarriedStack(player);"),
                "Business rejection and exceptions must also clear a client-side optimistic ghost stack.");
        assertTrue(sync.contains("new SPacketSetSlot("),
                "Minecraft 1.12 cursor stacks require the vanilla set-slot packet.");
        assertTrue(sync.contains("-1, -1"),
                "window=-1/slot=-1 is the vanilla 1.12 cursor-stack channel.");
        assertTrue(sync.contains("carried.copy()"),
                "The network acknowledgement must not expose the mutable server stack instance.");
    }

    private static String nestedClassBody(String source, String signatureStart) {
        return bracedBody(source, signatureStart);
    }

    private static String methodBody(String source, String signatureStart) {
        return bracedBody(source, signatureStart);
    }

    private static String bracedBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "declaration not found: " + signatureStart);
        int bodyStart = source.indexOf('{', start);
        assertTrue(bodyStart >= 0, "body not found: " + signatureStart);
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
        throw new AssertionError("body is not closed: " + signatureStart);
    }
}
