package com.rtsbuilding.rtsbuilding.client.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientRtsControllerInitializationContractTest {
    @Test
    void singletonIsCreatedLazilyAfterSensitivityConstantsInitialize() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/controller/ClientRtsController.java"));

        int sensitivityConstants = source.indexOf("private static final float[] INPUT_SENS_PRESETS");
        int instanceDeclaration = source.indexOf(
                "private static final ClientRtsController INSTANCE = new ClientRtsController();");
        assertTrue(sensitivityConstants >= 0 && instanceDeclaration > sensitivityConstants,
                "控制器单例不能在灵敏度预设等静态常量之前创建");
        assertTrue(source.contains("return Holder.INSTANCE;"),
                "get() 应通过延迟初始化 Holder 获取控制器");
        assertTrue(source.contains("private static final class Holder"),
                "必须保留独立 Holder，防止静态字段重排再次引发启动崩溃");
    }
}
