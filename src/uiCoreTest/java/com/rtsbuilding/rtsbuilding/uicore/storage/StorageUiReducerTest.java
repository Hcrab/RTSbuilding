package com.rtsbuilding.rtsbuilding.uicore.storage;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class StorageUiReducerTest {
    @Test void twoThousandRowsKeepOnlyVisibleWindow(){
        StorageUiState s=state(2000,1994);
        assertEquals(6,s.visibleEntries.size());assertEquals(1994,s.scroll);
        assertEquals(StorageUiTransition.Command.NONE,StorageUiReducer.apply(s,StorageUiAction.scroll(1)).command);
    }
    @Test void rowActionsRequireVisibleStableKey(){
        StorageUiState s=state(20,0);String key=s.visibleEntries.get(0).stableKey;
        assertEquals(StorageUiTransition.Command.TOGGLE_EXTRACT,StorageUiReducer.apply(s,StorageUiAction.key(StorageUiAction.Type.TOGGLE_EXTRACT,key)).command);
        assertEquals(StorageUiTransition.Command.NONE,StorageUiReducer.apply(s,StorageUiAction.key(StorageUiAction.Type.UNLINK,"missing")).command);
    }
    @Test void priorityClampsAndUnlinkUpdatesCount(){
        StorageUiState s=state(2,0);String key=s.visibleEntries.get(0).stableKey;
        assertEquals(9999,StorageUiReducer.apply(s,StorageUiAction.priority(key,50000)).state.visibleEntries.get(0).priority);
        assertEquals(1,StorageUiReducer.apply(s,StorageUiAction.key(StorageUiAction.Type.UNLINK,key)).state.totalRows);
    }
    private static StorageUiState state(int total,int scroll){
        StorageUiEntry[] rows=new StorageUiEntry[Math.min(6,total)];
        for(int i=0;i<rows.length;i++)rows[i]=new StorageUiEntry("p"+(scroll+i),"Chest "+i,"0,64,"+i,i,false,true,"minecraft:chest");
        return new StorageUiState(true,StorageUiStatus.READY,total,scroll,6,Arrays.asList(rows),"");
    }
}
