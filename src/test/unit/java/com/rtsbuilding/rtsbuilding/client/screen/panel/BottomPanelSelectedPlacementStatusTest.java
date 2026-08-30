package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BottomPanelSelectedPlacementStatusTest {
    private static final String ITEM_STATUS = "screen.rtsbuilding.status.selected_item";
    private static final String EMPTY_HAND_STATUS = "screen.rtsbuilding.status.selected_empty_hand";
    private static final String NONE_STATUS = "screen.rtsbuilding.status.selected_none";

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void resolvedMainHandLabelUsesItemStatusTranslation() {
        BuilderScreen screen = mock(BuilderScreen.class);
        ClientRtsController controller = mock(ClientRtsController.class);
        BottomPanel panel = panel(screen, controller);
        when(controller.hasSelectedFluid()).thenReturn(false);
        when(screen.selectedItemStatusLabel()).thenReturn("Dirt");

        panel.selectedPlacementStatusText();

        verify(screen).text(ITEM_STATUS, "Dirt");
    }

    @Test
    void emptyHandUsesExistingEmptyHandTranslation() {
        BuilderScreen screen = mock(BuilderScreen.class);
        ClientRtsController controller = mock(ClientRtsController.class);
        BottomPanel panel = panel(screen, controller);
        when(controller.hasSelectedFluid()).thenReturn(false);
        when(controller.isEmptyHandSelected()).thenReturn(true);
        when(screen.selectedItemStatusLabel()).thenReturn("");

        panel.selectedPlacementStatusText();

        verify(screen).text(EMPTY_HAND_STATUS);
    }

    @Test
    void missingClientPlayerUsesExistingNoneTranslation() {
        BuilderScreen screen = mock(BuilderScreen.class);
        ClientRtsController controller = mock(ClientRtsController.class);
        BottomPanel panel = panel(screen, controller);
        when(controller.hasSelectedFluid()).thenReturn(false);
        when(controller.isEmptyHandSelected()).thenReturn(false);
        when(screen.selectedItemStatusLabel()).thenReturn("");
        when(screen.getMinecraft()).thenReturn(null);

        panel.selectedPlacementStatusText();

        verify(screen).text(NONE_STATUS);
    }

    private static BottomPanel panel(BuilderScreen screen, ClientRtsController controller) {
        BottomPanel panel = new BottomPanel();
        panel.init(screen, controller);
        return panel;
    }
}
