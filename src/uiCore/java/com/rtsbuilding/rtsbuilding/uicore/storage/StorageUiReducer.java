package com.rtsbuilding.rtsbuilding.uicore.storage;

/** 储存绑定可见窗口与行操作状态机。 */
public final class StorageUiReducer {
    private StorageUiReducer(){}
    public static StorageUiTransition apply(StorageUiState state,StorageUiAction action){
        if(state==null||action==null)throw new IllegalArgumentException("state/action");
        switch(action.type){
            case SCROLL:{
                StorageUiState next=state.withScroll(state.scroll+action.value);
                return transition(next,action,next.scroll==state.scroll?StorageUiTransition.Command.NONE:StorageUiTransition.Command.SCROLL);
            }
            case SET_PRIORITY:
                return contains(state,action.stableKey)?transition(state.withEntry(action.stableKey,Integer.valueOf(action.value),false,false),action,StorageUiTransition.Command.SET_PRIORITY):none(state,action);
            case TOGGLE_EXTRACT:
                return contains(state,action.stableKey)?transition(state.withEntry(action.stableKey,null,true,false),action,StorageUiTransition.Command.TOGGLE_EXTRACT):none(state,action);
            case UNLINK:
                return contains(state,action.stableKey)?transition(state.withEntry(action.stableKey,null,false,true),action,StorageUiTransition.Command.UNLINK):none(state,action);
            default:return none(state,action);
        }
    }
    private static boolean contains(StorageUiState state,String key){for(StorageUiEntry e:state.visibleEntries)if(e.stableKey.equals(key))return true;return false;}
    private static StorageUiTransition transition(StorageUiState s,StorageUiAction a,StorageUiTransition.Command c){return new StorageUiTransition(s,a,c);}
    private static StorageUiTransition none(StorageUiState s,StorageUiAction a){return transition(s,a,StorageUiTransition.Command.NONE);}
}
