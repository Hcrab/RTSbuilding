package com.rtsbuilding.rtsbuilding.compat.jei;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 HEI 普通机器配方转移的安全边界与失败回退。 */
class RtsJeiContainerOverlayTransferContractTest {
    @Test
    void standardHandlersAreWrappedAtLookupWithoutReplacingCustomHandlers() throws IOException {
        String mixin = read("src/main/java/com/rtsbuilding/rtsbuilding/mixin/RecipeRegistryOverlayTransferMixin.java");
        String wrapper = read("src/main/java/com/rtsbuilding/rtsbuilding/compat/jei/RtsOverlayAwareJeiTransferHandler.java");
        String config = read("src/main/resources/mixins.rtsbuilding_jei.json");

        assertTrue(mixin.contains("@Pseudo")
                        && mixin.contains("getRecipeTransferHandler")
                        && mixin.contains("RtsOverlayAwareJeiTransferHandler.wrap"));
        assertTrue(config.contains("\"required\": false")
                        && config.contains("RecipeRegistryOverlayTransferMixin"));
        assertTrue(wrapper.contains("BASIC_HANDLER")
                        && wrapper.contains("findTransferInfo")
                        && wrapper.contains("COMPAT_FALLBACK")
                        && wrapper.contains("delegate.transferRecipe"),
                "只应包装 HEI 标准 Basic handler，结构不符或异常必须回退原生处理器。");
    }

    @Test
    void serverRevalidatesWindowSlotsItemsAndRestoresOnRace() throws IOException {
        String payload = read("src/main/java/com/rtsbuilding/rtsbuilding/network/craft/C2SRtsJeiContainerTransferPayload.java");
        String filler = read("src/main/java/com/rtsbuilding/rtsbuilding/server/service/crafting/RtsGenericJeiContainerFiller.java");

        assertTrue(payload.contains("MAX_INPUTS = 36")
                        && payload.contains("MAX_TOTAL_ALTERNATIVES = 192")
                        && payload.contains("targetSlots.size() != alternatives.size()"));
        assertTrue(filler.contains("container.windowId != windowId")
                        && filler.contains("slot.inventory == player.inventory")
                        && filler.contains("slot.isItemValid")
                        && filler.contains("rollbackFirstSet")
                        && filler.contains("RACE_ROLLBACK"),
                "客户端计划不可直接信任；动态提取失败还必须恢复机器旧输入。");
    }

    @Test
    void sparseRecipeInputsKeepTheirOriginalMachineSlotOrdinals() throws IOException {
        String plan = read("src/main/java/com/rtsbuilding/rtsbuilding/compat/jei/RtsJeiTransferPlan.java");
        assertTrue(plan.contains("int currentOrdinal = recipeSlotOrdinal++")
                        && plan.contains("ingredient.recipeSlotOrdinal")
                        && plan.contains("空白配方格"),
                "过滤空白格时仍须保留 JEI 原始槽位，否则居中配方会被压到第一排。");
    }

    @Test
    void overlayFrameOwnsAndRestoresItsGlState() throws IOException {
        String gate = read("src/main/java/com/rtsbuilding/rtsbuilding/client/input/RtsClientInputGate.java");
        assertTrue(gate.contains("RtsGuiRenderState.beginFrame()")
                        && gate.contains("renderContainerOverlay(event)"),
                "机器 GUI 的颜色、光照和深度状态不得泄漏进 RTS overlay。");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
