package com.rtsbuilding.rtsbuilding.client.rendering.overlay;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsOwnedBufferUploader;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsGlStateRestorer;

import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsClientPathfinding;
import net.minecraft.client.Minecraft;
import com.rtsbuilding.rtsbuilding.platform.render.BufferBuilder;
import com.rtsbuilding.rtsbuilding.platform.render.GlStateManager;
import com.rtsbuilding.rtsbuilding.platform.render.WorldVertexBufferUploader;
import net.minecraft.client.renderer.entity.RenderManager;
import com.rtsbuilding.rtsbuilding.platform.render.DefaultVertexFormats;
import com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Ctrl+右键移动目标的蓝色、加粗、抵达后淡出高亮。 */
public final class PlayerMoveTargetRenderer {
    private static final BufferBuilder DEPTH_BUFFER = new BufferBuilder(64 * 1024);
    private static final BufferBuilder NO_DEPTH_BUFFER = new BufferBuilder(64 * 1024);
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();

    private PlayerMoveTargetRenderer() {
    }

    public static void render(Minecraft minecraft) {
        if (minecraft == null || minecraft.theWorld == null) return;
        RtsClientPathfinding.MoveTargetHighlight highlight = RtsClientPathfinding.getMoveTargetHighlight();
        if (highlight == null || highlight.alpha() <= 0.0F) return;
        BlockPos target = highlight.target();
        AxisAlignedBB bounds = new AxisAlignedBB(target).grow(0.045D);
        RenderManager manager = net.minecraft.client.renderer.entity.RenderManager.instance;
        Vec3d camera = new Vec3d(manager.viewerPosX, manager.viewerPosY, manager.viewerPosZ);
        double distance = camera.distanceTo(new Vec3d(target).add(0.5D, 0.5D, 0.5D));

        beginBuffers(-manager.viewerPosX,-manager.viewerPosY,-manager.viewerPosZ);
        try {
            InteractionTargetRenderer.appendCornerBrackets(DEPTH_BUFFER,bounds,
                    0.16F,0.58F,1.0F,0.95F*highlight.alpha(),distance,1.85D);
            InteractionTargetRenderer.appendCornerBrackets(NO_DEPTH_BUFFER,bounds,
                    0.16F,0.58F,1.0F,0.28F*highlight.alpha(),distance,1.85D);
            drawOwnedBuffers();
        } catch (RuntimeException exception) {
            discardOwnedBuffers(); throw exception;
        } finally { resetTranslations(); }
    }

    /** 迁移期兼容入口：调用方缓冲不会被触碰。 */
    public static void render(Minecraft minecraft, BufferBuilder callerDepth, BufferBuilder callerNoDepth) {
        render(minecraft);
    }

    private static void drawOwnedBuffers() {
        GlSnapshot state=GlSnapshot.capture();
        try {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
            GlStateManager.disableTexture2D();GlStateManager.disableCull();GlStateManager.depthMask(false);
            GlStateManager.enableDepth();uploadOrReset(DEPTH_BUFFER);
            GlStateManager.disableDepth();uploadOrReset(NO_DEPTH_BUFFER);
        } finally {resetTranslations();state.restore();}
    }
    private static void beginBuffers(double x,double y,double z){
        DEPTH_BUFFER.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);DEPTH_BUFFER.setTranslation(x,y,z);
        try{NO_DEPTH_BUFFER.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);NO_DEPTH_BUFFER.setTranslation(x,y,z);}
        catch(RuntimeException exception){discard(DEPTH_BUFFER);throw exception;}
    }
    private static void uploadOrReset(BufferBuilder b){if(b.getVertexCount()>0)RtsOwnedBufferUploader.draw(b);else discard(b);}
    private static void discardOwnedBuffers(){discard(DEPTH_BUFFER);discard(NO_DEPTH_BUFFER);resetTranslations();}
    private static void discard(BufferBuilder b){try{b.finishDrawing();}catch(IllegalStateException ignored){}b.reset();}
    private static void resetTranslations(){DEPTH_BUFFER.setTranslation(0,0,0);NO_DEPTH_BUFFER.setTranslation(0,0,0);}

    private static final class GlSnapshot {
        final boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),
                cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final float lineWidth=GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        final int sr=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dr=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                sa=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),da=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        static GlSnapshot capture(){return new GlSnapshot();}
        void restore(){GlStateManager.tryBlendFuncSeparate(sr,dr,sa,da);set(GL11.GL_BLEND,blend);set(GL11.GL_TEXTURE_2D,texture);
            set(GL11.GL_CULL_FACE,cull);set(GL11.GL_DEPTH_TEST,depth);GlStateManager.depthMask(depthMask);
            GlStateManager.glLineWidth(lineWidth);GlStateManager.resetColor();}
        static void set(int cap,boolean on){RtsGlStateRestorer.restoreCapability(cap,on);}
    }
}
