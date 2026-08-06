package com.rtsbuilding.rtsbuilding.server.service.page;
import net.minecraft.item.ItemStack;
import java.util.List; import java.util.Set;
final class Entry { private final ItemStack stack; private final String itemId,namespace,path,label; private final long count;
 Entry(ItemStack s,String i,String n,String p,String l,long c){stack=s;itemId=i;namespace=n;path=p;label=l;count=c;}
 ItemStack stack(){return stack;} String itemId(){return itemId;} String namespace(){return namespace;} String path(){return path;} String label(){return label;} long count(){return count;} }
final class FluidEntry { private final String fluidId,namespace,path; private final long amount,capacity;
 FluidEntry(String i,String n,String p,long a,long c){fluidId=i;namespace=n;path=p;amount=a;capacity=c;}
 String fluidId(){return fluidId;} String namespace(){return namespace;} String path(){return path;} long amount(){return amount;} long capacity(){return capacity;} }
final class LinkedRefPayload { private final List<Long> positions; private final List<Integer> dimensions,priorities; private final List<String> names,iconItemIds; private final List<Byte> modes; private final List<Boolean> worldAvailable;
 LinkedRefPayload(List<Long> p,List<Integer>d,List<String> n,List<Byte> m,List<Integer> r,List<String> i,List<Boolean>w){positions=p;dimensions=d;names=n;modes=m;priorities=r;iconItemIds=i;worldAvailable=w;}
 List<Long> positions(){return positions;} List<Integer> dimensions(){return dimensions;} List<String> names(){return names;} List<Byte> modes(){return modes;} List<Integer> priorities(){return priorities;} List<String> iconItemIds(){return iconItemIds;} List<Boolean> worldAvailable(){return worldAvailable;} }
final class CategorySelection { private final CategorySelectionType type; private final String namespace,tabKey;
 private CategorySelection(CategorySelectionType t,String n,String k){type=t;namespace=n;tabKey=k;}
 static CategorySelection all(){return new CategorySelection(CategorySelectionType.ALL,"","");} static CategorySelection mod(String n){return new CategorySelection(CategorySelectionType.MOD,n,"");} static CategorySelection tab(String n,String k){return new CategorySelection(CategorySelectionType.TAB,n,k);}
 CategorySelectionType type(){return type;} String namespace(){return namespace;} String tabKey(){return tabKey;}
 boolean isCreativeTab(){return type==CategorySelectionType.TAB;} boolean matches(String n,Set<String> tabs){if(type==CategorySelectionType.ALL)return true;if(type==CategorySelectionType.MOD)return namespace.equals(n);return namespace.equals(n)&&tabs!=null&&tabs.contains(tabKey);} }
enum CategorySelectionType { ALL,MOD,TAB }
