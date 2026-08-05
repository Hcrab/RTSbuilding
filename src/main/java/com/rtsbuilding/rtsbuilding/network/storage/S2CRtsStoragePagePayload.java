package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 服务端生成的完整储存浏览器快照。所有列表在分配前读取有界数量；物品预览使用 1.12 原生
 * ItemStack/NBT codec。该消息只携带显示快照，不授予客户端提取或绑定权限。
 */
public final class S2CRtsStoragePagePayload implements IMessage {
    public static final byte RECENT_ITEM_PLACED=0, RECENT_ITEM_USED=1, RECENT_ITEM_CRAFTED=2;
    public static final byte RECENT_FLUID_PLACED=3, RECENT_FLUID_USED=4, RECENT_FLUID_CRAFTED=5;
    public static final int MAX_LINKED=50, MAX_CATEGORIES=256, MAX_PAGE_ITEMS=180;
    public static final int MAX_TOTAL_ITEMS=65536, MAX_FLUIDS=4096, MAX_RECENT=24;
    public static final int MAX_QUICK_SLOTS=27, MAX_GUI_BINDINGS=8, MAX_FUNNEL_ENTRIES=4096;

    private boolean linked, totalCountsSnapshot, ascending, autoStoreMinedDrops, useBdNetwork, funnelEnabled;
    private String linkedName="", search="", category="";
    private int page, totalPages, totalEntries;
    private byte sort;
    private List<Long> linkedPositions=empty(), counts=empty(), totalItemCounts=empty();
    private List<Long> fluidAmounts=empty(), fluidCapacities=empty(), recentAmounts=empty();
    private List<Long> recentCapacities=empty(), funnelBufferCounts=empty();
    private List<String> linkedNames=empty(), linkedIconItemIds=empty(), categories=empty();
    private List<String> totalItemIds=empty(), fluidIds=empty(), recentIds=empty();
    private List<String> quickSlotItemIds=empty(), guiBindingLabels=empty(), guiBindingItemIds=empty();
    private List<String> funnelBufferItemIds=empty();
    private List<Byte> linkedModes=empty(), recentKinds=empty();
    private List<Integer> linkedPriorities=empty();
    private List<Boolean> linkedWorldAvailable=empty();
    private List<ItemStack> itemStacks=empty(), quickSlotPreviews=empty();

    public S2CRtsStoragePagePayload() {
    }

    public S2CRtsStoragePagePayload(boolean linked, String linkedName, List<Long> linkedPositions,
            List<String> linkedNames, List<Byte> linkedModes, List<Integer> linkedPriorities,
            List<String> linkedIconItemIds, List<Boolean> linkedWorldAvailable,
            int page, int totalPages, int totalEntries, boolean totalCountsSnapshot,
            String search, String category, byte sort, boolean ascending,
            boolean autoStoreMinedDrops, boolean useBdNetwork, List<String> categories,
            List<ItemStack> itemStacks, List<Long> counts, List<String> totalItemIds,
            List<Long> totalItemCounts, List<String> fluidIds, List<Long> fluidAmounts,
            List<Long> fluidCapacities, List<String> recentIds, List<Long> recentAmounts,
            List<Long> recentCapacities, List<Byte> recentKinds, List<String> quickSlotItemIds,
            List<ItemStack> quickSlotPreviews, List<String> guiBindingLabels,
            List<String> guiBindingItemIds, boolean funnelEnabled,
            List<String> funnelBufferItemIds, List<Long> funnelBufferCounts) {
        this.linked=linked; this.linkedName=s(linkedName); this.linkedPositions=c(linkedPositions);
        this.linkedNames=c(linkedNames); this.linkedModes=c(linkedModes);
        this.linkedPriorities=c(linkedPriorities); this.linkedIconItemIds=c(linkedIconItemIds);
        this.linkedWorldAvailable=c(linkedWorldAvailable); this.page=page;
        this.totalPages=totalPages; this.totalEntries=totalEntries;
        this.totalCountsSnapshot=totalCountsSnapshot; this.search=s(search); this.category=s(category);
        this.sort=sort; this.ascending=ascending; this.autoStoreMinedDrops=autoStoreMinedDrops;
        this.useBdNetwork=useBdNetwork; this.categories=c(categories); this.itemStacks=c(itemStacks);
        this.counts=c(counts); this.totalItemIds=c(totalItemIds); this.totalItemCounts=c(totalItemCounts);
        this.fluidIds=c(fluidIds); this.fluidAmounts=c(fluidAmounts); this.fluidCapacities=c(fluidCapacities);
        this.recentIds=c(recentIds); this.recentAmounts=c(recentAmounts);
        this.recentCapacities=c(recentCapacities); this.recentKinds=c(recentKinds);
        this.quickSlotItemIds=c(quickSlotItemIds); this.quickSlotPreviews=c(quickSlotPreviews);
        this.guiBindingLabels=c(guiBindingLabels); this.guiBindingItemIds=c(guiBindingItemIds);
        this.funnelEnabled=funnelEnabled; this.funnelBufferItemIds=c(funnelBufferItemIds);
        this.funnelBufferCounts=c(funnelBufferCounts);
    }

