package com.rtsbuilding.rtsbuilding.client.screen.handler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapePlacementContextContractTest {
    @Test
    void shapePlacementFilteringUsesResolverOwnedBlockPlaceContext() throws IOException {
        String controllerSource = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/handler/ScreenShapeController.java"));
        String resolverSource = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapePlacementTargetResolver.java"));
        String sessionResolverSource = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeSessionInputResolver.java"));
        String sessionSource = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeSelectionSession.java"));
        String previewSource = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeGhostPreviewProvider.java"));
        String operationSource = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeWorldOperationPlanner.java"));

        assertTrue(controllerSource.contains("ShapePlacementTargetResolver.resolveClickedTarget"),
                "单方块点击目标应委托给统一放置目标解析器。");
        assertTrue(previewSource.contains("ShapePlacementTargetResolver.resolveSingleGhostTarget"),
                "单方块幽灵预览应复用统一放置目标解析器。");
        assertTrue(operationSource.contains("ShapePlacementTargetResolver.resolveTargets"),
                "批量形状 READY_CONFIRM 过滤应复用统一放置目标解析器。");
        assertTrue(operationSource.contains("ShapePlacementTargetResolver.minecraftWorld"),
                "世界 adapter 应显式把当前 Minecraft 世界/物品栈作为只读探针交给解析器。");
        assertTrue(operationSource.contains("placementStack()"),
                "创造模式物品栏/工具栏选中的方块原型要参与形状放置上下文判断。");
        assertFalse(controllerSource.contains("private BlockPlaceContext createShapePlacementContext"),
                "BlockPlaceContext 适配不应继续留在控制器里。");
        assertFalse(controllerSource.contains("private BlockPos resolvePlacementTargetPos"),
                "控制器不应继续维护自己的点击目标解析分支。");
        assertFalse(controllerSource.contains("private BlockPos resolveUniformShapePlacementTargetPos"),
                "统一平面偏移规则应由新 owner 持有。");
        assertTrue(sessionSource.contains("ShapeSessionInputResolver.resolve("),
                "会话到形状输入的转换应委托给纯解析器。");
        assertTrue(sessionSource.contains("ShapeSessionInputResolver.resolvePlanePoint"),
                "交互阶段的平面目标也应复用同一解析器。");
        assertFalse(controllerSource.contains("private Vec3 intersectCursorRayWithShapePlane"),
                "相机射线和平面求交不应继续内联在控制器。");
        assertFalse(controllerSource.contains("private BlockPos applyShapeFootprintNudges"),
                "脚印微调不应继续由控制器保存第二套几何实现。");

        assertTrue(resolverSource.contains("interface PlacementWorld"),
                "解析器应通过只读世界边界支持纯内存测试。");
        assertTrue(resolverSource.contains("BlockPlaceContext"),
                "台阶、雪层等上下文相关方块应通过 BlockPlaceContext 判断。");
        assertTrue(resolverSource.contains("state.canBeReplaced(context)"),
                "上下文相关替换判断必须保留。");
        assertFalse(resolverSource.contains("ClientRtsController"),
                "放置目标解析器不应拥有 RTS 控制器或网络发送职责。");
        assertFalse(resolverSource.contains("BuilderScreen"),
                "放置目标解析器不应拥有屏幕状态机。");
        assertFalse(resolverSource.contains("Config"),
                "放置目标解析器不应读取配置开关。");
        assertFalse(resolverSource.contains("sendToServer"),
                "放置目标解析器不应发送网络包。");
        assertFalse(resolverSource.contains("setBlock("),
                "放置目标解析器不应修改世界。");
        assertFalse(resolverSource.contains("destroyBlock("),
                "放置目标解析器不应修改世界。");

        assertFalse(sessionResolverSource.contains("import net.minecraft.client.Minecraft"),
                "会话解析器只接收射线数据，不应读取 Minecraft 客户端。");
        assertFalse(sessionResolverSource.contains("BuilderScreen"),
                "会话解析器不应拥有屏幕生命周期。");
        assertFalse(sessionResolverSource.contains("ClientRtsController"),
                "会话解析器不应拥有 RTS 状态或网络副作用。");
        assertFalse(sessionResolverSource.contains("Config"),
                "会话解析器不应自行读取配置。");
    }

    @Test
    void creativePlacementKeepsInfiniteClientAndServerMaterialPaths() throws IOException {
        String clientSource = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/service/BuildPlacementService.java"));
        String quickBuildSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementQuickBuild.java"));

        assertTrue(clientSource.contains("if (isLocalPlayerCreative) return Long.MAX_VALUE;"),
                "客户端快速放置数量判断中，创造模式应视为无限材料。");
        assertTrue(quickBuildSource.contains("boolean creativeSource = player.isCreative();"),
                "服务端批量快速建造也必须识别创造模式来源。");
        assertTrue(quickBuildSource.contains("? RtsPlacementExtractor.creativeStack"),
                "创造模式批量放置应构造创造模式物品栏，而不是从远程存储扣材料。");
    }
}
