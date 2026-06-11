package com.rtsbuilding.rtsbuilding.client.screen.panel;


import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import net.minecraft.client.gui.GuiGraphics;

/**
 * RTS 闈㈡澘缁熶竴鎺ュ彛銆?
 * <p>
 * 鎵€鏈?RTS UI 闈㈡澘瀹炵幇璇ユ帴鍙ｏ紝鐢?{@link BuilderScreen} 缁熶竴璋冨害
 * 鐨?init / tick / render / 浜嬩欢鍒嗗彂鐢熷懡鍛ㄦ湡銆?
 */
public interface RtsPanel {

    /** 鍒濆鍖栭潰鏉匡紝姣忔灞忓箷 init() 鏃惰皟鐢?*/
    default void init(BuilderScreen screen, ClientRtsController controller) {}

    /** 姣?tick 鏇存柊闈㈡澘鐘舵€?*/
    default void tick() {}

    /** 娓叉煋闈㈡澘鍐呭 */
    default void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    /** 娓叉煋 tooltip锛堝湪 hover 妫€娴嬩箣鍚庯級 */
    default void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {}

    // --- 杈撳叆浜嬩欢 ---

    default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }

    default boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return false; }

    default boolean mouseMoved(double mouseX, double mouseY) { return false; }

    default boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) { return false; }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    default boolean keyReleased(int keyCode, int scanCode, int modifiers) { return false; }

    default boolean charTyped(char codePoint, int modifiers) { return false; }

    /** 闈㈡澘鍏抽棴/灞忓箷鍏抽棴鏃惰皟鐢?*/
    default void close() {}
}
