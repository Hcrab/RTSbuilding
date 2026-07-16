package com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsBackpackRoutingContractTest {
    @Test
    void carriedBackpackKeepsUuidBindingAndPlacementNeverFallsBackToOpen() throws Exception {
        String compat = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/compat/sophisticatedbackpacks/RtsBackpackCompat.java"));
        String screen = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/BuilderScreen.java"));
        String placement = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementExecutor.java"));
        String lifecycle = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/storage/RtsLinkedStorageResolver.java"));

        assertTrue(compat.contains("PlayerInventoryProvider$BackpackInventorySlotConsumer")
                        && compat.contains("CapabilityBackpackWrapper")
                        && compat.contains("findCarriedBackpack(player, uuid)"),
                "旧版必须同时覆盖 Forge capability 与精妙背包注册的饰品/Curios 槽位。");
        assertTrue(screen.contains("forcePlace || forceBackpackPlacement")
                        && screen.contains("!forceBackpackPlacement && !forcePlace"),
                "客户端右键精妙背包必须绕过交互并进入强制放置链路。");
        assertTrue(placement.contains("forcePlace || sophisticatedBackpackPlacementOnly")
                        && placement.contains("!sophisticatedBackpackPlacementOnly && !selectedOutcome.result().consumesAction()"),
                "服务端精妙背包放置失败时不得回退到 useItem 打开界面。");
        assertTrue(lifecycle.contains("detachedBackpackRefs.add(ref)"),
                "背包被挖走后必须保留 UUID 绑定并切换到随身解析状态。");
        assertFalse(lifecycle.contains("removeBrokenLinkedStorageRef"),
                "背包被挖走不能删除 UUID 绑定。");
    }
}
