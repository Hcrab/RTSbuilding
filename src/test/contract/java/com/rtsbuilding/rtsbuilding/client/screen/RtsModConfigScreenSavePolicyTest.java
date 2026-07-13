package com.rtsbuilding.rtsbuilding.client.screen;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsModConfigScreenSavePolicyTest {
    @Test
    void saveAndCloseGuardsProgressionNetworkSync() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/RtsModConfigScreen.java"));
        int saveMethod = source.indexOf("private void saveAndClose()");
        int syncCall = source.indexOf("ClientRtsController.get().setSurvivalProgressionEnabled", saveMethod);
        int guardCall = source.lastIndexOf("shouldSyncProgressionToServer(this.minecraft)", syncCall);

        assertTrue(saveMethod >= 0 && syncCall > saveMethod && guardCall > saveMethod,
                "从主菜单模组 Config 保存时没有服务器连接，保存本地配置后不能无条件发送进度同步 C2S 包。");
    }

    @Test
    void mainMenuConfigSaveDoesNotNeedServerSync() {
        assertFalse(RtsModConfigScreen.shouldSyncProgressionToServer(false, false, false));
        assertFalse(RtsModConfigScreen.shouldSyncProgressionToServer(false, false, true));
        assertFalse(RtsModConfigScreen.shouldSyncProgressionToServer(true, false, true));
        assertFalse(RtsModConfigScreen.shouldSyncProgressionToServer(true, true, false));
    }

    @Test
    void inWorldConfigSaveCanSyncProgressionToServer() {
        assertTrue(RtsModConfigScreen.shouldSyncProgressionToServer(true, true, true));
    }
}
