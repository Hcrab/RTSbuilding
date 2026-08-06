package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultButtonTextureLayoutTest {
    @Test
    void allThemesUseTheExactLegacyNineSliceTopology() {
        DefaultButtonTextureLayout.Slice[] slices =
                DefaultButtonTextureLayout.slices(
                        new UiRect(10, 20, 84, 20), UiTextureState.ACTIVE);

        assertEquals(9, slices.length);
        assertEquals(new UiRect(0, 12, 1, 1), slices[0].source());
        assertEquals(new UiRect(1, 12, 2, 1), slices[1].source());
        assertEquals(new UiRect(3, 15, 1, 1), slices[8].source());
        assertEquals(new UiRect(10, 20, 1, 1), slices[0].target());
        assertEquals(new UiRect(11, 21, 82, 18), slices[4].target());
        assertEquals(new UiRect(93, 39, 1, 1), slices[8].target());
    }

    @Test
    void stateOrderMatchesTheLegacyAtlasInsteadOfInventingANewOne() {
        assertEquals(0, DefaultButtonTextureLayout.stateV(UiTextureState.INACTIVE));
        assertEquals(4, DefaultButtonTextureLayout.stateV(UiTextureState.HOVER));
        assertEquals(8, DefaultButtonTextureLayout.stateV(UiTextureState.PRESSED));
        assertEquals(12, DefaultButtonTextureLayout.stateV(UiTextureState.ACTIVE));
    }
}
