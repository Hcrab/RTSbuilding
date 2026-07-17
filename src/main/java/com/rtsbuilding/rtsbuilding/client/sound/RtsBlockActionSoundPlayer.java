package com.rtsbuilding.rtsbuilding.client.sound;

import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBlockActionSoundPayload;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * RTS 方块操作的客户端声音出口。
 *
 * <p>声音使用 {@link SoundSource#BLOCKS}，因此仍遵守玩家的“方块”音量设置；同时使用相对声源和
 * {@link SoundInstance.Attenuation#NONE}，不会因为 RTS 相机远离玩家实体而逐渐静音。服务端已经
 * 按当前 tick 限流，因此客户端收到后立即播放，不保留跨 tick 尾音。</p>
 */
public final class RtsBlockActionSoundPlayer {
    private static final RtsBlockActionSoundLimiter LIMITER = new RtsBlockActionSoundLimiter();

    private RtsBlockActionSoundPlayer() {
    }

    public static void play(S2CRtsBlockActionSoundPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (payload == null) {
            return;
        }
        if (!RtsClientUiStateStore.isRtsSoundsEnabled()) {
            return;
        }
        if (payload.breakAction() && !RtsClientUiStateStore.isRtsBreakSoundsEnabled()) {
            return;
        }
        if (minecraft.getSoundManager() == null) {
            return;
        }
        Identifier id = Identifier.tryParse(payload.soundId());
        if (id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
            return;
        }
        if (!LIMITER.tryAcquire(
                minecraft.level.getGameTime(),
                RtsClientUiStateStore.getRtsBlockSoundsPerTick())) {
            return;
        }
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(id);
        SoundInstance soundInstance = new SimpleSoundInstance(
                sound.location(),
                SoundSource.BLOCKS,
                Mth.clamp(payload.volume(), 0.0F, 4.0F),
                Mth.clamp(payload.pitch(), 0.5F, 2.0F),
                RandomSource.create(),
                false,
                0,
                SoundInstance.Attenuation.NONE,
                0.0D,
                0.0D,
                0.0D,
                true);
        minecraft.getSoundManager().play(soundInstance);
    }
}
