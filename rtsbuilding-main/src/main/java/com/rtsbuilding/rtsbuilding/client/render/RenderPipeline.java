package com.rtsbuilding.rtsbuilding.client.render;

import com.mojang.blaze3d.vertex.*;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass.BufferAllocator;
import com.rtsbuilding.rtsbuilding.client.render.pass.*;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster.CursorRay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class RenderPipeline {

    
    
    

    private static final RenderType CHUNK_XRAY_FILL = createXrayType("rtsbuilding_chunk_xray_fill");
    private static final RenderType CHUNK_XRAY_LINES = createXrayType("rtsbuilding_chunk_xray_lines");
    private static final RenderType BRACKET_QUADS = createBracketType();
    private static final RenderType TARGET_NO_DEPTH = createNoDepthType();
    private static final RenderType BOUNDARY_BARRIER = RenderType.entityTranslucent(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "textures/misc/barrier.png"));
    private static final RenderType LINES = RenderType.lines();
    private static final RenderType FILLED_BOX = RenderType.debugFilledBox();

    
    
    

    private static final int KB = 1024;
    private final Buf linesBuf, fill, brackets, noDepth, barrier;

    
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
            
            this.builder = new BufferBuilder(this.backing, this.type.mode, this.type.format);
        }
        void draw() {
            MeshData data = this.builder.build();
            if (data != null) this.type.draw(data);
        }
    }

    
    
    

    private final List<RenderPass> passes = new ArrayList<>();
    private int frameIndex;
    
    long frameMillis;
    
    public final BoxSelector boxSelector = new BoxSelector();
    
    public BoxSelectionPass boxSelectionPass;
    
    public LinkedStoragePass linkedStoragePass;
    
    public EntitySelectHighlightPass entitySelectHighlightPass;
    
    public UltiminePreviewPass ultiminePreviewPass;

    
    
    

    public RenderPipeline() {
        this.linesBuf = new Buf(LINES, 256);
        this.fill = new Buf(FILLED_BOX, 256);
        this.brackets = new Buf(BRACKET_QUADS, 128);
        this.noDepth = new Buf(TARGET_NO_DEPTH, 128);
        this.barrier = new Buf(BOUNDARY_BARRIER, 64);

        registerPass(new BoundaryPass());
        registerPass(new InteractionTargetPass());
        registerPass(new FunnelRangePass());
        registerPass(new LinkedStoragePass());
        var lsp = (LinkedStoragePass) passes.get(passes.size() - 1);
        this.linkedStoragePass = lsp;
        
        registerPass(new LocateMarkerPass());
        var bsp = new BoxSelectionPass(boxSelector);
        this.boxSelectionPass = bsp;
        registerPass(bsp);
        var eshp = new EntitySelectHighlightPass();
        this.entitySelectHighlightPass = eshp;
        registerPass(eshp);
        
        this.ultiminePreviewPass = new UltiminePreviewPass();
        registerPass(this.ultiminePreviewPass);
    }

    
    
    

    public void registerPass(RenderPass pass) {
        this.passes.add(pass);
    }

    
    public long frameMillis() {
        return this.frameMillis;
    }

    
    public void onRenderFrame(float partialTick, PoseStack poseStack) {
        
        reset();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        this.frameMillis = System.currentTimeMillis();

        
        BuilderScreen screen = mc.screen instanceof BuilderScreen bs ? bs : null;
        CursorRay cursorRay = screen != null ? CursorRaycaster.computeCursorRay(mc, screen) : null;

        BufferAllocator alloc = new BufferAllocator(
                linesBuf.builder, fill.builder, brackets.builder, noDepth.builder, barrier.builder,
                cursorRay);
        for (RenderPass pass : passes) {
            if (!pass.shouldRender(mc)) continue;
            pass.render(mc, alloc, poseStack, partialTick, frameIndex);
        }

        
        flush();
        this.frameIndex++;
    }

    
    public void reset() {
        linesBuf.reset();    fill.reset();
        brackets.reset();    noDepth.reset();
        barrier.reset();
    }

    
    public void flush() {
        linesBuf.draw();     fill.draw();
        brackets.draw();     noDepth.draw();
        barrier.draw();
    }

    
    
    

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
