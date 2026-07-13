package com.rtsbuilding.rtsbuilding.client.screen.handler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapePlacementContextContractTest {
    @Test
    void shapePlacementFilteringUsesBlockPlaceContextForReplaceableBlocks() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/ScreenShapeController.java"));

        assertTrue(source.contains("private BlockPlaceContext createShapePlacementContext"),
                "形状放置过滤需要构造 BlockPlaceContext，不能只用无上下文 canBeReplaced。");
        assertTrue(source.contains("state.canBeReplaced(context)"),
                "台阶、雪层等上下文相关方块应通过 canBeReplaced(context) 判断。");
        assertTrue(source.contains("resolveShapePlacementStackForContext()"),
                "创造模式物品栏/工具栏选中的方块原型要参与形状放置上下文判断。");
    }

    @Test
    void creativePlacementKeepsInfiniteClientAndServerMaterialPaths() throws IOException {
        String clientSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/controller/ClientRtsController.java"));
        String quickBuildSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementQuickBuild.java"));

        assertTrue(clientSource.contains("if (isLocalPlayerCreative())")
                        && clientSource.contains("return Long.MAX_VALUE;"),
                "客户端快速放置数量判断中，创造模式应视为无限材料。");
        assertTrue(quickBuildSource.contains("boolean creativeSource = player.isCreative();"),
                "服务端批量快速建造也必须识别创造模式来源。");
        assertTrue(quickBuildSource.contains("? RtsPlacementExtractor.creativeStack"),
                "创造模式批量放置应构造创造模式物品栈，而不是从远程存储扣材料。");
    }
}
