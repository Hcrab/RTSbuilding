package com.rtsbuilding.rtsbuilding.server.service.transfer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsBdOnlyTransferContractTest {
    @Test
    void menuSlotImportAcceptsBeyondDimensionsOnlyStorageSessions() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/transfer/RtsTransferPlayerIntegration.java"));
        String body = methodBody(source, "public static void importMenuSlotToLinked(ServerPlayer player, RtsStorageSession session, int menuSlot)");

        assertTrue(body.contains("RtsLinkedStorageResolver.hasAnyStorage(player, session)"),
                "Shift-import must consider BD-only storage sessions valid storage.");
        assertFalse(body.contains("session.linkedStorageInfo.isEmpty()"),
                "Shift-import must not reject sessions just because they have no linked block storage.");
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
