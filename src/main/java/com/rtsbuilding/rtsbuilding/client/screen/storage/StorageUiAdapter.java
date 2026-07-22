package com.rtsbuilding.rtsbuilding.client.screen.storage;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiAction;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiState;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiStatus;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiTransition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 1.21.1 绑定储存详情适配器；只转换当前可见行，避免 2000 项逐帧复制。 */
final class StorageUiAdapter {
    private StorageUiAdapter(){}

    static StorageUiState snapshot(ClientRtsController controller, boolean open,
            int scroll, int capacity) {
        List<LinkedStorageEntry> entries=controller.getLinkedStorageEntries();
        int max=Math.max(0,entries.size()-Math.max(1,capacity));
        int safe=Math.max(0,Math.min(scroll,max));
        List<StorageUiEntry> visible=new ArrayList<>();
        for(int i=safe;i<Math.min(entries.size(),safe+capacity);i++) visible.add(toCore(entries.get(i)));
        StorageUiStatus status=controller.isStorageScanRunning()?StorageUiStatus.LOADING
                :entries.isEmpty()?StorageUiStatus.EMPTY:StorageUiStatus.READY;
        return new StorageUiState(open,status,entries.size(),safe,capacity,visible,"");
    }

    static StorageUiTransition dispatch(ClientRtsController controller, StorageUiState state,
            StorageUiAction action) {
        StorageUiTransition transition=StorageUiReducer.apply(state,action);
        if(transition.command==StorageUiTransition.Command.SCROLL
                ||transition.command==StorageUiTransition.Command.NONE)return transition;
        LinkedStorageEntry entry=find(controller,action.stableKey);
        if(entry==null)return transition;
        if(transition.command==StorageUiTransition.Command.SET_PRIORITY){
            controller.updateLinkedStorageSettings(entry.pos(),
                    entry.mode()==C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY,action.value);
        }else if(transition.command==StorageUiTransition.Command.TOGGLE_EXTRACT){
            controller.updateLinkedStorageSettings(entry.pos(),
                    entry.mode()!=C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY,entry.priority());
        }else if(transition.command==StorageUiTransition.Command.UNLINK){
            controller.unlinkLinkedStorage(entry.pos());
        }
        return transition;
    }

    static String key(LinkedStorageEntry entry){
        BlockPos p=entry==null?null:entry.pos();
        return p==null?"":p.getX()+","+p.getY()+","+p.getZ();
    }
    private static LinkedStorageEntry find(ClientRtsController controller,String key){
        for(LinkedStorageEntry entry:controller.getLinkedStorageEntries())if(key(entry).equals(key))return entry;
        return null;
    }
    private static StorageUiEntry toCore(LinkedStorageEntry entry){
        ItemStack preview=entry.preview(); ResourceLocation id=preview==null||preview.isEmpty()?null:
                BuiltInRegistries.ITEM.getKey(preview.getItem());
        BlockPos p=entry.pos(); String pos=!entry.worldAvailable()?"N/A":p==null?"? ? ?":
                p.getX()+", "+p.getY()+", "+p.getZ();
        return new StorageUiEntry(key(entry),entry.label(),pos,entry.priority(),
                entry.mode()==C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY,
                entry.worldAvailable(),id==null?"":id.toString());
    }
}
