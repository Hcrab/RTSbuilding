package com.rtsbuilding.rtsbuilding.client.presentation.event.dispatcher;

import com.rtsbuilding.rtsbuilding.client.presentation.event.model.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class EventDispatcher {

    

    
    public static final int P_FLOATING_WINDOW = 100;
    
    public static final int P_UI_PANEL = 80;
    
    public static final int P_BIND_LOGIC = 60;
    
    public static final int P_SELECTION = 40;
    
    public static final int P_ENTITY_INTERACT = 50;
    
    public static final int P_MOVEMENT = 20;
    
    public static final int P_INPUT_PIPELINE = 0;
    
    public static final int P_BUILD_ACTION = -50;
    public static final int P_FALLBACK = -100;

    

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

    

    private record HandlerEntry(Object handler, int priority) {}

    

    private final List<HandlerEntry> mouseClickHandlers = new ArrayList<>();
    private final List<HandlerEntry> mouseReleaseHandlers = new ArrayList<>();
    private final List<HandlerEntry> mouseDragHandlers = new ArrayList<>();
    private final List<HandlerEntry> mouseScrollHandlers = new ArrayList<>();
    private final List<HandlerEntry> mouseMoveHandlers = new ArrayList<>();
    private final List<HandlerEntry> keyPressHandlers = new ArrayList<>();
    private final List<HandlerEntry> keyReleaseHandlers = new ArrayList<>();
    private final List<HandlerEntry> charHandlers = new ArrayList<>();

    private boolean sorted;

    

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

    

    
    public boolean dispatch(MouseClickEvent event) {
        ensureSorted();
        for (var entry : mouseClickHandlers) {
            if (((MouseClickHandler) entry.handler()).handle(event) == EventResult.CONSUMED) return true;
        }
        return false;
    }

    
    public boolean dispatch(MouseReleaseEvent event) {
        ensureSorted();
        for (var entry : mouseReleaseHandlers) {
            if (((MouseReleaseHandler) entry.handler()).handle(event) == EventResult.CONSUMED) return true;
        }
        return false;
    }

    
    public boolean dispatch(MouseDragEvent event) {
        ensureSorted();
        for (var entry : mouseDragHandlers) {
            if (((MouseDragHandler) entry.handler()).handle(event) == EventResult.CONSUMED) return true;
        }
        return false;
    }

    
    public boolean dispatch(MouseScrollEvent event) {
        ensureSorted();
        for (var entry : mouseScrollHandlers) {
            if (((MouseScrollHandler) entry.handler()).handle(event) == EventResult.CONSUMED) return true;
        }
        return false;
    }

    
    public boolean dispatch(MouseMoveEvent event) {
        ensureSorted();
        for (var entry : mouseMoveHandlers) {
            if (((MouseMoveHandler) entry.handler()).handle(event) == EventResult.CONSUMED) return true;
        }
        return false;
    }

    
    public boolean dispatch(KeyPressEvent event) {
        ensureSorted();
        for (var entry : keyPressHandlers) {
            if (((KeyPressHandler) entry.handler()).handle(event) == EventResult.CONSUMED) return true;
        }
        return false;
    }

    
    public boolean dispatch(KeyReleaseEvent event) {
        ensureSorted();
        for (var entry : keyReleaseHandlers) {
            if (((KeyReleaseHandler) entry.handler()).handle(event) == EventResult.CONSUMED) return true;
        }
        return false;
    }

    
    public boolean dispatch(CharEvent event) {
        ensureSorted();
        for (var entry : charHandlers) {
            if (((CharHandler) entry.handler()).handle(event) == EventResult.CONSUMED) return true;
        }
        return false;
    }

    

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
