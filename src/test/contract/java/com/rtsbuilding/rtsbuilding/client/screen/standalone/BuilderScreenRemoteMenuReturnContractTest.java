package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 1.12 容器覆盖与玩家主动退出 RTS 之间的生命周期差异。 */
class BuilderScreenRemoteMenuReturnContractTest {
    private static final Path SCREEN = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");

    @Test
    void 容器替换BuilderScreen时不得退出Rts() throws Exception {
        String source = Files.readString(SCREEN, StandardCharsets.UTF_8);
        String closed = methodBody(source, "public void onGuiClosed()", "@Override", source.indexOf("public void onGuiClosed()") + 1);

        assertTrue(closed.contains("this.lifecycleOwner.removed();"));
        assertFalse(closed.contains("this.lifecycleOwner.onClose();"),
                "远程箱子覆盖 BuilderScreen 时，1.12 onGuiClosed 不得误发退出 RTS 的相机切换包。");
    }

    @Test
    void 未被面板消费的Esc仍执行主动退出语义() throws Exception {
        String source = Files.readString(SCREEN, StandardCharsets.UTF_8);
        String keyTyped = methodBody(source, "protected void keyTyped", "@Override", source.indexOf("protected void keyTyped") + 1);

        assertTrue(keyTyped.contains("if (keyCode == Keyboard.KEY_ESCAPE)"));
        assertTrue(keyTyped.contains("this.lifecycleOwner.onClose();"));
        assertTrue(keyTyped.indexOf("this.lifecycleOwner.onClose();")
                        < keyTyped.indexOf("super.keyTyped(typedChar, keyCode);"),
                "主动 Esc 必须在原版关闭屏幕前保存状态并请求退出 RTS。");
    }

    private static String methodBody(String source, String signature, String nextMarker, int markerSearchFrom) {
        int start = source.indexOf(signature);
        if (start < 0) throw new AssertionError("找不到方法: " + signature);
        int end = source.indexOf(nextMarker, markerSearchFrom);
        if (end < 0) end = source.length();
        return source.substring(start, end);
    }
}
