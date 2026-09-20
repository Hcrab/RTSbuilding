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
    void clientUsesEffectiveStorageTabInsteadOfTreatingBuilderScreenAsVisible() throws IOException {
        String controller = source("com/rtsbuilding/rtsbuilding/client/controller/ClientRtsController.java");
        String screen = source("com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");
        String bottomPanel = source("com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanel.java");

        assertTrue(controller.contains("builderScreen.isStorageViewVisible()"));
        assertTrue(screen.contains("this.bottomPanel.isStorageBrowserVisible()"));
        assertTrue(bottomPanel.contains("activeBottomPanelTab() == BottomPanelLayoutTypes.BottomPanelTab.STORAGE"),
                "BuilderScreen 虽已打开，创造/蓝图标签仍必须保持 0 次自动构页");
        assertFalse(controller.contains("tickStorageAutoRefresh(this.storageStateManager.isStorageViewDirty())"));
    }

    @Test
    void clientRefreshesVisibleContainerOverlayUsingExistingQualification() throws IOException {
        String controller = source("com/rtsbuilding/rtsbuilding/client/controller/ClientRtsController.java");
        String inputGate = source("com/rtsbuilding/rtsbuilding/client/input/RtsClientInputGate.java");

        assertTrue(controller.contains("RtsClientInputGate.canHandleOverlayInput(minecraft.screen)"),
                "容器 Overlay 可见时必须沿用现有输入资格参与储存脏刷新");
        assertTrue(inputGate.contains("public static boolean canHandleOverlayInput(Screen screen)"));
        assertTrue(inputGate.contains("return shouldRenderContainerOverlay(screen);"),
                "刷新资格必须复用 Overlay 的既有容器筛选策略");
    }

    @Test
    void overlayPageSizeTracksVisibleCapacityBeforeFirstRefresh() throws IOException {
        String inputGate = source("com/rtsbuilding/rtsbuilding/client/input/RtsClientInputGate.java");
        String layout = source("com/rtsbuilding/rtsbuilding/client/input/overlay/OverlayLayoutHelper.java");

        int pageSizeUpdate = inputGate.indexOf(
                "controller.updateStoragePageSize(STORAGE_COLS * visibleStorageRows);");
        int bootstrap = inputGate.indexOf("requestOverlayBootstrap(event.getScreen(), controller);");
        int screenSync = inputGate.indexOf("syncOverlayScreen(event.getScreen(), controller);");
        assertTrue(pageSizeUpdate >= 0 && bootstrap > pageSizeUpdate && screenSync > pageSizeUpdate,
                "首次搜索/刷新前必须先把页大小同步为 Overlay 列数乘实际行数");
        assertTrue(inputGate.contains("int visibleStorageRows = layout.overlayCollapsed() ? 1 : layout.storageRows();"),
                "折叠 Overlay 只能请求实际画出的一行，避免一页内存在不可见物品");
        assertTrue(inputGate.contains("int visibleStorageSlots = STORAGE_COLS * visibleStorageRows;"),
                "Overlay 绘制容量必须继续使用同一列数");
        assertTrue(layout.contains("int rows = extremeScale ? 2 : highScale ? 3 : STORAGE_ROWS;"),
                "Overlay 行数必须随 UI scale profile 计算");
    }

    @Test
    void craftTerminalStorageGridDoesNotRepeatPlayerInventory() throws IOException {
        String pageHelpers = source("com/rtsbuilding/rtsbuilding/server/service/page/RtsPageSharedHelpers.java");

        assertTrue(pageHelpers.contains(
                        "player.containerMenu instanceof com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu"),
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
