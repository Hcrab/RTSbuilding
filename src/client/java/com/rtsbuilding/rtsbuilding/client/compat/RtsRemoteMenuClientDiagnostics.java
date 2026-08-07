package com.rtsbuilding.rtsbuilding.client.compat;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 客户端远程 GUI 的低噪声诊断入口。
 *
 * <p>这里记录玩家真正需要排查的生命周期边界：收到远程提示、Menu 打开或替换、 Screen 缺失/恢复、校验字段放宽、菜单关闭和安全 fallback。它明确不记录每 tick 的
 * 完整快照；重复状态由 {@link RtsRemoteMenuTraceState} 压缩，避免整合包日志被淹没。
 */
public final class RtsRemoteMenuClientDiagnostics {
  private static final String PREFIX = "[RTS-GUI-CLIENT]";
  private static final RtsRemoteMenuTraceState TRACE = new RtsRemoteMenuTraceState();

  private RtsRemoteMenuClientDiagnostics() {}

  public static void receiveServerHint(BlockPos pos, int graceTicks) {
    TRACE.receiveHint(formatPos(pos), System.nanoTime());
    RtsbuildingMod.LOGGER.debug(
        "{} hint session={} target={} graceTicks={}",
        PREFIX,
        TRACE.activeSessionId(),
        TRACE.target(),
        graceTicks);
  }

  public static void observe(Minecraft minecraft, int pendingTicks, int screenlessTicks) {
    if (minecraft == null || minecraft.player == null) {
      return;
    }
    AbstractContainerMenu menu = minecraft.player.containerMenu;
    int containerId = menu == null ? -1 : menu.containerId;
    String menuClass = containerId == 0 || menu == null ? "" : menu.getClass().getName();
    Screen screen = minecraft.screen;
    String screenClass = screen == null ? "" : screen.getClass().getName();
    RtsRemoteMenuTraceState.Transition transition =
        TRACE.observe(containerId, menuClass, screenClass, pendingTicks, System.nanoTime());

    switch (transition.event()) {
      case MENU_OPENED ->
          RtsbuildingMod.LOGGER.info(
              "{} opened session={} target={} containerId={} menu={} screen={} pendingTicks={}",
              PREFIX,
              transition.sessionId(),
              transition.target(),
              containerId,
              display(menuClass),
              display(screenClass),
              pendingTicks);
      case MENU_CHANGED ->
          RtsbuildingMod.LOGGER.info(
              "{} changed session={} target={} containerId={} menu={} screen={}",
              PREFIX,
              transition.sessionId(),
              transition.target(),
              containerId,
              display(menuClass),
              display(screenClass));
      case SCREEN_MISSING ->
          RtsbuildingMod.LOGGER.warn(
              "{} screen-missing session={} target={} containerId={} menu={} screenlessTicks={}",
              PREFIX,
              transition.sessionId(),
              transition.target(),
              containerId,
              display(menuClass),
              screenlessTicks);
      case SCREEN_RECOVERED ->
          RtsbuildingMod.LOGGER.info(
              "{} screen-recovered session={} target={} containerId={} menu={} screen={} ageMs={}",
              PREFIX,
              transition.sessionId(),
              transition.target(),
              containerId,
              display(menuClass),
              display(screenClass),
              transition.ageMillis());
      case MENU_CLOSED ->
          RtsbuildingMod.LOGGER.info(
              "{} closed session={} target={} ageMs={}",
              PREFIX,
              transition.sessionId(),
              transition.target(),
              transition.ageMillis());
      case HINT_EXPIRED ->
          RtsbuildingMod.LOGGER.debug(
              "{} hint-expired session={} target={} ageMs={}",
              PREFIX,
              transition.sessionId(),
              transition.target(),
              transition.ageMillis());
      case NONE -> {}
    }
  }

  public static void validationApplied(
      AbstractContainerMenu menu, RtsClientRemoteMenuCompat.RelaxationReport report) {
    if (menu == null || report == null) {
      return;
    }
    RtsbuildingMod.LOGGER.info(
        "{} validation session={} target={} containerId={} menu={} scanned={} accessWrapped={} "
            + "nullAccess={} containersWrapped={} skipped={} firstSkipped={}",
        PREFIX,
        TRACE.activeSessionId(),
        TRACE.target(),
        menu.containerId,
        menu.getClass().getName(),
        report.scannedFields(),
        report.accessWrappers(),
        report.nullAccessReplacements(),
        report.containerWrappers(),
        report.skippedFields(),
        display(report.firstSkippedField()));
  }

  public static void screenlessRecovery(AbstractContainerMenu menu, int screenlessTicks) {
    RtsbuildingMod.LOGGER.warn(
        "{} screenless-recovery session={} target={} containerId={} menu={} screenlessTicks={}; "
            + "closing the remote menu and returning to RTS",
        PREFIX,
        TRACE.activeSessionId(),
        TRACE.target(),
        menu == null ? -1 : menu.containerId,
        menu == null ? "none" : menu.getClass().getName(),
        screenlessTicks);
  }

  public static void compatFailure(Minecraft minecraft, Throwable throwable) {
    String menuClass =
        minecraft != null && minecraft.player != null && minecraft.player.containerMenu != null
            ? minecraft.player.containerMenu.getClass().getName()
            : "none";
    String screenClass =
        minecraft != null && minecraft.screen != null
            ? minecraft.screen.getClass().getName()
            : "none";
    int containerId =
        minecraft != null && minecraft.player != null && minecraft.player.containerMenu != null
            ? minecraft.player.containerMenu.containerId
            : -1;
    RtsbuildingMod.LOGGER.error(
        "{} compat-fallback session={} target={} containerId={} menu={} screen={} ageMs={}; "
            + "closing the remote menu to prevent a client crash",
        PREFIX,
        TRACE.activeSessionId(),
        TRACE.target(),
        containerId,
        menuClass,
        screenClass,
        TRACE.ageMillis(System.nanoTime()),
        throwable);
  }

  public static void reset(String reason) {
    if (TRACE.activeSessionId() != 0L) {
      RtsbuildingMod.LOGGER.debug(
          "{} reset session={} target={} reason={}",
          PREFIX,
          TRACE.activeSessionId(),
          TRACE.target(),
          reason);
    }
    TRACE.reset();
  }

  private static String formatPos(BlockPos pos) {
    return pos == null ? "unknown" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
  }

  private static String display(String value) {
    return value == null || value.isBlank() ? "none" : value;
  }
}
