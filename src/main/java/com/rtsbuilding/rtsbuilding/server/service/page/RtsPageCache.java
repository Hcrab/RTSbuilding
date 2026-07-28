package com.rtsbuilding.rtsbuilding.server.service.page;
import com.rtsbuilding.rtsbuilding.Config; import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import java.util.*;
public final class RtsPageCache {
 public static final RtsPageCache INSTANCE=new RtsPageCache();
 private final Map<UUID,CachedPage> cache=new LinkedHashMap<UUID,CachedPage>(16,0.75f,true);
 public static final class CachedPageKey { final String search,category; final RtsStorageSort sort; final boolean ascending,pinyinSearchEnabled,includePlayerInventory; final int pageSize;
  public CachedPageKey(String s,RtsStorageSort o,String c,boolean a,int p,boolean py,boolean inv){search=s;sort=o;category=c;ascending=a;pageSize=p;pinyinSearchEnabled=py;includePlayerInventory=inv;}
  @Override public boolean equals(Object x){if(this==x)return true;if(!(x instanceof CachedPageKey))return false;CachedPageKey k=(CachedPageKey)x;return ascending==k.ascending&&pageSize==k.pageSize&&pinyinSearchEnabled==k.pinyinSearchEnabled&&includePlayerInventory==k.includePlayerInventory&&Objects.equals(search,k.search)&&sort==k.sort&&Objects.equals(category,k.category);}
  @Override public int hashCode(){return Objects.hash(search,sort,category,ascending,pageSize,pinyinSearchEnabled,includePlayerInventory);} }
 public static final class CachedPage { final CachedPageKey key; final long dataVersion; final List<Entry> sortedEntries; final List<FluidEntry> sortedFluidEntries; final Map<String,Long> counts,namespaceTotals; final List<String> categories;
  public CachedPage(CachedPageKey k,long v,List<Entry>e,List<FluidEntry>f,Map<String,Long>c,Map<String,Long>n,List<String>a){key=k;dataVersion=v;sortedEntries=e;sortedFluidEntries=f;counts=c;namespaceTotals=n;categories=a;}
  CachedPageKey key(){return key;} long dataVersion(){return dataVersion;} List<Entry> sortedEntries(){return sortedEntries;} List<FluidEntry> sortedFluidEntries(){return sortedFluidEntries;} Map<String,Long> counts(){return counts;} Map<String,Long> namespaceTotals(){return namespaceTotals;} List<String> categories(){return categories;} }
 public synchronized CachedPage get(UUID id){return id==null?null:cache.get(id);} public synchronized void put(UUID id,CachedPage p){if(id==null||p==null)return;int max=Math.max(1,Config.pageCacheMaxPlayers());if(cache.size()>=max&&!cache.containsKey(id)){Iterator<Map.Entry<UUID,CachedPage>>it=cache.entrySet().iterator();if(it.hasNext()){it.next();it.remove();}}cache.put(id,p);} public synchronized void remove(UUID id){if(id!=null)cache.remove(id);} public synchronized void clear(){cache.clear();} public synchronized int size(){return cache.size();}
}
