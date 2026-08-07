package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.rtsbuilding.rtsbuilding.client.theme.UiThemeStorage;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;

/**
 * 主题导入文件选择器。
 *
 * <p>这个边界类只管理 TinyFileDialogs 的原生指针生命周期；主题面板本身不直接接触
 * LWJGL 内存，避免窗口渲染、主题校验和原生文件对话框耦合在一起。</p>
 */
final class ThemeFileDialogs {
    private ThemeFileDialogs() {
    }

    static Path chooseImport() {
        String selected;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*" + UiThemeStorage.THEME_SUFFIX));
            filters.flip();
            selected = TinyFileDialogs.tinyfd_openFileDialog(
                    "Import RTSBuilding UI Theme", null, filters,
                    "RTSBuilding UI themes", false);
        }
        return selected == null || selected.isBlank() ? null : Path.of(selected);
    }
}
