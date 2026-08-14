package com.rtsbuilding.rtsbuilding.client.util;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.ByteBuffer;

/**
 * TinyFD 图形文件窗口的只读能力探针。
 *
 * <p>本类只查询 TinyFD 将为“打开文件”或“保存文件”选择图形后端还是控制台回退。
 * 它不显示窗口、不读取文件，也不替调用方决定取消或错误提示。集中这个边界可以避免
 * Android/FCL、无图形桌面的 Linux 等环境退回控制台输入并阻塞 Minecraft 主线程。</p>
 *
 * <p>TinyFD 的查询模式用原生指针值 {@code 0}/{@code 1} 表示控制台/图形模式。
 * 因此这里必须调用 LWJGL 的底层入口，不能让高级字符串封装把 {@code 1} 当作字符串地址。</p>
 */
public final class TinyFileDialogSupport {
    private static final String QUERY_TITLE = "tinyfd_query";

    private TinyFileDialogSupport() {
    }

    public static boolean canOpenFileDialog() {
        return query(QueryKind.OPEN);
    }

    public static boolean canSaveFileDialog() {
        return query(QueryKind.SAVE);
    }

    private static boolean query(QueryKind kind) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer title = stack.UTF8(QUERY_TITLE);
            long titleAddress = MemoryUtil.memAddress(title);
            long result = kind == QueryKind.OPEN
                    ? TinyFileDialogs.ntinyfd_openFileDialog(
                            titleAddress, MemoryUtil.NULL, 0,
                            MemoryUtil.NULL, MemoryUtil.NULL, 0)
                    : TinyFileDialogs.ntinyfd_saveFileDialog(
                            titleAddress, MemoryUtil.NULL, 0,
                            MemoryUtil.NULL, MemoryUtil.NULL);
            return result == 1L;
        } catch (LinkageError | RuntimeException failure) {
            RtsbuildingMod.LOGGER.warn(
                    "TinyFD 图形文件窗口能力探测失败，将跳过系统文件窗口：{}",
                    kind, failure);
            return false;
        }
    }

    private enum QueryKind {
        OPEN,
        SAVE
    }
}
