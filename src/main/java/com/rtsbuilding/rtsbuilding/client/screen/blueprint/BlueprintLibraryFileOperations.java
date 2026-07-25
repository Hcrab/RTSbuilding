package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.io.BlueprintWriters;
import net.minecraft.Util;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanelFiles.*;
import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanelUi.text;
import static com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload.*;

/**
 * 本地蓝图库的对话框和文件系统操作边界。
 *
 * <p>负责打开目录、导入、Create 蓝图同步、另存为、重命名和删除。操作结果只描述是否
 * 需要重载、如何恢复选择以及应显示的状态；它不修改 {@link BlueprintPanel} 的选择、
 * 滚动、捕获、放置或弹窗状态，也不拥有蓝图列表。这样文件 I/O 失败不会把 UI 状态机的
 * 中间字段散落到每个异常分支。</p>
 */
final class BlueprintLibraryFileOperations {
    enum SelectionMode {
        NONE,
        INDEX_ONLY,
        FULL
    }

    record Result(
            boolean reload,
            String selectedFileName,
            SelectionMode selectionMode,
            Byte status,
            String messageKey,
            String detail) {
        Result {
            selectedFileName = selectedFileName == null ? "" : selectedFileName;
            selectionMode = selectionMode == null ? SelectionMode.NONE : selectionMode;
            messageKey = messageKey == null ? "" : messageKey;
            detail = detail == null ? "" : detail;
        }

        static Result status(byte status, String messageKey, String detail) {
            return new Result(false, "", SelectionMode.NONE,
                    status, messageKey, detail);
        }

        static Result reloadAndSelect(
                byte status,
                String messageKey,
                String detail,
                String selectedFileName) {
            return new Result(true, selectedFileName, SelectionMode.INDEX_ONLY,
                    status, messageKey, detail);
        }

        static Result selectFully(String selectedFileName) {
            return new Result(false, selectedFileName, SelectionMode.FULL,
                    null, "", "");
        }
    }

    /**
     * 放置上传前的本地文件读取结果。
     *
     * <p>这里只描述磁盘边界的结果，不发送网络包，也不改变蓝图选择或预览状态。</p>
     */
    record UploadReadResult(byte[] data, boolean tooLarge, String errorDetail) {
        UploadReadResult {
            data = data == null ? new byte[0] : data;
            errorDetail = errorDetail == null ? "" : errorDetail;
        }

        boolean succeeded() {
            return !tooLarge && errorDetail.isBlank();
        }
    }

    private BlueprintLibraryFileOperations() {}

    /**
     * 读取待上传的蓝图，并在文件跨过网络协议上限时提前拒绝。
     */
    static UploadReadResult readForUpload(BlueprintEntry entry, int maxBytes) {
        if (entry == null || entry.path() == null) {
            return new UploadReadResult(new byte[0], false, "Missing blueprint file");
        }
        try {
            byte[] data = Files.readAllBytes(entry.path());
            return new UploadReadResult(data, data.length > maxBytes, "");
        } catch (IOException ex) {
            return new UploadReadResult(new byte[0], false, ex.getMessage());
        }
    }

    static Result openFolder() {
        Path folder = blueprintFolder();
        try {
            Files.createDirectories(folder);
            Util.getPlatform().openFile(folder.toFile());
            return Result.status(INFO,
                    "screen.rtsbuilding.blueprints.status.folder_opened", "");
        } catch (Exception ex) {
            return Result.status(ERROR,
                    "screen.rtsbuilding.blueprints.status.folder_failed",
                    ex.getMessage());
        }
    }

