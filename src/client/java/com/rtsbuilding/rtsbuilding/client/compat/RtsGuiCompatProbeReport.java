package com.rtsbuilding.rtsbuilding.client.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * 探针的追加式报告和恢复游标。
 *
 * <p>每一行和每一个完成游标都立即关闭文件句柄落盘，因此即使大型整合包随后崩溃，前面的证据仍然保留。 这个类不判定 PASS/FAIL，也不控制客户端生命周期。
 */
final class RtsGuiCompatProbeReport {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final String HEADER =
      "timestamp\tsuiteId\tcaseId\ttargetBlock\ttick\tevent\tstatus"
          + "\tscreenClass\tscreenTitle\tmenuClass\tcontainerId\tnote\r\n";

  private final Path reportPath;
  private final Path statePath;
  private final String suiteId;
  private final String baselineSha;
  private final String manifestHash;
  private boolean headerChecked;

  RtsGuiCompatProbeReport(
      Path reportPath, String suiteId, String baselineSha, String manifestHash) {
    this.reportPath = reportPath;
    this.statePath =
        reportPath == null
            ? null
            : reportPath.resolveSibling(reportPath.getFileName() + ".state.json");
    this.suiteId = suiteId == null ? "unknown" : suiteId;
    this.baselineSha = baselineSha == null ? "unknown" : baselineSha;
    this.manifestHash = manifestHash == null ? "unknown" : manifestHash;
  }

  void append(
      long tick,
      RtsGuiCompatCase guiCase,
      String event,
      String status,
      String screenClass,
      String screenTitle,
      String menuClass,
      int containerId,
      String note) {
    if (this.reportPath == null) {
      return;
    }
    try {
      ensureHeader();
      String row =
          System.currentTimeMillis()
              + "\t"
              + escape(this.suiteId)
              + "\t"
              + escape(guiCase == null ? "unknown" : guiCase.id())
              + "\t"
              + escape(guiCase == null ? "unknown" : guiCase.blockId())
              + "\t"
              + tick
              + "\t"
              + escape(event)
              + "\t"
              + escape(status)
              + "\t"
              + escape(screenClass)
              + "\t"
              + escape(screenTitle)
              + "\t"
              + escape(menuClass)
              + "\t"
              + containerId
              + "\t"
              + escape(note)
              + "\r\n";
      Files.writeString(
          this.reportPath,
          row,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException exception) {
      RtsbuildingMod.LOGGER.warn(
          "Failed to write RTS GUI compat report: {}", this.reportPath, exception);
    }
  }

  int resumeIndex(int caseCount) {
    if (this.statePath == null || !Files.isRegularFile(this.statePath)) {
      return 0;
    }
    try {
      State state = GSON.fromJson(Files.readString(this.statePath), State.class);
      if (state == null
          || !this.suiteId.equals(state.suiteId)
          || !this.baselineSha.equals(state.baselineSha)
          || !this.manifestHash.equals(state.manifestHash)) {
        return 0;
      }
      return Math.max(0, Math.min(caseCount, state.nextIndex));
    } catch (RuntimeException | IOException exception) {
      RtsbuildingMod.LOGGER.warn(
          "Ignoring invalid RTS GUI compat checkpoint: {}", this.statePath, exception);
      return 0;
    }
  }

  void markCompleted(int completedIndex, RtsGuiCompatCase guiCase, String status) {
    if (this.statePath == null) {
      return;
    }
    State state =
        new State(
            this.suiteId,
            this.baselineSha,
            this.manifestHash,
            completedIndex + 1,
            guiCase == null ? "unknown" : guiCase.id(),
            status,
            ProcessHandle.current().pid(),
            System.currentTimeMillis());
    Path temporary = this.statePath.resolveSibling(this.statePath.getFileName() + ".tmp");
    try {
      Path parent = this.statePath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(
          temporary,
          GSON.toJson(state),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
      try {
        Files.move(
            temporary,
            this.statePath,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, this.statePath, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException exception) {
      RtsbuildingMod.LOGGER.warn(
          "Failed to write RTS GUI compat checkpoint: {}", this.statePath, exception);
    }
  }

  private void ensureHeader() throws IOException {
    if (this.headerChecked) {
      return;
    }
    Path parent = this.reportPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    if (!Files.exists(this.reportPath) || Files.size(this.reportPath) == 0L) {
      Files.writeString(
          this.reportPath,
          HEADER,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    }
    this.headerChecked = true;
  }

  private static String escape(String value) {
    return value == null ? "" : value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
  }

  private record State(
      String suiteId,
      String baselineSha,
      String manifestHash,
      int nextIndex,
      String lastCompletedCase,
      String lastStatus,
      long processId,
      long updatedAtMillis) {}
}
