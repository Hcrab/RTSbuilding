package com.rtsbuilding.rtsbuilding.mixin;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComposableExpressionMixinContractTest {
    @Test
    void remoteMenuGuardUsesOptionalComposableExpressionModification() throws Exception {
        String source = read("ServerPlayerRemoteMenuMixin.java");

        assertTrue(source.contains("@ModifyExpressionValue("));
        assertTrue(source.contains("require = 0"));
        assertTrue(source.contains("original || RtsRemoteMenuCompat.shouldKeepServerRemoteMenuOpen"));
        assertFalse(source.contains("@Redirect("));
    }

    @Test
    void createCursorBridgePreservesTheOriginalExpressionResult() throws Exception {
        String source = read("CreateWorldshaperRenderHandlerMixin.java");

        assertTrue(source.contains("@ModifyExpressionValue("));
        assertTrue(source.contains("hit != null ? hit : original"));
        assertFalse(source.contains("@Redirect("));
    }

    private static String read(String fileName) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/mixin", fileName));
    }
}
