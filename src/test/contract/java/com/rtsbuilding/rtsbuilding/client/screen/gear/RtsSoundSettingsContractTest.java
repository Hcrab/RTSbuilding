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
        String sections = Files.readString(Path.of(
                "src/uiCore/java/com/rtsbuilding/rtsbuilding/uicore/settings/SettingsSectionId.java"));
        String settings = Files.readString(Path.of(
                "src/uiCore/java/com/rtsbuilding/rtsbuilding/uicore/settings/SettingsId.java"));
        String adapter = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/gear/GearMenuUiAdapter.java"));
        String store = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/common/persist/RtsClientUiStateStore.java"));

        int soundSection = sections.indexOf("SOUND(\"screen.rtsbuilding.settings.category.sound\")");
        int animationSection = sections.indexOf(
                "ANIMATION(\"screen.rtsbuilding.settings.category.animation\")");
        int hurtSound = settings.indexOf(
                "DAMAGE_SOUND(SettingsSectionId.SOUND");
        int firstAnimationSetting = settings.indexOf(
                "UI_ANIMATIONS(SettingsSectionId.ANIMATION");
        assertTrue(soundSection >= 0 && animationSection > soundSection,
                "音效分类必须在正式 Core 目录中排在动画分类之前");
        assertTrue(hurtSound >= 0 && firstAnimationSetting > hurtSound,
                "RTS 受击音效应归入音效栏，而不是继续散落在辅助功能中");
        assertTrue(adapter.contains("RtsClientUiStateStore.setRtsSoundsEnabled"));
        assertTrue(adapter.contains("RtsClientUiStateStore.setRtsBreakSoundsEnabled"));
        assertTrue(adapter.contains("RtsClientUiStateStore.setRtsBlockSoundsPerTick"));
        assertTrue(adapter.contains("SettingsId.DAMAGE_SOUND"));
        assertTrue(adapter.contains("SettingsId.BLOCK_SOUNDS_PER_TICK"));
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