    @Override public void toBytes(ByteBuf b) {
        validate();
        b.writeBoolean(linked); ws(b,linkedName); wl(b,linkedPositions,MAX_LINKED,"linked positions");
        wc(b,linkedNames.size(),MAX_LINKED,"linked details");
        for(int i=0;i<linkedNames.size();i++){ws(b,linkedNames.get(i));b.writeByte(linkedModes.get(i));
            b.writeInt(linkedPriorities.get(i));ws(b,linkedIconItemIds.get(i));b.writeBoolean(linkedWorldAvailable.get(i));}
        b.writeInt(page);b.writeInt(totalPages);b.writeInt(totalEntries);b.writeBoolean(totalCountsSnapshot);
        ws(b,search);ws(b,category);b.writeByte(sort);b.writeBoolean(ascending);
        b.writeBoolean(autoStoreMinedDrops);b.writeBoolean(useBdNetwork);
        wsl(b,categories,MAX_CATEGORIES,"categories");
        wc(b,itemStacks.size(),MAX_PAGE_ITEMS,"page items");
        for(int i=0;i<itemStacks.size();i++){RtsPacketBuffer.writeItemStack(b,nz(itemStacks.get(i)));b.writeLong(counts.get(i));}
        wsp(b,totalItemIds,totalItemCounts,MAX_TOTAL_ITEMS,"total items");
        wc(b,fluidIds.size(),MAX_FLUIDS,"fluids");
        for(int i=0;i<fluidIds.size();i++){ws(b,fluidIds.get(i));b.writeLong(fluidAmounts.get(i));b.writeLong(fluidCapacities.get(i));}
        wc(b,recentIds.size(),MAX_RECENT,"recent entries");
        for(int i=0;i<recentIds.size();i++){ws(b,recentIds.get(i));b.writeLong(recentAmounts.get(i));b.writeLong(recentCapacities.get(i));b.writeByte(recentKinds.get(i));}
        wsl(b,quickSlotItemIds,MAX_QUICK_SLOTS,"quick slots");
        wc(b,quickSlotPreviews.size(),MAX_QUICK_SLOTS,"quick previews");
        for(ItemStack stack:quickSlotPreviews)RtsPacketBuffer.writeItemStack(b,nz(stack));
        wsl(b,guiBindingLabels,MAX_GUI_BINDINGS,"GUI labels");
        wsl(b,guiBindingItemIds,MAX_GUI_BINDINGS,"GUI item ids");
        b.writeBoolean(funnelEnabled);wsp(b,funnelBufferItemIds,funnelBufferCounts,MAX_FUNNEL_ENTRIES,"funnel entries");
    }

