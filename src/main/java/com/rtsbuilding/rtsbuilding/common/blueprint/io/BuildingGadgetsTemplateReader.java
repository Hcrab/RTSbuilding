package com.rtsbuilding.rtsbuilding.common.blueprint.io;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintParseException;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3i;
import net.minecraftforge.common.util.Constants;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Building Gadgets 新旧 JSON 模板兼容读取器。 */
final class BuildingGadgetsTemplateReader {
    private static final int B1=0xff,B2=0xffff,B3=0xffffff;
    private BuildingGadgetsTemplateReader(){}

    static RtsBlueprint parse(byte[] data,String file)throws BlueprintParseException{
        JsonObject root=json(data,file);String name=name(root,file);
        String mapped=string(root,"statePosArrayList");
        if(!mapped.trim().isEmpty())return parseMapped(snbt(mapped,file),name,file);
        String body=string(root,"body");
        if(!body.trim().isEmpty())return parseLegacy(root,body,name,file);
        throw new BlueprintParseException("Building Gadgets JSON 缺少模板数据: "+file);
    }

    private static JsonObject json(byte[]data,String file)throws BlueprintParseException{
        try{JsonElement value=new JsonParser().parse(new String(data,StandardCharsets.UTF_8));if(!value.isJsonObject())throw new BlueprintParseException("Building Gadgets 模板不是 JSON 对象: "+file);return value.getAsJsonObject();}
        catch(BlueprintParseException ex){throw ex;}catch(Exception ex){throw new BlueprintParseException("读取 Building Gadgets JSON 失败: "+file,ex);}
    }
    private static NBTTagCompound snbt(String text,String file)throws BlueprintParseException{
        try{return (NBTTagCompound) JsonToNBT.func_150315_a(text);}catch(Exception ex){throw new BlueprintParseException("读取 Building Gadgets 方块列表失败: "+file,ex);}
    }

    private static RtsBlueprint parseMapped(NBTTagCompound tag,String name,String file)throws BlueprintParseException{
        if(!tag.hasKey("blockstatemap",Constants.NBT.TAG_LIST)||!tag.hasKey("statelist",Constants.NBT.TAG_INT_ARRAY))
            throw new BlueprintParseException("Building Gadgets 模板缺少方块状态映射: "+file);
        Bounds bounds=Bounds.from(pos(tag.getCompoundTag("startpos")),pos(tag.getCompoundTag("endpos")));
        List<BlueprintNbtCompat.StateResult> palette=palette(tag.getTagList("blockstatemap",Constants.NBT.TAG_COMPOUND));
        int[] states=tag.getIntArray("statelist");List<RtsBlueprintBlock> blocks=new ArrayList<RtsBlueprintBlock>();int index=0;
        for(int y=bounds.min.getY();y<=bounds.max.getY();y++)for(int z=bounds.min.getZ();z<=bounds.max.getZ();z++)for(int x=bounds.min.getX();x<=bounds.max.getX();x++){
            if(index>=states.length)break;int id=states[index++];if(id>=0&&id<palette.size())add(blocks,new BlockPos(x,y,z),bounds.min,palette.get(id));
        }
        return RtsBlueprint.create(name,file,BlueprintFormat.BUILDING_GADGETS_JSON,bounds.size(),blocks);
    }

