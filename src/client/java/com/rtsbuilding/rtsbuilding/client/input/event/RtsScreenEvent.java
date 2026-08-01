package com.rtsbuilding.rtsbuilding.client.input.event;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

/**
 * 客户端屏幕输入的加载器中立事件值。
 *
 * <p>原实现直接把 NeoForge 的 {@code ScreenEvent} 传入数百行 UI 路由。Fabric 入口和
 * Mixin 只负责构造这里的窄事件对象，既保留取消语义，也让 UI 业务不再依赖某个加载器。
 */
public abstract class RtsScreenEvent {
    private final Screen screen;

    protected RtsScreenEvent(Screen screen) {
        this.screen = screen;
    }

    public final Screen getScreen() {
        return screen;
    }

    public abstract static class Cancellable extends RtsScreenEvent {
        private boolean canceled;

        protected Cancellable(Screen screen) {
            super(screen);
        }

        public final void setCanceled(boolean canceled) {
            this.canceled = canceled;
        }

        public final boolean isCanceled() {
            return canceled;
        }
    }

    public static final class Init {
        private Init() {
        }

        public static final class Post extends RtsScreenEvent {
            private final Consumer<AbstractWidget> listenerAdder;

            public Post(Screen screen, Consumer<AbstractWidget> listenerAdder) {
                super(screen);
                this.listenerAdder = listenerAdder;
            }

            public void addListener(AbstractWidget widget) {
                listenerAdder.accept(widget);
            }
        }
    }

    public static final class Render {
        private Render() {
        }

        public static final class Post extends RtsScreenEvent {
            private final GuiGraphics guiGraphics;
            private final int mouseX;
            private final int mouseY;
            private final float partialTick;

            public Post(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                super(screen);
                this.guiGraphics = guiGraphics;
                this.mouseX = mouseX;
                this.mouseY = mouseY;
                this.partialTick = partialTick;
            }

            public GuiGraphics getGuiGraphics() {
                return guiGraphics;
            }

            public int getMouseX() {
                return mouseX;
            }

            public int getMouseY() {
                return mouseY;
            }

            public float getPartialTick() {
                return partialTick;
            }
        }
    }

    public abstract static class Mouse extends Cancellable {
        private final double mouseX;
        private final double mouseY;

        protected Mouse(Screen screen, double mouseX, double mouseY) {
            super(screen);
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }

        public final double getMouseX() {
            return mouseX;
        }

        public final double getMouseY() {
            return mouseY;
        }
    }

    public static final class MouseButtonPressed {
        private MouseButtonPressed() {
        }

        public static final class Pre extends Mouse {
            private final int button;

            public Pre(Screen screen, double mouseX, double mouseY, int button) {
                super(screen, mouseX, mouseY);
                this.button = button;
            }

            public int getButton() {
                return button;
            }
        }
    }

    public static final class MouseDragged {
        private MouseDragged() {
        }

        public static final class Pre extends Mouse {
            private final int button;
            private final double dragX;
            private final double dragY;

            public Pre(Screen screen, double mouseX, double mouseY, int button, double dragX, double dragY) {
                super(screen, mouseX, mouseY);
                this.button = button;
                this.dragX = dragX;
                this.dragY = dragY;
            }

            public int getButton() {
                return button;
            }

            public double getDragX() {
                return dragX;
            }

            public double getDragY() {
                return dragY;
            }
        }
    }

    public static final class MouseButtonReleased {
        private MouseButtonReleased() {
        }

        public static final class Pre extends Mouse {
            private final int button;

            public Pre(Screen screen, double mouseX, double mouseY, int button) {
                super(screen, mouseX, mouseY);
                this.button = button;
            }

            public int getButton() {
                return button;
            }
        }
    }

    public static final class MouseScrolled {
        private MouseScrolled() {
        }

        public static final class Pre extends Mouse {
            private final double scrollDeltaX;
            private final double scrollDeltaY;

            public Pre(Screen screen, double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
                super(screen, mouseX, mouseY);
                this.scrollDeltaX = scrollDeltaX;
                this.scrollDeltaY = scrollDeltaY;
            }

            public double getScrollDeltaX() {
                return scrollDeltaX;
            }

            public double getScrollDeltaY() {
                return scrollDeltaY;
            }
        }
    }

    public static final class KeyPressed {
        private KeyPressed() {
        }

        public static final class Pre extends Cancellable {
            private final int keyCode;
            private final int scanCode;
            private final int modifiers;

            public Pre(Screen screen, int keyCode, int scanCode, int modifiers) {
                super(screen);
                this.keyCode = keyCode;
                this.scanCode = scanCode;
                this.modifiers = modifiers;
            }

            public int getKeyCode() {
                return keyCode;
            }

            public int getScanCode() {
                return scanCode;
            }

            public int getModifiers() {
                return modifiers;
            }
        }
    }

    public static final class CharacterTyped {
        private CharacterTyped() {
        }

        public static final class Pre extends Cancellable {
            private final int codePoint;
            private final int modifiers;

            public Pre(Screen screen, int codePoint, int modifiers) {
                super(screen);
                this.codePoint = codePoint;
                this.modifiers = modifiers;
            }

            public int getCodePoint() {
                return codePoint;
            }

            public int getModifiers() {
                return modifiers;
            }
        }
    }

    public static final class Closing extends RtsScreenEvent {
        public Closing(Screen screen) {
            super(screen);
        }
    }
}