    @Override public void fromBytes(ByteBuf b) {
        linked=b.readBoolean();linkedName=rs(b);linkedPositions=rl(b,MAX_LINKED,"linked positions");
        int n=rc(b,MAX_LINKED,"linked details");
        List<String> names=new ArrayList<String>(n),icons=new ArrayList<String>(n);
        List<Byte> modes=new ArrayList<Byte>(n);List<Integer> priorities=new ArrayList<Integer>(n);
        List<Boolean> available=new ArrayList<Boolean>(n);
        for(int i=0;i<n;i++){names.add(rs(b));modes.add(b.readByte());priorities.add(b.readInt());icons.add(rs(b));available.add(b.readBoolean());}
        linkedNames=c(names);linkedModes=c(modes);linkedPriorities=c(priorities);
        linkedIconItemIds=c(icons);linkedWorldAvailable=c(available);
        page=b.readInt();totalPages=b.readInt();totalEntries=b.readInt();totalCountsSnapshot=b.readBoolean();
        search=rs(b);category=rs(b);sort=b.readByte();ascending=b.readBoolean();
        autoStoreMinedDrops=b.readBoolean();useBdNetwork=b.readBoolean();
        categories=rsl(b,MAX_CATEGORIES,"categories");
        n=rc(b,MAX_PAGE_ITEMS,"page items");List<ItemStack> stacks=new ArrayList<ItemStack>(n);
        List<Long> itemCounts=new ArrayList<Long>(n);
        for(int i=0;i<n;i++){stacks.add(RtsPacketBuffer.readItemStack(b));itemCounts.add(b.readLong());}
        itemStacks=c(stacks);counts=c(itemCounts);
        StringLongPair totals=rsp(b,MAX_TOTAL_ITEMS,"total items");totalItemIds=totals.strings;totalItemCounts=totals.longs;
        n=rc(b,MAX_FLUIDS,"fluids");List<String> fids=new ArrayList<String>(n);
        List<Long> amounts=new ArrayList<Long>(n),capacities=new ArrayList<Long>(n);
        for(int i=0;i<n;i++){fids.add(rs(b));amounts.add(b.readLong());capacities.add(b.readLong());}
        fluidIds=c(fids);fluidAmounts=c(amounts);fluidCapacities=c(capacities);
        n=rc(b,MAX_RECENT,"recent entries");List<String> rids=new ArrayList<String>(n);
        List<Long> ramounts=new ArrayList<Long>(n),rcaps=new ArrayList<Long>(n);List<Byte> kinds=new ArrayList<Byte>(n);
        for(int i=0;i<n;i++){rids.add(rs(b));ramounts.add(b.readLong());rcaps.add(b.readLong());kinds.add(b.readByte());}
        recentIds=c(rids);recentAmounts=c(ramounts);recentCapacities=c(rcaps);recentKinds=c(kinds);
        quickSlotItemIds=rsl(b,MAX_QUICK_SLOTS,"quick slots");
        n=rc(b,MAX_QUICK_SLOTS,"quick previews");List<ItemStack> previews=new ArrayList<ItemStack>(n);
        for(int i=0;i<n;i++)previews.add(RtsPacketBuffer.readItemStack(b));quickSlotPreviews=c(previews);
        guiBindingLabels=rsl(b,MAX_GUI_BINDINGS,"GUI labels");
        guiBindingItemIds=rsl(b,MAX_GUI_BINDINGS,"GUI item ids");
        funnelEnabled=b.readBoolean();StringLongPair funnel=rsp(b,MAX_FUNNEL_ENTRIES,"funnel entries");
        funnelBufferItemIds=funnel.strings;funnelBufferCounts=funnel.longs;validate();
    }

