package com.rtsbuilding.rtsbuilding.client.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintCaptureRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintGhostRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.ShapeGhostRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.BoundaryLineRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.ChunkGuideRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.InteractionTargetRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.StorageRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * RTS边界渲染器主类
 * 负责协调和管理所有RTS相关的视觉渲染效果，包括：
 * - 区块引导网格
 * - 建造范围边界线
 * - 储存方块高亮
 * - 交互目标高亮
 * - 形状建造预览
 * - 蓝图捕获和幽灵预览
 * 采用模块化设计，将不同渲染逻辑委托给专门的子渲染器
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class RtsVisualOverlayRenderer {
    // OpenGL深度测试常量
    private static final int GL_LEQUAL = 515;

    /**
     * 自定义渲染类型：区块X射线填充（半透明）
     * 使用POSITION_COLOR格式，支持三角形带绘制
     */
    private static final RenderType CHUNK_XRAY_FILL = RenderType.create(
            "rtsbuilding_chunk_xray_fill",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLE_STRIP,
            2 * 1024 * 1024,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /**
     * 自定义渲染类型：区块X射线边框线
     * 使用POSITION_COLOR_NORMAL格式，支持线条绘制
     */
    private static final RenderType CHUNK_XRAY_LINES = RenderType.create(
            "rtsbuilding_chunk_xray_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            2 * 1024 * 1024,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(RenderStateShard.DEFAULT_LINE)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /**
     * 自定义渲染类型：屏障边界（使用世界边界纹理）
     * 使用POSITION_TEX_COLOR格式，支持贴图渲染
     */
    private static final ResourceLocation WORLD_BORDER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/forcefield.png");
    
    private static final RenderType BARRIER_BOUNDARY = RenderType.create(
            "rtsbuilding_barrier_boundary",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(WORLD_BORDER_TEXTURE, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .createCompositeState(false));

    // 后备缓冲区，用于存储渲染数据
    private static final ByteBufferBuilder CHUNK_FILL_BACKING = new ByteBufferBuilder(CHUNK_XRAY_FILL.bufferSize());
    private static final ByteBufferBuilder CHUNK_LINE_BACKING = new ByteBufferBuilder(CHUNK_XRAY_LINES.bufferSize());
    private static final ByteBufferBuilder LINE_BACKING = new ByteBufferBuilder(RenderType.lines().bufferSize());
    private static final ByteBufferBuilder FILL_BACKING = new ByteBufferBuilder(RenderType.debugFilledBox().bufferSize());

    /**
     * 私有构造函数，防止实例化
     */
    private RtsVisualOverlayRenderer() {
    }

    /**
     * 渲染等级事件监听器
     * 在半透明方块渲染完成后执行，确保RTS视觉效果显示在最上层
     * 
     * @param event 渲染等级事件
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // 仅在AFTER_TRANSLUCENT_BLOCKS阶段渲染
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        ClientRtsController controller = ClientRtsController.get();
        if (!controller.hasBounds()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        try {
            // 转换到相机坐标系
            poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

            // 1. 渲染区块引导网格（如果启用）
            if (controller.isChunkCurtainVisible()) {
                BufferBuilder chunkFillBuffer = bufferFor(CHUNK_XRAY_FILL, CHUNK_FILL_BACKING);
                BufferBuilder chunkLineBuffer = bufferFor(CHUNK_XRAY_LINES, CHUNK_LINE_BACKING);
                ChunkGuideRenderer.renderChunkGuides(minecraft, camPos, poseStack, chunkFillBuffer, chunkLineBuffer);
                drawBuiltBufferNoDepth(CHUNK_XRAY_FILL, chunkFillBuffer);
                drawBuiltBufferNoDepth(CHUNK_XRAY_LINES, chunkLineBuffer);
            }

            // 准备通用渲染缓冲区
            RenderType lines = RenderType.lines();
            RenderType filledBox = RenderType.debugFilledBox();
            BufferBuilder lineBuffer = bufferFor(lines, LINE_BACKING);
            BufferBuilder fillBuffer = bufferFor(filledBox, FILL_BACKING);

            // 获取锚点和半径信息
            double ax = controller.getAnchorX();
            double ay = controller.getAnchorY();
            double az = controller.getAnchorZ();
            double r = controller.getMaxRadius();

            // 服务端已将对齐到方块中心，直接使用
            double minX = ax - r;
            double maxX = ax + r;
            double minZ = az - r;
            double maxZ = az + r;

            // 2. 渲染红色建造范围边界线
            BoundaryLineRenderer.renderRedBoundary(minecraft, poseStack, lineBuffer, minX, minZ, maxX, maxZ, ay);

            // 3. 渲染已链接的储存方块高亮
            StorageRenderer.renderLinkedStorages(minecraft, controller, poseStack, lineBuffer);

            // 4. 渲染鼠标悬停的交互目标（方块或实体）
            InteractionTargetRenderer.renderHoveredInteractionTarget(minecraft, controller, poseStack, lineBuffer);

            // 5. 渲染形状建造预览（快速建造模式）
            ShapeGhostRenderer.renderShapeGhostPreview(minecraft, poseStack, lineBuffer, fillBuffer);

            // 6. 渲染蓝图捕获选择框
            BlueprintCaptureRenderer.renderBlueprintCaptureBox(poseStack, lineBuffer, fillBuffer);

            // 7. 渲染蓝图幽灵预览
            BlueprintGhostRenderer.renderBlueprintGhostPreview(minecraft, poseStack, lineBuffer, fillBuffer);

            // 提交所有渲染缓冲区
            drawBuiltBuffer(lines, lineBuffer);
            drawBuiltBuffer(filledBox, fillBuffer);
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * 为指定渲染类型创建缓冲区构建器
     * 
     * @param renderType 渲染类型
     * @param backing 后备字节缓冲区
     * @return 配置好的BufferBuilder实例
     */
    private static BufferBuilder bufferFor(RenderType renderType, ByteBufferBuilder backing) {
        return new BufferBuilder(backing, renderType.mode, renderType.format);
    }

    /**
     * 绘制并释放缓冲区（标准深度测试）
     * 
     * @param renderType 渲染类型
     * @param buffer 待绘制的缓冲区
     */
    private static void drawBuiltBuffer(RenderType renderType, BufferBuilder buffer) {
        MeshData meshData = buffer.build();
        if (meshData != null) {
            renderType.draw(meshData);
        }
    }

    /**
     * 绘制并释放缓冲区（禁用深度测试，用于透视效果）
     * 渲染后恢复深度测试状态
     * 
     * @param renderType 渲染类型
     * @param buffer 待绘制的缓冲区
     */
    private static void drawBuiltBufferNoDepth(RenderType renderType, BufferBuilder buffer) {
        MeshData meshData = buffer.build();
        if (meshData != null) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            renderType.draw(meshData);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL_LEQUAL);
        }
    }
}
