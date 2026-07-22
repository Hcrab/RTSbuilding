package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiState;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiStatus;

import java.util.ArrayList;
import java.util.List;

/** 绑定储存详情的 preview-only 有界 Core 快照。 */
final class StoragePreviewFixtures {
    private StoragePreviewFixtures(){}
    static boolean supports(UiPreviewScenario.Variant v){return v==UiPreviewScenario.Variant.STORAGE_LINKS_READY||v==UiPreviewScenario.Variant.STORAGE_LINKS_EMPTY||v==UiPreviewScenario.Variant.STORAGE_LINKS_LOADING||v==UiPreviewScenario.Variant.STORAGE_LINKS_2000||v==UiPreviewScenario.Variant.STORAGE_LINKS_FAILED;}
    static StorageUiState forScenario(UiPreviewScenario scenario,UiMainlineAssets assets){
        UiPreviewScenario.Variant v=scenario.variant(); int capacity=4;
        if(v==UiPreviewScenario.Variant.STORAGE_LINKS_EMPTY)return new StorageUiState(true,StorageUiStatus.EMPTY,0,0,capacity,new ArrayList<StorageUiEntry>(),"");
        if(v==UiPreviewScenario.Variant.STORAGE_LINKS_LOADING)return new StorageUiState(true,StorageUiStatus.LOADING,0,0,capacity,new ArrayList<StorageUiEntry>(),"");
        if(v==UiPreviewScenario.Variant.STORAGE_LINKS_FAILED)return new StorageUiState(true,StorageUiStatus.FAILED,0,0,capacity,new ArrayList<StorageUiEntry>(),"screen.rtsbuilding.storage_links.failed");
        int total=v==UiPreviewScenario.Variant.STORAGE_LINKS_2000?2000:4;
        int scroll=v==UiPreviewScenario.Variant.STORAGE_LINKS_2000?1996:0;
        List<String> names=assets.itemNames(); List<StorageUiEntry> rows=new ArrayList<StorageUiEntry>();
        for(int i=0;i<capacity;i++){int index=scroll+i;String name=names.get(index%names.size());rows.add(new StorageUiEntry("12,64,"+index,"Storage "+(index+1),"12, 64, "+index,(index%5)-2,index%3==0,true,"rtsbuilding:"+name));}
        return new StorageUiState(true,StorageUiStatus.READY,total,scroll,capacity,rows,"");
    }
}
