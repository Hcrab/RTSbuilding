package com.rtsbuilding.rtsbuilding.client.input;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定容器 Overlay 与 RTS 底栏共用物品数量覆盖层，避免旧深度顺序再次回流。 */
class ContainerOverlaySlotCountContractTest {
    @Test
    void overlayPaginationMustUseExactlyTheExpandedVisibleCapacity() throws IOException {
        String helper = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/input/overlay/OverlayLayoutHelper.java"));
        assertTrue(helper.contains("public static int overlayStoragePageCapacity(OverlayProfile profile)"));
        assertTrue(helper.contains("return STORAGE_COLS * Math.max(1, profile.storageRows());"));

        String gate = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/input/RtsClientInputGate.java"));
        int capacitySync = gate.indexOf("controller.updateStoragePageSize(overlayStoragePageCapacity(profile));");
        int bootstrap = gate.indexOf("if (!controller.canUseStorageOverlay())", capacitySync);
        assertTrue(capacitySync >= 0, "容器 overlay 必须把响应式可见容量同步给分页请求。");
        assertTrue(bootstrap > capacitySync, "首次存储快照也必须使用 overlay 的真实容量，不能先按默认 90 格请求。");
        assertTrue(gate.contains("int visibleStorageSlots = STORAGE_COLS * visibleStorageRows;"));
        assertFalse(helper.contains("overlayStoragePageCapacity(OverlayLayout"),
                "收起状态不得把服务端分页缩成一行，否则展开时页面边界会再次漂移。");
    }

    @Test
    void clearingOverlaySearchMustKeepKeyboardFocus() throws IOException {
        String router = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/input/RtsClientPointerRouter.java"));
        String clearBranch = "overlaySearchDraft = \"\";";
        int first = router.indexOf(clearBranch);
        int second = router.indexOf(clearBranch, first + clearBranch.length());
        assertTrue(first >= 0 && second > first, "展开和收起 overlay 都必须存在搜索清除分支。");
        assertTrue(router.substring(first, Math.min(first + 320, router.length()))
                .contains("setOverlaySearchFocused(true);"));
        assertTrue(router.substring(second, Math.min(second + 320, router.length()))
                .contains("setOverlaySearchFocused(true);"));
    }

    @Test
    void 侧边Overlay数量必须复用底栏的高层级紧凑绘制器() throws IOException {
        String helper = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/input/overlay/OverlayLayoutHelper.java"));
        int methodStart = helper.indexOf("public static void drawSlotCountOverlay");
        int methodEnd = helper.indexOf("public static String sortShort", methodStart);
        String method = helper.substring(methodStart, methodEnd);

        assertTrue(method.contains("RtsClientUiUtil.drawSlotCountOverlay"));
        assertFalse(method.contains("font.getStringWidth(countText)"));
        assertFalse(method.contains("g.drawString(font, countText"));

        String shared = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/util/RtsClientUiUtil.java"));
        assertTrue(shared.contains("GlStateManager.translate(0.0F, 0.0F, 300.0F)"));
        assertTrue(shared.contains("RtsMainlineTheme.SLOT_COUNT_BACKGROUND"));
        assertTrue(shared.contains("SLOT_COUNT_SCALE"));
        assertTrue(shared.contains("countText, scaledX - textWidth, scaledY, color, false"));

        String gate = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/input/RtsClientInputGate.java"));
        assertTrue(gate.indexOf("g.renderItem(entry.stack(), cx + 1, cy + 1)")
                < gate.indexOf("drawSlotCountOverlay(g, minecraft.fontRenderer, cx, cy, SLOT_SIZE"));

        String renderer = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/input/overlay/OverlayRenderer.java"));
        assertTrue(renderer.indexOf("g.renderItem(preview, cx + 1, cy + 1)")
                < renderer.lastIndexOf("drawSlotCountOverlay(g, font, cx, cy, SLOT_SIZE"));

        String terminal = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/RtsCraftTerminalScreen.java"));
        assertTrue(terminal.indexOf("g.renderItem(entry.stack(), sx + 1, sy + 1)")
                < terminal.indexOf("drawCountOverlay(g, sx, sy"));
        assertTrue(terminal.contains("RtsClientUiUtil.drawSlotCountOverlay("));
        assertFalse(terminal.contains("float scale = .65F"));
    }
}
