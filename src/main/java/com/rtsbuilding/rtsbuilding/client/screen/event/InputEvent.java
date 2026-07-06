package com.rtsbuilding.rtsbuilding.client.screen.event;

/**
 * 输入事件——所有 UI 输入事件的密封接口。
 * <p>统一事件类型，替代目前 BuilderScreen 中散落的参数传递。</p>
 */
public sealed interface InputEvent
        permits MouseClickEvent, MouseReleaseEvent, MouseDragEvent,
                MouseScrollEvent, MouseMoveEvent, KeyPressEvent, KeyReleaseEvent, CharEvent {

    /** 获取事件是否已被消费（用于不可变事件对象的传播控制） */
    boolean consumed();

    /** 将事件标记为已消费（返回新对象以保持不可变性） */
    InputEvent consume();
}
