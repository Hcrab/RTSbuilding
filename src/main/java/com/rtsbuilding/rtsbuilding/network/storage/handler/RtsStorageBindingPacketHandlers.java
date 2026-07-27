package com.rtsbuilding.rtsbuilding.network.storage.handler;

import com.rtsbuilding.rtsbuilding.network.storage.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 储存链接网络边界。身份和会话只取自连接玩家；新链接与漏斗目标必须位于该玩家
 * 当前 RTS 动作范围内。解除和修改则交给服务端按玩家会话核对现有链接所有权，
 * 不因玩家走远而让合法链接无法清理。本类不负责储存扫描或物品变异。
 */
public final class RtsStorageBindingPacketHandlers {
    private static final String CAMERA_MANAGER="com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager";
    private static final String SERVICE_REGISTRY="com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry";
    private RtsStorageBindingPacketHandlers(){ }

    public static final class Link implements IMessageHandler<C2SRtsLinkStoragePayload,IMessage>{
        public IMessage onMessage(final C2SRtsLinkStoragePayload m,MessageContext c){
            if(!m.isValid())return null; schedule(c,new Action(){public void run(EntityPlayerMP p){
                if(inRange(p,m.pos()))call("linkStorage",new Class<?>[]{EntityPlayerMP.class,BlockPos.class,byte.class},p,m.pos(),m.linkMode());}});return null;}
    }
    public static final class Unlink implements IMessageHandler<C2SRtsUnlinkStoragePayload,IMessage>{
        public IMessage onMessage(final C2SRtsUnlinkStoragePayload m,MessageContext c){
            if(!m.isValid())return null; schedule(c,new Action(){public void run(EntityPlayerMP p){
                call("unlinkStorage",new Class<?>[]{EntityPlayerMP.class,BlockPos.class},p,m.pos());}});return null;}
    }
    public static final class Update implements IMessageHandler<C2SRtsUpdateLinkedStoragePayload,IMessage>{
        public IMessage onMessage(final C2SRtsUpdateLinkedStoragePayload m,MessageContext c){
            if(!m.isValid())return null; schedule(c,new Action(){public void run(EntityPlayerMP p){
                call("updateLinkedStorageSettings",new Class<?>[]{EntityPlayerMP.class,BlockPos.class,byte.class,int.class},p,m.pos(),m.linkMode(),m.priority());}});return null;}
    }
    public static final class FunnelTarget implements IMessageHandler<C2SRtsFunnelTargetPayload,IMessage>{
        public IMessage onMessage(final C2SRtsFunnelTargetPayload m,MessageContext c){
            if(!m.isValid())return null; schedule(c,new Action(){public void run(EntityPlayerMP p){
                if(inRange(p,m.target()))call("updateFunnelTarget",new Class<?>[]{EntityPlayerMP.class,BlockPos.class},p,m.target());}});return null;}
    }
    public static final class SetFunnel implements IMessageHandler<C2SRtsSetFunnelPayload,IMessage>{
        public IMessage onMessage(final C2SRtsSetFunnelPayload m,MessageContext c){schedule(c,new Action(){public void run(EntityPlayerMP p){
            call("setFunnelEnabled",new Class<?>[]{EntityPlayerMP.class,boolean.class},p,m.enabled());}});return null;}
    }
    public static final class SetAutoStore implements IMessageHandler<C2SRtsSetAutoStorePayload,IMessage>{
        public IMessage onMessage(final C2SRtsSetAutoStorePayload m,MessageContext c){schedule(c,new Action(){public void run(EntityPlayerMP p){
            call("setAutoStoreMinedDrops",new Class<?>[]{EntityPlayerMP.class,boolean.class},p,m.enabled());}});return null;}
    }

    private interface Action{void run(EntityPlayerMP player);}
    private static void schedule(MessageContext context,final Action action){
        final EntityPlayerMP player=context.getServerHandler().player;
        player.getServerWorld().addScheduledTask(new Runnable(){public void run(){if(active(player))action.run(player);}});
    }
    private static boolean active(EntityPlayerMP player){return camera("isActive",new Class<?>[]{EntityPlayerMP.class},player);}
    private static boolean inRange(EntityPlayerMP player,BlockPos pos){return camera("isWithinActionRange",new Class<?>[]{EntityPlayerMP.class,BlockPos.class},player,pos);}
    private static boolean camera(String name,Class<?>[] types,Object... args){
        try{return Boolean.TRUE.equals(Class.forName(CAMERA_MANAGER).getMethod(name,types).invoke(null,args));}
        catch(ClassNotFoundException|NoSuchMethodException|IllegalAccessException e){throw new IllegalStateException("1.12.2 camera adapter unavailable",e);}
        catch(InvocationTargetException e){throw propagate("camera authority failed",e);}
    }
    private static void call(String name,Class<?>[] types,Object... args){
        try{
            Class<?> registryType=Class.forName(SERVICE_REGISTRY);
            Object registry=registryType.getMethod("getInstance").invoke(null);
            Object binding=registryType.getMethod("binding").invoke(registry);
            Method method=binding.getClass().getMethod(name,types);
            method.invoke(binding,args);
        }catch(ClassNotFoundException|NoSuchMethodException|IllegalAccessException e){throw new IllegalStateException("1.12.2 binding adapter unavailable: "+name,e);}
        catch(InvocationTargetException e){throw propagate("binding service failed: "+name,e);}
    }
    private static RuntimeException propagate(String message,InvocationTargetException e){
        Throwable cause=e.getCause();return cause instanceof RuntimeException?(RuntimeException)cause:new IllegalStateException(message,cause);
    }
}
