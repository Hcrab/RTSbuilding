package com.rtsbuilding.rtsbuilding.network.builder;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;import com.rtsbuilding.rtsbuilding.network.builder.handler.RtsActionControlHandlers;import cpw.mods.fml.relauncher.Side;
/** 注册不携带 ItemStack/NBT 的破坏、丢弃和流体来源控制。 */
public final class RtsActionControlPackets{private RtsActionControlPackets(){}public static void register(){
 RtsPayloadRegistrar.registerMessage(160,RtsActionControlHandlers.QuickDrop.class,C2SRtsQuickDropPayload.class,Side.SERVER);
 RtsPayloadRegistrar.registerMessage(161,RtsActionControlHandlers.Break.class,C2SRtsBreakPayload.class,Side.SERVER);
 RtsPayloadRegistrar.registerMessage(162,RtsActionControlHandlers.StoreFluid.class,C2SRtsStoreFluidPayload.class,Side.SERVER);
}}
