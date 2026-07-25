package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.blueprint.format.BlueprintReaders;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.network.S2CBlueprintStatusPayload;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 管理本地蓝图库的扫描、解析、排序与文件名索引。
 *
 * <p>本类不拥有面板选中项、滚动位置、旋转、弹窗、捕获或放置状态；
 * 这些玩家可见的工作流仍由 {@link BlueprintPanel} 统一控制。把磁盘仓储职责
 * 留在这里，可避免渲染/输入面板再次直接依赖文件扫描与蓝图解析细节。</p>
 */
final class BlueprintLibraryRepository {
    private static final Comparator<BlueprintEntry> ENTRY_ORDER =
            Comparator.comparing(BlueprintEntry::fileName, String.CASE_INSENSITIVE_ORDER);

    private final List<BlueprintEntry> entries = new ArrayList<>();
    private boolean loaded;

    void ensureLoaded(StatusSink status) {
        if (!loaded) {
            reload(status);
        }
    }

    void reload(StatusSink status) {
        loaded = true;
        entries.clear();
        Path folder = BlueprintPanelFiles.blueprintFolder();
        try {
            Files.createDirectories(folder);
            try (var stream = Files.list(folder)) {
                stream.filter(Files::isRegularFile)
                        .filter(BlueprintPanelFiles::isBlueprintFile)
                        .sorted(Comparator.comparing(
                                path -> path.getFileName().toString(),
                                String.CASE_INSENSITIVE_ORDER))
                        .limit(512)
                        .forEach(this::addEntry);
            }
        } catch (IOException ex) {
            if (status != null) {
                status.set(
                        S2CBlueprintStatusPayload.ERROR,
                        "screen.rtsbuilding.blueprints.status.folder_failed",
                        ex.getMessage());
            }
        }
    }

    int size() {
        return entries.size();
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    BlueprintEntry get(int index) {
        return entries.get(index);
    }

    List<BlueprintEntry> copyEntries() {
        return List.copyOf(entries);
    }

    boolean contains(BlueprintEntry entry) {
        return entries.contains(entry);
    }

    int indexOf(BlueprintEntry entry) {
        return entries.indexOf(entry);
    }

    BlueprintEntry findByFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        for (BlueprintEntry entry : entries) {
            if (entry.fileName().equals(fileName)) {
                return entry;
            }
        }
        return null;
    }

    int indexOfFileName(String fileName) {
        if (fileName == null) {
            return -1;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).fileName().equals(fileName)) {
                return i;
            }
        }
        return -1;
    }

    void addOrReplace(Path path, RtsBlueprint blueprint) {
        if (path == null || path.getFileName() == null || blueprint == null) {
            return;
        }
        String fileName = path.getFileName().toString();
        entries.removeIf(entry -> entry.fileName().equals(fileName));
        entries.add(BlueprintEntry.from(path, fileName, blueprint, ""));
        entries.sort(ENTRY_ORDER);
        loaded = true;
    }

    private void addEntry(Path path) {
        String fileName = path.getFileName().toString();
        try {
            byte[] data = Files.readAllBytes(path);
            RtsBlueprint blueprint = BlueprintReaders.parse(
                    data,
                    fileName,
                    Minecraft.getInstance().level.registryAccess());
            entries.add(BlueprintEntry.from(path, fileName, blueprint, ""));
        } catch (Exception ex) {
            entries.add(BlueprintEntry.error(path, fileName, ex.getMessage()));
        }
    }

    @FunctionalInterface
    interface StatusSink {
        void set(byte status, String messageKey, String detail);
    }
}