    private static RtsBlueprint parseLegacy(JsonObject root,String body,String name,String file)throws BlueprintParseException{
        NBTTagCompound nbt;
        try{nbt=CompressedStreamTools.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(body)));}
        catch(Exception ex){throw new BlueprintParseException("读取 Building Gadgets 模板 body 失败: "+file,ex);}
        if(nbt.hasKey("blockstatemap",Constants.NBT.TAG_LIST)&&nbt.hasKey("statelist",Constants.NBT.TAG_INT_ARRAY))return parseMapped(nbt,name,file);
        NBTTagList positions=nbt.getTagList("pos",Constants.NBT.TAG_LONG),data=nbt.getTagList("data",Constants.NBT.TAG_COMPOUND);
        if(positions.tagCount()==0||data.tagCount()==0)throw new BlueprintParseException("Building Gadgets 旧模板缺少方块数据: "+file);
        Map<BlockPos,BlueprintNbtCompat.StateResult> byPos=new HashMap<BlockPos,BlueprintNbtCompat.StateResult>();
        for(int i=0;i<positions.tagCount();i++){
            long encoded=com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.getLongAt(positions,i);int id=(int)((encoded>>40)&B3);
            if(id>=0&&id<data.tagCount()){NBTTagCompound value=data.getCompoundTagAt(id);if(value.hasKey("state",Constants.NBT.TAG_COMPOUND))value=value.getCompoundTag("state");byPos.put(new BlockPos((int)((encoded>>24)&B2),(int)((encoded>>16)&B1),(int)(encoded&B2)),BlueprintNbtCompat.readState(value));}
        }
        if(byPos.isEmpty())return RtsBlueprint.create(name,file,BlueprintFormat.BUILDING_GADGETS_JSON,new Vec3i(0,0,0),Collections.<RtsBlueprintBlock>emptyList());
        Bounds bounds=jsonBounds(root);
        if(bounds==null||!bounds.contains(byPos.keySet())){
            NBTTagCompound header=nbt.getCompoundTag("header");
            bounds=nbtBounds(header.getCompoundTag("bounds"));
        }
        if(bounds==null||!bounds.contains(byPos.keySet()))bounds=Bounds.positions(byPos.keySet());
        List<RtsBlueprintBlock> blocks=new ArrayList<RtsBlueprintBlock>();for(Map.Entry<BlockPos,BlueprintNbtCompat.StateResult> entry:byPos.entrySet())add(blocks,entry.getKey(),bounds.min,entry.getValue());
        return RtsBlueprint.create(name,file,BlueprintFormat.BUILDING_GADGETS_JSON,bounds.size(),blocks);
    }

    private static List<BlueprintNbtCompat.StateResult> palette(NBTTagList list){List<BlueprintNbtCompat.StateResult> out=new ArrayList<BlueprintNbtCompat.StateResult>(list.tagCount());for(int i=0;i<list.tagCount();i++)out.add(BlueprintNbtCompat.readState(list.getCompoundTagAt(i)));return out;}
    private static void add(List<RtsBlueprintBlock> out,BlockPos absolute,BlockPos min,BlueprintNbtCompat.StateResult entry){
        BlockPos relative=absolute.add(-min.getX(),-min.getY(),-min.getZ());if(entry.isMissing())out.add(RtsBlueprintBlock.missing(relative,entry.missingBlockId(),new NBTTagCompound()));
        else if(entry.state().getBlock()!=Blocks.air&&entry.state().getBlock()!=Blocks.air)out.add(new RtsBlueprintBlock(relative,entry.state(),new NBTTagCompound()));
    }
    private static BlockPos pos(NBTTagCompound tag){return new BlockPos(tag.getInteger("X"),tag.getInteger("Y"),tag.getInteger("Z"));}
    private static Bounds jsonBounds(JsonObject root){JsonObject header=object(root,"header");JsonObject box=header==null?null:object(header,"bounding_box");if(box==null&&header!=null)box=object(header,"bounds");return box==null?null:Bounds.from(new BlockPos(integer(box,"min_x","minX"),integer(box,"min_y","minY"),integer(box,"min_z","minZ")),new BlockPos(integer(box,"max_x","maxX"),integer(box,"max_y","maxY"),integer(box,"max_z","maxZ")));}
    private static Bounds nbtBounds(NBTTagCompound tag){return tag==null||com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.isEmpty(tag)?null:Bounds.from(new BlockPos(tag.getInteger("minX"),tag.getInteger("minY"),tag.getInteger("minZ")),new BlockPos(tag.getInteger("maxX"),tag.getInteger("maxY"),tag.getInteger("maxZ")));}
    private static String name(JsonObject root,String file){String value=string(root,"name");if(!value.trim().isEmpty())return value;JsonObject header=object(root,"header");value=header==null?"":string(header,"name");return value.trim().isEmpty()?clean(file):value;}
    private static JsonObject object(JsonObject root,String key){JsonElement e=root==null?null:root.get(key);return e!=null&&e.isJsonObject()?e.getAsJsonObject():null;}
    private static String string(JsonObject root,String key){try{JsonElement e=root==null?null:root.get(key);return e!=null&&e.isJsonPrimitive()?e.getAsString():"";}catch(Exception ignored){return"";}}
    private static int integer(JsonObject root,String a,String b){JsonElement e=root.has(a)?root.get(a):root.get(b);return e==null?0:e.getAsInt();}
    private static String clean(String file){if(file==null||file.trim().isEmpty())return"Building Gadgets Template";int s=Math.max(file.lastIndexOf('/'),file.lastIndexOf('\\'));String b=s>=0?file.substring(s+1):file;int d=b.lastIndexOf('.');return d>0?b.substring(0,d):b;}

    private static final class Bounds{
        final BlockPos min,max;Bounds(BlockPos min,BlockPos max){this.min=min;this.max=max;}
        static Bounds from(BlockPos a,BlockPos b){return new Bounds(new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ())),new BlockPos(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ())));}
        static Bounds positions(Iterable<BlockPos> values){BlockPos first=null;int minX=0,minY=0,minZ=0,maxX=0,maxY=0,maxZ=0;for(BlockPos p:values){if(first==null){first=p;minX=maxX=p.getX();minY=maxY=p.getY();minZ=maxZ=p.getZ();}else{minX=Math.min(minX,p.getX());minY=Math.min(minY,p.getY());minZ=Math.min(minZ,p.getZ());maxX=Math.max(maxX,p.getX());maxY=Math.max(maxY,p.getY());maxZ=Math.max(maxZ,p.getZ());}}return new Bounds(new BlockPos(minX,minY,minZ),new BlockPos(maxX,maxY,maxZ));}
        boolean contains(Iterable<BlockPos> values){for(BlockPos p:values)if(p.getX()<min.getX()||p.getY()<min.getY()||p.getZ()<min.getZ()||p.getX()>max.getX()||p.getY()>max.getY()||p.getZ()>max.getZ())return false;return true;}
        Vec3i size(){return new Vec3i(max.getX()-min.getX()+1,max.getY()-min.getY()+1,max.getZ()-min.getZ()+1);}
    }
}
