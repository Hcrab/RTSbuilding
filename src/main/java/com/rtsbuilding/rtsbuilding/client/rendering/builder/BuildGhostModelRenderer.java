package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;
import java.nio.ByteBuffer;
import java.util.List;

/** 以真实客户端世界和位置烘焙 1.12 半透明方块模型，并扩展门、双层植物和床。 */
public final class BuildGhostModelRenderer {
    public static final float GHOST_ALPHA=.8F;
    private static final BufferBuilder BUFFER=new BufferBuilder(2*1024*1024);
    private static final WorldVertexBufferUploader UPLOADER=new WorldVertexBufferUploader();
    private BuildGhostModelRenderer(){}

    public static void renderModels(Minecraft mc,List<BlockPos> blocks,BufferBuilder caller,IBlockState state){
        if(mc==null||mc.world==null||blocks==null||blocks.isEmpty()||state==null)return;
        RenderManager rm=mc.getRenderManager();
        for(BlockPos p:blocks){ renderAt(mc,state,p,rm); expand(mc,state,p,rm); }
    }
    private static void expand(Minecraft mc,IBlockState s,BlockPos p,RenderManager rm){
        if(s.getBlock() instanceof BlockDoor){
            BlockDoor.EnumDoorHalf h=s.getValue(BlockDoor.HALF);
            renderAt(mc,s.withProperty(BlockDoor.HALF,h==BlockDoor.EnumDoorHalf.LOWER?BlockDoor.EnumDoorHalf.UPPER:BlockDoor.EnumDoorHalf.LOWER),h==BlockDoor.EnumDoorHalf.LOWER?p.up():p.down(),rm);
        } else if(s.getBlock() instanceof BlockDoublePlant){
            BlockDoublePlant.EnumBlockHalf h=s.getValue(BlockDoublePlant.HALF);
            renderAt(mc,s.withProperty(BlockDoublePlant.HALF,h==BlockDoublePlant.EnumBlockHalf.LOWER?BlockDoublePlant.EnumBlockHalf.UPPER:BlockDoublePlant.EnumBlockHalf.LOWER),h==BlockDoublePlant.EnumBlockHalf.LOWER?p.up():p.down(),rm);
        } else if(s.getBlock() instanceof BlockBed){
            BlockBed.EnumPartType part=s.getValue(BlockBed.PART); EnumFacing f=s.getValue(BlockBed.FACING);
            renderAt(mc,s.withProperty(BlockBed.PART,part==BlockBed.EnumPartType.FOOT?BlockBed.EnumPartType.HEAD:BlockBed.EnumPartType.FOOT),part==BlockBed.EnumPartType.FOOT?p.offset(f):p.offset(f.getOpposite()),rm);
        }
    }
    private static boolean renderAt(Minecraft mc,IBlockState s,BlockPos p,RenderManager rm){
        BlockRendererDispatcher d=mc.getBlockRendererDispatcher(); BlockModelRenderer r=d.getBlockModelRenderer(); IBakedModel model=d.getModelForState(s);
        boolean any=false,closed=false; BUFFER.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK); BUFFER.setTranslation(-rm.viewerPosX,-rm.viewerPosY,-rm.viewerPosZ);
        try{
            for(BlockRenderLayer layer:BlockRenderLayer.values()) if(s.getBlock().canRenderInLayer(s,layer)){
                ForgeHooksClient.setRenderLayer(layer); any|=r.renderModel(mc.world,model,s,p,BUFFER,false,MathHelper.getPositionRandom(p));
            }
            if(!any||BUFFER.getVertexCount()==0){BUFFER.finishDrawing();BUFFER.reset();closed=true;return false;}
            ByteBuffer bytes=BUFFER.getByteBuffer();int stride=BUFFER.getVertexFormat().getSize(),off=BUFFER.getVertexFormat().getColorOffset(),a=Math.round(GHOST_ALPHA*255F);
            for(int i=0;i<BUFFER.getVertexCount();i++)bytes.put(i*stride+off+3,(byte)a);
            UltimineGhostRenderer.GlSnapshot gl=UltimineGhostRenderer.GlSnapshot.capture();mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE); GlStateManager.enableBlend(); GlStateManager.depthMask(false); GlStateManager.disableCull();
            try{UPLOADER.draw(BUFFER);closed=true;}finally{gl.restore();GlStateManager.resetColor();}
            return true;
        }finally{ForgeHooksClient.setRenderLayer(null);BUFFER.setTranslation(0,0,0);if(!closed){try{BUFFER.finishDrawing();}catch(IllegalStateException ignored){}BUFFER.reset();}}
    }
}
