package com.rtsbuilding.rtsbuilding.compat.remote;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 远程 GUI 支持应由已登记 windowId 决定，不应退回脆弱的模组类名白名单。 */
class RemoteContainerPersistenceContractTest {
    @Test
    void genericPlayerValidationBoundaryOwnsRemotePersistence() throws Exception {
        String compat = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/compat/remote/RtsRemoteMenuCompat.java"));
        String mixin = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/mixin/RemoteContainerPlayerMixin.java"));
        String baseMixin = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/mixin/RemoteBasePlayerContainerMixin.java"));
        String client = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/RtsClientRemoteMenuCompat.java"));

        assertTrue(compat.contains("menu != null && menu.windowId != 0"));
        assertTrue(mixin.contains("@Mixin(EntityPlayerMP.class)"));
        assertTrue(mixin.contains("Container;canInteractWith"));
        assertTrue(mixin.contains("shouldForceStillValid(container, player)"));
        assertTrue(baseMixin.contains("@Mixin(EntityPlayer.class)"));
        assertTrue(baseMixin.contains("method = \"onUpdate\""));
        assertTrue(baseMixin.contains("shouldForceStillValid(container, player)"));
        assertFalse(client.contains("AlwaysValidInventory"),
                "客户端不能反射替换第三方容器内部库存身份");
    }

}
