package com.rtsbuilding.rtsbuilding.client.rendering.builder;
import net.minecraft.util.math.AxisAlignedBB;import net.minecraft.util.math.BlockPos;import java.util.*;
/** 把相邻方块合并成长方体并输出外骨架边。 */
final class UltimineBlockMerger{
 private static final double INFLATION=.005D;private UltimineBlockMerger(){}
 static List<EdgeLine> getEdgeLines(Collection<BlockPos> positions){List<AxisAlignedBB> boxes=merge(positions);List<EdgeLine> out=new ArrayList<EdgeLine>(boxes.size()*12);for(AxisAlignedBB a:boxes)addBox(out,a.grow(INFLATION));return out;}
 private static void addBox(List<EdgeLine>o,AxisAlignedBB a){double x=a.minX,X=a.maxX,y=a.minY,Y=a.maxY,z=a.minZ,Z=a.maxZ;
  add(o,x,y,z,X,y,z);add(o,X,y,z,X,y,Z);add(o,X,y,Z,x,y,Z);add(o,x,y,Z,x,y,z);add(o,x,Y,z,X,Y,z);add(o,X,Y,z,X,Y,Z);add(o,X,Y,Z,x,Y,Z);add(o,x,Y,Z,x,Y,z);add(o,x,y,z,x,Y,z);add(o,X,y,z,X,Y,z);add(o,X,y,Z,X,Y,Z);add(o,x,y,Z,x,Y,Z);}
 private static void add(List<EdgeLine>o,double a,double b,double c,double d,double e,double f){o.add(new EdgeLine(a,b,c,d,e,f));}
 static final class EdgeLine{private final double x1,y1,z1,x2,y2,z2;EdgeLine(double a,double b,double c,double d,double e,double f){x1=a;y1=b;z1=c;x2=d;y2=e;z2=f;}double x1(){return x1;}double y1(){return y1;}double z1(){return z1;}double x2(){return x2;}double y2(){return y2;}double z2(){return z2;}float xn(){return(float)(x2-x1);}float yn(){return(float)(y2-y1);}float zn(){return(float)(z2-z1);}}
 private static List<AxisAlignedBB> merge(Collection<BlockPos> p){List<AxisAlignedBB>b=new ArrayList<AxisAlignedBB>();if(p==null)return b;for(BlockPos q:p)b.add(new AxisAlignedBB(q));boolean changed;do{changed=false;outer:for(int axis=0;axis<3;axis++)for(int i=0;i<b.size();i++)for(int j=i+1;j<b.size();j++){AxisAlignedBB a=b.get(i),c=b.get(j);if(can(a,c,axis)){b.set(i,a.union(c));b.remove(j);changed=true;break outer;}}}while(changed);return b;}
 private static boolean can(AxisAlignedBB a,AxisAlignedBB b,int k){if(k==0)return a.minY==b.minY&&a.maxY==b.maxY&&a.minZ==b.minZ&&a.maxZ==b.maxZ&&(a.maxX==b.minX||b.maxX==a.minX);if(k==1)return a.minX==b.minX&&a.maxX==b.maxX&&a.minZ==b.minZ&&a.maxZ==b.maxZ&&(a.maxY==b.minY||b.maxY==a.minY);return a.minX==b.minX&&a.maxX==b.maxX&&a.minY==b.minY&&a.maxY==b.maxY&&(a.maxZ==b.minZ||b.maxZ==a.minZ);}
}
