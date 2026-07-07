package com.rtsbuilding.rtsbuilding.client.screen.panel.color;

/**
 * 颜色源——调色盘面板编辑的目标颜色抽象。
 *
 * <p>当调色盘面板打开时，它会从当前绑定的 {@link ColorSource} 读取初始颜色；
 * 用户调节轮盘、灰度条或滑条时，修改实时写回该源。
 *
 * <p>不同的颜色条目（屏障颜色、框选颜色等）各自实现此接口，
 * 调色盘面板无需知道具体是哪个颜色被编辑，实现"打开哪个条目就加载哪个颜色"的效果。</p>
 *
 * <p>用法：</p>
 * <pre>{@code
 * ColorSource barrierSource = new ColorSource() {
 *     public int getColor() { return BoundaryPass.barrierColor; }
 *     public void setColor(int c) { BoundaryPass.barrierColor = c; }
 * };
 * colorPickerButton.setColorSource(barrierSource);
 * }</pre>
 */
public interface ColorSource {

    /** 获取当前颜色值（ARGB 格式） */
    int getColor();

    /** 设置颜色值（ARGB 格式） */
    void setColor(int color);
}
