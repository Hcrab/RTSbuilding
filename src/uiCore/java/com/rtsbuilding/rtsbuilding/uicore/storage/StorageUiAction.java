package com.rtsbuilding.rtsbuilding.uicore.storage;

/** 储存绑定详情动作。 */
public final class StorageUiAction {
    public enum Type { SCROLL, SET_PRIORITY, TOGGLE_EXTRACT, UNLINK }
    public final Type type;
    public final String stableKey;
    public final int value;
    private StorageUiAction(Type type, String stableKey, int value) {
        this.type=type; this.stableKey=stableKey == null ? "" : stableKey; this.value=value;
    }
    public static StorageUiAction scroll(int delta){return new StorageUiAction(Type.SCROLL,"",delta);}
    public static StorageUiAction priority(String key,int value){return new StorageUiAction(Type.SET_PRIORITY,key,value);}
    public static StorageUiAction key(Type type,String key){return new StorageUiAction(type,key,0);}
}
