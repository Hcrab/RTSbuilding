package com.rtsbuilding.rtsbuilding.network.pathfinding;
import net.minecraft.entity.player.EntityPlayerMP;import net.minecraft.util.math.BlockPos;import net.minecraftforge.fml.common.network.simpleimpl.*;import java.lang.reflect.*;
/** 路径目标必须位于连接玩家当前 RTS 范围，移动状态由服务端路径服务持有。 */
public final class RtsPathfindingNetworkHandlers{
 private static final String CAMERA="com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager",REGISTRY="com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry";private RtsPathfindingNetworkHandlers(){}
 public static final class Handler implements IMessageHandler<C2SRtsPathfindingPayload,IMessage>{public IMessage onMessage(final C2SRtsPathfindingPayload m,MessageContext c){if(!m.isValid())return null;final EntityPlayerMP p=c.getServerHandler().player;p.getServerWorld().addScheduledTask(new Runnable(){public void run(){if(range(p,m.target()))go(p,m.target());}});return null;}}
 private static boolean range(EntityPlayerMP p,BlockPos x){try{return Boolean.TRUE.equals(Class.forName(CAMERA).getMethod("isWithinActionRange",EntityPlayerMP.class,BlockPos.class).invoke(null,p,x));}catch(ReflectiveOperationException e){throw fail(e);}}
 private static void go(EntityPlayerMP p,BlockPos x){try{Class<?>r=Class.forName(REGISTRY);Object i=r.getMethod("getInstance").invoke(null),s=r.getMethod("pathfinding").invoke(i);s.getClass().getMethod("goTo",EntityPlayerMP.class,BlockPos.class).invoke(s,p,x);}catch(ReflectiveOperationException e){throw fail(e);}}
 private static RuntimeException fail(ReflectiveOperationException e){Throwable c=e instanceof InvocationTargetException?((InvocationTargetException)e).getCause():e;return c instanceof RuntimeException?(RuntimeException)c:new IllegalStateException("1.12.2 pathfinding adapter unavailable",c);}
}
