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

    @Test
    void clientRefreshesEveryVisibleStorageSurface() throws IOException {
        String controller = source("com/rtsbuilding/rtsbuilding/client/controller/ClientRtsLifecycleOwner.java");
        String screen = source("com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");
        String bottomPanel = source("com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanel.java");

        assertTrue(controller.contains("minecraft.screen instanceof RtsCraftTerminalScreen"),
                "合成终端打开期间必须消费储存脏通知并刷新实时数量");
        assertTrue(controller.contains("builderScreen.isStorageViewVisible()"));
        assertTrue(screen.contains("this.bottomPanel.isStorageBrowserVisible()"));
        assertTrue(bottomPanel.contains("activeBottomPanelTab() == BottomPanelLayoutTypes.BottomPanelTab.STORAGE"),
                "BuilderScreen 虽已打开，创造/蓝图标签仍必须保持 0 次自动构页");
        assertFalse(controller.contains("tickStorageAutoRefresh(controller.storageStateManager.isStorageViewDirty())"));
    }

    @Test
    void craftTerminalStorageGridDoesNotRepeatPlayerInventory() throws IOException {
        String pageHelpers = source("com/rtsbuilding/rtsbuilding/server/service/page/RtsPageSharedHelpers.java");

        assertTrue(pageHelpers.contains("player.containerMenu instanceof com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu"),
                "终端上方储存页必须排除下方已经单独显示的玩家背包和快捷栏");
    }

    @Test
    void pageServiceQueuesEveryCallerInsteadOfBuildingImmediately() throws IOException {
        String pageService = source("com/rtsbuilding/rtsbuilding/server/service/impl/RtsPageServiceImpl.java");
        assertTrue(pageService.contains("RtsStoragePageRequestCoalescer.enqueue"));
        assertTrue(pageService.contains("private void buildPageNow"));
        assertFalse(pageService.contains("public void buildPageNow"));
    }
}
