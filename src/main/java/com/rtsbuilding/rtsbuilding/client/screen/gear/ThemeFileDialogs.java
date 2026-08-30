package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.rtsbuilding.rtsbuilding.client.theme.UiThemeStorage;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;

/** 原生主题文件选择器；窗口本身不接触 LWJGL 指针和过滤器生命周期。 */
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
