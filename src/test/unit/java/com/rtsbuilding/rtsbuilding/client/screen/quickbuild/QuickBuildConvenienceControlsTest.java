package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.widget.WindowSlider;
import com.rtsbuilding.rtsbuilding.common.mining.SelectionVolumeLimit;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiAction;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceParameter;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceSettings;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiMode;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShape;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class QuickBuildConvenienceControlsTest {
    @Test
    void actualSliderDragUsesServerRangeAndRefreshesItWithoutEmittingAnEdit() {
        var limit = new AtomicReference<>(SelectionVolumeLimit.defaults());
        try (var config = mockStatic(Config.class)) {
            config.when(Config::areaMineSelectionLimit).thenAnswer(ignored -> limit.get());
            config.when(Config::maxTreeBlocks).thenReturn(8192);
            List<QuickBuildUiAction> actions = new ArrayList<>();
            var controls = new QuickBuildControlSurface(actions::add);
            var state = state();
            controls.refreshAll(state);
            WindowSlider slider = controls.convenienceSlider(QuickBuildUiConvenienceParameter.SIZE_Z);
            slider.setX(10);
            slider.setY(20);
            slider.setWidth(100);

            assertTrue(slider.mouseClicked(60, 25, 0));
            assertEquals(33, slider.getValue());
            assertTrue(slider.mouseDragged(200, 25, 0));
            assertEquals(64, slider.getValue());
            assertEquals(64, actions.get(actions.size() - 1).value);

            actions.clear();
            limit.set(new SelectionVolumeLimit(4096, 128, 32, 20));
            controls.refreshAll(state);
            assertSame(slider, controls.convenienceSlider(QuickBuildUiConvenienceParameter.SIZE_Z));
            assertEquals(20, slider.getValue());
            assertTrue(actions.isEmpty());

            assertTrue(slider.mouseDragged(10, 25, 0));
            assertEquals(1, slider.getValue());
            assertTrue(slider.mouseReleased(10, 25, 0));
        }
    }

    @Test
    void dimensionTextAndControllerSettingsUseTheSameEffectiveValues() {
        try (var config = mockStatic(Config.class)) {
            config.when(Config::areaMineSelectionLimit).thenReturn(SelectionVolumeLimit.defaults());
            config.when(Config::maxTreeBlocks).thenReturn(8192);
            var preferences = new QuickBuildPreferenceState();
            preferences.convenienceSettings(
                    new QuickBuildUiConvenienceSettings(3, 3, Integer.MAX_VALUE, 0, 15, 256));
            var controller = new QuickBuildConvenienceController(preferences);

            assertEquals(64, controller.settings().sizeZ());
            assertEquals("3×3×64", controller.dimensionLabel());
            controller.setParameter(QuickBuildUiConvenienceParameter.SIZE_Z, 7);
            assertEquals(7, preferences.convenienceSettings().sizeZ());
            assertEquals("3×3×7", controller.dimensionLabel());
        }
    }

    private static QuickBuildUiState state() {
        return new QuickBuildUiState(true, QuickBuildUiMode.DESTROY, true, "",
                QuickBuildUiShape.BLOCK, QuickBuildUiShape.BOX, List.of(), List.of(),
                64, 1, 256, -1, 0, 0, "", "", "", 0, "", "B", "3×3×3");
    }
}
