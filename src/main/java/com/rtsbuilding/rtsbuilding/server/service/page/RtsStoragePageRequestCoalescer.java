package com.rtsbuilding.rtsbuilding.server.service.page;
import net.minecraft.entity.player.EntityPlayerMP;
import java.util.UUID;
public final class RtsStoragePageRequestCoalescer {
 private static final LatestPlayerPageRequestQueue<UUID,PendingRequest> PENDING=new LatestPlayerPageRequestQueue<UUID,PendingRequest>();
 private RtsStoragePageRequestCoalescer(){}
 public static void enqueue(EntityPlayerMP player,Runnable action){if(player!=null&&action!=null)PENDING.offer(player.getUniqueID(),new PendingRequest(player,action));}
 public static void flushPending(){PENDING.drain(new java.util.function.Consumer<PendingRequest>(){public void accept(PendingRequest r){execute(r);}});}
 public static void clearPlayer(UUID id){PENDING.remove(id);} public static void clearAll(){PENDING.clear();}
 private static void execute(PendingRequest r){if(r.player.playerNetServerHandler!=null)r.action.run();}
 private static final class PendingRequest { final EntityPlayerMP player; final Runnable action; PendingRequest(EntityPlayerMP p,Runnable a){player=p;action=a;} }
}
