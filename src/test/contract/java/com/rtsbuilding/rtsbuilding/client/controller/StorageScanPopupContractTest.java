package com.rtsbuilding.rtsbuilding.client.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageScanPopupContractTest {
    @Test
    void disabledStorageReadyPopupClearsAndSuppressesLongRunningScanUiState() throws IOException {
        String storageState = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/controller/ClientRtsController.java"));
        String builderScreen = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/BuilderScreen.java"));

        assertTrue(storageState.contains("if (!RtsClientUiStateStore.isShowStorageReadyPopupEnabled())")
                        && storageState.contains("clearStorageScanState();"),
                "Starting a storage page request must not keep the scan popup alive when the user disabled it.");
        assertTrue(builderScreen.contains("toggleShowStorageReadyPopup()")
                        && builderScreen.contains("clearStorageScanPopupState()"),
                "Turning the storage ready popup off from the settings UI must clear any currently visible scan state.");
    }
}
