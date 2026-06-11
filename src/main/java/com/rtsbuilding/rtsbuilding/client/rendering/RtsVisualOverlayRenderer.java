package com.rtsbuilding.rtsbuilding.client.rendering;


import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintCaptureRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintGhostRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.PlacementAnimationRenderer;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * RTS杈圭晫娓叉煋鍣ㄤ富绫?
 * 璐熻矗鍗忚皟鍜岀鐞嗘墍鏈塕TS鐩稿叧鐨勮瑙夋覆鏌撴晥鏋滐紝鍖呮嫭锛?
 * - 鍖哄潡寮曞缃戞牸
 * - 寤洪€犺寖鍥磋竟鐣岀嚎
 * - 鍌ㄥ瓨鏂瑰潡楂樹寒
 * - 浜や簰鐩爣楂樹寒
 * - 褰㈢姸寤洪€犻瑙?
 * - 钃濆浘鎹曡幏鍜屽菇鐏甸瑙?
 * 閲囩敤妯″潡鍖栬璁★紝灏嗕笉鍚屾覆鏌撻€昏緫濮旀墭缁欎笓闂ㄧ殑瀛愭覆鏌撳櫒
 */
@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class RtsVisualOverlayRenderer extends RenderStateShard {
    // OpenGL娣卞害娴嬭瘯甯搁噺
    private static final int GL_LEQUAL = 515;

    /**
     * 鑷畾涔夋覆鏌撶被鍨嬶細鍖哄潡X灏勭嚎濉厖锛堝崐閫忔槑锛?
     * 浣跨敤POSITION_COLOR鏍煎紡锛屾敮鎸佷笁瑙掑舰甯︾粯鍒?
     */
    private static final RenderType CHUNK_XRAY_FILL = RenderType.create(
            "rtsbuilding_chunk_xray_fill",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLE_STRIP,
            2 * 1024 * 1024,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /**
     * 鑷畾涔夋覆鏌撶被鍨嬶細鍖哄潡X灏勭嚎杈规绾?
     * 浣跨敤POSITION_COLOR_NORMAL鏍煎紡锛屾敮鎸佺嚎鏉＄粯鍒?
     */
    private static final RenderType CHUNK_XRAY_LINES = RenderType.create(
            "rtsbuilding_chunk_xray_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            2 * 1024 * 1024,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(DEFAULT_LINE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /**
     * 鑷畾涔夋覆鏌撶被鍨嬶細灞忛殰杈圭晫锛堜娇鐢ㄤ笘鐣岃竟鐣岀汗鐞嗭級
     * 浣跨敤POSITION_TEX_COLOR鏍煎紡锛屾敮鎸佽创鍥炬覆鏌?
     */
    private static final ResourceLocation WORLD_BORDER_TEXTURE = new ResourceLocation("minecraft", "textures/misc/forcefield.png");

    private static final RenderType BARRIER_BOUNDARY = RenderType.create(
            "rtsbuilding_barrier_boundary",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(WORLD_BORDER_TEXTURE, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setLightmapState(NO_LIGHTMAP)
                    .setOutputState(MAIN_TARGET)
                    .createCompositeState(false));

    // 鍚庡缂撳啿鍖猴紝鐢ㄤ簬瀛樺偍娓叉煋鏁版嵁
    private static final BufferBuilder CHUNK_FILL_BUFFER = new BufferBuilder(CHUNK_XRAY_FILL.bufferSize());
    private static final BufferBuilder CHUNK_LINE_BUFFER = new BufferBuilder(CHUNK_XRAY_LINES.bufferSize());
    private static final BufferBuilder LINE_BUFFER = new BufferBuilder(RenderType.lines().bufferSize());
    private static final BufferBuilder FILL_BUFFER = new BufferBuilder(RenderType.debugFilledBox().bufferSize());

    /**
     * 绉佹湁鏋勯€犲嚱鏁帮紝闃叉瀹炰緥鍖?
     */
    private RtsVisualOverlayRenderer() {
        super("rtsbuilding_visual_overlay", () -> {}, () -> {});
    }

    /**
     * 娓叉煋绛夌骇浜嬩欢鐩戝惉鍣?
     * 鍦ㄥ崐閫忔槑鏂瑰潡娓叉煋瀹屾垚鍚庢墽琛岋紝纭繚RTS瑙嗚鏁堟灉鏄剧ず鍦ㄦ渶涓婂眰
     *
     * @param event 娓叉煋绛夌骇浜嬩欢
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // 浠呭湪AFTER_TRANSLUCENT_BLOCKS闃舵娓叉煋
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
            // 杞崲鍒扮浉鏈哄潗鏍囩郴
            poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

            // 1. 娓叉煋鍖哄潡寮曞缃戞牸锛堝鏋滃惎鐢級
            if (controller.isChunkCurtainVisible()) {
                BufferBuilder chunkFillBuffer = beginBuffer(CHUNK_XRAY_FILL, CHUNK_FILL_BUFFER);
                BufferBuilder chunkLineBuffer = beginBuffer(CHUNK_XRAY_LINES, CHUNK_LINE_BUFFER);
                ChunkGuideRenderer.renderChunkGuides(minecraft, camPos, poseStack, chunkFillBuffer, chunkLineBuffer);
                drawBuiltBufferNoDepth(CHUNK_XRAY_FILL, chunkFillBuffer);
                drawBuiltBufferNoDepth(CHUNK_XRAY_LINES, chunkLineBuffer);
            }

            // 鍑嗗閫氱敤娓叉煋缂撳啿鍖?
            RenderType lines = RenderType.lines();
            RenderType filledBox = RenderType.debugFilledBox();
            BufferBuilder lineBuffer = beginBuffer(lines, LINE_BUFFER);
            BufferBuilder fillBuffer = beginBuffer(filledBox, FILL_BUFFER);

            // 鑾峰彇閿氱偣鍜屽崐寰勪俊鎭?
            double ax = controller.getAnchorX();
            double ay = controller.getAnchorY();
            double az = controller.getAnchorZ();
            double r = controller.getMaxRadius();

            // 鏈嶅姟绔凡灏嗗榻愬埌鏂瑰潡涓績锛岀洿鎺ヤ娇鐢?
            double minX = ax - r;
            double maxX = ax + r;
            double minZ = az - r;
            double maxZ = az + r;

            // 2. 娓叉煋绾㈣壊寤洪€犺寖鍥磋竟鐣岀嚎
            BoundaryLineRenderer.renderRedBoundary(minecraft, poseStack, lineBuffer, minX, minZ, maxX, maxZ, ay);

            // 3. 娓叉煋宸查摼鎺ョ殑鍌ㄥ瓨鏂瑰潡楂樹寒
            StorageRenderer.renderLinkedStorages(minecraft, controller, poseStack, lineBuffer);

            // 4. 娓叉煋榧犳爣鎮仠鐨勪氦浜掔洰鏍囷紙鏂瑰潡鎴栧疄浣擄級
            InteractionTargetRenderer.renderHoveredInteractionTarget(minecraft, controller, poseStack, lineBuffer);

            // 5. 娓叉煋褰㈢姸寤洪€犻瑙堬紙蹇€熷缓閫犳ā寮忥級
            ShapeGhostRenderer.renderShapeGhostPreview(minecraft, poseStack, lineBuffer, fillBuffer);

            // 6. 娓叉煋钃濆浘鎹曡幏閫夋嫨妗?
            BlueprintCaptureRenderer.renderBlueprintCaptureBox(poseStack, lineBuffer, fillBuffer);

            // 7. 娓叉煋钃濆浘骞界伒棰勮
            BlueprintGhostRenderer.renderBlueprintGhostPreview(minecraft, poseStack, lineBuffer, fillBuffer);
            PlacementAnimationRenderer.render(minecraft, poseStack, lineBuffer, fillBuffer);

            // 鎻愪氦鎵€鏈夋覆鏌撶紦鍐插尯
            drawBuiltBuffer(lines, lineBuffer);
            drawBuiltBuffer(filledBox, fillBuffer);
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * 涓烘寚瀹氭覆鏌撶被鍨嬪垱寤虹紦鍐插尯鏋勫缓鍣?
     *
     * @param renderType 娓叉煋绫诲瀷
     * @param backing 鍚庡瀛楄妭缂撳啿鍖?
     * @return 閰嶇疆濂界殑BufferBuilder瀹炰緥
     */
    private static BufferBuilder beginBuffer(RenderType renderType, BufferBuilder buffer) {
        if (buffer.building()) {
            buffer.discard();
        }
        buffer.begin(renderType.mode(), renderType.format());
        return buffer;
    }

    /**
     * 缁樺埗骞堕噴鏀剧紦鍐插尯锛堟爣鍑嗘繁搴︽祴璇曪級
     *
     * @param renderType 娓叉煋绫诲瀷
     * @param buffer 寰呯粯鍒剁殑缂撳啿鍖?
     */
    private static void drawBuiltBuffer(RenderType renderType, BufferBuilder buffer) {
        if (!buffer.building()) {
            return;
        }
        if (buffer.isCurrentBatchEmpty()) {
            buffer.endOrDiscardIfEmpty();
            return;
        }
        renderType.end(buffer, VertexSorting.DISTANCE_TO_ORIGIN);
    }

    /**
     * 缁樺埗骞堕噴鏀剧紦鍐插尯锛堢鐢ㄦ繁搴︽祴璇曪紝鐢ㄤ簬閫忚鏁堟灉锛?
     * 娓叉煋鍚庢仮澶嶆繁搴︽祴璇曠姸鎬?
     *
     * @param renderType 娓叉煋绫诲瀷
     * @param buffer 寰呯粯鍒剁殑缂撳啿鍖?
     */
    private static void drawBuiltBufferNoDepth(RenderType renderType, BufferBuilder buffer) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        drawBuiltBuffer(renderType, buffer);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL_LEQUAL);
    }
}
