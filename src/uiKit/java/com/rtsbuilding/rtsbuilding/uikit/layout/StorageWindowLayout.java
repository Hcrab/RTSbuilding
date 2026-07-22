package com.rtsbuilding.rtsbuilding.uikit.layout;

/** 绑定储存详情窗口的生产/离屏唯一几何来源。 */
public final class StorageWindowLayout {
    public static final int WINDOW_W=390, WINDOW_H=210, ROW_H=32, HEADER_H=26;
    public static final int PRIORITY_W=46, EXTRACT_W=38, UNLINK_W=48, UNLINK_H=16, CONTROL_H=16;
    public static final int SCROLLBAR_W=6, SCROLLBAR_GAP=5;
    private StorageWindowLayout(){}
    public static int left(int contentX){return contentX+8;}
    public static int top(int contentY){return contentY+8;}
    public static int innerWidth(int contentWidth){return contentWidth-16;}
    public static int visibleRows(int contentHeight){return Math.max(1,(contentHeight-HEADER_H-16)/ROW_H);}
    public static int rowWidth(int innerWidth,boolean scrollbar){return innerWidth-(scrollbar?SCROLLBAR_W+SCROLLBAR_GAP:0);}
    public static int firstRowY(int contentY){return top(contentY)+HEADER_H;}
    public static int controlY(int rowY){return rowY+7;}
    public static int unlinkX(int rowX,int rowW){return rowX+rowW-UNLINK_W-6;}
    public static int extractX(int rowX,int rowW){return unlinkX(rowX,rowW)-EXTRACT_W-6;}
    public static int priorityX(int rowX,int rowW){return extractX(rowX,rowW)-PRIORITY_W-6;}
}
