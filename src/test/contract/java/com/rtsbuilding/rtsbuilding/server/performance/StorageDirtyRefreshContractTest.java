package com.rtsbuilding.rtsbuilding.server.performance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageDirtyRefreshContractTest {
    private static String source(String relative) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir"), "src", "main", "java")
                .resolve(relative));
    }

    private static String clientSource(String relative) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir"), "src", "client", "java")
                .resolve(relative));
    }

    @Test
    void clientUsesEffectiveStorageTabInsteadOfTreatingBuilderScreenAsVisible() throws IOException {
        String controller = clientSource("com/rtsbuilding/rtsbuilding/client/controller/ClientRtsLifecycleOwner.java");
        String screen = clientSource("com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");
        String bottomPanel = clientSource("com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanel.java");

        assertTrue(controller.contains("builderScreen.isStorageViewVisible()"));
        assertTrue(screen.contains("this.bottomPanel.isStorageBrowserVisible()"));
        assertTrue(bottomPanel.contains("activeBottomPanelTab() == BottomPanelLayoutTypes.BottomPanelTab.STORAGE"),
                "BuilderScreen 虽已打开，创造/蓝图标签仍必须保持 0 次自动构页");
        assertFalse(controller.contains("tickStorageAutoRefresh(controller.storageStateManager.isStorageViewDirty())"));
    }

    @Test
    void pageServiceQueuesEveryCallerInsteadOfBuildingImmediately() throws IOException {
        String pageService = source("com/rtsbuilding/rtsbuilding/server/service/impl/RtsPageServiceImpl.java");
        assertTrue(pageService.contains("RtsStoragePageRequestCoalescer.enqueue"));
        assertTrue(pageService.contains("private void buildPageNow"));
        assertFalse(pageService.contains("public void buildPageNow"));
    }
}
