package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.io.BlueprintWriters;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanelFiles.*;
import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanelUi.text;
import static com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload.*;

/**
 * 蓝图库的文件系统边界。内部写入都先规范化并锁在蓝图目录，文件选择器只负责显式导入/导出。
 */
final class BlueprintLibraryFileOperations {
    enum SelectionMode { NONE, INDEX_ONLY, FULL }

    static final class Result {
        private final boolean reload;
        private final String selectedFileName;
        private final SelectionMode selectionMode;
        private final Byte status;
        private final String messageKey;
        private final String detail;

        Result(boolean reload, String selectedFileName, SelectionMode selectionMode, Byte status,
                String messageKey, String detail) {
            this.reload = reload;
            this.selectedFileName = selectedFileName == null ? "" : selectedFileName;
            this.selectionMode = selectionMode == null ? SelectionMode.NONE : selectionMode;
            this.status = status;
            this.messageKey = messageKey == null ? "" : messageKey;
            this.detail = detail == null ? "" : detail;
        }

        boolean reload() { return reload; }
        String selectedFileName() { return selectedFileName; }
        SelectionMode selectionMode() { return selectionMode; }
        Byte status() { return status; }
        String messageKey() { return messageKey; }
        String detail() { return detail; }

        static Result status(byte status, String key, String detail) {
            return new Result(false, "", SelectionMode.NONE, status, key, detail);
        }

        static Result reloadAndSelect(byte status, String key, String detail, String selected) {
            return new Result(true, selected, SelectionMode.INDEX_ONLY, status, key, detail);
        }

        static Result selectFully(String selected) {
            return new Result(false, selected, SelectionMode.FULL, null, "", "");
        }
    }

    static final class UploadReadResult {
        private final byte[] data;
        private final boolean tooLarge;
        private final String errorDetail;

        UploadReadResult(byte[] data, boolean tooLarge, String errorDetail) {
            this.data = data == null ? new byte[0] : data;
            this.tooLarge = tooLarge;
            this.errorDetail = errorDetail == null ? "" : errorDetail;
        }

        byte[] data() { return data; }
        boolean tooLarge() { return tooLarge; }
        String errorDetail() { return errorDetail; }
        boolean succeeded() { return !tooLarge && errorDetail.trim().isEmpty(); }
    }

    private BlueprintLibraryFileOperations() {}

    static UploadReadResult readForUpload(BlueprintEntry entry, int maxBytes) {
        if (entry == null || entry.path() == null) {
            return new UploadReadResult(null, false, "Missing blueprint file");
        }
        try {
            Path source = requireLibraryFile(entry.path());
            long size = Files.size(source);
            if (size > maxBytes) return new UploadReadResult(null, true, "");
            byte[] data = Files.readAllBytes(source);
            return new UploadReadResult(data, data.length > maxBytes, "");
        } catch (Exception ex) {
            return new UploadReadResult(null, false, failureDetail(ex));
        }
    }

