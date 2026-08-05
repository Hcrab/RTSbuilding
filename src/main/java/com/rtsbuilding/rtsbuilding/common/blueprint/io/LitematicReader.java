package com.rtsbuilding.rtsbuilding.common.blueprint.io;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintParseException;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3i;
import net.minecraftforge.common.util.Constants;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Litematica 多区域、调色板、位压缩方块与方块实体读取器。 */
final class LitematicReader {
    private LitematicReader() {}

    static RtsBlueprint parse(byte[] data, String fileName) throws BlueprintParseException {
        NBTTagCompound root = compressed(data, fileName);
        if (!root.hasKey("Regions", Constants.NBT.TAG_COMPOUND))
            throw new BlueprintParseException("Litematic 文件缺少 Regions 数据: " + fileName);
        List<PendingBlock> pending = new ArrayList<PendingBlock>();
        NBTTagCompound regions = root.getCompoundTag("Regions");
        for (String name : regions.func_150296_c()) {
            if (regions.hasKey(name, Constants.NBT.TAG_COMPOUND)) readRegion(fileName, regions.getCompoundTag(name), pending);
        }
        if (pending.isEmpty()) return RtsBlueprint.create(readName(root, fileName), fileName, BlueprintFormat.LITEMATIC,
                new Vec3i(0, 0, 0), Collections.<RtsBlueprintBlock>emptyList());
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (PendingBlock block : pending) {
            BlockPos p = block.pos; minX=Math.min(minX,p.getX()); minY=Math.min(minY,p.getY()); minZ=Math.min(minZ,p.getZ());
            maxX=Math.max(maxX,p.getX()); maxY=Math.max(maxY,p.getY()); maxZ=Math.max(maxZ,p.getZ());
        }
        BlockPos offset = new BlockPos(-minX, -minY, -minZ);
        List<RtsBlueprintBlock> blocks = new ArrayList<RtsBlueprintBlock>(pending.size());
        for (PendingBlock pendingBlock : pending) {
            BlockPos pos = pendingBlock.pos.add(offset);
            NBTTagCompound nbt = pendingBlock.nbt == null ? new NBTTagCompound()
                    : (NBTTagCompound) pendingBlock.nbt.copy();
            BlueprintNbtCompat.StateResult state = pendingBlock.state;
            blocks.add(state.isMissing() ? RtsBlueprintBlock.missing(pos, state.missingBlockId(), nbt)
                    : new RtsBlueprintBlock(pos, state.state(), nbt));
        }
        return RtsBlueprint.create(readName(root,fileName), fileName, BlueprintFormat.LITEMATIC,
                new Vec3i(maxX-minX+1,maxY-minY+1,maxZ-minZ+1), blocks);
    }

    private static void readRegion(String fileName, NBTTagCompound region, List<PendingBlock> out)
            throws BlueprintParseException {
        Vec3i position=vec(region,"Position",new Vec3i(0,0,0)), size=vec(region,"Size",new Vec3i(0,0,0));
        int width=size.getX(),height=size.getY(),length=size.getZ();
        if(width==0||height==0||length==0)return;
        int aw=Math.abs(width),ah=Math.abs(height),al=Math.abs(length);
        long volume=(long)aw*ah*al;
        if(volume>Integer.MAX_VALUE)throw new BlueprintParseException("Litematic 区域过大: "+fileName);
        NBTTagList paletteTag=region.getTagList("BlockStatePalette",Constants.NBT.TAG_COMPOUND);
        List<BlueprintNbtCompat.StateResult> palette=new ArrayList<BlueprintNbtCompat.StateResult>(paletteTag.tagCount());
        for(int i=0;i<paletteTag.tagCount();i++)palette.add(BlueprintNbtCompat.readState(paletteTag.getCompoundTagAt(i)));
        if(palette.isEmpty())throw new BlueprintParseException("Litematic 区域缺少 BlockStatePalette: "+fileName);
        long[] states=longArray(region,"BlockStates");
        if(palette.size()>1&&states.length==0)throw new BlueprintParseException("Litematic 区域缺少 BlockStates: "+fileName);
        Map<BlockPos,NBTTagCompound> tiles=readTiles(region);
        int bits=Math.max(2,32-Integer.numberOfLeadingZeros(Math.max(1,palette.size()-1)));
        for(int index=0;index<(int)volume;index++){
            int paletteIndex=palette.size()==1?0:packed(states,index,bits);
            if(paletteIndex<0||paletteIndex>=palette.size())continue;
            BlueprintNbtCompat.StateResult entry=palette.get(paletteIndex);
            if(!entry.isMissing()&&(entry.state().getBlock()==Blocks.air||entry.state().getBlock()==Blocks.air))continue;
            int sx=index%aw,sz=(index/aw)%al,sy=index/(aw*al);
            BlockPos local=new BlockPos(coord(sx,width),coord(sy,height),coord(sz,length));
            BlockPos absolute=new BlockPos(position.getX()+local.getX(),position.getY()+local.getY(),position.getZ()+local.getZ());
            NBTTagCompound tile=tiles.get(local);
            out.add(new PendingBlock(absolute,entry,tile==null?new NBTTagCompound():tile));
        }
    }

