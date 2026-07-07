package com.rtsbuilding.rtsbuilding.client.render;

import com.mojang.blaze3d.vertex.*;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass.BufferAllocator;
import com.rtsbuilding.rtsbuilding.client.render.pass.*;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster.CursorRay;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 零分配渲染管线——所有 BufferBuilder 在构造时一次性分配，每帧只 clear() 不 new()。
 *
 * <p>构造时分配 5 个 BufferBuilder + ByteBufferBuilder，每帧 {@link #reset()} 复用 backing，
 * 但 1.21.1 的 BufferBuilder 无公共 reset API，每帧需新建 5 个 Builder（开销极小）。</p>
 */
public final class RenderPipeline {

    // ======================================================================
    //  Custom RenderTypes
    // ======================================================================

    private static final RenderType CHUNK_XRAY_FILL = createXrayType("rtsbuilding_chunk_xray_fill");
    private static final RenderType CHUNK_XRAY_LINES = createXrayType("rtsbuilding_chunk_xray_lines");
    private static final RenderType BRACKET_QUADS = createBracketType();
    private static final RenderType TARGET_NO_DEPTH = createNoDepthType();
    private static final RenderType BOUNDARY_BARRIER = RenderType.entityTranslucent(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "textures/misc/barrier.png"));
    private static final RenderType LINES = RenderType.lines();
    private static final RenderType FILLED_BOX = RenderType.debugFilledBox();

    // ======================================================================
    //  Pre-allocated backing + BufferBuilder pairs（只分配一次，永不释放）
    // ======================================================================

    private static final int KB = 1024;
    private final Buf linesBuf, fill, brackets, noDepth, barrier;

    /** Buffer 包装：封装 ByteBufferBuilder + BufferBuilder + RenderType */
    private static final class Buf {
        final ByteBufferBuilder backing;
        BufferBuilder builder;
        final RenderType type;
        Buf(RenderType type, int sizeKB) {
            this.backing = new ByteBufferBuilder(sizeKB * KB);
            this.builder = new BufferBuilder(this.backing, type.mode, type.format);
            this.type = type;
        }
        void reset() {
            this.backing.clear();
            // 1.21.1 BufferBuilder 无公共 reset API，必须新建
            this.builder = new BufferBuilder(this.backing, this.type.mode, this.type.format);
        }
        void draw() {
            MeshData data = this.builder.build();
            if (data != null) this.type.draw(data);
        }
    }

    // ======================================================================
    //  Render passes（注册顺序 = 渲染顺序）
    // ======================================================================

    private final List<RenderPass> passes = new ArrayList<>();
    private int frameIndex;
    /** 当前帧的时间戳（毫秒），缓存避免每帧 System.currentTimeMillis() */
    long frameMillis;
    /** 框选状态管理器 */
    public final BoxSelector boxSelector = new BoxSelector();
    /** 框选渲染 pass，供外部清除缓存 */
    public BoxSelectionPass boxSelectionPass;
    /** 已链接存储渲染 pass，供外部清理动画状态 */
    public LinkedStoragePass linkedStoragePass;
    /** 实体选择高亮 pass，供外部注入高亮状态源 */
    public EntitySelectHighlightPass entitySelectHighlightPass;

    // ======================================================================
    //  Construction
    // ======================================================================

    public RenderPipeline() {
        this.linesBuf = new Buf(LINES, 256);
        this.fill = new Buf(FILLED_BOX, 256);
        this.brackets = new Buf(BRACKET_QUADS, 128);
        this.noDepth = new Buf(TARGET_NO_DEPTH, 128);
        this.barrier = new Buf(BOUNDARY_BARRIER, 64);

        registerPass(new BoundaryPass());
        registerPass(new InteractionTargetPass());
        registerPass(new LinkedStoragePass());
        var lsp = (LinkedStoragePass) passes.get(passes.size() - 1);
        this.linkedStoragePass = lsp;
        var bsp = new BoxSelectionPass(boxSelector);
        this.boxSelectionPass = bsp;
        registerPass(bsp);
        var eshp = new EntitySelectHighlightPass();
        this.entitySelectHighlightPass = eshp;
        registerPass(eshp);
    }

    // ======================================================================
    //  Public API
    // ======================================================================

    public void registerPass(RenderPass pass) {
        this.passes.add(pass);
    }

    /** 当前帧的挂钟时间戳（毫秒），缓存避免各 Pass 重复调用 System.currentTimeMillis() */
    public long frameMillis() {
        return this.frameMillis;
    }

    /** 每渲染帧调用。由 {@link RtsClientKernel#onRenderFrame} 驱动。
     * <p>在渲染前自动调用 {@link #reset()} 重置所有缓冲区，
     * 确保 reset 与 render 原子执行，避免异常导致状态损坏。</p>
     * @param poseStack 已设置好摄像机偏移的 PoseStack（世界坐标空间） */
    public void onRenderFrame(float partialTick, PoseStack poseStack) {
        // 先重置缓冲区，再渲染（原子操作，防止 reset/render 分离导致状态损坏）
        reset();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        this.frameMillis = System.currentTimeMillis();

        // 统一计算当前帧的鼠标射线，各 Pass 从 BufferAllocator 读取，避免重复计算
        BuilderScreen screen = mc.screen instanceof BuilderScreen bs ? bs : null;
        CursorRay cursorRay = screen != null ? CursorRaycaster.computeCursorRay(mc, screen) : null;

        BufferAllocator alloc = new BufferAllocator(
                linesBuf.builder, fill.builder, brackets.builder, noDepth.builder, barrier.builder,
                cursorRay);
        for (RenderPass pass : passes) {
            if (!pass.shouldRender(mc)) continue;
            pass.render(mc, alloc, poseStack, partialTick, frameIndex);
        }

        // 所有缓冲区 build + draw
        flush();
        this.frameIndex++;
    }

    /** 每帧开始前重置所有缓冲区。 */
    public void reset() {
        linesBuf.reset();    fill.reset();
        brackets.reset();    noDepth.reset();
        barrier.reset();
    }

    /** 提交所有待绘制批次。由 RenderPass 在渲染完成后调用。 */
    public void flush() {
        linesBuf.draw();     fill.draw();
        brackets.draw();     noDepth.draw();
        barrier.draw();
    }

    // ======================================================================
    //  RenderType factory methods
    // ======================================================================

    private static RenderType createXrayType(String name) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 512, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                        .setOutputState(RenderStateShard.MAIN_TARGET)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false));
    }

    private static RenderType createBracketType() {
        return RenderType.create("rtsbuilding_bracket_quads", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 512, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setOutputState(RenderStateShard.MAIN_TARGET)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false));
    }

    private static RenderType createNoDepthType() {
        return RenderType.create("rtsbuilding_target_no_depth_quads", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 512, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                        .setOutputState(RenderStateShard.MAIN_TARGET)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false));
    }
}
