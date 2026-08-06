package com.rtsbuilding.rtsbuilding.client.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteGuiBindingContractTest {
    @Test
    void boundGuiStartsClientHandoffGraceBeforeSendingOpenRequest() throws IOException {
        String controller = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/controller/ClientRtsController.java"));

        int methodStart = controller.indexOf("public void openGuiBinding(int index)");
        int methodEnd = controller.indexOf("public void placeSelected(", methodStart);
        String method = controller.substring(methodStart, methodEnd);

        int grace = method.indexOf("beginRemoteMenuOpenGrace();");
        int send = method.indexOf("this.storageStateManager.openGuiBinding(index);");
        assertTrue(method.contains("hasGuiBinding(index)"),
                "空绑定槽不应启动远程菜单切换宽限期。");
        assertTrue(grace >= 0 && send > grace,
                "必须先暂停 BuilderScreen 自动恢复，再发送远程 GUI 打开请求。");
    }
}
