package com.rtsbuilding.rtsbuilding.client.rendering.builder;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import java.util.List;
/** 1.12 建造预览编排入口。 */
public final class BuildGhostRenderer{
 static final float BUILD_GHOST_ALPHA=.8F;private BuildGhostRenderer(){}
 static void render(Minecraft mc,ShapeDataRecords.GhostPreview preview,BufferBuilder lines,BufferBuilder fills,boolean model,boolean wire){
  if(preview==null||preview.blocks()==null||preview.blocks().isEmpty())return;List<BlockPos>b=preview.blocks();IBlockState s=BuildGhostBlockStateResolver.resolve(mc,b.get(0));
  if(model){if(s!=null&&s.getRenderType()==EnumBlockRenderType.MODEL)BuildGhostModelRenderer.renderModels(mc,b,fills,s);else{ItemStack egg=BuildGhostBlockStateResolver.resolveSpawnEggStack(mc);if(!egg.isEmpty())EntityGhostRenderer.renderEntities(mc,b,fills,egg);else if(!BuildGhostBlockStateResolver.resolveEndCrystalStack(mc).isEmpty())EntityGhostRenderer.renderEndCrystals(mc,b,fills);else BuildGhostFillRenderer.renderFill(b,fills,preview.readyConfirm());}}
  if(wire)BuildGhostWireframeRenderer.renderWireframes(b,lines,preview.readyConfirm());
 }
}