    private void validate(){
        same(linkedPositions.size(),linkedNames,linkedModes,linkedPriorities,linkedIconItemIds,linkedWorldAvailable);
        same(itemStacks.size(),counts);same(totalItemIds.size(),totalItemCounts);
        same(fluidIds.size(),fluidAmounts,fluidCapacities);
        same(recentIds.size(),recentAmounts,recentCapacities,recentKinds);
        same(funnelBufferItemIds.size(),funnelBufferCounts);
        cap(linkedPositions,MAX_LINKED,"linked");cap(categories,MAX_CATEGORIES,"categories");
        cap(itemStacks,MAX_PAGE_ITEMS,"page items");cap(totalItemIds,MAX_TOTAL_ITEMS,"total items");
        cap(fluidIds,MAX_FLUIDS,"fluids");cap(recentIds,MAX_RECENT,"recent");
        cap(quickSlotItemIds,MAX_QUICK_SLOTS,"quick slots");cap(quickSlotPreviews,MAX_QUICK_SLOTS,"quick previews");
        cap(guiBindingLabels,MAX_GUI_BINDINGS,"GUI labels");cap(guiBindingItemIds,MAX_GUI_BINDINGS,"GUI ids");
        cap(funnelBufferItemIds,MAX_FUNNEL_ENTRIES,"funnel");
        if(page<0||totalPages<1||totalEntries<0||sort<0||sort>=RtsStorageSort.values().length)throw new IllegalArgumentException("invalid storage page metadata");
    }
    private static void same(int n,List<?>...lists){for(List<?>x:lists)if(x==null||x.size()!=n)throw new IllegalArgumentException("parallel storage lists differ");}
    private static void cap(List<?>x,int max,String name){if(x==null||x.size()>max)throw new IllegalArgumentException(name+" exceeds "+max);}
    private static void wc(ByteBuf b,int n,int max,String name){if(n<0||n>max)throw new IllegalArgumentException(name+" count");RtsPacketBuffer.writeVarInt(b,n);}
    private static int rc(ByteBuf b,int max,String name){return RtsPacketBuffer.readBoundedCount(b,max,name);}
    private static void ws(ByteBuf b,String x){RtsPacketBuffer.writeString(b,s(x),128,"storage text");}
    private static String rs(ByteBuf b){return RtsPacketBuffer.readString(b,128,"storage text");}
    private static void wl(ByteBuf b,List<Long>x,int max,String name){wc(b,x.size(),max,name);for(Long v:x)b.writeLong(v==null?0L:v);}
    private static List<Long> rl(ByteBuf b,int max,String name){int n=rc(b,max,name);List<Long>x=new ArrayList<Long>(n);for(int i=0;i<n;i++)x.add(b.readLong());return c(x);}
    private static void wsl(ByteBuf b,List<String>x,int max,String name){wc(b,x.size(),max,name);for(String v:x)ws(b,v);}
    private static List<String> rsl(ByteBuf b,int max,String name){int n=rc(b,max,name);List<String>x=new ArrayList<String>(n);for(int i=0;i<n;i++)x.add(rs(b));return c(x);}
    private static void wsp(ByteBuf b,List<String>s,List<Long>l,int max,String name){wc(b,s.size(),max,name);for(int i=0;i<s.size();i++){ws(b,s.get(i));b.writeLong(l.get(i));}}
    private static StringLongPair rsp(ByteBuf b,int max,String name){int n=rc(b,max,name);List<String>s=new ArrayList<String>(n);List<Long>l=new ArrayList<Long>(n);for(int i=0;i<n;i++){s.add(rs(b));l.add(b.readLong());}return new StringLongPair(c(s),c(l));}
    private static final class StringLongPair{final List<String>strings;final List<Long>longs;StringLongPair(List<String>s,List<Long>l){strings=s;longs=l;}}
    private static String s(String x){return x==null?"":x;}
    private static ItemStack nz(ItemStack x){return x==null?null:x;}
    private static <T>List<T> c(List<T>x){return x==null||x.isEmpty()?Collections.<T>emptyList():Collections.unmodifiableList(new ArrayList<T>(x));}
    private static <T>List<T> empty(){return Collections.emptyList();}

    public boolean linked(){return linked;}public String linkedName(){return linkedName;}public List<Long> linkedPositions(){return linkedPositions;}public List<String> linkedNames(){return linkedNames;}public List<Byte> linkedModes(){return linkedModes;}public List<Integer> linkedPriorities(){return linkedPriorities;}public List<String> linkedIconItemIds(){return linkedIconItemIds;}public List<Boolean> linkedWorldAvailable(){return linkedWorldAvailable;}
    public int page(){return page;}public int totalPages(){return totalPages;}public int totalEntries(){return totalEntries;}public boolean totalCountsSnapshot(){return totalCountsSnapshot;}public String search(){return search;}public String category(){return category;}public byte sort(){return sort;}public boolean ascending(){return ascending;}public boolean autoStoreMinedDrops(){return autoStoreMinedDrops;}public boolean useBdNetwork(){return useBdNetwork;}
    public List<String> categories(){return categories;}public List<ItemStack> itemStacks(){return itemStacks;}public List<Long> counts(){return counts;}public List<String> totalItemIds(){return totalItemIds;}public List<Long> totalItemCounts(){return totalItemCounts;}public List<String> fluidIds(){return fluidIds;}public List<Long> fluidAmounts(){return fluidAmounts;}public List<Long> fluidCapacities(){return fluidCapacities;}
    public List<String> recentIds(){return recentIds;}public List<Long> recentAmounts(){return recentAmounts;}public List<Long> recentCapacities(){return recentCapacities;}public List<Byte> recentKinds(){return recentKinds;}public List<String> quickSlotItemIds(){return quickSlotItemIds;}public List<ItemStack> quickSlotPreviews(){return quickSlotPreviews;}public List<String> guiBindingLabels(){return guiBindingLabels;}public List<String> guiBindingItemIds(){return guiBindingItemIds;}public boolean funnelEnabled(){return funnelEnabled;}public List<String> funnelBufferItemIds(){return funnelBufferItemIds;}public List<Long> funnelBufferCounts(){return funnelBufferCounts;}
}
