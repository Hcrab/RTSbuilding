package com.rtsbuilding.rtsbuilding.client.sound;

import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBlockActionSoundPayload;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * RTS 方块操作的客户端声音出口。
 *
 * <p>声音使用 {@link SoundCategory#BLOCKS}，因此仍遵守玩家的“方块”音量设置；同时关闭距离衰减，
 * 不会因为 RTS 相机远离玩家实体而逐渐静音。服务端已经
 * 按当前 tick 限流，因此客户端收到后立即播放，不保留跨 tick 尾音。</p>
 */
public final class RtsBlockActionSoundPlayer {
    private static final RtsBlockActionSoundLimiter LIMITER = new RtsBlockActionSoundLimiter();

    private RtsBlockActionSoundPlayer() {
    }

    public static void play(S2CRtsBlockActionSoundPayload payload) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.player == null || minecraft.world == null) {
            return;
        }
        if (payload == null) {
            return;
        }
        if (!RtsClientUiStateStore.isRtsSoundsEnabled()) {
            return;
        }
        if (!payload.breakAction() && !RtsClientUiStateStore.isRtsPlacementSoundsEnabled()) {
            return;
        }
        if (payload.breakAction() && !RtsClientUiStateStore.isRtsBreakSoundsEnabled()) {
            return;
        }
        if (minecraft.getSoundHandler() == null) {
            return;
        }
        ResourceLocation id;
        try {
            id = new ResourceLocation(payload.soundId());
        } catch (RuntimeException invalidId) {
            return;
        }
        if (!ForgeRegistries.SOUND_EVENTS.containsKey(id)) {
            return;
        }
        if (!LIMITER.tryAcquire(
                minecraft.world.getTotalWorldTime(),
                RtsClientUiStateStore.getRtsBlockSoundsPerTick())) {
            return;
        }
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(id);
        if (sound == null) {
            return;
        }
        PositionedSoundRecord soundInstance = new PositionedSoundRecord(
                sound.getSoundName(),
                SoundCategory.BLOCKS,
                MathHelper.clamp(payload.volume(), 0.0F, 4.0F),
                MathHelper.clamp(payload.pitch(), 0.5F, 2.0F),
                false,
                0,
                ISound.AttenuationType.NONE,
                0.0F,
                0.0F,
                0.0F);
        minecraft.getSoundHandler().playSound(soundInstance);
    }
}
