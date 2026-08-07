package com.rtsbuilding.rtsbuilding.client.compat;

/**
 * 远程 GUI 客户端诊断的纯状态机。
 *
 * <p>它只负责把每 tick 的 Menu/Screen 快照压缩成少量有意义的生命周期事件， 不持有 Minecraft 对象，也不写日志。这样既能避免在大型整合包里每 tick 刷屏，
 * 又能用普通单元测试锁住“打开、缺屏、恢复、关闭、hint 超时”的判定语义。
 */
final class RtsRemoteMenuTraceState {
  enum Event {
    NONE,
    MENU_OPENED,
    MENU_CHANGED,
    SCREEN_MISSING,
    SCREEN_RECOVERED,
    MENU_CLOSED,
    HINT_EXPIRED
  }

  record Transition(
      Event event,
      long sessionId,
      String target,
      int containerId,
      String menuClass,
      String screenClass,
      long ageMillis) {

    static Transition none() {
      return new Transition(Event.NONE, 0L, "unknown", -1, "", "", 0L);
    }
  }

  private long nextSessionId;
  private long activeSessionId;
  private long startedAtNanos;
  private String target = "unknown";
  private int lastContainerId = -1;
  private String lastMenuClass = "";
  private String lastScreenClass = "";
  private boolean screenMissing;
  private boolean active;

  void receiveHint(String target, long nowNanos) {
    if (!this.active || this.lastContainerId >= 0) {
      beginSession(nowNanos);
    }
    if (target != null && !target.isBlank()) {
      this.target = target;
    }
  }

  Transition observe(
      int containerId, String menuClass, String screenClass, int pendingTicks, long nowNanos) {
    String safeMenuClass = menuClass == null ? "" : menuClass;
    String safeScreenClass = screenClass == null ? "" : screenClass;
    boolean hasMenu = containerId != 0 && containerId >= 0 && !safeMenuClass.isBlank();

    if (hasMenu) {
      if (!this.active) {
        beginSession(nowNanos);
      }
      Event event = Event.NONE;
      if (this.lastContainerId < 0) {
        event = Event.MENU_OPENED;
      } else if (this.lastContainerId != containerId || !this.lastMenuClass.equals(safeMenuClass)) {
        event = Event.MENU_CHANGED;
      } else if (safeScreenClass.isBlank() && !this.screenMissing) {
        event = Event.SCREEN_MISSING;
      } else if (!safeScreenClass.isBlank() && this.screenMissing) {
        event = Event.SCREEN_RECOVERED;
      }

      this.lastContainerId = containerId;
      this.lastMenuClass = safeMenuClass;
      this.lastScreenClass = safeScreenClass;
      this.screenMissing = safeScreenClass.isBlank();
      return transition(event, nowNanos);
    }

    if (!this.active) {
      return Transition.none();
    }
    if (this.lastContainerId >= 0) {
      Transition transition = transition(Event.MENU_CLOSED, nowNanos);
      reset();
      return transition;
    }
    if (pendingTicks <= 0) {
      Transition transition = transition(Event.HINT_EXPIRED, nowNanos);
      reset();
      return transition;
    }
    return Transition.none();
  }

  long activeSessionId() {
    return this.activeSessionId;
  }

  String target() {
    return this.target;
  }

  long ageMillis(long nowNanos) {
    if (!this.active || this.startedAtNanos <= 0L) {
      return 0L;
    }
    return Math.max(0L, (nowNanos - this.startedAtNanos) / 1_000_000L);
  }

  void reset() {
    this.active = false;
    this.activeSessionId = 0L;
    this.startedAtNanos = 0L;
    this.target = "unknown";
    this.lastContainerId = -1;
    this.lastMenuClass = "";
    this.lastScreenClass = "";
    this.screenMissing = false;
  }

  private void beginSession(long nowNanos) {
    reset();
    this.active = true;
    this.activeSessionId = ++this.nextSessionId;
    this.startedAtNanos = nowNanos;
  }

  private Transition transition(Event event, long nowNanos) {
    if (event == Event.NONE) {
      return Transition.none();
    }
    return new Transition(
        event,
        this.activeSessionId,
        this.target,
        this.lastContainerId,
        this.lastMenuClass,
        this.lastScreenClass,
        ageMillis(nowNanos));
  }
}
