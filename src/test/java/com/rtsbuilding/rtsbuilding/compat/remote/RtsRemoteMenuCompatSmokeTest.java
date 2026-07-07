package com.rtsbuilding.rtsbuilding.compat.remote;

import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.FakeBackpackMenu;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsRemoteMenuCompatSmokeTest {
    @Test
    void localSophisticatedMenusAreDetectedAndBlockedFromRtsSlotHijack() {
        FakeBackpackMenu menu = allocateMenuWithoutMinecraftBootstrap();

        assertTrue(RtsRemoteMenuCompat.isSophisticatedMenu(menu));
        assertTrue(RtsRemoteMenuCompat.isLocalSophisticatedMenu(menu, null));
    }

    @Test
    void transferEntrypointsKeepLocalSophisticatedGuard() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/transfer/RtsTransferPlayerIntegration.java"));

        assertTrue(source.contains("RtsRemoteMenuCompat.isLocalSophisticatedMenu(menu, player)"),
                "Shift 导入本地 Sophisticated 菜单时必须直接 return，避免把同一份槽位同时当来源和目标。");
        assertTrue(source.contains("RtsRemoteMenuCompat.isLocalSophisticatedMenu(player.containerMenu, player)"),
                "从 linked storage 快速移动到本地 Sophisticated 菜单时必须改走玩家背包，避免向打开的背包菜单回灌。");
    }

    private static FakeBackpackMenu allocateMenuWithoutMinecraftBootstrap() {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            return (FakeBackpackMenu) unsafe.allocateInstance(FakeBackpackMenu.class);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("无法创建不触发 Minecraft bootstrap 的 Sophisticated 菜单替身", e);
        }
    }
}
