package com.rtsbuilding.rtsbuilding.client.screen.panel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsWindowSnapperTest {
    @Test
    void oppositeHorizontalEdgesSnapOnlyWhenVerticalRangesOverlap() {
        StubWindow moving = window(125, 40, 80, 60);
        StubWindow other = window(40, 30, 80, 80);

        RtsWindowSnapper.Result result = RtsWindowSnapper.snap(
                moving, Arrays.asList(moving, other), 6);

        assertEquals(121, result.x);
        assertEquals(40, result.y);
        assertTrue(result.snapped);
    }

    @Test
    void nearbyInfiniteEdgeDoesNotSnapWithoutPerpendicularOverlap() {
        StubWindow moving = window(125, 200, 80, 60);
        StubWindow other = window(40, 30, 80, 80);

        RtsWindowSnapper.Result result = RtsWindowSnapper.snap(
                moving, Arrays.asList(moving, other), 6);

        assertEquals(125, result.x);
        assertEquals(200, result.y);
        assertFalse(result.snapped);
    }

    private static StubWindow window(int x, int y, int width, int height) {
        StubWindow window = new StubWindow();
        window.setBounds(x, y, width, height);
        return window;
    }

    private static final class StubWindow extends RtsWindowPanel {
        private StubWindow() {
            this.open = true;
        }

        @Override
        protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY,
                                     float partialTick) {
        }

        @Override
        protected void handleContentClick(double mouseX, double mouseY, int button) {
        }

        @Override
        protected Component getTitle() {
            return Component.empty();
        }

        @Override
        protected int getDefaultWidth() {
            return 40;
        }

        @Override
        protected int getDefaultHeight() {
            return 30;
        }

        @Override
        protected void computeDefaultPosition() {
        }
    }
}
