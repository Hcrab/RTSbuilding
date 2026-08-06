package com.rtsbuilding.rtsbuilding.compat.sable;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定 Fabric 的 Sable Companion 接入边界。
 *
 * <p>测试的是玩家正常在 plot 内远程施工、选择和访问容器时所需的坐标映射；它不把
 * Companion 当成 Sable 本体依赖，也不向原有远程/批量工作流添加距离或频率限制。</p>
 */
class RtsSableFabricCompatibilityContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/rtsbuilding/rtsbuilding");
    private static final Path CLIENT = Path.of("src/client/java/com/rtsbuilding/rtsbuilding");

    @Test
    void officialFabricCompanionIsEmbeddedWithoutRequiringSable() throws IOException {
        String build = Files.readString(Path.of("build.gradle"));
        String properties = Files.readString(Path.of("gradle.properties"));

        assertTrue(properties.contains("sable_companion_version=1.6.0"));
        assertTrue(build.contains("https://maven.ryanhcode.dev/releases"));
        assertTrue(build.contains("sable-companion-fabric-${minecraft_version}:${sable_companion_version}"));
        assertTrue(build.contains("modImplementation \"dev.ryanhcode.sable-companion:"));
        assertTrue(build.contains("include \"dev.ryanhcode.sable-companion:"));
    }

    @Test
    void spatialAdaptersKeepLogicalTargetsAndUsePreciseBlockRenderFrames() throws IOException {
        String serverSpatial = read(MAIN.resolve("compat/sable/RtsSableSpatialCompat.java"));
        String clientSpatial = read(CLIENT.resolve("client/compat/sable/RtsSableClientSpatialCompat.java"));
        String modelRenderer = read(CLIENT.resolve("client/rendering/util/GhostBlockModelRenderer.java"));

        assertTrue(serverSpatial.contains("SableCompanion.INSTANCE.getContaining"));
        assertTrue(serverSpatial.contains("return subLevel == null ? logicalPos"));
        assertTrue(serverSpatial.contains("physicalBlockPos"));
        assertTrue(serverSpatial.contains("physicalDirection"));
        assertTrue(clientSpatial.contains("Vec3.atLowerCornerOf(logicalPos)"));
        assertTrue(clientSpatial.contains("poseStack.translate(renderedOrigin.x, renderedOrigin.y, renderedOrigin.z)"));
        assertTrue(clientSpatial.contains("new Quaternionf().set(renderPose.orientation())"));
        assertTrue(modelRenderer.contains("renderAtLocal"));
    }

    @Test
    void normalRemoteAndBatchWorkflowsMapFramesAtEveryExistingBoundary() throws IOException {
        assertContains(MAIN.resolve("server/camera/RtsCameraManager.java"), "projectLogicalToGlobal");
        assertContains(MAIN.resolve("server/protection/RtsClaimProtectionService.java"), "physicalBlockPos");
        assertContains(MAIN.resolve("server/storage/resolver/RtsLinkedStorageResolver.java"),
                "return !sameDimension || RtsCameraManager.isWithinActionRange(player, pos)");
        assertContains(MAIN.resolve("server/util/TemporaryContextSwitcher.java"), "physicalFallbackPos");

        assertContains(CLIENT.resolve("client/screen/handler/ScreenCursorPicker.java"), "toRenderLocalRay");
        assertContains(CLIENT.resolve("client/screen/shape/ShapeSelectionSession.java"), "frameId");
        assertContains(CLIENT.resolve("client/service/MiningOperationService.java"), "areaMineFrameId");
        assertContains(CLIENT.resolve("compat/jade/RtsJadeRayTraceCallback.java"), "renderDistanceSquared");
        assertContains(CLIENT.resolve("client/rendering/builder/BuildGhostFillRenderer.java"), "applyBlockRenderFrame");
        assertContains(CLIENT.resolve("client/rendering/overlay/InteractionTargetRenderer.java"), "applyBlockRenderFrame");
    }

    private static void assertContains(Path source, String expected) throws IOException {
        assertTrue(read(source).contains(expected), () -> source + " 应包含 " + expected);
    }

    private static String read(Path source) throws IOException {
        return Files.readString(source);
    }
}
