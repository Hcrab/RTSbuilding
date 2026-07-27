package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import java.util.List;

/** 1.12 刷怪蛋和末影水晶幽灵预览。实体由临时实例渲染，绝不加入客户端世界。 */
public final class EntityGhostRenderer {
    private EntityGhostRenderer(){}
    public static void renderEntities(Minecraft mc,List<BlockPos> blocks,BufferBuilder caller,ItemStack stack){
        if(mc==null||mc.world==null||stack==null||stack.isEmpty()||!(stack.getItem() instanceof ItemMonsterPlacer))return;
        ResourceLocation id=ItemMonsterPlacer.getNamedIdFrom(stack); if(id==null)return;
        Entity entity=EntityList.createEntityByIDFromName(id,mc.world); if(entity!=null)renderEntityGhost(mc,blocks,entity);
    }
    public static void renderEndCrystals(Minecraft mc,List<BlockPos> blocks,BufferBuilder caller){
        if(mc!=null&&mc.world!=null)renderEntityGhost(mc,blocks,new EntityEnderCrystal(mc.world));
    }
    private static void renderEntityGhost(Minecraft mc,List<BlockPos> blocks,Entity entity){
        if(blocks==null||blocks.isEmpty()||entity==null)return;
        entity.setNoGravity(true); float partial=mc.getRenderPartialTicks();
        UltimineGhostRenderer.GlSnapshot gl=UltimineGhostRenderer.GlSnapshot.capture();GlStateManager.pushMatrix();
        try{
            GlStateManager.enableBlend();GlStateManager.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GlStateManager.color(1,1,1,.75F);
            for(BlockPos p:blocks){double dx=p.getX()+.5-mc.getRenderManager().viewerPosX,dz=p.getZ()+.5-mc.getRenderManager().viewerPosZ;float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
                entity.setPositionAndRotation(p.getX()+.5,p.getY(),p.getZ()+.5,yaw,0);entity.prevRotationYaw=yaw;
                if(entity instanceof EntityLivingBase){EntityLivingBase l=(EntityLivingBase)entity;l.rotationYawHead=yaw;l.prevRotationYawHead=yaw;l.renderYawOffset=yaw;l.prevRenderYawOffset=yaw;}
                mc.getRenderManager().renderEntityStatic(entity,partial,false);
            }
        }finally{GlStateManager.color(1,1,1,1);GlStateManager.popMatrix();gl.restore();}
    }
}