    static Result openFolder() {
        try {
            Path folder = blueprintFolder();
            Files.createDirectories(folder);
            if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()) {
                throw new IOException("Desktop folder opening is unavailable in this environment");
            }
            Desktop.getDesktop().open(folder.toFile());
            return Result.status(INFO, "screen.rtsbuilding.blueprints.status.folder_opened", "");
        } catch (Exception ex) {
            return Result.status(ERROR, "screen.rtsbuilding.blueprints.status.folder_failed", failureDetail(ex));
        }
    }

    static Result importFile() {
        final Path source;
        try {
            source = chooseFile(false, "nbt", null);
        } catch (Exception ex) {
            return Result.status(ERROR, "screen.rtsbuilding.blueprints.status.import_failed", failureDetail(ex));
        }
        if (source == null) return Result.status(INFO, "screen.rtsbuilding.blueprints.status.import_cancelled", "");
        if (!Files.isRegularFile(source) || !isBlueprintFile(source)) {
            return Result.status(ERROR, "screen.rtsbuilding.blueprints.status.invalid_file", "");
        }
        try {
            Files.createDirectories(blueprintFolder());
            Path dest = resolveInBlueprintFolder(sanitizeImportedFileName(source.getFileName().toString()));
            atomicCopy(source, dest, true);
            return Result.reloadAndSelect(SUCCESS, "screen.rtsbuilding.blueprints.status.imported",
                    dest.getFileName().toString(), dest.getFileName().toString());
        } catch (Exception ex) {
            return Result.status(ERROR, "screen.rtsbuilding.blueprints.status.import_failed", failureDetail(ex));
        }
    }

    static Result syncOtherMods() {
        Set<Path> unique = new LinkedHashSet<Path>();
        for (Path path : otherModBlueprintFolders()) {
            Path normalized = path.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) unique.add(normalized);
        }
        if (unique.isEmpty()) return Result.status(INFO,
                "screen.rtsbuilding.blueprints.status.create_sync_missing", "");
        int copied = 0, skipped = 0, failed = 0;
        String lastCopied = "";
        try {
            Files.createDirectories(blueprintFolder());
            Map<String, Path> filesByName = new LinkedHashMap<String, Path>();
            for (Path sourceFolder : unique) {
                try (Stream<Path> stream = Files.walk(sourceFolder, 3)) {
                    List<Path> found = new ArrayList<Path>();
                    stream.filter(Files::isRegularFile).filter(BlueprintPanelFiles::isSyncBlueprintFile)
                            .limit(512).forEach(found::add);
                    Collections.sort(found, Comparator.comparing(path -> path.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER));
                    for (Path path : found) if (!filesByName.containsKey(path.getFileName().toString()))
                        filesByName.put(path.getFileName().toString(), path);
                } catch (IOException ex) { failed++; }
            }
            for (Map.Entry<String, Path> entry : filesByName.entrySet()) {
                Path dest = resolveInBlueprintFolder(sanitizeImportedFileName(entry.getKey()));
                if (Files.exists(dest)) { skipped++; continue; }
                try { atomicCopy(entry.getValue(), dest, false); copied++; lastCopied = dest.getFileName().toString(); }
                catch (IOException ex) { failed++; }
            }
            boolean reload = copied > 0;
            if (copied == 0 && skipped == 0 && failed == 0) return new Result(false, "", SelectionMode.NONE,
                    INFO, "screen.rtsbuilding.blueprints.status.create_sync_empty", "");
            if (failed > 0) return new Result(reload, lastCopied, SelectionMode.INDEX_ONLY, ERROR,
                    "screen.rtsbuilding.blueprints.status.create_sync_partial", copied + "/" + skipped + "/" + failed);
            return new Result(reload, lastCopied, SelectionMode.INDEX_ONLY, SUCCESS,
                    "screen.rtsbuilding.blueprints.status.create_sync_done", copied + "/" + skipped);
        } catch (Exception ex) {
            return Result.status(ERROR, "screen.rtsbuilding.blueprints.status.create_sync_failed", failureDetail(ex));
        }
    }

    static Result saveAs(BlueprintEntry entry) {
        if (entry == null || !entry.error().trim().isEmpty()) return Result.status(ERROR,
                "screen.rtsbuilding.blueprints.status.no_selection", "");
        String extension = blueprintExtension(entry.fileName(), entry.format().extension());
        String defaultName = sanitizeFileBase(stripBlueprintExtension(entry.fileName())) + "." + extension;
        final Path dest;
        try {
            dest = chooseFile(true, extension, blueprintFolder().resolve(defaultName));
        } catch (Exception ex) {
            return Result.status(ERROR, "screen.rtsbuilding.blueprints.status.export_failed", failureDetail(ex));
        }
        if (dest == null) return Result.status(INFO, "screen.rtsbuilding.blueprints.status.export_cancelled", "");
        Path output = ensureExtension(dest, extension).toAbsolutePath().normalize();
        try {
            Path parent = output.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path source = entry.path();
            if (source != null && Files.isRegularFile(source)) atomicCopy(requireLibraryFile(source), output, true);
            else writeBlueprintAtomically(entry, output);
            return Result.status(SUCCESS, "screen.rtsbuilding.blueprints.status.exported",
                    output.getFileName() == null ? output.toString() : output.getFileName().toString());
        } catch (Exception ex) {
            return Result.status(ERROR, "screen.rtsbuilding.blueprints.status.export_failed", failureDetail(ex));
        }
    }

    static Result rename(BlueprintEntry entry, String requestedName) {
        try {
            Path source = requireLibraryFile(entry == null ? null : entry.path());
            if (!Files.isRegularFile(source)) throw new IOException("Missing source file");
            String extension = blueprintExtension(entry.fileName(), entry.format().extension());
            Path dest = uniqueBlueprintPath(requestedName, extension, source);
            if (source.equals(dest.toAbsolutePath().normalize())) return Result.selectFully(entry.fileName());
            try { Files.move(source, dest, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(source, dest); }
            IOException rotationError = BlueprintRotationDefaults.rename(entry.fileName(), dest.getFileName().toString());
            return rotationError == null
                    ? Result.reloadAndSelect(SUCCESS, "screen.rtsbuilding.blueprints.status.renamed",
                            dest.getFileName().toString(), dest.getFileName().toString())
                    : Result.reloadAndSelect(ERROR, "screen.rtsbuilding.blueprints.status.save_failed",
                            rotationError.getMessage(), dest.getFileName().toString());
        } catch (Exception ex) {
            return Result.status(ERROR, "screen.rtsbuilding.blueprints.status.rename_failed", failureDetail(ex));
        }
    }

    static Result delete(BlueprintEntry entry) {
        final boolean confirmed;
        try { confirmed = confirmDelete(entry == null ? "" : entry.name()); }
        catch (Exception ex) { return Result.status(ERROR, "screen.rtsbuilding.blueprints.status.delete_failed", failureDetail(ex)); }
        if (!confirmed) return Result.status(INFO, "screen.rtsbuilding.blueprints.status.delete_cancelled", "");
        try {
            Path source = requireLibraryFile(entry == null ? null : entry.path());
            Files.deleteIfExists(source);
            IOException rotationError = BlueprintRotationDefaults.remove(entry.fileName());
            return rotationError == null
                    ? new Result(true, "", SelectionMode.NONE, SUCCESS,
                            "screen.rtsbuilding.blueprints.status.deleted", entry.name())
                    : new Result(true, "", SelectionMode.NONE, ERROR,
                            "screen.rtsbuilding.blueprints.status.save_failed", rotationError.getMessage());
        } catch (Exception ex) {
            return Result.status(ERROR, "screen.rtsbuilding.blueprints.status.delete_failed", failureDetail(ex));
        }
    }

    private static Path chooseFile(final boolean save, final String extension, final Path initial) throws Exception {
        if (GraphicsEnvironment.isHeadless()) throw new IOException("File chooser unavailable in headless mode");
        final Path[] result = new Path[1];
        Runnable task = () -> {
            JFileChooser chooser = new JFileChooser(initial == null ? blueprintFolder().toFile() : initial.toFile());
            chooser.setDialogTitle(text(save ? "screen.rtsbuilding.blueprints.save_as_title"
                    : "screen.rtsbuilding.blueprints.import_file"));
            chooser.setFileFilter(new FileNameExtensionFilter("Blueprint files",
                    save ? new String[] {extension} : new String[] {"nbt", "schem", "schematic", "litematic", "json"}));
            int answer = save ? chooser.showSaveDialog(null) : chooser.showOpenDialog(null);
            if (answer == JFileChooser.APPROVE_OPTION) result[0] = chooser.getSelectedFile().toPath();
        };
        runOnEdt(task);
        return result[0];
    }

    private static boolean confirmDelete(final String name) throws Exception {
        if (GraphicsEnvironment.isHeadless()) throw new IOException("Delete confirmation unavailable in headless mode");
        final boolean[] result = new boolean[1];
        runOnEdt(() -> result[0] = JOptionPane.showConfirmDialog(null,
                text("screen.rtsbuilding.blueprints.delete_confirm_message", name),
                text("screen.rtsbuilding.blueprints.delete_confirm_title"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION);
        return result[0];
    }

    private static void runOnEdt(Runnable task) throws InvocationTargetException, InterruptedException {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeAndWait(task);
    }

    private static Path requireLibraryFile(Path path) throws IOException {
        if (path == null) throw new IOException("Missing blueprint file");
        Path folder = blueprintFolder().toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(folder) || normalized.equals(folder))
            throw new IOException("Blueprint path escapes its directory");
        return normalized;
    }

    private static String sanitizeImportedFileName(String name) {
        String extension = blueprintExtension(name, "nbt");
        return sanitizeFileBase(stripBlueprintExtension(name)) + "." + extension;
    }

    private static void atomicCopy(Path source, Path destination, boolean replace) throws IOException {
        if (source.toAbsolutePath().normalize().equals(destination.toAbsolutePath().normalize())) return;
        Path parent = destination.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".blueprint-copy-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            try {
                if (replace) Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                else Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                if (replace) Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                else Files.move(temporary, destination);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void writeBlueprintAtomically(BlueprintEntry entry, Path destination) throws IOException {
        Path parent = destination.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".blueprint-write-", ".tmp");
        try {
            BlueprintWriters.writeVanillaStructure(entry.blueprint(), temporary);
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static String failureDetail(Throwable throwable) {
        Throwable cause = throwable == null ? null : throwable.getCause() == null ? throwable : throwable.getCause();
        if (cause == null) return "Unknown error";
        String message = cause.getMessage();
        return message == null || message.trim().isEmpty() ? cause.getClass().getSimpleName() : message;
    }
}
