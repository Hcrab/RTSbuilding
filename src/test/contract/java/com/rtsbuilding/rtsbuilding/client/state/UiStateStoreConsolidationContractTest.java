package com.rtsbuilding.rtsbuilding.client.state;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiStateStoreConsolidationContractTest {
    @Test
    void clientFeaturesUseOnlyTheSharedPersistedUiStateStore() throws IOException {
        Path legacyStore = Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/state/RtsClientUiStateStore.java");
        String onboarding = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/RtsClientOnboardingReminder.java"));
        String soundPlayer = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/sound/RtsBlockActionSoundPlayer.java"));

        assertFalse(Files.exists(legacyStore), "旧 JSON Store 不能与正式二进制 Store 并存");
        assertTrue(onboarding.contains("common.persist.RtsClientUiStateStore"));
        assertTrue(soundPlayer.contains("common.persist.RtsClientUiStateStore"));
        assertFalse(onboarding.contains("client.state.RtsClientUiStateStore"));
        assertFalse(soundPlayer.contains("client.state.RtsClientUiStateStore"));
    }

    @Test
    void deadPreMainlineClientSurfacesStayRemovedWhileForgeCullingAdaptersRemain() throws IOException {
        List<String> deadClasses = List.of(
                "client/screen/guide/GuideTypes.java",
                "client/screen/guide/RtsAiChatStyle.java",
                "client/state/RtsClientLayoutStore.java",
                "client/screen/culling/RtsCullingPanelLayout.java",
                "client/rendering/builder/SkeletonRenderStyle.java",
                "client/screen/ultimine/UltimineMode.java",
                "client/screen/workflow/WorkflowPanelVisibilityGate.java");
        Path javaRoot = Path.of("src/main/java/com/rtsbuilding/rtsbuilding");
        for (String relativePath : deadClasses) {
            assertFalse(Files.exists(javaRoot.resolve(relativePath)), relativePath + " 不应回流");
        }

        List<Path> productionTextFiles;
        try (var files = Files.walk(Path.of("src/main"))) {
            productionTextFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".java") || name.endsWith(".json")
                                || name.endsWith(".toml") || name.endsWith(".properties");
                    })
                    .toList();
        }
        for (String relativePath : deadClasses) {
            String simpleName = Path.of(relativePath).getFileName().toString().replace(".java", "");
            for (Path file : productionTextFiles) {
                assertFalse(Files.readString(file).contains(simpleName),
                        simpleName + " 不能通过反射、资源或服务入口继续存活：" + file);
            }
        }

        assertTrue(Files.exists(javaRoot.resolve(
                "client/screen/culling/RtsCullingMixinVerifier.java")));
        assertTrue(Files.exists(javaRoot.resolve(
                "client/screen/culling/RtsCullingWorldSliceBridge.java")));
    }
}
