package com.rtsbuilding.rtsbuilding.client.presentation.event.model;

public sealed interface InputEvent
        permits MouseClickEvent, MouseReleaseEvent, MouseDragEvent,
                MouseScrollEvent, MouseMoveEvent, KeyPressEvent, KeyReleaseEvent, CharEvent {

    
    boolean consumed();

    
    InputEvent consume();
}