    private static Map<BlockPos,NBTTagCompound> readTiles(NBTTagCompound region){
        Map<BlockPos,NBTTagCompound> out=new HashMap<BlockPos,NBTTagCompound>();
        NBTTagList list=region.getTagList("TileEntities",Constants.NBT.TAG_COMPOUND);
        for(int i=0;i<list.tagCount();i++){
            NBTTagCompound tag=list.getCompoundTagAt(i); BlockPos pos=tilePos(tag); if(pos!=null)out.put(pos,(NBTTagCompound)tag.copy());
        }
        return out;
    }

    private static BlockPos tilePos(NBTTagCompound tag){
        if(tag.hasKey("x",Constants.NBT.TAG_ANY_NUMERIC)&&tag.hasKey("y",Constants.NBT.TAG_ANY_NUMERIC)&&tag.hasKey("z",Constants.NBT.TAG_ANY_NUMERIC))
            return new BlockPos(tag.getInteger("x"),tag.getInteger("y"),tag.getInteger("z"));
        Vec3i pos=vec(tag,"Pos",null); return pos==null?null:new BlockPos(pos.getX(),pos.getY(),pos.getZ());
    }

    private static int packed(long[] data,int index,int bits){
        long bit=(long)index*bits;int start=(int)(bit>>6),end=(int)((((long)index+1)*bits-1)>>6),offset=(int)(bit&63L);
        if(start<0||start>=data.length)return 0;long value=data[start]>>>offset;
        if(start!=end&&end>=0&&end<data.length)value|=data[end]<<(64-offset);
        return(int)(value&((1L<<bits)-1L));
    }

    /** 1.12 的 NBTTagLongArray 已能解码 TAG_Long_Array，但没有公开数组访问器。 */
    private static long[] longArray(NBTTagCompound root,String key)throws BlueprintParseException{
        if(!root.hasKey(key,Constants.NBT.TAG_INT_ARRAY))return new long[0];
        int[] encoded=root.getIntArray(key);
        if((encoded.length&1)!=0)throw new BlueprintParseException("Litematic BlockStates 转换数据长度损坏");
        long[] values=new long[encoded.length/2];
        for(int i=0;i<values.length;i++)values[i]=((long)encoded[i*2]<<32)|(encoded[i*2+1]&0xffffffffL);
        return values;
        /* 旧的 NBTTagLongArray 反射路径在 1.7.10 中不存在。
            throw new IllegalStateException("NBTTagLongArray 缺少 long[] 字段");
        }catch(Exception ex){throw new BlueprintParseException("无法读取 Litematic BlockStates",ex);}
        */
    }

    private static int coord(int stored,int size){return size<0?stored+size+1:stored;}

    private static Vec3i vec(NBTTagCompound root,String key,Vec3i fallback){
        if(root.hasKey(key,Constants.NBT.TAG_COMPOUND)){NBTTagCompound t=root.getCompoundTag(key);return new Vec3i(t.getInteger("x"),t.getInteger("y"),t.getInteger("z"));}
        if(root.hasKey(key,Constants.NBT.TAG_INT_ARRAY)){int[]v=root.getIntArray(key);if(v.length>=3)return new Vec3i(v[0],v[1],v[2]);}
        if(root.hasKey(key,Constants.NBT.TAG_LIST)){NBTTagList v=root.getTagList(key,Constants.NBT.TAG_INT);if(v.tagCount()>=3)return new Vec3i(com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.getIntAt(v,0),com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.getIntAt(v,1),com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.getIntAt(v,2));}
        return fallback;
    }

    private static NBTTagCompound compressed(byte[]data,String file)throws BlueprintParseException{
        try{return CompressedStreamTools.readCompressed(new ByteArrayInputStream(data));}
        catch(Exception ex){throw new BlueprintParseException("读取压缩 Litematic 失败: "+file,ex);}
    }
    private static String readName(NBTTagCompound root,String file){
        NBTTagCompound metadata=root.getCompoundTag("Metadata");String name=metadata.getString("Name");return name.trim().isEmpty()?clean(file):name;
    }
    private static String clean(String file){if(file==null||file.trim().isEmpty())return"Blueprint";int s=Math.max(file.lastIndexOf('/'),file.lastIndexOf('\\'));String b=s>=0?file.substring(s+1):file;int d=b.lastIndexOf('.');return d>0?b.substring(0,d):b;}

    private static final class PendingBlock{
        final BlockPos pos;final BlueprintNbtCompat.StateResult state;final NBTTagCompound nbt;
        PendingBlock(BlockPos pos,BlueprintNbtCompat.StateResult state,NBTTagCompound nbt){this.pos=pos;this.state=state;this.nbt=nbt;}
    }
}
