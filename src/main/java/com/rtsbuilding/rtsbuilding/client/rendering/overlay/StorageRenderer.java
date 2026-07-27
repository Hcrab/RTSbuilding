package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import net.minecraft.block.BlockChest;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 1.12 链接储存高亮，保留绑定/解绑缩放和双向/仅提取颜色过渡。 */
public final class StorageRenderer {
    private static final double LINE_OFFSET = 0.002D;
    private static final long ANIM_DURATION_MS = 300L;
    private static final BufferBuilder BUFFER = new BufferBuilder(1024 * 1024);
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();
    private static final Map<BlockPos, StorageAnim> ANIMS = new HashMap<BlockPos, StorageAnim>();
    private static Set<BlockPos> previousPositions = Collections.emptySet();
    private static boolean initialized;

    private StorageRenderer() {
    }

    public static void renderLinkedStorages(Minecraft minecraft, ClientRtsController controller) {
        if (minecraft == null || controller == null || minecraft.world == null) return;
        World world = minecraft.world;
        long now = System.currentTimeMillis();
        List<LinkedStorageEntry> entries = controller.getLinkedStorageEntries();
        Set<BlockPos> current = availablePositions(entries);
        updateMembership(world, entries, current, now);
        advanceAnimations(world, current, now);
        RenderManager manager = minecraft.getRenderManager();
        Vec3d camera = new Vec3d(manager.viewerPosX,manager.viewerPosY,manager.viewerPosZ);

        BUFFER.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);
        BUFFER.setTranslation(-manager.viewerPosX,-manager.viewerPosY,-manager.viewerPosZ);
        try {
            for (LinkedStorageEntry entry : entries) appendLinked(world,entry,camera,now);
            for (Map.Entry<BlockPos,StorageAnim> entry : ANIMS.entrySet()) {
                StorageAnim animation=entry.getValue();
                if(animation.phase!=Phase.UNBINDING||animation.bounds==null)continue;
                AxisAlignedBB bounds=expandBounds(animation.bounds,1.0F-animation.progress(now));
                appendHighlight(bounds,animation.red,animation.green,animation.blue,
                        camera.distanceTo(center(bounds)));
            }
            drawOwnedBuffer();
        } catch(RuntimeException exception){discard();throw exception;}
        finally{BUFFER.setTranslation(0,0,0);}
    }

    /** 迁移期兼容入口：调用方缓冲不会被触碰。 */
    public static void renderLinkedStorages(Minecraft minecraft,ClientRtsController controller,
            BufferBuilder callerBuffer){renderLinkedStorages(minecraft,controller);}

    private static Set<BlockPos> availablePositions(List<LinkedStorageEntry> entries){
        Set<BlockPos> result=new HashSet<BlockPos>();
        for(LinkedStorageEntry entry:entries)if(entry.worldAvailable()&&entry.pos()!=null)result.add(entry.pos());
        return result;
    }

    private static void updateMembership(World world,List<LinkedStorageEntry> entries,
            Set<BlockPos> current,long now){
        if(!initialized){
            for(BlockPos pos:current)if(isLoadedStorage(world,pos)){
                StorageAnim animation=new StorageAnim(Phase.BOUND,now);
                animation.bounds=computeStorageBounds(world,pos,world.getBlockState(pos));
                ANIMS.put(pos,animation);
            }
            previousPositions=new HashSet<BlockPos>(current);initialized=true;return;
        }
        for(BlockPos pos:previousPositions)if(!current.contains(pos)){
            StorageAnim old=ANIMS.get(pos);
            if(old!=null&&old.bounds!=null){
                StorageAnim animation=new StorageAnim(Phase.UNBINDING,now);
                animation.bounds=old.bounds;animation.red=old.getRenderRed(now);
                animation.green=old.getRenderGreen(now);animation.blue=old.getRenderBlue(now);
                animation.colorsSet=true;ANIMS.put(pos,animation);
            }
        }
        for(LinkedStorageEntry entry:entries){
            BlockPos pos=entry.pos();
            if(!entry.worldAvailable()||pos==null||previousPositions.contains(pos))continue;
            StorageAnim old=ANIMS.get(pos);
            if(old==null||old.phase==Phase.UNBINDING)ANIMS.put(pos,new StorageAnim(Phase.BINDING,now));
        }
        previousPositions=new HashSet<BlockPos>(current);
    }

    private static void advanceAnimations(World world,Set<BlockPos> current,long now){
        Iterator<Map.Entry<BlockPos,StorageAnim>> iterator=ANIMS.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<BlockPos,StorageAnim> entry=iterator.next();
            BlockPos pos=entry.getKey();StorageAnim animation=entry.getValue();
            if(animation.phase==Phase.BINDING){
                if(current.contains(pos)&&isLoadedStorage(world,pos))
                    animation.bounds=computeStorageBounds(world,pos,world.getBlockState(pos));
                if(animation.progress(now)>=1.0F)animation.phase=Phase.BOUND;
            }else if(animation.phase==Phase.UNBINDING){
                if(animation.progress(now)>=1.0F)iterator.remove();
            }else if(!current.contains(pos))iterator.remove();
        }
    }

    private static void appendLinked(World world,LinkedStorageEntry entry,Vec3d camera,long now){
        BlockPos pos=entry.pos();
        if(!entry.worldAvailable()||pos==null||!isLoadedStorage(world,pos))return;
        AxisAlignedBB full=computeStorageBounds(world,pos,world.getBlockState(pos));
        boolean extract=entry.mode()==C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY;
        float tr=extract?1.00F:0.24F,tg=extract?0.30F:0.55F,tb=extract?0.82F:1.00F;
        StorageAnim animation=ANIMS.get(pos);
        if(animation!=null){
            if(animation.colorsSet&&(different(animation.red,tr)||different(animation.green,tg)||different(animation.blue,tb))){
                animation.prevRed=animation.getRenderRed(now);animation.prevGreen=animation.getRenderGreen(now);
                animation.prevBlue=animation.getRenderBlue(now);animation.colorTransitionStart=now;
            }else if(!animation.colorsSet){
                animation.prevRed=animation.red=tr;animation.prevGreen=animation.green=tg;
                animation.prevBlue=animation.blue=tb;animation.colorTransitionStart=now;animation.colorsSet=true;
            }
            animation.red=tr;animation.green=tg;animation.blue=tb;animation.bounds=full;
        }
        float r=animation==null?tr:animation.getRenderRed(now);
        float g=animation==null?tg:animation.getRenderGreen(now);
        float b=animation==null?tb:animation.getRenderBlue(now);
        AxisAlignedBB rendered=animation!=null&&animation.phase==Phase.BINDING
                ?expandBounds(full,animation.progress(now)):full;
        appendHighlight(rendered,r,g,b,camera.distanceTo(center(rendered)));
    }

    private static boolean different(float first,float second){return Math.abs(first-second)>0.01F;}

    private static void appendHighlight(AxisAlignedBB bounds,float r,float g,float b,double distance){
        appendFog(bounds,r,g,b,0.10F);
        InteractionTargetRenderer.appendCornerBrackets(BUFFER,bounds.grow(LINE_OFFSET),
                r,g,b,1.0F,distance,1.0D);
    }

    private static void appendFog(AxisAlignedBB box,float r,float g,float b,float a){
        double x1=box.minX,x2=box.maxX,y1=box.minY,y2=box.maxY,z1=box.minZ,z2=box.maxZ;
        InteractionTargetRenderer.quad(BUFFER,x1,y1,z1,x1,y1,z2,x1,y2,z2,x1,y2,z1,r,g,b,a);
        InteractionTargetRenderer.quad(BUFFER,x2,y1,z1,x2,y2,z1,x2,y2,z2,x2,y1,z2,r,g,b,a);
        InteractionTargetRenderer.quad(BUFFER,x1,y1,z1,x2,y1,z1,x2,y1,z2,x1,y1,z2,r,g,b,a);
        InteractionTargetRenderer.quad(BUFFER,x1,y2,z1,x1,y2,z2,x2,y2,z2,x2,y2,z1,r,g,b,a);
        InteractionTargetRenderer.quad(BUFFER,x1,y1,z1,x2,y1,z1,x2,y2,z1,x1,y2,z1,r,g,b,a);
        InteractionTargetRenderer.quad(BUFFER,x1,y1,z2,x1,y2,z2,x2,y2,z2,x2,y1,z2,r,g,b,a);
    }

    private static boolean isLoadedStorage(World world,BlockPos pos){
        return world.isBlockLoaded(pos,false)&&world.getBlockState(pos).getBlock()!=Blocks.AIR;
    }

    private static AxisAlignedBB computeStorageBounds(World world,BlockPos pos,IBlockState state){
        if(state.getBlock() instanceof BlockChest){
            for(EnumFacing direction:EnumFacing.HORIZONTALS){
                BlockPos adjacent=pos.offset(direction);
                if(world.isBlockLoaded(adjacent,false)&&world.getBlockState(adjacent).getBlock()==state.getBlock()){
                    return new AxisAlignedBB(Math.min(pos.getX(),adjacent.getX()),Math.min(pos.getY(),adjacent.getY()),
                            Math.min(pos.getZ(),adjacent.getZ()),Math.max(pos.getX(),adjacent.getX())+1.0D,
                            Math.max(pos.getY(),adjacent.getY())+1.0D,Math.max(pos.getZ(),adjacent.getZ())+1.0D);
                }
            }
        }
        return new AxisAlignedBB(pos);
    }

    static AxisAlignedBB expandBounds(AxisAlignedBB bounds,float progress){
        float clamped=Math.max(0.0F,Math.min(1.0F,progress));double scale=1.0D-Math.pow(1.0D-clamped,3);
        Vec3d center=center(bounds);
        return new AxisAlignedBB(center.x+(bounds.minX-center.x)*scale,center.y+(bounds.minY-center.y)*scale,
                center.z+(bounds.minZ-center.z)*scale,center.x+(bounds.maxX-center.x)*scale,
                center.y+(bounds.maxY-center.y)*scale,center.z+(bounds.maxZ-center.z)*scale);
    }
    private static Vec3d center(AxisAlignedBB b){return new Vec3d((b.minX+b.maxX)*.5D,(b.minY+b.maxY)*.5D,(b.minZ+b.maxZ)*.5D);}

    private static void drawOwnedBuffer(){
        GlSnapshot state=GlSnapshot.capture();
        try{GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
            GlStateManager.disableTexture2D();GlStateManager.disableCull();GlStateManager.enableDepth();GlStateManager.depthMask(false);
            if(BUFFER.getVertexCount()>0)UPLOADER.draw(BUFFER);else discard();
        }finally{BUFFER.setTranslation(0,0,0);state.restore();}
    }
    private static void discard(){try{BUFFER.finishDrawing();}catch(IllegalStateException ignored){}BUFFER.reset();}

    private enum Phase{BINDING,BOUND,UNBINDING}
    private static final class StorageAnim{
        Phase phase;long startTime;AxisAlignedBB bounds;float red,green,blue,prevRed,prevGreen,prevBlue;
        long colorTransitionStart=-1L;boolean colorsSet;
        StorageAnim(Phase phase,long now){this.phase=phase;this.startTime=now;}
        float progress(long now){return Math.min(1.0F,(float)(now-startTime)/(float)ANIM_DURATION_MS);}
        boolean isColorTransitioning(long now){return colorTransitionStart>=0&&now-colorTransitionStart<ANIM_DURATION_MS;}
        float getRenderRed(long now){return lerp(prevRed,red,now);}float getRenderGreen(long now){return lerp(prevGreen,green,now);}
        float getRenderBlue(long now){return lerp(prevBlue,blue,now);}
        float lerp(float from,float to,long now){if(!isColorTransitioning(now))return to;
            float t=Math.min(1.0F,(float)(now-colorTransitionStart)/(float)ANIM_DURATION_MS);
            float eased=1.0F-(float)Math.pow(1.0F-t,3);return from+(to-from)*eased;}
    }

    private static final class GlSnapshot{
        final boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),
                cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);final float lineWidth=GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        final int sr=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dr=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                sa=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),da=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        static GlSnapshot capture(){return new GlSnapshot();}
        void restore(){GlStateManager.tryBlendFuncSeparate(sr,dr,sa,da);set(GL11.GL_BLEND,blend);set(GL11.GL_TEXTURE_2D,texture);
            set(GL11.GL_CULL_FACE,cull);set(GL11.GL_DEPTH_TEST,depth);GlStateManager.depthMask(depthMask);
            GlStateManager.glLineWidth(lineWidth);GlStateManager.resetColor();}
        static void set(int cap,boolean on){if(on)GL11.glEnable(cap);else GL11.glDisable(cap);}
    }
}
