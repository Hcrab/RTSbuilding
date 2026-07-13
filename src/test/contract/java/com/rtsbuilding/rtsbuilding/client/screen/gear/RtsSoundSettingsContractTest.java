package com.rtsbuilding.rtsbuilding.client.screen.gear;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsSoundSettingsContractTest {
    @Test
    void gearMenuOwnsTheSoundSectionAndItsThreePlayerControls() throws IOException {
        String gear = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/gear/GearMenuPanel.java"));
        String store = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/common/persist/RtsClientUiStateStore.java"));

        int soundSection = gear.indexOf("screen.rtsbuilding.settings.category.sound");
        int hurtSound = gear.indexOf("screen.rtsbuilding.settings.damage_sound", soundSection);
        int animationSection = gear.indexOf("screen.rtsbuilding.settings.category.animation", soundSection);
        assertTrue(soundSection >= 0 && hurtSound > soundSection && animationSection > hurtSound,
                "RTS 受击音效应归入音效栏，而不是继续散落在辅助功能中");
        assertTrue(gear.contains("RtsClientUiStateStore.setRtsSoundsEnabled"));
        assertTrue(gear.contains("RtsClientUiStateStore.setRtsBreakSoundsEnabled"));
        assertTrue(gear.contains("RtsClientUiStateStore.setRtsBlockSoundsPerTick"));
        assertTrue(gear.contains("settings_sound_expanded"));
        assertTrue(store.contains("public SoundState sound = new SoundState()"));
        assertTrue(store.contains("public int blockSoundsPerTick = 8"));
        assertTrue(store.contains("Math.max(1, Math.min(16, sourceSound.blockSoundsPerTick))"));
    }

    @Test
    void runtimeFiltersSoundsImmediatelyWithoutAddingAnotherQueue() throws IOException {
        String player = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/sound/RtsBlockActionSoundPlayer.java"));
        String payload = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/network/builder/S2CRtsBlockActionSoundPayload.java"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/controller/ClientRtsController.java"));

        assertTrue(player.contains("isRtsSoundsEnabled()"));
        assertTrue(player.contains("payload.breakAction()")
                && player.contains("isRtsBreakSoundsEnabled()"));
        assertTrue(player.contains("getRtsBlockSoundsPerTick()"));
        assertFalse(player.contains("Queue") || player.contains("pending"));
        assertTrue(payload.contains("boolean breakAction"));
        assertTrue(controller.contains("isRtsSoundsEnabled() && this.damageSoundEnabled"));
    }
}
