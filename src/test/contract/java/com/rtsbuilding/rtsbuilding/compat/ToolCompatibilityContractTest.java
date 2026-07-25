package com.rtsbuilding.rtsbuilding.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 保证第三方工具沿用真实物品栈与 Forge 工具能力，不被替换成原版工具。
 */
class ToolCompatibilityContractTest {

    @Test
    void moddedMiningAlwaysUsesTheRealSelectedStack() throws Exception {
        String validator = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsMiningValidator.java");
        String stateMachine = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsMiningStateMachine.java");
        String mekanismTest = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/gametest/MekanismToolsCompatibilityGameTests.java");
        String atmTest = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/gametest/AllTheModiumToolsCompatibilityGameTests.java");

        assertTrue(validator.contains("resolveMiningTool")
                        && validator.contains("tool.isCorrectToolForDrops(state)"),
                "采掘验证必须使用真实快捷栏或远程工具");
        assertTrue(stateMachine.contains("RtsToolLease")
                        && stateMachine.contains("destroyBlockWithTemporaryMainHand")
                        && stateMachine.contains("ItemStack tool"),
                "异步采掘执行期必须持有并使用真实工具租约");
        assertTrue(mekanismTest.contains("mekanismtools")
                        && mekanismTest.contains("osmium_paxel")
                        && atmTest.contains("allthemodium_pickaxe"),
                "Forge 1.20.1 必须保留 Mekanism Tools 与 ATM 的真实黑箱回归入口");
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath));
    }
}
