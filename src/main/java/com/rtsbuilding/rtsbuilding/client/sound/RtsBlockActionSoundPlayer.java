package com.rtsbuilding.rtsbuilding.client.sound;

import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBlockActionSoundPayload;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.util.ResourceLocation;
import com.rtsbuilding.rtsbuilding.platform.math.MathHelper;

/**
 * RTS 方块操作的客户端声音出口。
 *
 * <p>1.7.10 的声音分类由 sounds.json 决定；这里关闭距离衰减，
 * 不会因为 RTS 相机远离玩家实体而逐渐静音。服务端已经
 * 按当前 tick 限流，因此客户端收到后立即播放，不保留跨 tick 尾音。</p>
 */
public final class RtsBlockActionSoundPlayer {
    private static final RtsBlockActionSoundLimiter LIMITER = new RtsBlockActionSoundLimiter();

    private RtsBlockActionSoundPlayer() {
    }

    public static void play(S2CRtsBlockActionSoundPayload payload) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null || minecraft.theWorld == null) {
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
        if (!LIMITER.tryAcquire(
                minecraft.theWorld.getTotalWorldTime(),
                RtsClientUiStateStore.getRtsBlockSoundsPerTick())) {
            return;
        }
        NoAttenuationSound soundInstance = new NoAttenuationSound(
                id,
                MathHelper.clamp(payload.volume(), 0.0F, 4.0F),
                MathHelper.clamp(payload.pitch(), 0.5F, 2.0F));
        minecraft.getSoundHandler().playSound(soundInstance);
    }

    /** 1.7.10 的 PositionedSoundRecord 不公开无衰减构造器，因此使用最小专用实现。 */
    private static final class NoAttenuationSound extends PositionedSound {
        private NoAttenuationSound(ResourceLocation id, float volume, float pitch) {
            super(id);
            this.volume = volume;
            this.field_147663_c = pitch;
            this.field_147666_i = ISound.AttenuationType.NONE;
        }
    }
}