    static Result importFile() {
        String selected;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(5);
            filters.put(stack.UTF8("*.nbt"));
            filters.put(stack.UTF8("*.schem"));
            filters.put(stack.UTF8("*.schematic"));
            filters.put(stack.UTF8("*.litematic"));
            filters.put(stack.UTF8("*.json"));
            filters.flip();
            selected = TinyFileDialogs.tinyfd_openFileDialog(
                    text("screen.rtsbuilding.blueprints.import_file"),
                    null,
                    filters,
                    "Blueprint files",
                    false);
        }
        if (selected == null || selected.isBlank()) {
            return Result.status(INFO,
                    "screen.rtsbuilding.blueprints.status.import_cancelled", "");
        }
        Path source = Path.of(selected);
        if (!Files.isRegularFile(source) || !isBlueprintFile(source)) {
            return Result.status(ERROR,
                    "screen.rtsbuilding.blueprints.status.invalid_file", "");
        }
        try {
            Files.createDirectories(blueprintFolder());
            Path dest = blueprintFolder().resolve(source.getFileName().toString());
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            return Result.reloadAndSelect(
                    SUCCESS,
                    "screen.rtsbuilding.blueprints.status.imported",
                    dest.getFileName().toString(),
                    dest.getFileName().toString());
        } catch (Exception ex) {
            return Result.status(ERROR,
                    "screen.rtsbuilding.blueprints.status.import_failed",
                    ex.getMessage());
        }
    }

    static Result syncOtherMods() {
        List<Path> sourceFolders = otherModBlueprintFolders().stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::isDirectory)
                .distinct()
                .toList();
        if (sourceFolders.isEmpty()) {
            return Result.status(INFO,
                    "screen.rtsbuilding.blueprints.status.create_sync_missing", "");
        }
        int copied = 0;
        int skipped = 0;
        int failed = 0;
        String lastCopied = "";
        try {
            Files.createDirectories(blueprintFolder());
            Map<String, Path> filesByName = new LinkedHashMap<>();
            for (Path sourceFolder : sourceFolders) {
                try (var stream = Files.walk(sourceFolder, 3)) {
                    stream.filter(Files::isRegularFile)
                            .filter(BlueprintPanelFiles::isSyncBlueprintFile)
                            .sorted(Comparator.comparing(
                                    path -> path.getFileName().toString(),
                                    String.CASE_INSENSITIVE_ORDER))
                            .limit(512)
                            .forEach(path -> filesByName.putIfAbsent(
                                    path.getFileName().toString(), path));
                } catch (IOException ex) {
                    failed++;
                }
            }
            for (Map.Entry<String, Path> entry : filesByName.entrySet()) {
                Path dest = blueprintFolder().resolve(entry.getKey());
                if (Files.exists(dest)) {
                    skipped++;
                    continue;
                }
                try {
                    Files.copy(entry.getValue(), dest);
                    copied++;
                    lastCopied = entry.getKey();
                } catch (IOException ex) {
                    failed++;
                }
            }
            boolean reload = copied > 0;
            String selected = reload ? lastCopied : "";
            if (copied == 0 && skipped == 0 && failed == 0) {
                return new Result(false, "", SelectionMode.NONE, INFO,
                        "screen.rtsbuilding.blueprints.status.create_sync_empty", "");
            }
            if (failed > 0) {
                return new Result(reload, selected, SelectionMode.INDEX_ONLY, ERROR,
                        "screen.rtsbuilding.blueprints.status.create_sync_partial",
                        copied + "/" + skipped + "/" + failed);
            }
            return new Result(reload, selected, SelectionMode.INDEX_ONLY, SUCCESS,
                    "screen.rtsbuilding.blueprints.status.create_sync_done",
                    copied + "/" + skipped);
        } catch (Exception ex) {
            return Result.status(ERROR,
                    "screen.rtsbuilding.blueprints.status.create_sync_failed",
                    ex.getMessage());
        }
    }

    static Result saveAs(BlueprintEntry entry) {
        if (entry == null || !entry.error().isBlank()) {
            return Result.status(ERROR,
                    "screen.rtsbuilding.blueprints.status.no_selection", "");
        }
        String sourceExtension =
                blueprintExtension(entry.fileName(), entry.format().extension());
        String defaultFileName =
                sanitizeFileBase(stripBlueprintExtension(entry.fileName()))
                        + "." + sourceExtension;
        String selected;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*." + sourceExtension));
            filters.flip();
            selected = TinyFileDialogs.tinyfd_saveFileDialog(
                    text("screen.rtsbuilding.blueprints.save_as_title"),
                    blueprintFolder().resolve(defaultFileName).toString(),
                    filters,
                    "Blueprint files");
        }
        if (selected == null || selected.isBlank()) {
            return Result.status(INFO,
                    "screen.rtsbuilding.blueprints.status.export_cancelled", "");
        }
        Path dest = ensureExtension(Path.of(selected), sourceExtension);
        try {
            Path parent = dest.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path source = entry.path();
            if (source != null && Files.isRegularFile(source)) {
                Path normalizedSource = source.toAbsolutePath().normalize();
                Path normalizedDest = dest.toAbsolutePath().normalize();
                if (!normalizedSource.equals(normalizedDest)) {
                    Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                BlueprintWriters.writeVanillaStructure(entry.blueprint(), dest);
            }
            return Result.status(
                    SUCCESS,
                    "screen.rtsbuilding.blueprints.status.exported",
                    dest.getFileName() == null
                            ? dest.toString()
                            : dest.getFileName().toString());
        } catch (Exception ex) {
            return Result.status(ERROR,
                    "screen.rtsbuilding.blueprints.status.export_failed",
                    ex.getMessage());
        }
    }

    static Result rename(BlueprintEntry entry, String requestedName) {
        Path source = entry == null ? null : entry.path();
        if (source == null || !Files.isRegularFile(source)) {
            return Result.status(ERROR,
                    "screen.rtsbuilding.blueprints.status.rename_failed",
                    "Missing source file");
        }
        String extension =
                blueprintExtension(entry.fileName(), entry.format().extension());
        try {
            Files.createDirectories(blueprintFolder());
            Path dest = uniqueBlueprintPath(requestedName, extension, source);
            if (source.toAbsolutePath().normalize()
                    .equals(dest.toAbsolutePath().normalize())) {
                return Result.selectFully(entry.fileName());
            }
            Files.move(source, dest);
            IOException rotationError = BlueprintRotationDefaults.rename(
                    entry.fileName(), dest.getFileName().toString());
            if (rotationError == null) {
                return Result.reloadAndSelect(
                        SUCCESS,
                        "screen.rtsbuilding.blueprints.status.renamed",
                        dest.getFileName().toString(),
                        dest.getFileName().toString());
            }
            return Result.reloadAndSelect(
                    ERROR,
                    "screen.rtsbuilding.blueprints.status.save_failed",
                    rotationError.getMessage(),
                    dest.getFileName().toString());
        } catch (Exception ex) {
            return Result.status(ERROR,
                    "screen.rtsbuilding.blueprints.status.rename_failed",
                    ex.getMessage());
        }
    }

    static Result delete(BlueprintEntry entry) {
        boolean confirmed = TinyFileDialogs.tinyfd_messageBox(
                text("screen.rtsbuilding.blueprints.delete_confirm_title"),
                text("screen.rtsbuilding.blueprints.delete_confirm_message",
                        entry.name()),
                "yesno",
                "warning",
                false);
        if (!confirmed) {
            return Result.status(INFO,
                    "screen.rtsbuilding.blueprints.status.delete_cancelled", "");
        }
        try {
            Path source = entry.path();
            if (source != null) {
                Files.deleteIfExists(source);
            }
            IOException rotationError =
                    BlueprintRotationDefaults.remove(entry.fileName());
            if (rotationError == null) {
                return new Result(true, "", SelectionMode.NONE, SUCCESS,
                        "screen.rtsbuilding.blueprints.status.deleted",
                        entry.name());
            }
            return new Result(true, "", SelectionMode.NONE, ERROR,
                    "screen.rtsbuilding.blueprints.status.save_failed",
                    rotationError.getMessage());
        } catch (Exception ex) {
            return Result.status(ERROR,
                    "screen.rtsbuilding.blueprints.status.delete_failed",
                    ex.getMessage());
        }
    }
}
