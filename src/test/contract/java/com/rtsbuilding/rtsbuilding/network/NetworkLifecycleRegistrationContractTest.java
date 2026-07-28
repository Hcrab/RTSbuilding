package com.rtsbuilding.rtsbuilding.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定 1.12.2 网络通道的 FML 生命周期接线。
 *
 * <p>统一注册器内部拥有所有 discriminator，但它不会自行进入 FML 生命周期。若入口遗漏这一次调用，
 * 模组仍能完成加载，直到玩家第一次切换 RTS 镜头或操作界面发包时才会崩溃。</p>
 */
class NetworkLifecycleRegistrationContractTest {
    private static final Path MOD_ENTRY = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/RtsbuildingMod.java");

    @Test
    void commonPreInitRegistersNetworkBeforeClientBootstrap() throws IOException {
        String source = Files.readString(MOD_ENTRY);
        int preInit = source.indexOf("void preInit(FMLPreInitializationEvent event)");
        int register = source.indexOf("RtsPayloadRegistrar.register();", preInit);
        int clientBootstrap = source.indexOf("initializeClientSide();", preInit);

        assertTrue(preInit >= 0, "Forge 1.12.2 入口必须保留公共 pre-init");
        assertTrue(register > preInit, "公共 pre-init 必须注册 RTS 网络通道");
        assertTrue(clientBootstrap > register, "网络必须先于客户端输入事件完成注册");
        assertEquals(register, source.lastIndexOf("RtsPayloadRegistrar.register();"),
                "生命周期入口只能接入统一网络注册器一次");
    }
}
