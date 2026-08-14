package com.rtsbuilding.rtsbuilding.mixin;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComposableExpressionMixinContractTest {
    @Test
    void remoteMenuGuardUsesOptionalComposableExpressionModification() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/mixin/ServerPlayerRemoteMenuMixin.java"));
        String build = Files.readString(Path.of("build.gradle"));

        assertTrue(source.contains("@ModifyExpressionValue("));
        assertTrue(source.contains("require = 0"));
        assertTrue(source.contains("original || RtsRemoteMenuCompat.shouldKeepServerRemoteMenuOpen"));
        assertFalse(source.contains("@Redirect("));
        assertTrue(build.contains("mixinextras-forge"));
        assertTrue(build.contains("jarJar.enable()"));
    }
}
