package com.rtsbuilding.rtsbuilding.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 保证 Forge GUI 实机探针覆盖主线容器矩阵，同时保留 Forge 的 IE 真多方块场景。
 */
class GuiCompatSetupContractTest {

    @Test
    void mainlineContainerCasesAndForgeIeSetupRemainAvailable() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsGuiCompatSetupCommand.java"));
        String probe = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/RtsGuiCompatProbe.java"));

        for (String caseId : new String[] {
                "sophisticated_chest",
                "iron_furnace",
                "mek_metallurgic_infuser",
                "mek_enrichment_chamber",
                "if_resourceful_furnace",
                "rs_grid",
                "create_schematic_table",
                "create_schematicannon"
        }) {
            assertTrue(source.contains("case \"" + caseId + "\""),
                    "缺少 GUI 兼容探针场景: " + caseId);
        }
        assertTrue(source.contains("setupIeCokeOven")
                        && source.contains("createStructure")
                        && source.contains("IEMultiblocks"),
                "Forge 已有 IE 焦炉真多方块场景不能被通用单方块探针替换");
        assertTrue(source.contains("rtsbuilding.guiCompatTargetBlock")
                        && source.contains("rtsbuilding.guiCompatTargetDistance"),
                "探针必须允许自动化覆盖目标方块和距离");
        assertTrue(probe.contains("rtsbuilding.guiCompatExpectedMenuRegex")
                        && probe.contains("rtsbuilding.guiCompatExpectedScreenRegex")
                        && probe.contains("REQUIRED_STABLE_TICKS")
                        && probe.contains("TARGET_SEARCH_RADIUS"),
                "客户端探针必须验证预期菜单/屏幕并等待稳定窗口，不能只检查是否闪退");
    }
}
