package com.rtsbuilding.rtsbuilding.network.storage.handler;

import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsRequestStoragePagePayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/** 储存页面 C2S 请求边界；页面和链接内容始终由该连接对应的服务端玩家会话构建。 */
public final class RtsStoragePageHandlers1122 {
    private static final String REGISTRY="com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry";
    private RtsStoragePageHandlers1122(){}

    public static final class Request implements IMessageHandler<C2SRtsRequestStoragePagePayload,IMessage>{
        @Override public IMessage onMessage(final C2SRtsRequestStoragePagePayload m,MessageContext c){
            if(!m.isValid())return null;
            final EntityPlayerMP p=c.getServerHandler().player;
            p.getServerWorld().addScheduledTask(new Runnable(){@Override public void run(){
                // 容器 overlay 在相机退出后仍可能可见；权限由页面服务统一校验，不能在网络层静默丢弃搜索。
                invokePage(p,m);
            }});
            return null;
        }
    }

    private static void invokePage(EntityPlayerMP p,C2SRtsRequestStoragePagePayload m){
        try{
            Class<?>r=Class.forName(REGISTRY);Object registry=r.getMethod("getInstance").invoke(null);
            Object page=r.getMethod("page").invoke(registry);
            page.getClass().getMethod("requestPage",EntityPlayerMP.class,int.class,String.class,
                    String.class,RtsStorageSort.class,boolean.class,int.class,boolean.class,List.class)
                    .invoke(page,p,m.page(),m.search(),m.category(),RtsStorageSort.byId(m.sort()),
                            m.ascending(),m.pageSize(),m.pinyinSearchEnabled(),m.localizedSearchMatches());
        }catch(ClassNotFoundException|NoSuchMethodException|IllegalAccessException e){
            throw new IllegalStateException("1.12.2 storage page service adapter unavailable",e);
        }catch(InvocationTargetException e){throw propagate("Storage page request failed",e);}
    }

    private static RuntimeException propagate(String m,InvocationTargetException e){
        Throwable c=e.getCause();return c instanceof RuntimeException?(RuntimeException)c:new IllegalStateException(m,c);
    }
}
