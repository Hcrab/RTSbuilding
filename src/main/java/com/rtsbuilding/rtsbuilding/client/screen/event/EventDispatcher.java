package com.rtsbuilding.rtsbuilding.client.screen.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * 事件分发器——以优先级顺序将输入事件派发给注册的处理器。
 *
 * <p><b>解决的问题：</b></p>
 * <ul>
 *   <li>{@code BuilderScreen.mouseClicked()} 中 200 行的 if-else 链条</li>
 *   <li>加一个新功能就要在 BuilderScreen 里插入一段 if 逻辑</li>
 *   <li>事件处理优先级（浮窗＞业务逻辑＞默认行为）硬编码在方法体中</li>
 * </ul>
 *
 * <p><b>优先级常量：</b></p>
 * <ul>
 *   <li>{@link #P_FLOATING_WINDOW} = 100 — 悬浮窗口面板（GearMenu, ColorPicker）</li>
 *   <li>{@link #P_UI_PANEL} = 80 — 固定面板（TopBar, LeftSidebar）</li>
 *   <li>{@link #P_BIND_LOGIC} = 60 — 容器绑定业务逻辑</li>
 *   <li>{@link #P_SELECTION} = 40 — 框选系统</li>
 *   <li>{@link #P_MOVEMENT} = 20 — 玩家移动</li>
 *   <li>{@link #P_INPUT_PIPELINE} = 0 — 内核输入管道</li>
 *   <li>{@link #P_FALLBACK} = -100 — 默认行为（如 super.mouseClicked）</li>
 * </ul>
 *
 * <p><b>用法：</b></p>
 * <pre>{@code
 * EventDispatcher disp = new EventDispatcher();
 *
 * disp.onMouseClick(this::handleFloatingWindowClick, P_FLOATING_WINDOW);
 * disp.onMouseClick(event -> {
 *     if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && isBindMode()) {
 *         return bindHandler.handleClickModeUnbind(this) ? CONSUMED : PASS;
 *     }
 *     return PASS;
 * }, P_BIND_LOGIC);
 *
 * // 在 mouseClicked 中只需一行：
 * return disp.dispatch(new MouseClickEvent(x, y, button));
 * }</pre>
 */
public final class EventDispatcher {

    // ======================== 优先级常量 ========================

    /** 浮窗面板优先级（最高） */
    public static final int P_FLOATING_WINDOW = 100;
    /** 固定 UI 面板（顶栏、侧边栏） */
    public static final int P_UI_PANEL = 80;
    /** 绑定业务逻辑 */
    public static final int P_BIND_LOGIC = 60;
    /** 框选系统 */
    public static final int P_SELECTION = 40;
    /** 实体交互——交互模式下右键与生物/方块交互 */
    public static final int P_ENTITY_INTERACT = 50;
    /** 玩家移动 */
    public static final int P_MOVEMENT = 20;
    /** 内核输入管道 */
    public static final int P_INPUT_PIPELINE = 0;
    /** 默认行为 */
    public static final int P_FALLBACK = -100;

    // ======================== 函数式接口 ========================

    @FunctionalInterface
    public interface MouseClickHandler {
        EventResult handle(MouseClickEvent event);
    }

    @FunctionalInterface
    public interface MouseReleaseHandler {
        EventResult handle(MouseReleaseEvent event);
    }

    @FunctionalInterface
    public interface MouseDragHandler {
        EventResult handle(MouseDragEvent event);
    }

    @FunctionalInterface
    public interface MouseScrollHandler {
        EventResult handle(MouseScrollEvent event);
    }

    @FunctionalInterface
    public interface MouseMoveHandler {
        EventResult handle(MouseMoveEvent event);
    }

    @FunctionalInterface
    public interface KeyPressHandler {
        EventResult handle(KeyPressEvent event);
    }

    @FunctionalInterface
    public interface KeyReleaseHandler {
        EventResult handle(KeyReleaseEvent event);
    }

    @FunctionalInterface
    public interface CharHandler {
        EventResult handle(CharEvent event);
    }

    // ======================== 处理器条目 ========================

    private record HandlerEntry(Object handler, int priority) {}

    // ======================== 处理器列表 ========================

    private final List<HandlerEntry> mouseClickHandlers = new ArrayList<>();
    private final List<HandlerEntry> mouseReleaseHandlers = new ArrayList<>();
    private final List<HandlerEntry> mouseDragHandlers = new ArrayList<>();
    private final List<HandlerEntry> mouseScrollHandlers = new ArrayList<>();
    private final List<HandlerEntry> mouseMoveHandlers = new ArrayList<>();
    private final List<HandlerEntry> keyPressHandlers = new ArrayList<>();
    private final List<HandlerEntry> keyReleaseHandlers = new ArrayList<>();
    private final List<HandlerEntry> charHandlers = new ArrayList<>();

    private boolean sorted;

    // ======================== 注册方法 ========================

    public void onMouseClick(MouseClickHandler handler, int priority) {
        mouseClickHandlers.add(new HandlerEntry(handler, priority));
        sorted = false;
    }

    public void onMouseRelease(MouseReleaseHandler handler, int priority) {
        mouseReleaseHandlers.add(new HandlerEntry(handler, priority));
        sorted = false;
    }

    public void onMouseDrag(MouseDragHandler handler, int priority) {
        mouseDragHandlers.add(new HandlerEntry(handler, priority));
        sorted = false;
    }

    public void onMouseScroll(MouseScrollHandler handler, int priority) {
        mouseScrollHandlers.add(new HandlerEntry(handler, priority));
        sorted = false;
    }

    public void onMouseMove(MouseMoveHandler handler, int priority) {
        mouseMoveHandlers.add(new HandlerEntry(handler, priority));
        sorted = false;
    }

    public void onKeyPress(KeyPressHandler handler, int priority) {
        keyPressHandlers.add(new HandlerEntry(handler, priority));
        sorted = false;
    }

    public void onKeyRelease(KeyReleaseHandler handler, int priority) {
        keyReleaseHandlers.add(new HandlerEntry(handler, priority));
        sorted = false;
    }

    public void onChar(CharHandler handler, int priority) {
        charHandlers.add(new HandlerEntry(handler, priority));
        sorted = false;
    }

    // ======================== 分发方法 ========================

    /** 分发鼠标点击事件。返回 true 表示事件已消费。 */
    public boolean dispatch(MouseClickEvent event) {
        ensureSorted();
        for (var entry : mouseClickHandlers) {
            if (((MouseClickHandler) entry.handler()).handle(event) == EventResult.CONSUMED) return true;
        }
        return false;
    }

    /** 分发鼠标释放事件。 */
    public boolean dispatch(MouseReleaseEvent event) {
        ensureSorted();
        for (var entry : mouseReleaseHandlers) {
            if (((MouseReleaseHandler) entry.handler()).handle(event) == EventResult.CONSUMED) return true;
        }
        return false;
    }

    /** 分发鼠标拖拽事件。 */
    public boolean dispatch(MouseDragEvent event) {
        ensureSorted();
        for (var entry : mouseDragHandlers) {
            if (((MouseDragHandler) entry.handler()).handle(event) == EventResult.CONSUMED) return true;
        }
        return false;
    }

    /** 分发鼠标滚轮事件。 */
    public boolean dispatch(MouseScrollEvent event) {
        ensureSorted();
        for (var entry : mouseScrollHandlers) {
            if (((MouseScrollHandler) entry.handler()).handle(event) == EventResult.CONSUMED) return true;
        }
        return false;
    }

    /** 分发鼠标移动事件。 */
    public boolean dispatch(MouseMoveEvent event) {
        ensureSorted();
        for (var entry : mouseMoveHandlers) {
            if (((MouseMoveHandler) entry.handler()).handle(event) == EventResult.CONSUMED) return true;
        }
        return false;
    }

    /** 分发按键按下事件。 */
    public boolean dispatch(KeyPressEvent event) {
        ensureSorted();
        for (var entry : keyPressHandlers) {
            if (((KeyPressHandler) entry.handler()).handle(event) == EventResult.CONSUMED) return true;
        }
        return false;
    }

    /** 分发按键释放事件。 */
    public boolean dispatch(KeyReleaseEvent event) {
        ensureSorted();
        for (var entry : keyReleaseHandlers) {
            if (((KeyReleaseHandler) entry.handler()).handle(event) == EventResult.CONSUMED) return true;
        }
        return false;
    }

    /** 分发字符输入事件。 */
    public boolean dispatch(CharEvent event) {
        ensureSorted();
        for (var entry : charHandlers) {
            if (((CharHandler) entry.handler()).handle(event) == EventResult.CONSUMED) return true;
        }
        return false;
    }

    // ======================== 内部 ========================

    private void ensureSorted() {
        if (sorted) return;
        mouseClickHandlers.sort(Comparator.comparingInt(e -> ((HandlerEntry) e).priority()).reversed());
        mouseReleaseHandlers.sort(Comparator.comparingInt(e -> ((HandlerEntry) e).priority()).reversed());
        mouseDragHandlers.sort(Comparator.comparingInt(e -> ((HandlerEntry) e).priority()).reversed());
        mouseScrollHandlers.sort(Comparator.comparingInt(e -> ((HandlerEntry) e).priority()).reversed());
        mouseMoveHandlers.sort(Comparator.comparingInt(e -> ((HandlerEntry) e).priority()).reversed());
        keyPressHandlers.sort(Comparator.comparingInt(e -> ((HandlerEntry) e).priority()).reversed());
        keyReleaseHandlers.sort(Comparator.comparingInt(e -> ((HandlerEntry) e).priority()).reversed());
        charHandlers.sort(Comparator.comparingInt(e -> ((HandlerEntry) e).priority()).reversed());
        sorted = true;
    }
}
